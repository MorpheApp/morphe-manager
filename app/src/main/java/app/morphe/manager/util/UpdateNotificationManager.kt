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
import app.morphe.manager.patcher.worker.PatcherWorker

/**
 * Manages Android system notifications for Morphe Manager update events.
 *
 * Manager and patch updates post on channels of their own, [CHANNEL_MANAGER_UPDATES] and
 * [CHANNEL_PATCH_UPDATES] (both IMPORTANCE_HIGH), so either can be muted in system settings
 * without the other, whatever the delivery source (FCM push or WorkManager background check).
 * A queue result is quieter and has a channel of its own.
 *
 * | Method                          | Caller             | Description               |
 * |---------------------------------|--------------------|---------------------------|
 * | [showManagerUpdateNotification] | FCM / WorkManager  | New manager APK available |
 * | [showBundleUpdateNotification]  | FCM / WorkManager  | New patches available     |
 * | [showMountReplacedNotification] | Mount module boot  | Mounted app was replaced  |
 *
 * On GMS devices, FCM is the primary delivery path (bypasses Doze).
 * On non-GMS devices, WorkManager uses the same methods as a fallback.
 *
 * Channels are created once in [createNotificationChannels], called from
 * [app.morphe.manager.ManagerApplication.onCreate], except [CHANNEL_MOUNTED_APPS], which only
 * a device with a mounted app needs and gets with its first notification.
 */
class UpdateNotificationManager(private val context: Context) {
    private val notificationManager by lazy { context.getSystemService(NotificationManager::class.java) }

    /**
     * Creates the required notification channels.
     * Safe to call multiple times - Android no-ops if the channel already exists.
     * Must be called before posting any notification (required on API 26+).
     */
    fun createNotificationChannels() {
        // Update channels use IMPORTANCE_HIGH so the notification shows as a heads-up and wakes
        // the screen. FCM with "priority: high" delivers the message even in Doze mode via Google
        // Play Services; IMPORTANCE_HIGH makes it visible
        val managerChannel = channel(
            CHANNEL_MANAGER_UPDATES,
            R.string.notification_channel_manager_updates,
            R.string.notification_channel_manager_updates_description,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(true) }
        val patchChannel = channel(
            CHANNEL_PATCH_UPDATES,
            R.string.notification_channel_patch_updates,
            R.string.notification_channel_patch_updates_description,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(true) }

        // Created here rather than by the patcher worker, because a queue can post its result
        // without any worker having run, for example when every app failed to be prepared
        val patcherChannel = channel(
            CHANNEL_PATCHER,
            R.string.patching,
            R.string.notification_channel_patcher_description,
            NotificationManager.IMPORTANCE_LOW
        )

        notificationManager.createNotificationChannels(listOf(managerChannel, patchChannel, patcherChannel))
    }

    /** Lint cannot follow an importance constant through a parameter, hence the suppression. */
    @SuppressLint("WrongConstant")
    private fun channel(id: String, nameRes: Int, descriptionRes: Int, importance: Int): NotificationChannel {
        val channel = NotificationChannel(id, context.getString(nameRes), importance)
        channel.description = context.getString(descriptionRes)
        return channel
    }

    /** Post the result of a queue that finished while the user was not watching it. */
    fun showBatchCompletionNotification(patched: Int, failed: Int, skipped: Int) {
        val succeeded = patched > 0
        val notification = NotificationCompat.Builder(context, CHANNEL_PATCHER)
            .setSmallIcon(
                if (succeeded) R.drawable.ic_notification_done else R.drawable.ic_notification_failed
            )
            .setColor(
                context.getColor(
                    if (succeeded) R.color.notification_success else R.color.notification_failure
                )
            )
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

        notificationManager.notify(NOTIFICATION_ID_BATCH_RESULT, notification)
    }

