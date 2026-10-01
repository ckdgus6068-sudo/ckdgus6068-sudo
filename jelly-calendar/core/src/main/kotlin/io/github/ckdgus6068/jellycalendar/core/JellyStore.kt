package io.github.ckdgus6068.jellycalendar.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/**
 * The single source of truth for the calendar. The UI observes [data]; the Android layer
 * persists every new value and feeds in the system's next alarm.
 */
class JellyStore(
    initial: AppData,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val state = MutableStateFlow(initial)
    val data: StateFlow<AppData> = state.asStateFlow()

    private val next = MutableStateFlow<NextAlarm?>(null)
    val nextAlarm: StateFlow<NextAlarm?> = next.asStateFlow()

    private var undoSnapshot: AppData? = null
    private val canUndoState = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = canUndoState.asStateFlow()

    val current: AppData get() = state.value

    fun now(): LocalDateTime = clock()
    fun today(): LocalDate = clock().toLocalDate()
    private fun nowMillis(): Long = clock().atZone(zone()).toInstant().toEpochMilli()

    fun newId(): String = idFactory()

    private fun mutate(transform: (AppData) -> AppData) = state.update(transform)

    private fun mutateWithUndo(transform: (AppData) -> AppData) {
        val before = state.value
        mutate(transform)
        if (state.value != before) {
            undoSnapshot = before
            canUndoState.value = true
        }
    }

    fun undo(): Boolean {
        val snapshot = undoSnapshot ?: return false
        state.value = snapshot
        undoSnapshot = null
        canUndoState.value = false
        return true
    }

    fun seedIfNeeded(factory: (today: LocalDate, id: String) -> Routine) {
        if (current.seeded) return
        val routine = factory(today(), newId())
        mutate { Planner.seed(it, routine) }
    }

    /** Lays routines down, rolls missed jellies into the tray and tidies old records. */
    fun refresh(visible: Collection<LocalDate>) {
        val today = today()
        val millis = nowMillis()
        mutate { Planner.refresh(it, visible, today, millis, idFactory) }
    }

    fun observeAlarm(alarm: NextAlarm?) {
        next.value = alarm
        val now = now()
        mutate { WakeLogic.observe(it, alarm, now) }
    }

    fun move(id: String, date: LocalDate, startMin: Int) = mutate { Planner.move(it, id, date, startMin) }

    /** Like [move], but [undo] can take it back. For moves the user is told about, e.g. onto another day. */
    fun moveUndoable(id: String, date: LocalDate, startMin: Int) = mutateWithUndo { Planner.move(it, id, date, startMin) }

    fun sendToTray(id: String) = mutateWithUndo { Planner.move(it, id, null, null) }

    fun resize(id: String, durationMin: Int) = mutate { Planner.resize(it, id, durationMin) }

    fun toggleDone(id: String) {
        val now = now()
        val millis = nowMillis()
        mutate { Planner.toggleDone(it, id, now, millis) }
    }

    fun saveJelly(jelly: Jelly) = mutate { Planner.upsertJelly(it, jelly) }

    fun setPinned(id: String, pinned: Boolean) = mutate { Planner.setPinned(it, id, pinned) }

    fun newJelly(
        title: String,
        flavor: Int,
        durationMin: Int,
        date: LocalDate?,
        startMin: Int?,
        carryOver: Boolean = true,
        note: String = "",
        pinned: Boolean = false,
    ): Jelly {
        val jelly = Jelly(
            id = newId(),
            title = title,
            flavor = flavor,
            durationMin = durationMin,
            date = date?.takeIf { startMin != null },
            startMin = startMin?.takeIf { date != null },
            carryOver = carryOver,
            note = note,
            createdAt = nowMillis(),
            pinned = pinned && date != null && startMin != null,
        )
        mutate { Planner.upsertJelly(it, jelly) }
        return jelly
    }

    fun deleteJelly(id: String) = mutateWithUndo { Planner.deleteJelly(it, id) }

    fun clearTray() = mutateWithUndo { Planner.clearTray(it) }

    fun saveRoutine(routine: Routine, visible: Collection<LocalDate>, linkJellyId: String? = null) {
        val today = today()
        val millis = nowMillis()
        mutate { data ->
            var result = Planner.saveRoutine(data, routine, today)
            if (linkJellyId != null) result = Planner.linkToRoutine(result, linkJellyId, routine)
            Planner.refresh(result, visible, today, millis, idFactory)
        }
    }

    /** Deletes a routine and its untouched future days; [alsoJellyId] is removed in the same undoable step. */
    fun deleteRoutine(routineId: String, alsoJellyId: String? = null) {
        val today = today()
        mutateWithUndo { data ->
            val result = Planner.deleteRoutine(data, routineId, today)
            if (alsoJellyId != null) Planner.deleteJelly(result, alsoJellyId) else result
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val today = today()
        mutate { data ->
            val updated = data.copy(settings = transform(data.settings))
            if (updated.settings.wakeGapMin != data.settings.wakeGapMin ||
                updated.settings.followAlarm != data.settings.followAlarm
            ) {
                WakeLogic.realignAll(updated, today)
            } else {
                updated
            }
        }
    }

    fun markAlarmRequested(date: LocalDate, minute: Int) {
        val millis = nowMillis()
        mutate { it.copy(lastAlarmRequest = AlarmRequest(date, minute, millis)) }
    }

    /** Replaces everything, e.g. after importing a backup. The previous state can be restored with [undo]. */
    fun replaceAll(data: AppData) = mutateWithUndo { data }
}

object JellyCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        coerceInputValues = true
    }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)

    fun decode(text: String): AppData = json.decodeFromString(AppData.serializer(), text)
}
