package io.github.ckdgus6068.jellycalendar.ui.jelly

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.JellyFlavor
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyType
import kotlin.math.min
import kotlin.math.sin

enum class JellyLook { NORMAL, MISSED, GHOST }

/** Extra deformation that comes from outside the jelly, e.g. being dragged around. */
interface JellyLean {
    val leanX: Float
    val leanY: Float
    val grabX: Float
    val grabY: Float
}

/**
 * Draws one soft, wobbling jelly and puts [content] on top of it.
 * All animated values are read in the draw and layer phases only.
 */
@Composable
fun JellyBody(
    flavor: JellyFlavor,
    modifier: Modifier = Modifier,
    motion: JellyMotion? = null,
    look: JellyLook = JellyLook.NORMAL,
    cornerRadius: Dp = 12.dp,
    lean: JellyLean? = null,
    bottomPull: (() -> Float)? = null,
    alpha: Float = 1f,
    idle: Boolean = LocalIdleWobble.current,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val clock = LocalJellyClock.current
    val density = LocalDensity.current
    val outline = remember { JellyOutline() }
    val path = remember { Path() }
    val liquid = remember { Path() }
    val check = remember { Path() }
    val px = remember(density) { with(density) { 1.dp.toPx() } }
    val dash = remember(px) { PathEffect.dashPathEffect(floatArrayOf(5f * px, 4f * px), 0f) }
    val radiusPx = with(density) { cornerRadius.toPx() }
    val fallbackPhase = remember { (flavor.hashCode() % 628) / 100f }

    Box(
        modifier
            .graphicsLayer {
                val squash = motion?.squash?.value ?: 0f
                val lift = motion?.lift?.value ?: 0f
                val grow = 1f + lift * 0.05f
                scaleY = (1f - squash) * grow
                scaleX = (1f + squash * 0.6f) * grow
                transformOrigin = TransformOrigin(0.5f, 1f)
                this.alpha = alpha
            }
            .drawWithCache {
                // Colours and gradients only change with the size or the flavour: build them once.
                val missed = look == JellyLook.MISSED
                val deep = if (missed) desaturate(flavor.deep) else flavor.deep
                val paint = JellyPaint(
                    body = Brush.verticalGradient(
                        listOf(
                            if (missed) desaturate(flavor.light) else flavor.light,
                            if (missed) desaturate(flavor.base) else flavor.base,
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                    juice = Brush.verticalGradient(listOf(deep.copy(alpha = 0.86f), deep), startY = 0f, endY = size.height),
                    deep = deep,
                    missed = missed,
                )
                onDrawBehind {
                    val time = if (idle) clock.value else 0f
                    drawJelly(
                        outline = outline,
                        path = path,
                        liquid = liquid,
                        check = check,
                        flavor = flavor,
                        paint = paint,
                        ghost = look == JellyLook.GHOST,
                        radius = radiusPx,
                        unit = px,
                        dash = dash,
                        time = time,
                        phase = motion?.phase ?: fallbackPhase,
                        idleAmp = if (idle) 1.0f * px else 0f,
                        wobble = motion?.wobble?.value ?: 0f,
                        leanX = lean?.leanX ?: 0f,
                        leanY = lean?.leanY ?: 0f,
                        grabX = lean?.grabX ?: (size.width / 2f),
                        grabY = lean?.grabY ?: (size.height / 2f),
                        bottomPull = bottomPull?.invoke() ?: 0f,
                        fill = motion?.fill?.value ?: 0f,
                        wave = motion?.wave?.value ?: 0f,
                        lift = motion?.lift?.value ?: 0f,
                    )
                }
            },
        content = content,
    )
}

private fun desaturate(color: Color): Color = lerp(color, Color(0xFFB9B4BC), 0.62f)

private class JellyPaint(val body: Brush, val juice: Brush, val deep: Color, val missed: Boolean)

private fun DrawScope.drawJelly(
    outline: JellyOutline,
    path: Path,
    liquid: Path,
    check: Path,
    flavor: JellyFlavor,
    paint: JellyPaint,
    ghost: Boolean,
    radius: Float,
    unit: Float,
    dash: PathEffect,
    time: Float,
    phase: Float,
    idleAmp: Float,
    wobble: Float,
    leanX: Float,
    leanY: Float,
    grabX: Float,
    grabY: Float,
    bottomPull: Float,
    fill: Float,
    wave: Float,
    lift: Float,
) {
    val w = size.width
    val h = size.height
    if (w < 2f || h < 2f) return
    outline.prepare(w, h, radius, spacing = 4f * unit)
    val wobbleAmp = (min(w, h) * 0.08f).coerceIn(1.5f * unit, 6f * unit)
    outline.deform(time, phase, idleAmp, wobble, wobbleAmp, leanX, leanY, grabX, grabY, bottomPull)
    outline.writeTo(path)

    if (ghost) {
        drawPath(path, flavor.base.copy(alpha = 0.16f))
        drawPath(path, flavor.deep.copy(alpha = 0.55f), style = Stroke(width = 1.3f * unit, pathEffect = dash))
        return
    }
    val missed = paint.missed
    val deep = paint.deep

    // Soft shadow under the jelly.
    translate(top = (1.5f + lift * 5f) * unit) {
        drawPath(path, Color.Black.copy(alpha = 0.09f + lift * 0.10f))
    }
    // Translucent body: lighter on top like light passing through.
    drawPath(path, paint.body)

    if (fill > 0.001f) {
        clipPath(path) {
            val level = h * (1f - fill)
            val amp = wave * min(4f * unit, h * 0.18f)
            val travel = (1f - wave) * 16f
            liquid.reset()
            liquid.moveTo(-4f * unit, h + 8f * unit)
            val steps = 16
            for (k in 0..steps) {
                val x = -4f * unit + (w + 8f * unit) * k / steps
                val y = level + amp * sin(x / w * 9.4f + travel + phase)
                liquid.lineTo(x, y)
            }
            liquid.lineTo(w + 4f * unit, h + 8f * unit)
            liquid.close()
            drawPath(liquid, paint.juice)
        }
    }

    // Candy gloss.
    clipPath(path) {
        val top = min(h * 0.09f, 3f * unit)
        val glossH = min(h * 0.34f, 11f * unit)
        val glossAlpha = if (fill > 0.5f) 0.30f else 0.52f
        val glossLeft = min(w * 0.09f, 10f * unit)
        val glossW = min(w * 0.46f, 70f * unit)
        drawOval(
            Color.White.copy(alpha = glossAlpha),
            topLeft = Offset(glossLeft, top),
            size = Size(glossW, glossH),
        )
        drawCircle(
            Color.White.copy(alpha = glossAlpha),
            radius = min(2.2f * unit, glossH * 0.28f),
            center = Offset(glossLeft + glossW * 1.16f, top + glossH * 0.45f),
        )
    }

    drawPath(
        path,
        deep.copy(alpha = if (missed) 0.55f else 0.32f),
        style = Stroke(width = unit, pathEffect = if (missed) dash else null),
    )

    if (fill > 0.6f && w >= 26f * unit && h >= 18f * unit) {
        val grow = ((fill - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val r = 6.5f * unit * grow
        val c = Offset(w - 10f * unit, min(10f * unit, h / 2f))
        drawCircle(Color.White.copy(alpha = 0.94f), r, c)
        check.reset()
        check.moveTo(c.x - r * 0.45f, c.y + r * 0.02f)
        check.lineTo(c.x - r * 0.1f, c.y + r * 0.38f)
        check.lineTo(c.x + r * 0.5f, c.y - r * 0.36f)
        drawPath(check, deep, style = Stroke(width = 1.7f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Title and time printed on a jelly. The ink turns white once the jelly is filled.
 */
@Composable
fun BoxScope.JellyLabel(
    title: String,
    subtitle: String?,
    flavor: JellyFlavor,
    motion: JellyMotion?,
    heightDp: Float,
    compact: Boolean,
    missed: Boolean = false,
) {
    val filled by remember(motion) { derivedStateOf { (motion?.fill?.value ?: 0f) > 0.5f } }
    val ink by animateColorAsState(
        targetValue = when {
            missed -> Color(0xFF6F6873)
            filled -> Color.White
            else -> flavor.ink
        },
        label = "jellyInk",
    )
    val titleSize = if (compact) 11f else 13f
    val lineHeight = titleSize * 1.25f
    val showSubtitle = subtitle != null && heightDp >= (if (compact) 44f else 36f)
    val titleLines = (((heightDp - 6f - (if (showSubtitle) lineHeight else 0f)) / lineHeight).toInt()).coerceIn(1, 4)
    if (heightDp < 13f) return
    Column(
        Modifier.padding(
            start = if (compact) 4.dp else 8.dp,
            end = if (filled && !compact) 18.dp else 4.dp,
            top = if (heightDp < 22f) 0.dp else 3.dp,
        ),
    ) {
        // Narrow week columns keep the plain face; everywhere else jellies wear the display face.
        val type = LocalJellyType.current
        Text(
            text = keepWords(title),
            color = ink,
            fontSize = titleSize.sp,
            lineHeight = lineHeight.sp,
            fontFamily = if (compact) null else type.display,
            fontWeight = if (compact) FontWeight.SemiBold else type.displayWeight,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (showSubtitle) {
            Text(
                text = subtitle.orEmpty(),
                color = ink.copy(alpha = 0.78f),
                fontSize = (titleSize - 2f).sp,
                lineHeight = (lineHeight - 2f).sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
