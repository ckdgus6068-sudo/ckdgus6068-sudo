package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerTest {

    // 2026-09-28 is a Monday.
    private val monday = LocalDate.of(2026, 9, 28)
    private val wednesday = monday.plusDays(2)
    private val saturday = monday.plusDays(5)

    private var counter = 0
    private val ids: () -> String = { "id${counter++}" }

    private fun run(title: String = "아침 러닝", days: Set<Int> = ALL_DAYS, anchored: Boolean = false, since: LocalDate = monday) =
        Routine(
            id = "run",
            title = title,
            durationMin = 50,
            startMin = 6 * 60,
            days = days,
            wakeAnchored = anchored,
            since = since,
        )

    @Test
    fun weekStartsOnMondayOrSunday() {
        assertEquals(monday, Planner.weekStart(wednesday, sundayFirst = false))
        assertEquals(monday.minusDays(1), Planner.weekStart(wednesday, sundayFirst = true))
        assertEquals(monday, Planner.weekStart(monday, sundayFirst = false))
    }

    @Test
    fun snapRoundsToNearestStep() {
        assertEquals(60, Planner.snap(57, 10))
        assertEquals(50, Planner.snap(54, 10))
        assertEquals(55, Planner.snap(55, 5))
        assertEquals(0, Planner.snap(-3, 10))
    }

    @Test
    fun routinesAreLaidDownOncePerDay() {
        val data = AppData(routines = listOf(run()))
        val once = Planner.materialize(data, Planner.weekDays(monday), monday, 0L, ids)
        assertEquals(7, once.jellies.size)
        val twice = Planner.materialize(once, Planner.weekDays(monday), monday, 0L, ids)
        assertEquals(once, twice)
        assertTrue(once.jellies.all { it.startMin == 360 && it.routineId == "run" })
    }

    @Test
    fun deletedRoutineDayDoesNotComeBack() {
        val data = Planner.materialize(AppData(routines = listOf(run())), listOf(monday), monday, 0L, ids)
        val deleted = Planner.deleteJelly(data, data.jellies.single().id)
        val again = Planner.materialize(deleted, listOf(monday), monday, 0L, ids)
        assertTrue(again.jellies.isEmpty())
    }

    /** These tests count weeks from Monday; the app's default is Sunday. */
    private val mondayWeeks = Settings(weekStartsOnSunday = false)

    @Test
    fun pastWeeksAreNeverFilled() {
        val data = AppData(routines = listOf(run(since = monday.minusDays(30))), settings = mondayWeeks)
        val result = Planner.materialize(data, Planner.weekDays(monday.minusDays(7)), wednesday, 0L, ids)
        assertTrue(result.jellies.isEmpty())
    }

    @Test
    fun weekdayRoutineSkipsWeekend() {
        val data = AppData(routines = listOf(run(days = WEEKDAYS)))
        val result = Planner.materialize(data, Planner.weekDays(monday), monday, 0L, ids)
        assertEquals(5, result.jellies.size)
        assertTrue(result.jellies.none { it.date == saturday })
    }

    @Test
    fun missedJellyRollsIntoTrayAndLeavesMarker() {
        val data = Planner.materialize(AppData(routines = listOf(run())), listOf(monday), monday, 0L, ids)
        val rolled = Planner.rollover(data, wednesday)
        val jelly = rolled.jellies.single()
        assertTrue(jelly.isInTray)
        assertEquals(monday, jelly.missedFrom)
        assertEquals(360, jelly.missedFromStart)
        assertEquals(listOf(jelly), Planner.tray(rolled))
        assertEquals(listOf(jelly), Planner.ghostsOn(rolled, monday))
    }

    @Test
    fun doneJellyStaysOnItsDay() {
        val data = Planner.materialize(AppData(routines = listOf(run())), listOf(monday), monday, 0L, ids)
        val id = data.jellies.single().id
        val done = Planner.toggleDone(data, id, monday.atTime(7, 0), 1L)
        val rolled = Planner.rollover(done, wednesday)
        assertEquals(monday, rolled.jellies.single().date)
        assertEquals(JellyStatus.DONE, rolled.jellies.single().status)
    }

    @Test
    fun jellyWithoutCarryOverIsMarkedMissed() {
        val routine = run().copy(carryOver = false)
        val data = Planner.materialize(AppData(routines = listOf(routine)), listOf(monday), monday, 0L, ids)
        val rolled = Planner.rollover(data, wednesday)
        assertEquals(JellyStatus.MISSED, rolled.jellies.single().status)
        assertEquals(monday, rolled.jellies.single().date)
    }

    @Test
    fun refreshFillsEarlierDaysOfTheWeekAndRollsThem() {
        val data = AppData(routines = listOf(run(since = monday)), settings = mondayWeeks)
        val result = Planner.refresh(data, emptyList(), wednesday, 0L, ids)
        // Monday and Tuesday were never opened: they are laid down and rolled into the tray.
        assertEquals(2, Planner.tray(result).size)
        assertEquals(5, result.jellies.count { it.isScheduled })
    }

    @Test
    fun movingFromTrayToWeekendSchedulesIt() {
        val data = Planner.rollover(
            Planner.materialize(AppData(routines = listOf(run())), listOf(monday), monday, 0L, ids),
            wednesday,
        )
        val id = data.jellies.single().id
        val moved = Planner.move(data, id, saturday, 9 * 60)
        val jelly = moved.jellies.single()
        assertEquals(saturday, jelly.date)
        assertEquals(540, jelly.startMin)
        assertTrue(Planner.tray(moved).isEmpty())
        // The faint marker on Monday is still there.
        assertEquals(1, Planner.ghostsOn(moved, monday).size)
    }

    @Test
    fun movingToTrayResetsDoneAndAnchor() {
        val data = Planner.materialize(AppData(routines = listOf(run(anchored = true))), listOf(monday), monday, 0L, ids)
        val id = data.jellies.single().id
        val done = Planner.toggleDone(data, id, monday.atTime(7, 0), 1L)
        val tray = Planner.move(done, id, null, null).jellies.single()
        assertTrue(tray.isInTray)
        assertEquals(JellyStatus.PLANNED, tray.status)
        assertFalse(tray.wakeAnchored)
    }

    @Test
    fun trayJellyMarkedDoneIsLoggedOnToday() {
        val data = AppData(jellies = listOf(Jelly(id = "a", title = "책 읽기", durationMin = 30)))
        val now = LocalDateTime.of(2026, 9, 30, 21, 0)
        val done = Planner.toggleDone(data, "a", now, 5L).jellies.single()
        assertEquals(wednesday, done.date)
        assertEquals(20 * 60 + 30, done.startMin)
        assertEquals(JellyStatus.DONE, done.status)
    }

    @Test
    fun anchoredRoutinesFollowWakeTimeKeepingSpacing() {
        val runRoutine = run(anchored = true)
        val shower = Routine(
            id = "shower",
            title = "샤워",
            durationMin = 20,
            startMin = 7 * 60,
            wakeAnchored = true,
            since = monday,
        )
        val data = AppData(routines = listOf(runRoutine, shower), settings = Settings(wakeGapMin = 10))
        val laid = Planner.materialize(data, listOf(monday), monday, 0L, ids)
        val aligned = Planner.alignAnchored(laid, monday, 5 * 60 + 30)
        val byTitle = aligned.jellies.associateBy { it.title }
        assertEquals(5 * 60 + 40, byTitle.getValue("아침 러닝").startMin)
        assertEquals(6 * 60 + 40, byTitle.getValue("샤워").startMin)
    }

    @Test
    fun manualMoveStopsFollowingAlarm() {
        val data = Planner.materialize(AppData(routines = listOf(run(anchored = true))), listOf(monday), monday, 0L, ids)
        val id = data.jellies.single().id
        val moved = Planner.move(data, id, monday, 8 * 60)
        val aligned = Planner.alignAnchored(moved, monday, 5 * 60)
        assertEquals(480, aligned.jellies.single().startMin)
    }

    @Test
    fun editingRoutineRebuildsUntouchedFutureDaysOnly() {
        val data = Planner.materialize(AppData(routines = listOf(run())), Planner.weekDays(monday), monday, 0L, ids)
        val wednesdayJelly = data.jellies.first { it.date == wednesday }
        val touched = Planner.move(data, wednesdayJelly.id, wednesday, 9 * 60)
        val edited = Planner.saveRoutine(touched, run().copy(startMin = 5 * 60, durationMin = 30), monday)
        val rebuilt = Planner.materialize(edited, Planner.weekDays(monday), monday, 0L, ids)
        val wed = rebuilt.jellies.filter { it.date == wednesday }
        assertEquals(1, wed.size)
        assertEquals(540, wed.single().startMin)
        val thu = rebuilt.jellies.single { it.date == wednesday.plusDays(1) }
        assertEquals(300, thu.startMin)
        assertEquals(30, thu.durationMin)
    }

    @Test
    fun deletingRoutineKeepsHistory() {
        val data = Planner.materialize(AppData(routines = listOf(run())), Planner.weekDays(monday), wednesday, 0L, ids)
        val mondayJelly = data.jellies.first { it.date == monday }
        val done = Planner.toggleDone(data, mondayJelly.id, monday.atTime(7, 0), 1L)
        val deleted = Planner.deleteRoutine(done, "run", wednesday)
        assertTrue(deleted.routines.isEmpty())
        // Monday (done) and Tuesday (past) stay; Wednesday onwards is removed.
        assertEquals(setOf(monday, monday.plusDays(1)), deleted.jellies.mapNotNull { it.date }.toSet())
    }

    @Test
    fun freeSlotSkipsBusyTime() {
        val data = AppData(
            jellies = listOf(
                Jelly(id = "a", title = "a", date = monday, startMin = 540, durationMin = 60),
                Jelly(id = "b", title = "b", date = monday, startMin = 600, durationMin = 30),
            ),
        )
        assertEquals(630, Planner.findFreeSlot(data, monday, 50, 540))
        assertEquals(480, Planner.findFreeSlot(data, monday, 50, 480))
        assertEquals(700, Planner.findFreeSlot(data, monday, 50, 700))
    }

    @Test
    fun linkingJellyToNewRoutineAvoidsDuplicate() {
        val data = AppData(jellies = listOf(Jelly(id = "a", title = "요가", date = wednesday, startMin = 420)))
        val routine = Routine(id = "yoga", title = "요가", startMin = 420, since = wednesday)
        val saved = Planner.saveRoutine(data, routine, wednesday)
        val linked = Planner.linkToRoutine(saved, "a", routine)
        val laid = Planner.materialize(linked, Planner.weekDays(monday), wednesday, 0L, ids)
        assertEquals(1, laid.jellies.count { it.date == wednesday })
        assertEquals("yoga", laid.jellies.single { it.id == "a" }.routineId)
    }

    @Test
    fun pruneDropsOldBookkeeping() {
        val old = monday.minusDays(40)
        val data = AppData(
            materialized = setOf("run@$old", "run@$monday"),
            wakes = listOf(WakeRecord(old, 360), WakeRecord(monday, 360)),
        )
        val pruned = Planner.prune(data, wednesday)
        assertEquals(setOf("run@$monday"), pruned.materialized)
        assertEquals(listOf(WakeRecord(monday, 360)), pruned.wakes)
    }

    @Test
    fun resizeIsClampedToTheDay() {
        val data = AppData(jellies = listOf(Jelly(id = "a", title = "a", date = monday, startMin = 23 * 60)))
        assertEquals(60, Planner.resize(data, "a", 200).jellies.single().durationMin)
        assertEquals(Planner.MIN_DURATION, Planner.resize(data, "a", 1).jellies.single().durationMin)
    }

    @Test
    fun seedOnlyOnce() {
        val routine = run()
        val seeded = Planner.seed(AppData(), routine)
        assertTrue(seeded.seeded)
        assertEquals(1, seeded.routines.size)
        assertEquals(seeded, Planner.seed(seeded, routine.copy(id = "other")))
    }

    @Test
    fun firstOfDayIgnoresMissed() {
        val data = AppData(
            jellies = listOf(
                Jelly(id = "a", title = "a", date = monday, startMin = 300, status = JellyStatus.MISSED),
                Jelly(id = "b", title = "b", date = monday, startMin = 420),
            ),
        )
        assertEquals("b", assertNotNull(Planner.firstOfDay(data, monday)).id)
        assertNull(Planner.firstOfDay(data, wednesday))
    }
}
