package app.morphe.manager.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.BackgroundType
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.ui.theme.coerceToUiScale
import app.morphe.manager.util.AppCardColorDefaults
import app.morphe.manager.util.AppCardColorMode
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
    suspend fun resolveRandomBackground(interval: RandomInterval) {
        val pool = BackgroundType.randomizable(prefs.matrixBackgroundUnlocked.get())
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
     * Persists the card color [mode] together with the picked colors. The colors are kept even
     * for [AppCardColorMode.DEFAULT] so switching modes back and forth does not discard them.
     */
    fun applyAppCardColors(
        mode: AppCardColorMode,
        startColorHex: String,
        middleColorHex: String,
        endColorHex: String,
        solidColorHex: String
    ) = viewModelScope.launch {
        prefs.edit {
            prefs.appCardColorMode.value = mode
            prefs.customAppCardColors.value = AppCardColorDefaults.encodeColorValues(
                startHex = startColorHex,
                middleHex = middleColorHex,
                endHex = endColorHex,
                solidHex = solidColorHex
            )
        }
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

    fun toggleShowRepatchNotice(current: Boolean) = viewModelScope.launch {
        prefs.showRepatchNotice.update(!current)
    }

    fun setPureBlackTheme(enabled: Boolean) = viewModelScope.launch {
        prefs.pureBlackTheme.update(enabled)
    }

    fun setBackgroundType(type: BackgroundType) = viewModelScope.launch {
        prefs.backgroundType.update(type)
    }

    /**
     * Takes the red pill: reveals the Matrix background in the picker and switches to it at once,
     * so the choice is answered by the screen itself rather than by a line in the settings.
     */
    fun unlockMatrixBackground() = viewModelScope.launch {
        prefs.edit {
            prefs.matrixBackgroundUnlocked.value = true
            prefs.backgroundType.value = BackgroundType.MATRIX
        }
    }

    /**
     * Takes the blue pill: hides the Matrix background away again. A background picked since is
     * left alone, and the gesture that revealed it in the first place still works.
     */
    fun forgetMatrixBackground() = viewModelScope.launch {
        prefs.edit {
            prefs.matrixBackgroundUnlocked.value = false
            if (prefs.backgroundType.value == BackgroundType.MATRIX) {
                prefs.backgroundType.value = BackgroundType.DEFAULT
            }
        }

        // A random rotation that had already landed on Matrix would keep it on screen until the
        // next resolve, so it is drawn again from the pool Matrix has just left
        if (_resolvedRandomBackground.value == BackgroundType.MATRIX) {
            resolveRandomBackground(prefs.randomBackgroundInterval.get())
        }
    }

    fun toggleBackgroundParallax(current: Boolean) = viewModelScope.launch {
        prefs.enableBackgroundParallax.update(!current)
    }

    fun setThemeMode(theme: Theme) = viewModelScope.launch {
        prefs.theme.update(theme)
        if (theme == Theme.LIGHT) {
            prefs.pureBlackTheme.update(false)
        }
    }

    fun setUiScale(scale: Float) = viewModelScope.launch {
        prefs.uiScale.update(scale.coerceToUiScale())
    }

    fun setThemeStyle(style: ThemeStyle) = viewModelScope.launch {
        prefs.themeStyle.update(style)
        // Dynamic color drives its own accent from the wallpaper, so custom overrides are cleared
        if (style == ThemeStyle.MATERIAL_YOU) {
            prefs.customAccentColor.update("")
            prefs.customThemeColor.update("")
        }
    }
}
