package app.morphe.manager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val LocalMonochromeTheme = staticCompositionLocalOf { false }

internal fun monochromeColorScheme(base: ColorScheme, darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        base.copy(
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
        base.copy(
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

object MonochromeThemeDefaults {
    @Composable
    fun accentColor(base: Color): Color =
        if (LocalMonochromeTheme.current) MaterialTheme.colorScheme.primary else base

    @Composable
    fun surfaceColor(base: Color, selected: Boolean = false): Color {
        if (!LocalMonochromeTheme.current) return base

        val colors = MaterialTheme.colorScheme
        return if (selected) colors.primaryContainer else colors.surfaceContainerHigh
    }

    @Composable
    fun borderColor(selected: Boolean = false): Color {
        val colors = MaterialTheme.colorScheme
        if (!LocalMonochromeTheme.current) {
            return if (selected) colors.primary else colors.outline
        }

        val base = if (selected) colors.primary else colors.outlineVariant
        val alpha = if (colors.background.luminance() < 0.5f) 0.42f else 0.34f
        return base.copy(alpha = alpha)
    }
}
