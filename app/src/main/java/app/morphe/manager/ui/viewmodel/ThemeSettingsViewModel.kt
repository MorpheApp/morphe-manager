/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/ui/viewmodel/GeneralSettingsViewModel.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
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
import app.morphe.manager.util.AppLocale
import app.morphe.manager.util.toHexString
import kotlinx.coroutines.launch

/**
 * How often the random background rotates.
 * [ON_LAUNCH] picks a new background every time the app is opened.
 * [DAILY] keeps the same background for the local calendar day.
 * [EVERY_3_DAYS] keeps it for three local calendar days.
 */
enum class RandomInterval(val labelResId: Int) {
    ON_LAUNCH(R.string.settings_appearance_background_random_interval_launch),
    DAILY(R.string.settings_appearance_background_random_interval_daily),
    EVERY_3_DAYS(R.string.settings_appearance_background_random_interval_3days)
}

class ThemeSettingsViewModel(
    private val app: Application,
    val prefs: PreferencesManager
) : ViewModel() {
    fun setRandomInterval(interval: RandomInterval) = viewModelScope.launch {
        prefs.randomBackgroundInterval.update(interval)
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
    fun setAppLanguage(languageCode: String) = AppLocale.select(app, languageCode)

    fun toggleShowGreetingPhrases(current: Boolean) = viewModelScope.launch {
        prefs.showGreetingPhrases.update(!current)
    }

    fun toggleShowRepatchNotice(current: Boolean) = viewModelScope.launch {
        prefs.showRepatchNotice.update(!current)
    }

    fun setPureBlackTheme(enabled: Boolean) = viewModelScope.launch {
        prefs.pureBlackTheme.update(enabled)
    }

    fun toggleColorAccents(current: Boolean) = viewModelScope.launch {
        prefs.colorAccents.update(!current)
    }

    fun toggleOutlines(current: Boolean) = viewModelScope.launch {
        prefs.outlines.update(!current)
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
    }

    fun toggleBackgroundParallax(current: Boolean) = viewModelScope.launch {
        prefs.enableBackgroundParallax.update(!current)
    }

    fun toggleSeasonalThemes(current: Boolean) = viewModelScope.launch {
        prefs.seasonalThemes.update(!current)
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
