package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CycleTest {

    private val thursday = LocalDate.of(2026, 10, 1)

    @Test
    fun everySixDaysFromItsDay() {
        val duty = Routine(id = "d", title = "당직", since = thursday, everyDays = 6, cycleStart = thursday.plusDays(2))
        val on = (0L until 20L).map { thursday.plusDays(it) }.filter { duty.appliesTo(it) }
        assertEquals(listOf(thursday.plusDays(2), thursday.plusDays(8), thursday.plusDays(14)), on)
        // Days of the week no longer matter.
        assertTrue(duty.inCycle)
        assertFalse(duty.copy(everyDays = 0).inCycle)
    }

    @Test
    fun theCycleRunsBothWaysButNotBeforeItsFirstDay() {
        // A cycle day set in the future still counts the days before it, from the routine's start on.
        val shift = Routine(id = "s", title = "s", since = thursday, everyDays = 4, cycleStart = thursday.plusDays(5))
        assertTrue(shift.appliesTo(thursday.plusDays(1)))
        assertFalse(shift.appliesTo(thursday.minusDays(3)))
    }

    @Test
    fun weekdayRoutinesAreUnchanged() {
        val weekdays = Routine(id = "w", title = "w", since = thursday, days = WEEKDAYS)
        assertTrue(weekdays.appliesTo(thursday))
        assertFalse(weekdays.appliesTo(thursday.plusDays(2)))
    }

    @Test
    fun dayNightOffOffLaysDownTwoShiftsEveryFourDays() {
        var n = 0
        val routines = ShiftPatterns.routines(ShiftPatterns.DAY_NIGHT_OFF, thursday, thursday) { "r${n++}" }
        assertEquals(listOf("주간 근무", "야간 근무"), routines.map { it.title })
        var data = AppData(routines = routines, seeded = true)
        val days = (0L until 8L).map { thursday.plusDays(it) }
        data = Planner.materialize(data, days, thursday, 0L) { "j${n++}" }
        val byDay = days.map { day -> Planner.scheduledOn(data, day).map { it.title } }
        assertEquals(
            listOf(
                listOf("주간 근무"), listOf("야간 근무"), emptyList(), emptyList(),
                listOf("주간 근무"), listOf("야간 근무"), emptyList(), emptyList(),
            ),
            byDay,
        )
        // The night shift runs to 09:00 the next morning, and a missed shift stays on its day.
        val night = Planner.scheduledOn(data, thursday.plusDays(1)).single()
        assertEquals(18 * 60, night.startMin)
        assertTrue(night.overnight)
        assertFalse(night.carryOver)
    }

    @Test
    fun aDutyEverySixDaysLastsADay() {
        var n = 0
        val duty = ShiftPatterns.routines(ShiftPatterns.duty(6), thursday.plusDays(1), thursday) { "r${n++}" }.single()
        assertEquals(24 * 60, duty.durationMin)
        assertEquals(thursday.plusDays(1), duty.since)
        assertTrue(duty.appliesTo(thursday.plusDays(7)))
        assertFalse(duty.appliesTo(thursday))
    }

    @Test
    fun oldRoutinesStillLoad() {
        // Saved before cycles existed: no everyDays, no cycleStart.
        val json = "{\"jellies\":[],\"routines\":[{\"id\":\"r\",\"title\":\"아침 러닝\",\"since\":\"2026-09-28\"}],\"seeded\":true}"
        val data = JellyCodec.decode(json)
        assertEquals(0, data.routines.single().everyDays)
        assertTrue(data.routines.single().appliesTo(thursday))
    }
}
