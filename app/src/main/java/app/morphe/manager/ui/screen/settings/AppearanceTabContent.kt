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
import app.morphe.manager.ui.screen.shared.LanguageRepository.getLanguageDisplayName
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.ui.theme.resolveThemeStyle
import app.morphe.manager.ui.viewmodel.RandomInterval
import app.morphe.manager.ui.viewmodel.ThemeSettingsViewModel
import app.morphe.manager.util.AppCardColorDefaults
import app.morphe.manager.util.AppCardColorMode
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
    val appLanguage by themeViewModel.prefs.appLanguage.getAsState()
    val showGreetingPhrases by themeViewModel.prefs.showGreetingPhrases.getAsState()
    val appCardColorMode by themeViewModel.prefs.appCardColorMode.getAsState()
    val customAppCardColors by themeViewModel.prefs.customAppCardColors.getAsState()
    val showAppGroupingSwitcher by homeAppButtonPrefs.showCategoryViewSwitcher.collectAsStateWithLifecycle()
    val showSortButton by homeAppButtonPrefs.showSortButton.collectAsStateWithLifecycle()
    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()
    val enableParallax by themeViewModel.prefs.enableBackgroundParallax.getAsState()
    val randomInterval by themeViewModel.prefs.randomBackgroundInterval.getAsState()
    val effectiveThemeStyle = resolveThemeStyle(themeStyle, supportsDynamicColor)
    val showAppCardColorSetting = effectiveThemeStyle != ThemeStyle.MONOCHROME

    val showLanguageDialog = remember { mutableStateOf(false) }
    val showTranslationInfoDialog = remember { mutableStateOf(false) }
    val showAppCardColorDialog = remember { mutableStateOf(false) }
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

    val contentPadding = rememberWindowSize().contentPadding
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = contentPadding, vertical = MorpheDefaults.ContentPadding)
    ) {
        LanguageSection(
            appLanguage = appLanguage,
            onLanguageClick = { showTranslationInfoDialog.value = true }
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
            showSortButton = showSortButton,
            showAppGrouping = showAppGroupingSwitcher,
            onGreetingPhrasesToggle = { themeViewModel.toggleShowGreetingPhrases(showGreetingPhrases) },
            onSortButtonToggle = { homeAppButtonPrefs.setShowSortButton(!showSortButton) },
            onAppGroupingToggle = { homeAppButtonPrefs.setShowCategoryViewSwitcher(!showAppGroupingSwitcher) }
        )

        BackgroundSection(
            backgroundType = backgroundType,
            randomInterval = randomInterval,
            enableParallax = enableParallax,
            onBackgroundSelected = themeViewModel::setBackgroundType,
            onIntervalSelected = themeViewModel::setRandomInterval,
            onParallaxToggle = { themeViewModel.toggleBackgroundParallax(enableParallax) }
        )

        AppIconSection()
    }

    // App card color dialog
    AnimatedVisibility(
        visible = showAppCardColorDialog.value && showAppCardColorSetting,
        enter = MorpheAnimations.fadeIn,
        exit = MorpheAnimations.fadeOut
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

        SettingsGroup(modifier = Modifier.padding(bottom = MorpheDefaults.ContentPadding)) {
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
                            fontSize = 20.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            )
        }
    }
}

