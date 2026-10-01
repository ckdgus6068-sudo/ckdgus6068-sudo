package io.github.ckdgus6068.jellycalendar.ui.box

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.channels.Channel

/**
 * One soft jelly: a ring of points held together by edge springs and an area ("pressure")
 * constraint, simulated with position-based dynamics. Neighbours push each other, so a box of
 * them squashes into a tightly packed, gap-free pile.
 */
class SoftBlob(
    val id: String,
    cx: Float,
    cy: Float,
    area: Float,
    /**
     * A pinned jelly: it hangs from a pin at the top of the box. The pin grips the top of it and a
     * weak spring draws its middle back under the pin; the rest of its body stays soft.
     */
    val fixed: Boolean = false,
) {
    /** Where a pinned jelly's middle belongs. */
    var homeX = cx
        private set
    var homeY = cy
        private set
    /**
     * Still falling in from above the pinned row: it slips behind the pinned jellies instead of
     * landing on them, so the space below them stays reachable.
     */
    var passing = !fixed

    var targetArea = area
        private set
    val n: Int = (12 + sqrt(area) / 9f).toInt().coerceIn(14, 28)

    /** The point at the top, where the pin goes in (the ring starts on the right and runs clockwise). */
    private val pinIndex = (3f * n / 4f).roundToInt() % n
    val x = FloatArray(n)
    val y = FloatArray(n)
    val px = FloatArray(n)
    val py = FloatArray(n)
    private var restEdge = 0f
    private var restSkip = 0f

    var minX = 0f; var minY = 0f; var maxX = 0f; var maxY = 0f

    init {
        place(cx, cy)
    }

    private fun place(cx: Float, cy: Float) {
        val r = sqrt(targetArea / PI.toFloat())
        for (i in 0 until n) {
            val a = (2 * PI * i / n).toFloat()
            x[i] = cx + r * cos(a); y[i] = cy + r * sin(a)
            px[i] = x[i]; py[i] = y[i]
        }
        restEdge = (2 * PI * r / n).toFloat()
        restSkip = 2f * r * sin((2 * PI / n).toFloat())
    }

    fun resize(area: Float) {
        targetArea = area
        val r = sqrt(area / PI.toFloat())
        restEdge = (2 * PI * r / n).toFloat()
        restSkip = 2f * r * sin((2 * PI / n).toFloat())
    }

    fun centroidX(): Float = x.average().toFloat()
    fun centroidY(): Float = y.average().toFloat()

    fun area(): Float {
        var a = 0f
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            a += x[i] * y[j] - x[j] * y[i]
        }
        return a / 2f
    }

    fun updateBounds() {
        minX = x.min(); maxX = x.max(); minY = y.min(); maxY = y.max()
    }

    fun contains(qx: Float, qy: Float): Boolean {
        if (qx < minX || qx > maxX || qy < minY || qy > maxY) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            if ((y[i] > qy) != (y[j] > qy) && qx < (x[j] - x[i]) * (qy - y[i]) / (y[j] - y[i]) + x[i]) inside = !inside
            j = i
        }
        return inside
    }

    fun integrate(gravity: Float, damping: Float) {
        for (i in 0 until n) {
            val vx = (x[i] - px[i]) * damping
            val vy = (y[i] - py[i]) * damping
            px[i] = x[i]; py[i] = y[i]
            x[i] += vx; y[i] += vy + gravity
        }
    }

    fun solveShape() {
        distance(1, restEdge, 0.9f)
        distance(2, restSkip, 0.15f)
        // Area constraint: push the ring out (or in) along its normals.
        val a = area()
        var sum = 0f
        val gx = FloatArray(n); val gy = FloatArray(n)
        for (i in 0 until n) {
            val prev = if (i == 0) n - 1 else i - 1
            val next = if (i + 1 == n) 0 else i + 1
            gx[i] = (y[next] - y[prev]) / 2f
            gy[i] = (x[prev] - x[next]) / 2f
            sum += gx[i] * gx[i] + gy[i] * gy[i]
        }
        if (sum < 1e-6f) return
        val lambda = (targetArea - a) / sum * 0.9f
        for (i in 0 until n) {
            x[i] += lambda * gx[i]; y[i] += lambda * gy[i]
        }
    }

    private fun distance(step: Int, rest: Float, k: Float) {
        for (i in 0 until n) {
            val j = (i + step) % n
            val dx = x[j] - x[i]; val dy = y[j] - y[i]
            val d = sqrt(dx * dx + dy * dy)
            if (d < 1e-4f) continue
            val c = (d - rest) / d * 0.5f * k
            x[i] += dx * c; y[i] += dy * c
            x[j] -= dx * c; y[j] -= dy * c
        }
    }

    fun kinetic(): Float {
        var e = 0f
        for (i in 0 until n) {
            val vx = x[i] - px[i]; val vy = y[i] - py[i]
            e += vx * vx + vy * vy
        }
        return e / n
    }

    fun push(dx: Float, dy: Float) {
        for (i in 0 until n) { x[i] += dx; y[i] += dy }
    }

    /** Holds a pinned jelly by its pin, and draws its middle slowly back under it. */
    fun holdHome() {
        push((homeX - centroidX()) * PIN_HOLD, (homeY - centroidY()) * PIN_HOLD)
        val r = sqrt(targetArea / PI.toFloat())
        for (o in -1..1) {
            val i = (pinIndex + o + n) % n
            val a = (2 * PI * i / n).toFloat()
            x[i] += (homeX + r * cos(a) - x[i]) * PIN_GRIP
            y[i] += (homeY + r * sin(a) - y[i]) * PIN_GRIP
        }
    }

    /** Where the pin goes in: the top of the jelly, as it moves. */
    val pinX: Float get() = x[pinIndex]
    val pinY: Float get() = y[pinIndex]

    /** Puts a pinned jelly at its spot as a round shape, at rest. */
    fun placeAt(cx: Float, cy: Float) {
        homeX = cx
        homeY = cy
        val r = sqrt(targetArea / PI.toFloat())
        for (i in 0 until n) {
            val a = (2 * PI * i / n).toFloat()
            x[i] = cx + r * cos(a); y[i] = cy + r * sin(a)
            px[i] = x[i]; py[i] = y[i]
        }
    }

    /**
     * Sets the jelly wobbling without changing its size: wider and flatter first, then back and
     * forth. (A kick swells it, which its pressure mostly takes back at once.)
     */
    fun squish(strength: Float) {
        val cx = centroidX(); val cy = centroidY()
        for (i in 0 until n) {
            px[i] -= (x[i] - cx) * strength
            py[i] += (y[i] - cy) * strength
        }
    }

    fun kick(strength: Float) {
        val cx = centroidX(); val cy = centroidY()
        for (i in 0 until n) {
            px[i] -= (x[i] - cx) * strength
            py[i] -= (y[i] - cy) * strength
        }
    }
}

