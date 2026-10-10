/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import kotlin.math.max
import kotlin.math.min

/**
 * Hue in degrees, saturation and value in 0..1, the axes a color picker actually offers.
 *
 * Gray has no hue to speak of and the conversion reports 0 for it, which would swing the picker
 * back to red the moment a color loses its saturation, so callers keep the hue they were showing.
 */
fun Color.toHsv(): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return Triple(hsv[0], hsv[1], hsv[2])
}

/** Even blend of these colors, for judging what a gradient reads as overall. */
fun List<Color>.blend(): Color = when {
    isEmpty() -> Color.Transparent
    size == 1 -> first()
    else -> Color(
        red = sumOf { it.red.toDouble() }.toFloat() / size,
        green = sumOf { it.green.toDouble() }.toFloat() / size,
        blue = sumOf { it.blue.toDouble() }.toFloat() / size,
        alpha = sumOf { it.alpha.toDouble() }.toFloat() / size
    )
}

/** Determine if a color represents a dark background, the one [requiresLightContent] reads on. */
fun Color.isDarkBackground(): Boolean = requiresLightContent()

/** Returns true if the color is near-black or near-white, where tinted surfaces look better than a direct tint. */
fun Color.isExtremeAccent(): Boolean = luminance() !in 0.04f..0.92f

/**
 * Composites this color at [alpha] over [background] and returns the opaque result.
 */
fun Color.compositeOver(background: Color, alpha: Float = this.alpha): Color = Color(
    red   = background.red   * (1f - alpha) + red   * alpha,
    green = background.green * (1f - alpha) + green * alpha,
    blue  = background.blue  * (1f - alpha) + blue  * alpha,
)

/**
 * Returns true if this color, when used as a background, requires light (white) content for contrast.
 * Uses WCAG relative luminance threshold.
 */
fun Color.requiresLightContent(): Boolean = luminance() < 0.5f

/** Whichever of white and black stands out of this color, see [requiresLightContent]. */
fun Color.contrastingContent(): Color = if (requiresLightContent()) Color.White else Color.Black

/** WCAG contrast ratio against [background], from 1 for identical colors to 21 for black on white. */
fun Color.contrastAgainst(background: Color): Float {
    val content = luminance()
    val behind = background.luminance()
    return (max(content, behind) + 0.05f) / (min(content, behind) + 0.05f)
}

/**
 * This color when it suits a [fill] drawn over [surface], or plain black or white when it does not.
 * Any transparency of its own is carried over, so a dimmed color stays dimmed.
 *
 * A palette pairs each container with an on-color meant for that container drawn opaque. Tinted,
 * the fill becomes a blend the pairing never described, and a light container blends dark while
 * its on-color stays dark. Contrast alone passes that, so polarity is weighed alongside [minRatio].
 */
fun Color.readableOn(fill: Color, surface: Color, minRatio: Float = 3f): Color {
    val background = fill.compositeOver(surface)
    val wantsLightContent = background.requiresLightContent()

    val agreesWithFill = wantsLightContent != requiresLightContent()
    if (agreesWithFill && contrastAgainst(background) >= minRatio) return this

    val replacement = if (wantsLightContent) Color.White else Color.Black
    return replacement.copy(alpha = alpha)
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

/**
 * Adjusts the color so it has sufficient contrast against [background].
 * If the accent is too close to the background (same lightness zone),
 * it is lightened or darkened until it passes the [minLuminanceDiff] threshold.
 */
fun Color.ensureContrast(
    background: Color,
    minLuminanceDiff: Float = 0.05f
): Color {
    val bgLum = background.luminance()
    val fgLum = this.luminance()
    val diff = kotlin.math.abs(bgLum - fgLum)
    if (diff >= minLuminanceDiff) return this
    return if (bgLum > 0.5f) darken((minLuminanceDiff - diff + 0.05f).coerceIn(0f, 0.8f))
    else lighten((minLuminanceDiff - diff + 0.05f).coerceIn(0f, 0.8f))
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
 * Convert RGB float values to hex string
 */
fun rgbToHex(r: Float, g: Float, b: Float): String {
    return Color(r, g, b).toHexString(includeAlpha = false)
}
