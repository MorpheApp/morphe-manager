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
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.ui.viewmodel.GeneralSettingsViewModel
import app.revanced.manager.util.toColorOrNull
import kotlinx.coroutines.launch

/**
 * Appearance settings section
 * Contains theme selection, dark mode options, and color customization
 */
@Composable
fun AppearanceSection(
    theme: Theme,
    pureBlackTheme: Boolean,
    dynamicColor: Boolean,
    customAccentColorHex: String?,
    customThemeColorHex: String?,
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

            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            // Theme options row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeOption(
                    icon = Icons.Outlined.PhoneAndroid,
                    label = stringResource(R.string.system),
                    selected = theme == Theme.SYSTEM,
                    onClick = { viewModel.setTheme(Theme.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = Icons.Outlined.LightMode,
                    label = stringResource(R.string.light),
                    selected = theme == Theme.LIGHT,
                    onClick = { viewModel.setTheme(Theme.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    icon = Icons.Outlined.DarkMode,
                    label = stringResource(R.string.dark),
                    selected = theme == Theme.DARK,
                    onClick = { viewModel.setTheme(Theme.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Pure black theme option (only for dark themes)
            AnimatedVisibility(visible = theme != Theme.LIGHT) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    viewModel.prefs.pureBlackTheme.update(!pureBlackTheme)
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
                                imageVector = Icons.Outlined.Contrast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.pure_black_theme),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.pure_black_theme_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = pureBlackTheme,
                                onCheckedChange = {
                                    scope.launch {
                                        viewModel.prefs.pureBlackTheme.update(it)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Dynamic Color (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                viewModel.prefs.dynamicColor.update(!dynamicColor)
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
                                }
                            }
                        )
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
                isAccent = true
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
                isAccent = false
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
    isAccent: Boolean
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
                    .clickable { onColorSelected(preset) }
            )
        }
    }
}
