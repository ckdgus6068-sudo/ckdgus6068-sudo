package io.github.ckdgus6068.jellycalendar.ui.drag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.core.JellyStatus
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyBody
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyGestureHandler
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyLabel
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyLook
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyMotion
import io.github.ckdgus6068.jellycalendar.ui.jelly.detectJellyGestures
import io.github.ckdgus6068.jellycalendar.ui.jelly.rememberJellyMotion
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavors
import kotlin.math.roundToInt

private class CoordsHolder {
    var coords: LayoutCoordinates? = null

    fun rect(): Rect? {
        val c = coords ?: return null
        if (!c.isAttached) return null
        return Rect(c.positionInRoot(), c.size.toSize())
    }
}

/**
 * A jelly you can poke: tap to open, double tap or press and release to finish,
 * press and drag to carry it anywhere on the calendar.
 */
@Composable
fun DraggableJelly(
    jelly: Jelly,
    source: DragSource,
    drag: DragController,
    doneByDoubleTap: Boolean,
    doneByLongPress: Boolean,
    onTap: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    bottomPull: (() -> Float)? = null,
    content: @Composable BoxScope.(JellyMotion) -> Unit,
) {
    val flavor = JellyFlavors[jelly.flavor]
    val motion = rememberJellyMotion(jelly.id, jelly.isDone)
    val haptics = LocalHapticFeedback.current
    val holder = remember { CoordsHolder() }
    val currentJelly by rememberUpdatedState(jelly)
    val tap by rememberUpdatedState(onTap)
    val toggle by rememberUpdatedState(onToggleDone)
    val doubleTap by rememberUpdatedState(doneByDoubleTap)
    val longPress by rememberUpdatedState(doneByLongPress)

    DisposableEffect(jelly.id, drag) {
        val provider: () -> Rect? = { holder.rect() }
        drag.register(jelly.id, provider)
        onDispose { drag.unregister(jelly.id, provider) }
    }
    val arrival = drag.arrival
    LaunchedEffect(arrival) {
        if (arrival?.jellyId == jelly.id) motion.land()
    }

    val handler = remember(drag, motion) {
        object : JellyGestureHandler {
            override fun onPress() = motion.press()
            override fun onPressEnd() = motion.release()
            override fun onTap() = tap()
            override fun onDoubleTap() {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                toggle()
            }

            override fun onPickUp() {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                motion.pickUp()
            }

            override fun onPickUpRelease() {
                motion.putDown()
                if (longPress) toggle()
            }

            override fun onDragStart(position: Offset) {
                val c = holder.coords ?: return
                if (!c.isAttached) return
                val size = c.size.toSize()
                if (size.width <= 0f || size.height <= 0f) return
                drag.start(
                    jelly = currentJelly,
                    source = source,
                    grabFraction = Offset(position.x / size.width, position.y / size.height),
                    startRect = Rect(c.positionInRoot(), size),
                    pointerRoot = c.localToRoot(position),
                )
                motion.rest()
            }

            override fun onDrag(position: Offset, uptimeMillis: Long) {
                val c = holder.coords ?: return
                if (c.isAttached) drag.move(c.localToRoot(position), uptimeMillis)
            }

            override fun onDragEnd() = drag.end()
            override fun onDragCancel() = drag.cancel()
        }
    }

    val alpha = when {
        drag.isHidden(jelly.id) -> 0f
        drag.isGhost(jelly.id) -> 0.28f
        else -> 1f
    }
    JellyBody(
        flavor = flavor,
        modifier = modifier
            .onGloballyPositioned { holder.coords = it }
            .pointerInput(drag, motion) {
                detectJellyGestures(handler = { handler }, doubleTapEnabled = { doubleTap })
            },
        motion = motion,
        look = if (jelly.status == JellyStatus.MISSED) JellyLook.MISSED else JellyLook.NORMAL,
        cornerRadius = cornerRadius,
        bottomPull = bottomPull,
        alpha = alpha,
    ) {
        content(motion)
    }
}

/** Places a child at a rectangle (in the parent's pixels) read during layout, so moving it never recomposes. */
fun Modifier.placeAt(rect: () -> Rect): Modifier = layout { measurable, constraints ->
    val r = rect()
    val w = r.width.roundToInt().coerceAtLeast(1)
    val h = r.height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(r.left.roundToInt(), r.top.roundToInt())
    }
}

/**
 * Draws the jelly that is being carried (and the short flight after letting go) above everything.
 * [caption] can describe where it would land, e.g. "06:10 - 07:00".
 */
@Composable
fun DragOverlay(
    drag: DragController,
    compact: Boolean,
    caption: (Jelly, DropTarget?) -> String?,
    modifier: Modifier = Modifier,
) {
    val session = drag.session
    val flight = drag.flight
    val jelly = session?.jelly ?: flight?.jelly ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val motion = rememberJellyMotion("overlay-" + jelly.id, jelly.isDone)
    val density = LocalDensity.current

    LaunchedEffect(drag) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                drag.step(now, dt)
            }
        }
    }
    LaunchedEffect(jelly.id) { motion.hold() }
    LaunchedEffect(flight) { if (flight != null) motion.putDown() }

    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val flavor = JellyFlavors[jelly.flavor]
        JellyBody(
            flavor = flavor,
            modifier = Modifier.placeAt { drag.visual.rect.translate(-origin) },
            motion = motion,
            lean = drag.visual,
            cornerRadius = if (compact) 9.dp else 12.dp,
        ) {
            val heightDp = with(density) { drag.visual.rect.height.toDp().value }
            JellyLabel(
                title = jelly.title,
                subtitle = caption(jelly, drag.hover ?: drag.landingTarget),
                flavor = flavor,
                motion = motion,
                heightDp = heightDp,
                compact = compact,
            )
        }
    }
}
