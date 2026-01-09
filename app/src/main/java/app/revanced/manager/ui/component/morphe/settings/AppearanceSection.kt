package app.revanced.manager.ui.component.morphe.settings

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.component.morphe.shared.LanguageRepository.getLanguageDisplayName
import app.revanced.manager.ui.component.morphe.utils.darken
import app.revanced.manager.ui.screen.settings.THEME_PRESET_COLORS
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.ui.viewmodel.MorpheThemeSettingsViewModel
import app.revanced.manager.ui.viewmodel.ThemePreset
import app.revanced.manager.util.toColorOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
    viewModel: MorpheThemeSettingsViewModel
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
                    title = stringResource(R.string.morphe_settings_return_to_expert),
                    description = stringResource(R.string.morphe_settings_return_to_expert_description),
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
    viewModel: MorpheThemeSettingsViewModel,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val appLanguage by viewModel.prefs.appLanguage.getAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTranslationInfoDialog by remember { mutableStateOf(false) }
    val currentLanguage = remember(appLanguage, context) {
        getLanguageDisplayName(appLanguage, context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Language Selection
        MorpheClickableCard(
            onClick = { showTranslationInfoDialog = true },
            cornerRadius = 12.dp,
            alpha = 0.33f
        ) {
            IconTextRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.app_language),
                description = currentLanguage,
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

    // Translation Info Dialog
    AnimatedVisibility(
        visible = showTranslationInfoDialog,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(if (showLanguageDialog) 0 else 200))
    ) {
        MorpheDialogWithLinks(
            title = stringResource(R.string.morphe_appearance_translations_info_title),
            message = stringResource(
                R.string.morphe_appearance_translations_info_text,
                stringResource(R.string.morphe_appearance_translations_info_url)
            ),
            urlLink = "https://morphe.software/translate",
            onDismiss = {
                showTranslationInfoDialog = false
                scope.launch {
                    delay(50)
                    showLanguageDialog = true
                }
            }
        )
    }

    // Language Picker Dialog
    AnimatedVisibility(
        visible = showLanguageDialog,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        LanguagePickerDialog(
            currentLanguage = appLanguage,
            onLanguageSelected = { languageCode ->
                scope.launch {
                    viewModel.setAppLanguage(languageCode)
                    // Force activity recreation to apply new locale
                    (context as? Activity)?.recreate()
                }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
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
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (selectedArgb == null) 2.dp else 1.dp,
                    color = if (selectedArgb == null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
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
                modifier = Modifier.size(24.dp),
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
                    .size(56.dp)
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
                        RoundedCornerShape(14.dp)
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

/**
 * Language picker dialog with searchable list
 */
@Composable
private fun LanguagePickerDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val allLanguages = remember(context) {
        LanguageRepository.getSupportedLanguages(context)
    }

    val filteredLanguages = remember(searchQuery, allLanguages) {
        if (searchQuery.isBlank()) {
            allLanguages
        } else {
            allLanguages.filter { language ->
                language.displayName.contains(searchQuery, ignoreCase = true) ||
                        language.nativeName.contains(searchQuery, ignoreCase = true) ||
                        language.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.app_language),
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search field
            MorpheDialogTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = {
                    Text(
                        stringResource(R.string.morphe_appearance_search),
                        color = LocalDialogSecondaryTextColor.current
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = LocalDialogSecondaryTextColor.current
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = LocalDialogSecondaryTextColor.current
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Language list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLanguages) { language ->
                    LanguageItem(
                        language = language,
                        isSelected = currentLanguage == language.code,
                        onClick = { onLanguageSelected(language.code) }
                    )
                }

                if (filteredLanguages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.morphe_appearance_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual language item in the list
 */
@Composable
private fun LanguageItem(
    language: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    MorpheClickableCard(
        onClick = onClick,
        cornerRadius = 8.dp,
        alpha = if (isSelected) 0.1f else 0.05f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flag/Language icon
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    LocalDialogTextColor.current.copy(alpha = 0.1f)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = language.flag,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogTextColor.current,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