/**
 * Theme mode, color style and the pure black toggle.
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
    onSelectorPositioned: ((Rect) -> Unit)?,
    onSelectorScrollTarget: ((Int) -> Unit)?
) {
    SectionHeader(
        text = stringResource(R.string.settings_appearance_theme),
        icon = Icons.Outlined.Palette
    )

    Box(
        Modifier
            .padding(bottom = MorpheDefaults.ContentPadding)
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
            onThemeSelected = onThemeSelected
        )
    }

    Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
        ThemeStyleSelector(
            style = themeStyle,
            supportsDynamicColor = supportsDynamicColor,
            onStyleSelected = onStyleSelected
        )
    }

    AnimatedVisibility(
        visible = supportsPureBlack,
        enter = MorpheAnimations.expandFadeEnter,
        exit = MorpheAnimations.shrinkFadeExit
    ) {
        SettingsGroup(modifier = Modifier.padding(bottom = MorpheDefaults.ContentPadding)) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_appearance_pure_black),
                subtitle = stringResource(R.string.settings_appearance_pure_black_description),
                icon = Icons.Outlined.Contrast,
                checked = pureBlackTheme,
                onToggle = onPureBlackToggle
            )
        }
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
    AnimatedVisibility(
        visible = themeStyle != ThemeStyle.MATERIAL_YOU,
        enter = MorpheAnimations.expandFadeEnter,
        exit = MorpheAnimations.shrinkFadeExit
    ) {
        Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
            AccentColorSelector(
                selectedColorHex = accentColorHex,
                onColorSelected = onAccentSelected,
                dynamicColorEnabled = themeStyle == ThemeStyle.MATERIAL_YOU
            )
        }
    }

    AnimatedVisibility(
        visible = showAppCardColors,
        enter = MorpheAnimations.expandFadeEnter,
        exit = MorpheAnimations.shrinkFadeExit
    ) {
        SettingsGroup(modifier = Modifier.padding(bottom = MorpheDefaults.ContentPadding)) {
            SettingsItem(
                onClick = onAppCardColorsClick,
                title = stringResource(R.string.settings_appearance_app_card_colors),
                subtitle = stringResource(appCardColorMode.descriptionResId),
                leadingContent = { MorpheIcon(icon = Icons.Outlined.Style) }
            )
        }
    }
}

/**
 * Toggles for what the home screen shows.
 */
@Composable
private fun HomeScreenSection(
    showGreetingPhrases: Boolean,
    showSortButton: Boolean,
    showAppGrouping: Boolean,
    onGreetingPhrasesToggle: () -> Unit,
    onSortButtonToggle: () -> Unit,
    onAppGroupingToggle: () -> Unit
) {
    SectionHeader(
        text = stringResource(R.string.settings_appearance_home_screen),
        icon = Icons.Outlined.Dashboard
    )

    SettingsGroup(modifier = Modifier.padding(bottom = MorpheDefaults.ContentPadding)) {
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_greeting_phrases),
            subtitle = stringResource(R.string.settings_appearance_greeting_phrases_subtitle),
            icon = Icons.Outlined.ChatBubbleOutline,
            checked = showGreetingPhrases,
            onToggle = onGreetingPhrasesToggle
        )
        MorpheSettingsDivider()
        SettingsSwitchItem(
            title = stringResource(R.string.settings_appearance_sort_button),
            subtitle = stringResource(R.string.settings_appearance_sort_button_description),
            icon = Icons.AutoMirrored.Outlined.Sort,
            checked = showSortButton,
            onToggle = onSortButtonToggle
        )
        MorpheSettingsDivider()
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
 * Background picker and the parallax toggle it enables.
 */
@Composable
private fun BackgroundSection(
    backgroundType: BackgroundType,
    randomInterval: RandomInterval,
    enableParallax: Boolean,
    onBackgroundSelected: (BackgroundType) -> Unit,
    onIntervalSelected: (RandomInterval) -> Unit,
    onParallaxToggle: () -> Unit
) {
    SectionHeader(
        text = stringResource(R.string.settings_appearance_background),
        icon = Icons.Outlined.Wallpaper
    )

    Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
        BackgroundSelector(
            selectedBackground = backgroundType,
            onBackgroundSelected = onBackgroundSelected,
            selectedInterval = randomInterval,
            onIntervalSelected = onIntervalSelected
        )
    }

    AnimatedVisibility(
        visible = backgroundType != BackgroundType.NONE,
        enter = MorpheAnimations.expandFadeEnter,
        exit = MorpheAnimations.shrinkFadeExit
    ) {
        SettingsGroup(modifier = Modifier.padding(bottom = MorpheDefaults.ContentPadding)) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_appearance_parallax_effect),
                subtitle = stringResource(R.string.settings_appearance_parallax_effect_description),
                icon = Icons.Outlined.ScreenRotation,
                checked = enableParallax,
                onToggle = onParallaxToggle
            )
        }
    }
}

/**
 * Launcher icon picker.
 */
@Composable
private fun AppIconSection() {
    SectionHeader(
        text = stringResource(R.string.settings_appearance_app_icon_selector_title),
        icon = Icons.Outlined.Apps
    )

    AppIconSelector()
}

/**
 * [SectionTitle] with the spacing every section on this tab uses.
 */
@Composable
private fun SectionHeader(text: String, icon: ImageVector) {
    Box(Modifier.padding(bottom = MorpheDefaults.ContentPadding).fillMaxWidth()) {
        SectionTitle(text = text, icon = icon)
    }
}

