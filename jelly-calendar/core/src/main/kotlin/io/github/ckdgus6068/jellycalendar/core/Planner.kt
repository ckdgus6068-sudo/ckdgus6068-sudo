package io.github.ckdgus6068.jellycalendar.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Pure scheduling rules. Every function takes an [AppData] and returns a new one,
 * so the whole calendar can be unit tested without Android.
 */
object Planner {

    const val MIN_DURATION = 5

    fun weekStart(date: LocalDate, sundayFirst: Boolean): LocalDate {
        val first = if (sundayFirst) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        return date.with(TemporalAdjusters.previousOrSame(first))
    }

    fun weekDays(start: LocalDate): List<LocalDate> = (0L..6L).map { start.plusDays(it) }

    fun materializeKey(routineId: String, date: LocalDate): String = "$routineId@$date"

    private fun keyDate(key: String): LocalDate? =
        runCatching { LocalDate.parse(key.substringAfterLast('@')) }.getOrNull()

    /** Rounds [minute] to the nearest multiple of [step]. */
    fun snap(minute: Int, step: Int): Int {
        if (step <= 1) return minute
        val rest = Math.floorMod(minute, step)
        return if (rest * 2 >= step) minute - rest + step else minute - rest
    }

    fun clampStart(start: Int, duration: Int): Int =
        start.coerceIn(0, (MINUTES_PER_DAY - duration).coerceAtLeast(0))

    fun clampDuration(duration: Int, start: Int?): Int {
        val max = if (start != null) MINUTES_PER_DAY - start else MINUTES_PER_DAY
        return duration.coerceIn(MIN_DURATION, max.coerceAtLeast(MIN_DURATION))
    }

    // ---------------------------------------------------------------- queries

    fun scheduledOn(data: AppData, date: LocalDate): List<Jelly> =
        data.jellies
            .filter { it.date == date && it.startMin != null }
            .sortedWith(compareBy<Jelly>({ it.startMin }, { it.createdAt }))

    /** Jellies waiting at the bottom: the oldest miss first, then the ones added by hand. */
    fun tray(data: AppData): List<Jelly> =
        data.jellies
            .filter { it.isInTray }
            .sortedWith(compareBy<Jelly>({ it.missedFrom ?: LocalDate.MAX }, { it.createdAt }))

    /** Faint markers for jellies that were missed on [date] and have since moved away. */
    fun ghostsOn(data: AppData, date: LocalDate): List<Jelly> =
        data.jellies.filter { it.missedFrom == date && it.missedFromStart != null && it.date != date }

    fun firstOfDay(data: AppData, date: LocalDate): Jelly? =
        scheduledOn(data, date).firstOrNull { it.status != JellyStatus.MISSED }

    /** First start at or after [from] where [duration] minutes fit between the jellies of [date]. */
    fun findFreeSlot(
        data: AppData,
        date: LocalDate,
        duration: Int,
        from: Int,
        excludeId: String? = null,
    ): Int {
        var candidate = clampStart(from, duration)
        for (j in scheduledOn(data, date)) {
            if (j.id == excludeId || j.status == JellyStatus.MISSED) continue
            val start = j.startMin ?: continue
            val end = start + j.durationMin
            if (end <= candidate) continue
            if (candidate + duration <= start) return candidate
            candidate = end
        }
        return if (candidate + duration <= MINUTES_PER_DAY) candidate else clampStart(from, duration)
    }

    // ---------------------------------------------------------- routine days

    fun instanceOf(routine: Routine, date: LocalDate, nowMillis: Long, id: String): Jelly = Jelly(
        id = id,
        title = routine.title,
        flavor = routine.flavor,
        durationMin = routine.durationMin,
        date = date,
        startMin = clampStart(routine.startMin, routine.durationMin),
        routineId = routine.id,
        wakeAnchored = routine.wakeAnchored,
        anchorOrder = routine.startMin,
        carryOver = routine.carryOver,
        note = routine.note,
        createdAt = nowMillis,
    )

