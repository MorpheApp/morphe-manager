/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/ui/theme/Theme.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import app.morphe.manager.R
import app.morphe.manager.util.AppCardColorDefaults
import app.morphe.manager.util.AppCardColorMode
import app.morphe.manager.util.AppCardColorResolver
import app.morphe.manager.util.AppCardColorValues
import app.morphe.manager.util.contrastingContent
import app.morphe.manager.util.isDarkBackground
import app.morphe.manager.util.toColorOrNull
import kotlinx.serialization.Serializable

private val DarkColorScheme = darkColorScheme(
    primary = theme_dark_primary,
    onPrimary = theme_dark_onPrimary,
    primaryContainer = theme_dark_primaryContainer,
    onPrimaryContainer = theme_dark_onPrimaryContainer,
    secondary = theme_dark_secondary,
    onSecondary = theme_dark_onSecondary,
    secondaryContainer = theme_dark_secondaryContainer,
    onSecondaryContainer = theme_dark_onSecondaryContainer,
    tertiary = theme_dark_tertiary,
    onTertiary = theme_dark_onTertiary,
    tertiaryContainer = theme_dark_tertiaryContainer,
    onTertiaryContainer = theme_dark_onTertiaryContainer,
    error = theme_dark_error,
    errorContainer = theme_dark_errorContainer,
    onError = theme_dark_onError,
    onErrorContainer = theme_dark_onErrorContainer,
    background = theme_dark_background,
    onBackground = theme_dark_onBackground,
    surface = theme_dark_surface,
    onSurface = theme_dark_onSurface,
    surfaceVariant = theme_dark_surfaceVariant,
    onSurfaceVariant = theme_dark_onSurfaceVariant,
    outline = theme_dark_outline,
    inverseOnSurface = theme_dark_inverseOnSurface,
    inverseSurface = theme_dark_inverseSurface,
    inversePrimary = theme_dark_inversePrimary,
    surfaceTint = theme_dark_surfaceTint,
    outlineVariant = theme_dark_outlineVariant,
    scrim = theme_dark_scrim,
)

private val LightColorScheme = lightColorScheme(
    primary = theme_light_primary,
    onPrimary = theme_light_onPrimary,
    primaryContainer = theme_light_primaryContainer,
    onPrimaryContainer = theme_light_onPrimaryContainer,
    secondary = theme_light_secondary,
    onSecondary = theme_light_onSecondary,
    secondaryContainer = theme_light_secondaryContainer,
    onSecondaryContainer = theme_light_onSecondaryContainer,
    tertiary = theme_light_tertiary,
    onTertiary = theme_light_onTertiary,
    tertiaryContainer = theme_light_tertiaryContainer,
    onTertiaryContainer = theme_light_onTertiaryContainer,
    error = theme_light_error,
    errorContainer = theme_light_errorContainer,
    onError = theme_light_onError,
    onErrorContainer = theme_light_onErrorContainer,
    background = theme_light_background,
    onBackground = theme_light_onBackground,
    surface = theme_light_surface,
    onSurface = theme_light_onSurface,
    surfaceVariant = theme_light_surfaceVariant,
    onSurfaceVariant = theme_light_onSurfaceVariant,
    outline = theme_light_outline,
    inverseOnSurface = theme_light_inverseOnSurface,
    inverseSurface = theme_light_inverseSurface,
    inversePrimary = theme_light_inversePrimary,
    surfaceTint = theme_light_surfaceTint,
    outlineVariant = theme_light_outlineVariant,
    scrim = theme_light_scrim,
)

