package io.github.ckdgus6068.jellycalendar.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ckdgus6068.jellycalendar.ui.keepWords
import io.github.ckdgus6068.jellycalendar.ui.theme.LocalJellyColors

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** Clickable that squishes like a jelly while pressed. */
fun Modifier.squishyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 600f),
        label = "squish",
    )
    this
        .graphicsLayer {
            scaleX = scale * (if (pressed) 1.03f else 1f)
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun JellyChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalJellyColors.current.accent,
    fontFamily: FontFamily? = null,
) {
    val colors = LocalJellyColors.current
    Box(
        modifier
            .squishyClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else colors.surfaceSoft)
            .border(1.dp, if (selected) accent else colors.gridLineStrong, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) colors.onAccent else colors.text,
            fontSize = 13.sp,
            fontFamily = fontFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun JellyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    color: Color = LocalJellyColors.current.accent,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val colors = LocalJellyColors.current
    val content = if (filled) colors.onAccent else color
    Box(
        modifier
            .squishyClick(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(if (small) 12.dp else 16.dp))
            .background(if (filled) color.copy(alpha = if (enabled) 1f else 0.4f) else color.copy(alpha = 0.10f))
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 7.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = content.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = if (small) 13.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val colors = LocalJellyColors.current
    Text(
        text,
        color = colors.textSub,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = LocalJellyColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clickableNoRipple { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(keepWords(title), color = colors.text, fontSize = 15.sp)
            if (description != null) {
                Text(keepWords(description), color = colors.textSub, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = colors.onAccent,
            ),
        )
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    val colors = LocalJellyColors.current
    Box(modifier.fillMaxWidth().height(1.dp).background(colors.gridLine))
}

/**
 * One line of text that shrinks from [maxSize] down to [minSize] to fit its width, so a wide
 * typeface never cuts a date short. Only if even [minSize] is too wide does it end in "…".
 */
@Composable
fun FitText(
    text: String,
    color: Color,
    maxSize: TextUnit,
    minSize: TextUnit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val width = constraints.maxWidth
        val size = remember(text, fontFamily, fontWeight, width, maxSize, minSize) {
            var candidate = maxSize.value
            while (candidate > minSize.value) {
                val measured = measurer.measure(
                    text = text,
                    style = TextStyle(fontSize = candidate.sp, fontFamily = fontFamily, fontWeight = fontWeight),
                    maxLines = 1,
                    softWrap = false,
                )
                if (measured.size.width <= width) break
                candidate -= 0.5f
            }
            candidate.coerceAtLeast(minSize.value)
        }
        Text(
            text,
            color = color,
            fontSize = size.sp,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