    /**
     * Lays routine jellies down on [dates]. Weeks before the current one are never filled.
     * Earlier days of the current week are filled, so that what was missed can roll into the tray.
     */
    fun materialize(
        data: AppData,
        dates: Collection<LocalDate>,
        today: LocalDate,
        nowMillis: Long,
        newId: () -> String,
    ): AppData {
        if (data.routines.isEmpty()) return data
        val floor = weekStart(today, data.settings.weekStartsOnSunday)
        val keys = data.materialized.toMutableSet()
        val created = ArrayList<Jelly>()
        for (date in dates.toSortedSet()) {
            if (date.isBefore(floor)) continue
            for (routine in data.routines) {
                if (!routine.appliesTo(date)) continue
                if (!keys.add(materializeKey(routine.id, date))) continue
                created += instanceOf(routine, date, nowMillis, newId())
            }
        }
        if (created.isEmpty()) return data
        var result = data.copy(jellies = data.jellies + created, materialized = keys)
        if (result.settings.followAlarm) {
            for (date in created.mapNotNull { it.date }.toSortedSet()) {
                val wake = result.wakeOn(date) ?: continue
                result = alignAnchored(result, date, wake.minute)
            }
        }
        return result
    }

    /**
     * Puts the wake-anchored jellies of [date] right after the alarm, keeping the spacing
     * their routines were given. The earliest one starts at alarm + wake gap.
     */
    fun alignAnchored(data: AppData, date: LocalDate, wakeMinute: Int): AppData {
        val anchored = data.jellies.filter {
            it.date == date && it.wakeAnchored && it.status == JellyStatus.PLANNED && it.startMin != null
        }
        if (anchored.isEmpty()) return data
        val base = anchored.minOf { it.anchorOrder }
        val first = wakeMinute + data.settings.wakeGapMin
        val starts = anchored.associate { it.id to clampStart(first + (it.anchorOrder - base), it.durationMin) }
        if (anchored.all { starts[it.id] == it.startMin }) return data
        return data.copy(jellies = data.jellies.map { j -> starts[j.id]?.let { j.copy(startMin = it) } ?: j })
    }

    /** Past days are over: unfinished jellies drop into the tray (or stay behind as missed). */
    fun rollover(data: AppData, today: LocalDate): AppData {
        var changed = false
        val jellies = data.jellies.map { j ->
            val date = j.date
            val start = j.startMin
            if (date == null || start == null || !date.isBefore(today) || j.status != JellyStatus.PLANNED) {
                j
            } else {
                changed = true
                if (j.carryOver) {
                    j.copy(
                        date = null,
                        startMin = null,
                        missedFrom = date,
                        missedFromStart = start,
                        wakeAnchored = false,
                        detached = true,
                    )
                } else {
                    j.copy(status = JellyStatus.MISSED)
                }
            }
        }
        return if (changed) data.copy(jellies = jellies) else data
    }

    fun prune(data: AppData, today: LocalDate): AppData {
        val keyFloor = weekStart(today, data.settings.weekStartsOnSunday).minusDays(7)
        val keys = data.materialized.filterTo(HashSet()) { key ->
            keyDate(key)?.let { !it.isBefore(keyFloor) } ?: false
        }
        val wakeFloor = today.minusDays(21)
        val wakes = data.wakes.filter { !it.date.isBefore(wakeFloor) }
        if (keys.size == data.materialized.size && wakes.size == data.wakes.size) return data
        return data.copy(materialized = keys, wakes = wakes)
    }

    /** Everything that has to happen when the calendar is opened or the day changes. */
    fun refresh(
        data: AppData,
        visible: Collection<LocalDate>,
        today: LocalDate,
        nowMillis: Long,
        newId: () -> String,
    ): AppData {
        val thisWeek = weekDays(weekStart(today, data.settings.weekStartsOnSunday))
        var result = materialize(data, visible + thisWeek, today, nowMillis, newId)
        result = rollover(result, today)
        result = prune(result, today)
        return result
    }

    // ------------------------------------------------------------- mutations

    private inline fun AppData.updateJelly(id: String, transform: (Jelly) -> Jelly): AppData {
        var changed = false
        val list = jellies.map { j ->
            if (j.id != id) {
                j
            } else {
                val next = transform(j)
                if (next != j) changed = true
                next
            }
        }
        return if (changed) copy(jellies = list) else this
    }

    /** Moves a jelly to [date] at [startMin], or into the tray when either is null. */
    fun move(data: AppData, id: String, date: LocalDate?, startMin: Int?): AppData =
        data.updateJelly(id) { j ->
            if (date == null || startMin == null) {
                if (j.isInTray) {
                    j
                } else {
                    j.copy(
                        date = null,
                        startMin = null,
                        status = JellyStatus.PLANNED,
                        completedAt = null,
                        wakeAnchored = false,
                        detached = true,
                    )
                }
            } else {
                val start = clampStart(startMin, j.durationMin)
                if (j.date == date && j.startMin == start) {
                    j
                } else {
                    j.copy(
                        date = date,
                        startMin = start,
                        status = if (j.status == JellyStatus.MISSED) JellyStatus.PLANNED else j.status,
                        wakeAnchored = false,
                        detached = true,
                    )
                }
            }
        }

