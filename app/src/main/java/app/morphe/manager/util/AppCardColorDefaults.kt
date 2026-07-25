/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import androidx.compose.ui.graphics.Color

enum class AppCardColorMode {
    DEFAULT,
    ACCENT,
    GRADIENT,
    SOLID
}

enum class AppCardColorStop {
    START,
    MIDDLE,
    END,
    SOLID
}

object AppCardColorDefaults {
    val defaultGradientColors: List<Color>
        get() = KnownApps.DEFAULT_COLORS

    val defaultSolidColor: Color
        get() = defaultGradientColors.getOrElse(1) {
            defaultGradientColors.firstOrNull() ?: Color.White
        }

    val defaultAccentColor: Color
        get() = KnownApps.GRADIENT_MID

    fun colors(
        mode: AppCardColorMode,
        accentHex: String,
        startHex: String,
        middleHex: String,
        endHex: String,
        solidHex: String
    ): List<Color> = when (mode) {
        AppCardColorMode.DEFAULT -> defaultColors()
        AppCardColorMode.ACCENT -> accentColors(accentHex)
        AppCardColorMode.GRADIENT -> gradientColors(startHex, middleHex, endHex)
        AppCardColorMode.SOLID -> solidColors(solidHex)
    }

    fun previewColors(
        mode: AppCardColorMode,
        accentHex: String,
        startHex: String,
        middleHex: String,
        endHex: String,
        solidHex: String
    ): List<Color> = colors(
        mode = mode,
        accentHex = accentHex,
        startHex = startHex,
        middleHex = middleHex,
        endHex = endHex,
        solidHex = solidHex
    )

    fun defaultColors(): List<Color> = defaultGradientColors

    fun accentColors(accentHex: String): List<Color> {
        val accent = accentHex.toColorOrNull() ?: defaultAccentColor
        return accentColors(accent)
    }

    fun accentColors(accent: Color): List<Color> =
        listOf(
            accent.darken(if (accent.requiresLightContent()) 0.16f else 0.46f),
            accent,
            if (accent.requiresLightContent()) accent.lighten(0.22f) else accent.darken(0.18f)
        )

    fun gradientColors(
        startHex: String,
        middleHex: String,
        endHex: String
    ): List<Color> {
        val fallbackColors = defaultGradientColors
        return listOf(
            startHex.toColorOrNull() ?: fallbackColors.getOrElse(0) { Color.White },
            middleHex.toColorOrNull() ?: fallbackColors.getOrElse(1) {
                fallbackColors.firstOrNull() ?: Color.White
            },
            endHex.toColorOrNull() ?: fallbackColors.getOrElse(2) {
                fallbackColors.lastOrNull() ?: Color.White
            }
        )
    }

    fun solidColors(colorHex: String): List<Color> {
        val color = colorHex.toColorOrNull() ?: defaultSolidColor
        return listOf(color, color, color)
    }
}
