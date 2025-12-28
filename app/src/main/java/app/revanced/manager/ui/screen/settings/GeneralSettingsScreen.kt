package app.revanced.manager.ui.screen.settings

import android.graphics.Color.parseColor
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.GroupHeader
import app.revanced.manager.ui.component.morphe.shared.darken
import app.revanced.manager.ui.component.settings.ExpressiveSettingsCard
import app.revanced.manager.ui.viewmodel.GeneralSettingsViewModel
import app.revanced.manager.ui.viewmodel.ThemePreset
import app.revanced.manager.util.toColorOrNull
import app.revanced.manager.util.toHexString
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

val THEME_PRESET_COLORS = listOf(
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

// Morphe: This is presented in the UI as "Appearance"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: GeneralSettingsViewModel = koinViewModel()
) {
    val prefs = viewModel.prefs
    // Morphe
//    var showAccentPicker by rememberSaveable { mutableStateOf(false) }
//    var showThemeColorPicker by rememberSaveable { mutableStateOf(false) }

    val customAccentColorHex by prefs.customAccentColor.getAsState()
//    val customThemeColorHex by prefs.customThemeColor.getAsState()
//    val theme by prefs.theme.getAsState()
//    val appLanguage by prefs.appLanguage.getAsState()
//    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    // Allow selecting the AMOLED preset regardless of the current theme since selecting it switches to dark mode anyway.
//    val allowPureBlackPreset = true
//    val dynamicColorEnabled by prefs.dynamicColor.getAsState()
//    val pureBlackThemeEnabled by prefs.pureBlackTheme.getAsState()
    val themePresetSelectionEnabled by prefs.themePresetSelectionEnabled.getAsState()
    val selectedThemePresetName by prefs.themePresetSelectionName.getAsState()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val selectedThemePreset = remember(selectedThemePresetName, themePresetSelectionEnabled, supportsDynamicColor) {
        if (!themePresetSelectionEnabled) null else selectedThemePresetName.takeIf { it.isNotBlank() }?.let {
            val preset = runCatching { ThemePreset.valueOf(it) }.getOrNull()
            if (!supportsDynamicColor && preset == ThemePreset.DYNAMIC) ThemePreset.DEFAULT else preset
        }
    }
//    val canAdjustThemeColor = selectedThemePreset == null
    val canAdjustAccentColor = selectedThemePreset != ThemePreset.DYNAMIC
//    val themeControlsAlpha = if (canAdjustThemeColor) 1f else 0.5f
    val accentControlsAlpha = if (canAdjustAccentColor) 1f else 0.5f

    // Morphe For now hide this
//    val languageOptions = remember {
//        listOf(
//            LanguageOption("system", R.string.language_option_system),
//            LanguageOption("en", R.string.language_option_english),
//            LanguageOption("zh-CN", R.string.language_option_chinese_simplified),
//            // From PR #38: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/38
//            LanguageOption("vi", R.string.language_option_vietnamese),
//            // From PR #42: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/42
//            LanguageOption("ko", R.string.language_option_korean),
//            LanguageOption("ja", R.string.language_option_japanese),
//            LanguageOption("ru", R.string.language_option_russian),
//            LanguageOption("uk", R.string.language_option_ukrainian)
//        )
//    }
//
//    if (!canAdjustThemeColor && showThemeColorPicker) showThemeColorPicker = false
//    if (!canAdjustAccentColor && showAccentPicker) showAccentPicker = false
//    if (showThemeColorPicker) {
//        val currentThemeColor = customThemeColorHex.toColorOrNull()
//        ColorPickerDialog(
//            titleRes = R.string.theme_color_picker_title,
//            previewLabelRes = R.string.theme_color_preview,
//            resetLabelRes = R.string.theme_color_reset,
//            initialColor = currentThemeColor ?: MaterialTheme.colorScheme.surface,
//            allowReset = currentThemeColor != null,
//            onReset = { viewModel.setCustomThemeColor(null) },
//            onConfirm = { color -> viewModel.setCustomThemeColor(color) },
//            onDismiss = { showThemeColorPicker = false }
//        )
//    }
//    if (showAccentPicker) {
//        val currentAccent = customAccentColorHex.toColorOrNull()
//        ColorPickerDialog(
//            titleRes = R.string.accent_color_picker_title,
//            previewLabelRes = R.string.accent_color_preview,
//            resetLabelRes = R.string.accent_color_reset,
//            initialColor = currentAccent ?: MaterialTheme.colorScheme.primary,
//            allowReset = currentAccent != null,
//            onReset = { viewModel.setCustomAccentColor(null) },
//            onConfirm = { color -> viewModel.setCustomAccentColor(color) },
//            onDismiss = { showAccentPicker = false }
//        )
//    }
//
//    val context = LocalContext.current
//    if (showLanguageDialog) {
//        LanguageDialog(
//            options = languageOptions,
//            selectedCode = appLanguage,
//            onSelect = {
//                viewModel.setAppLanguage(it)
//                // Force activity recreation so every screen picks up the new locale immediately.
//                (context as? android.app.Activity)?.recreate()
//                showLanguageDialog = false
//            },
//            onDismiss = { showLanguageDialog = false }
//        )
//    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.appearance),
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

            // Morphe
//            val selectedLanguageLabel = when (appLanguage) {
//                "system" -> R.string.language_option_system
//                else -> languageOptions.firstOrNull { it.code == appLanguage }?.labelRes
//                    ?: R.string.language_option_english
//            }
//
//            Text(
//                text = stringResource(R.string.theme_presets),
//                style = MaterialTheme.typography.titleSmall,
//                color = MaterialTheme.colorScheme.onSurface,
//                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
//            )
//            Text(
//                text = stringResource(R.string.theme_presets_description),
//                style = MaterialTheme.typography.bodySmall,
//                color = MaterialTheme.colorScheme.onSurfaceVariant,
//                modifier = Modifier.padding(horizontal = 16.dp)
//            )

            val baseThemeSwatches = remember(supportsDynamicColor) {
                buildList {
                    add(ThemePresetSwatch(ThemePreset.DEFAULT, R.string.theme_preset_default, listOf(Color(0xFF4CD964), Color(0xFF4A90E2))))
                    add(ThemePresetSwatch(ThemePreset.LIGHT, R.string.light, listOf(Color(0xFFEEF2FF), Color(0xFFE2E6FB))))
                    add(ThemePresetSwatch(ThemePreset.DARK, R.string.dark, listOf(Color(0xFF1C1B1F), Color(0xFF2A2830))))
                    add(ThemePresetSwatch(ThemePreset.PURE_BLACK, R.string.theme_preset_amoled, listOf(Color(0xFF000000), Color(0xFF1C1B1F))))
                    if (supportsDynamicColor) {
                        add(ThemePresetSwatch(ThemePreset.DYNAMIC, R.string.theme_preset_dynamic, listOf(Color(0xFF6750A4), Color(0xFF4285F4))))
                    }
                }
            }

            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.theme_presets),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Morphe
