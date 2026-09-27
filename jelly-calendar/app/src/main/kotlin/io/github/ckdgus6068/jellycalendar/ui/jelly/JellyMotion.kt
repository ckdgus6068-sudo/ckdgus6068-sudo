package io.github.ckdgus6068.jellycalendar.ui.jelly

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The bouncy state of one jelly. Values are read only while drawing, so animating them
 * redraws the jelly without recomposing anything.
 */
@Stable
class JellyMotion(private val scope: CoroutineScope, done: Boolean) {
    /** Positive squashes the jelly flat, negative stretches it tall. */
    val squash = Animatable(0f)

    /** Oscillates around zero after a hit; the outline bulges with it. */
    val wobble = Animatable(0f)

    /** Liquid level of the "done" colour, 0..1. */
    val fill = Animatable(if (done) 1f else 0f)

    /** Height of the waves on the liquid surface, 0..1. */
    val wave = Animatable(0f)

    /** Picked up by a long press: slightly bigger with a deeper shadow. */
    val lift = Animatable(0f)

    val phase: Float = Random.nextFloat() * 6.283f

    fun press() {
        scope.launch { squash.animateTo(0.07f, spring(dampingRatio = 0.7f, stiffness = 900f)) }
    }

    fun release(kick: Float = 0.5f) {
        scope.launch { squash.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 380f)) }
        if (kick > 0f) kick(kick)
    }

    fun kick(strength: Float) {
        scope.launch {
            wobble.snapTo(0f)
            wobble.animateTo(0f, spring(dampingRatio = 0.16f, stiffness = 170f), initialVelocity = strength * 15f)
        }
    }

    fun pickUp() {
        scope.launch { squash.animateTo(-0.05f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
        scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
        kick(0.7f)
    }

    fun putDown() {
        scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
        release(0.4f)
    }

    /** The overlay takes over while dragging: the jelly left behind rests immediately. */
    fun rest() {
        scope.launch { lift.snapTo(0f) }
        scope.launch { squash.snapTo(0f) }
    }

    fun hold() {
        scope.launch { lift.snapTo(1f) }
    }

    /** Plops down after a drop: squash, then jiggle. */
    fun land() {
        scope.launch {
            squash.snapTo(0.22f)
            squash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 300f))
        }
        scope.launch { lift.snapTo(0f) }
        kick(1f)
    }

    fun setDone(done: Boolean) {
        val target = if (done) 1f else 0f
        if (fill.targetValue == target) return
        scope.launch {
            if (done) {
                launch {
                    wave.snapTo(1f)
                    wave.animateTo(0f, tween(durationMillis = 1600, easing = LinearOutSlowInEasing))
                }
                launch {
                    squash.snapTo(-0.14f)
                    squash.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 260f))
                }
                kick(1.2f)
                fill.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 38f))
            } else {
                kick(0.6f)
                fill.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 140f))
            }
        }
    }
}

@Composable
fun rememberJellyMotion(key: Any, done: Boolean): JellyMotion {
    val scope = rememberCoroutineScope()
    val motion = remember(key) { JellyMotion(scope, done) }
    LaunchedEffect(motion, done) { motion.setDone(done) }
    return motion
}

/**
 * A damped spring advanced by hand once per frame. Used where the target changes every frame
 * (following a finger), which keeps the motion continuous.
 */
class FrameSpring(initial: Float = 0f) {
    var value = initial
    var velocity = 0f

    fun step(target: Float, dt: Float, stiffness: Float, dampingRatio: Float) {
        val damping = 2f * dampingRatio * sqrt(stiffness)
        var remaining = dt.coerceAtMost(0.05f)
        // Sub-steps keep stiff springs stable on slow frames.
        while (remaining > 0f) {
            val h = remaining.coerceAtMost(1f / 120f)
            val accel = -stiffness * (value - target) - damping * velocity
            velocity += accel * h
            value += velocity * h
            remaining -= h
        }
    }

    fun snap(to: Float) {
        value = to
        velocity = 0f
    }

    fun isSettled(target: Float, epsilon: Float): Boolean =
        kotlin.math.abs(value - target) < epsilon && kotlin.math.abs(velocity) < epsilon * 10f
}

/** Seconds since the calendar appeared; ticks at about 30 fps while breathing is enabled. */
val LocalJellyClock = compositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** Whether resting jellies breathe. */
val LocalIdleWobble = compositionLocalOf { true }

@Composable
fun rememberJellyClock(running: Boolean): State<Float> {
    val time = remember { mutableStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var origin = -1L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (origin < 0L) origin = now - (time.value * 1_000_000_000L).toLong()
                if (now - last >= 33_000_000L) {
                    last = now
                    time.value = (now - origin) / 1_000_000_000f
                }
            }
        }
    }
    return time
}
