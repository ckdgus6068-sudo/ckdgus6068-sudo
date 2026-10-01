package io.github.ckdgus6068.jellycalendar.ui.box

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.ui.drag.DragController
import io.github.ckdgus6068.jellycalendar.ui.drag.DragSource
import io.github.ckdgus6068.jellycalendar.ui.drag.DropTarget
import io.github.ckdgus6068.jellycalendar.ui.durationText
import io.github.ckdgus6068.jellycalendar.ui.hm
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyType
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Loud, flat colours in the spirit of the reference puzzle game, one per flavour. */
private val BOX_COLORS = listOf(
    Color(0xFFFF3DA5), Color(0xFFFF8A3D), Color(0xFFFFE14D), Color(0xFF8CF04F), Color(0xFF63F0C4),
    Color(0xFF43B4FF), Color(0xFF3F6BF2), Color(0xFFB05CFF), Color(0xFFD8F55A), Color(0xFFF03C5A),
)

fun boxColor(flavor: Int): Color = BOX_COLORS[Math.floorMod(flavor, BOX_COLORS.size)]

private val INK = Color(0xFF1A1320)

/** What a jelly's label depends on. The diameter is rounded so that squashing does not re-layout. */
private data class LabelKey(
    val title: String,
    val startMin: Int?,
    val durationMin: Int,
    val done: Boolean,
    val diameter: Int,
)

/** A jelly's text, laid out once for its size and drawn every frame. */
private class BlobLabel(
    val key: LabelKey,
    val title: TextLayoutResult,
    val sub: TextLayoutResult?,
    /** Size of the check mark drawn above a finished jelly's name, 0 when not finished. */
    val check: Float,
)

/**
 * One day as a box of soft jellies. Each jelly's area follows its length; they fall in order of
 * their start time and squash against each other until the box is packed.
 *
 * Tap: open. Double tap, or press and release: finish. Drag: shake it inside the box, or carry it
 * out onto a day above or into the tray below. A sideways swipe on an empty spot turns the day.
 * Jellies dragged from the tray can be dropped anywhere on the box.
 */
