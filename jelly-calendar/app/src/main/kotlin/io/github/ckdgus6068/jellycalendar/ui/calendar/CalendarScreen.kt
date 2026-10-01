package io.github.ckdgus6068.jellycalendar.ui.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.AppData
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.JellyStatus
import io.github.ckdgus6068.jellycalendar.core.KoreanHolidays
import io.github.ckdgus6068.jellycalendar.core.Planner
import io.github.ckdgus6068.jellycalendar.core.WakeLogic
import io.github.ckdgus6068.jellycalendar.ui.box.JellyBoxBoard
import io.github.ckdgus6068.jellycalendar.ui.common.FitText
import io.github.ckdgus6068.jellycalendar.ui.common.clickableNoRipple
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.dateTitle
import io.github.ckdgus6068.jellycalendar.ui.drag.DragController
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType
import io.github.ckdgus6068.jellycalendar.ui.weekTitle
import java.time.LocalDate
import java.time.LocalDateTime

/** What the calendar shows: the phone's own jellies, the shared calendar, or both on one day. */
enum class Space { MINE, SHARED, ALL }

/** How the phone's own jellies are shown. */
enum class ViewMode { BOX, WEEK, DAY, MONTH }

/** Everything the calendar screen asks its owner to do. */
interface CalendarActions {
    fun select(date: LocalDate)
    fun changeSpace(newSpace: Space)
    fun changeMode(newMode: ViewMode)
    fun shift(direction: Int)
    fun goToday()
    fun open(jelly: Jelly)
    fun toggleDone(jelly: Jelly)
    fun resize(jelly: Jelly, duration: Int)
    fun create(date: LocalDate?, start: Int?)
    fun setAlarm(date: LocalDate, minute: Int, label: String)
    fun moveFirst(jelly: Jelly, date: LocalDate, minute: Int)
    fun openAlarms()
    fun openRoutines()
    fun openSettings()
    fun openGuide()
    fun dismissHint(mode: ViewMode)

    /** A jelly in the box was squeezed into gold (GoldenJelly.kt). */
    fun foundGolden(jelly: Jelly)
}

