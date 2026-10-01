package io.github.ckdgus6068.jellycalendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ckdgus6068.jellycalendar.core.FontChoice
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.Look
import io.github.ckdgus6068.jellycalendar.ui.theme.BundledFonts
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import java.time.LocalDate

/** Things only the phone can do. The Android implementation lives next to MainActivity. */
interface JellyPlatform {
    /**
     * Asks the clock app (Samsung Clock on Galaxy phones) to create a one-time alarm, and then to
     * switch off the alarms at [dismissMinutes] that it replaces. The new alarm always goes first,
     * so a failed switch-off never leaves the user without a wake-up alarm.
     * Returns false when no clock app accepted the new alarm.
     */
    fun setWakeAlarm(hour: Int, minute: Int, label: String, skipUi: Boolean, dismissMinutes: List<Int>): Boolean

    /**
     * Asks the clock app for a plain one-time alarm, e.g. a little before a jelly starts. It rings
     * at the next [hour]:[minute] (24-hour clock). Returns false when no clock app accepted it.
     */
    fun setAlarm(hour: Int, minute: Int, label: String, skipUi: Boolean): Boolean = false

    /** Opens the clock app's alarm list. */
    fun openAlarmList(): Boolean

    /** Offers [text] to the phone's share sheet (KakaoTalk, messages, …) under [title]. */
    fun shareText(text: String, title: String): Boolean = false

    /** Lets the user save [json] as a file. */
    fun exportBackup(fileName: String, json: String)

    /** Lets the user pick a backup file; [onLoaded] receives its text. */
    fun importBackup(onLoaded: (String) -> Unit)

    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)

    /** The typefaces bundled with the app; without them the phone's own font is used. */
    val fonts: BundledFonts get() = BundledFonts.None

    /** The installed version, e.g. "0.2.12", shown in settings. */
    val appVersion: String get() = ""

    /**
     * The shared calendar ("공유 젤리"), a web page that the other person can open on any phone.
     * [host] says what it shows here: the calendar itself, or one day with the phone's own jellies.
     */
    @Composable
    fun SharedSpace(modifier: Modifier, host: SharedHost) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "이 기기에서는 공유 젤리를 열 수 없어요.",
                color = LocalJellyColors.current.textSub,
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    /** Lets the shared page take the back button first, e.g. to close an open sheet. */
    fun sharedBack(): Boolean = false
}

/** What the shared page shows inside the app, and what it may ask the app to do. */
class SharedHost(
    /** False: the shared calendar. True: everything on [date] in one box ("모두"). */
    val all: Boolean,
    val date: LocalDate,
    /** The phone's own jellies of the six weeks around [date]; only handed to the page when [all]. */
    val personal: List<Jelly>,
    val doneByDoubleTap: Boolean,
    val doneByLongPress: Boolean,
    val weekStartsOnSunday: Boolean,
    /** The lettering picked in settings, so the page matches the rest of the app. */
    val font: FontChoice,
    /** "말랑말랑 숨쉬기": pinned jellies sway in the page's box too. */
    val idleWobble: Boolean,
    /** My character (null until picked: the page then hands over its own), and whether mine wear it. */
    val look: Look?,
    val lookOnMine: Boolean,
    val actions: SharedHostActions,
)

/** Requests from the shared page about the phone's own jellies. */
interface SharedHostActions {
    fun openPersonal(id: String)
    fun togglePersonal(id: String)
    fun createPersonal(date: LocalDate)
    fun shiftDay(direction: Int)
    fun showDay(date: LocalDate)
    fun showShared()
}
