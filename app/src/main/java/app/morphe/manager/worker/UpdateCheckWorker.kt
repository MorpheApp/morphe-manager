/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.ManagerUpdateRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.notification.UpdateNotifications
import app.morphe.manager.util.normalizeVersion
import app.morphe.manager.util.tag
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically checks for updates in the background.
 * The repeat interval is controlled by [ScheduleInterval] and stored in
 * [PreferencesManager.updateCheckInterval] (default: [ScheduleInterval.DAILY]).
 *
 * Checks for:
 * - Morphe Manager app updates
 * - Patch bundle updates
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val prefs: PreferencesManager,
    private val managerUpdateRepository: ManagerUpdateRepository,
    private val patchBundleRepository: PatchBundleRepository,
    private val notificationManager: UpdateNotifications
) : CoroutineWorker(context, params) {
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
     * Delegates to [ManagerUpdateRepository] which handles semver comparison, prerelease
     * logic, fetching from the correct branch and verifying that the APK is downloadable.
     */
    private suspend fun checkForManagerUpdate() {
        val update = runCatching {
            managerUpdateRepository.refresh()
        }.getOrNull() ?: return

        val newVersion = update.version.normalizeVersion()
        Log.d(tag, "UpdateCheckWorker: manager update available (${BuildConfig.VERSION_NAME} -> $newVersion)")
        notificationManager.showManagerUpdateNotification(newVersion)
    }

    /**
     * Check if any remote patch bundle has a newer version available.
     * Delegates to [PatchBundleRepository.checkForBundleUpdatesQuiet] which compares
     * local vs. remote versions without applying the update.
     */
    private suspend fun checkForBundleUpdate() {
        val sources = patchBundleRepository.sources.first()
        if (sources.isEmpty()) return

        val update = patchBundleRepository.checkForBundleUpdatesQuiet()

        if (update != null) {
            Log.d(tag, "UpdateCheckWorker: patch bundle update available (${update.version})")
            notificationManager.showBundleUpdateNotification(update.version, update.bundleUid)
        } else {
            Log.d(tag, "UpdateCheckWorker: patch bundles are up to date")
        }
    }

    companion object {
        /** Unique name used to identify the periodic work in WorkManager */
        const val WORK_NAME = "morphe_update_check"

        /**
         * Schedule (or reschedule) the periodic update check with the given [interval].
         *
         * Uses [ExistingPeriodicWorkPolicy.UPDATE] so that changing the interval in Settings takes effect immediately.
         */
        fun schedule(context: Context, interval: ScheduleInterval = ScheduleInterval.DAILY) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                interval.minutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(interval.minutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            Log.d("UpdateCheckWorker", "Periodic update check scheduled (every ${interval.minutes}m / ${interval.name})")
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