@Composable
fun CalendarScreen(
    data: AppData,
    now: LocalDateTime,
    space: Space,
    mode: ViewMode,
    selected: LocalDate,
    weekStart: LocalDate,
    drag: DragController,
    weekScroll: ScrollState,
    dayScroll: ScrollState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
    sharedSpace: @Composable (Modifier) -> Unit = {},
) {
    val today = now.toLocalDate()
    val nowMinute = now.hour * 60 + now.minute
    val weekDays = remember(weekStart) { Planner.weekDays(weekStart) }
    val compact = mode == ViewMode.WEEK

    // The shared calendar is a web page, the same one the other person opens on an iPhone; in "모두"
    // it also gets the phone's own jellies. It draws the same date bar and bottom bar as here.
    Column(modifier.fillMaxSize()) {
        // One place for the tabs, so the jelly behind them slides from tab to tab.
        SpaceTabs(space, actions)
        if (space != Space.MINE) {
            // The page has its own text fields (memos), so it makes room for the keyboard itself.
            sharedSpace(Modifier.weight(1f).fillMaxWidth().imePadding())
        } else {
            DateBar(
                title = when (mode) {
                    ViewMode.WEEK -> weekTitle(weekStart)
                    ViewMode.MONTH -> "${selected.year}년 ${selected.monthValue}월"
                    else -> dateTitle(selected)
                },
                subtitle = when (mode) {
                    ViewMode.WEEK -> if (today in weekDays) "이번 주" else null
                    ViewMode.MONTH -> if (selected.year == today.year && selected.monthValue == today.monthValue) "이번 달" else null
                    else -> listOfNotNull(relativeDay(selected, today), KoreanHolidays.on(selected)?.name).joinToString(" · ").ifEmpty { null }
                },
                actions = actions,
            )
            if (mode == ViewMode.WEEK) {
                WeekHeader(
                    days = weekDays,
                    data = data,
                    today = today,
                    gutter = 30.dp,
                    drag = drag,
                    onPick = {
                        actions.select(it)
                        actions.changeMode(ViewMode.DAY)
                    },
                )
            } else if (mode != ViewMode.MONTH) {
                DayStrip(
                    days = weekDays,
                    selected = selected,
                    data = data,
                    today = today,
                    drag = drag,
                    onPick = { actions.select(it) },
                )
                if (!selected.isBefore(today)) {
                    val status = WakeLogic.status(data, selected, now)
                    WakeCard(
                        status = status,
                        gapMin = data.settings.wakeGapMin,
                        source = data.wakeOn(selected)?.source,
                        requestedMin = data.lastAlarmRequest?.takeIf { it.date == selected }?.minute,
                        onSetAlarm = { minute, label -> actions.setAlarm(selected, minute, label) },
                        onMoveFirst = { jelly, minute -> actions.moveFirst(jelly, selected, minute) },
                        onOpenAlarms = { actions.openAlarms() },
                        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 8.dp),
                    )
                }
            }
            val hintHidden = if (mode == ViewMode.BOX) data.settings.boxHintDismissed else data.settings.hintDismissed
            if (!hintHidden) {
                HintBanner(
                    text = hintText(mode, data.settings.doneByDoubleTap, data.settings.doneByLongPress),
                    onGuide = { actions.openGuide() },
                    onDismiss = { actions.dismissHint(mode) },
                )
            }
            Box(Modifier.weight(1f).padding(top = 6.dp)) {
                when (mode) {
                    ViewMode.BOX -> JellyBoxBoard(
                        date = selected,
                        jellies = Planner.scheduledOn(data, selected).filter { it.status != JellyStatus.MISSED },
                        drag = drag,
                        doneByDoubleTap = data.settings.doneByDoubleTap,
                        doneByLongPress = data.settings.doneByLongPress,
                        onOpen = { actions.open(it) },
                        onToggleDone = { actions.toggleDone(it) },
                        onSwipe = { actions.shift(it) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp).padding(bottom = 8.dp),
                        onGolden = { actions.foundGolden(it) },
                    )
                    ViewMode.MONTH -> MonthView(
                        data = data,
                        selected = selected,
                        today = today,
                        drag = drag,
                        onPick = { actions.select(it) },
                        onOpen = { actions.open(it) },
                        onToggleDone = { actions.toggleDone(it) },
                        onSwipe = { actions.shift(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        val scroll = if (compact) weekScroll else dayScroll
                        val days = if (compact) weekDays else listOf(selected)
                        InitialScroll(scroll, data, days, today, nowMinute, compact)
                        TimelineGrid(
                            days = days,
                            data = data,
                            today = today,
                            nowMinute = nowMinute,
                            compact = compact,
                            scrollState = scroll,
                            drag = drag,
                            onOpen = { actions.open(it) },
                            onToggleDone = { actions.toggleDone(it) },
                            onResize = { jelly, duration -> actions.resize(jelly, duration) },
                            onCreateAt = { date, start -> actions.create(date, start) },
                            onSwipe = { actions.shift(it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            JellyTray(
                items = Planner.tray(data),
                weekStart = weekStart,
                drag = drag,
                settings = data.settings,
                onOpen = { actions.open(it) },
                onToggleDone = { actions.toggleDone(it) },
                onAdd = { actions.create(null, null) },
            )
            BottomBar(
                today = today,
                showingToday = when (mode) {
                    ViewMode.WEEK -> today in weekDays
                    ViewMode.MONTH -> selected.year == today.year && selected.monthValue == today.monthValue
                    else -> selected == today
                },
                onToday = { actions.goToday() },
                onAdd = { actions.create(if (compact && today in weekDays) today else selected, null) },
            ) {
                ModeToggle(mode) { actions.changeMode(it) }
            }
        }
    }
}

/** "오늘", "내일", "3일 뒤", "어제"... for the date bar. */
internal fun relativeDay(date: LocalDate, today: LocalDate): String? {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "오늘"
        days == 1L -> "내일"
        days == 2L -> "모레"
        days == -1L -> "어제"
        days in 3L..30L -> "${days}일 뒤"
        days in -30L..-2L -> "${-days}일 전"
        else -> null
    }
}

/** Scrolls the timeline to the start of the morning the first time a view appears. */
@Composable
private fun InitialScroll(
    scroll: ScrollState,
    data: AppData,
    days: List<LocalDate>,
    today: LocalDate,
    nowMinute: Int,
    compact: Boolean,
) {
    val density = LocalDensity.current
    LaunchedEffect(compact) {
        if (scroll.value != 0) return@LaunchedEffect
        val candidates = ArrayList<Int>()
        for (date in days) {
            Planner.firstOfDay(data, date)?.startMin?.let { candidates += it }
            data.wakeOn(date)?.minute?.let { candidates += it }
        }
        if (today in days) candidates += nowMinute - 60
        val minute = ((candidates.minOrNull() ?: 7 * 60) - 30).coerceIn(0, 20 * 60)
        val pxPerMin = with(density) { (if (compact) 52.dp else 68.dp).toPx() } / 60f
        scroll.scrollTo((minute * pxPerMin).toInt())
    }
}

/** The day, week or month on screen, with arrows to the ones before and after. */
@Composable
private fun DateBar(
    title: String,
    subtitle: String?,
    actions: CalendarActions,
) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { actions.shift(-1) }) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전", tint = colors.text)
        }
        Column(
            Modifier
                .weight(1f)
                .clickableNoRipple { actions.goToday() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FitText(
                title,
                color = colors.text,
                maxSize = 18.sp,
                minSize = 12.sp,
                fontFamily = type.display,
                fontWeight = type.displayWeight,
            )
            if (subtitle != null) Text(subtitle, color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = { actions.shift(1) }) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음", tint = colors.text)
        }
    }
}

/**
 * The same bar at the bottom of every screen: back to today on the left, the view switch in the
 * middle and a new jelly on the right. The shared page draws its own copy of it.
 */
@Composable
private fun BottomBar(
    today: LocalDate,
    showingToday: Boolean,
    onToday: () -> Unit,
    onAdd: () -> Unit,
    toggle: @Composable () -> Unit,
) {
    val colors = LocalJellyColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TodayButton(today, highlighted = !showingToday, onClick = onToday)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { toggle() }
        AddButton(onAdd)
    }
}

/** A little calendar leaf with today's date on it, and the word 오늘. */
@Composable
private fun TodayButton(today: LocalDate, highlighted: Boolean, onClick: () -> Unit) {
    val colors = LocalJellyColors.current
    Row(
        Modifier
            .height(40.dp)
            .squishyClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) colors.accent.copy(alpha = 0.16f) else colors.surfaceSoft)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .size(width = 22.dp, height = 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surface)
                .border(1.dp, colors.gridLineStrong, RoundedCornerShape(6.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(colors.accent))
            Text("${today.dayOfMonth}", color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
        }
        Text(
            "오늘",
            color = if (highlighted) colors.accent else colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val flavor = JellyFlavors[0]
    JellyBody(
        flavor = flavor,
        modifier = Modifier.size(48.dp).squishyClick(onClick = onClick),
        cornerRadius = 18.dp,
    ) {
        Text(
            "+",
            color = flavor.ink,
            fontSize = 26.sp,
            fontFamily = LocalJellyType.current.display,
            fontWeight = LocalJellyType.current.displayWeight,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * "내 젤리 | 공유 젤리 | 모두" across the top, with the app menu at the end. A milky jelly slides to
 * the chosen one, so it does not compete with the pink 상자 · 주 · 일 switch below it.
 */
@Composable
private fun SpaceTabs(space: Space, actions: CalendarActions) {
    val colors = LocalJellyColors.current
    val flavor = JellyFlavors[9]
    val position by animateFloatAsState(
        targetValue = space.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "space",
    )
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceSoft),
        ) {
            val part = maxWidth / Space.values().size
            JellyBody(
                flavor = flavor,
                modifier = Modifier
                    .offset(x = part * position)
                    .width(part)
                    .height(maxHeight)
                    .padding(3.dp),
                cornerRadius = 17.dp,
            )
            Row(Modifier.fillMaxSize()) {
                for (option in Space.values()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickableNoRipple { actions.changeSpace(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (option) {
                                Space.MINE -> "내 젤리"
                                Space.SHARED -> "공유 젤리"
                                Space.ALL -> "모두"
                            },
                            color = if (option == space) flavor.ink else colors.textSub,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "메뉴", tint = colors.text)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("오늘로 가기") }, onClick = {
                    menu = false
                    actions.goToday()
                })
                DropdownMenuItem(text = { Text("반복 젤리") }, onClick = {
                    menu = false
                    actions.openRoutines()
                })
                DropdownMenuItem(text = { Text("삼성 시계 알람") }, onClick = {
                    menu = false
                    actions.openAlarms()
                })
                DropdownMenuItem(text = { Text("설정") }, onClick = {
                    menu = false
                    actions.openSettings()
                })
                DropdownMenuItem(text = { Text("사용법") }, onClick = {
                    menu = false
                    actions.openGuide()
                })
            }
        }
    }
}

/** "상자 | 주 | 일 | 달" switch for the phone's own jellies, with a jelly that slides between them. */
@Composable
private fun ModeToggle(mode: ViewMode, onChange: (ViewMode) -> Unit) {
    val colors = LocalJellyColors.current
    val position by animateFloatAsState(
        targetValue = mode.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "mode",
    )
    BoxWithConstraints(
        Modifier
            .width(168.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(colors.surfaceSoft),
    ) {
        val half = maxWidth / ViewMode.values().size
        JellyBody(
            flavor = JellyFlavors[0],
            modifier = Modifier
                .offset(x = half * position)
                .width(half)
                .height(maxHeight)
                .padding(3.dp),
            cornerRadius = 14.dp,
        )
        Row(Modifier.fillMaxSize()) {
            for (option in ViewMode.values()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickableNoRipple { onChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (option) {
                            ViewMode.BOX -> "상자"
                            ViewMode.WEEK -> "주"
                            ViewMode.DAY -> "일"
                            ViewMode.MONTH -> "달"
                        },
                        color = if (option == mode) JellyFlavors[0].ink else colors.textSub,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** One line of the gestures that work in [mode], following the finishing gestures chosen in settings. */
internal fun hintText(mode: ViewMode, doubleTapDone: Boolean, longPressDone: Boolean): String {
    val done = listOfNotNull(
        if (doubleTapDone) "두 번 톡" else null,
        if (longPressDone) "꾹 눌렀다 떼기" else null,
    ).joinToString(" 또는 ")
    val parts = ArrayList<String>()
    parts += "톡: 열기"
    if (done.isNotEmpty()) parts += "$done: 다 먹었어요"
    if (mode == ViewMode.BOX) {
        parts += "끌기: 흔들기"
        parts += "위 날짜나 아래 보관함에 놓기: 옮기기"
        parts += "빈 곳을 옆으로 밀기: 다른 날"
    } else if (mode == ViewMode.MONTH) {
        parts.clear()
        parts += "날짜 톡: 그날 젤리 보기"
        parts += "보관함 젤리를 날짜에 놓기: 시간 골라 넣기"
        parts += "옆으로 밀기: 다른 달"
    } else {
        parts += "꾹 눌러 끌기: 옮기기"
        parts += "아래 손잡이: 길이"
        parts += "빈 곳 꾹: 새 젤리"
        parts += if (mode == ViewMode.WEEK) "옆으로 밀기: 다른 주" else "옆으로 밀기: 다른 날"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun HintBanner(text: String, onGuide: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalJellyColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accent.copy(alpha = 0.10f))
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            keepWords(text),
            color = colors.text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent)
                .clickableNoRipple(onGuide)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text("사용법", color = colors.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .size(32.dp)
                .clickableNoRipple(onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = colors.textSub, fontSize = 14.sp)
        }
    }
}
