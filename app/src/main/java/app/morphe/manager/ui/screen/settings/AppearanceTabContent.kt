/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.ui.screen.settings.appearance.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.shared.LanguageRepository.getLanguageDisplayName
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.viewmodel.ThemeSettingsViewModel
import app.morphe.manager.util.saveLanguageToPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Appearance tab content.
 */
@Composable
fun AppearanceTabContent(
    theme: Theme,
    pureBlackTheme: Boolean,
    dynamicColor: Boolean,
    customAccentColorHex: String?,
    themeViewModel: ThemeSettingsViewModel,
    homeAppButtonPrefs: HomeAppButtonPreferences = koinInject(),
    scrollState: ScrollState = rememberScrollState(),
    onThemeSelectorPositioned: ((Rect) -> Unit)? = null,
    onThemeSelectorScrollTarget: ((Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val appLanguage by themeViewModel.prefs.appLanguage.getAsState()
    val showGreetingPhrases by themeViewModel.prefs.showGreetingPhrases.getAsState()
    val showAppGroupingSwitcher by homeAppButtonPrefs.showCategoryViewSwitcher.collectAsStateWithLifecycle()
    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()
    val enableParallax by themeViewModel.prefs.enableBackgroundParallax.getAsState()
    val randomInterval by themeViewModel.prefs.randomBackgroundInterval.getAsState()

    val showLanguageDialog = remember { mutableStateOf(false) }
    val showTranslationInfoDialog = remember { mutableStateOf(false) }

    // Localized strings for accessibility
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    val contentPadding = rememberWindowSize().contentPadding
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = contentPadding, vertical = MorpheDefaults.ContentPadding)
    ) {
        // Language section
        Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
            LanguageSection(
                appLanguage = appLanguage,
                onLanguageClick = { showTranslationInfoDialog.value = true }
            )
        }

        // Home screen section
        Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
            SectionTitle(
                text = stringResource(R.string.settings_appearance_home_screen),
                icon = Icons.Outlined.Dashboard
            )
        }

        SettingsGroup(
            modifier = Modifier.padding(bottom = MorpheDefaults.ContentPadding)
        ) {
            SettingsItem(
                onClick = { themeViewModel.toggleShowGreetingPhrases(showGreetingPhrases) },
                title = stringResource(R.string.settings_appearance_greeting_phrases),
                subtitle = stringResource(R.string.settings_appearance_greeting_phrases_subtitle),
                leadingContent = {
                    MorpheIcon(icon = Icons.Outlined.ChatBubbleOutline)
                },
                trailingContent = {
                    MorpheSwitch(
                        checked = showGreetingPhrases,
                        onCheckedChange = null,
                        modifier = Modifier.semantics {
                            stateDescription = if (showGreetingPhrases) enabledState else disabledState
                        }
                    )
                }
            )
            MorpheSettingsDivider()
            SettingsItem(
                onClick = { homeAppButtonPrefs.setShowCategoryViewSwitcher(!showAppGroupingSwitcher) },
                title = stringResource(R.string.settings_appearance_app_grouping),
                subtitle = stringResource(R.string.settings_appearance_app_grouping_description),
                leadingContent = {
                    MorpheIcon(icon = Icons.Outlined.ViewAgenda)
                },
                trailingContent = {
                    MorpheSwitch(
                        checked = showAppGroupingSwitcher,
                        onCheckedChange = null,
                        modifier = Modifier.semantics {
                            stateDescription = if (showAppGroupingSwitcher) enabledState else disabledState
                        }
                    )
                }
            )
        }

        // Theme section
        Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
            SectionTitle(
                text = stringResource(R.string.settings_appearance_theme),
                icon = Icons.Outlined.Palette
            )
        }

        Box(
            Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth().then(
                if (onThemeSelectorPositioned != null || onThemeSelectorScrollTarget != null)
                    Modifier.onGloballyPositioned { coords ->
                        onThemeSelectorPositioned?.invoke(coords.boundsInWindow())
                        onThemeSelectorScrollTarget?.invoke(coords.boundsInParent().top.roundToInt())
                    }
                else Modifier
            )
        ) {
            ThemeSelector(
                theme = theme,
                dynamicColor = dynamicColor,
                supportsDynamicColor = supportsDynamicColor,
                onThemeSelected = { selectedTheme ->
                    themeViewModel.applyThemePresetByKey(selectedTheme)
                }
            )
        }

        // Pure black theme toggle
        AnimatedVisibility(
            visible = theme != Theme.LIGHT,
            enter = MorpheAnimations.expandFadeEnter,
            exit = MorpheAnimations.shrinkFadeExit
        ) {
            SettingsGroup(
                modifier = Modifier.padding(bottom = MorpheDefaults.ContentPadding)
            ) {
                SettingsItem(
                    onClick = { themeViewModel.togglePureBlackTheme(pureBlackTheme) },
                    title = stringResource(R.string.settings_appearance_pure_black),
                    subtitle = stringResource(R.string.settings_appearance_pure_black_description),
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.Contrast)
                    },
                    trailingContent = {
                        MorpheSwitch(
                            checked = pureBlackTheme,
                            onCheckedChange = null,
                            modifier = Modifier.semantics {
                                stateDescription = if (pureBlackTheme) enabledState else disabledState
                            }
                        )
                    }
                )
            }
        }

        // Accent color section
        AnimatedVisibility(
            visible = !dynamicColor,
            enter = MorpheAnimations.expandFadeEnter,
            exit = MorpheAnimations.shrinkFadeExit
        ) {
            Column {
                Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
                    SectionTitle(
                        text = stringResource(R.string.settings_appearance_accent_color),
                        icon = Icons.Outlined.ColorLens
                    )
                }
                Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
                    AccentColorSelector(
                        selectedColorHex = customAccentColorHex,
                        onColorSelected = { color -> themeViewModel.setCustomAccentColor(color) },
                        dynamicColorEnabled = dynamicColor
                    )
                }
            }
        }

        // Background type section
        Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
            SectionTitle(
                text = stringResource(R.string.settings_appearance_background),
                icon = Icons.Outlined.Wallpaper
            )
        }

        Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
            BackgroundSelector(
                selectedBackground = backgroundType,
                onBackgroundSelected = { selectedType ->
                    themeViewModel.setBackgroundType(selectedType)
                },
                selectedInterval = randomInterval,
                onIntervalSelected = { interval ->
                    themeViewModel.setRandomInterval(interval)
                }
            )
        }

        // Parallax effect toggle
        AnimatedVisibility(
            visible = backgroundType != BackgroundType.NONE,
            enter = MorpheAnimations.expandFadeEnter,
            exit = MorpheAnimations.shrinkFadeExit
        ) {
            SettingsGroup(
                modifier = Modifier.padding(bottom = MorpheDefaults.ContentPadding)
            ) {
                SettingsItem(
                    onClick = { themeViewModel.toggleBackgroundParallax(enableParallax) },
                    title = stringResource(R.string.settings_appearance_parallax_effect),
                    subtitle = stringResource(R.string.settings_appearance_parallax_effect_description),
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.ScreenRotation)
                    },
                    trailingContent = {
                        MorpheSwitch(
                            checked = enableParallax,
                            onCheckedChange = null,
                            modifier = Modifier.semantics {
                                stateDescription = if (enableParallax) enabledState else disabledState
                            }
                        )
                    }
                )
            }
        }

        // App icon section
        Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
            SectionTitle(
                text = stringResource(R.string.settings_appearance_app_icon_selector_title),
                icon = Icons.Outlined.Apps
            )
        }

        AppIconSelector()
    }

    // Translation info dialog
    AnimatedVisibility(
        visible = showTranslationInfoDialog.value,
        enter = MorpheAnimations.fadeIn,
        exit = MorpheAnimations.fadeOut(if (showLanguageDialog.value) 0 else MorpheDefaults.ANIMATION_DURATION)
    ) {
        MorpheDialogWithLinks(
            title = stringResource(R.string.settings_appearance_translations_info_title),
            message = stringResource(
                R.string.settings_appearance_translations_info_text,
                stringResource(R.string.settings_appearance_translations_info_url)
            ),
            urlLink = "https://morphe.software/translate",
            onDismiss = {
                showTranslationInfoDialog.value = false
                scope.launch {
                    delay(50.milliseconds)
                    showLanguageDialog.value = true
                }
            }
        )
    }

    // Language picker dialog
    AnimatedVisibility(
        visible = showLanguageDialog.value,
        enter = MorpheAnimations.fadeIn,
        exit = MorpheAnimations.fadeOut
    ) {
        LanguagePickerDialog(
            currentLanguage = appLanguage,
            onLanguageSelected = { languageCode ->
                saveLanguageToPrefs(context, languageCode)
                themeViewModel.setAppLanguage(languageCode)
                showLanguageDialog.value = false
                (context as? Activity)?.recreate()
            },
            onDismiss = { showLanguageDialog.value = false }
        )
    }
}


/**
 * Language selection section.
 */
@Composable
private fun LanguageSection(
    appLanguage: String,
    onLanguageClick: () -> Unit
) {
    val context = LocalContext.current
    val currentLanguage = remember(appLanguage, context) {
        getLanguageDisplayName(appLanguage, context)
    }

    val currentLanguageOption = remember(appLanguage, context) {
        LanguageRepository.getSupportedLanguages(context)
            .find { it.code == appLanguage }
    }

    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
        SectionTitle(
            text = stringResource(R.string.settings_appearance_app_language),
            icon = Icons.Outlined.Language
        )

        SettingsGroup {
            SettingsItem(
                onClick = onLanguageClick,
                title = stringResource(R.string.settings_appearance_app_language_current),
                subtitle = currentLanguage,
                leadingContent = {
                    Box(
                        modifier = Modifier.size(MorpheDefaults.IconSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentLanguageOption?.flag ?: "🌐",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                trailingContent = {
                    MorpheIcon(icon = Icons.Outlined.ChevronRight)
                }
            )
        }
    }
}