    /**
     * Clear patching results once the user is back in the manager, where the result is already
     * on screen. Tapping one is not the only way back, recents and the launcher are too.
     */
    fun cancelPatchingResultNotifications() {
        notificationManager.cancel(PatcherWorker.COMPLETION_NOTIFICATION_ID)
        notificationManager.cancel(NOTIFICATION_ID_BATCH_RESULT)
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
            channelId = CHANNEL_MANAGER_UPDATES,
            title = context.getString(R.string.home_update_available),
            contentText = if (!version.isNullOrBlank())
                context.getString(R.string.notification_update_text, version)
            else
                context.getString(R.string.home_update_available),
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
            channelId = CHANNEL_PATCH_UPDATES,
            title = context.getString(R.string.notification_bundle_update_title),
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
     * Post that [installedVersion] replaced the mounted [patchedVersion] of [packageName], as found
     * by [app.morphe.manager.receiver.MountReplacedReceiver]. Tagged per package.
     */
    fun showMountReplacedNotification(
        packageName: String,
        label: String,
        installedVersion: String,
        patchedVersion: String
    ) {
        // Created on first use, so only a device that mounted an app lists it. Kept apart from
        // the update channels, since muting those must not silence a patch that stopped applying
        notificationManager.createNotificationChannel(
            channel(
                CHANNEL_MOUNTED_APPS,
                R.string.notification_channel_mounted_apps,
                R.string.notification_channel_mounted_apps_description,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        postNotification(
            channelId = CHANNEL_MOUNTED_APPS,
            title = context.getString(R.string.notification_mount_replaced_title, label),
            contentText = context.getString(
                R.string.notification_mount_replaced_text,
                installedVersion,
                patchedVersion
            ),
            notificationId = NOTIFICATION_ID_MOUNT_REPLACED,
            tag = packageName,
            contentIntent = buildActivityIntent(REQUEST_CODE_MOUNT_REPLACED) {}
        )
    }

    /**
     * Builds and posts a high-priority notification on [channelId], which sets how loudly it
     * actually arrives. Tapping it runs [contentIntent], by default opening [MainActivity] with
     * an update check via [EXTRA_TRIGGER_UPDATE_CHECK].
     */
    private fun postNotification(
        channelId: String,
        title: String,
        contentText: String,
        notificationId: Int,
        action: NotificationCompat.Action? = null,
        tag: String? = null,
        contentIntent: PendingIntent = buildOpenAppIntent()
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .apply { action?.let { addAction(it) } }
            .build()

        notificationManager.notify(tag, notificationId, notification)
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
        /** Notification channel ID for manager update notifications. */
        const val CHANNEL_MANAGER_UPDATES = "morphe_manager_updates"

        /**
         * Notification channel ID for patch update notifications. Keeps the ID of the channel
         * both update kinds once shared, so whatever the user set on it carries over to patches.
         */
        const val CHANNEL_PATCH_UPDATES = "morphe_fcm_updates"

        /** Owned by the patcher worker, reused so a queue result lands where patching does. */
        const val CHANNEL_PATCHER = "morphe-patcher-patching"

        /** Notification channel ID for mounted apps whose patch stopped applying. */
        const val CHANNEL_MOUNTED_APPS = "morphe_mounted_apps"

        private const val NOTIFICATION_ID_MANAGER_UPDATE = 2001
        private const val NOTIFICATION_ID_BUNDLE_UPDATE  = 2002
        private const val NOTIFICATION_ID_BATCH_RESULT   = 2005
        private const val NOTIFICATION_ID_MOUNT_REPLACED = 2007

        private const val REQUEST_CODE_UPDATE_CHECK = 1
        private const val REQUEST_CODE_BATCH_RESULT = 2
        private const val REQUEST_CODE_BUNDLE_CHANGELOG = 3
        private const val REQUEST_CODE_MANAGER_CHANGELOG = 4
        private const val REQUEST_CODE_MOUNT_REPLACED = 5

        /**
         * Intent extra key. When set to `true`, [MainActivity] triggers a bundle/manager
         * update check immediately after opening.
         */
        const val EXTRA_TRIGGER_UPDATE_CHECK = "trigger_update_check"
    }
}
