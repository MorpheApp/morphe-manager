/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.morphe.manager.MainActivity
import app.morphe.manager.R
import app.morphe.manager.domain.repository.PatchBundleRepository

/**
 * Notifications about what changed outside the app: new releases and mounted apps.
 *
 * Manager and patch updates post on channels of their own, [NotificationChannels.MANAGER_UPDATES]
 * and [NotificationChannels.PATCH_UPDATES], so either can be muted in system settings without the
 * other, whatever the delivery source (FCM push or WorkManager background check).
 *
 * | Method                          | Caller             | Description               |
 * |---------------------------------|--------------------|---------------------------|
 * | [showManagerUpdateNotification] | FCM / WorkManager  | New manager APK available |
 * | [showBundleUpdateNotification]  | FCM / WorkManager  | New patches available     |
 * | [showMountReplacedNotification] | Mount module boot  | Mounted app was replaced  |
 *
 * On GMS devices, FCM is the primary delivery path (bypasses Doze).
 * On non-GMS devices, WorkManager uses the same methods as a fallback.
 */
class UpdateNotifications(private val context: Context) {
    private val notificationManager by lazy { context.getSystemService(NotificationManager::class.java) }

    /**
     * Post a notification that a new Morphe Manager version is available.
     * Called from [app.morphe.manager.worker.UpdateCheckWorker] on non-GMS devices
     * and from [app.morphe.manager.service.FcmService] on GMS devices.
     *
     * The changelog action opens what the pending release changes, not the installed one.
     */
    fun showManagerUpdateNotification(version: String? = null) {
        postNotification(
            channelId = NotificationChannels.MANAGER_UPDATES,
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
     * and from [app.morphe.manager.service.FcmService] on GMS devices.
     *
     * The changelog action opens [bundleUid], the default source when FCM does not name one.
     */
    fun showBundleUpdateNotification(
        version: String? = null,
        bundleUid: Int = PatchBundleRepository.DEFAULT_SOURCE_UID
    ) {
        postNotification(
            channelId = NotificationChannels.PATCH_UPDATES,
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
        // Created on first use, so only a device that mounted an app lists it
        NotificationChannels.createMountedApps(context)

        postNotification(
            channelId = NotificationChannels.MOUNTED_APPS,
            title = context.getString(R.string.notification_mount_replaced_title, label),
            contentText = context.getString(
                R.string.notification_mount_replaced_text,
                installedVersion,
                patchedVersion
            ),
            notificationId = NOTIFICATION_ID_MOUNT_REPLACED,
            tag = packageName,
            contentIntent = NotificationIntents.activity(context, NotificationIntents.REQUEST_CODE_MOUNT_REPLACED) {
                action = MainActivity.ACTION_SHOW_APP_INFO
                putExtra(MainActivity.EXTRA_APP_INFO_PACKAGE, packageName)
            }
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
        NotificationIntents.activity(context, NotificationIntents.REQUEST_CODE_BUNDLE_CHANGELOG) {
            action = MainActivity.ACTION_SHOW_BUNDLE_CHANGELOG
            putExtra(MainActivity.EXTRA_CHANGELOG_BUNDLE_UID, bundleUid)
        }

    /**
     * Creates a [PendingIntent] that opens [MainActivity] on the manager update details.
     * Picked up by [MainActivity] through [MainActivity.ACTION_SHOW_MANAGER_CHANGELOG].
     */
    private fun buildManagerChangelogIntent() =
        NotificationIntents.activity(context, NotificationIntents.REQUEST_CODE_MANAGER_CHANGELOG) {
            action = MainActivity.ACTION_SHOW_MANAGER_CHANGELOG
        }

    /**
     * Creates a [PendingIntent] that opens [MainActivity] and triggers an update check.
     * The [EXTRA_TRIGGER_UPDATE_CHECK] extra is picked up by [MainActivity] via
     * [app.morphe.manager.ui.viewmodel.MainViewModel.pendingUpdateCheck].
     */
    private fun buildOpenAppIntent() =
        NotificationIntents.activity(context, NotificationIntents.REQUEST_CODE_UPDATE_CHECK) {
            putExtra(EXTRA_TRIGGER_UPDATE_CHECK, true)
        }

    companion object {
        private const val NOTIFICATION_ID_MANAGER_UPDATE = 2001
        private const val NOTIFICATION_ID_BUNDLE_UPDATE  = 2002
        private const val NOTIFICATION_ID_MOUNT_REPLACED = 2007

        /**
         * Intent extra key. When set to `true`, [MainActivity] triggers a bundle/manager
         * update check immediately after opening.
         */
        const val EXTRA_TRIGGER_UPDATE_CHECK = "trigger_update_check"
    }
}