private fun monochromeColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        DarkColorScheme.copy(
            primary = monochrome_dark_primary,
            onPrimary = monochrome_dark_onPrimary,
            primaryContainer = monochrome_dark_primaryContainer,
            onPrimaryContainer = monochrome_dark_onPrimaryContainer,
            secondary = monochrome_dark_secondary,
            onSecondary = monochrome_dark_onSecondary,
            secondaryContainer = monochrome_dark_secondaryContainer,
            onSecondaryContainer = monochrome_dark_onSecondaryContainer,
            tertiary = monochrome_dark_tertiary,
            onTertiary = monochrome_dark_onTertiary,
            tertiaryContainer = monochrome_dark_tertiaryContainer,
            onTertiaryContainer = monochrome_dark_onTertiaryContainer,
            background = monochrome_dark_background,
            onBackground = monochrome_dark_onBackground,
            surface = monochrome_dark_surface,
            onSurface = monochrome_dark_onSurface,
            surfaceVariant = monochrome_dark_surfaceVariant,
            onSurfaceVariant = monochrome_dark_onSurfaceVariant,
            outline = monochrome_dark_outline,
            outlineVariant = monochrome_dark_outlineVariant,
            inverseOnSurface = monochrome_dark_inverseOnSurface,
            inverseSurface = monochrome_dark_inverseSurface,
            inversePrimary = monochrome_dark_inversePrimary,
            surfaceTint = monochrome_dark_surfaceTint,
            surfaceContainerLowest = monochrome_dark_surfaceContainerLowest,
            surfaceContainerLow = monochrome_dark_surfaceContainerLow,
            surfaceContainer = monochrome_dark_surfaceContainer,
            surfaceContainerHigh = monochrome_dark_surfaceContainerHigh,
            surfaceContainerHighest = monochrome_dark_surfaceContainerHighest,
            surfaceBright = monochrome_dark_surfaceBright,
            surfaceDim = monochrome_dark_surfaceDim
        )
    } else {
        LightColorScheme.copy(
            primary = monochrome_light_primary,
            onPrimary = monochrome_light_onPrimary,
            primaryContainer = monochrome_light_primaryContainer,
            onPrimaryContainer = monochrome_light_onPrimaryContainer,
            secondary = monochrome_light_secondary,
            onSecondary = monochrome_light_onSecondary,
            secondaryContainer = monochrome_light_secondaryContainer,
            onSecondaryContainer = monochrome_light_onSecondaryContainer,
            tertiary = monochrome_light_tertiary,
            onTertiary = monochrome_light_onTertiary,
            tertiaryContainer = monochrome_light_tertiaryContainer,
            onTertiaryContainer = monochrome_light_onTertiaryContainer,
            background = monochrome_light_background,
            onBackground = monochrome_light_onBackground,
            surface = monochrome_light_surface,
            onSurface = monochrome_light_onSurface,
            surfaceVariant = monochrome_light_surfaceVariant,
            onSurfaceVariant = monochrome_light_onSurfaceVariant,
            outline = monochrome_light_outline,
            outlineVariant = monochrome_light_outlineVariant,
            inverseOnSurface = monochrome_light_inverseOnSurface,
            inverseSurface = monochrome_light_inverseSurface,
            inversePrimary = monochrome_light_inversePrimary,
            surfaceTint = monochrome_light_surfaceTint,
            surfaceContainerLowest = monochrome_light_surfaceContainerLowest,
            surfaceContainerLow = monochrome_light_surfaceContainerLow,
            surfaceContainer = monochrome_light_surfaceContainer,
            surfaceContainerHigh = monochrome_light_surfaceContainerHigh,
            surfaceContainerHighest = monochrome_light_surfaceContainerHighest,
            surfaceBright = monochrome_light_surfaceBright,
            surfaceDim = monochrome_light_surfaceDim
        )
    }

/**
 * Resolves home app card colors from the appearance settings, or `null` when cards keep the
 * per-app colors declared by their bundle.
 */
val LocalAppCardColorResolver = staticCompositionLocalOf<AppCardColorResolver?> { null }

