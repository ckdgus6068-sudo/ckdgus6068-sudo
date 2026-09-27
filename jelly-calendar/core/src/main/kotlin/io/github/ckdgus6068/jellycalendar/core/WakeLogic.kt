package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDate
import java.time.LocalDateTime

/** What the wake-up card should say about one day. */
sealed interface WakeStatus {
    /** The alarm and the first jelly are [Settings.wakeGapMin] apart, as intended. */
    data class Synced(val alarmMin: Int, val first: Jelly, val alarmPassed: Boolean) : WakeStatus

    data class Mismatch(
        val alarmMin: Int,
        val first: Jelly,
        val suggestedAlarm: Int,
        val alarmPassed: Boolean,
        val canSetNow: Boolean,
    ) : WakeStatus

    /** No alarm is known for the day. [canSetNow] is true when a one-time alarm would ring on it. */
    data class NoAlarm(val first: Jelly, val suggestedAlarm: Int, val canSetNow: Boolean) : WakeStatus

    data class AlarmOnly(val alarmMin: Int, val alarmPassed: Boolean) : WakeStatus

    data object Empty : WakeStatus
}

object WakeLogic {

    fun inWindow(settings: Settings, minute: Int): Boolean =
        minute >= settings.wakeWindowStartMin && minute < settings.wakeWindowEndMin

    /**
     * Records the system's next alarm. Future records that no longer match it are dropped
     * (the alarm was changed or deleted). A day whose alarm already rang keeps its record,
     * so a second, later alarm on the same day does not move the morning around.
     */
    fun observe(data: AppData, next: NextAlarm?, now: LocalDateTime): AppData {
        var wakes = data.wakes.filter { rec ->
            !rec.dateTime.isAfter(now) || (next != null && rec.date == next.date && rec.minute == next.minute)
        }
        var alignDate: LocalDate? = null
        var alignMinute = 0
        if (next != null && inWindow(data.settings, next.minute) && wakes.none { it.date == next.date }) {
            wakes = wakes + WakeRecord(next.date, next.minute, next.source)
            alignDate = next.date
            alignMinute = next.minute
        }
        var result = if (wakes == data.wakes) data else data.copy(wakes = wakes)
        if (alignDate != null && result.settings.followAlarm) {
            result = Planner.alignAnchored(result, alignDate, alignMinute)
        }
        return result
    }

    /** Re-applies every known alarm from [today] on, e.g. after the wake gap was changed. */
    fun realignAll(data: AppData, today: LocalDate): AppData {
        if (!data.settings.followAlarm) return data
        var result = data
        for (rec in data.wakes) {
            if (!rec.date.isBefore(today)) result = Planner.alignAnchored(result, rec.date, rec.minute)
        }
        return result
    }

    /** A one-time clock alarm rings at the next occurrence of hh:mm after [now]. */
    fun nextOccurrence(minute: Int, now: LocalDateTime): LocalDateTime {
        val candidate = now.toLocalDate().atTime(minute / 60, minute % 60)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    fun targetsDate(date: LocalDate, minute: Int, now: LocalDateTime): Boolean =
        nextOccurrence(minute, now).toLocalDate() == date

    /** The alarm time that keeps [Settings.wakeGapMin] before [first]; null when it is not a morning jelly. */
    fun suggestedAlarm(settings: Settings, first: Jelly?): Int? {
        val start = first?.startMin ?: return null
        val alarm = (start - settings.wakeGapMin).coerceAtLeast(0)
        return alarm.takeIf { inWindow(settings, it) }
    }

    /**
     * Alarm times that a new wake-up alarm at [newMinute] on [date] replaces: the wake-up alarm the
     * phone reported for that day and the alarm this app asked for earlier, while they are still
     * ahead. A repeating alarm is only skipped once by the clock app, so this is safe for both kinds.
     */
    fun replacedAlarms(data: AppData, date: LocalDate, newMinute: Int, now: LocalDateTime): List<Int> {
        if (!data.settings.autoDismissOld) return emptyList()
        val minutes = LinkedHashSet<Int>()
        data.wakeOn(date)?.takeIf { it.dateTime.isAfter(now) }?.let { minutes += it.minute }
        data.lastAlarmRequest?.takeIf { it.date == date && it.dateTime.isAfter(now) }?.let { minutes += it.minute }
        minutes -= newMinute
        return minutes.toList()
    }

    fun status(data: AppData, date: LocalDate, now: LocalDateTime): WakeStatus {
        val settings = data.settings
        val wake = data.wakeOn(date)
        val first = Planner.firstOfDay(data, date)
        val suggested = suggestedAlarm(settings, first)
        val passed = wake != null && !wake.dateTime.isAfter(now)
        return when {
            wake != null && first != null && suggested != null ->
                if (wake.minute == suggested) {
                    WakeStatus.Synced(wake.minute, first, passed)
                } else {
                    WakeStatus.Mismatch(
                        alarmMin = wake.minute,
                        first = first,
                        suggestedAlarm = suggested,
                        alarmPassed = passed,
                        canSetNow = targetsDate(date, suggested, now),
                    )
                }
            first != null && suggested != null -> {
                // Once the wake-up window has opened the day has begun: no wake-up alarm for it any more.
                // Late at night (before the window) a same-day morning alarm still makes sense.
                val nowMinute = now.hour * 60 + now.minute
                val morningOver = date == now.toLocalDate() &&
                    (nowMinute >= settings.wakeWindowStartMin || suggested - nowMinute < 60)
                if (morningOver) WakeStatus.Empty else WakeStatus.NoAlarm(first, suggested, targetsDate(date, suggested, now))
            }
            wake != null -> WakeStatus.AlarmOnly(wake.minute, passed)
            else -> WakeStatus.Empty
        }
    }
}
