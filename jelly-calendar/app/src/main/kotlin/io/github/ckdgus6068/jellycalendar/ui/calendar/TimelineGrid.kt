package io.github.ckdgus6068.jellycalendar.ui.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.AppData
import io.github.ckdgus6068.jellycalendar.core.DayLayout
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.JellyStatus
import io.github.ckdgus6068.jellycalendar.core.MINUTES_PER_DAY
import io.github.ckdgus6068.jellycalendar.core.Planner
import io.github.ckdgus6068.jellycalendar.ui.drag.DragController
import io.github.ckdgus6068.jellycalendar.ui.drag.DragSource
import io.github.ckdgus6068.jellycalendar.ui.drag.DraggableJelly
import io.github.ckdgus6068.jellycalendar.ui.drag.DropTarget
import io.github.ckdgus6068.jellycalendar.ui.drag.TimelineZone
import io.github.ckdgus6068.jellycalendar.ui.common.squishyClick
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyLabel
import io.github.ckdgus6068.jellycalendar.ui.jelly.rememberJellyMotion
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyLook
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyMotion
import io.github.ckdgus6068.jellycalendar.ui.range
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Pixel geometry of the grid: a time gutter on the left, then one column per day. */
internal class GridGeometry(
    val days: List<LocalDate>,
    val gutter: Float,
    val colWidth: Float,
    val pxPerMin: Float,
    val topPad: Float,
    val bottomPad: Float,
    val jellyPad: Float,
    val minJelly: Float,
) {
    val contentHeight: Float get() = topPad + MINUTES_PER_DAY * pxPerMin + bottomPad

    fun colLeft(index: Int): Float = gutter + index * colWidth
    fun yOf(minute: Int): Float = topPad + minute * pxPerMin
    fun minuteAt(y: Float): Float = (y - topPad) / pxPerMin
    fun jellyHeight(duration: Int): Float = max(duration * pxPerMin - 1f, minJelly)

    fun dayIndexAt(x: Float): Int? {
        if (x < gutter) return null
        val index = floor((x - gutter) / colWidth).toInt()
        return if (index in days.indices) index else null
    }
}

private class GridZone : TimelineZone {
    var geometry: GridGeometry? = null
    override var viewport: Rect = Rect.Zero
    var contentOrigin: Offset = Offset.Zero
    var today: LocalDate = LocalDate.MIN
    var snap: Int = 10

    override fun slotAt(pointer: Offset, jellyTop: Float, durationMin: Int): DropTarget.Slot? {
        val g = geometry ?: return null
        val index = g.dayIndexAt(pointer.x - contentOrigin.x) ?: return null
        val minute = g.minuteAt(jellyTop - contentOrigin.y)
        val start = Planner.clampStart(Planner.snap(minute.roundToInt(), snap), durationMin)
        val date = g.days[index]
        return DropTarget.Slot(date, start, allowed = !date.isBefore(today))
    }

    override fun slotRect(date: LocalDate, startMin: Int, durationMin: Int): Rect? {
        val g = geometry ?: return null
        val index = g.days.indexOf(date)
        if (index < 0) return null
        val left = contentOrigin.x + g.colLeft(index) + g.jellyPad
        val top = contentOrigin.y + g.yOf(startMin)
        val shown = durationMin.coerceAtMost(MINUTES_PER_DAY - startMin)
        return Rect(left, top, left + g.colWidth - 2 * g.jellyPad, top + g.jellyHeight(shown))
    }

    override fun jellySize(durationMin: Int): Size {
        val g = geometry ?: return Size(120f, 120f)
        // A night shift being carried is shown at half a day's height at most.
        return Size(g.colWidth - 2 * g.jellyPad, g.jellyHeight(durationMin.coerceAtMost(MINUTES_PER_DAY / 2)))
    }
}

