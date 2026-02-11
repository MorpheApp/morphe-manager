package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.SectionCard
import app.morphe.manager.ui.screen.shared.WindowWidthSizeClass
import app.morphe.manager.ui.screen.shared.rememberWindowSize
import app.morphe.manager.ui.theme.Theme

/**
 * Theme mode selector with adaptive grid
 */
@Composable
fun ThemeSelector(
    theme: Theme,
    dynamicColor: Boolean,
    supportsDynamicColor: Boolean,
    onThemeSelected: (String) -> Unit
) {
    val windowSize = rememberWindowSize()
    val columns = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 3
        WindowWidthSizeClass.Medium -> 4
        WindowWidthSizeClass.Expanded -> 5
    }

    // Determine current theme selection
    val currentTheme = when {
        dynamicColor && supportsDynamicColor -> "DYNAMIC"
        theme == Theme.SYSTEM -> "SYSTEM"
        theme == Theme.LIGHT -> "LIGHT"
        theme == Theme.DARK -> "DARK"
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
        if (supportsDynamicColor) {
            add(
                Triple(
                    "DYNAMIC",
                    Icons.Outlined.AutoAwesome,
                    stringResource(R.string.settings_appearance_dynamic)
                )
            )
        }
    }

    SectionCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
