package io.github.ckdgus6068.jellycalendar.ui.box

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.ui.drag.DragController
import io.github.ckdgus6068.jellycalendar.ui.durationText
import kotlin.math.max
import kotlin.math.sqrt

/** Loud, flat colours in the spirit of the reference puzzle game, one per flavour. */
private val BOX_COLORS = listOf(
    Color(0xFFFF3DA5), Color(0xFFFF8A3D), Color(0xFFFFE14D), Color(0xFF8CF04F), Color(0xFF63F0C4),
    Color(0xFF43B4FF), Color(0xFF3F6BF2), Color(0xFFB05CFF), Color(0xFFD8F55A), Color(0xFFF03C5A),
)

fun boxColor(flavor: Int): Color = BOX_COLORS[Math.floorMod(flavor, BOX_COLORS.size)]

/**
 * One day as a box of soft jellies. Each jelly's area follows its length, they fall in order of
 * their start time and squash against each other until the box is packed. Tap to open,
 * double tap (or press and release) to finish, drag one to shake it or throw it into the tray.
 */
@Composable
fun JellyBoxBoard(
    jellies: List<Jelly>,
    drag: DragController,
    doneByDoubleTap: Boolean,
    doneByLongPress: Boolean,
    onOpen: (Jelly) -> Unit,
    onToggleDone: (Jelly) -> Unit,
    onSendToTray: (Jelly) -> Unit,
    modifier: Modifier = Modifier,
) {
    val world = remember { SoftBodyWorld() }
    var frame by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val current by rememberUpdatedState(jellies)
    val open by rememberUpdatedState(onOpen)
    val toggle by rememberUpdatedState(onToggleDone)
    val toTray by rememberUpdatedState(onSendToTray)
    val doubleTap by rememberUpdatedState(doneByDoubleTap)
    val longPress by rememberUpdatedState(doneByLongPress)
    var origin by remember { mutableStateOf(Offset.Zero) }

    // Frame
    Box(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF2B2B31))
            .padding(10.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF050507))
                .onGloballyPositioned { origin = it.positionInRoot() },
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
                var spawnY = -40f
                ordered.forEachIndexed { index, jelly ->
                    val area = (wanted[jelly.id] ?: 0f) * scale
                    val blob = world.blobs[jelly.id]
                    if (blob == null) {
                        val r = sqrt(area / Math.PI.toFloat())
                        spawnY -= r * 2.1f
                        val x = (w * (0.3f + 0.4f * ((index * 37) % 10) / 10f)).coerceIn(r + pad, w - r - pad)
                        world.add(jelly.id, area, x, spawnY + r)
                    } else if (kotlin.math.abs(blob.targetArea - area) > 1f) {
                        blob.resize(area)
                        world.wake()
                    }
                }
            }
            LaunchedEffect(world) {
                val gravity = with(density) { 0.35.dp.toPx() }
                while (true) {
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
                        detectTapGestures(
                            onTap = { p -> world.blobAt(p.x, p.y)?.let { b -> current.find { it.id == b.id }?.let(open) } },
                            onDoubleTap = { p ->
                                if (doubleTap) {
                                    world.blobAt(p.x, p.y)?.let { b ->
                                        current.find { it.id == b.id }?.let {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            b.kick(0.12f); world.wake(); toggle(it)
                                        }
                                    }
                                }
                            },
                            onLongPress = { p ->
                                if (longPress) {
                                    world.blobAt(p.x, p.y)?.let { b ->
                                        current.find { it.id == b.id }?.let {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            b.kick(0.12f); world.wake(); toggle(it)
                                        }
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(world) {
                        detectDragGestures(
                            onDragStart = { p ->
                                world.blobAt(p.x, p.y)?.let {
                                    world.grabId = it.id
                                    world.grabX = p.x; world.grabY = p.y
                                    world.wake()
                                }
                            },
                            onDragEnd = {
                                val id = world.grabId
                                world.grabId = null
                                world.wake()
                                val tray = drag.tray?.bounds
                                val root = origin + Offset(world.grabX, world.grabY)
                                if (id != null && tray != null && tray.contains(root)) {
                                    current.find { it.id == id }?.let(toTray)
                                }
                            },
                            onDragCancel = { world.grabId = null; world.wake() },
                        ) { change, amount ->
                            if (world.grabId != null) {
                                change.consume()
                                world.grabX += amount.x; world.grabY += amount.y
                                world.wake()
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
                    drawPath(path, color)
                    if (jelly.isDone) drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.5.dp.toPx()))
                    if (blob.id == world.grabId) drawPath(path, Color.White, style = Stroke(width = 3.dp.toPx()))

                    // Label: length big like the numbers in the game, title small below.
                    val size = sqrt(kotlin.math.abs(blob.area()))
                    val big = (size / 5.2f).coerceIn(9f * density.density, 34f * density.density)
                    val ink = if (jelly.isDone) Color.White else Color(0xFF1A1320)
                    val head = (if (jelly.isDone) "✓ " else "") + durationText(jelly.durationMin)
                    val headLayout = measurer.measure(
                        head,
                        TextStyle(color = ink, fontSize = (big / density.density).sp, fontWeight = FontWeight.Black),
                    )
                    val titleLayout = measurer.measure(
                        jelly.title,
                        TextStyle(
                            color = ink.copy(alpha = 0.85f),
                            fontSize = (big * 0.45f / density.density).coerceAtLeast(9f).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        constraints = Constraints(maxWidth = max(1, (size * 0.9f).toInt())),
                    )
                    val cx = blob.centroidX(); val cy = blob.centroidY()
                    val totalH = headLayout.size.height + titleLayout.size.height
                    drawText(headLayout, topLeft = Offset(cx - headLayout.size.width / 2f, cy - totalH / 2f))
                    if (size > 70f * density.density / 1.5f) {
                        drawText(titleLayout, topLeft = Offset(cx - titleLayout.size.width / 2f, cy - totalH / 2f + headLayout.size.height))
                    }
                }
            }

            if (jellies.isEmpty()) {
                Text(
                    "이 날은 비어 있어요\n+ 버튼이나 보관함에서 젤리를 넣어 보세요",
                    color = Color(0xFF8E8A96),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
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
