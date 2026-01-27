package app.revanced.manager.ui.component.morphe.settings

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Appearance tab content
 */
@Composable
fun AppearanceTabContent(
    theme: Theme,
    pureBlackTheme: Boolean,
    dynamicColor: Boolean,
    customAccentColorHex: String?,
    themeViewModel: MorpheThemeSettingsViewModel
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val appLanguage by themeViewModel.prefs.appLanguage.getAsState()
    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTranslationInfoDialog by remember { mutableStateOf(false) }
    val currentLanguage = remember(appLanguage, context) {
        getLanguageDisplayName(appLanguage, context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Background Type
        SectionTitle(
            text = stringResource(R.string.morphe_background_type),
            icon = Icons.Outlined.Wallpaper
        )

        SectionCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SelectorSection(
                    title = "",
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
                            themeViewModel.prefs.backgroundType.update(BackgroundType.valueOf(selectedType))
                        }
                    },
                    columns = null
                )
            }
        }

        // Theme Mode
        SectionTitle(
            text = stringResource(R.string.theme),
            icon = Icons.Outlined.Palette
        )

        SectionCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SelectorSection(
                    title = "",
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
                                "SYSTEM" -> themeViewModel.applyThemePreset(ThemePreset.DEFAULT)
                                "LIGHT" -> themeViewModel.applyThemePreset(ThemePreset.LIGHT)
                                "DARK" -> themeViewModel.applyThemePreset(ThemePreset.DARK)
                                "BLACK" -> themeViewModel.applyThemePreset(ThemePreset.PURE_BLACK)
                                "DYNAMIC" -> themeViewModel.applyThemePreset(ThemePreset.DYNAMIC)
                            }
                        }
                    },
                    columns = null
                )
            }
        }

        // Accent Color
        SectionTitle(
            text = stringResource(R.string.accent_color_presets),
            icon = Icons.Outlined.ColorLens
        )

        SectionCard {
            Column(modifier = Modifier.padding(16.dp)) {
                AccentColorPresetsRow(
                    selectedColorHex = customAccentColorHex,
                    onColorSelected = { color -> themeViewModel.setCustomAccentColor(color) },
                    dynamicColorEnabled = dynamicColor
                )
            }
        }

        // Language
        SectionTitle(
            text = stringResource(R.string.app_language),
            icon = Icons.Outlined.Language
        )

        val currentLanguageOption = remember(appLanguage, context) {
            LanguageRepository.getSupportedLanguages(context)
                .find { it.code == appLanguage }
        }

        RichSettingsItem(
            onClick = { showTranslationInfoDialog = true },
            showBorder = true,
            title = stringResource(R.string.morphe_appearance_current_language),
            subtitle = currentLanguage,
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentLanguageOption?.flag ?: "🌐",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            },
            trailingContent = {
                MorpheIcon(icon = Icons.Outlined.ChevronRight)
            }
        )

        // Icon manager
        SectionTitle(
            text = stringResource(R.string.morphe_app_icon_selector_title),
            icon = Icons.Outlined.Apps
        )

        SectionCard {
            AppIconSection()
        }
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
                    themeViewModel.setAppLanguage(languageCode)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Reset button
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
            MorpheIcon(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.clear),
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
                            preset.darken(0.4f)
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
