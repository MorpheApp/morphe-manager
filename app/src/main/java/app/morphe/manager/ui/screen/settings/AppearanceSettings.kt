/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.appearance.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.SeasonalEvent
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.ui.theme.resolveThemeStyle
import app.morphe.manager.ui.viewmodel.RandomInterval
import app.morphe.manager.ui.viewmodel.ThemeSettingsViewModel
import app.morphe.manager.util.AppCardColorDefaults
import app.morphe.manager.util.AppCardColorMode
import app.morphe.manager.util.AppCardColorStop
import app.morphe.manager.util.AppCardColorValues
import app.morphe.manager.util.AppLocale
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * The appearance section of the settings: language and scale, theme, colors and the home screen.
 */
@Composable
fun AppearanceSettings(themeViewModel: ThemeSettingsViewModel = koinViewModel()) {
    val scope = rememberCoroutineScope()
    val theme by themeViewModel.prefs.theme.getAsState()
    val themeStyle by themeViewModel.prefs.themeStyle.getAsState()
    val pureBlackTheme by themeViewModel.prefs.pureBlackTheme.getAsState()
    val customAccentColorHex by themeViewModel.prefs.customAccentColor.getAsState()
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val appLanguage by AppLocale.selected.collectAsStateWithLifecycle()
    val showGreetingPhrases by themeViewModel.prefs.showGreetingPhrases.getAsState()
    val showRepatchNotice by themeViewModel.prefs.showRepatchNotice.getAsState()
    val showMoreAppsCard by themeViewModel.prefs.showMoreAppsCard.getAsState()
    val colorAccents by themeViewModel.prefs.colorAccents.getAsState()
    val outlines by themeViewModel.prefs.outlines.getAsState()
    val blur by themeViewModel.prefs.blur.getAsState()
    val appCardColorMode by themeViewModel.prefs.appCardColorMode.getAsState()
    val customAppCardColors by themeViewModel.prefs.customAppCardColors.getAsState()
    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()
    val enableParallax by themeViewModel.prefs.enableBackgroundParallax.getAsState()
    val randomInterval by themeViewModel.prefs.randomBackgroundInterval.getAsState()
    val matrixUnlocked by themeViewModel.prefs.matrixBackgroundUnlocked.getAsState()
    val seasonalThemes by themeViewModel.prefs.seasonalThemes.getAsState()
    val seasonalEvent = remember { SeasonalEvent.on() }
    val resolvedRandomBackground =
        if (backgroundType == BackgroundType.RANDOM) rememberRandomBackground(themeViewModel.prefs) else null
    val effectiveThemeStyle = resolveThemeStyle(themeStyle, supportsDynamicColor)

    val uiScale by themeViewModel.prefs.uiScale.getAsState()

    val showLanguageDialog = remember { mutableStateOf(false) }
    val showBackgroundDialog = remember { mutableStateOf(false) }
    val appCardColorValues = remember(customAppCardColors) {
        AppCardColorDefaults.decodeColorValues(customAppCardColors)
    }
    val supportsPureBlack = theme != Theme.LIGHT

    LaunchedEffect(supportsPureBlack, pureBlackTheme) {
        if (!supportsPureBlack && pureBlackTheme) {
            themeViewModel.setPureBlackTheme(false)
        }
    }

    SettingsColumn {
        DisplaySection(
            appLanguage = appLanguage,
            uiScale = uiScale,
            onLanguageClick = { showLanguageDialog.value = true },
            // The scale lives on the activity context, so it takes effect on the next attach.
            // To write has to land first, or that attach would read the previous value
            onUiScaleApply = { scale ->
                scope.launch {
                    themeViewModel.setUiScale(scale).join()
                    (context as? Activity)?.recreate()
                }
            }
        )

        ThemeSection(
            theme = theme,
            themeStyle = effectiveThemeStyle,
            supportsDynamicColor = supportsDynamicColor,
            pureBlackTheme = pureBlackTheme,
            supportsPureBlack = supportsPureBlack,
            onThemeSelected = themeViewModel::setThemeMode,
            onStyleSelected = themeViewModel::setThemeStyle,
            onPureBlackToggle = { themeViewModel.setPureBlackTheme(!pureBlackTheme) },
            colorAccents = colorAccents,
            onColorAccentsToggle = { themeViewModel.toggleColorAccents(colorAccents) },
            accentColorHex = customAccentColorHex,
            onAccentSelected = themeViewModel::setCustomAccentColor,
            appCardColorMode = appCardColorMode,
            appCardColorValues = appCardColorValues,
            onAppCardColorModeSelected = themeViewModel::setAppCardColorMode,
            onAppCardColorSelected = themeViewModel::setAppCardColor
        )

        EffectsSection(
            outlines = outlines,
            onOutlinesToggle = { themeViewModel.toggleOutlines(outlines) },
            blur = blur,
            onBlurToggle = { themeViewModel.toggleBlur(blur) },
            backgroundType = backgroundType,
            randomInterval = randomInterval,
            onBackgroundClick = { showBackgroundDialog.value = true }
        )

        HomeScreenSection(
            showGreetingPhrases = showGreetingPhrases,
            showRepatchNotice = showRepatchNotice,
            onGreetingPhrasesToggle = { themeViewModel.toggleShowGreetingPhrases(showGreetingPhrases) },
            onRepatchNoticeToggle = { themeViewModel.toggleShowRepatchNotice(showRepatchNotice) },
            showMoreAppsCard = showMoreAppsCard,
            onMoreAppsCardToggle = { themeViewModel.toggleShowMoreAppsCard(showMoreAppsCard) }
        )
    }

    DialogHost(visible = showBackgroundDialog.value) {
        BackgroundPickerDialog(
            selectedBackground = backgroundType,
            onBackgroundSelected = themeViewModel::setBackgroundType,
            selectedInterval = randomInterval,
            onIntervalSelected = themeViewModel::setRandomInterval,
            onDismiss = { showBackgroundDialog.value = false },
            resolvedRandomBackground = resolvedRandomBackground,
            enableParallax = enableParallax,
            onParallaxToggle = { themeViewModel.toggleBackgroundParallax(enableParallax) },
            seasonalThemes = seasonalThemes,
            seasonalEvent = seasonalEvent,
            onSeasonalThemesToggle = { themeViewModel.toggleSeasonalThemes(seasonalThemes) },
            matrixUnlocked = matrixUnlocked
        )
    }

    // Language picker dialog
    DialogHost(visible = showLanguageDialog.value) {
        LanguagePickerDialog(
            currentLanguage = appLanguage,
            onLanguageSelected = { languageCode ->
                themeViewModel.setAppLanguage(languageCode)
                showLanguageDialog.value = false
            },
            onDismiss = { showLanguageDialog.value = false }
        )
    }
}

