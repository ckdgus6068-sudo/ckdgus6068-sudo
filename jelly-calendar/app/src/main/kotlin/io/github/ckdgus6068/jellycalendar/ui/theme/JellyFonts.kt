package io.github.ckdgus6068.jellycalendar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.ckdgus6068.jellycalendar.core.FontChoice

/**
 * The typefaces shipped with the app, loaded by the platform side.
 * [round] is Bagel Fat One, declared as one Black weight so it is never artificially bolded.
 * [clean] is Pretendard in Regular, SemiBold and Bold.
 */
@Immutable
class BundledFonts(val round: FontFamily?, val clean: FontFamily?) {
    companion object {
        val None = BundledFonts(null, null)
    }
}

/**
 * [display] letters the jellies, dates and headings; [body] is everything else.
 * [displayWeight] is the weight to ask [display] for.
 */
@Immutable
data class JellyType(
    val display: FontFamily,
    val displayWeight: FontWeight,
    val body: FontFamily,
)

val SystemJellyType = JellyType(FontFamily.Default, FontWeight.ExtraBold, FontFamily.Default)

fun jellyType(choice: FontChoice, fonts: BundledFonts): JellyType {
    val clean = fonts.clean
    val round = fonts.round
    return when {
        choice == FontChoice.ROUND && round != null -> JellyType(round, FontWeight.Black, clean ?: FontFamily.Default)
        choice != FontChoice.SYSTEM && clean != null -> JellyType(clean, FontWeight.Bold, clean)
        else -> SystemJellyType
    }
}

val LocalJellyType = staticCompositionLocalOf { SystemJellyType }