//                Text(
//                    text = stringResource(R.string.theme_presets_description),
//                    style = MaterialTheme.typography.bodySmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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
                                // Morphe
                                enabled = true,
//                                enabled = option.preset != ThemePreset.PURE_BLACK || allowPureBlackPreset,
                                onClick = { viewModel.applyThemePreset(option.preset) }
//                                onClick = { viewModel.toggleThemePreset(option.preset) }
                            )
                        }
                    }
                }
            }


            // Morphe
//            ExpressiveSettingsCard(
//                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
//                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
//            ) {
//                ExpressiveSettingsItem(
//                    modifier = Modifier
//                        .alpha(themeControlsAlpha),
//                    headlineContent = stringResource(R.string.theme_color),
//                    supportingContent = stringResource(R.string.theme_color_description),
//                    trailingContent = {
//                        val previewColor = customThemeColorHex.toColorOrNull() ?: MaterialTheme.colorScheme.surface
//                        Box(
//                            modifier = Modifier
//                                .size(32.dp)
//                                .clip(RoundedCornerShape(12.dp))
//                                .border(
//                                    width = 1.dp,
//                                    color = MaterialTheme.colorScheme.outline,
//                                    shape = RoundedCornerShape(12.dp)
//                                )
//                                .background(previewColor, RoundedCornerShape(12.dp))
//                        )
//                    },
//                    enabled = canAdjustThemeColor,
//                    onClick = { showThemeColorPicker = true }
//                )
//                ExpressiveSettingsDivider()
//                ExpressiveSettingsItem(
//                    modifier = Modifier.alpha(accentControlsAlpha),
//                    headlineContent = stringResource(R.string.accent_color),
//                    supportingContent = stringResource(R.string.accent_color_description),
//                    trailingContent = {
//                        val previewColor = customAccentColorHex.toColorOrNull() ?: MaterialTheme.colorScheme.primary
//                        Box(
//                            modifier = Modifier
//                                .size(32.dp)
//                                .clip(RoundedCornerShape(12.dp))
//                                .border(
//                                    width = 1.dp,
//                                    color = MaterialTheme.colorScheme.outline,
//                                    shape = RoundedCornerShape(12.dp)
//                                )
//                                .background(previewColor, RoundedCornerShape(12.dp))
//                        )
//                    },
//                    enabled = canAdjustAccentColor,
//                    onClick = { showAccentPicker = true }
//                )
//            }
//
//            val accentPresets = remember {
//                listOf(
//                    Color(0xFF6750A4),
//                    Color(0xFF386641),
//                    Color(0xFF0061A4),
//                    Color(0xFF8E24AA),
//                    Color(0xFFEF6C00),
//                    Color(0xFF00897B),
//                    Color(0xFFD81B60),
//                    Color(0xFF5C6BC0),
//                    Color(0xFF43A047),
//                    Color(0xFFFF7043),
//                    Color(0xFF1DE9B6),
//                    Color(0xFFFFC400),
//                    Color(0xFF00B8D4),
//                    Color(0xFFBA68C8)
//                )
//            }
//            val selectedAccentArgb = customAccentColorHex.toColorOrNull()?.toArgb()
//            Text(
//                text = stringResource(R.string.accent_color_presets),
//                style = MaterialTheme.typography.titleSmall,
//                color = MaterialTheme.colorScheme.onSurface,
//                modifier = Modifier
//                    .padding(horizontal = 16.dp, vertical = 8.dp)
//                    .alpha(accentControlsAlpha)
//            )
//            Text(
//                text = stringResource(R.string.accent_color_presets_description),
//                style = MaterialTheme.typography.bodySmall,
//                color = MaterialTheme.colorScheme.onSurfaceVariant,
//                modifier = Modifier
//                    .padding(horizontal = 16.dp)
//                    .alpha(accentControlsAlpha)
//            )
//            FlowRow(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = 16.dp, vertical = 12.dp)
//                    .alpha(accentControlsAlpha),
//                horizontalArrangement = Arrangement.spacedBy(12.dp),
//                verticalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                accentPresets.forEach { preset ->
//                    val isSelected = selectedAccentArgb != null && preset.toArgb() == selectedAccentArgb
//                    Box(
//                        modifier = Modifier
//                            .size(40.dp)
//                            .clip(RoundedCornerShape(14.dp))
//                            .border(
//                                width = if (isSelected) 2.dp else 1.dp,
//                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
//                                shape = RoundedCornerShape(14.dp)
//                            )
//                            .background(preset, RoundedCornerShape(12.dp))
//                            .clickable(enabled = canAdjustAccentColor) {
//                                viewModel.setCustomAccentColor(preset)
//                            }
//                    )
//                }
//            }
//            Spacer(modifier = Modifier.height(16.dp))

            // Accent Color Section with Expressive Style
            val selectedAccentArgb = customAccentColorHex.toColorOrNull()?.toArgb()
            ExpressiveSettingsCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(accentControlsAlpha),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.accent_color_presets),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reset button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (selectedAccentArgb == null) 2.dp else 1.dp,
                                color = if (selectedAccentArgb == null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = canAdjustAccentColor) {
                                if (canAdjustAccentColor) {
                                    viewModel.setCustomAccentColor(null)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Reset accent color",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Color presets
                    THEME_PRESET_COLORS.forEach { preset ->
                        val isSelected = selectedAccentArgb != null && preset.toArgb() == selectedAccentArgb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected)
                                        preset.darken(0.4f) // Darker version of the color
                                    else
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .background(preset, RoundedCornerShape(12.dp))
                                .clickable(enabled = canAdjustAccentColor) {
                                    viewModel.setCustomAccentColor(preset)
                                }
                        )
                    }
                }
            }

            GroupHeader(stringResource(R.string.theme_preview_title))

            ExpressiveThemePreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = { viewModel.resetThemeSettings() },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(stringResource(R.string.theme_reset))
            }

            // FIXME: Hide this until we can add all Crowdin languages
