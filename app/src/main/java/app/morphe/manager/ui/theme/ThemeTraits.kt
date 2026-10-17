/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the appearance settings dress every screen beyond the color scheme itself. Components read
 * it through [ThemeTraitsDefaults] rather than from here directly.
 *
 * @param monochrome Whether the monochrome style flattens the app's accent-heavy visuals into neutral tokens.
 * @param colorAccents Whether apps and sources wear their own colors, or the theme's accent instead.
 * @param outlines Whether cards, panels and buttons draw a hairline edge around them.
 * @param blur Whether headers, bars and panels blur what scrolls under them.
 */
@Immutable
data class ThemeTraits(
    val monochrome: Boolean = false,
    val colorAccents: Boolean = true,
    val outlines: Boolean = true,
    val blur: Boolean = true
)

val LocalThemeTraits = staticCompositionLocalOf { ThemeTraits() }

/**
 * Adapters that turn [ThemeTraits] into what components draw with, passing the original values
 * through where no trait asks otherwise. Component code routes through here instead of branching
 * on [LocalThemeTraits] so that a new override lands in a single place.
 */
object ThemeTraitsDefaults {
    private val traits: ThemeTraits
        @Composable @ReadOnlyComposable get() = LocalThemeTraits.current

    /**
     * [base], an app's or a source's own color, or the theme's accent in its place where monochrome
     * or the appearance settings leave apps and sources without colors of their own.
     */
    @Composable
    fun accentColor(base: Color): Color =
        if (traits.monochrome || !traits.colorAccents) MaterialTheme.colorScheme.primary else base

    @Composable
    fun surfaceColor(base: Color, selected: Boolean = false): Color {
        if (!traits.monochrome) return base

        val colors = MaterialTheme.colorScheme
        return if (selected) colors.primaryContainer else colors.surfaceContainerHigh
    }

    /** Content paired with a fill [surfaceColor] tinted, or [neutral] where monochrome drops the tint. */
    @Composable
    fun contentColor(base: Color, neutral: Color): Color = if (traits.monochrome) neutral else base

    /** [base] for a translucent control, or a faint one where monochrome flattens the control. */
    @Composable
    fun borderColor(base: Color, selected: Boolean = false): Color {
        if (!traits.monochrome) return base

        val colors = MaterialTheme.colorScheme
        val edge = if (selected) colors.primary else colors.outlineVariant
        return edge.copy(alpha = if (isDarkTheme()) 0.42f else 0.34f)
    }

    /** Edge in [color], or none where the appearance settings take outlines off. */
    @Composable
    fun outline(color: Color, width: Dp): BorderStroke? =
        if (traits.outlines) BorderStroke(width, color) else null

    /** Whether a surface floating over content blurs it, or stands solid where the settings say not to. */
    @Composable
    fun blur(): Boolean = traits.blur

    @Composable
    fun cardElevation(base: Dp): Dp = if (traits.monochrome) 0.dp else base

    /** Divider between the rows of a card, tinted after the theme unless monochrome keeps it neutral. */
    @Composable
    fun dividerColor(): Color {
        val colors = MaterialTheme.colorScheme
        return if (traits.monochrome) {
            colors.outlineVariant.copy(alpha = 0.28f)
        } else {
            lerp(colors.outlineVariant, colors.surfaceTint, 0.18f).copy(alpha = 0.55f)
        }
    }

    /**
     * Text shadows read as noise on a flat monochrome palette, so drop them
     * when monochrome is active and pass them through otherwise.
     */
    @Composable
    fun textShadow(base: Shadow): Shadow? = if (traits.monochrome) null else base

    /**
     * Solid neutral fill in monochrome mode, gradient fill otherwise. Used by
     * decorative circular icons that would otherwise carry a colored gradient.
     */
    @Composable
    fun iconBackground(gradient: List<Color>): Brush =
        if (traits.monochrome) {
            SolidColor(MaterialTheme.colorScheme.primaryContainer)
        } else {
            Brush.linearGradient(gradient)
        }

    /**
     * Paired with [iconBackground], which fills these icons with the primary container. Primary
     * itself sits too close to that fill once a custom accent derives both from one color.
     */
    @Composable
    fun iconTint(base: Color): Color =
        if (traits.monochrome) MaterialTheme.colorScheme.onPrimaryContainer else base
}