@Composable
fun JellyBoxBoard(
    date: LocalDate,
    jellies: List<Jelly>,
    drag: DragController,
    doneByDoubleTap: Boolean,
    doneByLongPress: Boolean,
    onOpen: (Jelly) -> Unit,
    onToggleDone: (Jelly) -> Unit,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val world = remember { SoftBodyWorld() }
    var frame by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val colors = LocalJellyColors.current
    val type = LocalJellyType.current
    val measurer = rememberTextMeasurer()
    val labels = remember(type, density) { HashMap<String, BlobLabel>() }
    val current by rememberUpdatedState(jellies)
    val open by rememberUpdatedState(onOpen)
    val toggle by rememberUpdatedState(onToggleDone)
    val swipe by rememberUpdatedState(onSwipe)
    val doubleTap by rememberUpdatedState(doneByDoubleTap)
    val longPress by rememberUpdatedState(doneByLongPress)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(Rect.Zero) }

    // Jellies dragged from the tray may be dropped anywhere on the box.
    SideEffect { drag.board = date to bounds }
    DisposableEffect(drag) {
        onDispose { drag.board = null }
    }
    val dropHere = drag.session != null && (drag.hover as? DropTarget.Day)?.date == date

    // Frame
    Box(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF2B2B31))
            .border(3.dp, if (dropHere) colors.accent else Color.Transparent, RoundedCornerShape(30.dp))
            .padding(10.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF050507))
                .onGloballyPositioned {
                    origin = it.positionInRoot()
                    bounds = it.boundsInRoot()
                },
        ) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val pad = with(density) { 3.dp.toPx() }

            // Keep the world in step with the day's jellies.
            val ordered = jellies.sortedBy { it.startMin ?: 0 }
            val totalArea = w * h
            val wanted = ordered.associate { it.id to max(it.durationMin, 10) / 540f * 0.8f * totalArea }
            val sum = wanted.values.sum()
            val scale = if (sum > 0.82f * totalArea) 0.82f * totalArea / sum else 1f
            LaunchedEffect(w, h) { world.resize(w, h, pad) }
            LaunchedEffect(ordered.map { it.id to it.durationMin }, w, h) {
                world.resize(w, h, pad)
                val ids = ordered.map { it.id }.toSet()
                world.blobs.keys.filter { it !in ids }.forEach { world.remove(it) }
                labels.keys.retainAll(ids)
                var spawnY = -40f
                ordered.forEachIndexed { index, jelly ->
                    val area = (wanted[jelly.id] ?: 0f) * scale
                    val blob = world.blobs[jelly.id]
                    if (blob == null) {
                        val r = sqrt(area / PI.toFloat())
                        spawnY -= r * 2.1f
                        val x = (w * (0.3f + 0.4f * ((index * 37) % 10) / 10f)).coerceIn(r + pad, w - r - pad)
                        world.add(jelly.id, area, x, spawnY + r)
                    } else if (abs(blob.targetArea - area) > 1f) {
                        blob.resize(area)
                        world.wake()
                    }
                }
            }
            LaunchedEffect(world) {
                val gravity = with(density) { 0.35.dp.toPx() }
                world.quietDrift = with(density) { 0.8.dp.toPx() }
                while (true) {
                    if (world.isResting) world.awaitWake()
                    withFrameNanos {
                        if (!world.isResting) {
                            world.step(gravity)
                            frame++
                        }
                    }
                }
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(world) {
                        val swipeDistance = 72.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val blob = world.blobAt(down.position.x, down.position.y)
                            if (blob == null) {
                                // Empty spot: a sideways swipe turns the day.
                                var travel = Offset.Zero
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    travel += change.positionChange()
                                }
                                if (abs(travel.x) > swipeDistance && abs(travel.x) > abs(travel.y) * 1.5f) {
                                    swipe(if (travel.x < 0) 1 else -1)
                                }
                                return@awaitEachGesture
                            }
                            val id = blob.id
                            blob.kick(-0.03f)
                            world.wake()

                            // Released quickly, moved, or held?
                            val slop = viewConfiguration.touchSlop
                            var released = false
                            var movedTo: Offset? = null
                            val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        change?.consume()
                                        released = true
                                        break
                                    }
                                    if ((change.position - down.position).getDistance() > slop) {
                                        movedTo = change.position
                                        break
                                    }
                                }
                            } == null

                            if (released) {
                                val tapped = current.find { it.id == id } ?: return@awaitEachGesture
                                if (doubleTap) {
                                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                        awaitFirstDown()
                                    }
                                    if (second != null) {
                                        val again = world.blobAt(second.position.x, second.position.y)
                                        val up = waitForUpOrCancellation()
                                        if (again?.id == id && up != null) {
                                            up.consume()
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            world.blobs[id]?.kick(0.12f)
                                            world.wake()
                                            toggle(tapped)
                                            return@awaitEachGesture
                                        }
                                    }
                                }
                                open(tapped)
                                return@awaitEachGesture
                            }

                            // Held or dragged: the jelly follows the finger inside the box. Once the finger
                            // leaves the box, the calendar-wide drag takes over and floats the jelly over
                            // the screen, so it can be dropped on a day above or into the tray below.
                            val jelly = current.find { it.id == id } ?: return@awaitEachGesture
                            if (held) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val start = movedTo ?: down.position
                            world.grab(blob, start.x, start.y)
                            var dragging = movedTo != null
                            var handedOver = false
                            var travel = Offset.Zero
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    val root = origin + change.position
                                    if (!change.pressed) {
                                        change.consume()
                                        if (handedOver) {
                                            drag.end()
                                        } else if (!dragging && longPress) {
                                            world.blobs[id]?.kick(0.12f)
                                            toggle(jelly)
                                        }
                                        break
                                    }
                                    if (!dragging) {
                                        travel += change.positionChange()
                                        if (travel.getDistance() > slop * 0.5f) dragging = true
                                    }
                                    if (dragging && !handedOver && !bounds.contains(root)) {
                                        val pill = drag.tray?.pillSize(jelly.durationMin)
                                            ?: Size(120.dp.toPx(), 50.dp.toPx())
                                        world.release()
                                        drag.start(
                                            jelly = jelly,
                                            source = DragSource.BOX,
                                            grabFraction = Offset(0.5f, 0.5f),
                                            startRect = Rect(Offset(root.x - pill.width / 2f, root.y - pill.height / 2f), pill),
                                            pointerRoot = root,
                                        )
                                        handedOver = true
                                    }
                                    if (handedOver) {
                                        drag.move(root, change.uptimeMillis)
                                    } else if (dragging) {
                                        world.grabX = change.position.x
                                        world.grabY = change.position.y
                                        world.wake()
                                    }
                                    change.consume()
                                }
                            } finally {
                                if (handedOver) drag.cancel() else world.release()
                            }
                        }
                    },
            ) {
                if (frame < 0) return@Canvas
                val path = Path()
                for (blob in world.blobs.values) {
                    val jelly = current.find { it.id == blob.id } ?: continue
                    blobPath(blob, path)
                    val base = boxColor(jelly.flavor)
                    val color = if (jelly.isDone) lerp(base, Color.Black, 0.35f) else base
                    // Carried away over the calendar: only a faint shape stays behind.
                    val away = drag.isGhost(jelly.id) || drag.isHidden(jelly.id)
                    drawPath(path, if (away) color.copy(alpha = 0.25f) else color)
                    if (away) continue
                    if (jelly.isDone) drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.5.dp.toPx()))
                    if (blob.id == world.grabId) drawPath(path, Color.White, style = Stroke(width = 3.dp.toPx()))

                    val key = LabelKey(
                        title = jelly.title.ifBlank { "이름 없는 젤리" },
                        startMin = jelly.startMin,
                        durationMin = jelly.durationMin,
                        done = jelly.isDone,
                        diameter = (2f * sqrt(blob.targetArea / PI.toFloat()) / 4f).roundToInt() * 4,
                    )
                    val label = labels[jelly.id]?.takeIf { it.key == key }
                        ?: layoutLabel(measurer, key, type, this).also { labels[jelly.id] = it }
                    drawLabel(label, blob.centroidX(), blob.centroidY())
                }
            }

            if (jellies.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "이 날은 비어 있어요",
                        color = Color(0xFFD9D4E0),
                        fontSize = 18.sp,
                        fontFamily = type.display,
                        fontWeight = type.displayWeight,
                    )
                    Text(
                        keepWords("+ 버튼으로 만들거나, 보관함 젤리를 꾹 눌러 여기로 끌어 오세요"),
                        color = Color(0xFF8E8A96),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * Lays out a jelly's name as large as its size allows (two lines at most), with its time and
 * length below when there is room.
 */
private fun layoutLabel(measurer: TextMeasurer, key: LabelKey, type: JellyType, density: Density): BlobLabel {
    val d = key.diameter.toFloat()
    val px = density.density
    val maxWidth = (d * 0.74f).toInt().coerceAtLeast(1)
    val maxHeight = d * 0.62f
    val ink = if (key.done) Color.White else INK

    val minTitle = 10f * px
    var titleSize = (d * 0.17f).coerceIn(13f * px, 30f * px)
    var title: TextLayoutResult
    while (true) {
        title = measurer.measure(
            text = keepWords(key.title),
            style = TextStyle(
                color = ink,
                fontFamily = type.display,
                fontWeight = type.displayWeight,
                fontSize = with(density) { titleSize.toSp() },
                lineHeight = with(density) { (titleSize * 1.12f).toSp() },
                textAlign = TextAlign.Center,
            ),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            constraints = Constraints(maxWidth = maxWidth),
            density = density,
        )
        val fits = !title.hasVisualOverflow && title.size.height <= maxHeight * 0.72f
        if (fits || titleSize <= minTitle) break
        titleSize = max(minTitle, titleSize * 0.88f)
    }

    val subStyle = TextStyle(
        color = ink.copy(alpha = 0.78f),
        fontFamily = type.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = with(density) { max(9.5f * px, titleSize * 0.52f).toSp() },
        textAlign = TextAlign.Center,
    )
    val candidates = listOfNotNull(
        key.startMin?.let { "${hm(it)} · ${durationText(key.durationMin)}" },
        durationText(key.durationMin),
    )
    var sub: TextLayoutResult? = null
    for (text in candidates) {
        val layout = measurer.measure(
            text = text,
            style = subStyle,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth),
            density = density,
        )
        if (!layout.hasVisualOverflow && title.size.height + layout.size.height <= maxHeight) {
            sub = layout
            break
        }
    }

    val used = title.size.height + (sub?.size?.height ?: 0)
    val check = if (key.done) (titleSize * 0.95f).coerceAtMost(max(0f, maxHeight - used)) else 0f
    return BlobLabel(key, title, sub, if (check >= 8f * px) check else 0f)
}

