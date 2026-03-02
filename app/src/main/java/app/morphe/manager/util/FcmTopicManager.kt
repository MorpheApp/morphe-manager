/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FCM topic for stable manager releases (published from the `main` branch).
 *
 * Subscribed when notifications are ON AND prereleases are OFF.
 * Dev builds also subscribe to this topic when prereleases are OFF — a stable
 * release (e.g. `1.5.0`) is a valid upgrade from a dev build (e.g. `1.5.0-dev.1`).
 * See [syncFcmTopics] for the full subscription matrix.
 */
const val FCM_TOPIC_MANAGER_STABLE = "morphe_updates"

/**
 * FCM topic for prerelease manager builds (published from the `dev` branch).
 *
 * Routing rule: subscribe when notifications are ON AND (the installed manager
 * is a dev build OR the user has enabled prereleases).
 */
const val FCM_TOPIC_MANAGER_DEV = "morphe_updates_dev"

/**
 * FCM topic for stable patch bundle releases.
 *
 * Routing rule: subscribe when notifications are ON AND the user has NOT
 * enabled prereleases. The installed manager build variant does NOT affect
 * this subscription - a dev manager can still use stable patches.
 */
const val FCM_TOPIC_PATCHES_STABLE = "morphe_patches_updates"

/**
 * FCM topic for prerelease patch bundle releases.
 *
 * Routing rule: subscribe when notifications are ON AND the user HAS
 * enabled prereleases.
 */
const val FCM_TOPIC_PATCHES_DEV = "morphe_patches_updates_dev"

/**
 * Synchronises **all four** FCM topic subscriptions with the user's current preferences.
 *
 * Stable topics are always subscribed — a stable release is always a valid upgrade,
 * even for prerelease users (`1.0.2-dev.1 → 1.0.2`). Dev topics are subscribed
 * only when the user has explicitly enabled prereleases.
 *
 * | Parameter               | stable topic | dev topic    |
 * |-------------------------|--------------|--------------|
 * | useManagerPrereleases   | ✓ always     | if true only |
 * | usePatchesPrereleases   | ✓ always     | if true only |
 *
 * When [notificationsEnabled] is false, unsubscribes from all four topics.
 *
 * Custom third-party bundles do not have FCM topics — only the built-in Morphe bundle does.
 *
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
    val tag = "FcmTopicSync"
    val messaging = FirebaseMessaging.getInstance()

    if (!notificationsEnabled) {
        listOf(
            FCM_TOPIC_MANAGER_STABLE,
            FCM_TOPIC_MANAGER_DEV,
            FCM_TOPIC_PATCHES_STABLE,
            FCM_TOPIC_PATCHES_DEV,
        ).forEach { topic ->
            messaging.unsubscribeFromTopic(topic)
                .addOnCompleteListener { Log.d(tag, "Unsubscribed from $topic") }
        }
        return
    }

    // Stable releases are always a valid upgrade for all users
    // Dev releases are subscribed only if the user has explicitly enabled prereleases
    Log.d(tag, "syncFcmTopics: useManagerPrereleases=$useManagerPrereleases, usePatchesPrereleases=$usePatchesPrereleases")
    Log.d(tag, "  manager stable=true, manager dev=$useManagerPrereleases")
    Log.d(tag, "  patches stable=true, patches dev=$usePatchesPrereleases")

    messaging.subscribeToTopic(FCM_TOPIC_MANAGER_STABLE)
        .addOnCompleteListener { task ->
            Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_MANAGER_STABLE" else "Failed to subscribe to $FCM_TOPIC_MANAGER_STABLE")
        }

    if (useManagerPrereleases) {
        messaging.subscribeToTopic(FCM_TOPIC_MANAGER_DEV)
            .addOnCompleteListener { task ->
                Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_MANAGER_DEV" else "Failed to subscribe to $FCM_TOPIC_MANAGER_DEV")
            }
    } else {
        messaging.unsubscribeFromTopic(FCM_TOPIC_MANAGER_DEV)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_MANAGER_DEV") }
    }

    // Stable patches are always relevant
    // Dev patches are subscribed only if the user has explicitly enabled prereleases
    messaging.subscribeToTopic(FCM_TOPIC_PATCHES_STABLE)
        .addOnCompleteListener { task ->
            Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_PATCHES_STABLE" else "Failed to subscribe to $FCM_TOPIC_PATCHES_STABLE")
        }

    if (usePatchesPrereleases) {
        messaging.subscribeToTopic(FCM_TOPIC_PATCHES_DEV)
            .addOnCompleteListener { task ->
                Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_PATCHES_DEV" else "Failed to subscribe to $FCM_TOPIC_PATCHES_DEV")
            }
    } else {
        messaging.unsubscribeFromTopic(FCM_TOPIC_PATCHES_DEV)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_PATCHES_DEV") }
    }
}