//            GroupHeader(stringResource(R.string.language_settings))
//            ExpressiveSettingsCard(
//                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
//                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
//            ) {
//                ExpressiveSettingsItem(
//                    headlineContent = stringResource(R.string.app_language),
//                    supportingContent = stringResource(selectedLanguageLabel),
//                    onClick = { showLanguageDialog = true }
//                )
//            }
            Spacer(modifier = Modifier.height(16.dp))
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
            style = MaterialTheme.typography.labelSmall.copy(lineBreak = LineBreak.Heading),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center,
            maxLines = 2,   // Allow wrapping to 2 lines
            minLines = 2,   // Keep consistent height
            softWrap = true // Allow words wrapping to a new line
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
    return runCatching { Color(parseColor(normalized)) }.getOrNull()
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
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape),
        shape = shape,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = "UR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
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
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { }) {
                    Text(stringResource(R.string.apply))
                }
                TextButton(onClick = { }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun ExpressiveThemePreview(modifier: Modifier = Modifier) {
    ExpressiveSettingsCard(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Palette, contentDescription = null)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
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
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            text = stringResource(R.string.theme_preview_title),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary
                ).forEach { swatch ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = swatch,
                        tonalElevation = 1.dp,
                        modifier = Modifier.size(18.dp)
                    ) {}
                }
            }
        }
    }
}
