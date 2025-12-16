package app.revanced.manager.ui.component.morphe.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.BackgroundType
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.ui.viewmodel.GeneralSettingsViewModel
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
    customThemeColorHex: String?,
    backgroundType: String,
    onBackToAdvanced: () -> Unit,
    viewModel: GeneralSettingsViewModel
) {
    val scope = rememberCoroutineScope()

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Interface switcher
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBackToAdvanced),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.morphe_settings_return_to_advanced),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.morphe_settings_return_to_advanced_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Background Type Selection
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.morphe_background_type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackgroundType.entries.forEach { bgType ->
                    val isSelected = backgroundType == bgType.name
                    ThemeOption(
                        icon = when (bgType) {
                            BackgroundType.CIRCLES -> Icons.Outlined.Circle
                            BackgroundType.RINGS -> Icons.Outlined.RadioButtonUnchecked
                            BackgroundType.WAVES -> Icons.Outlined.WaterDrop
                            BackgroundType.PARTICLES -> Icons.Outlined.ScatterPlot
                            BackgroundType.MORPHE -> R.drawable.morphe_settings_icon_bold
                            BackgroundType.NONE -> Icons.Outlined.VisibilityOff
                        },
                        label = stringResource(bgType.displayNameResId),
                        selected = isSelected,
                        onClick = {
                            scope.launch {
                                viewModel.prefs.backgroundType.update(bgType.name)
                            }
                        },
                        modifier = Modifier.width(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            // Theme options row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // System option
                ThemeOption(
                    icon = Icons.Outlined.PhoneAndroid,
                    label = stringResource(R.string.system),
                    selected = theme == Theme.SYSTEM && !pureBlackTheme,
                    onClick = {
                        viewModel.setTheme(Theme.SYSTEM)
                        scope.launch {
                            viewModel.prefs.pureBlackTheme.update(false)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                // Light option
                ThemeOption(
                    icon = Icons.Outlined.LightMode,
                    label = stringResource(R.string.light),
                    selected = theme == Theme.LIGHT,
                    onClick = {
                        viewModel.setTheme(Theme.LIGHT)
                        scope.launch {
                            viewModel.prefs.pureBlackTheme.update(false)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                // Dark option
                ThemeOption(
                    icon = Icons.Outlined.DarkMode,
                    label = stringResource(R.string.dark),
                    selected = theme == Theme.DARK && !pureBlackTheme,
                    onClick = {
                        viewModel.setTheme(Theme.DARK)
                        scope.launch {
                            viewModel.prefs.pureBlackTheme.update(false)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                // Black option
                ThemeOption(
                    icon = Icons.Outlined.Contrast,
                    label = stringResource(R.string.black),
                    selected = pureBlackTheme,
                    onClick = {
                        scope.launch {
                            viewModel.prefs.pureBlackTheme.update(true)
                            viewModel.prefs.dynamicColor.update(false)
                            // Reset custom theme color when Black is selected
                            viewModel.setCustomThemeColor(null)
                            // Ensure dark theme is selected
                            if (theme == Theme.LIGHT) {
                                viewModel.setTheme(Theme.DARK)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Dynamic Color toggle (Android 12+) - only show when Black is not selected
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AnimatedVisibility(visible = !pureBlackTheme) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        val newValue = !dynamicColor
                                        viewModel.prefs.dynamicColor.update(newValue)
                                        // Reset custom theme color when dynamic color is enabled
                                        if (newValue) {
                                            viewModel.setCustomThemeColor(null)
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.dynamic_color),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.dynamic_color_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = dynamicColor,
                                    onCheckedChange = {
                                        scope.launch {
                                            viewModel.prefs.dynamicColor.update(it)
                                            // Reset custom theme color when dynamic color is enabled
                                            if (it) {
                                                viewModel.setCustomThemeColor(null)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Accent Color Presets
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.accent_color_presets),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ColorPresetsRow(
                selectedColorHex = customAccentColorHex,
                onColorSelected = { color -> viewModel.setCustomAccentColor(color) },
                isAccent = true,
                viewModel = viewModel,
                scope = scope
            )

            // Theme Color Presets
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.theme_color),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ColorPresetsRow(
                selectedColorHex = customThemeColorHex,
                onColorSelected = { color -> viewModel.setCustomThemeColor(color) },
                isAccent = false,
                viewModel = viewModel,
                scope = scope
            )
        }
    }
}

/**
 * Row of color preset buttons
 * Shows reset button and color swatches
 */
@Composable
private fun ColorPresetsRow(
    selectedColorHex: String?,
    onColorSelected: (Color?) -> Unit,
    isAccent: Boolean,
    viewModel: GeneralSettingsViewModel,
    scope: CoroutineScope
) {
    val presets = remember {
        if (isAccent) {
            // Accent color presets
            listOf(
                Color(0xFF6750A4),
                Color(0xFF386641),
                Color(0xFF0061A4),
                Color(0xFF8E24AA),
                Color(0xFFEF6C00),
                Color(0xFF00897B),
                Color(0xFFD81B60),
                Color(0xFF5C6BC0),
                Color(0xFF43A047),
                Color(0xFFFF7043),
                Color(0xFF1DE9B6),
                Color(0xFFFFC400),
                Color(0xFF00B8D4),
                Color(0xFFBA68C8)
            )
        } else {
            // Theme color presets (dark backgrounds)
            listOf(
                Color(0xFF1C1B1F),
                Color(0xFF2D2A32),
                Color(0xFF1A1A2E),
                Color(0xFF0F0F1E),
                Color(0xFF16213E),
                Color(0xFF1F1B24),
                Color(0xFF0A1929),
                Color(0xFF1B1B2F),
                Color(0xFF162447),
                Color(0xFF1F1D2B),
                Color(0xFF2C2C54),
                Color(0xFF1E1E2E)
            )
        }
    }

    val selectedArgb = selectedColorHex.toColorOrNull()?.toArgb()

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
                .clickable { onColorSelected(null) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Reset",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Color presets
        presets.forEach { preset ->
            val isSelected = selectedArgb != null && preset.toArgb() == selectedArgb
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .background(preset, RoundedCornerShape(12.dp))
                    .clickable {
                        onColorSelected(preset)
                        // If this is theme color (not accent), reset Dynamic Color and Pure Black
                        if (!isAccent) {
                            scope.launch {
                                viewModel.prefs.dynamicColor.update(false)
                                viewModel.prefs.pureBlackTheme.update(false)
                            }
                        }
                    }
            )
        }
    }
}
