package app.morphe.manager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import app.morphe.manager.R
import app.morphe.manager.util.toColorOrNull
import kotlinx.serialization.Serializable

private val DarkColorScheme = darkColorScheme(
    primary = rv_theme_dark_primary,
    onPrimary = rv_theme_dark_onPrimary,
    primaryContainer = rv_theme_dark_primaryContainer,
    onPrimaryContainer = rv_theme_dark_onPrimaryContainer,
    secondary = rv_theme_dark_secondary,
    onSecondary = rv_theme_dark_onSecondary,
    secondaryContainer = rv_theme_dark_secondaryContainer,
    onSecondaryContainer = rv_theme_dark_onSecondaryContainer,
    tertiary = rv_theme_dark_tertiary,
    onTertiary = rv_theme_dark_onTertiary,
    tertiaryContainer = rv_theme_dark_tertiaryContainer,
    onTertiaryContainer = rv_theme_dark_onTertiaryContainer,
    error = rv_theme_dark_error,
    errorContainer = rv_theme_dark_errorContainer,
    onError = rv_theme_dark_onError,
    onErrorContainer = rv_theme_dark_onErrorContainer,
    background = rv_theme_dark_background,
    onBackground = rv_theme_dark_onBackground,
    surface = rv_theme_dark_surface,
    onSurface = rv_theme_dark_onSurface,
    surfaceVariant = rv_theme_dark_surfaceVariant,
    onSurfaceVariant = rv_theme_dark_onSurfaceVariant,
    outline = rv_theme_dark_outline,
    inverseOnSurface = rv_theme_dark_inverseOnSurface,
    inverseSurface = rv_theme_dark_inverseSurface,
    inversePrimary = rv_theme_dark_inversePrimary,
    surfaceTint = rv_theme_dark_surfaceTint,
    outlineVariant = rv_theme_dark_outlineVariant,
    scrim = rv_theme_dark_scrim,
)

private val LightColorScheme = lightColorScheme(
    primary = rv_theme_light_primary,
    onPrimary = rv_theme_light_onPrimary,
    primaryContainer = rv_theme_light_primaryContainer,
    onPrimaryContainer = rv_theme_light_onPrimaryContainer,
    secondary = rv_theme_light_secondary,
    onSecondary = rv_theme_light_onSecondary,
    secondaryContainer = rv_theme_light_secondaryContainer,
    onSecondaryContainer = rv_theme_light_onSecondaryContainer,
    tertiary = rv_theme_light_tertiary,
    onTertiary = rv_theme_light_onTertiary,
    tertiaryContainer = rv_theme_light_tertiaryContainer,
    onTertiaryContainer = rv_theme_light_onTertiaryContainer,
    error = rv_theme_light_error,
    errorContainer = rv_theme_light_errorContainer,
    onError = rv_theme_light_onError,
    onErrorContainer = rv_theme_light_onErrorContainer,
    background = rv_theme_light_background,
    onBackground = rv_theme_light_onBackground,
    surface = rv_theme_light_surface,
    onSurface = rv_theme_light_onSurface,
    surfaceVariant = rv_theme_light_surfaceVariant,
    onSurfaceVariant = rv_theme_light_onSurfaceVariant,
    outline = rv_theme_light_outline,
    inverseOnSurface = rv_theme_light_inverseOnSurface,
    inverseSurface = rv_theme_light_inverseSurface,
    inversePrimary = rv_theme_light_inversePrimary,
    surfaceTint = rv_theme_light_surfaceTint,
    outlineVariant = rv_theme_light_outlineVariant,
    scrim = rv_theme_light_scrim,
)

@Composable
fun ManagerTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    pureBlackTheme: Boolean,
    accentColorHex: String? = null,
    themeColorHex: String? = null,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
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

    val finalScheme = themeColorHex.toColorOrNull()?.let {
        applyCustomThemeColor(schemeWithAccent, it, darkTheme)
    } ?: schemeWithAccent

    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val activity = view.context as Activity

            WindowCompat.setDecorFitsSystemWindows(activity.window, false)

            activity.window.statusBarColor = Color.Transparent.toArgb()
            activity.window.navigationBarColor = Color.Transparent.toArgb()

            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = Typography,
        content = content
    )
}

@Serializable
enum class Theme(val displayName: Int) {
    SYSTEM(R.string.settings_appearance_system),
    LIGHT(R.string.settings_appearance_light),
    DARK(R.string.settings_appearance_dark);
}

private fun applyCustomAccent(
    colorScheme: ColorScheme,
    accent: Color,
    darkTheme: Boolean
): ColorScheme {
    val primary = accent
    val primaryContainer = accent.adjustLightness(if (darkTheme) 0.25f else -0.25f)
    val secondary = accent.adjustLightness(if (darkTheme) 0.15f else -0.15f)
    val secondaryContainer = accent.adjustLightness(if (darkTheme) 0.35f else -0.35f)
    val tertiary = accent.adjustLightness(if (darkTheme) -0.1f else 0.1f)
    val tertiaryContainer = accent.adjustLightness(if (darkTheme) 0.4f else -0.4f)
    return colorScheme.copy(
        primary = primary,
        onPrimary = primary.contrastingForeground(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = primaryContainer.contrastingForeground(),
        secondary = secondary,
        onSecondary = secondary.contrastingForeground(),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = secondaryContainer.contrastingForeground(),
        tertiary = tertiary,
        onTertiary = tertiary.contrastingForeground(),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = tertiaryContainer.contrastingForeground(),
        surfaceTint = primary,
        inversePrimary = primary.adjustLightness(if (darkTheme) -0.4f else 0.4f)
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

    val onBackground = background.contrastingForeground()
    val onSurface = surface.contrastingForeground()
    val onSurfaceVariant = surfaceVariant.contrastingForeground()

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

private fun Color.contrastingForeground(): Color {
    val luminance = ColorUtils.calculateLuminance(this.toArgb())
    return if (luminance > 0.5) Color.Black else Color.White
}
