package io.github.ckdgus6068.jellycalendar.ui.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

enum class ViewMode { BOX, WEEK, DAY }

/** Everything the calendar screen asks its owner to do. */
interface CalendarActions {
    fun select(date: LocalDate)
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
}

@Composable
fun CalendarScreen(
    data: AppData,
    now: LocalDateTime,
    mode: ViewMode,
    selected: LocalDate,
    weekStart: LocalDate,
    drag: DragController,
    weekScroll: ScrollState,
    dayScroll: ScrollState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val today = now.toLocalDate()
    val nowMinute = now.hour * 60 + now.minute
    val weekDays = remember(weekStart) { Planner.weekDays(weekStart) }
    val compact = mode == ViewMode.WEEK

    Column(modifier.fillMaxSize()) {
        TopBar(
            title = if (compact) weekTitle(weekStart) else dateTitle(selected),
            subtitle = when {
                compact && today in weekDays -> "이번 주"
                !compact && selected == today -> "오늘"
                !compact && selected == today.plusDays(1) -> "내일"
                else -> null
            },
            mode = mode,
            actions = actions,
        )
        if (compact) {
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
        } else {
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
            val scroll = if (compact) weekScroll else dayScroll
            val days = if (compact) weekDays else listOf(selected)
            if (mode == ViewMode.BOX) {
                JellyBoxBoard(
                    date = selected,
                    jellies = Planner.scheduledOn(data, selected).filter { it.status != JellyStatus.MISSED },
                    drag = drag,
                    doneByDoubleTap = data.settings.doneByDoubleTap,
                    doneByLongPress = data.settings.doneByLongPress,
                    onOpen = { actions.open(it) },
                    onToggleDone = { actions.toggleDone(it) },
                    onSwipe = { actions.shift(it) },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp).padding(bottom = 8.dp),
                )
            } else {
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
            JellyFab(
                onClick = { actions.create(if (compact && today in weekDays) today else selected, null) },
                // In the box the jellies pile up at the bottom, so the button waits in the empty top corner.
                modifier = if (mode == ViewMode.BOX) {
                    Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 22.dp)
                } else {
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 14.dp)
                },
            )
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

@Composable
private fun TopBar(title: String, subtitle: String?, mode: ViewMode, actions: CalendarActions) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 2.dp),
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
        ) {
            FitText(
                title,
                color = colors.text,
                maxSize = 17.sp,
                minSize = 12.sp,
                fontFamily = type.display,
                fontWeight = type.displayWeight,
            )
            Text(subtitle ?: "눌러서 오늘로", color = if (subtitle != null) colors.accent else colors.textSub, fontSize = 11.sp)
        }
        IconButton(onClick = { actions.shift(1) }) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음", tint = colors.text)
        }
        ModeToggle(mode) { actions.changeMode(it) }
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

/** "상자 | 주 | 일" switch with a jelly that slides between the three. */
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
            .width(120.dp)
            .height(34.dp)
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

@Composable
private fun JellyFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val flavor = JellyFlavors[0]
    JellyBody(
        flavor = flavor,
        modifier = modifier.size(58.dp).squishyClick(onClick = onClick),
        cornerRadius = 22.dp,
    ) {
        Text(
            "+",
            color = flavor.ink,
            fontSize = 30.sp,
            fontFamily = LocalJellyType.current.display,
            fontWeight = LocalJellyType.current.displayWeight,
            modifier = Modifier.align(Alignment.Center),
        )
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
    if (done.isNotEmpty()) parts += "$done: 완료"
    if (mode == ViewMode.BOX) {
        parts += "끌기: 흔들기"
        parts += "위 날짜나 아래 보관함에 놓기: 옮기기"
        parts += "빈 곳을 옆으로 밀기: 다른 날"
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
