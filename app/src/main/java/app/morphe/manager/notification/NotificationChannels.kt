/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.morphe.manager.R

/**
 * Every notification channel of the app. Each kind of notification posts on a channel of its own,
 * so the user can mute one in system settings without the others.
 *
 * Patching keeps two apart. [PATCHER] carries the ongoing notification its foreground services
 * cannot go without, quiet since it changes with every step. [PATCHING_RESULTS] carries what the
 * user should notice once they left the app, so making it audible never makes the progress
 * audible as well.
 */
object NotificationChannels {
    /** New manager releases. */
    const val MANAGER_UPDATES = "morphe_manager_updates"

    /**
     * New patch releases. Keeps the ID of the channel both update kinds once shared, so whatever
     * the user set on it carries over to patches.
     */
    const val PATCH_UPDATES = "morphe_fcm_updates"

    /** The ongoing notification the foreground services of patching and installing hold. */
    const val PATCHER = "morphe-patcher-patching"

    /** Patching results and installs waiting on the user, posted once the app is left. */
    const val PATCHING_RESULTS = "morphe_patching_results"

    /** Mounted apps whose patch stopped applying. */
    const val MOUNTED_APPS = "morphe_mounted_apps"

    /**
     * Creates the channels, or renames them after a language change. Called before any
     * notification can be posted, except [MOUNTED_APPS], which only a device with a mounted app
     * needs and gets with its first notification through [createMountedApps].
     */
    fun create(context: Context) {
        // Update channels use IMPORTANCE_HIGH so the notification shows as a heads-up and wakes
        // the screen. FCM with "priority: high" delivers the message even in Doze mode via Google
        // Play Services; IMPORTANCE_HIGH makes it visible
        val managerChannel = channel(
            context,
            MANAGER_UPDATES,
            R.string.notification_channel_manager_updates,
            R.string.notification_channel_manager_updates_description,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(true) }
        val patchChannel = channel(
            context,
            PATCH_UPDATES,
            R.string.notification_channel_patch_updates,
            R.string.notification_channel_patch_updates_description,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(true) }

        val patcherChannel = channel(
            context,
            PATCHER,
            R.string.patching,
            R.string.notification_channel_patcher_description,
            NotificationManager.IMPORTANCE_LOW
        )
        // Created here rather than by the patcher worker, because a queue can post its result
        // without any worker having run, for example when every app failed to be prepared
        val resultsChannel = channel(
            context,
            PATCHING_RESULTS,
            R.string.notification_channel_patching_results,
            R.string.notification_channel_patching_results_description,
            NotificationManager.IMPORTANCE_DEFAULT
        )

        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(managerChannel, patchChannel, patcherChannel, resultsChannel)
        )
    }

    /**
     * Creates [MOUNTED_APPS]. Kept apart from the update channels, since muting those must not
     * silence a patch that stopped applying.
     */
    fun createMountedApps(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            channel(
                context,
                MOUNTED_APPS,
                R.string.notification_channel_mounted_apps,
                R.string.notification_channel_mounted_apps_description,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /** Lint cannot follow an importance constant through a parameter, hence the suppression. */
    @SuppressLint("WrongConstant")
    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
        descriptionRes: Int,
        importance: Int
    ): NotificationChannel {
        val channel = NotificationChannel(id, context.getString(nameRes), importance)
        channel.description = context.getString(descriptionRes)
        return channel
    }
}
