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
import app.morphe.manager.MainActivity
import app.morphe.manager.R
import app.morphe.manager.util.UpdateNotificationManager.Companion.CHANNEL_FCM_UPDATES
import app.morphe.manager.util.UpdateNotificationManager.Companion.EXTRA_TRIGGER_UPDATE_CHECK

/**
 * Manages Android system notifications for Morphe Manager update events.
 *
 * All notifications use a single [CHANNEL_FCM_UPDATES] (IMPORTANCE_HIGH) channel,
 * regardless of the delivery source (FCM push or WorkManager background check).
 *
 * | Method                          | Caller             | Description               |
 * |---------------------------------|--------------------|---------------------------|
 * | [showManagerUpdateNotification] | FCM / WorkManager  | New manager APK available |
 * | [showBundleUpdateNotification]  | FCM / WorkManager  | New patches available     |
 *
 * On GMS devices, FCM is the primary delivery path (bypasses Doze).
 * On non-GMS devices, WorkManager uses the same methods as a fallback.
 *
 * Channels are created once in [createNotificationChannels], called from
 * [app.morphe.manager.ManagerApplication.onCreate].
 */
class UpdateNotificationManager(private val context: Context) {

    /**
     * Creates the required notification channels.
     * Safe to call multiple times - Android no-ops if the channel already exists.
     * Must be called before posting any notification (required on API 26+).
     */
    fun createNotificationChannels() {
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
        systemNotificationManager.createNotificationChannel(fcmChannel)
    }

    /**
     * Post a notification that a new Morphe Manager version is available.
     * Called from [app.morphe.manager.worker.UpdateCheckWorker] on non-GMS devices
     * and from [app.morphe.manager.service.MorpheFcmService] on GMS devices.
     */
    fun showManagerUpdateNotification(version: String? = null) {
        postNotification(
            titleRes = R.string.notification_manager_update_title,
            contentText = if (!version.isNullOrBlank())
                context.getString(R.string.notification_update_text, version)
            else
                context.getString(R.string.notification_manager_update_title),
            notificationId = NOTIFICATION_ID_MANAGER_UPDATE
        )
    }

    /**
     * Post a notification that new patch bundle updates are available.
     * Called from [app.morphe.manager.worker.UpdateCheckWorker] on non-GMS devices
     * and from [app.morphe.manager.service.MorpheFcmService] on GMS devices.
     */
    fun showBundleUpdateNotification(version: String? = null) {
        postNotification(
            titleRes = R.string.notification_bundle_update_title,
            contentText = if (!version.isNullOrBlank())
                context.getString(R.string.notification_update_text, version)
            else
                context.getString(R.string.notification_bundle_update_text_unversioned),
            notificationId = NOTIFICATION_ID_BUNDLE_UPDATE
        )
    }

    /**
     * Builds and posts a high-priority update notification on [CHANNEL_FCM_UPDATES].
     * Uses IMPORTANCE_HIGH so the device wakes from Doze. Tapping the notification
     * opens [MainActivity] and triggers an update check via [EXTRA_TRIGGER_UPDATE_CHECK].
     */
    private fun postNotification(titleRes: Int, contentText: String, notificationId: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_FCM_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildOpenAppIntent())
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    /**
     * Creates a [PendingIntent] that opens [MainActivity] and triggers an update check.
     * The [EXTRA_TRIGGER_UPDATE_CHECK] extra is picked up by [MainActivity] via
     * [app.morphe.manager.ui.viewmodel.MainViewModel.pendingUpdateCheck].
     */
    private fun buildOpenAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TRIGGER_UPDATE_CHECK, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_UPDATE_CHECK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Notification channel ID for all update notifications */
        const val CHANNEL_FCM_UPDATES = "morphe_fcm_updates"

        private const val NOTIFICATION_ID_MANAGER_UPDATE = 2001
        private const val NOTIFICATION_ID_BUNDLE_UPDATE  = 2002

        private const val REQUEST_CODE_UPDATE_CHECK = 1

        /**
         * Intent extra key. When set to `true`, [MainActivity] triggers a bundle/manager
         * update check immediately after opening.
         */
        const val EXTRA_TRIGGER_UPDATE_CHECK = "trigger_update_check"
    }
}
