package app.revanced.manager.ui.screen.settings

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.GroupHeader
import app.revanced.manager.ui.component.settings.SettingsListItem
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.ui.viewmodel.GeneralSettingsViewModel
import app.revanced.manager.ui.viewmodel.ThemePreset
import app.revanced.manager.util.toColorOrNull
import app.revanced.manager.util.toHexString
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: GeneralSettingsViewModel = koinViewModel()
) {
    val prefs = viewModel.prefs
    var showAccentPicker by rememberSaveable { mutableStateOf(false) }
    var showThemeColorPicker by rememberSaveable { mutableStateOf(false) }

    val customAccentColorHex by prefs.customAccentColor.getAsState()
    val customThemeColorHex by prefs.customThemeColor.getAsState()
    val theme by prefs.theme.getAsState()
    val appLanguage by prefs.appLanguage.getAsState()
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    // PR #30: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/30
    val allowPureBlackPreset = theme == Theme.DARK || (theme == Theme.SYSTEM && isSystemInDarkTheme())
    val dynamicColorEnabled by prefs.dynamicColor.getAsState()
    val pureBlackThemeEnabled by prefs.pureBlackTheme.getAsState()
    val themePresetSelectionEnabled by prefs.themePresetSelectionEnabled.getAsState()
    val selectedThemePresetName by prefs.themePresetSelectionName.getAsState()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val selectedThemePreset = remember(selectedThemePresetName, themePresetSelectionEnabled, supportsDynamicColor) {
        if (!themePresetSelectionEnabled) null else selectedThemePresetName.takeIf { it.isNotBlank() }?.let {
            val preset = runCatching { ThemePreset.valueOf(it) }.getOrNull()
            if (!supportsDynamicColor && preset == ThemePreset.DYNAMIC) ThemePreset.DEFAULT else preset
        }
    }
    val canAdjustThemeColor = selectedThemePreset == null
    val canAdjustAccentColor = selectedThemePreset != ThemePreset.DYNAMIC
    val themeControlsAlpha = if (canAdjustThemeColor) 1f else 0.5f
    val accentControlsAlpha = if (canAdjustAccentColor) 1f else 0.5f
    // TODO: Hide this until the app is localized.
    val languageOptions = remember {
        listOf(
            LanguageOption("en", R.string.language_option_english)
        )
    }

    if (!canAdjustThemeColor && showThemeColorPicker) showThemeColorPicker = false
    if (!canAdjustAccentColor && showAccentPicker) showAccentPicker = false
    if (showThemeColorPicker) {
        val currentThemeColor = customThemeColorHex.toColorOrNull()
        ColorPickerDialog(
            titleRes = R.string.theme_color_picker_title,
            previewLabelRes = R.string.theme_color_preview,
            resetLabelRes = R.string.theme_color_reset,
            initialColor = currentThemeColor ?: MaterialTheme.colorScheme.surface,
            allowReset = currentThemeColor != null,
            onReset = { viewModel.setCustomThemeColor(null) },
            onConfirm = { color -> viewModel.setCustomThemeColor(color) },
            onDismiss = { showThemeColorPicker = false }
        )
    }
    if (showAccentPicker) {
        val currentAccent = customAccentColorHex.toColorOrNull()
        ColorPickerDialog(
            titleRes = R.string.accent_color_picker_title,
            previewLabelRes = R.string.accent_color_preview,
            resetLabelRes = R.string.accent_color_reset,
            initialColor = currentAccent ?: MaterialTheme.colorScheme.primary,
            allowReset = currentAccent != null,
            onReset = { viewModel.setCustomAccentColor(null) },
            onConfirm = { color -> viewModel.setCustomAccentColor(color) },
            onDismiss = { showAccentPicker = false }
        )
    }
    val context = LocalContext.current
    if (showLanguageDialog) {
        LanguageDialog(
            options = languageOptions,
            selectedCode = appLanguage,
            onSelect = {
                viewModel.setAppLanguage(it)
                // Force activity recreation so every screen picks up the new locale immediately.
                (context as? android.app.Activity)?.recreate()
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.general),
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        ColumnWithScrollbar(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GroupHeader(stringResource(R.string.appearance))

            val selectedLanguageLabel = languageOptions.firstOrNull { it.code == appLanguage }?.labelRes
                ?: R.string.language_option_english

            Text(
                text = stringResource(R.string.theme_presets),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = stringResource(R.string.theme_presets_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            val baseThemeSwatches = remember(supportsDynamicColor) {
                buildList {
                    add(ThemePresetSwatch(ThemePreset.DEFAULT, R.string.theme_preset_default, listOf(Color(0xFF4CD964), Color(0xFF4A90E2))))
                    add(ThemePresetSwatch(ThemePreset.LIGHT, R.string.light, listOf(Color(0xFFEEF2FF), Color(0xFFE2E6FB))))
                    add(ThemePresetSwatch(ThemePreset.DARK, R.string.dark, listOf(Color(0xFF1C1B1F), Color(0xFF2A2830))))
                    if (supportsDynamicColor) {
                        add(ThemePresetSwatch(ThemePreset.DYNAMIC, R.string.theme_preset_dynamic, listOf(Color(0xFF6750A4), Color(0xFF4285F4))))
                    }
                    add(ThemePresetSwatch(ThemePreset.PURE_BLACK, R.string.theme_preset_amoled, listOf(Color(0xFF000000), Color(0xFF1C1B1F))))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                baseThemeSwatches.forEach { option ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ThemeSwatchChip(
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(option.labelRes),
                            colors = option.colors,
                            isSelected = selectedThemePreset == option.preset,
                            enabled = option.preset != ThemePreset.PURE_BLACK || allowPureBlackPreset,
                            onClick = { viewModel.toggleThemePreset(option.preset) }
                        )
                    }
                }
            }


            SettingsListItem(
                modifier = Modifier
                    .alpha(themeControlsAlpha)
                    .clickable(enabled = canAdjustThemeColor) { showThemeColorPicker = true },
                headlineContent = stringResource(R.string.theme_color),
                supportingContent = stringResource(R.string.theme_color_description),
                trailingContent = {
                    val previewColor = customThemeColorHex.toColorOrNull() ?: MaterialTheme.colorScheme.surface
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(previewColor, RoundedCornerShape(12.dp))
                    )
                }
            )

            SettingsListItem(
                modifier = Modifier
                    .alpha(accentControlsAlpha)
                    .clickable(enabled = canAdjustAccentColor) { showAccentPicker = true },
                headlineContent = stringResource(R.string.accent_color),
                supportingContent = stringResource(R.string.accent_color_description),
                trailingContent = {
                    val previewColor = customAccentColorHex.toColorOrNull() ?: MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(previewColor, RoundedCornerShape(12.dp))
                    )
                }
            )
            val accentPresets = remember {
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
            }
            val selectedAccentArgb = customAccentColorHex.toColorOrNull()?.toArgb()
            Text(
                text = stringResource(R.string.accent_color_presets),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(accentControlsAlpha)
            )
            Text(
                text = stringResource(R.string.accent_color_presets_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .alpha(accentControlsAlpha)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .alpha(accentControlsAlpha),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                accentPresets.forEach { preset ->
                    val isSelected = selectedAccentArgb != null && preset.toArgb() == selectedAccentArgb
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .background(preset, RoundedCornerShape(12.dp))
                            .clickable(enabled = canAdjustAccentColor) {
                                viewModel.setCustomAccentColor(preset)
                            }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.theme_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.theme_preview_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            ThemePreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            FilledTonalButton(
                onClick = { viewModel.resetThemeSettings() },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(stringResource(R.string.theme_reset))
            }

            if (false) { // FIXME: App language setting is turned off until Crowdin is setup
            GroupHeader(stringResource(R.string.language_settings))
            SettingsListItem(
                modifier = Modifier.clickable { showLanguageDialog = true },
                headlineContent = stringResource(R.string.app_language),
                supportingContent = stringResource(selectedLanguageLabel)
            )
            Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private data class ThemePresetSwatch(val preset: ThemePreset, @StringRes val labelRes: Int, val colors: List<Color>)
private data class LanguageOption(val code: String, @StringRes val labelRes: Int)

@Composable
private fun ThemeSwatchChip(
    modifier: Modifier = Modifier,
    label: String,
    colors: List<Color>,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val swatchAlpha = if (enabled) 1f else 0.5f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .alpha(swatchAlpha)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp)
                )
                .background(
                    brush = when {
                        colors.size >= 2 -> Brush.linearGradient(colors.take(2))
                        else -> Brush.linearGradient(colors.ifEmpty { listOf(MaterialTheme.colorScheme.primary) })
                    }
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LanguageDialog(
    options: List<LanguageOption>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.language_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option.code) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.code == selectedCode,
                            onClick = { onSelect(option.code) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}

private fun hexToComposeColor(input: String): Color? {
    val normalized = input.trim().let { if (it.startsWith("#")) it else "#" + it }
    return runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrNull()
}

@Composable
private fun ColorPickerDialog(
    @StringRes titleRes: Int,
    @StringRes previewLabelRes: Int,
    @StringRes resetLabelRes: Int,
    initialColor: Color,
    allowReset: Boolean,
    onReset: () -> Unit,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by rememberSaveable(initialColor) { mutableStateOf((initialColor.red * 255).roundToInt()) }
    var green by rememberSaveable(initialColor) { mutableStateOf((initialColor.green * 255).roundToInt()) }
    var blue by rememberSaveable(initialColor) { mutableStateOf((initialColor.blue * 255).roundToInt()) }
    var hexInput by rememberSaveable(initialColor) { mutableStateOf(initialColor.toHexString().uppercase()) }

    fun rgbToColor(r: Int, g: Int, b: Int) = Color(
        red = r.coerceIn(0, 255) / 255f,
        green = g.coerceIn(0, 255) / 255f,
        blue = b.coerceIn(0, 255) / 255f
    )

    val previewColor = rgbToColor(red, green, blue)
    fun updateHexFromRgb(r: Int = red, g: Int = green, b: Int = blue) {
        hexInput = rgbToColor(r, g, b).toHexString().uppercase()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(previewLabelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(previewColor)
                )
                TextField(
                    value = hexInput,
                    onValueChange = { value ->
                        val input = value.trim().uppercase().let {
                            if (it.startsWith("#")) it else "#" + it
                        }
                        hexInput = input
                        hexToComposeColor(input)?.let { color ->
                            red = (color.red * 255).roundToInt()
                            green = (color.green * 255).roundToInt()
                            blue = (color.blue * 255).roundToInt()
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                )
                ColorChannelSlider(
                    label = stringResource(R.string.color_channel_red),
                    value = red,
                    trackColor = Color.Red,
                    onValueChange = {
                        red = it
                        updateHexFromRgb(it, green, blue)
                    }
                )
                ColorChannelSlider(
                    label = stringResource(R.string.color_channel_green),
                    value = green,
                    trackColor = Color.Green,
                    onValueChange = {
                        green = it
                        updateHexFromRgb(red, it, blue)
                    }
                )
                ColorChannelSlider(
                    label = stringResource(R.string.color_channel_blue),
                    value = blue,
                    trackColor = Color.Blue,
                    onValueChange = {
                        blue = it
                        updateHexFromRgb(red, green, it)
                    }
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        if (allowReset) {
                            OutlinedButton(
                                modifier = Modifier.defaultMinSize(
                                    minWidth = ButtonDefaults.MinWidth,
                                    minHeight = ButtonDefaults.MinHeight
                                ),
                                onClick = {
                                    onReset()
                                    onDismiss()
                                }
                            ) {
                                Text(
                                    text = stringResource(resetLabelRes),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        TextButton(
                            modifier = Modifier.defaultMinSize(
                                minWidth = ButtonDefaults.MinWidth,
                                minHeight = ButtonDefaults.MinHeight
                            ),
                            onClick = onDismiss
                        ) {
                            Text(
                                text = stringResource(android.R.string.cancel),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        FilledTonalButton(
                            modifier = Modifier.defaultMinSize(
                                minWidth = ButtonDefaults.MinWidth,
                                minHeight = ButtonDefaults.MinHeight
                            ),
                            onClick = {
                                onConfirm(previewColor)
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.apply),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Int,
    trackColor: Color,
    onValueChange: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value.toString(), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                activeTrackColor = trackColor,
                inactiveTrackColor = trackColor.copy(alpha = 0.3f),
                thumbColor = trackColor
            )
        )
    }
}

@Composable
private fun ThemePreview(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.theme_preview_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { }) {
                    Text(stringResource(R.string.apply))
                }
                TextButton(onClick = { }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    }
}
