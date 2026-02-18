/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.service

import android.util.Log
import app.morphe.manager.util.UpdateNotificationManager
import app.morphe.manager.util.tag
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.inject

/**
 * Firebase Cloud Messaging service for Morphe Manager.
 *
 * High-priority FCM messages (sent with `"android": { "priority": "high" }`) are
 * delivered by Google Play Services even when the device is in Doze mode or the
 * app process is dead — unlike WorkManager, which can be suppressed by aggressive
 * vendor battery optimisations (Xiaomi, Huawei, Samsung, OnePlus, etc.).
 *
 * ## Two-topic model
 *
 * The server publishes to two separate topics after each release:
 *
 * | Topic                  | Branch  | Audience                        |
 * |------------------------|---------|---------------------------------|
 * | `morphe_updates`       | `main`  | Users with prereleases OFF      |
 * | `morphe_updates_dev`   | `dev`   | Users with prereleases ON       |
 * | *(neither)*            |   -     | Users with notifications OFF    |
 *
 * Subscription is managed by [app.morphe.manager.util.syncFcmTopics], which is called
 * whenever the user toggles "Background notifications" or "Use prereleases" in Settings,
 * and on every cold start in [app.morphe.manager.ManagerApplication].
 *
 * ## Message contract
 *
 * Every FCM message must include a `type` key in its `data` map:
 *
 * | type             | extra keys | action                                    |
 * |------------------|------------|-------------------------------------------|
 * | `manager_update` | `version`  | Calls [UpdateNotificationManager.showFcmManagerUpdateNotification] |
 * | `bundle_update`  |    -       | Calls [UpdateNotificationManager.showFcmBundleUpdateNotification]  |
 *
 * Unknown types are silently ignored for forward-compatibility with future message types.
 *
 * ## Token rotation
 *
 * [onNewToken] is called when FCM rotates the registration token. Topic subscriptions
 * survive rotation automatically - Play Services re-subscribes with the new token.
 */
class MorpheFcmService : FirebaseMessagingService() {

    private val notificationManager: UpdateNotificationManager by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        if (data.isEmpty()) {
            Log.w(tag, "MorpheFcmService: received empty data payload - ignoring")
            return
        }

        val type = data[KEY_TYPE]
        Log.d(tag, "MorpheFcmService: message received, type=$type, topic=${message.from}")

        when (type) {
            TYPE_MANAGER_UPDATE -> {
                val version = data[KEY_VERSION]
                notificationManager.showFcmManagerUpdateNotification(version)
            }

            TYPE_BUNDLE_UPDATE -> {
                val version = data[KEY_VERSION]
                notificationManager.showFcmBundleUpdateNotification(version)
            }

            else -> {
                // Silently ignore unknown types - keeps old builds compatible with new message types
                Log.d(tag, "MorpheFcmService: unknown type '$type' - ignoring")
            }
        }
    }

    /**
     * Called by FCM when the registration token is rotated.
     *
     * Because Morphe Manager uses topic-based messaging, token rotation is fully
     * transparent - Play Services re-subscribes to all active topics automatically.
     */
    override fun onNewToken(token: String) {
        Log.d(tag, "MorpheFcmService: registration token refreshed (topics will re-subscribe automatically)")
    }

    companion object {
        private const val KEY_TYPE    = "type"
        private const val KEY_VERSION = "version"

        private const val TYPE_MANAGER_UPDATE = "manager_update"
        private const val TYPE_BUNDLE_UPDATE  = "bundle_update"
    }
}
