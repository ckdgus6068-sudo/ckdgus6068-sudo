package io.github.ckdgus6068.jellycalendar.ui.drag

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.ckdgus6068.jellycalendar.core.Jelly
import io.github.ckdgus6068.jellycalendar.ui.jelly.FrameSpring
import io.github.ckdgus6068.jellycalendar.ui.jelly.JellyLean
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp

sealed interface DropTarget {
    data class Slot(val date: LocalDate, val startMin: Int, val allowed: Boolean) : DropTarget
    data class Day(val date: LocalDate, val allowed: Boolean) : DropTarget
    data object Tray : DropTarget
}

enum class DragSource { TIMELINE, TRAY, BOX }

/** What the timeline tells the drag controller about itself. All rectangles are in root coordinates. */
interface TimelineZone {
    val viewport: Rect
    fun slotAt(pointer: Offset, jellyTop: Float, durationMin: Int): DropTarget.Slot?
    fun slotRect(date: LocalDate, startMin: Int, durationMin: Int): Rect?
    fun jellySize(durationMin: Int): Size
}

/** What the tray tells the drag controller. */
interface TrayZone {
    val bounds: Rect
    fun pillSize(durationMin: Int): Size
    fun landingRect(durationMin: Int): Rect
}

class DragSession(
    val jelly: Jelly,
    val source: DragSource,
    /** Where the finger holds the jelly, as a fraction of its size. */
    val grabFraction: Offset,
    val startRect: Rect,
)

class Flight(
    val jelly: Jelly,
    val target: () -> Rect?,
) {
    internal var startedAtNanos = 0L
}

class Arrival(val jellyId: String)

/** The dragged jelly's on-screen rectangle and its lag, stepped once per frame by [DragOverlay]. */
class DragVisual : JellyLean {
    private val x = FrameSpring()
    private val y = FrameSpring()
    private val w = FrameSpring()
    private val h = FrameSpring()
    private val lagX = FrameSpring()
    private val lagY = FrameSpring()

    var rect by mutableStateOf(Rect.Zero)
        private set
    override var leanX by mutableStateOf(0f)
        private set
    override var leanY by mutableStateOf(0f)
        private set
    override var grabX by mutableStateOf(0f)
        private set
    override var grabY by mutableStateOf(0f)
        private set

    fun begin(start: Rect) {
        x.snap(start.left)
        y.snap(start.top)
        w.snap(start.width)
        h.snap(start.height)
        lagX.snap(0f)
        lagY.snap(0f)
        rect = start
        leanX = 0f
        leanY = 0f
    }

    /** Follows the finger. The body keeps up exactly; only the far ends lag behind. */
    fun followFinger(pointer: Offset, grab: Offset, targetSize: Size, velocity: Offset, dt: Float) {
        w.step(targetSize.width, dt, stiffness = 320f, dampingRatio = 0.5f)
        h.step(targetSize.height, dt, stiffness = 320f, dampingRatio = 0.5f)
        val left = pointer.x - grab.x * w.value
        val top = pointer.y - grab.y * h.value
        x.snap(left)
        y.snap(top)
        stepLag(velocity, dt)
        grabX = grab.x * w.value
        grabY = grab.y * h.value
        rect = Rect(left, top, left + w.value, top + h.value)
    }

    /** Springs towards [target]; returns true once it has come to rest. */
    fun flyTo(target: Rect, dt: Float): Boolean {
        x.step(target.left, dt, stiffness = 360f, dampingRatio = 0.62f)
        y.step(target.top, dt, stiffness = 360f, dampingRatio = 0.62f)
        w.step(target.width, dt, stiffness = 360f, dampingRatio = 0.7f)
        h.step(target.height, dt, stiffness = 360f, dampingRatio = 0.7f)
        stepLag(Offset(x.velocity, y.velocity), dt)
        grabX = w.value / 2f
        grabY = h.value / 2f
        rect = Rect(x.value, y.value, x.value + w.value, y.value + h.value)
        return x.isSettled(target.left, 0.8f) && y.isSettled(target.top, 0.8f) &&
            abs(w.value - target.width) < 1f && abs(h.value - target.height) < 1f
    }

    private fun stepLag(velocity: Offset, dt: Float) {
        val maxX = (w.value * 0.45f).coerceAtLeast(6f)
        val maxY = (h.value * 0.3f).coerceAtLeast(6f)
        lagX.step((-velocity.x * 0.032f).coerceIn(-maxX, maxX), dt, stiffness = 230f, dampingRatio = 0.22f)
        lagY.step((-velocity.y * 0.024f).coerceIn(-maxY, maxY), dt, stiffness = 230f, dampingRatio = 0.22f)
        leanX = lagX.value
        leanY = lagY.value
    }
}

/**
 * Drag and drop across the whole calendar: from a day to another day, into the tray at the
 * bottom and back. Jellies report where they are; drop zones report what lies under the finger.
 */
@Stable
class DragController {
    var session by mutableStateOf<DragSession?>(null)
        private set
    var flight by mutableStateOf<Flight?>(null)
        private set
    var hover by mutableStateOf<DropTarget?>(null)
        private set
    var arrival by mutableStateOf<Arrival?>(null)
        private set

    /** Where the flying jelly is going, so the overlay can already show its new time. */
    var landingTarget by mutableStateOf<DropTarget?>(null)
        private set

    val visual = DragVisual()

    var pointer = Offset.Zero
        private set
    var velocity = Offset.Zero
        private set
    private var lastMoveMillis = 0L
    private var moveSerial = 0
    private var seenSerial = 0

