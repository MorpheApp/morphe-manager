/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FCM topic strings used for update push notifications.
 *
 * Manager and patches topics are independent — each has a stable/dev pair:
 *
 * | Topic            | Audience                                      |
 * |------------------|-----------------------------------------------|
 * | [MANAGER_STABLE] | Manager stable releases (`main` branch)       |
 * | [MANAGER_DEV]    | Manager prerelease builds (`dev` branch)      |
 * | [PATCHES_STABLE] | Patch bundle stable releases                  |
 * | [PATCHES_DEV]    | Patch bundle prerelease releases              |
 *
 * Stable topics are always subscribed when notifications are enabled —
 * a stable release (e.g. `1.5.0`) is always a valid upgrade from a dev
 * build (e.g. `1.5.0-dev.1`). Dev topics require explicit opt-in.
 */
object FcmTopics {
    const val MANAGER_STABLE = "morphe_updates"
    const val MANAGER_DEV    = "morphe_updates_dev"
    const val PATCHES_STABLE = "morphe_patches_updates"
    const val PATCHES_DEV    = "morphe_patches_updates_dev"

    val all = listOf(MANAGER_STABLE, MANAGER_DEV, PATCHES_STABLE, PATCHES_DEV)
}

/**
 * Synchronises all four FCM topic subscriptions with the user's current preferences.
 *
 * | Parameter             | stable topic | dev topic    |
 * |-----------------------|--------------|--------------|
 * | useManagerPrereleases | ✓ always     | if true only |
 * | usePatchesPrereleases | ✓ always     | if true only |
 *
 * When [notificationsEnabled] is false, unsubscribes from all four topics.
 *
 * Custom third-party bundles do not have FCM topics — only the built-in Morphe bundle does.
 * Safe to call multiple times — FCM deduplicates subscribe/unsubscribe internally.
 *
 * Called from:
 * - [app.morphe.manager.ManagerApplication] on every cold start
 * - [app.morphe.manager.ui.screen.settings.advanced.UpdatesSettingsItem] on preference toggle
 */
fun syncFcmTopics(
    notificationsEnabled: Boolean,
    useManagerPrereleases: Boolean,
    usePatchesPrereleases: Boolean = false,
) {
    val messaging = FirebaseMessaging.getInstance()

    if (!notificationsEnabled) {
        FcmTopics.all.forEach { topic ->
            messaging.unsubscribeFromTopic(topic)
                .addOnCompleteListener { Log.d("FcmTopicSync", "Unsubscribed from $topic") }
        }
        return
    }

    // Stable topics: always subscribed when notifications are enabled
    // Dev topics: only when user has explicitly enabled prereleases
    messaging.syncTopic(FcmTopics.MANAGER_STABLE, subscribe = true)
    messaging.syncTopic(FcmTopics.MANAGER_DEV,    subscribe = useManagerPrereleases)
    messaging.syncTopic(FcmTopics.PATCHES_STABLE, subscribe = true)
    messaging.syncTopic(FcmTopics.PATCHES_DEV,    subscribe = usePatchesPrereleases)
}

/**
 * Subscribes to or unsubscribes from a single FCM topic and logs the result.
 */
private fun FirebaseMessaging.syncTopic(topic: String, subscribe: Boolean) {
    val tag = "FcmTopicSync"
    if (subscribe) {
        subscribeToTopic(topic).addOnCompleteListener { task ->
            Log.d(tag, if (task.isSuccessful) "Subscribed to $topic" else "Failed to subscribe to $topic")
        }
    } else {
        unsubscribeFromTopic(topic).addOnCompleteListener {
            Log.d(tag, "Unsubscribed from $topic")
        }
    }
}
