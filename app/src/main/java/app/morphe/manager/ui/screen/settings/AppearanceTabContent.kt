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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.ui.screen.settings.appearance.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.ui.theme.resolveThemeStyle
import app.morphe.manager.ui.theme.toUiScalePercent
import app.morphe.manager.ui.viewmodel.RandomInterval
import app.morphe.manager.ui.viewmodel.ThemeSettingsViewModel
import app.morphe.manager.util.AppCardColorDefaults
import app.morphe.manager.util.AppCardColorMode
import app.morphe.manager.util.AppLocale
import app.morphe.manager.util.MORPHE_WEBSITE_URL
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
    themeStyle: ThemeStyle,
    pureBlackTheme: Boolean,
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
    val appLanguage by AppLocale.selected.collectAsStateWithLifecycle()
    val showGreetingPhrases by themeViewModel.prefs.showGreetingPhrases.getAsState()
    val showRepatchNotice by themeViewModel.prefs.showRepatchNotice.getAsState()
    val appCardColorMode by themeViewModel.prefs.appCardColorMode.getAsState()
    val customAppCardColors by themeViewModel.prefs.customAppCardColors.getAsState()
    val showAppGroupingSwitcher by homeAppButtonPrefs.showCategoryViewSwitcher.collectAsStateWithLifecycle()
    val showSortButton by homeAppButtonPrefs.showSortButton.collectAsStateWithLifecycle()
    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()
    val enableParallax by themeViewModel.prefs.enableBackgroundParallax.getAsState()
    val randomInterval by themeViewModel.prefs.randomBackgroundInterval.getAsState()
    val matrixUnlocked by themeViewModel.prefs.matrixBackgroundUnlocked.getAsState()
    val resolvedRandomBackground by themeViewModel.resolvedRandomBackground.collectAsStateWithLifecycle()
    val effectiveThemeStyle = resolveThemeStyle(themeStyle, supportsDynamicColor)
    val showAppCardColorSetting = effectiveThemeStyle != ThemeStyle.MONOCHROME

    val uiScale by themeViewModel.prefs.uiScale.getAsState()

    val showLanguageDialog = remember { mutableStateOf(false) }
    val showUiScaleDialog = remember { mutableStateOf(false) }
    val showTranslationInfoDialog = remember { mutableStateOf(false) }
    val showAppCardColorDialog = remember { mutableStateOf(false) }
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

    LaunchedEffect(showAppCardColorSetting) {
        if (!showAppCardColorSetting) showAppCardColorDialog.value = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(settingsTabPadding())
    ) {
        LanguageAndDisplaySection(
            appLanguage = appLanguage,
            uiScale = uiScale,
            onLanguageClick = { showTranslationInfoDialog.value = true },
            onUiScaleClick = { showUiScaleDialog.value = true }
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
            backgroundType = backgroundType,
            randomInterval = randomInterval,
            onBackgroundClick = { showBackgroundDialog.value = true },
            onSelectorPositioned = onThemeSelectorPositioned,
            onSelectorScrollTarget = onThemeSelectorScrollTarget
        )

        ColorsSection(
            themeStyle = effectiveThemeStyle,
            accentColorHex = customAccentColorHex,
            appCardColorMode = appCardColorMode,
            showAppCardColors = showAppCardColorSetting,
            onAccentSelected = themeViewModel::setCustomAccentColor,
            onAppCardColorsClick = { showAppCardColorDialog.value = true }
        )

        HomeScreenSection(
            showGreetingPhrases = showGreetingPhrases,
            showRepatchNotice = showRepatchNotice,
            showSortButton = showSortButton,
            showAppGrouping = showAppGroupingSwitcher,
            onGreetingPhrasesToggle = { themeViewModel.toggleShowGreetingPhrases(showGreetingPhrases) },
            onRepatchNoticeToggle = { themeViewModel.toggleShowRepatchNotice(showRepatchNotice) },
            onSortButtonToggle = { homeAppButtonPrefs.setShowSortButton(!showSortButton) },
            onAppGroupingToggle = { homeAppButtonPrefs.setShowCategoryViewSwitcher(!showAppGroupingSwitcher) }
        )
    }

    if (showBackgroundDialog.value) {
        BackgroundPickerDialog(
            selectedBackground = backgroundType,
            onBackgroundSelected = themeViewModel::setBackgroundType,
            selectedInterval = randomInterval,
            onIntervalSelected = themeViewModel::setRandomInterval,
            onDismiss = { showBackgroundDialog.value = false },
            resolvedRandomBackground = resolvedRandomBackground,
            enableParallax = enableParallax,
            onParallaxToggle = { themeViewModel.toggleBackgroundParallax(enableParallax) },
            matrixUnlocked = matrixUnlocked
        )
    }

    // App card color dialog
    AnimatedVisibility(
        visible = showAppCardColorDialog.value && showAppCardColorSetting,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        AppCardColorDialog(
            mode = appCardColorMode,
            accentColorHex = customAccentColorHex.orEmpty(),
            startColorHex = appCardColorValues.startHex,
            middleColorHex = appCardColorValues.middleHex,
            endColorHex = appCardColorValues.endHex,
            solidColorHex = appCardColorValues.solidHex,
            onApply = themeViewModel::applyAppCardColors,
            onDismiss = { showAppCardColorDialog.value = false }
        )
    }

    // Interface scale dialog
    AnimatedVisibility(
        visible = showUiScaleDialog.value,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        UiScaleDialog(
            currentScale = uiScale,
            // The scale lives on the activity context, so it takes effect on the next attach.
            // To write has to land first, or that attach would read the previous value
            onApply = { scale ->
                scope.launch {
                    themeViewModel.setUiScale(scale).join()
                    (context as? Activity)?.recreate()
                }
            },
            onDismiss = { showUiScaleDialog.value = false }
        )
    }

    // Translation info dialog
    AnimatedVisibility(
        visible = showTranslationInfoDialog.value,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut(if (showLanguageDialog.value) 0 else Defaults.ANIMATION_DURATION)
    ) {
        AppDialogWithLinks(
            title = stringResource(R.string.settings_appearance_translations_info_title),
            message = stringResource(
                R.string.settings_appearance_translations_info_text,
                stringResource(R.string.settings_appearance_translations_info_url)
            ),
            urlLink = "$MORPHE_WEBSITE_URL/translate",
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
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
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
private fun LanguageAndDisplaySection(
    appLanguage: String,
    uiScale: Float,
    onLanguageClick: () -> Unit,
    onUiScaleClick: () -> Unit
) {
    val context = LocalContext.current
    val currentLanguage = remember(appLanguage, context) {
        LanguageRepository.getLanguage(appLanguage, context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)) {
        SectionTitle(
            text = stringResource(R.string.settings_appearance_language_and_display),
            icon = Icons.Outlined.Language
        )

        SettingsGroup(modifier = Modifier.padding(bottom = Defaults.ContentPadding)) {
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
            SettingsDivider()
            SettingsItem(
                onClick = onUiScaleClick,
                title = stringResource(R.string.settings_appearance_ui_scale),
                subtitle = "${uiScale.toUiScalePercent()}%",
                leadingContent = { ThemedIcon(icon = Icons.Outlined.FormatSize) }
            )
        }
    }
}

/**
 * Theme mode and color style, then how the rest of the manager is dressed: pure black, the
 * animated background and the launcher icon.
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
    backgroundType: BackgroundType,
    randomInterval: RandomInterval,
    onBackgroundClick: () -> Unit,
    onSelectorPositioned: ((Rect) -> Unit)?,
    onSelectorScrollTarget: ((Int) -> Unit)?
) {
    SectionHeader(
        text = stringResource(R.string.settings_appearance_theme),
        icon = Icons.Outlined.Palette
    )

    Box(
        Modifier
            .padding(bottom = Defaults.ContentPadding)
            .fillMaxWidth()
            .then(
                if (onSelectorPositioned != null || onSelectorScrollTarget != null)
                    Modifier.onGloballyPositioned { coords ->
                        onSelectorPositioned?.invoke(coords.boundsInWindow())
                        onSelectorScrollTarget?.invoke(coords.boundsInParent().top.roundToInt())
                    }
                else Modifier
            )
    ) {
        ThemeSelector(
            theme = theme,
            onThemeSelected = onThemeSelected,
            style = themeStyle,
            supportsDynamicColor = supportsDynamicColor,
            onStyleSelected = onStyleSelected
        )
    }

    SettingsGroup(modifier = Modifier.padding(bottom = Defaults.ContentPadding)) {
        AnimatedVisibility(
            visible = supportsPureBlack,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            Column {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_appearance_pure_black),
                    subtitle = stringResource(R.string.settings_appearance_pure_black_description),
                    icon = Icons.Outlined.Contrast,
                    checked = pureBlackTheme,
                    onToggle = onPureBlackToggle
                )
                SettingsDivider()
            }
        }

        BackgroundSettingsItem(
            selectedBackground = backgroundType,
            selectedInterval = randomInterval,
            onClick = onBackgroundClick
        )

        SettingsDivider()

        AppIconSettingsItem()
    }
}

/**
 * Accent color and home app card colors, both driven by the active theme style.
 */
@Composable
private fun ColorsSection(
    themeStyle: ThemeStyle,
    accentColorHex: String?,
    appCardColorMode: AppCardColorMode,
    showAppCardColors: Boolean,
    onAccentSelected: (Color?) -> Unit,
    onAppCardColorsClick: () -> Unit
) {
    SectionHeader(
        text = stringResource(R.string.settings_appearance_colors),
        icon = Icons.Outlined.ColorLens
    )

    // Dynamic color derives the accent from the wallpaper, leaving nothing to pick here
    val showAccent = themeStyle != ThemeStyle.MATERIAL_YOU

    // Monochrome leaves app cards colorless and Material You has no accent to pick, and no style
    // is both, so the group always has at least one of the two
    SettingsGroup(modifier = Modifier.padding(bottom = Defaults.ContentPadding)) {
        AnimatedVisibility(
            visible = showAccent,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            AccentColorSelector(
                selectedColorHex = accentColorHex,
                onColorSelected = onAccentSelected,
                dynamicColorEnabled = !showAccent
            )
        }

        AnimatedVisibility(
            visible = showAppCardColors,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            Column {
                if (showAccent) SettingsDivider()
                SettingsItem(
                    onClick = onAppCardColorsClick,
                    title = stringResource(R.string.settings_appearance_app_card_colors),
                    subtitle = stringResource(appCardColorMode.descriptionResId),
                    leadingContent = { ThemedIcon(icon = Icons.Outlined.Style) }
                )
            }
        }
    }
}

/**
 * Toggles for what the home screen shows.
 */
@Composable
private fun HomeScreenSection(
    showGreetingPhrases: Boolean,
    showRepatchNotice: Boolean,
    showSortButton: Boolean,
    showAppGrouping: Boolean,
    onGreetingPhrasesToggle: () -> Unit,
    onRepatchNoticeToggle: () -> Unit,
    onSortButtonToggle: () -> Unit,
    onAppGroupingToggle: () -> Unit
) {
    SectionHeader(
        text = stringResource(R.string.settings_appearance_home_screen),
        icon = Icons.Outlined.Dashboard
    )

    SettingsGroup(modifier = Modifier.padding(bottom = Defaults.ContentPadding)) {
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_greeting_phrases),
            subtitle = stringResource(R.string.settings_appearance_greeting_phrases_subtitle),
            icon = Icons.Outlined.ChatBubbleOutline,
            checked = showGreetingPhrases,
            onToggle = onGreetingPhrasesToggle
        )
        SettingsDivider()
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_repatch_notice),
            subtitle = stringResource(R.string.settings_appearance_repatch_notice_description),
            icon = Icons.Outlined.AutoFixHigh,
            checked = showRepatchNotice,
            onToggle = onRepatchNoticeToggle
        )
        SettingsDivider()
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_sort_button),
            subtitle = stringResource(R.string.settings_appearance_sort_button_description),
            icon = Icons.AutoMirrored.Outlined.Sort,
            checked = showSortButton,
            onToggle = onSortButtonToggle
        )
        SettingsDivider()
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_app_grouping),
            subtitle = stringResource(R.string.settings_appearance_app_grouping_description),
            icon = Icons.Outlined.ViewAgenda,
            checked = showAppGrouping,
            onToggle = onAppGroupingToggle
        )
    }
}

/**
 * [SectionTitle] with the spacing every section on this tab uses.
 */
@Composable
private fun SectionHeader(text: String, icon: ImageVector) {
    Box(Modifier.padding(bottom = Defaults.ContentPadding).fillMaxWidth()) {
        SectionTitle(text = text, icon = icon)
    }
}

