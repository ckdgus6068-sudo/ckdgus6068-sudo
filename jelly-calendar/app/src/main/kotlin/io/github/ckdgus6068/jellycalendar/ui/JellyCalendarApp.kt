package io.github.ckdgus6068.jellycalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ckdgus6068.jellycalendar.core.ALL_DAYS
import io.github.ckdgus6068.jellycalendar.core.AppData
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.JellyCodec
import io.github.ckdgus6068.jellycalendar.core.JellyStore
import io.github.ckdgus6068.jellycalendar.core.Planner
import io.github.ckdgus6068.jellycalendar.core.Routine
import io.github.ckdgus6068.jellycalendar.core.WakeLogic
import io.github.ckdgus6068.jellycalendar.ui.calendar.CalendarActions
import io.github.ckdgus6068.jellycalendar.ui.calendar.CalendarScreen
import io.github.ckdgus6068.jellycalendar.ui.calendar.ViewMode
import io.github.ckdgus6068.jellycalendar.ui.drag.DragController
import io.github.ckdgus6068.jellycalendar.ui.drag.DragOverlay
import io.github.ckdgus6068.jellycalendar.ui.drag.DropTarget
import io.github.ckdgus6068.jellycalendar.ui.editor.EditorKind
import io.github.ckdgus6068.jellycalendar.ui.editor.EditorState
import io.github.ckdgus6068.jellycalendar.ui.editor.JellyForm
import io.github.ckdgus6068.jellycalendar.ui.jelly.LocalIdleWobble
import io.github.ckdgus6068.jellycalendar.ui.jelly.LocalJellyClock
import io.github.ckdgus6068.jellycalendar.ui.jelly.rememberJellyClock
import io.github.ckdgus6068.jellycalendar.ui.screens.GuideScreen
import io.github.ckdgus6068.jellycalendar.ui.screens.RoutinesScreen
import io.github.ckdgus6068.jellycalendar.ui.screens.SettingsScreen
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyTheme
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.jellyType
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { CALENDAR, ROUTINES, SETTINGS, GUIDE }

