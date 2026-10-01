package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoldenJellyTest {
    @Test
    fun grabsInARowCount() {
        val streak = GrabStreak()
        var t = 1_000L
        repeat(GOLDEN_GRABS - 1) {
            streak.grabbed("a", t)
            t += 800
        }
        assertEquals(GOLDEN_GRABS - 1, streak.count)
        assertEquals(GOLDEN_GRABS, streak.grabbed("a", t))
    }

    @Test
    fun aPauseOrAnotherJellyStartsOver() {
        val streak = GrabStreak()
        streak.grabbed("a", 0)
        streak.grabbed("a", 1_000)
        assertEquals(1, streak.grabbed("a", 1_000 + GOLDEN_PATIENCE_MS + 1))
        streak.grabbed("a", 5_000)
        assertEquals(1, streak.grabbed("b", 5_500))
        assertEquals(1, streak.grabbed("a", 6_000))
    }

    @Test
    fun glitterStartsAtTheHint() {
        val streak = GrabStreak()
        var t = 0L
        repeat(GOLDEN_HINT - 1) { streak.grabbed("a", t); t += 500 }
        assertEquals(0f, streak.glitter("a"))
        streak.grabbed("a", t)
        assertTrue(streak.glitter("a") > 0f)
        assertEquals(0f, streak.glitter("b"))
        repeat(GOLDEN_GRABS) { t += 500; streak.grabbed("a", t) }
        assertEquals(1f, streak.glitter("a"))
    }

    @Test
    fun codesAreReadable() {
        repeat(200) {
            val code = newGoldenCode()
            assertTrue(Regex("^GOLD-[2-9A-HJKMNP-TV-Z]{4}-[2-9A-HJKMNP-TV-Z]{4}$").matches(code), code)
        }
    }

    @Test
    fun theFirstFindKeepsItsCode() {
        val now = LocalDateTime.of(2026, 10, 1, 15, 12)
        val store = JellyStore(AppData(), clock = { now }, idFactory = { "j" })
        val jelly = store.newJelly("보고서", 6, 60, now.toLocalDate(), 16 * 60)
        val first = store.makeGolden(jelly.id) { "GOLD-AAAA-BBBB" }
        assertEquals("GOLD-AAAA-BBBB", first.code)
        assertEquals(GOLDEN_FLAVOR, store.current.jelly(jelly.id)?.flavor)
        assertEquals(true, store.goldenNews.value?.first)

        store.dismissGoldenNews()
        assertNull(store.goldenNews.value)
        // Found once more, on a shared jelly: the code stays, and nothing of mine changes colour.
        val again = store.makeGolden(null) { "GOLD-CCCC-DDDD" }
        assertEquals(first, again)
        assertEquals(false, store.goldenNews.value?.first)
        assertEquals(first, store.current.golden)
    }

    @Test
    fun goldenFindSurvivesASave() {
        val data = AppData(golden = GoldenFind("GOLD-AAAA-BBBB", 1_790_000_000_000L))
        val back = JellyCodec.decode(JellyCodec.encode(data))
        assertNotNull(back.golden)
        assertEquals(data, back)
        assertFalse(JellyCodec.encode(AppData()).contains("golden"))
    }
}
