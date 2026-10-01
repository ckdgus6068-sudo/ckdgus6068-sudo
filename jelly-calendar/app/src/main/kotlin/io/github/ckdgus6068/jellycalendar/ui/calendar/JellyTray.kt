package io.github.ckdgus6068.jellycalendar.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.Settings
import io.github.ckdgus6068.jellycalendar.ui.common.clickableNoRipple
import io.github.ckdgus6068.jellycalendar.ui.drag.DragController
import io.github.ckdgus6068.jellycalendar.ui.drag.DragSource
import io.github.ckdgus6068.jellycalendar.ui.drag.DraggableJelly
import io.github.ckdgus6068.jellycalendar.ui.drag.DropTarget
import io.github.ckdgus6068.jellycalendar.ui.drag.TrayZone
import io.github.ckdgus6068.jellycalendar.ui.durationText
import io.github.ckdgus6068.jellycalendar.ui.missedText
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType
import java.time.LocalDate

private val PILL_HEIGHT = 50.dp

internal fun pillWidth(durationMin: Int): Dp = (64f + durationMin * 0.9f).coerceIn(104f, 200f).dp

private class TrayGeometry(private val density: Density) : TrayZone {
    override var bounds: Rect = Rect.Zero
    var rowStart: Rect = Rect.Zero

    override fun pillSize(durationMin: Int): Size = with(density) {
        Size(pillWidth(durationMin).toPx(), PILL_HEIGHT.toPx())
    }

    override fun landingRect(durationMin: Int): Rect {
        val size = pillSize(durationMin)
        return Rect(rowStart.left, rowStart.top, rowStart.left + size.width, rowStart.top + size.height)
    }
}

/**
 * The strip at the bottom of the calendar: unfinished jellies wait here until they are
 * dragged into another day, for example the weekend.
 */
@Composable
fun JellyTray(
    items: List<Jelly>,
    weekStart: LocalDate,
    drag: DragController,
    settings: Settings,
    onOpen: (Jelly) -> Unit,
    onToggleDone: (Jelly) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalJellyColors.current
    val density = LocalDensity.current
    val zone = remember(drag, density) { TrayGeometry(density) }
    DisposableEffect(drag, zone) {
        drag.tray = zone
        onDispose { if (drag.tray === zone) drag.tray = null }
    }
    val carrying = drag.session?.let { it.jelly.isScheduled } ?: false
    val hovered = drag.hover == DropTarget.Tray
    val edge by animateColorAsState(
        targetValue = when {
            hovered -> colors.accent
            carrying -> colors.accent.copy(alpha = 0.45f)
            else -> colors.trayEdge
        },
        label = "trayEdge",
    )
    val swell by animateFloatAsState(
        targetValue = if (hovered) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "traySwell",
    )

    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { zone.bounds = it.boundsInRoot() }
            .graphicsLayer {
                scaleX = swell
                scaleY = swell
            }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(colors.tray)
            .border(1.5.dp, edge, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(top = 8.dp, bottom = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "보관함",
                color = colors.text,
                fontSize = 15.sp,
                fontFamily = LocalJellyType.current.display,
                fontWeight = LocalJellyType.current.displayWeight,
            )
            if (items.isNotEmpty()) {
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text("${items.size}", color = colors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = when {
                    hovered -> "여기에 놓으면 보관함으로 들어가요"
                    carrying -> "아래로 끌어 오면 보관함에 넣을 수 있어요"
                    items.isEmpty() -> "못 한 젤리는 여기로 모여요"
                    else -> "꾹 눌러 다른 날로 끌어 넣으세요"
                },
                color = colors.textSub,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.trayEdge, RoundedCornerShape(12.dp))
                    .clickableNoRipple(onAdd)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("+ 할 일", color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(PILL_HEIGHT + 8.dp)) {
            // Where a jelly dropped into the tray lands.
            Box(
                Modifier
                    .padding(start = 14.dp, top = 2.dp)
                    .size(1.dp)
                    .onGloballyPositioned { zone.rowStart = it.boundsInRoot() },
            )
            if (items.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, colors.trayEdge, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("비어 있어요. 오늘도 말랑하게!", color = colors.textSub, fontSize = 12.sp)
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (jelly in items) {
                        key(jelly.id) {
                            TrayJelly(jelly, weekStart, drag, settings, onOpen, onToggleDone)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayJelly(
    jelly: Jelly,
    weekStart: LocalDate,
    drag: DragController,
    settings: Settings,
    onOpen: (Jelly) -> Unit,
    onToggleDone: (Jelly) -> Unit,
) {
    val flavor = JellyFlavors[jelly.flavor]
    DraggableJelly(
        jelly = jelly,
        source = DragSource.TRAY,
        drag = drag,
        doneByDoubleTap = settings.doneByDoubleTap,
        doneByLongPress = settings.doneByLongPress,
        onTap = { onOpen(jelly) },
        onToggleDone = { onToggleDone(jelly) },
        modifier = Modifier.width(pillWidth(jelly.durationMin)).height(PILL_HEIGHT),
        cornerRadius = 18.dp,
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),
        ) {
            Text(
                jelly.title,
                color = flavor.ink,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontFamily = LocalJellyType.current.display,
                fontWeight = LocalJellyType.current.displayWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val missed = missedText(jelly.missedFrom, weekStart)
            Text(
                text = listOfNotNull(durationText(jelly.durationMin), missed).joinToString(" · "),
                color = flavor.ink.copy(alpha = 0.75f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
