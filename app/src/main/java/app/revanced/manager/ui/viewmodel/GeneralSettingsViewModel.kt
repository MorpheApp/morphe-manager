package app.revanced.manager.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.theme.Theme
import app.revanced.manager.util.resetListItemColorsCached
import app.revanced.manager.util.toHexString
import kotlinx.coroutines.launch

class GeneralSettingsViewModel(
    val prefs: PreferencesManager
) : ViewModel() {
    fun setTheme(theme: Theme) = viewModelScope.launch {
        prefs.theme.update(theme)
        resetListItemColorsCached()
    }

    fun resetThemeSettings() = viewModelScope.launch {
        prefs.theme.update(Theme.SYSTEM)
        prefs.dynamicColor.update(true)
        prefs.pureBlackTheme.update(false)
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

    fun togglePatchesPrerelease(usePrerelease: Boolean) = viewModelScope.launch {
        prefs.usePatchesPrereleases.update(usePrerelease)
        prefs.patchesBundleJsonUrl.update(
            PreferencesManager.PatchBundleConstants.getBundleUrl(usePrerelease)
        )
    }

    fun toggleRootMode(enabled: Boolean) = viewModelScope.launch {
        prefs.useRootMode.update(enabled)
    }
}
