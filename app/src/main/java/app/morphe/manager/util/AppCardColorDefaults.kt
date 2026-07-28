/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import androidx.compose.ui.graphics.Color
import app.morphe.manager.R
import kotlinx.serialization.Serializable

@Serializable
enum class AppCardColorMode(val labelResId: Int, val descriptionResId: Int) {
    DEFAULT(
        R.string.settings_appearance_app_card_colors_default,
        R.string.settings_appearance_app_card_colors_default_description
    ),
    ACCENT(
        R.string.settings_appearance_app_card_colors_accent,
        R.string.settings_appearance_app_card_colors_accent_description
    ),
    GRADIENT(
        R.string.settings_appearance_app_card_colors_gradient,
        R.string.settings_appearance_app_card_colors_gradient_description
    ),
    SOLID(
        R.string.settings_appearance_app_card_colors_solid,
        R.string.settings_appearance_app_card_colors_solid_description
    )
}

enum class AppCardColorStop(val titleResId: Int) {
    START(R.string.settings_appearance_app_card_colors_start),
    MIDDLE(R.string.settings_appearance_app_card_colors_middle),
    END(R.string.settings_appearance_app_card_colors_end),
    SOLID(R.string.settings_appearance_app_card_colors_solid_color)
}

data class AppCardColorValues(
    val startHex: String = "",
    val middleHex: String = "",
    val endHex: String = "",
    val solidHex: String = ""
)

object AppCardColorDefaults {
    private const val COLOR_VALUES_SEPARATOR = "|"

    val defaultGradientColors: List<Color>
        get() = KnownApps.DEFAULT_COLORS

    val defaultSolidColor: Color
        get() = KnownApps.GRADIENT_MID

    /**
     * Card colors for [mode], or `null` when cards keep the per-app colors declared by their
     * bundle. [accentFallback] is the accent of the active theme, used by [AppCardColorMode.ACCENT]
     * when the user has not picked a custom accent color.
     */
    fun colors(
        mode: AppCardColorMode,
        accentHex: String,
        accentFallback: Color,
        values: AppCardColorValues
    ): List<Color>? = when (mode) {
        AppCardColorMode.DEFAULT -> null
        AppCardColorMode.ACCENT -> accentColors(accentHex.toColorOrNull() ?: accentFallback)
        AppCardColorMode.GRADIENT -> gradientColors(
            startHex = values.startHex,
            middleHex = values.middleHex,
            endHex = values.endHex
        )
        AppCardColorMode.SOLID -> solidColors(values.solidHex)
    }

    fun encodeColorValues(
        startHex: String,
        middleHex: String,
        endHex: String,
        solidHex: String
    ): String = listOf(startHex, middleHex, endHex, solidHex)
        .joinToString(COLOR_VALUES_SEPARATOR)

    fun decodeColorValues(value: String): AppCardColorValues {
        if (value.isBlank()) return AppCardColorValues()

        val parts = value.split(COLOR_VALUES_SEPARATOR)
        return AppCardColorValues(
            startHex = parts.getOrNull(0).orEmpty(),
            middleHex = parts.getOrNull(1).orEmpty(),
            endHex = parts.getOrNull(2).orEmpty(),
            solidHex = parts.getOrNull(3).orEmpty()
        )
    }

    /** Spreads [accent] into a three-stop gradient so single-color cards keep their depth. */
    fun accentColors(accent: Color): List<Color> = listOf(
        accent.darken(if (accent.requiresLightContent()) 0.16f else 0.46f),
        accent,
        if (accent.requiresLightContent()) accent.lighten(0.22f) else accent.darken(0.18f)
    )

    fun gradientColors(
        startHex: String,
        middleHex: String,
        endHex: String
    ): List<Color> = listOf(
        startHex.toColorOrNull() ?: defaultGradientColors[0],
        middleHex.toColorOrNull() ?: defaultGradientColors[1],
        endHex.toColorOrNull() ?: defaultGradientColors[2]
    )

    fun solidColors(colorHex: String): List<Color> {
        val color = colorHex.toColorOrNull() ?: defaultSolidColor
        return listOf(color, color, color)
    }
}
