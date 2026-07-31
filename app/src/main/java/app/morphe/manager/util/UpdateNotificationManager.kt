/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.annotation.SuppressLint
import android.app.Notification
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
        @SuppressLint("WrongConstant")
        val fcmChannel = NotificationChannel(
            CHANNEL_FCM_UPDATES,
            context.getString(R.string.notification_channel_fcm_updates),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_fcm_updates_description)
            enableVibration(true)
        }

        // Automatic re-patching reports itself without making a sound: the user did not ask
        // for anything right now, so it stays in the shade instead of interrupting
        @SuppressLint("WrongConstant")
        val autoPatchChannel = NotificationChannel(
            CHANNEL_AUTO_PATCH,
            context.getString(R.string.notification_channel_auto_patch),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_auto_patch_description)
            enableVibration(false)
            setSound(null, null)
        }

        // Created here rather than by the patcher worker, because a queue can post its result
        // without any worker having run, for example when every app failed to be prepared
        val patcherChannel = NotificationChannel(
            CHANNEL_PATCHER,
            context.getString(R.string.notification_channel_patcher),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_patcher_description)
        }

        val systemNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemNotificationManager.createNotificationChannel(fcmChannel)
        systemNotificationManager.createNotificationChannel(autoPatchChannel)
        systemNotificationManager.createNotificationChannel(patcherChannel)
    }

    /**
     * Ongoing notification shown while an automatic re-patch run is working through its queue.
     * Doubles as the foreground service notification that keeps the run alive.
     */
    fun buildAutoPatchRunNotification(appCount: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_AUTO_PATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_auto_patch_running_title))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.batch_patch_ready_count,
                    appCount,
                    appCount
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .setGroup(GROUP_PATCHING)
            .setContentIntent(buildBatchResultIntent())
            .build()

    /**
     * Post the result of an automatic re-patch run.
     *
     * @param installed Apps installed without asking, possible only with a silent installer.
     * @param pending Apps that were patched and are waiting for the user to install them.
     */
    fun showAutoPatchNotification(installed: Int, pending: Int) {
        if (installed == 0 && pending == 0) return

        val contentText = if (pending > 0) {
            context.resources.getQuantityString(
                R.plurals.batch_patch_notification_ready,
                pending,
                pending
            )
        } else {
            context.resources.getQuantityString(
                R.plurals.batch_install_summary,
                installed,
                installed
            )
        }

        postSilentNotification(
            title = context.getString(R.string.notification_auto_patch_title),
            contentText = contentText,
            notificationId = NOTIFICATION_ID_AUTO_PATCH
        )
    }

    /**
     * Post the result of a queue the user started themselves.
     *
     * Scheduled runs are reported by [showAutoPatchNotification] instead: they are silent by
     * design and often finish while nobody is looking.
     */
    fun showBatchCompletionNotification(patched: Int, failed: Int, skipped: Int) {
        val succeeded = patched > 0
        val notification = NotificationCompat.Builder(context, CHANNEL_PATCHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    if (succeeded) R.string.patcher_complete_title else R.string.patcher_failed_title
                )
            )
            .setContentText(
                context.getString(R.string.batch_patch_summary, patched, failed, skipped)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(buildBatchResultIntent())
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BATCH_RESULT, notification)
    }

    /**
     * Post why an automatic re-patch run could not start. Android blocks background foreground
     * services and defers work in Doze, so the user has to know the schedule is not working
     * rather than silently getting nothing.
     */
    fun showAutoPatchBlockedNotification(reasonRes: Int) {
        postSilentNotification(
            title = context.getString(R.string.notification_auto_patch_blocked_title),
            contentText = context.getString(reasonRes),
            notificationId = NOTIFICATION_ID_AUTO_PATCH_BLOCKED,
            // Nothing ran, so there is no summary to open
            contentIntent = buildOpenAppIntent(triggerUpdateCheck = false)
        )
    }

    private fun postSilentNotification(
        title: String,
        contentText: String,
        notificationId: Int,
        contentIntent: PendingIntent = buildBatchResultIntent()
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_AUTO_PATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    /** Opens the batch queue on the run these notifications report about. */
    private fun buildBatchResultIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_BATCH_RESULT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        @SuppressLint("WrongConstant")
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_BATCH_RESULT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
    private fun buildOpenAppIntent(triggerUpdateCheck: Boolean = true): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (triggerUpdateCheck) putExtra(EXTRA_TRIGGER_UPDATE_CHECK, true)
        }
        @SuppressLint("WrongConstant")
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

        /** Silent channel for automatic re-patching progress and results. */
        const val CHANNEL_AUTO_PATCH = "morphe_auto_patch"

        /** Owned by the patcher worker, reused so a queue result lands where patching does. */
        const val CHANNEL_PATCHER = "morphe-patcher-patching"

        /**
         * Groups the ongoing notifications a run produces. A scheduled queue holds two
         * foreground services at once, its own and the patcher's, and each is required to
         * show a notification. Grouping lets the shade fold them into one entry.
         */
        const val GROUP_PATCHING = "morphe_patching"

        private const val NOTIFICATION_ID_MANAGER_UPDATE = 2001
        private const val NOTIFICATION_ID_BUNDLE_UPDATE  = 2002
        private const val NOTIFICATION_ID_AUTO_PATCH     = 2003
        private const val NOTIFICATION_ID_AUTO_PATCH_BLOCKED = 2004
        private const val NOTIFICATION_ID_BATCH_RESULT   = 2005

        /** Foreground service notification held for the duration of an automatic run. */
        const val NOTIFICATION_ID_AUTO_PATCH_RUN = 3001

        private const val REQUEST_CODE_UPDATE_CHECK = 1
        private const val REQUEST_CODE_BATCH_RESULT = 2

        /**
         * Intent extra key. When set to `true`, [MainActivity] triggers a bundle/manager
         * update check immediately after opening.
         */
        const val EXTRA_TRIGGER_UPDATE_CHECK = "trigger_update_check"
    }
}