    fun resize(data: AppData, id: String, durationMin: Int): AppData =
        data.updateJelly(id) { j ->
            val duration = clampDuration(durationMin, j.startMin)
            if (duration == j.durationMin) j else j.copy(durationMin = duration, detached = true)
        }

    /** Done jellies from the tray are logged on today, ending now. */
    fun toggleDone(data: AppData, id: String, now: LocalDateTime, nowMillis: Long): AppData =
        data.updateJelly(id) { j ->
            when {
                j.status == JellyStatus.DONE -> j.copy(status = JellyStatus.PLANNED, completedAt = null)
                j.isInTray -> {
                    val nowMin = now.hour * 60 + now.minute
                    j.copy(
                        status = JellyStatus.DONE,
                        completedAt = nowMillis,
                        date = now.toLocalDate(),
                        startMin = clampStart(nowMin - j.durationMin, j.durationMin),
                    )
                }
                else -> j.copy(status = JellyStatus.DONE, completedAt = nowMillis)
            }
        }

    fun upsertJelly(data: AppData, jelly: Jelly): AppData {
        val fixed = jelly.copy(
            durationMin = clampDuration(jelly.durationMin, jelly.startMin),
            startMin = jelly.startMin?.let { clampStart(it, jelly.durationMin) },
        )
        return if (data.jellies.any { it.id == fixed.id }) {
            data.copy(jellies = data.jellies.map { if (it.id == fixed.id) fixed else it })
        } else {
            data.copy(jellies = data.jellies + fixed)
        }
    }

    fun deleteJelly(data: AppData, id: String): AppData =
        data.copy(jellies = data.jellies.filterNot { it.id == id })

    fun clearTray(data: AppData): AppData = data.copy(jellies = data.jellies.filterNot { it.isInTray })

    /**
     * Saves a routine. Its untouched jellies from [today] on are removed so that the next
     * [materialize] lays them down again with the new settings.
     */
    fun saveRoutine(data: AppData, routine: Routine, today: LocalDate): AppData {
        val exists = data.routines.any { it.id == routine.id }
        val routines = if (exists) {
            data.routines.map { if (it.id == routine.id) routine else it }
        } else {
            data.routines + routine
        }
        val (stale, keep) = data.jellies.partition { isReplaceable(it, routine.id, today) }
        val staleKeys = stale.mapNotNull { j -> j.date?.let { materializeKey(routine.id, it) } }.toSet()
        return data.copy(routines = routines, jellies = keep, materialized = data.materialized - staleKeys)
    }

    fun deleteRoutine(data: AppData, routineId: String, today: LocalDate): AppData {
        val prefix = "$routineId@"
        return data.copy(
            routines = data.routines.filterNot { it.id == routineId },
            jellies = data.jellies.filterNot { isReplaceable(it, routineId, today) },
            materialized = data.materialized.filterNotTo(HashSet()) { it.startsWith(prefix) },
        )
    }

    private fun isReplaceable(j: Jelly, routineId: String, today: LocalDate): Boolean {
        val date = j.date ?: return false
        return j.routineId == routineId && !j.detached && j.status == JellyStatus.PLANNED && !date.isBefore(today)
    }

    /** Links an existing scheduled jelly to a new routine so it counts as that day's copy. */
    fun linkToRoutine(data: AppData, jellyId: String, routine: Routine): AppData {
        val jelly = data.jelly(jellyId) ?: return data
        val date = jelly.date ?: return data
        if (!routine.appliesTo(date)) return data
        val linked = jelly.copy(
            routineId = routine.id,
            anchorOrder = routine.startMin,
            wakeAnchored = routine.wakeAnchored,
            carryOver = routine.carryOver,
            detached = false,
        )
        return data.copy(
            jellies = data.jellies.map { if (it.id == jellyId) linked else it },
            materialized = data.materialized + materializeKey(routine.id, date),
        )
    }

    fun seed(data: AppData, routine: Routine): AppData =
        if (data.seeded) data else data.copy(seeded = true, routines = data.routines + routine)
}