/** A box of soft jellies falling under gravity and pressing against each other. */
class SoftBodyWorld {
    val blobs = LinkedHashMap<String, SoftBlob>()
    var width = 1f
        private set
    var height = 1f
        private set
    private var pad = 4f
    var restFrames = 0
        private set

    /**
     * A packed pile never stops trembling completely, so the world also rests once no jelly has
     * moved more than [quietDrift] px over two windows of [QUIET_FRAMES] frames, and at the latest
     * [MAX_FRAMES] frames after the last touch or change.
     */
    var quietDrift = 2f
    private var frames = 0
    private var quietWindows = 0
    private var snapshot: Map<String, FloatArray>? = null

    /** The held jelly follows the finger at ([grabX], [grabY]), keeping the offset it was grabbed at. */
    var grabId: String? = null
    var grabX = 0f
    var grabY = 0f
    var grabOffsetX = 0f
    var grabOffsetY = 0f

    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    fun resize(w: Float, h: Float, padding: Float) {
        width = w; height = h; pad = padding
    }

    val isResting: Boolean
        get() = grabId == null && (restFrames > 45 || quietWindows >= 2 || frames > MAX_FRAMES)

    fun wake() {
        restFrames = 0
        frames = 0
        quietWindows = 0
        snapshot = null
        wakeSignal.trySend(Unit)
    }

    /** Suspends until something moves the jellies again, so a still box costs no frames. */
    suspend fun awaitWake() {
        wakeSignal.receive()
    }

