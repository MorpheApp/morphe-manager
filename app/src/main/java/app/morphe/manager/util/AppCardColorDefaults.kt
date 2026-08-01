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

/**
 * Resolves the palette of a single card. 'bundleColors' are the colors declared by that app's
 * bundle, so stops bound to the bundle can differ from card to card.
 */
fun interface AppCardColorResolver {
    fun resolve(bundleColors: List<Color>): List<Color>
}

object AppCardColorDefaults {
    private const val COLOR_VALUES_SEPARATOR = "|"

    /** Stored in place of a hex value when a stop follows the color declared by the app bundle. */
    const val BUNDLE_COLOR_TOKEN = "bundle"

    // Hue sweep standing in for the bundle colors of apps the user may install, wide enough that
    // the shading of a stop stays visible across both light and dark source colors
    private val BUNDLE_PREVIEW_HUES = listOf(
        Color(0xFFFF3B30),
        Color(0xFFFF9500),
        Color(0xFFFFCC00),
        Color(0xFF34C759),
        Color(0xFF007AFF),
        Color(0xFFAF52DE)
    )

    val defaultGradientColors: List<Color>
        get() = KnownApps.DEFAULT_COLORS

    val defaultSolidColor: Color
        get() = KnownApps.GRADIENT_MID

    fun isBundleColor(hex: String): Boolean =
        hex.trim().equals(BUNDLE_COLOR_TOKEN, ignoreCase = true)

    /**
     * Card color resolver for [mode], or `null` when cards keep the per-app colors declared by
     * their bundle. [accentFallback] is the accent of the active theme, used by
     * [AppCardColorMode.ACCENT] when the user has not picked a custom accent color.
     */
    fun resolver(
        mode: AppCardColorMode,
        accentHex: String,
        accentFallback: Color,
        values: AppCardColorValues
    ): AppCardColorResolver? = when (mode) {
        AppCardColorMode.DEFAULT -> null

        AppCardColorMode.ACCENT -> {
            val accentPalette = accentColors(accentHex.toColorOrNull() ?: accentFallback)
            AppCardColorResolver { accentPalette }
        }

        // Hex stops are parsed once here rather than on every resolve, because only the stops
        // bound to the bundle depend on the card being drawn
        AppCardColorMode.GRADIENT -> {
            val start = fixedStop(values.startHex, defaultGradientColors[0])
            val middle = fixedStop(values.middleHex, defaultGradientColors[1])
            val end = fixedStop(values.endHex, defaultGradientColors[2])

            if (start != null && middle != null && end != null) {
                val palette = listOf(start, middle, end)
                AppCardColorResolver { palette }
            } else {
                AppCardColorResolver { bundleColors ->
                    val bundleColor = bundleColors.bundleColor()
                    listOf(
                        start ?: bundleShade(AppCardColorStop.START, bundleColor),
                        middle ?: bundleShade(AppCardColorStop.MIDDLE, bundleColor),
                        end ?: bundleShade(AppCardColorStop.END, bundleColor)
                    )
                }
            }
        }

        AppCardColorMode.SOLID -> {
            val fixed = fixedStop(values.solidHex, defaultSolidColor)

            if (fixed != null) {
                val palette = listOf(fixed, fixed, fixed)
                AppCardColorResolver { palette }
            } else {
                AppCardColorResolver { bundleColors ->
                    val color = bundleShade(AppCardColorStop.SOLID, bundleColors.bundleColor())
                    listOf(color, color, color)
                }
            }
        }
    }

    /**
     * Card colors for [mode] without a specific app in context, used by previews. Bundle-bound
     * stops resolve against [previewBundleColors]. Null keeps cards on their own bundle colors.
     */
    fun colors(
        mode: AppCardColorMode,
        accentHex: String,
        accentFallback: Color,
        values: AppCardColorValues,
        previewBundleColors: List<Color> = defaultGradientColors
    ): List<Color>? = resolver(mode, accentHex, accentFallback, values)
        ?.resolve(previewBundleColors)

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
        endHex: String,
        bundleColor: Color = defaultGradientColors[0]
    ): List<Color> = listOf(
        stopColor(startHex, AppCardColorStop.START, bundleColor, defaultGradientColors[0]),
        stopColor(middleHex, AppCardColorStop.MIDDLE, bundleColor, defaultGradientColors[1]),
        stopColor(endHex, AppCardColorStop.END, bundleColor, defaultGradientColors[2])
    )

    fun solidColors(colorHex: String, bundleColor: Color = defaultSolidColor): List<Color> {
        val color = stopColor(colorHex, AppCardColorStop.SOLID, bundleColor, defaultSolidColor)
        return listOf(color, color, color)
    }

    /** Color of [stop]: either its fixed [hex] value or a shade derived from [bundleColor]. */
    private fun stopColor(
        hex: String,
        stop: AppCardColorStop,
        bundleColor: Color,
        fallback: Color
    ): Color = fixedStop(hex, fallback) ?: bundleShade(stop, bundleColor)

    /** Fixed color of a stop, or `null` when it follows the bundle and needs a card to resolve. */
    private fun fixedStop(hex: String, fallback: Color): Color? =
        if (isBundleColor(hex)) null else (hex.toColorOrNull() ?: fallback)

    /**
     * Stand-in shown where a bundle-bound [stop] would otherwise preview as one color: a hue
     * sweep shaded the way the stop shades a real bundle color, so the preview reads as varying
     * per app instead of as a color the user picked.
     */
    fun bundleStopPreview(stop: AppCardColorStop): List<Color> =
        BUNDLE_PREVIEW_HUES.map { bundleShade(stop, it) }

    /**
     * Shade of [bundleColor] used by a stop bound to the bundle. The start stop keeps the color
     * untouched so a card still opens on the app's own color the way [AppCardColorMode.DEFAULT]
     * does, while the later stops drift away from it to keep the gradient from reading flat.
     */
    private fun bundleShade(stop: AppCardColorStop, bundleColor: Color): Color = when (stop) {
        AppCardColorStop.START, AppCardColorStop.SOLID -> bundleColor

        AppCardColorStop.MIDDLE -> if (bundleColor.requiresLightContent()) {
            bundleColor.lighten(0.18f)
        } else {
            bundleColor.darken(0.16f)
        }

        AppCardColorStop.END -> if (bundleColor.requiresLightContent()) {
            bundleColor.lighten(0.38f)
        } else {
            bundleColor.darken(0.34f)
        }
    }

    private fun List<Color>.bundleColor(): Color = firstOrNull() ?: defaultGradientColors[0]
}