private fun DrawScope.drawLabel(label: BlobLabel, cx: Float, cy: Float) {
    val gap = if (label.check > 0f) label.check * 0.15f else 0f
    val total = label.check + gap + label.title.size.height + (label.sub?.size?.height ?: 0)
    var y = cy - total / 2f
    if (label.check > 0f) {
        drawCheck(Offset(cx, y + label.check / 2f), label.check)
        y += label.check + gap
    }
    drawText(label.title, topLeft = Offset(cx - label.title.size.width / 2f, y))
    y += label.title.size.height
    label.sub?.let { drawText(it, topLeft = Offset(cx - it.size.width / 2f, y)) }
}

private fun DrawScope.drawCheck(center: Offset, size: Float) {
    val path = Path().apply {
        moveTo(center.x - size * 0.34f, center.y + size * 0.02f)
        lineTo(center.x - size * 0.1f, center.y + size * 0.26f)
        lineTo(center.x + size * 0.36f, center.y - size * 0.24f)
    }
    drawPath(
        path,
        Color.White,
        style = Stroke(width = size * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Smooth closed curve through the blob's ring of points. */
private fun blobPath(b: SoftBlob, path: Path) {
    path.reset()
    val n = b.n
    path.moveTo((b.x[0] + b.x[n - 1]) / 2f, (b.y[0] + b.y[n - 1]) / 2f)
    for (i in 0 until n) {
        val j = if (i + 1 == n) 0 else i + 1
        path.quadraticBezierTo(b.x[i], b.y[i], (b.x[i] + b.x[j]) / 2f, (b.y[i] + b.y[j]) / 2f)
    }
    path.close()
}
