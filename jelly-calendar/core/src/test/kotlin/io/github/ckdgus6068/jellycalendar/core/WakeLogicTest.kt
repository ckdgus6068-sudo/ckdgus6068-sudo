package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WakeLogicTest {

    private val monday = LocalDate.of(2026, 9, 28)
    private val tuesday = monday.plusDays(1)
    private val sundayNight = LocalDateTime.of(2026, 9, 27, 22, 0)

    private var counter = 0
    private val ids: () -> String = { "id${counter++}" }

    private val run = Routine(
        id = "run",
        title = "아침 러닝",
        durationMin = 50,
        startMin = 6 * 60,
        wakeAnchored = true,
        since = monday,
    )

    private fun laid(settings: Settings = Settings()) =
        Planner.materialize(AppData(routines = listOf(run), settings = settings), listOf(monday, tuesday), monday, 0L, ids)

    @Test
    fun nextAlarmMovesAnchoredJelly() {
        val data = laid()
        val observed = WakeLogic.observe(data, NextAlarm(monday.atTime(5, 30), "com.sec.android.app.clockpackage"), sundayNight)
        val mondayRun = observed.jellies.single { it.date == monday }
        assertEquals(5 * 60 + 40, mondayRun.startMin)
        assertEquals(WakeRecord(monday, 330, "com.sec.android.app.clockpackage"), observed.wakeOn(monday))
        // Tuesday has no alarm yet: it keeps the routine time.
        assertEquals(360, observed.jellies.single { it.date == tuesday }.startMin)
    }

    @Test
    fun alarmOutsideWakeWindowIsIgnored() {
        val data = laid()
        val observed = WakeLogic.observe(data, NextAlarm(monday.atTime(14, 0), null), sundayNight)
        assertTrue(observed.wakes.isEmpty())
        assertEquals(data.jellies, observed.jellies)
    }

    @Test
    fun changedAlarmReplacesFutureRecord() {
        val first = WakeLogic.observe(laid(), NextAlarm(monday.atTime(5, 30), null), sundayNight)
        val changed = WakeLogic.observe(first, NextAlarm(monday.atTime(6, 0), null), sundayNight)
        assertEquals(360, changed.wakeOn(monday)?.minute)
        assertEquals(370, changed.jellies.single { it.date == monday }.startMin)
    }

    @Test
    fun deletedAlarmForgetsFutureRecord() {
        val first = WakeLogic.observe(laid(), NextAlarm(monday.atTime(5, 30), null), sundayNight)
        val gone = WakeLogic.observe(first, null, sundayNight)
        assertTrue(gone.wakes.isEmpty())
    }

    @Test
    fun laterAlarmOnSameDayDoesNotMoveMorning() {
        val woke = WakeLogic.observe(laid(), NextAlarm(monday.atTime(5, 30), null), sundayNight)
        // It is now 07:00 on Monday; the next alarm is a 10:00 reminder.
        val later = WakeLogic.observe(woke, NextAlarm(monday.atTime(10, 0), null), monday.atTime(7, 0))
        assertEquals(330, later.wakeOn(monday)?.minute)
    }

    @Test
    fun followAlarmOffOnlyRecords() {
        val data = laid(Settings(followAlarm = false))
        val observed = WakeLogic.observe(data, NextAlarm(monday.atTime(5, 0), null), sundayNight)
        assertEquals(300, observed.wakeOn(monday)?.minute)
        assertEquals(360, observed.jellies.single { it.date == monday }.startMin)
    }

    @Test
    fun oneTimeAlarmTargetsNextOccurrence() {
        assertTrue(WakeLogic.targetsDate(monday, 350, sundayNight))
        assertTrue(!WakeLogic.targetsDate(tuesday, 350, sundayNight))
        // At 07:00 on Monday a 05:50 alarm would ring on Tuesday.
        assertTrue(WakeLogic.targetsDate(tuesday, 350, monday.atTime(7, 0)))
    }

    @Test
    fun statusReportsSyncMismatchAndMissingAlarm() {
        val data = laid()
        val none = WakeLogic.status(data, monday, sundayNight)
        val noAlarm = assertIs<WakeStatus.NoAlarm>(none)
        assertEquals(350, noAlarm.suggestedAlarm)
        assertTrue(noAlarm.canSetNow)

        val synced = WakeLogic.observe(data, NextAlarm(monday.atTime(5, 50), null), sundayNight)
        assertIs<WakeStatus.Synced>(WakeLogic.status(synced, monday, sundayNight))

        val moved = Planner.move(synced, synced.jellies.single { it.date == monday }.id, monday, 7 * 60)
        val mismatch = assertIs<WakeStatus.Mismatch>(WakeLogic.status(moved, monday, sundayNight))
        assertEquals(410, mismatch.suggestedAlarm)
        assertEquals(350, mismatch.alarmMin)
    }

    @Test
    fun noWakeSuggestionOnceTheDayHasBegun() {
        val data = AppData(jellies = listOf(Jelly(id = "a", title = "팀 점심", date = monday, startMin = 12 * 60)))
        assertIs<WakeStatus.Empty>(WakeLogic.status(data, monday, monday.atTime(7, 20)))
        // At 01:00 the same morning is still ahead.
        val early = AppData(jellies = listOf(Jelly(id = "b", title = "러닝", date = monday, startMin = 6 * 60)))
        assertIs<WakeStatus.NoAlarm>(WakeLogic.status(early, monday, monday.atTime(1, 0)))
    }

    @Test
    fun afternoonFirstJellyGetsNoSuggestion() {
        val data = AppData(jellies = listOf(Jelly(id = "a", title = "회의", date = monday, startMin = 14 * 60)))
        assertIs<WakeStatus.Empty>(WakeLogic.status(data, monday, sundayNight))
    }

    @Test
    fun changingGapRealignsKnownDays() {
        val store = JellyStore(laid(), clock = { sundayNight }, idFactory = ids)
        store.observeAlarm(NextAlarm(monday.atTime(5, 30), null))
        store.updateSettings { it.copy(wakeGapMin = 0) }
        assertEquals(330, store.current.jellies.single { it.date == monday }.startMin)
    }
}