@Composable
fun ManagerTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    pureBlackTheme: Boolean,
    traits: ThemeTraits = ThemeTraits(),
    accentColorHex: String? = null,
    themeColorHex: String? = null,
    appCardColorMode: AppCardColorMode = AppCardColorMode.DEFAULT,
    appCardColorValues: AppCardColorValues = AppCardColorValues(),
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        traits.monochrome -> monochromeColorScheme(darkTheme)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.let {
        if (darkTheme && pureBlackTheme) {
            val pureBlack = Color.Black
            it.copy(
                background = pureBlack,
                surface = pureBlack,
                surfaceDim = pureBlack
            )
        } else it
    }

    val schemeWithAccent = accentColorHex.toColorOrNull()?.let {
        applyCustomAccent(baseScheme, it, darkTheme)
    } ?: baseScheme

    val finalScheme = if (traits.monochrome) {
        schemeWithAccent
    } else {
        themeColorHex.toColorOrNull()?.let {
            applyCustomThemeColor(schemeWithAccent, it, darkTheme)
        } ?: schemeWithAccent
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // The edge to edge layout and the transparent bars come from enableEdgeToEdge() in
        // MainActivity, so only the icon tint is left to follow the theme
        SideEffect {
            val activity = view.context as Activity
            val insetsController = WindowCompat.getInsetsController(activity.window, view)

            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // Monochrome cards draw on neutral theme surfaces, so custom card colors never apply there.
    // Remembered because a new resolver instance would invalidate every card that reads it
    val appCardColorResolver = remember(
        traits.monochrome,
        appCardColorMode,
        accentColorHex,
        finalScheme.primary,
        appCardColorValues
    ) {
        if (traits.monochrome) {
            null
        } else {
            AppCardColorDefaults.resolver(
                mode = appCardColorMode,
                accentHex = accentColorHex.orEmpty(),
                accentFallback = finalScheme.primary,
                values = appCardColorValues
            )
        }
    }

    CompositionLocalProvider(
        LocalThemeTraits provides traits,
        LocalAppCardColorResolver provides appCardColorResolver
    ) {
        MaterialTheme(
            colorScheme = finalScheme,
            typography = Typography,
            content = content
        )
    }
}

@Serializable
enum class Theme(val displayName: Int) {
    SYSTEM(R.string.settings_appearance_system),
    LIGHT(R.string.settings_appearance_light),
    DARK(R.string.settings_appearance_dark);
}

@Serializable
enum class ThemeStyle(val displayName: Int) {
    MORPHE(R.string.settings_appearance_style_morphe),
    MATERIAL_YOU(R.string.settings_appearance_dynamic),
    MONOCHROME(R.string.settings_appearance_monochrome);
}

/**
 * Whether the theme in effect is a dark one. Read from the background rather than the mode picked
 * in the settings, so a pure black or custom colored theme still gets the colors meant for it.
 */
@Composable
@ReadOnlyComposable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.isDarkBackground()

/**
 * Downgrades [ThemeStyle.MATERIAL_YOU] to [ThemeStyle.MORPHE] on devices that
 * do not expose the platform dynamic color palette.
 */
fun resolveThemeStyle(storedStyle: ThemeStyle, supportsDynamicColor: Boolean): ThemeStyle =
    if (storedStyle == ThemeStyle.MATERIAL_YOU && !supportsDynamicColor) ThemeStyle.MORPHE
    else storedStyle

private fun applyCustomAccent(
    colorScheme: ColorScheme,
    accent: Color,
    darkTheme: Boolean
): ColorScheme {
    val primaryContainer = accent.adjustLightness(if (darkTheme) 0.25f else -0.25f)
    val secondary = accent.adjustLightness(if (darkTheme) 0.15f else -0.15f)
    val secondaryContainer = accent.adjustLightness(if (darkTheme) 0.35f else -0.35f)
    val tertiary = accent.adjustLightness(if (darkTheme) -0.1f else 0.1f)
    val tertiaryContainer = accent.adjustLightness(if (darkTheme) 0.4f else -0.4f)
    return colorScheme.copy(
        primary = accent,
        onPrimary = accent.contrastingContent(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = primaryContainer.contrastingContent(),
        secondary = secondary,
        onSecondary = secondary.contrastingContent(),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = secondaryContainer.contrastingContent(),
        tertiary = tertiary,
        onTertiary = tertiary.contrastingContent(),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = tertiaryContainer.contrastingContent(),
        surfaceTint = accent,
        inversePrimary = accent.adjustLightness(if (darkTheme) -0.4f else 0.4f)
    )
}

private fun applyCustomThemeColor(
    colorScheme: ColorScheme,
    themeColor: Color,
    darkTheme: Boolean
): ColorScheme {
    // Morphe
    // For dark theme, use the selected color directly without excessive darkening
    // For light theme, lighten the color
    val background = if (darkTheme) {
        themeColor.adjustLightness(0.05f)
    } else {
        themeColor.adjustLightness(0.55f)
    }

    val surface = if (darkTheme) {
        themeColor.adjustLightness(0.15f)
    } else {
        themeColor.adjustLightness(0.45f)
    }

    val surfaceVariant = if (darkTheme) {
        themeColor.adjustLightness(0.25f)
    } else {
        themeColor.adjustLightness(0.35f)
    }

    val containerLowest = if (darkTheme) {
        themeColor.adjustLightness(0.0f)
    } else {
        themeColor.adjustLightness(0.5f)
    }

    val containerLow = if (darkTheme) {
        themeColor.adjustLightness(0.08f)
    } else {
        themeColor.adjustLightness(0.48f)
    }

    val container = if (darkTheme) {
        themeColor.adjustLightness(0.18f)
    } else {
        themeColor.adjustLightness(0.42f)
    }

    val containerHigh = if (darkTheme) {
        themeColor.adjustLightness(0.26f)
    } else {
        themeColor.adjustLightness(0.34f)
    }

    val containerHighest = if (darkTheme) {
        themeColor.adjustLightness(0.32f)
    } else {
        themeColor.adjustLightness(0.28f)
    }

    val surfaceBright = if (darkTheme) {
        themeColor.adjustLightness(0.4f)
    } else {
        themeColor.adjustLightness(0.12f)
    }

    val surfaceDim = if (darkTheme) {
        themeColor.adjustLightness(-0.1f)
    } else {
        themeColor.adjustLightness(0.6f)
    }

    val onBackground = background.contrastingContent()
    val onSurface = surface.contrastingContent()
    val onSurfaceVariant = surfaceVariant.contrastingContent()

    return colorScheme.copy(
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = themeColor,
        surfaceContainerLowest = containerLowest,
        surfaceContainerLow = containerLow,
        surfaceContainer = container,
        surfaceContainerHigh = containerHigh,
        surfaceContainerHighest = containerHighest,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim
    )
}

private fun Color.adjustLightness(delta: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}
