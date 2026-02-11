package app.morphe.manager.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.util.applyAppLanguage
import app.morphe.manager.util.resetListItemColorsCached
import app.morphe.manager.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemePreset {
    DEFAULT,
    LIGHT,
    DARK,
    DYNAMIC
}

private data class ThemePresetConfig(
    val theme: Theme,
    val dynamicColor: Boolean = false,
    val customAccentHex: String = "",
    val customThemeHex: String = ""
)

class ThemeSettingsViewModel(
    val prefs: PreferencesManager
) : ViewModel() {
    private val presetConfigs = mapOf(
        ThemePreset.DEFAULT to ThemePresetConfig(
            theme = Theme.SYSTEM
        ),
        ThemePreset.LIGHT to ThemePresetConfig(
            theme = Theme.LIGHT
        ),
        ThemePreset.DARK to ThemePresetConfig(
            theme = Theme.DARK
        ),
        ThemePreset.DYNAMIC to ThemePresetConfig(
            theme = Theme.SYSTEM,
            dynamicColor = true
        )
    )

    fun setCustomAccentColor(color: Color?) = viewModelScope.launch {
        val value = color?.toHexString().orEmpty()
        prefs.customAccentColor.update(value)
        resetListItemColorsCached()
    }

    fun setAppLanguage(languageCode: String) = viewModelScope.launch {
        prefs.appLanguage.update(languageCode)
        withContext(Dispatchers.Main) {
            applyAppLanguage(languageCode)
        }
    }

    fun applyThemePreset(preset: ThemePreset) = viewModelScope.launch {
        val config = presetConfigs[preset] ?: return@launch
        prefs.themePresetSelectionEnabled.update(true)
        prefs.theme.update(config.theme)
        prefs.dynamicColor.update(config.dynamicColor)

        // Pure Black should be disabled for incompatible themes
        if (preset == ThemePreset.LIGHT) {
            prefs.pureBlackTheme.update(false)
        }

        // Only reset colors for DYNAMIC preset, preserve for others
        if (preset == ThemePreset.DYNAMIC) {
            prefs.customAccentColor.update("")
            prefs.customThemeColor.update("")
        }
        // For other presets, keep existing custom colors

        prefs.themePresetSelectionName.update(preset.name)
        resetListItemColorsCached()
    }
}
