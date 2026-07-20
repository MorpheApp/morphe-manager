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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.SectionCard
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle

/**
 * Theme mode selector with adaptive grid.
 */
@Composable
fun ThemeSelector(
    theme: Theme,
    onThemeSelected: (String) -> Unit
) {
    val columns = 3

    // Determine current theme selection
    val currentTheme = when (theme) {
        Theme.LIGHT -> "LIGHT"
        Theme.DARK -> "DARK"
        else -> "SYSTEM"
    }

    // Build theme options
    val themeOptions = buildList {
        add(
            Triple(
                "SYSTEM",
                Icons.Outlined.PhoneAndroid,
                stringResource(R.string.settings_appearance_system)
            )
        )
        add(
            Triple(
                "LIGHT",
                Icons.Outlined.LightMode,
                stringResource(R.string.settings_appearance_light)
            )
        )
        add(
            Triple(
                "DARK",
                Icons.Outlined.DarkMode,
                stringResource(R.string.settings_appearance_dark)
            )
        )
    }

    SectionCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_theme_mode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            themeOptions.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (key, icon, label) ->
                        ModernIconOptionCard(
                            selected = currentTheme == key,
                            onClick = { onThemeSelected(key) },
                            icon = icon,
                            label = label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeStyleSelector(
    style: ThemeStyle,
    supportsDynamicColor: Boolean,
    onStyleSelected: (ThemeStyle) -> Unit
) {
    val styleOptions = buildList {
        add(
            Triple(
                ThemeStyle.MORPHE,
                Icons.Outlined.Palette,
                stringResource(R.string.settings_appearance_style_morphe)
            )
        )
        if (supportsDynamicColor) {
            add(
                Triple(
                    ThemeStyle.MATERIAL_YOU,
                    Icons.Outlined.AutoAwesome,
                    stringResource(R.string.settings_appearance_dynamic)
                )
            )
        }
        add(
            Triple(
                ThemeStyle.MONOCHROME,
                Icons.Outlined.Contrast,
                stringResource(R.string.settings_appearance_monochrome)
            )
        )
    }
    val columns = styleOptions.size.coerceAtMost(3)

    SectionCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_color_style),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            styleOptions.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (themeStyle, icon, label) ->
                        ModernIconOptionCard(
                            selected = style == themeStyle,
                            onClick = { onStyleSelected(themeStyle) },
                            icon = icon,
                            label = label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
