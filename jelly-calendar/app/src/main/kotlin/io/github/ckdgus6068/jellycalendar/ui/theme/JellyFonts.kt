package io.github.ckdgus6068.jellycalendar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.ckdgus6068.jellycalendar.core.FontChoice

/** A display face, declared at the one weight it is drawn in so Compose never fakes another. */
@Immutable
class DisplayFace(val family: FontFamily, val weight: FontWeight)

/**
 * The typefaces shipped with the app, loaded by the platform side.
 * [clean] is Pretendard in Regular, SemiBold and Bold: the body text of every bundled choice and
 * the lettering of [FontChoice.CLEAN]. [faces] letters the other choices.
 */
@Immutable
class BundledFonts(val clean: FontFamily?, val faces: Map<FontChoice, DisplayFace> = emptyMap()) {
    companion object {
        val None = BundledFonts(null)
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
    if (choice == FontChoice.SYSTEM) return SystemJellyType
    val clean = fonts.clean
    val face = fonts.faces[choice]
    return when {
        face != null -> JellyType(face.family, face.weight, clean ?: FontFamily.Default)
        clean != null -> JellyType(clean, FontWeight.Bold, clean)
        else -> SystemJellyType
    }
}

/** The choices offered in settings, gentlest first. */
val FontChoices = listOf(FontChoice.NANUM_ROUND, FontChoice.JUA, FontChoice.CLEAN, FontChoice.ROUND, FontChoice.SYSTEM)

fun fontName(choice: FontChoice): String = when (choice) {
    FontChoice.NANUM_ROUND -> "동글"
    FontChoice.JUA -> "말랑"
    FontChoice.CLEAN -> "깔끔"
    FontChoice.ROUND -> "통통"
    FontChoice.SYSTEM -> "휴대폰 글꼴"
}

val LocalJellyType = staticCompositionLocalOf { SystemJellyType }
