package io.github.ckdgus6068.jellycalendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** One jelly colour: a pastel body, a deep "filled" colour for done jellies and a readable ink. */
@Immutable
data class JellyFlavor(
    val name: String,
    val light: Color,
    val base: Color,
    val deep: Color,
    val ink: Color,
)

object JellyFlavors {
    val all: List<JellyFlavor> = listOf(
        JellyFlavor("딸기", Color(0xFFFFD6E0), Color(0xFFFF9DB4), Color(0xFFEE4F78), Color(0xFF7A1F38)),
        JellyFlavor("복숭아", Color(0xFFFFE3D1), Color(0xFFFFB68F), Color(0xFFF07B3C), Color(0xFF7A3512)),
        JellyFlavor("레몬", Color(0xFFFFF5C7), Color(0xFFFFE276), Color(0xFFEDB20A), Color(0xFF6B5000)),
        JellyFlavor("청포도", Color(0xFFE6F8CE), Color(0xFFBCE791), Color(0xFF6DBB3C), Color(0xFF2F5A12)),
        JellyFlavor("민트", Color(0xFFD3F6EA), Color(0xFF93E4C9), Color(0xFF26B386), Color(0xFF0F5A43)),
        JellyFlavor("소다", Color(0xFFD6EDFF), Color(0xFF98CEFF), Color(0xFF3689F2), Color(0xFF0F3F7A)),
        JellyFlavor("블루베리", Color(0xFFE0E4FF), Color(0xFFADB7FB), Color(0xFF5566E6), Color(0xFF232C7A)),
        JellyFlavor("포도", Color(0xFFEDE0FF), Color(0xFFC9A9F6), Color(0xFF8850DA), Color(0xFF42207A)),
        JellyFlavor("콜라", Color(0xFFF2E1D5), Color(0xFFD8AD91), Color(0xFF9C5D3A), Color(0xFF4F2A15)),
        JellyFlavor("우유", Color(0xFFF4F6FA), Color(0xFFD7DDE7), Color(0xFF7F8BA1), Color(0xFF3A4252)),
    )

    operator fun get(index: Int): JellyFlavor = all[Math.floorMod(index, all.size)]
}

/** Colours of the calendar around the jellies. */
@Immutable
data class JellyColors(
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val gridLine: Color,
    val gridLineStrong: Color,
    val text: Color,
    val textSub: Color,
    val accent: Color,
    val onAccent: Color,
    val today: Color,
    val saturday: Color,
    val sunday: Color,
    val nowLine: Color,
    val wake: Color,
    val tray: Color,
    val trayEdge: Color,
    val danger: Color,
    val ok: Color,
    val isDark: Boolean,
)

val LightJellyColors = JellyColors(
    background = Color(0xFFFFF9F5),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF7EFEA),
    gridLine = Color(0xFFF1E7E1),
    gridLineStrong = Color(0xFFE5D8CF),
    text = Color(0xFF2B2530),
    textSub = Color(0xFF8C8089),
    accent = Color(0xFFFF6F93),
    onAccent = Color(0xFFFFFFFF),
    today = Color(0xFFFF6F93),
    saturday = Color(0xFF3F74F0),
    sunday = Color(0xFFEE4F63),
    nowLine = Color(0xFFFF4D6D),
    wake = Color(0xFFFFA21F),
    tray = Color(0xFFFFF0E8),
    trayEdge = Color(0xFFFFD3C2),
    danger = Color(0xFFE5484D),
    ok = Color(0xFF2DB386),
    isDark = false,
)

val DarkJellyColors = JellyColors(
    background = Color(0xFF16131B),
    surface = Color(0xFF211D28),
    surfaceSoft = Color(0xFF2A2532),
    gridLine = Color(0xFF26212D),
    gridLineStrong = Color(0xFF363040),
    text = Color(0xFFF4EFF7),
    textSub = Color(0xFFA89FB2),
    accent = Color(0xFFFF8FAB),
    onAccent = Color(0xFF3A0716),
    today = Color(0xFFFF8FAB),
    saturday = Color(0xFF86A8FF),
    sunday = Color(0xFFFF8A96),
    nowLine = Color(0xFFFF6B86),
    wake = Color(0xFFFFB84D),
    tray = Color(0xFF231C27),
    trayEdge = Color(0xFF4A3747),
    danger = Color(0xFFFF6B6F),
    ok = Color(0xFF5FD3A8),
    isDark = true,
)

val LocalJellyColors = staticCompositionLocalOf { LightJellyColors }

/** Material's type scale in [family], without the wide letter spacing meant for Latin text. */
private fun typographyIn(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.inFamily() = copy(fontFamily = family, letterSpacing = 0.sp)
    return Typography(
        displayLarge = base.displayLarge.inFamily(),
        displayMedium = base.displayMedium.inFamily(),
        displaySmall = base.displaySmall.inFamily(),
        headlineLarge = base.headlineLarge.inFamily(),
        headlineMedium = base.headlineMedium.inFamily(),
        headlineSmall = base.headlineSmall.inFamily(),
        titleLarge = base.titleLarge.inFamily(),
        titleMedium = base.titleMedium.inFamily(),
        titleSmall = base.titleSmall.inFamily(),
        bodyLarge = base.bodyLarge.inFamily(),
        bodyMedium = base.bodyMedium.inFamily(),
        bodySmall = base.bodySmall.inFamily(),
        labelLarge = base.labelLarge.inFamily(),
        labelMedium = base.labelMedium.inFamily(),
        labelSmall = base.labelSmall.inFamily(),
    )
}

@Composable
fun JellyTheme(
    dark: Boolean = isSystemInDarkTheme(),
    type: JellyType = SystemJellyType,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkJellyColors else LightJellyColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.wake,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceSoft,
            onSurfaceVariant = colors.textSub,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.wake,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceSoft,
            onSurfaceVariant = colors.textSub,
            error = colors.danger,
        )
    }
    val typography = remember(type.body) { typographyIn(type.body) }
    CompositionLocalProvider(LocalJellyColors provides colors, LocalJellyType provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