/** The routine laid down on first launch: the morning run from the original idea. */
fun sampleRoutine(today: LocalDate, id: String): Routine = Routine(
    id = id,
    title = "아침 러닝",
    flavor = 4,
    durationMin = 50,
    startMin = 6 * 60,
    days = ALL_DAYS,
    wakeAnchored = true,
    carryOver = true,
    since = today,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyCalendarApp(
    store: JellyStore,
    platform: JellyPlatform,
    modifier: Modifier = Modifier,
    initialMode: ViewMode = ViewMode.BOX,
    initialScreen: Screen = Screen.CALENDAR,
) {
    val data by store.data.collectAsState()
    val nextAlarm by store.nextAlarm.collectAsState()
    val now by produceState(store.now()) {
        while (true) {
            val current = store.now()
            value = current
            delay((60_000L - current.second * 1_000L - current.nano / 1_000_000L).coerceAtLeast(500L))
        }
    }
    val today = now.toLocalDate()
    val nowMinute = now.hour * 60 + now.minute

    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    val weekStart = Planner.weekStart(selected, data.settings.weekStartsOnSunday)
    val weekDays = remember(weekStart) { Planner.weekDays(weekStart) }

    val drag = remember { DragController() }
    drag.today = today
    val weekScroll = rememberScrollState()
    val dayScroll = rememberScrollState()
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var confirmDelete by remember { mutableStateOf<EditorState?>(null) }
    var pendingImport by remember { mutableStateOf<AppData?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The how-to screen opens by itself once, the first time the app starts with it.
    LaunchedEffect(Unit) {
        if (!store.current.settings.guideSeen) {
            screen = Screen.GUIDE
            store.updateSettings { it.copy(guideSeen = true) }
        }
    }

    LaunchedEffect(today, weekStart) {
        // Start the sample tomorrow when this morning's run is already over.
        store.seedIfNeeded { day, id -> sampleRoutine(if (nowMinute < 6 * 60) day else day.plusDays(1), id) }
        store.refresh(weekDays)
    }

    fun notify(message: String, undo: Boolean = false) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = if (undo) "되돌리기" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) store.undo()
        }
    }

    fun nextFlavor(): Int = (data.jellies.size + data.routines.size) % JellyFlavors.all.size

    fun defaultStart(date: LocalDate): Int =
        if (date == today) Planner.snap(maxOf(nowMinute + 10, 8 * 60), 10) else 9 * 60

    SideEffect {
        drag.onDrop = { session, target ->
            val jelly = session.jelly
            when (target) {
                is DropTarget.Slot -> store.move(jelly.id, target.date, target.startMin)
                is DropTarget.Day -> {
                    val start = jelly.startMin ?: Planner.findFreeSlot(
                        store.current,
                        target.date,
                        jelly.durationMin,
                        defaultStart(target.date),
                        jelly.id,
                    )
                    // Moving to a day that is not on screen is announced, with a way back.
                    if (mode != ViewMode.WEEK && target.date != selected) {
                        store.moveUndoable(jelly.id, target.date, start)
                        notify("‘${jelly.title}’ 젤리를 ${dateTitle(target.date)}로 옮겼어요", undo = true)
                    } else {
                        store.move(jelly.id, target.date, start)
                    }
                }
                DropTarget.Tray -> {
                    store.sendToTray(jelly.id)
                    notify("‘${jelly.title}’ 젤리를 보관함에 넣었어요", undo = true)
                }
            }
        }
    }

    fun saveEditor(e: EditorState) {
        val title = e.title.trim()
        when (e.kind) {
            EditorKind.ROUTINE -> {
                val existing = data.routine(e.routineId)
                val routine = Routine(
                    id = existing?.id ?: store.newId(),
                    title = title,
                    flavor = e.flavor,
                    durationMin = e.duration,
                    startMin = e.start ?: 7 * 60,
                    days = e.days,
                    wakeAnchored = e.wakeAnchored,
                    carryOver = e.carryOver,
                    active = e.active,
                    since = existing?.since ?: today,
                    note = e.note,
                )
                store.saveRoutine(routine, weekDays)
                notify(if (existing == null) "반복 젤리를 만들었어요" else "반복 젤리를 고쳤어요")
            }
            EditorKind.JELLY -> {
                val existing = e.jellyId?.let { data.jelly(it) }
                if (existing == null) {
                    if (e.days.isNotEmpty()) {
                        val routine = Routine(
                            id = store.newId(),
                            title = title,
                            flavor = e.flavor,
                            durationMin = e.duration,
                            startMin = e.start ?: 9 * 60,
                            days = e.days,
                            wakeAnchored = e.wakeAnchored,
                            carryOver = e.carryOver,
                            since = maxOf(today, e.date ?: today),
                            note = e.note,
                        )
                        store.saveRoutine(routine, weekDays)
                        notify("${daysText(e.days)} 반복 젤리를 만들었어요")
                    } else {
                        store.newJelly(title, e.flavor, e.duration, e.date, e.start, e.carryOver, e.note)
                    }
                } else {
                    val moved = existing.date != e.date || existing.startMin != e.start
                    val updated = existing.copy(
                        title = title,
                        flavor = e.flavor,
                        durationMin = e.duration,
                        date = e.date,
                        startMin = if (e.date == null) null else e.start,
                        carryOver = e.carryOver,
                        note = e.note,
                        wakeAnchored = existing.wakeAnchored && !moved && e.date != null,
                    )
                    val edited = updated != existing
                    store.saveJelly(updated.copy(detached = existing.detached || (edited && existing.routineId != null)))
                    if (e.days.isNotEmpty() && existing.routineId == null) {
                        val routine = Routine(
                            id = store.newId(),
                            title = title,
                            flavor = e.flavor,
                            durationMin = e.duration,
                            startMin = e.start ?: 9 * 60,
                            days = e.days,
                            wakeAnchored = e.wakeAnchored,
                            carryOver = e.carryOver,
                            since = maxOf(today, e.date ?: today),
                            note = e.note,
                        )
                        store.saveRoutine(routine, weekDays, linkJellyId = existing.id)
                        notify("${daysText(e.days)} 반복 젤리로 만들었어요")
                    }
                }
            }
        }
        editor = null
    }

    fun deleteNow(e: EditorState, wholeRoutine: Boolean) {
        if (e.kind == EditorKind.ROUTINE || wholeRoutine) {
            val id = e.routineId ?: e.linkedRoutine?.id
            if (id != null) store.deleteRoutine(id, alsoJellyId = if (wholeRoutine) e.jellyId else null)
            notify("반복 젤리를 지웠어요", undo = true)
        } else {
            e.jellyId?.let { store.deleteJelly(it) }
            notify("젤리를 지웠어요", undo = true)
        }
        editor = null
        confirmDelete = null
    }

    val actions = object : CalendarActions {
        override fun select(date: LocalDate) {
            selectedDay = date.toEpochDay()
        }

        override fun changeMode(newMode: ViewMode) {
            mode = newMode
        }

        override fun shift(direction: Int) {
            selectedDay += if (mode == ViewMode.WEEK) 7L * direction else direction.toLong()
        }

        override fun goToday() {
            selectedDay = today.toEpochDay()
        }

        override fun open(jelly: Jelly) {
            editor = EditorState.editJelly(jelly, data.routine(jelly.routineId))
        }

        override fun toggleDone(jelly: Jelly) = store.toggleDone(jelly.id)

        override fun resize(jelly: Jelly, duration: Int) = store.resize(jelly.id, duration)

        override fun create(date: LocalDate?, start: Int?) {
            val day = date
            editor = EditorState.newJelly(day, if (day == null) null else start ?: defaultStart(day), nextFlavor())
        }

        override fun setAlarm(date: LocalDate, minute: Int, label: String) {
            val replaced = WakeLogic.replacedAlarms(data, date, minute, store.now())
            val ok = platform.setWakeAlarm(minute / 60, minute % 60, label, data.settings.alarmSkipUi, replaced)
            if (ok) {
                store.markAlarmRequested(date, minute)
                notify(
                    if (replaced.isEmpty()) {
                        "삼성 시계에 ${hm(minute)} 알람을 요청했어요"
                    } else {
                        "${hm(minute)} 알람을 추가하고, 예전 ${replaced.joinToString { hm(it) }} 알람 끄기를 요청했어요"
                    },
                )
            } else {
                notify("알람을 맞출 시계 앱을 찾지 못했어요")
            }
        }

        override fun moveFirst(jelly: Jelly, date: LocalDate, minute: Int) = store.move(jelly.id, date, minute)

        override fun openAlarms() {
            if (!platform.openAlarmList()) notify("시계 앱을 열 수 없어요")
        }

        override fun openRoutines() {
            screen = Screen.ROUTINES
        }

        override fun openSettings() {
            screen = Screen.SETTINGS
        }

        override fun openGuide() {
            screen = Screen.GUIDE
        }

        override fun dismissHint(mode: ViewMode) = store.updateSettings {
            if (mode == ViewMode.BOX) it.copy(boxHintDismissed = true) else it.copy(hintDismissed = true)
        }
    }

    platform.BackHandler(enabled = editor == null && (screen != Screen.CALENDAR || mode != ViewMode.BOX)) {
        if (screen != Screen.CALENDAR) screen = Screen.CALENDAR else mode = ViewMode.BOX
    }

    val type = remember(data.settings.font, platform.fonts) { jellyType(data.settings.font, platform.fonts) }
    JellyTheme(type = type) {
        val colors = LocalJellyColors.current
        val clock = rememberJellyClock(data.settings.idleWobble)
        CompositionLocalProvider(
            LocalJellyClock provides clock,
            LocalIdleWobble provides data.settings.idleWobble,
        ) {
            Box(modifier.fillMaxSize().background(colors.background)) {
                when (screen) {
                    Screen.CALENDAR -> CalendarScreen(
                        data = data,
                        now = now,
                        mode = mode,
                        selected = selected,
                        weekStart = weekStart,
                        drag = drag,
                        weekScroll = weekScroll,
                        dayScroll = dayScroll,
                        actions = actions,
                    )
                    Screen.ROUTINES -> RoutinesScreen(
                        routines = data.routines,
                        gapMin = data.settings.wakeGapMin,
                        onBack = { screen = Screen.CALENDAR },
                        onEdit = { editor = EditorState.editRoutine(it) },
                        onNew = { editor = EditorState.newRoutine(nextFlavor()) },
                        onToggleActive = { routine, on -> store.saveRoutine(routine.copy(active = on), weekDays) },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        settings = data.settings,
                        nextAlarm = nextAlarm,
                        trayCount = Planner.tray(data).size,
                        onBack = { screen = Screen.CALENDAR },
                        onChange = { store.updateSettings(it) },
                        onOpenGuide = { screen = Screen.GUIDE },
                        fonts = platform.fonts,
                        appVersion = platform.appVersion,
                        onOpenAlarms = { actions.openAlarms() },
                        onClearTray = {
                            store.clearTray()
                            notify("보관함을 비웠어요", undo = true)
                        },
                        onExport = {
                            platform.exportBackup("jelly-calendar-$today.json", JellyCodec.encode(store.current))
                        },
                        onImport = {
                            platform.importBackup { text ->
                                val decoded = runCatching { JellyCodec.decode(text) }.getOrNull()
                                if (decoded == null) notify("백업 파일을 읽을 수 없어요") else pendingImport = decoded
                            }
                        },
                    )
                    Screen.GUIDE -> GuideScreen(
                        settings = data.settings,
                        gapMin = data.settings.wakeGapMin,
                        onBack = { screen = Screen.CALENDAR },
                    )
                }
                DragOverlay(
                    drag = drag,
                    compact = mode == ViewMode.WEEK,
                    caption = { jelly, target ->
                        when (target) {
                            is DropTarget.Slot -> if (target.allowed) range(target.startMin, jelly.durationMin) else "지난 날"
                            is DropTarget.Day -> "${target.date.monthValue}/${target.date.dayOfMonth} ${dayName(target.date)}"
                            DropTarget.Tray -> "보관함으로"
                            null -> jelly.startMin?.let { range(it, jelly.durationMin) } ?: durationText(jelly.durationMin)
                        }
                    },
                )
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 124.dp))
            }

            editor?.let { e ->
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { editor = null },
                    sheetState = sheetState,
                    containerColor = colors.surface,
                ) {
                    JellyForm(
                        state = e,
                        weekStart = weekStart,
                        gapMin = data.settings.wakeGapMin,
                        onSave = { saveEditor(e) },
                        onDelete = {
                            if (e.kind == EditorKind.JELLY && e.linkedRoutine != null) confirmDelete = e else deleteNow(e, false)
                        },
                        onToggleDone = {
                            e.jellyId?.let { store.toggleDone(it) }
                            editor = null
                        },
                        onSendToTray = {
                            e.jellyId?.let { store.sendToTray(it) }
                            notify("‘${e.title}’ 젤리를 보관함에 넣었어요", undo = true)
                            editor = null
                        },
                        onOpenRoutine = { editor = EditorState.editRoutine(it) },
                    )
                }
            }

            confirmDelete?.let { e ->
                AlertDialog(
                    onDismissRequest = { confirmDelete = null },
                    title = { Text("무엇을 지울까요?") },
                    text = { Text("이 젤리는 반복 젤리의 하루치예요. 이 날 것만 지울 수도 있고, 앞으로 깔릴 반복 전체를 지울 수도 있어요.") },
                    confirmButton = {
                        TextButton(onClick = { deleteNow(e, false) }) { Text("이 날만") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteNow(e, true) }) { Text("반복 전체") }
                    },
                )
            }

            pendingImport?.let { incoming ->
                AlertDialog(
                    onDismissRequest = { pendingImport = null },
                    title = { Text("백업을 불러올까요?") },
                    text = { Text("젤리 ${incoming.jellies.size}개, 반복 젤리 ${incoming.routines.size}개가 지금 데이터를 대신해요.") },
                    confirmButton = {
                        TextButton(onClick = {
                            store.replaceAll(incoming)
                            store.refresh(weekDays)
                            pendingImport = null
                            notify("백업을 불러왔어요", undo = true)
                        }) { Text("불러오기") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingImport = null }) { Text("취소") }
                    },
                )
            }
        }
    }
}
