package io.github.ckdgus6068.jellycalendar.ui.box

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

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
) {
    var targetArea = area
        private set
    val n: Int = (12 + sqrt(area) / 9f).toInt().coerceIn(14, 28)
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

    var grabId: String? = null
    var grabX = 0f
    var grabY = 0f

    fun resize(w: Float, h: Float, padding: Float) {
        width = w; height = h; pad = padding
    }

    val isResting: Boolean get() = restFrames > 45 && grabId == null

    fun wake() {
        restFrames = 0
    }

    fun add(id: String, area: Float, spawnX: Float, spawnY: Float) {
        blobs[id] = SoftBlob(id, spawnX, spawnY, area)
        wake()
    }

    fun remove(id: String) {
        if (blobs.remove(id) != null) wake()
    }

    fun step(gravity: Float) {
        val iterations = 8
        for (b in blobs.values) b.integrate(gravity, 0.985f)
        repeat(iterations) {
            for (b in blobs.values) {
                b.solveShape()
                if (b.id == grabId) {
                    val cx = b.centroidX(); val cy = b.centroidY()
                    b.push((grabX - cx) * 0.18f, (grabY - cy) * 0.18f)
                }
            }
            for (b in blobs.values) b.updateBounds()
            collide()
            walls()
        }
        var energy = 0f
        for (b in blobs.values) energy = max(energy, b.kinetic())
        restFrames = if (energy < 0.004f) restFrames + 1 else 0
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
                    a.x[i] += dx * 0.5f; a.y[i] += dy * 0.5f
                    val k = if (bj + 1 == b.n) 0 else bj + 1
                    b.x[bj] -= dx * 0.5f * (1 - bt); b.y[bj] -= dy * 0.5f * (1 - bt)
                    b.x[k] -= dx * 0.5f * bt; b.y[k] -= dy * 0.5f * bt
                }
            }
        }
    }

    fun blobAt(qx: Float, qy: Float): SoftBlob? {
        for (b in blobs.values.reversed()) {
            b.updateBounds()
            if (b.contains(qx, qy)) return b
        }
        return null
    }

    /** Highest point of the pile, used to know how full the box is. */
    fun fillTop(): Float = blobs.values.minOfOrNull { it.minY } ?: height
}
