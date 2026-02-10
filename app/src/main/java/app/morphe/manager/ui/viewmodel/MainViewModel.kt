package app.morphe.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import app.morphe.manager.domain.manager.PreferencesManager

class MainViewModel(
    val prefs: PreferencesManager
) : ViewModel()
