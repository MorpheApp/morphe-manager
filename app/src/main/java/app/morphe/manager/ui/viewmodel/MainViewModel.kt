package app.morphe.manager.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.morphe.manager.domain.manager.PreferencesManager

class MainViewModel(
    val prefs: PreferencesManager
) : ViewModel() {

    /**
     * Set by [app.morphe.manager.MainActivity.handleUpdateCheckIntent] when the user taps
     * an FCM update notification. HomeScreen observes this via LaunchedEffect, triggers
     * updateCheck(), then resets the flag back to false.
     */
    var triggerUpdateCheckOnResume by mutableStateOf(false)

    /**
     * Set by [app.morphe.manager.MainActivity.handleDeepLinkIntent] when the app is opened
     * via a deep link to add a patch bundle. HomeScreen observes this via LaunchedEffect,
     * shows a confirmation dialog, then resets the flag to null.
     */
    var pendingDeepLinkBundle: DeepLinkBundle? by mutableStateOf(null)

    data class DeepLinkBundle(val url: String, val name: String?)
}
