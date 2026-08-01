/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.*
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.batch.BatchInstallPolicy
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.BatchPatchItem
import app.morphe.manager.domain.batch.BatchPlanResolver
import app.morphe.manager.domain.batch.BatchPhase
import app.morphe.manager.domain.installer.InstallResult
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.SessionInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.PM
import app.morphe.manager.util.UpdateNotificationManager
import app.morphe.manager.util.tag
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Re-patches installed apps in the background when their patch bundle has moved on.
 *
 * The worker only queues apps that need no user input: an app qualifies when the manager
 * still has its original APK (or the stock APK is on the device) and a saved patch
 * selection. Everything else is skipped, because a scheduled run has no way to ask for a
 * missing APK or to confirm an unsupported version.
 *
 * Installing is a separate question from patching. Only an unattended installer such as
 * Shizuku can finish the job silently, so with anything else the run stops after the APKs
 * are patched and the user is notified that they are ready to install.
 */
class AutoPatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val prefs: PreferencesManager by inject()
    private val coordinator: BatchPatchCoordinator by inject()
    private val planResolver: BatchPlanResolver by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val installerManager: InstallerManager by inject()
    private val sessionInstaller: SessionInstaller by inject()
    private val notificationManager: UpdateNotificationManager by inject()
    private val pm: PM by inject()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0)

    private fun foregroundInfo(appCount: Int) = ForegroundInfo(
        UpdateNotificationManager.NOTIFICATION_ID_AUTO_PATCH_RUN,
        notificationManager.buildAutoPatchRunNotification(appCount),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    )

    override suspend fun doWork(): Result {
        if (!prefs.autoPatchEnabled.get()) {
            Log.d(tag, "AutoPatchWorker: disabled, skipping")
            return Result.success()
        }

        // A live queue owns the patcher, and its state would be replaced here. Retry rather
        // than wait out the whole interval: the user's run ends on its own
        val existingRun = coordinator.state.value
        if (existingRun != null && existingRun.phase != BatchPhase.FINISHED) {
            Log.d(tag, "AutoPatchWorker: a batch run is already present, retrying later")
            return Result.retry()
        }

        // A finished run sticks around so its summary survives until the user opens the app.
        // Left alone it would block every future run, so it is dropped here instead
        if (existingRun != null) coordinator.clear()

        // Without this exemption Doze defers the job and, worse, Android 12+ refuses to let a
        // background app start the foreground service that patching needs to outlive a job
        val powerManager = applicationContext.getSystemService(PowerManager::class.java)
        if (powerManager?.isIgnoringBatteryOptimizations(applicationContext.packageName) != true) {
            Log.w(tag, "AutoPatchWorker: app is not exempt from battery optimizations, skipping")
            notificationManager.showAutoPatchBlockedNotification(
                R.string.notification_auto_patch_blocked_battery
            )
            return Result.success()
        }

        val candidates = planResolver.findOutdatedPackages()
        if (candidates.isEmpty()) {
            Log.d(tag, "AutoPatchWorker: no patched apps are out of date")
            return Result.success()
        }

        // One foreground service covers the whole queue. Promotion has to succeed before any
        // patching starts, because a plain job is killed around the ten minute mark and a
        // half-patched app would be worse than none
        try {
            setForeground(foregroundInfo(candidates.size))
        } catch (e: Exception) {
            Log.w(tag, "AutoPatchWorker: cannot run in the background", e)
            notificationManager.showAutoPatchBlockedNotification(
                R.string.notification_auto_patch_blocked_foreground
            )
            return Result.success()
        }

        Log.i(tag, "AutoPatchWorker: queueing ${candidates.size} app(s) for re-patching")
        coordinator.plan(
            candidates,
            useMount = false,
            policy = BatchInstallPolicy.SAVE_ONLY,
            scheduled = true
        )

        // A null state means something cleared the run underneath us, which is a race rather
        // than a decision, so the queue is worth another attempt
        val planned = coordinator.state.first { it == null || it.phase == BatchPhase.PREFLIGHT }
            ?: return Result.retry()
        if (planned.runnable.isEmpty()) {
            Log.d(tag, "AutoPatchWorker: nothing could be resolved without user input")
            coordinator.clear()
            return Result.success()
        }

        coordinator.start()
        val finished = coordinator.state.first { it == null || it.phase == BatchPhase.FINISHED }
            ?: return Result.retry()

        val patched = finished.patchedItems
        if (patched.isEmpty()) {
            Log.d(tag, "AutoPatchWorker: run produced no patched APKs")
            return Result.success()
        }

        val installed = if (prefs.autoPatchInstall.get()) installSilently(patched) else 0
        notificationManager.showAutoPatchNotification(
            installed = installed,
            pending = patched.size - installed
        )

        // The finished state is left in place so opening the app shows the summary
        return Result.success()
    }

    /**
     * Installs the patched APKs through Shizuku, the only installer that never shows a
     * confirmation dialog. Returns how many apps were actually installed.
     */
    private suspend fun installSilently(items: List<BatchPatchItem>): Int {
        val token = installerManager.getPrimaryToken()
        val asPlayStore = token == InstallerManager.Token.ShizukuPlayStore
        if (token != InstallerManager.Token.Shizuku && !asPlayStore) {
            Log.d(tag, "AutoPatchWorker: primary installer cannot install unattended")
            return 0
        }

        var installed = 0
        for (item in items) {
            val file = item.patchedFile?.takeIf { it.exists() } ?: continue
            val info = pm.getPackageInfo(file)
            val targetPackage = info?.packageName ?: item.packageName

            val result = runCatching {
                if (asPlayStore) {
                    sessionInstaller.installShizukuAsPlayStore(file, targetPackage)
                } else {
                    sessionInstaller.installShizuku(file, targetPackage)
                }
            }.getOrElse { error ->
                Log.w(tag, "AutoPatchWorker: install failed for $targetPackage", error)
                null
            }

            if (result !is InstallResult.Success) continue

            installed++
            val version = info?.versionName?.takeUnless { it.isBlank() } ?: item.version ?: continue
            installedAppRepository.addOrUpdate(
                currentPackageName = targetPackage,
                originalPackageName = item.packageName,
                version = version,
                installType = if (asPlayStore) InstallType.SHIZUKU_PLAY_STORE else InstallType.SHIZUKU,
                patchSelection = item.selection,
                selectionPayload = patchBundleRepository.snapshotSelection(item.selection)
            )
        }
        return installed
    }

    companion object {
        /** Unique name used to identify the periodic work in WorkManager. */
        const val WORK_NAME = "morphe_auto_patch"

        /** Delay added per attempt when a run is postponed instead of skipped. */
        private const val RETRY_DELAY_MINUTES = 15L

        /**
         * Schedules (or reschedules) automatic re-patching.
         *
         * Patching pushes the CPU and several hundred megabytes of RAM for minutes at a
         * time, so the work waits for healthy battery and storage, and optionally a charger.
         */
        fun schedule(
            context: Context,
            interval: UpdateCheckInterval,
            requiresCharging: Boolean
        ) {
            // Deliberately no device-idle constraint: combined with charging it narrows the
            // window so far that the job barely ever runs
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .setRequiresCharging(requiresCharging)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoPatchWorker>(
                interval.minutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(interval.minutes, TimeUnit.MINUTES)
                // A retry here means the device was busy with the user's own queue, so waiting
                // a while beats hammering a job this heavy with exponential backoff
                .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            Log.d("AutoPatchWorker", "Automatic re-patching scheduled (every ${interval.minutes}m)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("AutoPatchWorker", "Automatic re-patching cancelled")
        }
    }
}
