package app.revanced.manager.ui.component.morphe.utils

import androidx.compose.ui.graphics.Color

/**
 * Determine if a color represents a dark background
 */
fun Color.isDarkBackground(): Boolean = luminance() < 0.5f

/**
 * Get luminance from a Color
 */
fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

/**
 * Lighten a color by mixing with white
 */
fun Color.lighten(factor: Float): Color {
    return Color(
        red = red + (1f - red) * factor,
        green = green + (1f - green) * factor,
        blue = blue + (1f - blue) * factor,
        alpha = alpha
    )
}

/**
 * Darken a color by mixing with black
 */
fun Color.darken(factor: Float): Color {
    return Color(
        red = red * (1f - factor),
        green = green * (1f - factor),
        blue = blue * (1f - factor),
        alpha = alpha
    )
}
