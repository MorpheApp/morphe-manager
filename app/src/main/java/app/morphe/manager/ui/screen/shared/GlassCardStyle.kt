/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.theme.LocalAppCardColorResolver
import app.morphe.manager.ui.theme.LocalThemeTraits
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import app.morphe.manager.ui.theme.headerTitle
import app.morphe.manager.util.AppCardColorResolver
import app.morphe.manager.util.blend
import app.morphe.manager.util.requiresLightContent

/**
 * Look of the glass cards the home screen lists apps on and the sources page lists sources on: a
 * tint of their own color under white text, or the theme's surface where monochrome asks for none.
 */
internal data class GlassCardStyle(
    val monochrome: Boolean,
    val outlines: Boolean,
    val colorResolver: AppCardColorResolver?,
    val iconSize: Dp,
    val titleColor: Color,
    val subtitleColor: Color,
    val titleStyle: TextStyle,
    val subtitleStyle: TextStyle,
    val chipContainerColor: Color,
    val chipContentColor: Color,
    /** Fill of the one action a card offers, solid in its text color so it stands out of the glass. */
    val actionContainerColor: Color,
    val actionContentColor: Color,
    /** Fill of the mark on the icon, denser than a chip's: it is too small to lean on a label. */
    val markContainerColor: Color,
    /** Diameter of a mark on the icon, as tall as a chip but never past the share the icon spares. */
    val markSize: Dp,
    val cardColor: Color,
    val cardRadius: Dp = Defaults.CornerLargeIncreased,
    val cardHeight: Dp = 80.dp,
    val contentPadding: Dp = 16.dp,
    val contentSpacing: Dp = 16.dp,
    /** Gap between the name and the row under it. */
    val textSpacing: Dp = 4.dp
) {
    /** Even edge of a card in one color, which the gradient's lit edge has nothing to borrow from. */
    val flatEdgeColor: Color get() = titleColor.copy(alpha = 0.30f)

    /**
     * Applies the card colors chosen in the appearance settings to [bundleColors], the palette
     * declared by this card's own bundle. Stops bound to the bundle are derived from it, so the
     * result stays per-app even when the rest of the gradient is fixed.
     */
    fun cardColors(bundleColors: List<Color>): List<Color> =
        colorResolver?.resolve(bundleColors) ?: bundleColors

    /**
     * [color] after the card colors chosen in the appearance settings, read as one color, for a
     * card drawn without a gradient. Left as given while cards keep their own colors.
     */
    fun flatColor(color: Color): Color = colorResolver?.resolve(listOf(color))?.blend() ?: color

    /** Fill of a card in [color] alone, or the neutral one without it, see [FILL_ALPHA]. */
    fun flatFill(color: Color?): Color =
        if (monochrome || color == null) cardColor else color.copy(alpha = FILL_ALPHA)

    /**
     * This style adjusted to the gradient it will be drawn on. White reads on the colors a bundle
     * declares for itself, but the appearance settings let a card be any color, including one
     * light enough to swallow it.
     */
    fun onCard(bundleColors: List<Color>): GlassCardStyle = onFill(cardColors(bundleColors).blend())

    /** This style adjusted to a card in [color] alone, see [flatFill] and [onCard]. */
    fun onFill(color: Color?): GlassCardStyle {
        if (monochrome) return this

        val fill = color ?: cardColor
        if (fill.requiresLightContent()) return this

        // The shadows lift white text off the glass, and only smudge dark text on a light fill
        return copy(
            titleColor = Color.Black,
            subtitleColor = Color.Black.copy(alpha = subtitleColor.alpha),
            titleStyle = titleStyle.copy(shadow = null),
            subtitleStyle = subtitleStyle.copy(shadow = null),
            chipContainerColor = Color.Black.copy(alpha = chipContainerColor.alpha),
            chipContentColor = Color.Black,
            actionContainerColor = Color.Black,
            actionContentColor = Color.White,
            markContainerColor = Color.Black.copy(alpha = markContainerColor.alpha)
        )
    }

    private companion object {
        /** Where the glass sweep of a gradient card ends, so a card in one color sits as strong. */
        const val FILL_ALPHA = 0.64f
    }
}

@Composable
internal fun glassCardStyle(subtitleAlpha: Float = 0.75f): GlassCardStyle {
    // The cards draw a palette of their own rather than shared tokens, so they read the traits directly
    val traits = LocalThemeTraits.current
    val monochrome = traits.monochrome
    val titleShadow = ThemeTraitsDefaults.textShadow(
        Shadow(
            color = Color.Black.copy(alpha = 0.4f),
            offset = Offset(0f, 2f),
            blurRadius = 4f
        )
    )
    val subtitleShadow = ThemeTraitsDefaults.textShadow(
        Shadow(
            color = Color.Black.copy(alpha = 0.4f),
            offset = Offset(0f, 1f),
            blurRadius = 2f
        )
    )
    val iconSize = 60.dp

    return GlassCardStyle(
        monochrome = monochrome,
        outlines = traits.outlines,
        colorResolver = LocalAppCardColorResolver.current,
        iconSize = iconSize,
        titleColor = if (monochrome) MaterialTheme.colorScheme.onSurface else Color.White,
        subtitleColor = if (monochrome) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            Color.White.copy(alpha = subtitleAlpha)
        },
        titleStyle = MaterialTheme.typography.headerTitle.copy(shadow = titleShadow),
        subtitleStyle = MaterialTheme.typography.bodyMedium.copy(shadow = subtitleShadow),
        chipContainerColor = if (monochrome) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            Color.White.copy(alpha = 0.20f)
        },
        chipContentColor = if (monochrome) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            Color.White
        },
        actionContainerColor = if (monochrome) MaterialTheme.colorScheme.primary else Color.White,
        actionContentColor = if (monochrome) MaterialTheme.colorScheme.onPrimary else Color.Black,
        markContainerColor = if (monochrome) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            Color.White.copy(alpha = 0.35f)
        },
        markSize = minOf(statusBadgeHeight, iconSize * 0.4f),
        cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
