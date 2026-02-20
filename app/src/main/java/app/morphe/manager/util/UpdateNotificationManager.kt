/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.morphe.manager.MainActivity
import app.morphe.manager.R

/**
 * Manages Android system notifications for Morphe Manager update events.
 *
 * Four notification methods across two delivery channels:
 *
 * | Method                          | Channel     | Description                              |
 * |---------------------------------|-------------|------------------------------------------|
 * | [showManagerUpdateNotification] | WorkManager | New manager APK available (with version) |
 * | [showBundleUpdateNotification]  | WorkManager | New patches available (with version)     |
 * | [showFcmManagerUpdateNotification] | FCM      | Same as above, high-priority push        |
 * | [showFcmBundleUpdateNotification]  | FCM      | Same as above, high-priority push        |
 *
 * FCM methods use [CHANNEL_FCM_UPDATES] (IMPORTANCE_HIGH) to wake the device from Doze.
 * WorkManager methods use lower-priority channels for periodic background checks.
 * All notifications tap through to [MainActivity].
 *
 * Channels are created once in [createNotificationChannels], called from
 * [app.morphe.manager.ManagerApplication.onCreate].
 */
class UpdateNotificationManager(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Creates the required notification channels.
     * Safe to call multiple times - Android no-ops if the channel already exists.
     * Must be called before posting any notification (required on API 26+).
     */
    fun createNotificationChannels() {
        val managerChannel = NotificationChannel(
            CHANNEL_MANAGER_UPDATES,
            context.getString(R.string.notification_channel_manager_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_manager_updates_description)
        }

        val bundleChannel = NotificationChannel(
            CHANNEL_BUNDLE_UPDATES,
            context.getString(R.string.notification_channel_bundle_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_bundle_updates_description)
        }

        // FCM channel uses IMPORTANCE_HIGH so the notification shows as a heads-up
        // and wakes the screen. FCM with "priority: high" delivers the message even
        // in Doze mode via Google Play Services; IMPORTANCE_HIGH makes it visible.
        val fcmChannel = NotificationChannel(
            CHANNEL_FCM_UPDATES,
            context.getString(R.string.notification_channel_fcm_updates),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_fcm_updates_description)
            enableVibration(true)
        }

        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemNotificationManager.createNotificationChannel(managerChannel)
        systemNotificationManager.createNotificationChannel(bundleChannel)
        systemNotificationManager.createNotificationChannel(fcmChannel)
    }

    /**
     * Post a notification that a new Morphe Manager version is available.
     */
    fun showManagerUpdateNotification(newVersion: String) {
        if (!notificationManager.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_MANAGER_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_manager_update_title))
            .setContentText(context.getString(R.string.notification_update_text, newVersion))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildOpenAppIntent())
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MANAGER_UPDATE, notification)
    }

    /**
     * Post a notification that new patch bundle updates are available.
     */
    fun showBundleUpdateNotification(version: String? = null) {
        if (!notificationManager.areNotificationsEnabled()) return

        val contentText = if (!version.isNullOrBlank()) {
            context.getString(R.string.notification_update_text, version)
        } else {
            context.getString(R.string.notification_bundle_update_text_unversioned)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_BUNDLE_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_bundle_update_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildOpenAppIntent())
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BUNDLE_UPDATE, notification)
    }

    /**
     * Post a high-priority notification that a new Morphe Manager version is available.
     * Called from [app.morphe.manager.service.MorpheFcmService] when an FCM push arrives.
     *
     * Uses [CHANNEL_FCM_UPDATES] (IMPORTANCE_HIGH) so the device wakes from Doze.
     * No [NotificationManagerCompat.areNotificationsEnabled] guard - FCM already
     * verified delivery eligibility before waking the device.
     */
    fun showFcmManagerUpdateNotification(version: String? = null) {
        val contentText = if (!version.isNullOrBlank()) {
            context.getString(R.string.notification_update_text, version)
        } else {
            context.getString(R.string.notification_manager_update_title)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_FCM_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_manager_update_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildOpenAppIntent(triggerUpdateCheck = true))
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_FCM_MANAGER_UPDATE, notification)
    }

    /**
     * Post a high-priority notification that new patch bundles are available.
     * Called from [app.morphe.manager.service.MorpheFcmService] when an FCM push arrives.
     */
    fun showFcmBundleUpdateNotification(version: String? = null) {
        val contentText = if (!version.isNullOrBlank()) {
            context.getString(R.string.notification_update_text, version)
        } else {
            context.getString(R.string.notification_bundle_update_text_unversioned)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_FCM_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_bundle_update_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildOpenAppIntent(triggerUpdateCheck = true))
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_FCM_BUNDLE_UPDATE, notification)
    }

    /**
     * Creates a [PendingIntent] that opens [MainActivity] when the notification is tapped.
     * When [triggerUpdateCheck] is true, adds an extra so [MainActivity] will automatically
     * trigger a bundle/manager update check on open (used for FCM push notifications).
     */
    private fun buildOpenAppIntent(triggerUpdateCheck: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (triggerUpdateCheck) putExtra(EXTRA_TRIGGER_UPDATE_CHECK, true)
        }
        return PendingIntent.getActivity(
            context,
            if (triggerUpdateCheck) REQUEST_CODE_UPDATE_CHECK else REQUEST_CODE_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Notification channel ID for WorkManager manager update notifications */
        const val CHANNEL_MANAGER_UPDATES = "morphe_manager_updates"

        /** Notification channel ID for WorkManager bundle update notifications */
        const val CHANNEL_BUNDLE_UPDATES = "morphe_bundle_updates"

        /** Notification channel ID for FCM high-priority push notifications */
        const val CHANNEL_FCM_UPDATES = "morphe_fcm_updates"

        /** Stable notification ID for WorkManager manager update notification */
        private const val NOTIFICATION_ID_MANAGER_UPDATE = 1001

        /** Stable notification ID for WorkManager bundle update notification */
        private const val NOTIFICATION_ID_BUNDLE_UPDATE = 1002

        /** Stable notification ID for FCM manager update notification */
        private const val NOTIFICATION_ID_FCM_MANAGER_UPDATE = 2001

        /** Stable notification ID for FCM bundle update notification */
        private const val NOTIFICATION_ID_FCM_BUNDLE_UPDATE = 2002

        /** PendingIntent request code for the tap-through open-app action */
        private const val REQUEST_CODE_OPEN_APP = 0

        /**
         * PendingIntent request code for the tap-through open-app action with update check.
         * Must differ from [REQUEST_CODE_OPEN_APP] so Android creates a distinct PendingIntent.
         */
        private const val REQUEST_CODE_UPDATE_CHECK = 1

        /**
         * Intent extra key. When set to `true`, [MainActivity] will trigger a bundle/manager
         * update check immediately after opening. Set by FCM notification tap-through intents.
         */
        const val EXTRA_TRIGGER_UPDATE_CHECK = "trigger_update_check"
    }
}
