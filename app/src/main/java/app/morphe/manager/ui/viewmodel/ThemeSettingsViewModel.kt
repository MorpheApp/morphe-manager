package app.morphe.manager.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.BackgroundType
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.util.applyAppLanguage
import app.morphe.manager.util.toHexString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * How often the random background rotates.
 * [ON_LAUNCH] picks a new background every time the app is opened.
 * [DAILY] keeps the same background for the calendar day.
 * [EVERY_3_DAYS] rotates every 3 days based on epoch day.
 */
enum class RandomInterval(val labelResId: Int) {
    ON_LAUNCH(R.string.settings_appearance_background_random_interval_launch),
    DAILY(R.string.settings_appearance_background_random_interval_daily),
    EVERY_3_DAYS(R.string.settings_appearance_background_random_interval_3days)
}

class ThemeSettingsViewModel(
    val prefs: PreferencesManager
) : ViewModel() {
    /**
     * The currently resolved background for this session when RANDOM mode is active.
     * Populated by [resolveRandomBackground]; null until first resolution.
     */
    private val _resolvedRandomBackground = MutableStateFlow<BackgroundType?>(null)
    val resolvedRandomBackground: StateFlow<BackgroundType?> = _resolvedRandomBackground.asStateFlow()

    /**
     * Resolves the effective background type when [BackgroundType.RANDOM] is selected.
     * Called once on app start and again whenever the interval preference changes.
     *
     * - [RandomInterval.ON_LAUNCH] — picks a new random type each time.
     * - [RandomInterval.DAILY] — uses today's epoch day as a stable index.
     * - [RandomInterval.EVERY_3_DAYS] — uses epoch day ÷ 3 as a stable index.
     */
    fun resolveRandomBackground(interval: RandomInterval) {
        val pool = BackgroundType.RANDOMIZABLE
        _resolvedRandomBackground.value = when (interval) {
            RandomInterval.ON_LAUNCH -> pool.random()
            RandomInterval.DAILY -> {
                val dayIndex = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
                pool[(dayIndex % pool.size).toInt()]
            }
            RandomInterval.EVERY_3_DAYS -> {
                val periodIndex = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()) / 3
                pool[(periodIndex % pool.size).toInt()]
            }
        }
    }

    fun setRandomInterval(interval: RandomInterval) = viewModelScope.launch {
        prefs.randomBackgroundInterval.update(interval)
        resolveRandomBackground(interval)
    }

    fun setCustomAccentColor(color: Color?) = viewModelScope.launch {
        val value = color?.toHexString().orEmpty()
        prefs.customAccentColor.update(value)
    }

    /**
     * Change the app language.
     */
    fun setAppLanguage(languageCode: String) = viewModelScope.launch {
        prefs.appLanguage.update(languageCode)
        // Apply immediately on the calling coroutine - setApplicationLocales posts
        // internally to the main thread and is safe to call from any thread
        applyAppLanguage(languageCode)
    }

    fun toggleShowGreetingPhrases(current: Boolean) = viewModelScope.launch {
        prefs.showGreetingPhrases.update(!current)
    }

    fun togglePureBlackTheme(current: Boolean) = viewModelScope.launch {
        prefs.pureBlackTheme.update(!current)
    }

    fun setBackgroundType(type: BackgroundType) = viewModelScope.launch {
        prefs.backgroundType.update(type)
    }

    fun toggleBackgroundParallax(current: Boolean) = viewModelScope.launch {
        prefs.enableBackgroundParallax.update(!current)
    }

    fun applyThemeSelectionByKey(key: String) {
        when (key) {
            "SYSTEM" -> setThemeMode(Theme.SYSTEM)
            "LIGHT" -> setThemeMode(Theme.LIGHT)
            "DARK" -> setThemeMode(Theme.DARK)
            "DYNAMIC" -> setThemeStyle(ThemeStyle.MATERIAL_YOU)
            "MONOCHROME" -> setThemeStyle(ThemeStyle.MONOCHROME)
            else -> setThemeMode(Theme.SYSTEM)
        }
    }

    fun setThemeMode(theme: Theme) = viewModelScope.launch {
        prefs.themePresetSelectionEnabled.update(true)
        if (prefs.theme.get() == Theme.MONOCHROME) {
            prefs.themeStyle.update(ThemeStyle.MONOCHROME)
            prefs.dynamicColor.update(false)
        }
        prefs.theme.update(theme)

        if (theme == Theme.LIGHT) {
            prefs.pureBlackTheme.update(false)
        }

        prefs.themePresetSelectionName.update(theme.name)
    }

    fun setThemeStyle(style: ThemeStyle) = viewModelScope.launch {
        prefs.themePresetSelectionEnabled.update(true)
        prefs.themeStyle.update(style)
        prefs.dynamicColor.update(style == ThemeStyle.MATERIAL_YOU)

        if (prefs.theme.get() == Theme.MONOCHROME) {
            prefs.theme.update(Theme.SYSTEM)
        }

        if (style == ThemeStyle.MATERIAL_YOU) {
            prefs.customAccentColor.update("")
            prefs.customThemeColor.update("")
        }

        prefs.themePresetSelectionName.update(style.name)
    }
}
