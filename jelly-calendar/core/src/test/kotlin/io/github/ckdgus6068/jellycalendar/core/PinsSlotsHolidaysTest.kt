package io.github.ckdgus6068.jellycalendar.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinsSlotsHolidaysTest {

    private val day = LocalDate.of(2026, 10, 7)

    private fun jelly(id: String, start: Int, duration: Int = 60, pinned: Boolean = false, date: LocalDate? = day) =
        Jelly(id = id, title = id, durationMin = duration, date = date, startMin = start.takeIf { date != null }, pinned = pinned)

    @Test
    fun aDayHoldsAtMostThreePinnedJellies() {
        var data = AppData(jellies = (1..4).map { jelly("j$it", it * 120) })
        for (id in listOf("j1", "j2", "j3")) data = Planner.setPinned(data, id, true)
        assertEquals(3, Planner.pinnedOn(data, day).size)
        assertFalse(Planner.canPin(data, day))
        assertTrue(Planner.canPin(data, day, "j2"), "a pinned jelly may stay pinned")
        data = Planner.setPinned(data, "j4", true)
        assertFalse(data.jelly("j4")!!.pinned, "the fourth pin is refused")
        data = Planner.setPinned(data, "j1", false)
        data = Planner.setPinned(data, "j4", true)
        assertTrue(data.jelly("j4")!!.pinned)
    }

    @Test
    fun aPinnedJellyMovedOntoAFullDayLetsGo() {
        val other = day.plusDays(1)
        var data = AppData(jellies = (1..3).map { jelly("j$it", it * 120, pinned = true) } + jelly("x", 60, pinned = true, date = other))
        data = Planner.move(data, "x", day, 20 * 60)
        assertFalse(data.jelly("x")!!.pinned)
        assertEquals(setOf("j1", "j2", "j3"), Planner.pinnedOn(data, day).map { it.id }.toSet())
    }

    @Test
    fun goingToTheTrayUnpins() {
        var data = AppData(jellies = listOf(jelly("j1", 600, pinned = true)))
        data = Planner.move(data, "j1", null, null)
        assertFalse(data.jelly("j1")!!.pinned)
        val saved = Planner.upsertJelly(data, data.jelly("j1")!!.copy(pinned = true))
        assertFalse(saved.jelly("j1")!!.pinned, "a jelly in the tray cannot be pinned")
    }

    @Test
    fun freeSlotsSkipBusyTimeAndSpreadOut() {
        val data = AppData(jellies = listOf(jelly("a", 9 * 60, 60), jelly("b", 10 * 60 + 30, 90)))
        val slots = Planner.freeSlots(data, day, 60, from = 9 * 60)
        // 9:00-10:00 and 10:30-12:00 are taken: first fit 12:00, then two hours later each time.
        assertEquals(listOf(12 * 60, 14 * 60, 16 * 60), slots)
        // Without b, 10:00 is free right after a.
        assertEquals(listOf(10 * 60, 12 * 60), Planner.freeSlots(data, day, 30, from = 10 * 60, count = 2, excludeId = "b"))
        assertEquals(listOf(21 * 60), Planner.freeSlots(AppData(), day, 120, from = 21 * 60))
        assertTrue(Planner.freeSlots(AppData(), day, 120, from = 23 * 60).isEmpty())
    }

    @Test
    fun monthGridsStartOnSundayByDefault() {
        assertTrue(Settings().weekStartsOnSunday)
        val grid = Planner.monthGrid(day, sundayFirst = true)
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 9, 27), grid.first())
        assertEquals(DayOfWeek.SUNDAY, grid.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 28), Planner.monthGrid(day, sundayFirst = false).first())
    }

    @Test
    fun oldSavesWithoutTheWeekSettingStartOnSunday() {
        val old = JellyCodec.decode("""{"settings":{"guideSeen":true}}""")
        assertTrue(old.settings.weekStartsOnSunday)
        val monday = JellyCodec.decode(JellyCodec.encode(AppData(settings = Settings(weekStartsOnSunday = false))))
        assertFalse(monday.settings.weekStartsOnSunday)
        val pinned = JellyCodec.decode(JellyCodec.encode(AppData(jellies = listOf(jelly("p", 600, pinned = true)))))
        assertTrue(pinned.jellies.single().pinned)
    }

    @Test
    fun koreanHolidaysIncludeSubstituteAndNewDays() {
        assertEquals("개천절", KoreanHolidays.on(LocalDate.of(2026, 10, 3))?.name)
        assertEquals("대체공휴일", KoreanHolidays.on(LocalDate.of(2026, 10, 5))?.name)
        assertEquals("한글날", KoreanHolidays.on(LocalDate.of(2026, 10, 9))?.name)
        assertEquals("추석", KoreanHolidays.on(LocalDate.of(2026, 9, 25))?.name)
        assertEquals("설날", KoreanHolidays.on(LocalDate.of(2026, 2, 17))?.name)
        assertEquals("노동절", KoreanHolidays.on(LocalDate.of(2026, 5, 1))?.name)
        assertEquals("제헌절", KoreanHolidays.on(LocalDate.of(2026, 7, 17))?.name)
        assertEquals("성탄절", KoreanHolidays.on(LocalDate.of(2026, 12, 25))?.short)
        assertEquals("대체휴일", KoreanHolidays.on(LocalDate.of(2026, 10, 5))?.short)
        assertNull(KoreanHolidays.on(LocalDate.of(2026, 10, 7)))
    }

    @Test
    fun isFreeChecksOverlapsOnly() {
        val day = LocalDate.of(2026, 10, 3)
        val data = AppData(
            jellies = listOf(
                Jelly(id = "a", title = "a", durationMin = 60, date = day, startMin = 10 * 60),
                Jelly(id = "b", title = "b", durationMin = 30, date = day, startMin = 14 * 60, status = JellyStatus.MISSED),
            ),
        )
        assertTrue(Planner.isFree(data, day, 9 * 60, 60))
        assertFalse(Planner.isFree(data, day, 9 * 60 + 30, 60))
        assertTrue(Planner.isFree(data, day, 11 * 60, 30))
        // A missed jelly does not hold its time, and a jelly does not get in its own way.
        assertTrue(Planner.isFree(data, day, 14 * 60, 30))
        assertTrue(Planner.isFree(data, day, 10 * 60, 60, excludeId = "a"))
        // Past midnight is fine, as long as the next morning is clear.
        assertTrue(Planner.isFree(data, day, 23 * 60 + 30, 60))
    }

    @Test
    fun isFreeLooksPastMidnightBothWays() {
        val day = LocalDate.of(2026, 10, 3)
        val data = AppData(
            jellies = listOf(
                // Last night's shift runs into this morning until 08:00.
                Jelly(id = "night", title = "야간", durationMin = 10 * 60, date = day.minusDays(1), startMin = 22 * 60),
                // Something early the next morning.
                Jelly(id = "early", title = "새벽", durationMin = 30, date = day.plusDays(1), startMin = 0),
            ),
        )
        assertFalse(Planner.isFree(data, day, 7 * 60, 60))
        assertTrue(Planner.isFree(data, day, 8 * 60, 30))
        assertFalse(Planner.isFree(data, day, 23 * 60 + 30, 60))
        assertTrue(Planner.isFree(data, day, 23 * 60, 60))
        // A 24-hour duty from 09:00 runs into the next morning's jelly.
        assertFalse(Planner.isFree(data, day, 9 * 60, 24 * 60))
        assertEquals(listOf(9 * 60), Planner.freeSlots(AppData(), day, 24 * 60, from = 9 * 60, count = 1))
    }
}
