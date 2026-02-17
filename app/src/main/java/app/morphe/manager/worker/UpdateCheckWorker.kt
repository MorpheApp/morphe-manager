/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.utils.getOrNull
import app.morphe.manager.util.UpdateNotificationManager
import app.morphe.manager.util.tag
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically checks for updates in the background.
 * Runs every 30 minutes when network is available and sends Android notifications if new updates are found.
 *
 * Checks for:
 * - Morphe Manager app updates
 * - Patch bundle updates
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val prefs: PreferencesManager by inject()
    private val morpheAPI: MorpheAPI by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val notificationManager: UpdateNotificationManager by inject()

    override suspend fun doWork(): Result {
        // Skip if background update notifications are disabled by user
        if (!prefs.backgroundUpdateNotifications.get()) {
            Log.d(tag, "UpdateCheckWorker: background notifications disabled, skipping")
            return Result.success()
        }

        Log.d(tag, "UpdateCheckWorker: starting background update check")

        return try {
            checkForManagerUpdate()
            checkForBundleUpdate()
            Log.d(tag, "UpdateCheckWorker: background update check completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "UpdateCheckWorker: failed to check for updates", e)
            // Retry later; avoids spamming logs on persistent failures (e.g. no internet)
            Result.retry()
        }
    }

    /**
     * Check if a new Morphe Manager version is available.
     * A notification is sent only if the remote version differs from the installed one.
     */
    private suspend fun checkForManagerUpdate() {
        if (!prefs.managerAutoUpdates.get()) return

        val remoteInfo = runCatching {
            morpheAPI.getLatestAppInfoFromJson().getOrNull()
        }.getOrNull() ?: return

        val remoteVersion = remoteInfo.version.removePrefix("v")
        val currentVersion = BuildConfig.VERSION_NAME

        if (remoteVersion != currentVersion) {
            Log.d(tag, "UpdateCheckWorker: manager update available ($currentVersion -> $remoteVersion)")
            notificationManager.showManagerUpdateNotification(remoteVersion)
        } else {
            Log.d(tag, "UpdateCheckWorker: manager is up to date ($currentVersion)")
        }
    }

    /**
     * Check if any remote patch bundle has a newer version available.
     * Delegates to [PatchBundleRepository.checkForBundleUpdatesQuiet] which compares
     * local vs. remote versions without applying the update.
     */
    private suspend fun checkForBundleUpdate() {
        val sources = patchBundleRepository.sources.first()
        if (sources.isEmpty()) return

        val hadUpdates = patchBundleRepository.checkForBundleUpdatesQuiet()

        if (hadUpdates) {
            Log.d(tag, "UpdateCheckWorker: patch bundle update available")
            notificationManager.showBundleUpdateNotification()
        } else {
            Log.d(tag, "UpdateCheckWorker: patch bundles are up to date")
        }
    }

    companion object {
        /** Unique name used to identify the periodic work in WorkManager */
        const val WORK_NAME = "morphe_update_check"

        /** How often WorkManager should run the update check */
        private const val INTERVAL_MINUTES = 30L

        /**
         * Schedule (or reschedule) the periodic update check.
         * Using [ExistingPeriodicWorkPolicy.KEEP] so an already-running schedule
         * is never restarted unnecessarily.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d("UpdateCheckWorker", "Periodic update check scheduled (every ${INTERVAL_MINUTES}m)")
        }

        /**
         * Cancel the periodic update check when the user turns off background notifications.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("UpdateCheckWorker", "Periodic update check cancelled")
        }
    }
}
