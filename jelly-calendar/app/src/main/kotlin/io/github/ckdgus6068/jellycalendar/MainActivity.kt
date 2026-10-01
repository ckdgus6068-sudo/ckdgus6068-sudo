package io.github.ckdgus6068.jellycalendar

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
import androidx.lifecycle.lifecycleScope
import io.github.ckdgus6068.jellycalendar.core.FontChoice
import io.github.ckdgus6068.jellycalendar.ui.JellyCalendarApp
import io.github.ckdgus6068.jellycalendar.ui.JellyPlatform
import io.github.ckdgus6068.jellycalendar.ui.SharedHost
import io.github.ckdgus6068.jellycalendar.ui.SharedHostActions
import io.github.ckdgus6068.jellycalendar.ui.theme.BundledFonts
import io.github.ckdgus6068.jellycalendar.ui.theme.DarkJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.DisplayFace
import io.github.ckdgus6068.jellycalendar.ui.theme.LightJellyColors
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The shared calendar, published from the repository's docs/share folder by GitHub Pages. */
private const val SHARE_URL = "https://ckdgus6068-sudo.github.io/ckdgus6068-sudo/share/"
private val SHARE_HOST = Uri.parse(SHARE_URL).host

class MainActivity : ComponentActivity() {

    private val app: JellyApplication get() = application as JellyApplication

    /** Kept for the life of the screen, so switching tabs does not reload the shared calendar. */
    private var sharedWeb: WebView? = null

    /** What the shared page should show, as the JSON it reads through [ShareBridge.hostState]. */
    @Volatile
    private var hostJson: String = "{\"mode\":\"shared\"}"
    private var hostActions: SharedHostActions? = null

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

        override fun setAlarm(hour: Int, minute: Int, label: String, skipUi: Boolean): Boolean =
            AlarmBridge.setAlarm(this@MainActivity, hour, minute, label, skipUi)

        override fun openAlarmList(): Boolean = AlarmBridge.openAlarmList(this@MainActivity)

