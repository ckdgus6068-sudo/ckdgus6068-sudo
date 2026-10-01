@file:UseSerializers(LocalDateSerializer::class)

package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val MINUTES_PER_DAY = 24 * 60

/** Every ISO day-of-week number, Monday (1) through Sunday (7). */
val ALL_DAYS: Set<Int> = (1..7).toSet()
val WEEKDAYS: Set<Int> = (1..5).toSet()
val WEEKEND: Set<Int> = setOf(6, 7)

@Serializable
enum class JellyStatus { PLANNED, DONE, MISSED }

/**
 * The lettering of jellies and headings: NANUM_ROUND (동글, NanumSquareRound), JUA (말랑, Jua),
 * CLEAN (깔끔, Pretendard), ROUND (통통, Bagel Fat One), or SYSTEM, whatever the phone uses.
 */
@Serializable
enum class FontChoice { ROUND, CLEAN, SYSTEM, NANUM_ROUND, JUA }

/**
 * One block of time ("jelly").
 *
 * A jelly with no [date] or no [startMin] lives in the tray at the bottom of the screen:
 * that is where unfinished jellies go so they can be dropped into another day.
 */
@Serializable
data class Jelly(
    val id: String,
    val title: String,
    val flavor: Int = 0,
    val durationMin: Int = 50,
    val date: LocalDate? = null,
    val startMin: Int? = null,
    val status: JellyStatus = JellyStatus.PLANNED,
    /** Set when the jelly was generated from a [Routine]. */
    val routineId: String? = null,
    /** Follows the wake-up alarm: starts at alarm time + [Settings.wakeGapMin]. */
    val wakeAnchored: Boolean = false,
    /** Routine default start, used to keep anchored jellies in their intended order and spacing. */
    val anchorOrder: Int = 0,
    /** True once the user changed this routine instance by hand; routine edits then leave it alone. */
    val detached: Boolean = false,
    /** When the day passes unfinished: true moves it to the tray, false marks it [JellyStatus.MISSED]. */
    val carryOver: Boolean = true,
    /** The day this jelly was last missed on, for the faint marker left behind on that day. */
    val missedFrom: LocalDate? = null,
    val missedFromStart: Int? = null,
    val note: String = "",
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    /** Pinned ("젤위로 고정"): held at the top of its day's box, at most [Planner.MAX_PINNED] a day. */
    val pinned: Boolean = false,
) {
    val isScheduled: Boolean get() = date != null && startMin != null
    val isInTray: Boolean get() = !isScheduled
    val isDone: Boolean get() = status == JellyStatus.DONE
    val endMin: Int? get() = startMin?.let { it + durationMin }
}

/** A jelly that is laid down again and again on chosen days of the week. */
@Serializable
data class Routine(
    val id: String,
    val title: String,
    val flavor: Int = 0,
    val durationMin: Int = 50,
    val startMin: Int = 7 * 60,
    /** ISO day-of-week numbers, 1 = Monday ... 7 = Sunday. */
    val days: Set<Int> = ALL_DAYS,
    val wakeAnchored: Boolean = false,
    val carryOver: Boolean = true,
    val active: Boolean = true,
    val since: LocalDate,
    val note: String = "",
) {
    fun appliesTo(date: LocalDate): Boolean =
        active && !date.isBefore(since) && date.dayOfWeek.value in days
}

@Serializable
data class Settings(
    /** Move wake-anchored jellies so they start right after the next alarm. */
    val followAlarm: Boolean = true,
    /** Only alarms inside this window count as a wake-up alarm. */
    val wakeWindowStartMin: Int = 3 * 60,
    val wakeWindowEndMin: Int = 12 * 60,
    /** Minutes between the alarm and the first jelly. Used in both directions. */
    val wakeGapMin: Int = 10,
    /** Create the alarm silently instead of showing the clock app's confirmation screen. */
    val alarmSkipUi: Boolean = true,
    /** After a new wake-up alarm was added, ask the clock app to switch off the one it replaces. */
    val autoDismissOld: Boolean = true,
    val snapMin: Int = 10,
    val idleWobble: Boolean = true,
    val doneByDoubleTap: Boolean = true,
    val doneByLongPress: Boolean = true,
    /** Weeks (and month grids) start on Sunday, as Korean wall calendars do. */
    val weekStartsOnSunday: Boolean = true,
    val hintDismissed: Boolean = false,
    val boxHintDismissed: Boolean = false,
    /** The how-to screen opens by itself once, on the first launch that has it. */
    val guideSeen: Boolean = false,
    val font: FontChoice = FontChoice.NANUM_ROUND,
    /**
     * My jelly character, the same in every shared calendar. Null until one is picked here: the
     * shared page then hands over the one picked there, if any (else it is the smiling face).
     */
    val look: Look? = null,
    /** My own jellies wear my character too, not only the ones I put up in a shared calendar. */
    val lookOnMine: Boolean = false,
)

/**
 * A jelly character: a job of [LookBook] ("face" is just a face) in one of its two outfits [v].
 * Any other job is a plain jelly. The shared page keeps the same in people's profiles.
 */
@Serializable
data class Look(val job: String, val v: Int = 0) {
    /** A plain jelly: no face and no outfit. */
    val plain: Boolean get() = LookBook.job(job) == null

    companion object {
        /** Someone who has not picked a character yet: the smiling face. */
        val FIRST = Look("face", 0)
        val PLAIN = Look("none", 0)
    }
}

/** A wake-up time observed from the system's next alarm, remembered per day. */
@Serializable
data class WakeRecord(
    val date: LocalDate,
    val minute: Int,
    val source: String? = null,
) {
    val dateTime: LocalDateTime get() = date.atTime(LocalTime.of(minute / 60, minute % 60))
}

@Serializable
data class AlarmRequest(
    val date: LocalDate,
    val minute: Int,
    val requestedAt: Long,
) {
    val dateTime: LocalDateTime get() = date.atTime(LocalTime.of(minute / 60, minute % 60))
}

@Serializable
data class AppData(
    val version: Int = 1,
    val jellies: List<Jelly> = emptyList(),
    val routines: List<Routine> = emptyList(),
    /** "routineId@yyyy-MM-dd" keys of routine days that were already laid down. */
    val materialized: Set<String> = emptySet(),
    val wakes: List<WakeRecord> = emptyList(),
    val settings: Settings = Settings(),
    val lastAlarmRequest: AlarmRequest? = null,
    val seeded: Boolean = false,
    /** The hidden golden jelly, once it has been found on this phone (GoldenJelly.kt). */
    val golden: GoldenFind? = null,
) {
    fun jelly(id: String): Jelly? = jellies.firstOrNull { it.id == id }
    fun routine(id: String?): Routine? = id?.let { rid -> routines.firstOrNull { it.id == rid } }
    fun wakeOn(date: LocalDate): WakeRecord? = wakes.firstOrNull { it.date == date }
}

/** The next alarm the system knows about (for example from Samsung Clock). */
data class NextAlarm(
    val dateTime: LocalDateTime,
    val source: String?,
) {
    val date: LocalDate get() = dateTime.toLocalDate()
    val minute: Int get() = dateTime.hour * 60 + dateTime.minute
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JellyLocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