    // A pinned jelly is stretched instead: the side facing the way the finger goes follows it.
    private var grabStartX = 0f
    private var grabStartY = 0f
    private var grabIndex = -1
    private var grabRestX = 0f
    private var grabRestY = 0f

    fun grab(blob: SoftBlob, x: Float, y: Float) {
        grabId = blob.id
        grabOffsetX = x - blob.centroidX()
        grabOffsetY = y - blob.centroidY()
        grabX = x
        grabY = y
        grabStartX = x
        grabStartY = y
        grabIndex = -1
        wake()
    }

    /** Pulls one side of a pinned jelly after the finger, by up to most of its size; the pin holds the rest. */
    private fun stretch(b: SoftBlob) {
        val fx = grabX - grabStartX
        val fy = grabY - grabStartY
        val d = hypot(fx, fy)
        val r = sqrt(b.targetArea / PI.toFloat())
        if (grabIndex < 0) {
            if (d < 3f) return
            val cx = b.centroidX(); val cy = b.centroidY()
            var best = -Float.MAX_VALUE
            for (i in 0 until b.n) {
                val dot = (b.x[i] - cx) * fx + (b.y[i] - cy) * fy
                if (dot > best) { best = dot; grabIndex = i }
            }
            // Where that side sits when the jelly is round and at its spot.
            val ox = b.x[grabIndex] - cx; val oy = b.y[grabIndex] - cy
            val len = hypot(ox, oy).takeIf { it > 0f } ?: 1f
            grabRestX = b.homeX + ox / len * r
            grabRestY = b.homeY + oy / len * r
        }
        val reach = 0.9f * r
        val k = if (d > reach) reach / d else 1f
        val gx = grabRestX + fx * k - b.x[grabIndex]
        val gy = grabRestY + fy * k - b.y[grabIndex]
        for (o in -3..3) {
            val w = STRETCH[abs(o)] * 0.12f
            val i = (grabIndex + o + b.n) % b.n
            b.x[i] += gx * w; b.y[i] += gy * w
        }
    }

    fun release() {
        grabId = null
        wake()
    }

    /** Lower edge of the pinned row; jellies falling in pass the pinned ones until they are below it. */
    var pinnedBottom = 0f

    fun add(id: String, area: Float, spawnX: Float, spawnY: Float, fixed: Boolean = false) {
        blobs[id] = SoftBlob(id, spawnX, spawnY, area, fixed)
        wake()
    }

    fun remove(id: String) {
        if (blobs.remove(id) != null) wake()
    }

    fun step(gravity: Float) {
        val iterations = 8
        // Pinned jellies hang on their pins: no falling, and they settle a little sooner.
        for (b in blobs.values) b.integrate(if (b.fixed) 0f else gravity, if (b.fixed) PIN_DAMPING else 0.985f)
        for (b in blobs.values) {
            if (b.passing) {
                b.updateBounds()
                if (b.minY > pinnedBottom) b.passing = false
            }
        }
        repeat(iterations) {
            for (b in blobs.values) {
                b.solveShape()
                if (b.fixed) b.holdHome()
                if (b.id == grabId) {
                    if (b.fixed) {
                        stretch(b)
                    } else {
                        val cx = b.centroidX(); val cy = b.centroidY()
                        b.push((grabX - grabOffsetX - cx) * 0.18f, (grabY - grabOffsetY - cy) * 0.18f)
                    }
                }
            }
            for (b in blobs.values) b.updateBounds()
            collide()
            walls()
        }
        var energy = 0f
        for (b in blobs.values) energy = max(energy, b.kinetic())
        restFrames = if (energy < 0.004f) restFrames + 1 else 0
        frames++
        if (frames % QUIET_FRAMES == 0) {
            // A pinned jelly's middle hardly moves, so its width and height have to settle too.
            val now = blobs.mapValues { (_, b) ->
                floatArrayOf(b.centroidX(), b.centroidY(), if (b.fixed) b.maxX - b.minX else 0f, if (b.fixed) b.maxY - b.minY else 0f)
            }
            snapshot?.let { before ->
                var drift = 0f
                for ((id, at) in now) {
                    val was = before[id]
                    drift = if (was == null) {
                        Float.MAX_VALUE
                    } else {
                        max(drift, maxOf(hypot(at[0] - was[0], at[1] - was[1]), abs(at[2] - was[2]), abs(at[3] - was[3])))
                    }
                }
                quietWindows = if (drift < quietDrift) quietWindows + 1 else 0
            }
            snapshot = now
        }
    }

