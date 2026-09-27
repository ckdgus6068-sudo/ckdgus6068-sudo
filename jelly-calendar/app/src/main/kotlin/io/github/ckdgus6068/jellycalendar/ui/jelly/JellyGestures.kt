package io.github.ckdgus6068.jellycalendar.ui.jelly

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.coroutines.cancellation.CancellationException

/** Callbacks of [detectJellyGestures]. Positions are local to the jelly. */
interface JellyGestureHandler {
    fun onPress() {}
    fun onPressEnd() {}
    fun onTap() {}
    fun onDoubleTap() {}

    /** The finger stayed down long enough: the jelly is picked up. */
    fun onPickUp() {}

    /** Picked up and let go without moving. */
    fun onPickUpRelease() {}
    fun onDragStart(position: Offset) {}
    fun onDrag(position: Offset, uptimeMillis: Long) {}
    fun onDragEnd() {}
    fun onDragCancel() {}
}

/**
 * One detector for everything a jelly understands:
 * tap, double tap, long press (pick up) and long press followed by a drag.
 *
 * A quick swipe is not consumed, so the calendar underneath can still scroll.
 */
suspend fun PointerInputScope.detectJellyGestures(
    handler: () -> JellyGestureHandler,
    doubleTapEnabled: () -> Boolean,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Claim the touch so that "press on empty space" handlers underneath stay quiet.
        // Scrolling still works: scroll containers do not require an unconsumed down.
        down.consume()
        val h = handler()
        h.onPress()
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            h.onPressEnd()
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up == null || up.pressed || up.isConsumed) return@awaitEachGesture
            up.consume()
            if (!doubleTapEnabled()) {
                h.onTap()
                return@awaitEachGesture
            }
            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                awaitFirstDown(requireUnconsumed = false)
            }
            if (second == null) {
                h.onTap()
                return@awaitEachGesture
            }
            second.consume()
            h.onPress()
            val secondUp = waitForUpOrCancellation()
            h.onPressEnd()
            if (secondUp != null) {
                secondUp.consume()
                h.onDoubleTap()
            } else {
                h.onTap()
            }
            return@awaitEachGesture
        }

        h.onPickUp()
        longPress.consume()
        val pointer = longPress.id
        val slop = viewConfiguration.touchSlop * 0.5f
        var dragging = false
        var travelled = Offset.Zero
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointer } ?: break
                if (!change.pressed) {
                    change.consume()
                    if (dragging) {
                        h.onDragEnd()
                    } else {
                        h.onPressEnd()
                        h.onPickUpRelease()
                    }
                    return@awaitEachGesture
                }
                if (!dragging) {
                    travelled += change.positionChange()
                    if (travelled.getDistance() > slop) {
                        dragging = true
                        h.onDragStart(change.position)
                    }
                }
                if (dragging) h.onDrag(change.position, change.uptimeMillis)
                change.consume()
            }
            if (dragging) h.onDragCancel() else h.onPressEnd()
        } catch (e: CancellationException) {
            if (dragging) h.onDragCancel() else h.onPressEnd()
            throw e
        }
    }
}