/**
 * How the app presents itself before any styling: the language it speaks and the scale it is
 * drawn at.
 */
@Composable
private fun DisplaySection(
    appLanguage: String,
    uiScale: Float,
    onLanguageClick: () -> Unit,
    onUiScaleApply: (Float) -> Unit
) {
    val context = LocalContext.current
    val currentLanguage = remember(appLanguage, context) {
        LanguageRepository.getLanguage(appLanguage, context)
    }

    SettingsSection(
        title = stringResource(R.string.settings_appearance_display),
        icon = MaterialSymbols.Rounded.Language
    ) {
        SettingsGroup {
            SettingsItem(
                onClick = onLanguageClick,
                title = stringResource(R.string.settings_appearance_app_language_current),
                subtitle = currentLanguage.displayName,
                leadingContent = {
                    Box(
                        modifier = Modifier.size(Defaults.IconSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentLanguage.flag,
                            fontSize = 20.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            )
            UiScaleSetting(currentScale = uiScale, onApply = onUiScaleApply)
            // How the manager shows on the launcher, as the language and scale say how it reads
            AppIconSettingsItem()
        }
    }
}

/**
 * Theme mode and color style, then the colors the manager wears: its accent, pure black, and the
 * colors apps and sources wear, on their cards on the home screen too.
 */
@Composable
private fun ThemeSection(
    theme: Theme,
    themeStyle: ThemeStyle,
    supportsDynamicColor: Boolean,
    pureBlackTheme: Boolean,
    supportsPureBlack: Boolean,
    onThemeSelected: (Theme) -> Unit,
    onStyleSelected: (ThemeStyle) -> Unit,
    onPureBlackToggle: () -> Unit,
    colorAccents: Boolean,
    onColorAccentsToggle: () -> Unit,
    accentColorHex: String?,
    onAccentSelected: (Color?) -> Unit,
    appCardColorMode: AppCardColorMode,
    appCardColorValues: AppCardColorValues,
    onAppCardColorModeSelected: (AppCardColorMode) -> Unit,
    onAppCardColorSelected: (AppCardColorStop, String) -> Unit
) {
    SettingsSection(
        title = stringResource(R.string.settings_appearance_theme),
        icon = MaterialSymbols.Rounded.Palette
    ) {
        SearchableSetting(listOf(stringResource(R.string.settings_appearance_theme))) {
            ThemeSelector(
                theme = theme,
                onThemeSelected = onThemeSelected,
                style = themeStyle,
                supportsDynamicColor = supportsDynamicColor,
                onStyleSelected = onStyleSelected
            )
        }

        // Dynamic color derives the accent from the wallpaper, leaving nothing to pick here
        val showAccent = themeStyle != ThemeStyle.MATERIAL_YOU

        SettingsGroup {
            FoldingVisibility(visible = showAccent) {
                SearchableSetting(listOf(stringResource(R.string.settings_appearance_accent_color))) {
                    SettingsItemCard(onClick = null) {
                        AccentColorSelector(
                            selectedColorHex = accentColorHex,
                            onColorSelected = onAccentSelected,
                            dynamicColorEnabled = !showAccent
                        )
                    }
                }
            }

            FoldingVisibility(visible = supportsPureBlack) {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_appearance_pure_black),
                    subtitle = stringResource(R.string.settings_appearance_pure_black_description),
                    icon = MaterialSymbols.Rounded.Contrast,
                    checked = pureBlackTheme,
                    onToggle = onPureBlackToggle
                )
            }

            // Monochrome already trades every app's and source's color for its own accent, and leaves
            // the app cards without one to pick
            FoldingVisibility(visible = themeStyle != ThemeStyle.MONOCHROME) {
                Column {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_appearance_color_accents),
                        subtitle = stringResource(R.string.settings_appearance_color_accents_description),
                        icon = MaterialSymbols.Rounded.Format_color_fill,
                        checked = colorAccents,
                        onToggle = onColorAccentsToggle
                    )
                    val title = stringResource(R.string.settings_appearance_app_card_colors)
                    SearchableSetting(listOf(title)) {
                        SettingsItemCard(onClick = null) {
                            AppCardColorSelector(
                                mode = appCardColorMode,
                                values = appCardColorValues,
                                onModeSelected = onAppCardColorModeSelected,
                                onColorSelected = onAppCardColorSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How the surfaces are drawn: their edges, the blur under what floats, and the animated background. */
@Composable
private fun EffectsSection(
    outlines: Boolean,
    onOutlinesToggle: () -> Unit,
    blur: Boolean,
    onBlurToggle: () -> Unit,
    backgroundType: BackgroundType,
    randomInterval: RandomInterval,
    onBackgroundClick: () -> Unit
) {
    SettingsSection(
        title = stringResource(R.string.settings_appearance_effects),
        icon = MaterialSymbols.Rounded.Auto_awesome
    ) {
        SettingsGroup {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_appearance_outlines),
                subtitle = stringResource(R.string.settings_appearance_outlines_description),
                icon = MaterialSymbols.Rounded.Border_style,
                checked = outlines,
                onToggle = onOutlinesToggle
            )

            // Blurring needs Android 12, before which what floats over the content is solid anyway
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_appearance_blur),
                    subtitle = stringResource(R.string.settings_appearance_blur_description),
                    icon = MaterialSymbols.Rounded.Blur_on,
                    checked = blur,
                    onToggle = onBlurToggle
                )
            }

            BackgroundSettingsItem(
                selectedBackground = backgroundType,
                selectedInterval = randomInterval,
                onClick = onBackgroundClick
            )
        }
    }
}

/**
 * What the home screen shows besides the apps.
 */
@Composable
private fun HomeScreenSection(
    showGreetingPhrases: Boolean,
    showRepatchNotice: Boolean,
    onGreetingPhrasesToggle: () -> Unit,
    onRepatchNoticeToggle: () -> Unit,
    showMoreAppsCard: Boolean,
    onMoreAppsCardToggle: () -> Unit
) {
    SettingsSection(
        title = stringResource(R.string.settings_appearance_home_screen),
        icon = MaterialSymbols.Rounded.Dashboard
    ) {
        SettingsGroup {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_appearance_greeting_phrases),
                subtitle = stringResource(R.string.settings_appearance_greeting_phrases_subtitle),
                icon = MaterialSymbols.Rounded.Chat_bubble,
                checked = showGreetingPhrases,
                onToggle = onGreetingPhrasesToggle
            )
            SettingsSwitchItem(
                title = stringResource(R.string.settings_appearance_repatch_notice),
                subtitle = stringResource(R.string.settings_appearance_repatch_notice_description),
                icon = MaterialSymbols.Rounded.Auto_fix_high,
                checked = showRepatchNotice,
                onToggle = onRepatchNoticeToggle
            )
            SettingsSwitchItem(
                title = stringResource(R.string.settings_appearance_more_apps_card),
                subtitle = stringResource(R.string.settings_appearance_more_apps_card_description),
                icon = MaterialSymbols.Rounded.Apps,
                checked = showMoreAppsCard,
                onToggle = onMoreAppsCardToggle
            )
        }
    }
}
