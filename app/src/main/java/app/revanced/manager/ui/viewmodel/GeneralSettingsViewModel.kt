package app.revanced.manager.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.util.applyAppLanguage
import app.revanced.manager.util.resetListItemColorsCached
import app.revanced.manager.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemePreset {
    DEFAULT,
    LIGHT,
    DARK,
    DYNAMIC,
    PURE_BLACK
}

private data class ThemePresetConfig(
    val theme: Theme,
    val dynamicColor: Boolean = false,
    val pureBlackTheme: Boolean = false,
    val customAccentHex: String = "",
    val customThemeHex: String = ""
)

class GeneralSettingsViewModel(
    val prefs: PreferencesManager
) : ViewModel() {
    fun setTheme(theme: Theme) = viewModelScope.launch {
        prefs.theme.update(theme)
        resetListItemColorsCached()
    }

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
        ),
        ThemePreset.PURE_BLACK to ThemePresetConfig(
            theme = Theme.DARK,
            pureBlackTheme = true
        )
    )

    fun resetThemeSettings() = viewModelScope.launch {
        prefs.theme.update(Theme.SYSTEM)
        prefs.dynamicColor.update(true)
        prefs.pureBlackTheme.update(false)
        prefs.themePresetSelectionEnabled.update(true)
        prefs.themePresetSelectionName.update(ThemePreset.DEFAULT.name)
        prefs.customAccentColor.update("")
        prefs.customThemeColor.update("")
        resetListItemColorsCached()
    }

    fun setCustomAccentColor(color: Color?) = viewModelScope.launch {
        val value = color?.toHexString().orEmpty()
        prefs.customAccentColor.update(value)
        resetListItemColorsCached()
    }

    fun setCustomThemeColor(color: Color?) = viewModelScope.launch {
        val value = color?.toHexString().orEmpty()
        prefs.customThemeColor.update(value)
        resetListItemColorsCached()
    }

    fun setAppLanguage(languageCode: String) = viewModelScope.launch {
        prefs.appLanguage.update(languageCode)
        withContext(Dispatchers.Main) {
            applyAppLanguage(languageCode)
        }
    }

    fun toggleThemePreset(preset: ThemePreset) = viewModelScope.launch {
        val current = getCurrentThemePreset()
        if (current == preset) {
            val resetTheme = if (preset == ThemePreset.LIGHT) Theme.SYSTEM else null
            clearThemePresetSelection(resetTheme)
        } else {
            applyThemePreset(preset)
        }
    }

    private suspend fun applyThemePreset(preset: ThemePreset) {
        val config = presetConfigs[preset] ?: return
        prefs.themePresetSelectionEnabled.update(true)
        prefs.theme.update(config.theme)
        prefs.dynamicColor.update(config.dynamicColor)
        prefs.pureBlackTheme.update(config.pureBlackTheme)
        prefs.customAccentColor.update(config.customAccentHex)
        prefs.customThemeColor.update(config.customThemeHex)
        prefs.themePresetSelectionName.update(preset.name)
        resetListItemColorsCached()
    }

    private suspend fun clearThemePresetSelection(resetTheme: Theme? = null) {
        prefs.themePresetSelectionEnabled.update(false)
        prefs.themePresetSelectionName.update("")
        prefs.dynamicColor.update(false)
        prefs.pureBlackTheme.update(false)
        resetTheme?.let { prefs.theme.update(it) }
    }

    private suspend fun getCurrentThemePreset(): ThemePreset? {
        if (!prefs.themePresetSelectionEnabled.get()) return null
        val storedName = prefs.themePresetSelectionName.get().takeIf { it.isNotBlank() }
        return storedName?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() }
    }

    fun togglePatchesPrerelease(usePrerelease: Boolean) = viewModelScope.launch {
        prefs.usePatchesPrereleases.update(usePrerelease)
    }
}
