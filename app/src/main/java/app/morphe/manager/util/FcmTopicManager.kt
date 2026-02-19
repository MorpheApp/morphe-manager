/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.util.Log
import app.morphe.manager.BuildConfig
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
 * Returns `true` if the currently installed manager is itself a dev/prerelease build.
 *
 * Detection is based on [BuildConfig.VERSION_NAME]: versions produced by
 * `multi-semantic-release` on the `dev` branch always contain a pre-release
 * identifier (e.g. `1.2.3-dev.1`, `1.2.3-alpha.2`).
 * Stable releases on `main` produce a clean semver like `1.2.3`.
 */
val isDevBuild: Boolean
    get() = BuildConfig.VERSION_NAME.contains('-')

/**
 * Synchronises **all four** FCM topic subscriptions with the user's current preferences.
 *
 * ## Manager topics
 *
 * The manager subscription is determined by combining [isDevBuild] and [usePrereleases]:
 *
 * | isDevBuild | usePrereleases | stable topic | dev topic |
 * |------------|----------------|--------------|-----------|
 * | false      | false          | ✓ subscribed | ✗ unsub   |
 * | false      | true           | ✗ unsub      | ✓ subscribed |
 * | true       | false          | ✓ subscribed | ✓ subscribed |
 * | true       | true           | ✗ unsub      | ✓ subscribed |
 *
 * A dev-build user with prereleases OFF is subscribed to **both** manager topics because
 * a stable release (e.g. `1.5.0`) is a valid upgrade from a dev build (e.g. `1.5.0-dev.1`).
 * Enabling prereleases signals intent to stay on the cutting edge, so stable is unsubscribed.
 *
 * ## Patches topics
 *
 * The patches topic is chosen **only** by [usePrereleases], independent of [isDevBuild]:
 * - prereleases ON  → [FCM_TOPIC_PATCHES_DEV]
 * - prereleases OFF → [FCM_TOPIC_PATCHES_STABLE]
 *
 * This way a user running a dev manager build with prereleases OFF will still
 * receive stable patch notifications - dev manager does not imply dev patches.
 *
 * ## Notifications OFF
 *
 * Unsubscribes from all four topics.
 *
 * Safe to call multiple times - FCM deduplicates subscribe/unsubscribe internally.
 *
 * Called from:
 * - [app.morphe.manager.ManagerApplication] on every cold start
 * - [app.morphe.manager.ui.screen.settings.advanced.UpdatesSettingsItem] on preference toggle
 */
fun syncFcmTopics(notificationsEnabled: Boolean, usePrereleases: Boolean) {
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

    // Matrix:
    //   stable build + prereleases OFF → stable only      (normal stable user)
    //   stable build + prereleases ON  → dev only         (opted into prereleases)
    //   dev build    + prereleases OFF → stable + dev     (dev build, but stable 1.5.0 is a valid upgrade from 1.5.0-dev.1)
    //   dev build    + prereleases ON  → dev only         (wants to stay on cutting edge)
    val subscribeManagerStable = !usePrereleases  // stable is relevant unless user wants only prereleases
    val subscribeManagerDev    = isDevBuild || usePrereleases  // dev is relevant for dev builds and prerelease users

    Log.d(tag, "syncFcmTopics: isDevBuild=$isDevBuild, usePrereleases=$usePrereleases")
    Log.d(tag, "  manager stable=$subscribeManagerStable, manager dev=$subscribeManagerDev")
    Log.d(tag, "  patches topic → ${if (usePrereleases) FCM_TOPIC_PATCHES_DEV else FCM_TOPIC_PATCHES_STABLE}")

    if (subscribeManagerStable) {
        messaging.subscribeToTopic(FCM_TOPIC_MANAGER_STABLE)
            .addOnCompleteListener { task ->
                Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_MANAGER_STABLE" else "Failed to subscribe to $FCM_TOPIC_MANAGER_STABLE")
            }
    } else {
        messaging.unsubscribeFromTopic(FCM_TOPIC_MANAGER_STABLE)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_MANAGER_STABLE") }
    }

    if (subscribeManagerDev) {
        messaging.subscribeToTopic(FCM_TOPIC_MANAGER_DEV)
            .addOnCompleteListener { task ->
                Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_MANAGER_DEV" else "Failed to subscribe to $FCM_TOPIC_MANAGER_DEV")
            }
    } else {
        messaging.unsubscribeFromTopic(FCM_TOPIC_MANAGER_DEV)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_MANAGER_DEV") }
    }

    // Determined solely by usePrereleases - dev manager does not imply dev patches
    if (usePrereleases) {
        messaging.subscribeToTopic(FCM_TOPIC_PATCHES_DEV)
            .addOnCompleteListener { task ->
                Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_PATCHES_DEV" else "Failed to subscribe to $FCM_TOPIC_PATCHES_DEV")
            }
        messaging.unsubscribeFromTopic(FCM_TOPIC_PATCHES_STABLE)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_PATCHES_STABLE") }
    } else {
        messaging.subscribeToTopic(FCM_TOPIC_PATCHES_STABLE)
            .addOnCompleteListener { task ->
                Log.d(tag, if (task.isSuccessful) "Subscribed to $FCM_TOPIC_PATCHES_STABLE" else "Failed to subscribe to $FCM_TOPIC_PATCHES_STABLE")
            }
        messaging.unsubscribeFromTopic(FCM_TOPIC_PATCHES_DEV)
            .addOnCompleteListener { Log.d(tag, "Unsubscribed from $FCM_TOPIC_PATCHES_DEV") }
    }
}
