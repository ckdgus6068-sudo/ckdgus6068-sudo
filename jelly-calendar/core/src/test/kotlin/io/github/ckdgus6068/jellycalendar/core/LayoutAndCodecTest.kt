package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutAndCodecTest {

    @Test
    fun separateJelliesGetFullWidth() {
        val placed = DayLayout.place(
            listOf(DayLayout.Item("a", 60, 120), DayLayout.Item("b", 120, 180)),
        )
        assertEquals(LanePlacement(0, 1), placed["a"])
        assertEquals(LanePlacement(0, 1), placed["b"])
    }

    @Test
    fun overlappingJelliesShareTheColumn() {
        val placed = DayLayout.place(
            listOf(
                DayLayout.Item("a", 60, 180),
                DayLayout.Item("b", 90, 120),
                DayLayout.Item("c", 130, 150),
                DayLayout.Item("d", 200, 230),
            ),
        )
        assertEquals(LanePlacement(0, 2), placed["a"])
        assertEquals(LanePlacement(1, 2), placed["b"])
        assertEquals(LanePlacement(1, 2), placed["c"])
        assertEquals(LanePlacement(0, 1), placed["d"])
    }

    @Test
    fun shortJelliesAreSpreadWithMinimumHeight() {
        val placed = DayLayout.place(
            listOf(DayLayout.Item("a", 60, 65), DayLayout.Item("b", 65, 70)),
            minVisualMinutes = 15,
        )
        assertEquals(2, placed.getValue("a").lanes)
    }

    @Test
    fun codecRoundTrip() {
        val monday = LocalDate.of(2026, 9, 28)
        val data = AppData(
            jellies = listOf(
                Jelly(id = "a", title = "러닝", date = monday, startMin = 360, status = JellyStatus.DONE, completedAt = 9L),
                Jelly(id = "b", title = "빨래", missedFrom = monday, missedFromStart = 600),
            ),
            routines = listOf(Routine(id = "r", title = "러닝", days = WEEKEND, since = monday, wakeAnchored = true)),
            materialized = setOf("r@$monday"),
            wakes = listOf(WakeRecord(monday, 350, "com.sec.android.app.clockpackage")),
            settings = Settings(wakeGapMin = 5, snapMin = 15),
            lastAlarmRequest = AlarmRequest(monday, 350, 1L),
            seeded = true,
        )
        val text = JellyCodec.encode(data)
        assertTrue(text.contains("\"2026-09-28\""))
        assertEquals(data, JellyCodec.decode(text))
    }

    @Test
    fun codecToleratesUnknownFields() {
        val text = """{"version":1,"jellies":[{"id":"a","title":"x","future":42}],"extra":true}"""
        val data = JellyCodec.decode(text)
        assertEquals("x", data.jellies.single().title)
        assertFalse(data.seeded)
    }

    @Test
    fun storeUndoRestoresDeletedJelly() {
        val now = LocalDateTime.of(2026, 9, 28, 9, 0)
        val store = JellyStore(AppData(), clock = { now }, idFactory = { "new" })
        val jelly = store.newJelly("빨래", 1, 30, now.toLocalDate(), 600)
        store.deleteJelly(jelly.id)
        assertTrue(store.current.jellies.isEmpty())
        assertTrue(store.undo())
        assertEquals(listOf(jelly), store.current.jellies)
        assertFalse(store.undo())
    }
}
