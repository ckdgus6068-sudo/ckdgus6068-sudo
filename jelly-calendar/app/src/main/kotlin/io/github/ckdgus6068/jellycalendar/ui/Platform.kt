package io.github.ckdgus6068.jellycalendar.ui

import androidx.compose.runtime.Composable

/** Things only the phone can do. The Android implementation lives next to MainActivity. */
interface JellyPlatform {
    /**
     * Asks the clock app (Samsung Clock on Galaxy phones) to create a one-time alarm.
     * Returns false when no clock app accepted the request.
     */
    fun setWakeAlarm(hour: Int, minute: Int, label: String, skipUi: Boolean): Boolean

    /** Opens the clock app's alarm list. */
    fun openAlarmList(): Boolean

    /** Lets the user save [json] as a file. */
    fun exportBackup(fileName: String, json: String)

    /** Lets the user pick a backup file; [onLoaded] receives its text. */
    fun importBackup(onLoaded: (String) -> Unit)

    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)
}
