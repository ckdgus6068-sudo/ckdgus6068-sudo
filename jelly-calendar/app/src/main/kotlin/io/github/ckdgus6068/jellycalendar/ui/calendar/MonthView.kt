package io.github.ckdgus6068.jellycalendar.ui.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.AppData
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.JellyStatus
import io.github.ckdgus6068.jellycalendar.core.KoreanHolidays
import io.github.ckdgus6068.jellycalendar.core.Planner
import io.github.ckdgus6068.jellycalendar.ui.common.clickableNoRipple
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.dateTitle
import io.github.ckdgus6068.jellycalendar.ui.dayName
import io.github.ckdgus6068.jellycalendar.ui.drag.DragController
import io.github.ckdgus6068.jellycalendar.ui.drag.DropTarget
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.jelly.rememberJellyMotion
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.range
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType
import java.time.LocalDate
import kotlin.math.abs

/** Jellies shown in a month cell; finished ones leave the grid (they stay in the day's list). */
private const val CELL_JELLIES = 3

/** The day's jellies in the order a month shows them: pinned first, then by start time. */
internal fun monthOrder(jellies: List<Jelly>): List<Jelly> =
    jellies.sortedWith(compareBy<Jelly>({ !it.pinned }, { it.startMin ?: 0 }, { it.createdAt }))

/**
 * A month of the phone's own jellies: six weeks of days with up to three unfinished jellies each,
 * and the chosen day's jellies below. Swipe sideways for another month; tray jellies can be
 * dropped on a day.
 */
