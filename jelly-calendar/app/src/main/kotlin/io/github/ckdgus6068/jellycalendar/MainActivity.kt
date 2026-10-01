package io.github.ckdgus6068.jellycalendar

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import io.github.ckdgus6068.jellycalendar.ui.JellyCalendarApp
import io.github.ckdgus6068.jellycalendar.ui.JellyPlatform
import io.github.ckdgus6068.jellycalendar.ui.theme.BundledFonts
import io.github.ckdgus6068.jellycalendar.ui.theme.DarkJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LightJellyColors

/** The shared calendar, published from the repository's docs/share folder by GitHub Pages. */
private const val SHARE_URL = "https://ckdgus6068-sudo.github.io/ckdgus6068-sudo/share/"
private val SHARE_HOST = Uri.parse(SHARE_URL).host

class MainActivity : ComponentActivity() {

    private val app: JellyApplication get() = application as JellyApplication

    /** Kept for the life of the screen, so switching tabs does not reload the shared calendar. */
    private var sharedWeb: WebView? = null

    private var pendingExport: String? = null
    private var pendingImport: ((String) -> Unit)? = null

    /** Old alarms still to be switched off, one clock-app round trip at a time. */
    private val pendingDismissals = ArrayDeque<Int>()
    private var dismissOwner: String? = null
    private var waitingForClockSince = 0L

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri == null || json == null) return@registerForActivityResult
        val saved = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
        toast(if (saved) "백업 파일을 저장했어요" else "백업 파일을 저장하지 못했어요")
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pendingImport
        pendingImport = null
        if (uri == null || callback == null) return@registerForActivityResult
        val text = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (text != null) callback(text) else toast("파일을 열지 못했어요")
    }

    private val platform = object : JellyPlatform {
        override fun setWakeAlarm(hour: Int, minute: Int, label: String, skipUi: Boolean, dismissMinutes: List<Int>): Boolean {
            pendingDismissals.clear()
            // The new alarm goes first: if switching the old one off fails, the user still wakes up.
            if (!AlarmBridge.setAlarm(this@MainActivity, hour, minute, label, skipUi)) return false
            pendingDismissals.addAll(dismissMinutes)
            // The app that made the alarm the phone reported, e.g. Samsung Clock.
            dismissOwner = app.store.nextAlarm.value?.source
            waitingForClockSince = SystemClock.elapsedRealtime()
            return true
        }

        override fun openAlarmList(): Boolean = AlarmBridge.openAlarmList(this@MainActivity)

        override fun exportBackup(fileName: String, json: String) {
            pendingExport = json
            exportLauncher.launch(fileName)
        }

        override fun importBackup(onLoaded: (String) -> Unit) {
            pendingImport = onLoaded
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }

        @Composable
        override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
            androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
        }

        @Composable
        override fun SharedSpace(modifier: Modifier) {
            AndroidView(
                factory = { sharedWebView().also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = modifier,
            )
        }

        override fun sharedBack(): Boolean {
            val web = sharedWeb ?: return false
            if (!web.canGoBack()) return false
            web.goBack()
            return true
        }

        override val appVersion: String
            get() = runCatching {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull().orEmpty()

        override val fonts = BundledFonts(
            // One heavy weight only: declared as Black so that Compose never fakes a bolder one.
            round = FontFamily(Font(R.font.bagel_fat_one, FontWeight.Black)),
            clean = FontFamily(
                Font(R.font.pretendard_regular, FontWeight.Normal),
                Font(R.font.pretendard_semibold, FontWeight.SemiBold),
                Font(R.font.pretendard_bold, FontWeight.Bold),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val background = if (isSystemInDarkTheme()) DarkJellyColors.background else LightJellyColors.background
            Box(
                Modifier
                    .fillMaxSize()
                    .background(background)
                    .systemBarsPadding(),
            ) {
                JellyCalendarApp(store = app.store, platform = platform)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sharedWeb?.onResume()
        // Back from the clock app: send the next switch-off request, one at a time, so the clock
        // app never gets two requests at once. Stale requests (the user went elsewhere) are dropped.
        if (waitingForClockSince != 0L) {
            val fresh = SystemClock.elapsedRealtime() - waitingForClockSince < 120_000L
            waitingForClockSince = 0L
            if (!fresh) pendingDismissals.clear()
            while (pendingDismissals.isNotEmpty()) {
                val minute = pendingDismissals.removeFirst()
                if (AlarmBridge.dismissAlarm(this, minute / 60, minute % 60, dismissOwner)) {
                    waitingForClockSince = SystemClock.elapsedRealtime()
                    break
                }
            }
        }
        // Coming back from Samsung Clock (or from the night): pick up the next alarm and the new day.
        app.store.observeAlarm(AlarmBridge.readNextAlarm(this))
        app.store.refresh(emptyList())
    }

    override fun onStop() {
        super.onStop()
        app.flush()
    }

    override fun onPause() {
        super.onPause()
        sharedWeb?.onPause()
    }

    override fun onDestroy() {
        sharedWeb?.destroy()
        sharedWeb = null
        super.onDestroy()
    }

    /** Hands invite texts from the shared page to Android's share sheet (KakaoTalk, messages...). */
    inner class ShareBridge {
        @JavascriptInterface
        fun share(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, "공유 젤리 초대 보내기"))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun sharedWebView(): WebView = sharedWeb ?: WebView(this).also { web ->
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Lets the page know it runs inside the app (no "add to home screen" hint, app share sheet).
        web.settings.userAgentString = "${web.settings.userAgentString} JellyCalendarApp/${platform.appVersion}"
        web.setBackgroundColor(Color.TRANSPARENT)
        web.addJavascriptInterface(ShareBridge(), "JellyBridge")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == SHARE_HOST) return false
                // Anything else (a link in a memo, say) opens in the browser.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showSharedProblem(view)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) showSharedProblem(view)
            }
        }
        web.loadUrl(SHARE_URL)
        sharedWeb = web
    }

    private fun showSharedProblem(web: WebView) {
        val page = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="font-family:sans-serif;text-align:center;padding:72px 24px;color:#8C8089;word-break:keep-all">
            <p style="font-size:18px;color:#2B2530">공유 젤리를 열지 못했어요</p>
            <p>인터넷 연결을 확인하거나, 잠시 뒤 다시 시도해 주세요.</p>
            <p><a href="$SHARE_URL" style="color:#FF6F93;font-weight:bold">다시 시도</a></p>
            </body></html>
        """.trimIndent()
        web.loadDataWithBaseURL(SHARE_URL, page, "text/html", "utf-8", null)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
