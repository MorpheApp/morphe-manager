package app.revanced.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import app.revanced.manager.domain.manager.PreferencesManager

class MainViewModel(
    val prefs: PreferencesManager
) : ViewModel()
