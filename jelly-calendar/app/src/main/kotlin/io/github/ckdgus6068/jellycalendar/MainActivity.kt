package io.github.ckdgus6068.jellycalendar

import android.os.Bundle
import android.os.SystemClock
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
import io.github.ckdgus6068.jellycalendar.ui.JellyCalendarApp
import io.github.ckdgus6068.jellycalendar.ui.JellyPlatform
import io.github.ckdgus6068.jellycalendar.ui.theme.BundledFonts
import io.github.ckdgus6068.jellycalendar.ui.theme.DarkJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LightJellyColors

class MainActivity : ComponentActivity() {

    private val app: JellyApplication get() = application as JellyApplication

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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
