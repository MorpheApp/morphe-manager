package app.morphe.manager.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt

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

fun Color.toHexString(includeAlpha: Boolean = false): String {
    val argb = toArgb()
    return if (includeAlpha) {
        String.format("#%08X", argb)
    } else {
        String.format("#%06X", argb and 0xFFFFFF)
    }
}

fun String?.toColorOrNull(): Color? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching {
        val hexValue = if (value.startsWith("#")) value else "#$value"
        Color(hexValue.toColorInt())
    }.getOrNull()
}

/**
 * Parse color string to RGB float values (0-1 range)
 */
fun parseColorToRgb(color: String): Triple<Float, Float, Float> {
    return color.toColorOrNull()?.let {
        Triple(it.red, it.green, it.blue)
    } ?: Triple(0f, 0f, 0f)
}

/**
 * Parse hex color string to RGB float values
 * Supports both #RRGGBB and #AARRGGBB formats
 */
fun parseHexToRgb(hex: String): Triple<Float, Float, Float>? {
    return hex.toColorOrNull()?.let {
        Triple(it.red, it.green, it.blue)
    }
}

/**
 * Convert RGB float values to hex string
 */
fun rgbToHex(r: Float, g: Float, b: Float): String {
    return Color(r, g, b).toHexString(includeAlpha = false)
}
