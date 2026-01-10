package app.revanced.manager.ui.screen.settings

import android.app.Activity
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.AppTopBar
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.GroupHeader
import app.revanced.manager.ui.component.morphe.shared.LanguageOption
import app.revanced.manager.ui.component.morphe.shared.LanguageRepository
import app.revanced.manager.ui.component.morphe.shared.rememberSelectedLanguageLabel
import app.revanced.manager.ui.component.morphe.utils.darken
import app.revanced.manager.ui.component.settings.ExpressiveSettingsCard
import app.revanced.manager.ui.component.settings.ExpressiveSettingsItem
import app.revanced.manager.ui.viewmodel.MorpheThemeSettingsViewModel
import app.revanced.manager.ui.viewmodel.ThemePreset
import app.revanced.manager.util.toColorOrNull
import org.koin.androidx.compose.koinViewModel

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MorpheThemeSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: MorpheThemeSettingsViewModel = koinViewModel()
) {
    val prefs = viewModel.prefs

    val customAccentColorHex by prefs.customAccentColor.getAsState()
    val themePresetSelectionEnabled by prefs.themePresetSelectionEnabled.getAsState()
    val selectedThemePresetName by prefs.themePresetSelectionName.getAsState()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val selectedThemePreset = remember(selectedThemePresetName, themePresetSelectionEnabled, supportsDynamicColor) {
        if (!themePresetSelectionEnabled) null else selectedThemePresetName.takeIf { it.isNotBlank() }?.let {
            val preset = runCatching { ThemePreset.valueOf(it) }.getOrNull()
            if (!supportsDynamicColor && preset == ThemePreset.DYNAMIC) ThemePreset.DEFAULT else preset
        }
    }
    val canAdjustAccentColor = selectedThemePreset != ThemePreset.DYNAMIC
    val accentControlsAlpha = if (canAdjustAccentColor) 1f else 0.5f

    val appLanguage by prefs.appLanguage.getAsState()
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val languageOptions = remember(context) {
        LanguageRepository.getSupportedLanguages(context)
    }

    if (showLanguageDialog) {
        LanguageDialog(
            options = languageOptions,
            selectedCode = appLanguage,
            onSelect = {
                viewModel.setAppLanguage(it)
                (context as? Activity)?.recreate()
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

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
                    text = stringResource(R.string.theme),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Create rows with 5 items each
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    baseThemeSwatches.chunked(5).forEach { rowSwatches ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowSwatches.forEach { option ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ThemeSwatchChip(
                                        modifier = Modifier.fillMaxWidth(),
                                        label = stringResource(option.labelRes),
                                        colors = option.colors,
                                        isSelected = selectedThemePreset == option.preset,
                                        onClick = { viewModel.applyThemePreset(option.preset) }
                                    )
                                }
                            }
                            // Fill remaining space if row is incomplete
                            repeat(5 - rowSwatches.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

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

                // Create rows with 5 items each
                val allColors = buildList {
                    add(null) // Reset button
                    addAll(THEME_PRESET_COLORS)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    allColors.chunked(5).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (color == null) {
                                        // Reset button - same style as theme chips
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
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
                                                    RoundedCornerShape(14.dp)
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
                                    } else {
                                        // Color preset - same style as theme chips
                                        val isSelected = selectedAccentArgb != null && color.toArgb() == selectedAccentArgb
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected)
                                                        color.darken(0.4f)
                                                    else
                                                        MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .background(color, RoundedCornerShape(14.dp))
                                                .clickable(enabled = canAdjustAccentColor) {
                                                    viewModel.setCustomAccentColor(color)
                                                }
                                        )
                                    }
                                }
                            }
                            // Fill remaining space if row is incomplete
                            repeat(5 - rowColors.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            ExpressiveThemePreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            GroupHeader(stringResource(R.string.morphe_appearance_app_language))
            ExpressiveSettingsCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                ExpressiveSettingsItem(
                    headlineContent = stringResource(R.string.app_language),
                    supportingContent = rememberSelectedLanguageLabel(appLanguage),
                    onClick = { showLanguageDialog = true }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private data class ThemePresetSwatch(val preset: ThemePreset, @param:StringRes val labelRes: Int, val colors: List<Color>)


@Composable
private fun ThemeSwatchChip(
    modifier: Modifier = Modifier,
    label: String,
    colors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
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
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            softWrap = true
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
        title = {
            Text(
                text = stringResource(R.string.language_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(options) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option.code) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.code == selectedCode,
                            onClick = { onSelect(option.code) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${option.flag} ${option.displayName}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    )
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
