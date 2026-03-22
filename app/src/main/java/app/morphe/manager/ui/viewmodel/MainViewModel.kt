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
     * Set by [app.morphe.manager.MainActivity.onNewIntent] when the user taps an FCM
     * update notification. HomeScreen observes this via LaunchedEffect, triggers
     * an update check, then resets the flag back to false.
     */
    var pendingUpdateCheck by mutableStateOf(false)

    /**
     * Set by [app.morphe.manager.MainActivity.handleDeepLinkIntent] when the app is opened
     * via a deep link to add a patch source. HomeScreen observes this via LaunchedEffect,
     * shows a confirmation dialog, then resets the flag to null.
     */
    var pendingDeepLinkSource: DeepLinkSource? by mutableStateOf(null)

    data class DeepLinkSource(val url: String, val name: String?)
}
