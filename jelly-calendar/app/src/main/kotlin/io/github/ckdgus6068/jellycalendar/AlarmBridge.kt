package io.github.ckdgus6068.jellycalendar

import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import io.github.ckdgus6068.jellycalendar.core.NextAlarm
import java.time.Instant
import java.time.ZoneId

/**
 * The link to the phone's alarm clock (Samsung Clock on Galaxy phones).
 *
 * Android offers these public doors, and this is all that is used:
 * - reading: [AlarmManager.getNextAlarmClock] tells the time of the next alarm set by any clock app;
 * - writing: [AlarmClock.ACTION_SET_ALARM] asks the clock app to create a one-time alarm. It takes
 *   a time but no date, so it rings within a day; for a later day Samsung Clock's own new-alarm
 *   screen is opened instead, where the date can be picked;
 * - switching off: [AlarmClock.ACTION_DISMISS_ALARM] asks it to dismiss the alarm at a given time
 *   (a one-time alarm is turned off, a repeating one only skips its next ring).
 * Existing alarms cannot be listed, edited or deleted through a public API.
 */
object AlarmBridge {
    const val SAMSUNG_CLOCK = "com.sec.android.app.clockpackage"

    fun readNextAlarm(context: Context): NextAlarm? {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return null
        val info = manager.nextAlarmClock ?: return null
        val time = Instant.ofEpochMilli(info.triggerTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val source = runCatching { info.showIntent?.creatorPackage }.getOrNull()
        return NextAlarm(time, source)
    }

    fun setAlarm(activity: Activity, hour: Int, minute: Int, label: String, skipUi: Boolean): Boolean {
        val request = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
        return startPreferringSamsung(activity, request)
    }

    /**
     * For an alarm on a later day: opens Samsung Clock's new-alarm screen with [hour]:[minute] and
     * [label] filled in. The request cannot carry a date, so the person picks it there (the calendar
     * icon) and saves. Only Samsung Clock is asked: other clock apps may save the alarm for the
     * coming day straight away. False when Samsung Clock is not there.
     */
    fun openAlarmEditor(activity: Activity, hour: Int, minute: Int, label: String): Boolean {
        val request = Intent(AlarmClock.ACTION_SET_ALARM)
            .setPackage(SAMSUNG_CLOCK)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        return start(activity, request)
    }

    /**
     * Asks the clock app that owns the alarm ([ownerPackage], else Samsung Clock) to dismiss the
     * alarm set for [hour]:[minute] (24-hour clock). Other clock apps are never asked: they cannot
     * hold that alarm and would only show their own screen.
     */
    fun dismissAlarm(activity: Activity, hour: Int, minute: Int, ownerPackage: String?): Boolean {
        val request = Intent(AlarmClock.ACTION_DISMISS_ALARM)
            .putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
        val owners = listOfNotNull(ownerPackage, SAMSUNG_CLOCK).distinct()
        return owners.any { start(activity, Intent(request).setPackage(it)) }
    }

    fun openAlarmList(activity: Activity): Boolean =
        startPreferringSamsung(activity, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    // Prefer Samsung Clock so no app chooser pops up; fall back to any clock app.
    private fun startPreferringSamsung(activity: Activity, request: Intent): Boolean =
        start(activity, Intent(request).setPackage(SAMSUNG_CLOCK)) || start(activity, request)

    private fun start(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
