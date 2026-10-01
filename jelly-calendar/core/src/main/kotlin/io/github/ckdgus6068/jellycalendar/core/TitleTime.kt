package io.github.ckdgus6068.jellycalendar.core

/**
 * A time of day written in a jelly's title, the way people write it in Korean: "11시 미용실",
 * "오후 3시 반 회의", "저녁 7시 30분 영화", "19:30 영화". The shared page reads titles with the same
 * rules (docs/share/titletime.js), and both are tested with the same sentences.
 *
 * - The first time in the title counts. "1시간" is a length, not a time.
 * - 오전/오후, 아침/점심/낮/저녁/밤/새벽 right before the hour say which half of the day it is.
 * - Without them, 1–6시 is the afternoon and 7–11시 the morning, unless the title says otherwise
 *   somewhere else (저녁 약속 7시 → 19:00, 5시 기상 → 05:00).
 */
object TitleTime {
    /** [minute] after midnight, read from the words [text] ("오후 3시 반"). */
    data class Said(val minute: Int, val text: String)

    private const val PART = "(오전|오후|아침|점심|낮|저녁|밤|새벽)"
    private val KOREAN = Regex("""(^|[^\d])(?:$PART\s*)?(\d{1,2})\s*시(?!간)(?:\s*(반)|\s*(\d{1,2})\s*분)?""")
    private val CLOCK = Regex("""(^|[^\d:])(?:$PART\s*)?(\d{1,2}):([0-5]\d)(?![\d:])""")
    private val AM_HINTS = listOf("오전", "아침", "새벽", "기상")
    private val PM_HINTS = listOf("오후", "저녁", "밤")

    /** The first time of day in [title], or null when it has none. */
    fun find(title: String): Said? {
        if (title.isEmpty()) return null
        val k = KOREAN.find(title)
        val c = CLOCK.find(title)
        fun at(m: MatchResult?) = if (m == null) Int.MAX_VALUE else m.range.first + m.groupValues[1].length
        val match: MatchResult
        val hour: Int?
        val minute: Int
        when {
            k == null && c == null -> return null
            at(k) <= at(c) -> {
                match = k!!
                hour = hourOf(k.groupValues[2], k.groupValues[3].toInt(), title)
                minute = when {
                    k.groupValues[4].isNotEmpty() -> 30
                    k.groupValues[5].isNotEmpty() -> k.groupValues[5].toInt()
                    else -> 0
                }
            }
            else -> {
                match = c!!
                hour = hourOf(c.groupValues[2], c.groupValues[3].toInt(), title)
                minute = c.groupValues[4].toInt()
            }
        }
        if (hour == null || minute > 59) return null
        return Said(hour * 60 + minute, match.value.substring(match.groupValues[1].length).trim())
    }

    /** The hour (0–23) meant by [h] o'clock with [part] before it, in [title]; null when unclear. */
    private fun hourOf(part: String, h: Int, title: String): Int? {
        if (h >= 24) return null
        return when (part) {
            "오전", "아침", "새벽" -> if (h == 12) 0 else h
            "오후" -> if (h < 12) h + 12 else h
            "저녁", "밤" -> when {
                h == 12 -> null
                h < 12 -> h + 12
                else -> h
            }
            "점심", "낮" -> if (h in 1..6) h + 12 else h
            else -> when {
                h == 0 || h >= 12 -> h
                AM_HINTS.any { title.contains(it) } -> h
                PM_HINTS.any { title.contains(it) } -> h + 12
                h <= 6 -> h + 12
                else -> h
            }
        }
    }
}