    private fun walls() {
        val left = pad; val right = width - pad; val bottom = height - pad; val top = -height * 3f
        for (b in blobs.values) {
            for (i in 0 until b.n) {
                if (b.x[i] < left) { b.x[i] = left; b.py[i] = b.y[i] + (b.py[i] - b.y[i]) * 0.6f }
                if (b.x[i] > right) { b.x[i] = right; b.py[i] = b.y[i] + (b.py[i] - b.y[i]) * 0.6f }
                if (b.y[i] > bottom) { b.y[i] = bottom; b.px[i] = b.x[i] + (b.px[i] - b.x[i]) * 0.5f }
                if (b.y[i] < top) b.y[i] = top
            }
        }
    }

    private fun collide() {
        val list = blobs.values.toList()
        for (ai in list.indices) {
            val a = list[ai]
            for (bi in list.indices) {
                if (ai == bi) continue
                val b = list[bi]
                if (a.fixed && b.fixed) continue
                // A jelly still falling in slips behind the pinned ones.
                if ((a.fixed && b.passing) || (b.fixed && a.passing)) continue
                if (a.maxX < b.minX || a.minX > b.maxX || a.maxY < b.minY || a.minY > b.maxY) continue
                for (i in 0 until a.n) {
                    val qx = a.x[i]; val qy = a.y[i]
                    if (!b.contains(qx, qy)) continue
                    // Push the point to the nearest edge of b, and the edge back a little.
                    var best = Float.MAX_VALUE; var bj = 0; var bt = 0f; var bx = 0f; var by = 0f
                    for (j in 0 until b.n) {
                        val k = if (j + 1 == b.n) 0 else j + 1
                        val ex = b.x[k] - b.x[j]; val ey = b.y[k] - b.y[j]
                        val len2 = ex * ex + ey * ey
                        val t = if (len2 < 1e-6f) 0f else (((qx - b.x[j]) * ex + (qy - b.y[j]) * ey) / len2).coerceIn(0f, 1f)
                        val cx = b.x[j] + ex * t; val cy = b.y[j] + ey * t
                        val d = (cx - qx) * (cx - qx) + (cy - qy) * (cy - qy)
                        if (d < best) { best = d; bj = j; bt = t; bx = cx; by = cy }
                    }
                    val dx = bx - qx; val dy = by - qy
                    // A pinned jelly hardly gives way: the other one takes most of the push.
                    val share = when {
                        b.fixed -> 0.85f
                        a.fixed -> 0.15f
                        else -> 0.5f
                    }
                    a.x[i] += dx * share; a.y[i] += dy * share
                    val k = if (bj + 1 == b.n) 0 else bj + 1
                    val back = 1f - share
                    b.x[bj] -= dx * back * (1 - bt); b.y[bj] -= dy * back * (1 - bt)
                    b.x[k] -= dx * back * bt; b.y[k] -= dy * back * bt
                }
            }
        }
    }

    /** The jelly under a finger; pinned ones first, since they are drawn on top. */
    fun blobAt(qx: Float, qy: Float): SoftBlob? {
        val order = blobs.values.filter { it.fixed } + blobs.values.filterNot { it.fixed }.reversed()
        for (b in order) {
            b.updateBounds()
            if (b.contains(qx, qy)) return b
        }
        return null
    }

    /** Highest point of the pile, used to know how full the box is. */
    fun fillTop(): Float = blobs.values.minOfOrNull { it.minY } ?: height

    private companion object {
        const val QUIET_FRAMES = 30
        const val MAX_FRAMES = 900
        const val PIN_DAMPING = 0.95f

        /** How much the points next to the pulled one follow it when a pinned jelly is stretched. */
        val STRETCH = floatArrayOf(1f, 0.7f, 0.4f, 0.15f)
    }
}

/** How hard a pin grips the top of its jelly, and how strongly the jelly's middle is drawn back under it. */
private const val PIN_GRIP = 0.3f
private const val PIN_HOLD = 0.006f