/**
 * The day or week timeline: hour lines, a column per day, the jellies, the faint marks of
 * missed ones, the wake-up line and a red "now" line. Jellies can be carried anywhere; the
 * grid scrolls by itself when a jelly is held near its top or bottom edge.
 */
@Composable
fun TimelineGrid(
    days: List<LocalDate>,
    data: AppData,
    today: LocalDate,
    nowMinute: Int,
    compact: Boolean,
    scrollState: ScrollState,
    drag: DragController,
    onOpen: (Jelly) -> Unit,
    onToggleDone: (Jelly) -> Unit,
    onResize: (Jelly, Int) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    val swipeCallback by rememberUpdatedState(onSwipe)
    val createCallback by rememberUpdatedState(onCreateAt)
    val settings = data.settings

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                val threshold = 72.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val direction = when {
                            swipe.value > threshold -> -1
                            swipe.value < -threshold -> 1
                            else -> 0
                        }
                        scope.launch {
                            if (direction != 0) {
                                swipeCallback(direction)
                                swipe.snapTo(direction * size.width * 0.25f)
                            }
                            swipe.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 380f))
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.55f)) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + amount) }
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val geometry = remember(days, widthPx, compact, density) {
            with(density) {
                val gutter = (if (compact) 30.dp else 46.dp).toPx()
                GridGeometry(
                    days = days,
                    gutter = gutter,
                    colWidth = (widthPx - gutter - 4.dp.toPx()) / days.size.coerceAtLeast(1),
                    pxPerMin = (if (compact) 52.dp else 68.dp).toPx() / 60f,
                    topPad = 12.dp.toPx(),
                    bottomPad = 36.dp.toPx(),
                    jellyPad = (if (compact) 1.5.dp else 3.dp).toPx(),
                    minJelly = (if (compact) 12.dp else 16.dp).toPx(),
                )
            }
        }
        val zone = remember(drag) { GridZone() }
        zone.geometry = geometry
        zone.today = today
        zone.snap = settings.snapMin
        DisposableEffect(drag, zone) {
            drag.timeline = zone
            onDispose { if (drag.timeline === zone) drag.timeline = null }
        }

        val dragging = drag.session != null
        LaunchedEffect(dragging) {
            if (!dragging) return@LaunchedEffect
            val edge = with(density) { 64.dp.toPx() }
            val maxSpeed = with(density) { 820.dp.toPx() }
            var last = -1L
            while (drag.session != null) {
                withFrameNanos { now ->
                    val dt = if (last < 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    val vp = zone.viewport
                    val p = drag.pointer
                    if (p.x >= vp.left && p.x <= vp.right) {
                        val speed = when {
                            p.y >= vp.top && p.y < vp.top + edge -> -maxSpeed * (1f - (p.y - vp.top) / edge)
                            p.y <= vp.bottom && p.y > vp.bottom - edge -> maxSpeed * (1f - (vp.bottom - p.y) / edge)
                            else -> 0f
                        }
                        if (speed != 0f) {
                            scrollState.dispatchRawDelta(speed * dt)
                            drag.refresh()
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { zone.viewport = it.boundsInRoot() }
                .verticalScroll(scrollState),
        ) {
            val contentHeightDp = with(density) { geometry.contentHeight.toDp() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(contentHeightDp)
                    .graphicsLayer { translationX = swipe.value * 0.35f }
                    .onGloballyPositioned { zone.contentOrigin = it.positionInRoot() }
                    .pointerInput(geometry) {
                        detectTapGestures(
                            onLongPress = { pos ->
                                val index = geometry.dayIndexAt(pos.x) ?: return@detectTapGestures
                                val minute = geometry.minuteAt(pos.y).toInt()
                                val start = Planner.clampStart((minute / 30) * 30, 50)
                                createCallback(geometry.days[index], start)
                            },
                        )
                    },
            ) {
                GridBackground(geometry, today, compact)
                HourLabels(geometry, compact)

                // Where missed jellies used to be.
                days.forEachIndexed { index, date ->
                    for (ghost in Planner.ghostsOn(data, date)) {
                        val start = ghost.missedFromStart ?: continue
                        GhostJelly(ghost, geometry, index, start, compact)
                    }
                }

                WakeLines(days, data, geometry, compact)

                // The jellies themselves, keyed by id so that moving to another visible day keeps them alive.
                val minVisual = ceil(geometry.minJelly / geometry.pxPerMin).toInt()
                days.forEachIndexed { index, date ->
                    val onDay = Planner.scheduledOn(data, date)
                    // Last night's jellies that run on into this morning, drawn from midnight.
                    val carried = Planner.scheduledOn(data, date.minusDays(1)).filter { it.overnight }
                    val lanes = DayLayout.place(
                        onDay.map {
                            val start = it.startMin ?: 0
                            DayLayout.Item(it.id, start, (start + it.durationMin).coerceAtMost(MINUTES_PER_DAY))
                        } + carried.map { DayLayout.Item(carriedKey(it), 0, it.endMin!! - MINUTES_PER_DAY) },
                        minVisualMinutes = minVisual,
                    )
                    for (jelly in carried) {
                        val lane = lanes[carriedKey(jelly)]
                        val laneWidth = geometry.colWidth / (lane?.lanes ?: 1)
                        key(carriedKey(jelly)) {
                            CarriedJelly(
                                jelly = jelly,
                                x = geometry.colLeft(index) + laneWidth * (lane?.lane ?: 0) + geometry.jellyPad,
                                widthPx = (laneWidth - 2 * geometry.jellyPad).coerceAtLeast(1f),
                                geometry = geometry,
                                compact = compact,
                                onOpen = onOpen,
                            )
                        }
                    }
                    for (jelly in onDay) {
                        val start = jelly.startMin ?: continue
                        val lane = lanes[jelly.id]
                        val laneWidth = geometry.colWidth / (lane?.lanes ?: 1)
                        val x = geometry.colLeft(index) + laneWidth * (lane?.lane ?: 0) + geometry.jellyPad
                        val y = geometry.yOf(start)
                        // Many overlapping jellies in a narrow week column must never get a negative width.
                        val width = (laneWidth - 2 * geometry.jellyPad).coerceAtLeast(1f)
                        key(jelly.id) {
                            TimelineJelly(
                                jelly = jelly,
                                x = x,
                                y = y,
                                widthPx = width,
                                geometry = geometry,
                                compact = compact,
                                drag = drag,
                                snap = settings.snapMin,
                                doneByDoubleTap = settings.doneByDoubleTap,
                                doneByLongPress = settings.doneByLongPress,
                                onOpen = onOpen,
                                onToggleDone = onToggleDone,
                                onResize = onResize,
                            )
                        }
                    }
                }

                DropPreview(drag, geometry, compact)
                NowLine(days, today, nowMinute, geometry)
            }
        }
    }
}

@Composable
private fun GridBackground(geometry: GridGeometry, today: LocalDate, compact: Boolean) {
    val colors = LocalJellyColors.current
    Canvas(Modifier.fillMaxSize()) {
        val right = size.width
        geometry.days.forEachIndexed { index, date ->
            val left = geometry.colLeft(index)
            val tint = when {
                date == today -> colors.accent.copy(alpha = if (colors.isDark) 0.08f else 0.05f)
                date.dayOfWeek == DayOfWeek.SATURDAY -> colors.saturday.copy(alpha = 0.035f)
                date.dayOfWeek == DayOfWeek.SUNDAY -> colors.sunday.copy(alpha = 0.035f)
                date.isBefore(today) -> colors.text.copy(alpha = 0.025f)
                else -> Color.Transparent
            }
            if (tint != Color.Transparent) {
                drawRect(tint, topLeft = Offset(left, 0f), size = Size(geometry.colWidth, size.height))
            }
        }
        val halfHour = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()), 0f)
        for (hour in 0..24) {
            val y = geometry.yOf(hour * 60)
            drawLine(
                color = if (hour % 6 == 0) colors.gridLineStrong else colors.gridLine,
                start = Offset(geometry.gutter - 4.dp.toPx(), y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
            if (!compact && hour < 24) {
                val half = geometry.yOf(hour * 60 + 30)
                drawLine(
                    color = colors.gridLine,
                    start = Offset(geometry.gutter, half),
                    end = Offset(right, half),
                    strokeWidth = 0.7.dp.toPx(),
                    pathEffect = halfHour,
                )
            }
        }
        if (geometry.days.size > 1) {
            for (index in 0..geometry.days.size) {
                val x = geometry.colLeft(index)
                drawLine(colors.gridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

@Composable
private fun HourLabels(geometry: GridGeometry, compact: Boolean) {
    val colors = LocalJellyColors.current
    val density = LocalDensity.current
    for (hour in 0..23) {
        val y = with(density) { (geometry.yOf(hour * 60)).toDp() }
        Text(
            text = if (compact) "$hour" else hm(hour * 60),
            color = colors.textSub,
            fontSize = if (compact) 10.sp else 11.sp,
            modifier = Modifier
                .offset(x = if (compact) 4.dp else 6.dp, y = y - 7.dp)
                .width(with(density) { geometry.gutter.toDp() } - 6.dp),
        )
    }
}

private fun carriedKey(jelly: Jelly) = "${jelly.id}~next"

/**
 * The morning part of a jelly that started the evening before (a night shift), from midnight to its
 * end. A tap opens the jelly; it is moved and stretched on its own day.
 */
@Composable
private fun CarriedJelly(
    jelly: Jelly,
    x: Float,
    widthPx: Float,
    geometry: GridGeometry,
    compact: Boolean,
    onOpen: (Jelly) -> Unit,
) {
    val density = LocalDensity.current
    val end = jelly.endMin!! - MINUTES_PER_DAY
    val h = geometry.jellyHeight(end)
    val heightDp = with(density) { h.toDp() }
    val flavor = JellyFlavors[jelly.flavor]
    val motion = rememberJellyMotion("carried-${jelly.id}", jelly.isDone)
    JellyBody(
        flavor = flavor,
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), geometry.yOf(0).roundToInt()) }
            .size(with(density) { widthPx.toDp() }, heightDp)
            .squishyClick { onOpen(jelly) },
        motion = motion,
        cornerRadius = if (compact) 9.dp else 12.dp,
        idle = false,
    ) {
        JellyLabel(
            title = "↳ ${jelly.title}",
            subtitle = if (compact) "~${hm(end)}" else "어제부터 · ${hm(end)}까지",
            flavor = flavor,
            motion = motion,
            heightDp = heightDp.value,
            compact = compact,
            missed = jelly.status == JellyStatus.MISSED,
        )
    }
}

@Composable
private fun GhostJelly(jelly: Jelly, geometry: GridGeometry, index: Int, start: Int, compact: Boolean) {
    val density = LocalDensity.current
    val x = geometry.colLeft(index) + geometry.jellyPad
    val y = geometry.yOf(start)
    val w = geometry.colWidth - 2 * geometry.jellyPad
    val h = geometry.jellyHeight(jelly.durationMin)
    val flavor = JellyFlavors[jelly.flavor]
    JellyBody(
        flavor = flavor,
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() }),
        look = JellyLook.GHOST,
        cornerRadius = if (compact) 9.dp else 12.dp,
        idle = false,
    ) {
        if (!compact && h > with(density) { 20.dp.toPx() }) {
            Text(
                text = "놓침 · ${jelly.title}",
                color = flavor.deep.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun WakeLines(days: List<LocalDate>, data: AppData, geometry: GridGeometry, compact: Boolean) {
    val colors = LocalJellyColors.current
    val density = LocalDensity.current
    val dash = remember(density) { with(density) { PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f) } }
    Canvas(Modifier.fillMaxSize()) {
        days.forEachIndexed { index, date ->
            val wake = data.wakeOn(date) ?: return@forEachIndexed
            val y = geometry.yOf(wake.minute)
            val left = geometry.colLeft(index)
            drawLine(
                color = colors.wake,
                start = Offset(left, y),
                end = Offset(left + geometry.colWidth, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
            drawCircle(colors.wake, radius = 3.5.dp.toPx(), center = Offset(left + 3.5.dp.toPx(), y))
        }
    }
    if (!compact) {
        days.forEachIndexed { index, date ->
            val wake = data.wakeOn(date) ?: return@forEachIndexed
            val x = geometry.colLeft(index) + geometry.colWidth
            val y = geometry.yOf(wake.minute)
            Box(
                Modifier
                    .offset { IntOffset((x - with(density) { 74.dp.toPx() }).roundToInt(), (y - with(density) { 18.dp.toPx() }).roundToInt()) }
                    .clip(RoundedCornerShape(8.dp))
                    .drawBehind { drawRect(colors.wake.copy(alpha = 0.18f)) }
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text("기상 ${hm(wake.minute)}", color = colors.wake, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NowLine(days: List<LocalDate>, today: LocalDate, nowMinute: Int, geometry: GridGeometry) {
    val index = days.indexOf(today)
    if (index < 0) return
    val colors = LocalJellyColors.current
    Canvas(Modifier.fillMaxSize()) {
        val y = geometry.yOf(nowMinute)
        val left = geometry.colLeft(index)
        drawLine(colors.nowLine, Offset(left, y), Offset(left + geometry.colWidth, y), strokeWidth = 2.dp.toPx())
        drawCircle(colors.nowLine, radius = 4.dp.toPx(), center = Offset(left, y))
    }
}

@Composable
private fun DropPreview(drag: DragController, geometry: GridGeometry, compact: Boolean) {
    val session = drag.session ?: return
    val hover = drag.hover as? DropTarget.Slot ?: return
    val index = geometry.days.indexOf(hover.date)
    if (index < 0) return
    val colors = LocalJellyColors.current
    val flavor = JellyFlavors[session.jelly.flavor]
    val density = LocalDensity.current
    val x = geometry.colLeft(index) + geometry.jellyPad
    val y = geometry.yOf(hover.startMin)
    val w = geometry.colWidth - 2 * geometry.jellyPad
    val h = geometry.jellyHeight(session.jelly.durationMin.coerceAtMost(MINUTES_PER_DAY - hover.startMin))
    val stroke = if (hover.allowed) flavor.deep else colors.danger
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .drawBehind {
                val radius = CornerRadius((if (compact) 9.dp else 12.dp).toPx())
                drawRoundRect(stroke.copy(alpha = 0.14f), cornerRadius = radius)
                drawRoundRect(
                    stroke.copy(alpha = 0.8f),
                    cornerRadius = radius,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
                    ),
                )
            },
    ) {
        if (!hover.allowed && !compact) {
            Text(
                "지난 날에는 넣을 수 없어요",
                color = colors.danger,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun TimelineJelly(
    jelly: Jelly,
    x: Float,
    y: Float,
    widthPx: Float,
    geometry: GridGeometry,
    compact: Boolean,
    drag: DragController,
    snap: Int,
    doneByDoubleTap: Boolean,
    doneByLongPress: Boolean,
    onOpen: (Jelly) -> Unit,
    onToggleDone: (Jelly) -> Unit,
    onResize: (Jelly, Int) -> Unit,
) {
    val density = LocalDensity.current
    val target = Offset(x, y)
    val position = remember { Animatable(target, Offset.VectorConverter) }
    LaunchedEffect(target) {
        if (drag.isHidden(jelly.id) || drag.isGhost(jelly.id)) {
            position.snapTo(target)
        } else {
            position.animateTo(target, spring(dampingRatio = 0.62f, stiffness = 320f))
        }
    }
    var preview by remember { mutableStateOf<Int?>(null) }
    var tension by remember { mutableStateOf(0f) }
    val shown = preview ?: jelly.durationMin
    val start = jelly.startMin ?: 0
    // A jelly that runs past midnight ends at the bottom of its day; the rest is drawn on the next.
    val heightPx = geometry.jellyHeight(shown.coerceAtMost(MINUTES_PER_DAY - start))
    val heightDp = with(density) { heightPx.toDp() }
    val widthDp = with(density) { widthPx.toDp() }

    DraggableJelly(
        jelly = jelly,
        source = DragSource.TIMELINE,
        drag = drag,
        doneByDoubleTap = doneByDoubleTap,
        doneByLongPress = doneByLongPress,
        onTap = { onOpen(jelly) },
        onToggleDone = { onToggleDone(jelly) },
        modifier = Modifier
            .offset { position.value.round() }
            .size(widthDp, heightDp),
        cornerRadius = if (compact) 9.dp else 12.dp,
        bottomPull = { tension },
    ) { motion ->
        JellyLabel(
            title = jelly.title,
            subtitle = if (compact) hm(start) else range(start, shown),
            flavor = JellyFlavors[jelly.flavor],
            motion = motion,
            heightDp = heightDp.value,
            compact = compact,
            missed = jelly.status == JellyStatus.MISSED,
        )
        // A night shift's length is changed in its sheet: its end is not on this day.
        val canResize = jelly.status != JellyStatus.MISSED && !jelly.overnight && heightDp.value >= (if (compact) 40f else 30f)
        if (canResize) {
            ResizeHandle(
                motion = motion,
                startDuration = jelly.durationMin,
                maxDuration = MINUTES_PER_DAY - start,
                pxPerMin = geometry.pxPerMin,
                snap = snap,
                inkColor = JellyFlavors[jelly.flavor].deep,
                onPreview = { duration, pull ->
                    preview = duration
                    tension = pull
                },
                onCommit = { onResize(jelly, it) },
            )
        }
    }
}

@Composable
private fun BoxScope.ResizeHandle(
    motion: JellyMotion,
    startDuration: Int,
    maxDuration: Int,
    pxPerMin: Float,
    snap: Int,
    inkColor: Color,
    onPreview: (Int?, Float) -> Unit,
    onCommit: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .size(width = 40.dp, height = 18.dp)
            .pointerInput(startDuration, maxDuration, pxPerMin, snap) {
                var dy = 0f
                var current = startDuration
                val step = snap.coerceAtLeast(5)
                val maxPull = with(density) { 8.dp.toPx() }
                detectVerticalDragGestures(
                    onDragStart = {
                        dy = 0f
                        current = startDuration
                        motion.press()
                    },
                    onDragEnd = {
                        commit(current)
                        preview(null, 0f)
                        motion.release(0.9f)
                    },
                    onDragCancel = {
                        preview(null, 0f)
                        motion.release(0.3f)
                    },
                ) { change, amount ->
                    change.consume()
                    dy += amount
                    val raw = startDuration + dy / pxPerMin
                    val snapped = Planner.snap(raw.roundToInt(), step).coerceIn(step, maxDuration.coerceAtLeast(step))
                    if (snapped != current) {
                        current = snapped
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    val pull = ((raw - snapped) * pxPerMin).coerceIn(-maxPull / 2f, maxPull)
                    preview(current, pull)
                }
            },
    ) {
        Canvas(Modifier.align(Alignment.Center).size(width = 18.dp, height = 4.dp)) {
            drawRoundRect(inkColor.copy(alpha = 0.45f), cornerRadius = CornerRadius(size.height / 2f))
        }
    }
}
