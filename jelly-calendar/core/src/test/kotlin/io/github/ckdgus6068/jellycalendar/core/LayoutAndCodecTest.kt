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
    fun characterIsKept() {
        val data = AppData(settings = Settings(look = Look("police", 1), lookOnMine = true))
        assertEquals(data, JellyCodec.decode(JellyCodec.encode(data)))
        // A plain jelly is a choice of its own, kept apart from "not picked yet".
        val plain = AppData(settings = Settings(look = Look.PLAIN))
        assertEquals(Look.PLAIN, JellyCodec.decode(JellyCodec.encode(plain)).settings.look)
        assertTrue(Look.PLAIN.plain)
        assertFalse(Look.FIRST.plain)
        assertEquals(null, JellyCodec.decode("{\"settings\":{\"wakeGapMin\":15}}").settings.look)
    }

    @Test
    fun everyJobHasTwoOutfitsAndAName() {
        assertEquals(21, LookBook.jobs.size)
        for (job in LookBook.jobs) {
            assertEquals(2, job.variants.size, job.id)
            assertTrue(job.name.isNotBlank(), job.id)
        }
        assertTrue(LookBook.variantFaces.all { it in LookBook.faces })
        assertEquals(LookBook.jobs.size, LookBook.jobs.map { it.id }.toSet().size)
    }

    @Test
    fun codecToleratesUnknownFields() {
        val text = """{"version":1,"jellies":[{"id":"a","title":"x","future":42}],"extra":true}"""
        val data = JellyCodec.decode(text)
        assertEquals("x", data.jellies.single().title)
        assertFalse(data.seeded)
    }

    @Test
    fun olderSettingsGetTheNewDefaults() {
        // Saved before the font choice and the how-to screen existed.
        val text = """{"settings":{"wakeGapMin":15,"hintDismissed":true}}"""
        val settings = JellyCodec.decode(text).settings
        assertEquals(15, settings.wakeGapMin)
        assertTrue(settings.hintDismissed)
        assertFalse(settings.boxHintDismissed)
        assertFalse(settings.guideSeen)
        assertEquals(FontChoice.NANUM_ROUND, settings.font)
    }

    @Test
    fun unknownFontFallsBackToDefault() {
        val text = """{"settings":{"font":"COMIC","guideSeen":true}}"""
        val settings = JellyCodec.decode(text).settings
        assertEquals(FontChoice.NANUM_ROUND, settings.font)
        assertTrue(settings.guideSeen)
    }

    @Test
    fun fontChoiceRoundTrips() {
        for (font in FontChoice.entries) {
            val data = AppData(settings = Settings(font = font, guideSeen = true, boxHintDismissed = true))
            assertEquals(data, JellyCodec.decode(JellyCodec.encode(data)))
        }
    }

    @Test
    fun earlierFontPicksAreKept() {
        // The fat round face is no longer the default, but a save that names it keeps it.
        val text = """{"settings":{"font":"ROUND"}}"""
        assertEquals(FontChoice.ROUND, JellyCodec.decode(text).settings.font)
    }

    @Test
    fun undoableMoveCanBeTakenBack() {
        val now = LocalDateTime.of(2026, 9, 28, 9, 0)
        val store = JellyStore(AppData(), clock = { now }, idFactory = { "new" })
        val jelly = store.newJelly("보고서", 6, 90, now.toLocalDate(), 14 * 60)
        val saturday = now.toLocalDate().plusDays(5)
        store.moveUndoable(jelly.id, saturday, 14 * 60)
        assertEquals(saturday, store.current.jelly(jelly.id)?.date)
        assertTrue(store.undo())
        assertEquals(now.toLocalDate(), store.current.jelly(jelly.id)?.date)
    }

    @Test
    fun plainMoveKeepsTheLastUndo() {
        val now = LocalDateTime.of(2026, 9, 28, 9, 0)
        var n = 0
        val store = JellyStore(AppData(), clock = { now }, idFactory = { "id${n++}" })
        val a = store.newJelly("빨래", 1, 30, now.toLocalDate(), 600)
        val b = store.newJelly("독서", 2, 40, now.toLocalDate(), 700)
        store.deleteJelly(a.id)
        store.move(b.id, now.toLocalDate(), 800)
        // The move did not replace the undo step: undo still brings the deleted jelly back.
        assertTrue(store.undo())
        assertEquals(2, store.current.jellies.size)
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
