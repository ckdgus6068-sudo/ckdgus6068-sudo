package io.github.ckdgus6068.jellycalendar.ui

import io.github.ckdgus6068.jellycalendar.core.ALL_DAYS
import io.github.ckdgus6068.jellycalendar.core.WEEKDAYS
import io.github.ckdgus6068.jellycalendar.core.WEEKEND
import java.time.LocalDate

private val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

fun hm(minute: Int): String {
    val m = minute.coerceIn(0, 24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}

/** "06:00로" or "06:50으로": the particle follows how the time is read aloud (시 or 분). */
fun hmTo(minute: Int): String = hm(minute) + if (minute % 60 == 0) "로" else "으로"

/** When a jelly ends: "10:30", or "다음 날 09:00" when it runs past midnight. */
fun endText(start: Int, duration: Int): String {
    val end = start + duration
    return if (end > 24 * 60) "다음 날 ${hm(end - 24 * 60)}" else hm(end)
}

/** "09:00–10:30", or "18:00–다음 날 09:00" for a night shift. */
fun range(start: Int, duration: Int): String = "${hm(start)}–${endText(start, duration)}"

private const val WORD_JOINER = '\u2060'

private fun isHangul(c: Char): Boolean = c in '\uAC00'..'\uD7A3' || c in '\u3131'..'\u318E'

/**
 * Glues each Korean word together so that lines wrap between words, the way Korean text is
 * read, instead of in the middle of a word. A word longer than the whole line still breaks.
 */
fun keepWords(text: String): String {
    if (text.length < 2) return text
    val out = StringBuilder(text.length + text.length / 2)
    for (i in text.indices) {
        val c = text[i]
        out.append(c)
        val next = text.getOrNull(i + 1) ?: continue
        if (c.isLetterOrDigit() && next.isLetterOrDigit() && (isHangul(c) || isHangul(next))) out.append(WORD_JOINER)
    }
    return out.toString()
}

fun durationText(minutes: Int): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0 -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

fun dayName(isoDay: Int): String = DAY_NAMES[(isoDay - 1).coerceIn(0, 6)]

fun dayName(date: LocalDate): String = dayName(date.dayOfWeek.value)

fun dateTitle(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일 ${dayName(date)}요일"

fun shortDate(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}(${dayName(date)})"

fun weekTitle(start: LocalDate): String {
    val end = start.plusDays(6)
    return if (start.monthValue == end.monthValue) {
        "${start.monthValue}월 ${start.dayOfMonth}일 – ${end.dayOfMonth}일"
    } else {
        "${start.monthValue}월 ${start.dayOfMonth}일 – ${end.monthValue}월 ${end.dayOfMonth}일"
    }
}

fun daysText(days: Set<Int>): String = when (days) {
    ALL_DAYS -> "매일"
    WEEKDAYS -> "평일"
    WEEKEND -> "주말"
    emptySet<Int>() -> "요일 없음"
    else -> days.sorted().joinToString("·") { dayName(it) }
}

/** "월 놓침", "지난주 놓침" or null for jellies that were never missed. */
fun missedText(missedFrom: LocalDate?, weekStart: LocalDate): String? {
    val date = missedFrom ?: return null
    return if (date.isBefore(weekStart)) "지난주 놓침" else "${dayName(date)} 놓침"
}

fun alarmSourceName(pkg: String?): String = when (pkg) {
    null -> "알 수 없는 앱"
    "com.sec.android.app.clockpackage" -> "삼성 시계"
    "com.google.android.deskclock" -> "Google 시계"
    else -> pkg
}
