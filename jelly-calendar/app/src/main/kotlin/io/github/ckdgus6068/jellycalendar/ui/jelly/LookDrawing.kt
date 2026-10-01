package io.github.ckdgus6068.jellycalendar.ui.jelly

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import io.github.ckdgus6068.jellycalendar.core.Look
import io.github.ckdgus6068.jellycalendar.core.LookBook
import io.github.ckdgus6068.jellycalendar.core.LookLayer
import io.github.ckdgus6068.jellycalendar.core.LookPart

// Draws the jelly characters of LookBook, the same shapes as the shared page's character.js. Parts
// are in units of the jelly's radius (100 = the radius, y down).

private class LookParts(val back: List<LookPart>, val face: List<LookPart>, val head: List<LookPart>, val body: List<LookPart>)

private val partsCache = HashMap<Look, LookParts?>()
private val pathCache = HashMap<String, Path>()

private fun partsOf(look: Look?): LookParts? {
    if (look == null) return null
    return partsCache.getOrPut(look) {
        val job = LookBook.job(look.job) ?: return@getOrPut null
        val v = if (look.v == 1) 1 else 0
        val outfit = job.variants[v]
        LookParts(
            back = outfit.filter { it.on == LookLayer.BACK },
            face = LookBook.faces[LookBook.variantFaces[v]].orEmpty() + outfit.filter { it.on == LookLayer.FACE },
            head = outfit.filter { it.on == LookLayer.HEAD },
            body = outfit.filter { it.on == LookLayer.BODY },
        )
    }
}

private fun pathOf(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private fun colorOf(value: String, body: Color): Color = when (value) {
    "body" -> body
    "shade" -> lerp(body, Color.Black, 0.28f)
    "light" -> lerp(body, Color.White, 0.45f)
    else -> Color(0xFF000000 or value.removePrefix("#").toLong(16))
}

private fun DrawScope.paint(parts: List<LookPart>, body: Color) {
    for (part in parts) {
        val path = pathOf(part.d)
        part.fill?.let { drawPath(path, colorOf(it, body), alpha = part.alpha) }
        part.stroke?.let {
            drawPath(path, colorOf(it, body), alpha = part.alpha, style = Stroke(width = part.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** In units of a jelly with its middle at [center] and radius [r]. */
private inline fun DrawScope.inJellyUnits(center: Offset, r: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(center.x, center.y)
        scale(r / 100f, r / 100f, pivot = Offset.Zero)
    }, block)
}

/** What goes behind a jelly (a hood, say): call before drawing the jelly, and [drawLookFront] after. */
fun DrawScope.drawLookBack(look: Look?, center: Offset, r: Float, body: Color) {
    val parts = partsOf(look) ?: return
    if (parts.back.isEmpty()) return
    inJellyUnits(center, r) { paint(parts.back, body) }
}

/**
 * The face and outfit on a jelly with its middle at [center] and radius [r]. [faceY] and
 * [faceScale] place the face (in the same units); [face], [head] and [bodyParts] leave out the face,
 * the hat and what is held up, or what is worn lower down. [hatLift] raises the hat a little.
 */
fun DrawScope.drawLookFront(
    look: Look?,
    center: Offset,
    r: Float,
    body: Color,
    face: Boolean = true,
    faceY: Float = 8f,
    faceScale: Float = 1f,
    head: Boolean = true,
    bodyParts: Boolean = true,
    hatLift: Float = 0f,
) {
    val parts = partsOf(look) ?: return
    inJellyUnits(center, r) {
        if (bodyParts) paint(parts.body, body)
        if (face) {
            withTransform({
                translate(0f, faceY)
                scale(faceScale, faceScale, pivot = Offset.Zero)
            }) { paint(parts.face, body) }
        }
        if (head) withTransform({ translate(0f, -hatLift) }) { paint(parts.head, body) }
    }
}

/** The room a character needs around its jelly, in jelly units (as the shared page's pictures). */
private const val VIEW_LEFT = -122f
private const val VIEW_TOP = -170f
private const val VIEW_WIDTH = 252f
private const val VIEW_HEIGHT = 282f

/**
 * A little picture of [look] on a jelly of colour [body], [height] tall (0.9 of that wide). With
 * no look, or a plain one, it is a plain jelly.
 */
@Composable
fun LookAvatar(look: Look?, body: Color, height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = height * (VIEW_WIDTH / VIEW_HEIGHT), height = height)) {
        val unit = size.height / VIEW_HEIGHT
        val center = Offset(-VIEW_LEFT * unit, -VIEW_TOP * unit)
        val r = 100f * unit
        drawLookBack(look, center, r, body)
        drawCircle(body, radius = r, center = center)
        // A soft shine on the jelly.
        rotate(-30f, pivot = Offset(center.x - 42f * unit, center.y - 52f * unit)) {
            drawOval(
                Color.White.copy(alpha = 0.35f),
                topLeft = Offset(center.x - 64f * unit, center.y - 65f * unit),
                size = Size(44f * unit, 26f * unit),
            )
        }
        drawLookFront(look, center, r, body)
    }
}