    var today: LocalDate = LocalDate.now()
    var timeline: TimelineZone? = null
    var tray: TrayZone? = null
    val dayRects = HashMap<LocalDate, Rect>()

    /** The box view of one day: dropping a jelly anywhere on it puts the jelly into that day. */
    var board: Pair<LocalDate, Rect>? = null
    private val rectProviders = HashMap<String, () -> Rect?>()

    /** Called when a jelly is dropped somewhere it is allowed to go. */
    var onDrop: ((DragSession, DropTarget) -> Unit)? = null

    val isActive: Boolean get() = session != null || flight != null

    fun isGhost(id: String): Boolean = session?.jelly?.id == id
    fun isHidden(id: String): Boolean = flight?.jelly?.id == id

    fun register(id: String, provider: () -> Rect?) {
        rectProviders[id] = provider
    }

    fun unregister(id: String, provider: () -> Rect?) {
        if (rectProviders[id] === provider) rectProviders.remove(id)
    }

    fun start(jelly: Jelly, source: DragSource, grabFraction: Offset, startRect: Rect, pointerRoot: Offset) {
        flight = null
        pointer = pointerRoot
        velocity = Offset.Zero
        lastMoveMillis = 0L
        visual.begin(startRect)
        session = DragSession(jelly, source, grabFraction, startRect)
        hover = resolve()
    }

    fun move(pointerRoot: Offset, uptimeMillis: Long) {
        if (session == null) return
        val dt = uptimeMillis - lastMoveMillis
        if (lastMoveMillis != 0L && dt in 1..100) {
            val instant = (pointerRoot - pointer) / (dt / 1000f)
            velocity = velocity * 0.55f + instant * 0.45f
        }
        lastMoveMillis = uptimeMillis
        pointer = pointerRoot
        moveSerial++
        hover = resolve()
    }

    /** Re-evaluates what is under the finger, e.g. after the timeline scrolled by itself. */
    fun refresh() {
        if (session != null) hover = resolve()
    }

    fun end() {
        val s = session ?: return
        val target = resolve()
        session = null
        hover = null
        val accepted = when (target) {
            is DropTarget.Slot -> target.allowed
            is DropTarget.Day -> target.allowed
            DropTarget.Tray -> s.jelly.isScheduled
            null -> false
        }
        if (accepted && target != null) onDrop?.invoke(s, target)
        landingTarget = if (accepted) target else null
        val id = s.jelly.id
        val fallback: () -> Rect? = when {
            !accepted -> ({ s.startRect })
            target is DropTarget.Slot -> ({ timeline?.slotRect(target.date, target.startMin, s.jelly.durationMin) })
            target is DropTarget.Day -> ({
                // Into the top of the box when it shows that day, otherwise onto the day's chip.
                val box = board?.takeIf { it.first == target.date }?.second
                if (box != null) {
                    Rect(center = Offset(box.center.x, box.top + box.height * 0.12f), radius = 10f)
                } else {
                    dayRects[target.date]?.let { Rect(center = it.center, radius = 10f) }
                }
            })
            else -> ({ tray?.landingRect(s.jelly.durationMin) })
        }
        flight = Flight(s.jelly) { rectProviders[id]?.invoke() ?: fallback() }
    }

    fun cancel() {
        val s = session ?: return
        session = null
        hover = null
        landingTarget = null
        flight = Flight(s.jelly) { rectProviders[s.jelly.id]?.invoke() ?: s.startRect }
    }

    /** One animation frame of the overlay. */
    fun step(frameNanos: Long, dt: Float) {
        val s = session
        if (s != null) {
            if (seenSerial == moveSerial) velocity *= exp(-dt * 9f)
            seenSerial = moveSerial
            val size = when (hover) {
                is DropTarget.Slot -> timeline?.jellySize(s.jelly.durationMin)
                DropTarget.Tray -> tray?.pillSize(s.jelly.durationMin)
                is DropTarget.Day -> null
                null -> null
            } ?: s.startRect.size
            visual.followFinger(pointer, s.grabFraction, size, velocity, dt)
            return
        }
        val f = flight ?: return
        if (f.startedAtNanos == 0L) f.startedAtNanos = frameNanos
        val target = f.target() ?: visual.rect
        val settled = visual.flyTo(target, dt)
        if (settled || frameNanos - f.startedAtNanos > 1_200_000_000L) {
            flight = null
            landingTarget = null
            arrival = Arrival(f.jelly.id)
        }
    }

    private fun resolve(): DropTarget? {
        val s = session ?: return null
        val p = pointer
        tray?.let { if (it.bounds.contains(p)) return DropTarget.Tray }
        for ((date, rect) in dayRects) {
            if (rect.contains(p)) return DropTarget.Day(date, allowed = s.jelly.isDone || !date.isBefore(today))
        }
        board?.let { (date, rect) ->
            if (rect.contains(p)) return DropTarget.Day(date, allowed = s.jelly.isDone || !date.isBefore(today))
        }
        val zone = timeline ?: return null
        if (!zone.viewport.contains(p)) return null
        val size = zone.jellySize(s.jelly.durationMin)
        val top = p.y - s.grabFraction.y * size.height
        val slot = zone.slotAt(p, top, s.jelly.durationMin) ?: return null
        // A finished jelly may be moved onto a past day (to fix the record); planned ones may not.
        return if (!slot.allowed && s.jelly.isDone) slot.copy(allowed = true) else slot
    }
}
