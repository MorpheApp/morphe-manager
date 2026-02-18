/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.util.Log
import app.morphe.manager.BuildConfig
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FCM topic for stable releases (published from the `main` branch).
 * Devices that do NOT use prereleases subscribe to this topic.
 */
const val FCM_TOPIC_STABLE = "morphe_updates"

/**
 * FCM topic for prerelease builds (published from the `dev` branch).
 * Devices running a dev build OR with prereleases enabled subscribe to this topic.
 */
const val FCM_TOPIC_DEV = "morphe_updates_dev"

/**
 * Returns `true` if the currently installed manager is itself a dev/prerelease build.
 *
 * Detection is based on [BuildConfig.VERSION_NAME]: versions produced by
 * `multi-semantic-release` on the `dev` branch always contain a pre-release
 * identifier (e.g. `1.2.3-dev.1`, `1.2.3-alpha.2`, `1.2.3-beta.1`).
 * Stable releases on `main` produce a clean semver like `1.2.3`.
 *
 * This is intentionally separate from the user's "Use prereleases" preference -
 * a device running a dev build should always receive dev notifications regardless
 * of what the user has toggled in Settings.
 */
val isDevBuild: Boolean
    get() = BuildConfig.VERSION_NAME.contains('-')

/**
 * Synchronises the device's FCM topic subscriptions with the current state.
 *
 * The effective topic is determined by combining two signals:
 * - [isDevBuild]    - whether the installed manager is itself a dev/prerelease build
 * - [usePrereleases] - the user's "Use prereleases" preference in Settings
 *
 * Rules:
 * - Notifications OFF → unsubscribe from both topics.
 * - Notifications ON + (dev build OR prereleases enabled) → subscribe to [FCM_TOPIC_DEV],
 *   unsubscribe from [FCM_TOPIC_STABLE].
 * - Notifications ON + stable build AND prereleases disabled → subscribe to [FCM_TOPIC_STABLE],
 *   unsubscribe from [FCM_TOPIC_DEV].
 *
 * This ensures a device is always subscribed to exactly one topic (or none), so it
 * never receives duplicate notifications.
 *
 * Safe to call multiple times - FCM deduplicates subscribe/unsubscribe calls internally.
 *
 * Called from:
 * - [app.morphe.manager.ManagerApplication] on every cold start
 * - [app.morphe.manager.ui.screen.settings.advanced.UpdatesSettingsItem] on preference toggle
 */
fun syncFcmTopics(notificationsEnabled: Boolean, usePrereleases: Boolean) {
    val tag = "FcmTopicSync"
    val messaging = FirebaseMessaging.getInstance()

    if (!notificationsEnabled) {
        messaging.unsubscribeFromTopic(FCM_TOPIC_STABLE)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_STABLE") }
        messaging.unsubscribeFromTopic(FCM_TOPIC_DEV)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_DEV") }
        return
    }

    // A device running a dev build should always track the dev topic - even if the
    // user hasn't explicitly enabled prereleases - because stable releases are not
    // relevant upgrades for a dev build.
    val useDevTopic = isDevBuild || usePrereleases

    Log.d(tag, "syncFcmTopics: isDevBuild=$isDevBuild, usePrereleases=$usePrereleases → topic=${if (useDevTopic) FCM_TOPIC_DEV else FCM_TOPIC_STABLE}")

    if (useDevTopic) {
        messaging.subscribeToTopic(FCM_TOPIC_DEV)
            .addOnCompleteListener { task ->
                Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_DEV" else "Failed to subscribe to $FCM_TOPIC_DEV")
            }
        messaging.unsubscribeFromTopic(FCM_TOPIC_STABLE)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_STABLE") }
    } else {
        messaging.subscribeToTopic(FCM_TOPIC_STABLE)
            .addOnCompleteListener { task ->
                Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_STABLE" else "Failed to subscribe to $FCM_TOPIC_STABLE")
            }
        messaging.unsubscribeFromTopic(FCM_TOPIC_DEV)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_DEV") }
    }
}
