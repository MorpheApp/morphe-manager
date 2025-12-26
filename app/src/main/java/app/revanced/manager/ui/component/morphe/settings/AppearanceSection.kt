package app.revanced.manager.ui.component.morphe.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import app.revanced.manager.ui.component.morphe.shared.lighten
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
    var expanded by remember { mutableStateOf(false) }

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Interface switcher
            MorpheClickableCard(
                onClick = onBackToAdvanced,
                cornerRadius = 12.dp
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
                    customThemeColorHex = customThemeColorHex,
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
    customThemeColorHex: String?,
    backgroundType: String,
    viewModel: GeneralSettingsViewModel,
    scope: CoroutineScope
) {
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
                        BackgroundType.NONE -> Icons.Outlined.VisibilityOff
                    },
                    label = stringResource(bgType.displayNameResId)
                )
            },
            selectedItem = backgroundType,
            onItemSelected = { selectedType ->
                scope.launch {
                    viewModel.prefs.backgroundType.update(selectedType)
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
            },
            selectedItem = when {
                pureBlackTheme -> "BLACK"
                theme == Theme.SYSTEM -> "SYSTEM"
                theme == Theme.LIGHT -> "LIGHT"
                theme == Theme.DARK -> "DARK"
                else -> "SYSTEM"
            },
            onItemSelected = { selectedTheme ->
                scope.launch {
                    when (selectedTheme) {
                        "SYSTEM" -> {
                            viewModel.setTheme(Theme.SYSTEM)
                            viewModel.prefs.pureBlackTheme.update(false)
                        }
                        "LIGHT" -> {
                            viewModel.setTheme(Theme.LIGHT)
                            viewModel.prefs.pureBlackTheme.update(false)
                        }
                        "DARK" -> {
                            viewModel.setTheme(Theme.DARK)
                            viewModel.prefs.pureBlackTheme.update(false)
                        }
                        "BLACK" -> {
                            viewModel.prefs.pureBlackTheme.update(true)
                            viewModel.prefs.dynamicColor.update(false)
                            viewModel.setCustomThemeColor(null)
                            if (theme == Theme.LIGHT) {
                                viewModel.setTheme(Theme.DARK)
                            }
                        }
                    }
                }
            },
            columns = null // Horizontal scroll
        )

        // Dynamic Color toggle (Android 12+) - only show when Black is not selected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AnimatedVisibility(visible = !pureBlackTheme) {
                MorpheClickableCard(
                    onClick = {
                        scope.launch {
                            val newValue = !dynamicColor
                            viewModel.prefs.dynamicColor.update(newValue)
                            if (newValue) {
                                viewModel.setCustomThemeColor(null)
                            }
                        }
                    },
                    cornerRadius = 12.dp
                ) {
                    IconTextRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.dynamic_color),
                        description = stringResource(R.string.dynamic_color_description),
                        modifier = Modifier.padding(12.dp),
                        trailingContent = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = {
                                    scope.launch {
                                        viewModel.prefs.dynamicColor.update(it)
                                        if (it) {
                                            viewModel.setCustomThemeColor(null)
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }

        // Accent Color Presets
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
                Color(0xFF7C4DFF), // Electric Purple
                Color(0xFF536DFE), // Indigo Neon
                Color(0xFF2979FF), // Hyper Blue
                Color(0xFF00B0FF), // Cyan Pop
                Color(0xFF00E5FF), // Aqua Glow
                Color(0xFF1DE9B6), // Mint Neon
                Color(0xFF00E676), // Green Flash
                Color(0xFF76FF03), // Lime Shock
                Color(0xFFFFD600), // Yellow Punch
                Color(0xFFFF9100), // Orange Blast
                Color(0xFFFF5252), // Red Pulse
                Color(0xFFFF4081), // Pink Voltage
                Color(0xFFE040FB), // Purple Pop
                Color(0xFFB388FF)  // Lavender Glow
            )
        } else {
            // Theme color presets (dark colors applied, lighter display variants shown)
            listOf(
                Color(0xFF1C1B1F), // Obsidian Black
                Color(0xFF2D2A32), // Dark Slate Purple
                Color(0xFF1A1A2E), // Midnight Indigo
                Color(0xFF0F0F1E), // Deep Void
                Color(0xFF16213E), // Night Navy
                Color(0xFF1F1B24), // Charcoal Plum
                Color(0xFF0A1929), // Abyss Blue
                Color(0xFF1B1B2F), // Shadow Indigo
                Color(0xFF162447), // Deep Ocean Blue
                Color(0xFF1F1D2B), // Dark Graphite Violet
                Color(0xFF2C2C54), // Muted Royal Indigo
                Color(0xFF1E1E2E)  // Eclipse Gray

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
            val displayColor = if (!isAccent) preset.lighten(0.2f) else preset
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
                    .background(displayColor, RoundedCornerShape(12.dp))
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
