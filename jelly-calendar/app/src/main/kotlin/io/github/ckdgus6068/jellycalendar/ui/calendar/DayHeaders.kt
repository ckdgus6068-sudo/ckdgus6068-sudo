package io.github.ckdgus6068.jellycalendar.ui.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.AppData
import io.github.ckdgus6068.jellycalendar.core.JellyStatus
import io.github.ckdgus6068.jellycalendar.core.Planner
import io.github.ckdgus6068.jellycalendar.ui.dayName
import io.github.ckdgus6068.jellycalendar.ui.drag.DragController
import io.github.ckdgus6068.jellycalendar.ui.drag.DropTarget
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import java.time.DayOfWeek
import java.time.LocalDate

internal fun dayColor(date: LocalDate, colors: JellyColors): Color = when (date.dayOfWeek) {
    DayOfWeek.SATURDAY -> colors.saturday
    DayOfWeek.SUNDAY -> colors.sunday
    else -> colors.text
}

private data class DayProgress(val done: Int, val total: Int, val flavors: List<Int>)

private fun progressOf(data: AppData, date: LocalDate): DayProgress {
    val jellies = Planner.scheduledOn(data, date).filter { it.status != JellyStatus.MISSED }
    return DayProgress(
        done = jellies.count { it.isDone },
        total = jellies.size,
        flavors = jellies.take(5).map { it.flavor },
    )
}

/** Remembers where a day sits on screen so jellies can be dropped onto it. */
@Composable
private fun Modifier.dayDropTarget(date: LocalDate, drag: DragController): Modifier {
    DisposableEffect(date, drag) {
        onDispose { drag.dayRects.remove(date) }
    }
    return this.onGloballyPositioned { drag.dayRects[date] = it.boundsInRoot() }
}

/** Column titles of the week view: tap a day to open it. */
@Composable
fun WeekHeader(
    days: List<LocalDate>,
    data: AppData,
    today: LocalDate,
    gutter: Dp,
    drag: DragController,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalJellyColors.current
    Row(modifier.fillMaxWidth().padding(end = 4.dp)) {
        Spacer(Modifier.width(gutter))
        for (date in days) {
            val hovered = (drag.hover as? DropTarget.Day)?.date == date
            val bounce by animateFloatAsState(
                targetValue = if (hovered) 1.12f else 1f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 500f),
                label = "dayHover",
            )
            val progress = progressOf(data, date)
            Column(
                Modifier
                    .weight(1f)
                    .dayDropTarget(date, drag)
                    .graphicsLayer {
                        scaleX = bounce
                        scaleY = bounce
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (hovered) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPick(date) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(dayName(date), color = dayColor(date, colors), fontSize = 11.sp)
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (date == today) colors.today else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${date.dayOfMonth}",
                        color = if (date == today) colors.onAccent else dayColor(date, colors),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                ProgressBar(progress.done, progress.total, Modifier.padding(top = 4.dp).width(22.dp).height(4.dp))
            }
        }
    }
}

/** The seven days above the day view, with a ring showing how much of each day is done. */
@Composable
fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    data: AppData,
    today: LocalDate,
    drag: DragController,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalJellyColors.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (date in days) {
            val isSelected = date == selected
            val hovered = (drag.hover as? DropTarget.Day)?.date == date
            val bounce by animateFloatAsState(
                targetValue = when {
                    hovered -> 1.14f
                    isSelected -> 1.04f
                    else -> 1f
                },
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 500f),
                label = "chip",
            )
            val progress = progressOf(data, date)
            val background = when {
                hovered -> colors.accent.copy(alpha = 0.22f)
                isSelected -> colors.accent
                else -> colors.surfaceSoft
            }
            val ink = if (isSelected && !hovered) colors.onAccent else dayColor(date, colors)
            Column(
                Modifier
                    .weight(1f)
                    .dayDropTarget(date, drag)
                    .graphicsLayer {
                        scaleX = bounce
                        scaleY = bounce
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(date) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(dayName(date), color = ink.copy(alpha = 0.85f), fontSize = 11.sp)
                Box(Modifier.padding(top = 2.dp).size(30.dp), contentAlignment = Alignment.Center) {
                    ProgressRing(
                        done = progress.done,
                        total = progress.total,
                        color = if (isSelected && !hovered) colors.onAccent else colors.accent,
                        track = ink.copy(alpha = 0.18f),
                    )
                    Text("${date.dayOfMonth}", color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                if (date == today) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.onAccent else colors.today),
                    )
                } else {
                    FlavorDots(progress.flavors, Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun FlavorDots(flavors: List<Int>, modifier: Modifier = Modifier) {
    Row(modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        for (flavor in flavors.take(4)) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(JellyFlavors[flavor].deep))
        }
    }
}

@Composable
private fun ProgressRing(done: Int, total: Int, color: Color, track: Color) {
    Canvas(Modifier.size(30.dp)) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
        if (total > 0 && done > 0) {
            drawArc(
                color,
                -90f,
                360f * done / total,
                false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ProgressBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = LocalJellyColors.current
    Canvas(modifier) {
        val radius = size.height / 2f
        drawRoundRect(colors.gridLineStrong, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        if (total > 0 && done > 0) {
            drawRoundRect(
                colors.ok,
                size = Size(size.width * done / total, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
        }
    }
}
