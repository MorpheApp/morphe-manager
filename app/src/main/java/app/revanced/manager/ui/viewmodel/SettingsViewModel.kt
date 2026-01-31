package app.revanced.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.manager.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(
    val prefs: PreferencesManager,
    private val installerManager: InstallerManager
) : ViewModel() {
    fun setPrimaryInstaller(token: InstallerManager.Token) = viewModelScope.launch(Dispatchers.Default) {
        installerManager.updatePrimaryToken(token)
    }
}
