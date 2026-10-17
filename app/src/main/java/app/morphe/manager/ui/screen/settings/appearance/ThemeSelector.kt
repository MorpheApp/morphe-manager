/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.SectionCard
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle

/**
 * Theme mode and color style, each a single row of compact tiles in one card, since both are a
 * pick of at most three.
 */
@Composable
fun ThemeSelector(
    theme: Theme,
    onThemeSelected: (Theme) -> Unit,
    style: ThemeStyle,
    supportsDynamicColor: Boolean,
    onStyleSelected: (ThemeStyle) -> Unit
) {
    val themeOptions = listOf(
        ThemeOption(
            Theme.SYSTEM,
            Icons.Outlined.PhoneAndroid,
            stringResource(R.string.settings_appearance_system)
        ),
        ThemeOption(
            Theme.LIGHT,
            Icons.Outlined.LightMode,
            stringResource(R.string.settings_appearance_light)
        ),
        ThemeOption(
            Theme.DARK,
            Icons.Outlined.DarkMode,
            stringResource(R.string.settings_appearance_dark)
        )
    )
    val styleOptions = buildList {
        add(
            ThemeOption(
                ThemeStyle.MORPHE,
                Icons.Outlined.Palette,
                stringResource(R.string.settings_appearance_style_morphe)
            )
        )
        if (supportsDynamicColor) {
            add(
                ThemeOption(
                    ThemeStyle.MATERIAL_YOU,
                    Icons.Outlined.AutoAwesome,
                    stringResource(R.string.settings_appearance_dynamic)
                )
            )
        }
        add(
            ThemeOption(
                ThemeStyle.MONOCHROME,
                Icons.Outlined.Contrast,
                stringResource(R.string.settings_appearance_monochrome)
            )
        )
    }

    SectionCard {
        Column(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            ThemeOptionRow(
                title = stringResource(R.string.settings_appearance_theme_mode),
                options = themeOptions,
                selected = theme,
                onSelected = onThemeSelected
            )
            ThemeOptionRow(
                title = stringResource(R.string.settings_appearance_color_style),
                options = styleOptions,
                selected = style,
                onSelected = onStyleSelected
            )
        }
    }
}

private data class ThemeOption<T>(val value: T, val icon: ImageVector, val label: String)

@Composable
private fun <T> ThemeOptionRow(
    title: String,
    options: List<ThemeOption<T>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        OptionGroup(items = options) { option, shape, interactionSource, itemModifier ->
            ModernIconOptionCard(
                selected = option.value == selected,
                onClick = { onSelected(option.value) },
                icon = option.icon,
                label = option.label,
                modifier = itemModifier,
                compact = true,
                shape = shape,
                interactionSource = interactionSource
            )
        }
    }
}
