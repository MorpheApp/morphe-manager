/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import android.app.Application
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.util.hasGooglePlayServices
import app.morphe.manager.util.syncFcmTopics
import app.morphe.manager.worker.UpdateCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Prompts offered once a patched app is installed or saved: the notification permission,
 * then the onboarding tour on first launch.
 * Owned by the ViewModel of whichever screen finished the patch, so the single-app and the
 * batch flows ask the same questions in the same order.
 */
class PostPatchPrompts(
    private val app: Application,
    private val prefs: PreferencesManager,
    private val scope: CoroutineScope
) {
    private val hasGms = app.hasGooglePlayServices()

    private val _notification = MutableStateFlow(false)

    /** True while the notification permission dialog should be on screen. */
    val notification: StateFlow<Boolean> = _notification.asStateFlow()

    private val _tour = MutableStateFlow(false)

    /** True while the tour dialog should be on screen. Raised only once [notification] clears, so the two never overlap. */
    val tour: StateFlow<Boolean> = _tour.asStateFlow()

    private var pending: Job? = null

    /**
     * Raises whichever prompts are still due. Repeated calls while one is pending are ignored,
     * so a batch that installs and saves several apps asks only once.
     */
    fun trigger() {
        if (pending?.isActive == true) return
        pending = scope.launch {
            val needsNotification = !prefs.notificationPermissionRequested.get() &&
                    !prefs.backgroundUpdateNotifications.get()
            val needsTour = prefs.firstLaunch.get()

            if (needsNotification) _notification.value = true
            if (needsTour) {
                _notification.first { !it }
                _tour.value = true
                _tour.first { !it }
            }
        }
    }

    /**
     * Records the user's answer to the notification dialog, dismissal included, and sets up
     * FCM topics or the update worker when notifications were granted.
     */
    fun onNotificationResult(granted: Boolean) {
        scope.launch {
            prefs.notificationPermissionRequested.update(true)
            if (granted) {
                prefs.backgroundUpdateNotifications.update(true)
                val useManagerPrereleases = prefs.useManagerPrereleases.get()
                val usePatchesPrereleases = prefs.bundlePrereleasesEnabled.get()
                    .contains(DEFAULT_SOURCE_UID.toString())
                syncFcmTopics(
                    notificationsEnabled = true,
                    useManagerPrereleases = useManagerPrereleases,
                    usePatchesPrereleases = usePatchesPrereleases
                )
                if (!hasGms) UpdateCheckWorker.schedule(app, prefs.updateCheckInterval.get())
            }
        }
        _notification.value = false
    }

    fun consumeTour() {
        _tour.value = false
    }
}