@Composable
fun MonthView(
    data: AppData,
    selected: LocalDate,
    today: LocalDate,
    drag: DragController,
    onPick: (LocalDate) -> Unit,
    onOpen: (Jelly) -> Unit,
    onToggleDone: (Jelly) -> Unit,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
) {
    val colors = LocalJellyColors.current
    val sundayFirst = data.settings.weekStartsOnSunday
    val days = remember(selected.year, selected.monthValue, sundayFirst) { Planner.monthGrid(selected, sundayFirst) }
    val swipe by rememberUpdatedState(onSwipe)
    Column(modifier.verticalScroll(scroll).padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
            for (date in days.take(7)) {
                Text(
                    dayName(date),
                    color = when (date.dayOfWeek.value) {
                        7 -> colors.sunday
                        6 -> colors.saturday
                        else -> colors.textSub
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Column(
            Modifier.pointerInput(Unit) {
                var travel = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragEnd = { if (abs(travel) > 80.dp.toPx()) swipe(if (travel < 0) 1 else -1) },
                    onHorizontalDrag = { _, amount -> travel += amount },
                )
            },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (week in days.chunked(7)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (date in week) {
                        MonthCell(
                            date = date,
                            inMonth = date.monthValue == selected.monthValue,
                            today = today,
                            selected = selected,
                            jellies = monthOrder(Planner.scheduledOn(data, date).filter { it.status != JellyStatus.MISSED }),
                            drag = drag,
                            onPick = onPick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        DayList(
            date = selected,
            today = today,
            jellies = monthOrder(Planner.scheduledOn(data, selected).filter { it.status != JellyStatus.MISSED }),
            onOpen = onOpen,
            onToggleDone = onToggleDone,
            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun MonthCell(
    date: LocalDate,
    inMonth: Boolean,
    today: LocalDate,
    selected: LocalDate,
    jellies: List<Jelly>,
    drag: DragController,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalJellyColors.current
    val holiday = KoreanHolidays.on(date)
    val open = jellies.filter { !it.isDone }
    val shown = open.take(CELL_JELLIES)
    val hovered = (drag.hover as? DropTarget.Day)?.date == date
    val bounce by animateFloatAsState(
        targetValue = if (hovered) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "cell",
    )
    val isSelected = date == selected
    Column(
        modifier
            .dayDropTarget(date, drag)
            .graphicsLayer {
                scaleX = bounce
                scaleY = bounce
            }
            .height(80.dp)
            .alpha(if (inMonth) 1f else 0.38f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) colors.accent.copy(alpha = 0.16f) else colors.surface)
            .border(1.5.dp, if (isSelected) colors.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickableNoRipple { onPick(date) }
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (date == today) colors.today else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${date.dayOfMonth}",
                    color = if (date == today) colors.onAccent else dayColor(date, colors),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            val more = open.size - shown.size
            Text(
                when {
                    more > 0 -> "+$more"
                    holiday != null -> holiday.short
                    else -> ""
                },
                color = if (more > 0) colors.textSub else colors.sunday,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 1.dp),
            )
        }
        for (jelly in shown) {
            val flavor = JellyFlavors[jelly.flavor]
            Text(
                jelly.title,
                color = if (jelly.pinned) Color.White else flavor.ink,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (jelly.pinned) flavor.deep else flavor.base)
                    .padding(horizontal = 3.dp, vertical = 0.5.dp),
            )
        }
    }
}

/** The chosen day's jellies as cards; finished ones are folded away at the end. */
@Composable
internal fun DayList(
    date: LocalDate,
    today: LocalDate,
    jellies: List<Jelly>,
    onOpen: (Jelly) -> Unit,
    onToggleDone: (Jelly) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    val open = jellies.filter { !it.isDone }
    val done = jellies.filter { it.isDone }
    var showDone by rememberSaveable(date) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                dateTitle(date),
                color = colors.text,
                fontSize = 17.sp,
                fontFamily = type.display,
                fontWeight = type.displayWeight,
            )
            val notes = listOfNotNull(
                KoreanHolidays.on(date)?.name,
                if (date == today) "오늘" else null,
                if (jellies.isEmpty()) null else "젤리 ${jellies.size}개",
            )
            Text(
                notes.joinToString(" · "),
                color = colors.textSub,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }
        if (jellies.isEmpty()) {
            Text(
                keepWords("이 날은 아직 말랑하게 비어 있어요. 아래 ＋ 로 젤리를 담아 보세요."),
                color = colors.textSub,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, colors.gridLineStrong, RoundedCornerShape(18.dp))
                    .padding(16.dp),
            )
        }
        for (jelly in open) JellyCard(jelly, onOpen, onToggleDone)
        if (done.isNotEmpty()) {
            Text(
                if (showDone) "다 먹은 젤리 ${done.size}개 접기 ▴" else "다 먹은 젤리 ${done.size}개 ▾",
                color = colors.textSub,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickableNoRipple { showDone = !showDone }.padding(vertical = 4.dp, horizontal = 2.dp),
            )
            if (showDone) for (jelly in done) JellyCard(jelly, onOpen, onToggleDone)
        }
    }
}

/** One jelly as a soft card: name, time and length, a pin when pinned, and a round finish button. */
@Composable
internal fun JellyCard(jelly: Jelly, onOpen: (Jelly) -> Unit, onToggleDone: (Jelly) -> Unit) {
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    val flavor = JellyFlavors[jelly.flavor]
    val ink = if (jelly.isDone) Color.White else flavor.ink
    // The finish colour rises like liquid, as on the other jellies of the app.
    val motion = rememberJellyMotion("card-${jelly.id}", jelly.isDone)
    JellyBody(
        flavor = flavor,
        modifier = Modifier.fillMaxWidth().height(58.dp).squishyClick { onOpen(jelly) },
        motion = motion,
        cornerRadius = 18.dp,
        idle = false,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp).align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (jelly.pinned) Text("📌 ", fontSize = 12.sp)
                    Text(
                        jelly.title.ifBlank { "이름 없는 젤리" },
                        color = ink,
                        fontSize = 15.sp,
                        fontFamily = type.display,
                        fontWeight = type.displayWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    jelly.startMin?.let { range(it, jelly.durationMin) } ?: "",
                    color = ink.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (jelly.isDone) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.55f))
                    .squishyClick { onToggleDone(jelly) },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (jelly.isDone) "✓" else "", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}
