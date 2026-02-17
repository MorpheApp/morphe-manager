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
 * Two notification types:
 * - Manager update: notifies when a new Morphe Manager APK is available
 * - Bundle update: notifies when new patches are available for download
 *
 * Both notifications tap through to [MainActivity]. The notification channels are
 * created once during [createNotificationChannels] which should be called from
 * [app.morphe.manager.ManagerApplication.onCreate].
 */
class UpdateNotificationManager(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Creates the required notification channels.
     * Safe to call multiple times — Android no-ops if the channel already exists.
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

        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemNotificationManager.createNotificationChannel(managerChannel)
        systemNotificationManager.createNotificationChannel(bundleChannel)
    }

    /**
     * Post a notification that a new Morphe Manager version is available.
     *
     * @param newVersion The new version string, e.g. "1.2.3"
     */
    fun showManagerUpdateNotification(newVersion: String) {
        if (!notificationManager.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_MANAGER_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_manager_update_title))
            .setContentText(
                context.getString(R.string.notification_manager_update_text, newVersion)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildOpenAppIntent())
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MANAGER_UPDATE, notification)
    }

    /**
     * Post a notification that new patch bundle updates are available.
     */
    fun showBundleUpdateNotification() {
        if (!notificationManager.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_BUNDLE_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_bundle_update_title))
            .setContentText(context.getString(R.string.notification_bundle_update_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildOpenAppIntent())
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BUNDLE_UPDATE, notification)
    }

    /** Creates a [PendingIntent] that opens [MainActivity] when the notification is tapped. */
    private fun buildOpenAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Notification channel ID for manager (app) update notifications */
        const val CHANNEL_MANAGER_UPDATES = "morphe_manager_updates"

        /** Notification channel ID for patch bundle update notifications */
        const val CHANNEL_BUNDLE_UPDATES = "morphe_bundle_updates"

        /** Stable notification ID for the "manager update available" notification */
        private const val NOTIFICATION_ID_MANAGER_UPDATE = 1001

        /** Stable notification ID for the "bundle update available" notification */
        private const val NOTIFICATION_ID_BUNDLE_UPDATE = 1002

        /** PendingIntent request code for the tap-through open-app action */
        private const val REQUEST_CODE_OPEN_APP = 0
    }
}
