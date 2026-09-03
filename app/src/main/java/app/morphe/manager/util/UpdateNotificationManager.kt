/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.morphe.manager.MainActivity
import app.morphe.manager.R
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.UpdateNotificationManager.Companion.CHANNEL_FCM_UPDATES
import app.morphe.manager.util.UpdateNotificationManager.Companion.EXTRA_TRIGGER_UPDATE_CHECK

/**
 * Manages Android system notifications for Morphe Manager update events.
 *
 * Update notifications all use [CHANNEL_FCM_UPDATES] (IMPORTANCE_HIGH), regardless of the
 * delivery source (FCM push or WorkManager background check). A queue result is quieter and
 * has a channel of its own.
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
        systemNotificationManager.createNotificationChannel(patcherChannel)
    }

    /** Post the result of a queue that finished while the user was not watching it. */
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
                context.getString(
                    R.string.batch_patch_summary,
                    patched.toString(),
                    failed.toString(),
                    skipped.toString()
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(buildBatchResultIntent())
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BATCH_RESULT, notification)
    }

    /** Opens the batch queue on the run these notifications report about. */
    private fun buildBatchResultIntent() =
        buildActivityIntent(REQUEST_CODE_BATCH_RESULT) {
            action = MainActivity.ACTION_SHOW_BATCH_RESULT
        }

    /**
     * Post a notification that a new Morphe Manager version is available.
     * Called from [app.morphe.manager.worker.UpdateCheckWorker] on non-GMS devices
     * and from [app.morphe.manager.service.MorpheFcmService] on GMS devices.
     *
     * The changelog action opens what the pending release changes, not the installed one.
     */
    fun showManagerUpdateNotification(version: String? = null) {
        postNotification(
            titleRes = R.string.notification_manager_update_title,
            contentText = if (!version.isNullOrBlank())
                context.getString(R.string.notification_update_text, version)
            else
                context.getString(R.string.notification_manager_update_title),
            notificationId = NOTIFICATION_ID_MANAGER_UPDATE,
            action = NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.whats_new),
                buildManagerChangelogIntent()
            ).build()
        )
    }

    /**
     * Post a notification that new patch bundle updates are available.
     * Called from [app.morphe.manager.worker.UpdateCheckWorker] on non-GMS devices
     * and from [app.morphe.manager.service.MorpheFcmService] on GMS devices.
     *
     * The changelog action opens [bundleUid], the default source when FCM does not name one.
     */
    fun showBundleUpdateNotification(
        version: String? = null,
        bundleUid: Int = PatchBundleRepository.DEFAULT_SOURCE_UID
    ) {
        postNotification(
            titleRes = R.string.notification_bundle_update_title,
            contentText = if (!version.isNullOrBlank())
                context.getString(R.string.notification_update_text, version)
            else
                context.getString(R.string.notification_bundle_update_text_unversioned),
            notificationId = NOTIFICATION_ID_BUNDLE_UPDATE,
            action = NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.whats_new),
                buildBundleChangelogIntent(bundleUid)
            ).build()
        )
    }

    /**
     * Builds and posts a high-priority update notification on [CHANNEL_FCM_UPDATES].
     * Uses IMPORTANCE_HIGH so the device wakes from Doze. Tapping the notification
     * opens [MainActivity] and triggers an update check via [EXTRA_TRIGGER_UPDATE_CHECK].
     */
    private fun postNotification(
        titleRes: Int,
        contentText: String,
        notificationId: Int,
        action: NotificationCompat.Action? = null
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_FCM_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildOpenAppIntent())
            .setAutoCancel(true)
            .apply { action?.let { addAction(it) } }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    /**
     * Creates a [PendingIntent] that opens [MainActivity] on the changelog of one source.
     * Picked up by [MainActivity] through [MainActivity.ACTION_SHOW_BUNDLE_CHANGELOG].
     */
    private fun buildBundleChangelogIntent(bundleUid: Int) =
        buildActivityIntent(REQUEST_CODE_BUNDLE_CHANGELOG) {
            action = MainActivity.ACTION_SHOW_BUNDLE_CHANGELOG
            putExtra(MainActivity.EXTRA_CHANGELOG_BUNDLE_UID, bundleUid)
        }

    /**
     * Creates a [PendingIntent] that opens [MainActivity] on the manager update details.
     * Picked up by [MainActivity] through [MainActivity.ACTION_SHOW_MANAGER_CHANGELOG].
     */
    private fun buildManagerChangelogIntent() =
        buildActivityIntent(REQUEST_CODE_MANAGER_CHANGELOG) {
            action = MainActivity.ACTION_SHOW_MANAGER_CHANGELOG
        }

    /**
     * Creates a [PendingIntent] that opens [MainActivity] and triggers an update check.
     * The [EXTRA_TRIGGER_UPDATE_CHECK] extra is picked up by [MainActivity] via
     * [app.morphe.manager.ui.viewmodel.MainViewModel.pendingUpdateCheck].
     */
    private fun buildOpenAppIntent() =
        buildActivityIntent(REQUEST_CODE_UPDATE_CHECK) {
            putExtra(EXTRA_TRIGGER_UPDATE_CHECK, true)
        }

    /**
     * Wraps an intent onto [MainActivity] as a [PendingIntent] under [requestCode], which has to
     * be unique per target: a shared one would rewrite the intent of every notification using it.
     */
    private fun buildActivityIntent(requestCode: Int, configure: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            configure()
        }
        @SuppressLint("WrongConstant")
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Notification channel ID for all update notifications */
        const val CHANNEL_FCM_UPDATES = "morphe_fcm_updates"

        /** Owned by the patcher worker, reused so a queue result lands where patching does. */
        const val CHANNEL_PATCHER = "morphe-patcher-patching"

        private const val NOTIFICATION_ID_MANAGER_UPDATE = 2001
        private const val NOTIFICATION_ID_BUNDLE_UPDATE  = 2002
        private const val NOTIFICATION_ID_BATCH_RESULT   = 2005

        private const val REQUEST_CODE_UPDATE_CHECK = 1
        private const val REQUEST_CODE_BATCH_RESULT = 2
        private const val REQUEST_CODE_BUNDLE_CHANGELOG = 3
        private const val REQUEST_CODE_MANAGER_CHANGELOG = 4

        /**
         * Intent extra key. When set to `true`, [MainActivity] triggers a bundle/manager
         * update check immediately after opening.
         */
        const val EXTRA_TRIGGER_UPDATE_CHECK = "trigger_update_check"
    }
}
