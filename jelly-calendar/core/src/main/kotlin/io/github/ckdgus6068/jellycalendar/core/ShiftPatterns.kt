package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDate

/**
 * Rotations of shift work, laid down as repeating jellies that come back every few days: 주야휴비
 * (a day shift, a night shift, then two days off, over four days) and a 24-hour duty every few
 * days. Days off stay empty. Each shift becomes an ordinary repeating jelly, to be changed later.
 */
object ShiftPatterns {
    /** A shift [offset] days into the cycle. Flavours: 2 yellow, 6 periwinkle, 1 peach. */
    data class Shift(val title: String, val flavor: Int, val startMin: Int, val durationMin: Int, val offset: Int)

    /** [name], a [cycle] of days, the [shifts] in it, and what each day of the cycle is called. */
    data class Pattern(val name: String, val cycle: Int, val shifts: List<Shift>, val dayNames: List<String>)

    /** 주간 09:00–18:00, 야간 18:00–다음 날 09:00, 비번, 휴무. */
    val DAY_NIGHT_OFF = Pattern(
        name = "주야휴비",
        cycle = 4,
        shifts = listOf(
            Shift("주간 근무", 2, 9 * 60, 9 * 60, 0),
            Shift("야간 근무", 6, 18 * 60, 15 * 60, 1),
        ),
        dayNames = listOf("주간", "야간", "비번", "휴무"),
    )

    /** A 24-hour duty from 09:00, every [every] days (2–30). */
    fun duty(every: Int): Pattern {
        val cycle = every.coerceIn(2, 30)
        return Pattern(
            name = "당직",
            cycle = cycle,
            shifts = listOf(Shift("당직", 1, 9 * 60, 24 * 60, 0)),
            dayNames = listOf("당직") + List(cycle - 1) { "" },
        )
    }

    /**
     * The repeating jellies of [pattern] whose first day is [firstDay]. They start from that day (or
     * from [today] when it has passed), and a missed shift stays on its day instead of going to the tray.
     */
    fun routines(pattern: Pattern, firstDay: LocalDate, today: LocalDate, newId: () -> String): List<Routine> =
        pattern.shifts.map { shift ->
            Routine(
                id = newId(),
                title = shift.title,
                flavor = shift.flavor,
                durationMin = shift.durationMin,
                startMin = shift.startMin,
                days = ALL_DAYS,
                carryOver = false,
                since = maxOf(today, firstDay),
                everyDays = pattern.cycle,
                cycleStart = firstDay.plusDays(shift.offset.toLong()),
            )
        }
}
