package app.revanced.manager.ui.component.morphe.settings

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.BackgroundType
import app.revanced.manager.ui.component.morphe.shared.IconTextRow
import app.revanced.manager.ui.component.morphe.shared.MorpheClickableCard
import app.revanced.manager.ui.component.morphe.utils.darken
import app.revanced.manager.ui.screen.settings.THEME_PRESET_COLORS
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.ui.viewmodel.GeneralSettingsViewModel
import app.revanced.manager.ui.viewmodel.ThemePreset
import app.revanced.manager.util.toColorOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Appearance settings section
 * Contains theme selection, dark mode options, background type, and color customization
 */
@Composable
fun AppearanceSection(
    theme: Theme,
    pureBlackTheme: Boolean,
    dynamicColor: Boolean,
    customAccentColorHex: String?,
    backgroundType: BackgroundType,
    onBackToAdvanced: () -> Unit,
    viewModel: GeneralSettingsViewModel
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Interface switcher
            MorpheClickableCard(
                onClick = onBackToAdvanced,
                cornerRadius = 12.dp,
                alpha = 0.33f
            ) {
                IconTextRow(
                    icon = Icons.Outlined.SwapHoriz,
                    title = stringResource(R.string.morphe_settings_return_to_advanced),
                    description = stringResource(R.string.morphe_settings_return_to_advanced_description),
                    modifier = Modifier.padding(12.dp),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable appearance options
            ExpandableSection(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.morphe_appearance_options),
                description = stringResource(R.string.morphe_appearance_options_description),
                expanded = expanded,
                onExpandChange = { expanded = it }
            ) {
                AppearanceContent(
                    theme = theme,
                    pureBlackTheme = pureBlackTheme,
                    dynamicColor = dynamicColor,
                    customAccentColorHex = customAccentColorHex,
                    backgroundType = backgroundType,
                    viewModel = viewModel,
                    scope = scope
                )
            }
        }
    }
}

/**
 * Appearance content
 */
@Composable
private fun AppearanceContent(
    theme: Theme,
    pureBlackTheme: Boolean,
    dynamicColor: Boolean,
    customAccentColorHex: String?,
    backgroundType: BackgroundType,
    viewModel: GeneralSettingsViewModel,
    scope: CoroutineScope
) {
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Background Type Selection
        SelectorSection(
            title = stringResource(R.string.morphe_background_type),
            items = BackgroundType.entries.map { bgType ->
                SelectorItem(
                    key = bgType.name,
                    icon = when (bgType) {
                        BackgroundType.CIRCLES -> Icons.Outlined.Circle
                        BackgroundType.RINGS -> Icons.Outlined.RadioButtonUnchecked
                        BackgroundType.WAVES -> Icons.Outlined.Waves
                        BackgroundType.SPACE -> Icons.Outlined.AutoAwesome
                        BackgroundType.SHAPES -> Icons.Outlined.Pentagon
                        BackgroundType.SNOW -> Icons.Outlined.AcUnit
                        BackgroundType.NONE -> Icons.Outlined.VisibilityOff
                    },
                    label = stringResource(bgType.displayNameResId)
                )
            },
            selectedItem = backgroundType.name,
            onItemSelected = { selectedType ->
                scope.launch {
                    viewModel.prefs.backgroundType.update(BackgroundType.valueOf(selectedType))
                }
            },
            columns = null // Horizontal scroll
        )

        // Theme Selection
        SelectorSection(
            title = stringResource(R.string.theme),
            items = buildList {
                add(
                    SelectorItem(
                        key = "SYSTEM",
                        icon = Icons.Outlined.PhoneAndroid,
                        label = stringResource(R.string.system)
                    )
                )
                add(
                    SelectorItem(
                        key = "LIGHT",
                        icon = Icons.Outlined.LightMode,
                        label = stringResource(R.string.light)
                    )
                )
                add(
                    SelectorItem(
                        key = "DARK",
                        icon = Icons.Outlined.DarkMode,
                        label = stringResource(R.string.dark)
                    )
                )
                add(
                    SelectorItem(
                        key = "BLACK",
                        icon = Icons.Outlined.Contrast,
                        label = stringResource(R.string.black)
                    )
                )
                // Add Material You option for Android 12+
                if (supportsDynamicColor) {
                    add(
                        SelectorItem(
                            key = "DYNAMIC",
                            icon = Icons.Outlined.AutoAwesome,
                            label = stringResource(R.string.theme_preset_dynamic)
                        )
                    )
                }
            },
            selectedItem = when {
                dynamicColor && supportsDynamicColor -> "DYNAMIC"
                pureBlackTheme -> "BLACK"
                theme == Theme.SYSTEM -> "SYSTEM"
                theme == Theme.LIGHT -> "LIGHT"
                theme == Theme.DARK -> "DARK"
                else -> "SYSTEM"
            },
            onItemSelected = { selectedTheme ->
                scope.launch {
                    when (selectedTheme) {
                        "SYSTEM" -> viewModel.applyThemePreset(ThemePreset.DEFAULT)
                        "LIGHT" -> viewModel.applyThemePreset(ThemePreset.LIGHT)
                        "DARK" -> viewModel.applyThemePreset(ThemePreset.DARK)
                        "BLACK" -> viewModel.applyThemePreset(ThemePreset.PURE_BLACK)
                        "DYNAMIC" -> viewModel.applyThemePreset(ThemePreset.DYNAMIC)
                    }
                }
            },
            columns = null // Horizontal scroll
        )

        // Accent Color Presets
        Text(
            text = stringResource(R.string.accent_color_presets),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        AccentColorPresetsRow(
            selectedColorHex = customAccentColorHex,
            onColorSelected = { color -> viewModel.setCustomAccentColor(color) },
            dynamicColorEnabled = dynamicColor
        )
    }
}

/**
 * Row of accent color preset buttons
 */
@Composable
private fun AccentColorPresetsRow(
    selectedColorHex: String?,
    onColorSelected: (Color?) -> Unit,
    dynamicColorEnabled: Boolean
) {
    val selectedArgb = selectedColorHex.toColorOrNull()?.toArgb()
    val isEnabled = !dynamicColorEnabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Reset button (no color selected)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (selectedArgb == null) 2.dp else 1.dp,
                    color = if (selectedArgb == null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .clickable(enabled = isEnabled) {
                    if (isEnabled) {
                        onColorSelected(null)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Reset",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (isEnabled) 1f else 0.5f
                )
            )
        }

        // Color presets
        THEME_PRESET_COLORS.forEach { preset ->
            val isSelected = selectedArgb != null && preset.toArgb() == selectedArgb
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected)
                            preset.darken(0.4f) // Darker version of the color
                        else
                            MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .background(
                        preset.copy(alpha = if (isEnabled) 1f else 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = isEnabled) {
                        if (isEnabled) {
                            onColorSelected(preset)
                        }
                    }
            )
        }
    }
}
