package io.github.ckdgus6068.jellycalendar.ui

import androidx.compose.runtime.Composable
import io.github.ckdgus6068.jellycalendar.ui.theme.BundledFonts

/** Things only the phone can do. The Android implementation lives next to MainActivity. */
interface JellyPlatform {
    /**
     * Asks the clock app (Samsung Clock on Galaxy phones) to create a one-time alarm, and then to
     * switch off the alarms at [dismissMinutes] that it replaces. The new alarm always goes first,
     * so a failed switch-off never leaves the user without a wake-up alarm.
     * Returns false when no clock app accepted the new alarm.
     */
    fun setWakeAlarm(hour: Int, minute: Int, label: String, skipUi: Boolean, dismissMinutes: List<Int>): Boolean

    /** Opens the clock app's alarm list. */
    fun openAlarmList(): Boolean

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
}