        override fun shareText(text: String, title: String): Boolean = runCatching {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(send, title))
        }.isSuccess

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
        override fun SharedSpace(modifier: Modifier, host: SharedHost) {
            AndroidView(
                factory = { sharedWebView().also { (it.parent as? ViewGroup)?.removeView(it) } },
                update = { showHost(it, host) },
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
            clean = FontFamily(
                Font(R.font.pretendard_regular, FontWeight.Normal),
                Font(R.font.pretendard_semibold, FontWeight.SemiBold),
                Font(R.font.pretendard_bold, FontWeight.Bold),
            ),
            // Each display face has one weight, declared as such so that Compose never fakes another.
            faces = mapOf(
                FontChoice.NANUM_ROUND to face(R.font.nanum_square_round_extrabold, FontWeight.ExtraBold),
                FontChoice.JUA to face(R.font.jua_regular, FontWeight.Normal),
                FontChoice.ROUND to face(R.font.bagel_fat_one, FontWeight.Black),
            ),
        )

        private fun face(id: Int, weight: FontWeight) = DisplayFace(FontFamily(Font(id, weight)), weight)
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

    /**
     * The app's side of the shared page: invite texts go to Android's share sheet (KakaoTalk,
     * messages...), and in "모두" the page reads the day's own jellies and asks the app to open,
     * finish or add them. Called on a background thread, so everything is handed to the UI thread.
     */
    inner class ShareBridge {
        @JavascriptInterface
        fun share(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, "공유 젤리 초대 보내기"))
            }
        }

        @JavascriptInterface
        fun hostState(): String = hostJson

        /** Whether the app can sign in with Google for the page (set up in res/values/config.xml). */
        @JavascriptInterface
        fun googleAvailable(): Boolean = GoogleSignIn.isAvailable(this@MainActivity)

        /** Shows the phone's Google account sheet; the answer goes to the page's jellyHost. */
        @JavascriptInterface
        fun googleSignIn() {
            runOnUiThread {
                lifecycleScope.launch {
                    val js = when (val outcome = GoogleSignIn.idToken(this@MainActivity)) {
                        is GoogleSignIn.Outcome.Token -> "window.jellyHost && window.jellyHost.googleToken(${JSONObject.quote(outcome.idToken)})"
                        is GoogleSignIn.Outcome.Failed -> "window.jellyHost && window.jellyHost.googleFailed(${JSONObject.quote(outcome.reason)})"
                    }
                    sharedWeb?.evaluateJavascript(js, null)
                }
            }
        }

        /**
         * The shared page found the golden jelly, on one of this phone's jellies ([personalId]) or on a
         * shared one (empty). The app keeps one code per phone and celebrates.
         */
        @JavascriptInterface
        fun foundGolden(personalId: String) {
            runOnUiThread { app.store.makeGolden(personalId.ifEmpty { null }) }
        }

        /** A clock alarm for a shared jelly, asked for on the shared page (24-hour [hour]:[minute]). */
        @JavascriptInterface
        fun setAlarm(hour: Int, minute: Int, label: String) {
            if (hour !in 0..23 || minute !in 0..59) return
            runOnUiThread {
                val skipUi = app.store.current.settings.alarmSkipUi
                if (!AlarmBridge.setAlarm(this@MainActivity, hour, minute, label.take(60), skipUi)) {
                    toast("알람을 맞출 시계 앱을 찾지 못했어요")
                }
            }
        }

        @JavascriptInterface
        fun openPersonal(id: String) = onHost { it.openPersonal(id) }

        @JavascriptInterface
        fun togglePersonal(id: String) = onHost { it.togglePersonal(id) }

        @JavascriptInterface
        fun createPersonal(date: String) {
            val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
            onHost { it.createPersonal(day) }
        }

        @JavascriptInterface
        fun shiftDay(direction: Int) = onHost { it.shiftDay(direction) }

        @JavascriptInterface
        fun showDay(date: String) {
            val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
            onHost { it.showDay(day) }
        }

        @JavascriptInterface
        fun showShared() = onHost { it.showShared() }
    }

    private fun onHost(request: (SharedHostActions) -> Unit) {
        runOnUiThread { hostActions?.let(request) }
    }

    /** Hands the page what to show, and pokes it when that changed (it then reads [hostJson]). */
    private fun showHost(web: WebView, host: SharedHost) {
        hostActions = host.actions
        val json = hostJsonOf(host)
        if (json == hostJson) return
        hostJson = json
        web.evaluateJavascript("window.jellyHost && window.jellyHost.poke()", null)
    }

    private fun hostJsonOf(host: SharedHost): String {
        val personal = JSONArray()
        if (host.all) {
            for (jelly in host.personal) {
                personal.put(
                    JSONObject()
                        .put("id", jelly.id)
                        .put("title", jelly.title)
                        .put("date", jelly.date?.toString() ?: JSONObject.NULL)
                        .put("start", jelly.startMin ?: JSONObject.NULL)
                        .put("duration", jelly.durationMin)
                        .put("flavor", jelly.flavor)
                        .put("done", jelly.isDone)
                        .put("pinned", jelly.pinned),
                )
            }
        }
        return JSONObject()
            .put("mode", if (host.all) "all" else "shared")
            .put("date", host.date.toString())
            .put("doubleTap", host.doneByDoubleTap)
            .put("longPress", host.doneByLongPress)
            .put("sundayFirst", host.weekStartsOnSunday)
            .put("font", host.font.name)
            .put("personal", personal)
            .toString()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun sharedWebView(): WebView = sharedWeb ?: WebView(this).also { web ->
        // Without this, Compose adds the view as WRAP_CONTENT, and the web view then lays the page out
        // as if it had no height: CSS vh becomes 0 and the page's bottom sheets collapse to a sliver.
        web.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Lets the page know it runs inside the app (no "add to home screen" hint, app share sheet).
        web.settings.userAgentString = "${web.settings.userAgentString} JellyCalendarApp/${platform.appVersion}"
        web.setBackgroundColor(Color.TRANSPARENT)
        web.addJavascriptInterface(ShareBridge(), "JellyBridge")
        // Without a chrome client a web view drops browser dialogs and confirm() answers "no". The page
        // asks its questions itself, but an older copy of it still uses confirm().
        web.webChromeClient = WebChromeClient()
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
