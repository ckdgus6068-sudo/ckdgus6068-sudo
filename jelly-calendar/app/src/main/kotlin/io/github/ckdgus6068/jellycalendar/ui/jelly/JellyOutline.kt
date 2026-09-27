package io.github.ckdgus6068.jellycalendar.ui.jelly

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The outline of a soft jelly.
 *
 * The rounded rectangle is sampled into evenly spaced points; every frame the points are pushed
 * in and out along their normals (idle breathing, impact wobble, drag lean) and a smooth closed
 * curve is drawn through them. Nothing here allocates per frame.
 */
class JellyOutline {
    private var count = 0
    private var baseX = FloatArray(0)
    private var baseY = FloatArray(0)
    private var normalX = FloatArray(0)
    private var normalY = FloatArray(0)
    private var angle = FloatArray(0)
    private var x = FloatArray(0)
    private var y = FloatArray(0)

    private var cachedW = -1f
    private var cachedH = -1f
    private var cachedR = -1f

    var width = 0f
        private set
    var height = 0f
        private set

    /** Samples the resting shape. Cheap to call every frame: it only works when the size changes. */
    fun prepare(w: Float, h: Float, radius: Float, spacing: Float) {
        if (w == cachedW && h == cachedH && radius == cachedR) return
        cachedW = w
        cachedH = h
        cachedR = radius
        width = w
        height = h
        val r = min(radius, min(w, h) / 2f).coerceAtLeast(0.5f)
        val straightW = (w - 2 * r).coerceAtLeast(0f)
        val straightH = (h - 2 * r).coerceAtLeast(0f)
        val arc = (PI / 2 * r).toFloat()
        val total = 2 * straightW + 2 * straightH + 4 * arc
        val n = (total / spacing.coerceAtLeast(1f)).roundToInt().coerceIn(24, 96)
        if (n != count) {
            count = n
            baseX = FloatArray(n)
            baseY = FloatArray(n)
            normalX = FloatArray(n)
            normalY = FloatArray(n)
            angle = FloatArray(n)
            x = FloatArray(n)
            y = FloatArray(n)
        }
        val cx = w / 2f
        val cy = h / 2f
        val segments = floatArrayOf(straightW, arc, straightH, arc, straightW, arc, straightH, arc)
        for (i in 0 until n) {
            var s = i * total / n
            var k = 0
            while (k < 7 && s >= segments[k]) {
                s -= segments[k]
                k++
            }
            when (k) {
                0 -> put(i, r + s, 0f, 0f, -1f)
                1 -> putArc(i, w - r, r, (-PI / 2 + s / r).toFloat(), r)
                2 -> put(i, w, r + s, 1f, 0f)
                3 -> putArc(i, w - r, h - r, (s / r), r)
                4 -> put(i, w - r - s, h, 0f, 1f)
                5 -> putArc(i, r, h - r, (PI / 2 + s / r).toFloat(), r)
                6 -> put(i, 0f, h - r - s, -1f, 0f)
                else -> putArc(i, r, r, (PI + s / r).toFloat(), r)
            }
            // Angle around the centre, measured as if the jelly were square so that
            // long and short jellies wobble with the same number of lobes.
            angle[i] = atan2((baseY[i] - cy) / h.coerceAtLeast(1f), (baseX[i] - cx) / w.coerceAtLeast(1f))
        }
    }

    private fun put(i: Int, px: Float, py: Float, nx: Float, ny: Float) {
        baseX[i] = px
        baseY[i] = py
        normalX[i] = nx
        normalY[i] = ny
    }

    private fun putArc(i: Int, cx: Float, cy: Float, a: Float, r: Float) {
        val nx = cos(a)
        val ny = sin(a)
        put(i, cx + r * nx, cy + r * ny, nx, ny)
    }

    /**
     * Pushes the outline around.
     *
     * @param idleAmp breathing amplitude in px, driven by [time] (seconds)
     * @param wobble impact wobble, an oscillating value around zero
     * @param wobbleAmp px of bulge for wobble = 1
     * @param leanX horizontal lag in px of the parts far from the grab point (drag)
     * @param leanY vertical lag in px
     * @param grabX grab point x in px
     * @param grabY grab point y in px
     * @param bottomPull extra px the bottom edge bulges (stretching while resizing)
     */
    fun deform(
        time: Float,
        phase: Float,
        idleAmp: Float,
        wobble: Float,
        wobbleAmp: Float,
        leanX: Float,
        leanY: Float,
        grabX: Float,
        grabY: Float,
        bottomPull: Float,
    ) {
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        // Tiny jellies must not turn inside out.
        val limit = min(w, h) * 0.18f
        val idle = min(idleAmp, limit)
        val impact = min(wobbleAmp, limit * 1.4f)
        for (i in 0 until count) {
            val th = angle[i]
            var d = 0f
            if (idle != 0f) {
                d += idle * (0.55f * cos(2f * th + time * 1.9f + phase) +
                    0.45f * sin(3f * th - time * 2.6f + phase * 1.7f))
            }
            if (wobble != 0f) {
                d += wobble * impact * (0.8f * cos(2f * th) + 0.35f * cos(3f * th + phase))
            }
            var px = baseX[i] + normalX[i] * d
            var py = baseY[i] + normalY[i] * d
            if (leanX != 0f) {
                val t = (baseY[i] - grabY) / h
                px += leanX * min(t * t * 2.4f, 1.35f)
            }
            if (leanY != 0f) {
                val t = (baseX[i] - grabX) / w
                py += leanY * min(t * t * 2.4f, 1.35f)
            }
            if (bottomPull != 0f && normalY[i] > 0f) {
                val u = (baseX[i] / w).coerceIn(0f, 1f)
                py += bottomPull * normalY[i] * sin((PI * u).toFloat())
            }
            x[i] = px
            y[i] = py
        }
    }

    /** Writes a smooth closed Catmull-Rom curve through the deformed points into [path]. */
    fun writeTo(path: Path) {
        path.reset()
        val n = count
        if (n < 3) return
        path.moveTo(x[0], y[0])
        for (i in 0 until n) {
            val i0 = if (i == 0) n - 1 else i - 1
            val i2 = if (i + 1 >= n) i + 1 - n else i + 1
            val i3 = if (i + 2 >= n) i + 2 - n else i + 2
            val c1x = x[i] + (x[i2] - x[i0]) / 6f
            val c1y = y[i] + (y[i2] - y[i0]) / 6f
            val c2x = x[i2] - (x[i3] - x[i]) / 6f
            val c2y = y[i2] - (y[i3] - y[i]) / 6f
            path.cubicTo(c1x, c1y, c2x, c2y, x[i2], y[i2])
        }
        path.close()
    }

    /** Sample count, for tests. */
    val size: Int get() = count

    fun pointX(i: Int): Float = x[i]
    fun pointY(i: Int): Float = y[i]
}
