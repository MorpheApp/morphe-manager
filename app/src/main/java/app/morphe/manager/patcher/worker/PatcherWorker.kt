/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/worker/PatcherWorker.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.morphe.manager.BuildConfig
import app.morphe.manager.MainActivity
import app.morphe.manager.ManagerApplication
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.worker.Worker
import app.morphe.manager.domain.worker.WorkerRepository
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.patch.ApkArchitectureResolver
import app.morphe.manager.patcher.patch.PatchSourceRef
import app.morphe.manager.patcher.runtime.HeapExhaustedException
import app.morphe.manager.patcher.runtime.JvmUnavailableException
import app.morphe.manager.patcher.runtime.PatcherFailureException
import app.morphe.manager.patcher.runtime.PatcherKilledException
import app.morphe.manager.patcher.runtime.PatcherProcesses
import app.morphe.manager.patcher.runtime.Runtime
import app.morphe.manager.patcher.runtime.PatcherVm
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.model.State
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.restrictTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.measureTime

typealias ProgressEventHandler = (name: String?, state: State?, message: String?) -> Unit

class PatcherWorker(
    context: Context,
    parameters: WorkerParameters,
    private val workerRepository: WorkerRepository,
    private val prefs: PreferencesManager,
    private val keystoreManager: KeystoreManager,
    private val pm: PM,
    private val fs: Filesystem,
    private val installedAppRepository: InstalledAppRepository,
    private val originalApkRepository: OriginalApkRepository,
    private val rootInstaller: RootInstaller,
    private val installerManager: InstallerManager,
    private val patchBundleRepository: PatchBundleRepository,
    private val patcherProcesses: PatcherProcesses
) : Worker<PatcherWorker.Args>(context, parameters) {
    private fun runtime(vm: PatcherVm) =
        Runtime(applicationContext, fs, patchBundleRepository, patcherProcesses, vm)

    class Args(
        val input: SelectedApp,
        val output: String,
        val selectedPatches: PatchSelection,
        val options: Options,
        val logger: Logger,
        val onPatchCompleted: suspend () -> Unit,
        /** Called with the name of the patch the run failed on. */
        val onPatchFailed: (String) -> Unit,
        /**
         * Patching was abandoned and started over from the first step, on ART,
         * so anything reported by the previous attempt has to be discarded rather than counted twice.
         */
        val onPatchingRestarted: suspend () -> Unit,
        val setInputFile: suspend (File, Boolean, Boolean) -> Unit,
        val onProgress: ProgressEventHandler,
        val patchSources: List<PatchSourceRef> = emptyList(),
        /**
         * Batch runs announce the whole queue once instead of every app, so the completion
         * tone and notification are suppressed per item.
         */
        val announceCompletion: Boolean = true,
        /** Place of this app in the queue and the queue total, null for a single run. */
        val queuePosition: Pair<Int, Int>? = null,
        /** Whether the build is mounted over the stock app rather than installed. */
        val useMount: Boolean = false
    ) {
        val packageName get() = input.packageName
    }

    /** Queue position shown in the ongoing notification, set once the args are claimed. */
    private var queueLabel: String? = null

    override suspend fun getForegroundInfo() =
        ForegroundInfo(
            NOTIFICATION_ID,
            createNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

    @SuppressLint("WrongConstant")
    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @SuppressLint("WrongConstant")
    private fun createNotification(
        stepName: String? = null,
        patchProgress: Pair<Int, Int>? = null, // Completed to total patches
        contentText: String? = null,
    ): Notification {
        val pendingIntent = mainActivityPendingIntent()
        return Notification.Builder(applicationContext, UpdateNotificationManager.CHANNEL_PATCHER)
            .setContentTitle(
                stepName ?: applicationContext.getString(R.string.patcher_notification_title)
            )
            .setContentText(
                contentText
                    ?: queueLabel
                    ?: applicationContext.getText(R.string.patcher_notification_text)
            )
            .apply {
                if (patchProgress != null) {
                    val (completed, total) = patchProgress
                    setSubText("$completed / $total")
                    setProgress(total, completed, false)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    applyLiveUpdate(patchProgress)
                }
            }
            .setSmallIcon(Icon.createWithResource(applicationContext, R.drawable.ic_notification))
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    /** Request promotion to a Live Update, shown as a status bar chip while the manager is in the background. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun Notification.Builder.applyLiveUpdate(patchProgress: Pair<Int, Int>?) {
        val style = Notification.ProgressStyle()
        if (patchProgress != null) {
            val (completed, total) = patchProgress
            // The bar length is the sum of its segments, a single one spans every patch
            style.progressSegments = listOf(Notification.ProgressStyle.Segment(total.coerceAtLeast(1)))
            style.progress = completed
            setShortCriticalText("$completed/$total")
        } else {
            style.isProgressIndeterminate = true
        }
        setStyle(style)
        // The builder setter only exists from 36.1, the extra is what the framework reads on 36.0
        addExtras(Bundle().apply { putBoolean(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING, true) })
    }

    private fun updatePatcherNotification(
        stepName: String?,
        patchProgress: Pair<Int, Int>? = null,
        contentText: String? = null,
    ) {
        val notificationManager =
            applicationContext.getSystemService(NotificationManager::class.java)
        // Android won't visually switch from indeterminate → determinate on the same notification
        // ID unless we first post a brief non-indeterminate update. Post the real notification
        // directly - the determinate bar replaces the spinning one cleanly this way
        notificationManager.notify(NOTIFICATION_ID, createNotification(stepName, patchProgress, contentText))
    }

    private fun showCompletionNotification(
        succeeded: Boolean,
        autoInstallPending: Boolean,
        playSound: Boolean,
        successSoundUri: String,
        errorSoundUri: String,
    ) {
        if (playSound) CompletionSound.play(
            applicationContext,
            succeeded,
            successSoundUri,
            errorSoundUri
        )
        // Don't show "patching complete" when an auto-install will immediately follow: it
        // either needs nothing from the user or asks for it in a notification of its own
        if (succeeded && autoInstallPending) return
        // Don't notify when the app is in the foreground - user sees the result on screen
        if (ManagerApplication.isInForeground) return
        val notification = Notification.Builder(applicationContext, UpdateNotificationManager.CHANNEL_PATCHER)
            .setContentTitle(
                applicationContext.getString(
                    if (succeeded) R.string.patcher_complete_title else R.string.patcher_failed_title
                )
            )
            .setContentText(applicationContext.getText(R.string.patcher_notification_text))
            .setSmallIcon(
                Icon.createWithResource(
                    applicationContext,
                    if (succeeded) R.drawable.ic_notification_done else R.drawable.ic_notification_failed
                )
            )
            .setColor(
                applicationContext.getColor(
                    if (succeeded) R.color.notification_success else R.color.notification_failure
                )
            )
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        if (runAttemptCount > 0) {
            Log.d(tag, "Android requested retrying but retrying is disabled.".logFmt())
            return Result.failure()
        }

        try {
            // This does not always show up for some reason
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            // Foreground promotion can fail on some devices or when notification permission is
            // denied. Log it but continue - patching still works, just with less OS protection
            Log.w(tag, "Failed to promote worker to foreground service:".logFmt(), e)
        }

        val wakeLock: PowerManager.WakeLock =
            (applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$tag::Patcher")
                .apply {
                    // No timeout: the finally block below always releases this lock, so a cap
                    // would only risk the CPU sleeping mid-patch on large or slow devices
                    @Suppress("WakelockTimeout")
                    acquire()
                    Log.d(tag, "Acquired wakelock.")
                }

        lateinit var args: Args
        var patchingSucceeded = false
        val result = try {
            args = workerRepository.claimInput(this)
            queueLabel = args.queuePosition?.let { (position, total) ->
                applicationContext.getString(
                    R.string.batch_patch_progress_counter,
                    position.toString(),
                    total.toString()
                )
            }
            runPatcher(args).also { if (it == Result.success()) patchingSucceeded = true }
        } finally {
            wakeLock.release()
        }

        // A mount can still restore the stock app from it, the install flow deletes it after mounting
        if (patchingSucceeded && !args.useMount) {
            (args.input as? SelectedApp.Local)?.takeIf { it.temporary }?.file?.delete()
        }

        return result
    }

    private suspend fun runPatcher(args: Args): Result {

        val totalPatches = args.selectedPatches.values.sumOf { it.size }
        var completedPatches = 0
        // Cached so onPatchCompleted can update the title without a string lookup race
        val applyingPatchesLabel = applicationContext.getString(R.string.applying_patches)
        val writingApkLabel = applicationContext.getString(R.string.patcher_step_write_patched)
        val signingApkLabel = applicationContext.getString(R.string.patcher_step_sign_apk)
        val isExpertMode = prefs.useExpertMode.get()
        // Flipped once when the patching phase completes to trigger the writing-step notification
        var patchingPhaseCompleted = false

        fun updateProgress(name: String? = null, state: State? = null, message: String? = null) {
            if (state == State.RUNNING) {
                if (name != null) {
                    updatePatcherNotification(stepName = name, patchProgress = null)
                } else if (totalPatches > 0) {
                    updatePatcherNotification(
                        stepName = applyingPatchesLabel,
                        patchProgress = completedPatches to totalPatches
                    )
                }
            } else if (state == State.COMPLETED && !patchingPhaseCompleted
                && completedPatches == totalPatches && totalPatches > 0
            ) {
                patchingPhaseCompleted = true
                updatePatcherNotification(stepName = writingApkLabel, patchProgress = null)
            }
            args.onProgress(name, state, message)
        }

        val onPatchCompleted: suspend (String) -> Unit = { patchName ->
            completedPatches++
            // Update both title and progress bar together on every completed patch;
            // in expert mode also show which patch just finished
            updatePatcherNotification(
                stepName = applyingPatchesLabel,
                patchProgress = completedPatches to totalPatches,
                contentText = if (isExpertMode && patchName.isNotBlank()) patchName else null,
            )
            args.onPatchCompleted()
        }

        // The notification carries a patch count of its own, which would otherwise keep
        // climbing past the total once a restarted attempt reports the same patches again.
        // It goes back to the indeterminate form because the next attempt starts at loading
        // patches, not at applying them
        val onRestart: suspend () -> Unit = {
            completedPatches = 0
            patchingPhaseCompleted = false
            updatePatcherNotification(stepName = null, patchProgress = null)
            args.onPatchingRestarted()
        }

        val patchedApk = fs.tempDir.resolve("patched.apk")
        var succeeded = false
        var autoInstallPending = false
        val completionSoundEnabled = prefs.patcherCompletionSound.get()
        val successSoundUri = prefs.patcherSuccessSoundUri.get()
        val errorSoundUri = prefs.patcherErrorSoundUri.get()

        var unmounted = false

        return try {
            if (args.input is SelectedApp.Installed &&
                installedAppRepository.get(args.packageName)?.installType == InstallType.MOUNT
            ) {
                rootInstaller.unmount(args.packageName)
                unmounted = true
            }

            val inputFile = when (val selectedApp = args.input) {
                is SelectedApp.Local -> {
                    val needsSplit = SplitApkPreparer.isSplitArchive(selectedApp.file)
                    args.setInputFile(selectedApp.file, needsSplit, false)
                    selectedApp.file
                }

                is SelectedApp.Installed -> {
                    val sourceDir = pm.getPackageInfo(selectedApp.packageName)?.applicationInfo?.sourceDir
                        ?: error("${selectedApp.packageName} is no longer installed")
                    val source = File(sourceDir)
                    args.setInputFile(source, false, false)
                    source
                }
            }

            keystoreManager.preloadSigner()

            val vm = patcherProcesses.preferredVm()
            val stripNativeLibs = prefs.stripUnusedNativeLibs.get()
            val inputIsSplitArchive = SplitApkPreparer.isSplitArchive(inputFile)
            // The architecture the patches were selected against, worth a line of its own now
            // that a patch can declare itself unavailable for the one the input carries. Read
            // from the app rather than from [inputFile], which for an installed one is the base
            // APK alone and says nothing about the split its native libraries live in
            val apkArchitecture = ApkArchitectureResolver.resolve(args.input, pm)
            val selectedCount = args.selectedPatches.values.sumOf { it.size }

            // Log device environment for diagnostics
            val deviceStats = applicationContext.deviceStats()

            // What this build of Morphe brings to the run. Every bug report needs the versions,
            // and native lib stripping silently changes what ends up in the output APK
            args.logger.info(
                "$LOG_WORKER_PREFIX_BUILD " +
                        "$LOG_WORKER_FIELD_MANAGER=${BuildConfig.VERSION_NAME} " +
                        "$LOG_WORKER_FIELD_PATCHER=${BuildConfig.PATCHER_VERSION} " +
                        "$LOG_WORKER_FIELD_NATIVE_LIBS=$stripNativeLibs"
            )

            args.logger.info(
                "$LOG_WORKER_PREFIX_DEVICE " +
                        "$LOG_WORKER_FIELD_ANDROID=${Build.VERSION.RELEASE} " +
                        "$LOG_WORKER_FIELD_API=${Build.VERSION.SDK_INT} " +
                        "$LOG_WORKER_FIELD_RAM_AVAIL=\"${formatBytesForReport(deviceStats?.ramAvailable ?: 0L)}\" " +
                        "$LOG_WORKER_FIELD_RAM_TOTAL=\"${formatBytesForReport(deviceStats?.ramTotal ?: 0L)}\" " +
                        "$LOG_WORKER_FIELD_STORAGE_AVAIL=\"${formatBytesForReport(deviceStats?.storageAvailable ?: 0L)}\" " +
                        "$LOG_WORKER_FIELD_STORAGE_TOTAL=\"${formatBytesForReport(deviceStats?.storageTotal ?: 0L)}\""
            )

            args.logger.info(
                "$LOG_WORKER_PREFIX_STARTED ${System.currentTimeMillis()} " +
                        "$LOG_WORKER_FIELD_PACKAGE=${args.packageName} " +
                        "$LOG_WORKER_FIELD_VERSION=${args.input.version} " +
                        "$LOG_WORKER_FIELD_INPUT=${inputFile.absolutePath} " +
                        "$LOG_WORKER_FIELD_SIZE=${inputFile.length()} " +
                        "$LOG_WORKER_FIELD_SPLIT=$inputIsSplitArchive " +
                        "$LOG_WORKER_FIELD_ARCH=$apkArchitecture " +
                        "$LOG_WORKER_FIELD_PATCHES=$selectedCount " +
                        "$LOG_WORKER_FIELD_DEVICE=${Build.MANUFACTURER} " +
                        "$LOG_WORKER_FIELD_MODEL=${Build.MODEL}"
            )

            // One line per source rather than a joined list, so a name and its version stay
            // together no matter how many sources contributed to this run
            args.patchSources.forEach { source ->
                args.logger.info(
                    "$LOG_WORKER_PREFIX_SOURCE $LOG_WORKER_FIELD_NAME=\"${source.name}\" " +
                            "$LOG_WORKER_FIELD_VERSION=\"${source.version ?: "?"}\""
                )
            }

            args.logger.logRuntime(vm)

            val options = args.options.restrictTo(args.selectedPatches)

            // After merging a split archive (in either runtime), save the resulting mono-APK
            // directly to originalApksDir so it is used for repatching instead of the archive.
            // The runtime is done with the file by then, so it is moved rather than copied.
            // A mount keeps the archive instead: the stock app it restores has to carry the
            // original signatures, which no longer verify on the merged APK
            val onMergedApkReady: suspend (File) -> Unit = { mergedFile ->
                val version = pm.getPackageInfo(mergedFile)?.versionName
                    ?.takeUnless { it.isBlank() }
                    ?: args.input.version
                    ?: "unknown"
                val savedFile = originalApkRepository.saveOriginalApk(
                    packageName = args.packageName,
                    version = version,
                    sourceFile = mergedFile,
                    moveSource = true
                )
                args.setInputFile(savedFile ?: mergedFile, true, true)
            }

            suspend fun Runtime.patchInput() = execute(
                inputFile.absolutePath,
                patchedApk.absolutePath,
                args.packageName,
                args.selectedPatches,
                options,
                args.logger,
                onPatchCompleted,
                args.onPatchFailed,
                ::updateProgress,
                stripNativeLibs,
                onMergedApkReady.takeUnless { args.useMount }
            )

            val elapsed = try {
                runtime(vm).patchInput()
            } catch (e: JvmUnavailableException) {
                // Nothing was patched yet, and ART can still take the run
                // The cause tells a JVM that crashed on start from one the device had no room for
                args.logger.warn("${e.message} (${e.cause?.javaClass?.simpleName}), patching on ART instead")
                Log.w(tag, "Patcher JVM unavailable".logFmt(), e)
                onRestart()
                args.logger.logRuntime(PatcherVm.ART)
                runtime(PatcherVm.ART).patchInput()
            }

            updatePatcherNotification(stepName = signingApkLabel, patchProgress = null)
            val signTime = measureTime {
                keystoreManager.sign(patchedApk)
                withContext(Dispatchers.IO) {
                    Files.move(patchedApk.toPath(), File(args.output).toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            args.logger.info("Signed apk in ${signTime.inWholeMilliseconds}ms")
            updateProgress(state = State.COMPLETED) // Signing

            args.logger.info(
                "$LOG_WORKER_PREFIX_SUCCEEDED $LOG_WORKER_FIELD_OUTPUT=${args.output} " +
                        "$LOG_WORKER_FIELD_SIZE=${File(args.output).length()} " +
                        "$LOG_WORKER_FIELD_ELAPSED=${elapsed.inWholeMilliseconds}ms"
            )

            Log.i(tag, "Patching succeeded".logFmt())
            val outputPackageName = pm.getPackageInfo(File(args.output))?.packageName ?: args.packageName
            autoInstallPending = installerManager.autoInstallAllowed(outputPackageName, mount = args.useMount)
            succeeded = true
            Result.success()
        } catch (e: PatcherKilledException) {
            Log.e(tag, "Patcher process ended: ${e.reason}".logFmt(), e)
            args.logger.error("Patcher process ended: ${e.reason}")
            val message = applicationContext.getString(
                if (e.crashed) R.string.patcher_process_crashed_message else R.string.patcher_process_killed_message
            )
            updateProgress(state = State.FAILED, message = message)
            Result.failure(
                workDataOf(FAILURE_MESSAGE_KEY to message)
            )
        } catch (e: HeapExhaustedException) {
            Log.e(
                tag,
                "Patcher exhausted its ${e.heapLimitMb}MB heap. ${e.originalStackTrace}".logFmt()
            )
            // The stack trace is already in the log; the failure itself says what the user can
            // act on, since no memory limit this device allows would have been enough
            val message = applicationContext.getString(
                R.string.patcher_out_of_memory_message,
                e.heapLimitMb.toString()
            )
            updateProgress(state = State.FAILED, message = message)
            Result.failure(
                workDataOf(FAILURE_MESSAGE_KEY to message)
            )
        } catch (e: PatcherFailureException) {
            Log.e(
                tag,
                "An exception occurred in the patcher process while patching. ${e.originalStackTrace}".logFmt()
            )
            updateProgress(state = State.FAILED, message = e.originalStackTrace)
            Result.failure(
                workDataOf(FAILURE_MESSAGE_KEY to e.originalStackTrace.truncateForWorkData())
            )
        } catch (e: Exception) {
            if (isStopped || e is CancellationException) {
                Log.i(tag, "Patching was cancelled".logFmt())
                throw e
            }
            Log.e(tag, "An exception occurred while patching".logFmt(), e)
            updateProgress(state = State.FAILED, message = e.stackTraceToString())
            Result.failure(
                workDataOf(FAILURE_MESSAGE_KEY to e.stackTraceToString().truncateForWorkData())
            )
        } finally {
            // Unmounted only to read the stock APK, so a run that produced nothing puts the patched one back
            if (unmounted && !succeeded) withContext(NonCancellable) {
                runCatching { rootInstaller.mount(args.packageName) }
                    .onFailure { Log.w(tag, "Failed to mount ${args.packageName} again".logFmt(), it) }
            }
            if (!patchedApk.delete() && patchedApk.exists()) {
                Log.w(tag, "Failed to delete temporary patched APK: ${patchedApk.absolutePath}".logFmt())
            }
            if (!isStopped && args.announceCompletion) showCompletionNotification(
                succeeded,
                autoInstallPending,
                completionSoundEnabled,
                successSoundUri,
                errorSoundUri
            )
        }
    }

    private fun Logger.logRuntime(vm: PatcherVm) {
        val name = when (vm) {
            PatcherVm.JVM -> RUNTIME_JVM
            PatcherVm.ART -> RUNTIME_ART
        }
        val heapLimitMb = patcherProcesses.heapLimitMb(vm)
        info("$LOG_WORKER_PREFIX_RUNTIME $LOG_WORKER_FIELD_NAME=$name $LOG_WORKER_FIELD_MEMORY_LIMIT=$heapLimitMb")
    }

    companion object {
        private const val LOG_PREFIX = "[Worker]"
        private fun String.logFmt() = "$LOG_PREFIX $this"

        const val NOTIFICATION_ID = 1
        const val COMPLETION_NOTIFICATION_ID = 2

        /** Kept as the patcher screen's entry point now that the tone itself is shared. */
        fun stopCompletionSound() = CompletionSound.stop()

        /** What a failed run failed on, in the output data of the work. */
        const val FAILURE_MESSAGE_KEY = "failure_message"

        const val RUNTIME_JVM = "jvm"
        const val RUNTIME_ART = "art"

        const val LOG_WORKER_PREFIX_STARTED = "Patching started at"
        const val LOG_WORKER_PREFIX_SUCCEEDED = "Patching succeeded:"
        const val LOG_WORKER_PREFIX_DEVICE = "Device:"
        const val LOG_WORKER_PREFIX_RUNTIME = "Runtime:"
        const val LOG_WORKER_PREFIX_SOURCE = "Source:"
        const val LOG_WORKER_PREFIX_BUILD = "Build:"

        const val LOG_WORKER_FIELD_PACKAGE = "pkg"
        const val LOG_WORKER_FIELD_INPUT = "input"
        const val LOG_WORKER_FIELD_SPLIT = "split"
        const val LOG_WORKER_FIELD_ARCH = "arch"
        const val LOG_WORKER_FIELD_PATCHES = "patches"
        const val LOG_WORKER_FIELD_DEVICE = "device"
        const val LOG_WORKER_FIELD_MODEL = "model"
        const val LOG_WORKER_FIELD_OUTPUT = "output"
        const val LOG_WORKER_FIELD_NAME = "name"
        const val LOG_WORKER_FIELD_VERSION = "version"
        const val LOG_WORKER_FIELD_MANAGER = "manager"
        const val LOG_WORKER_FIELD_PATCHER = "patcher"
        const val LOG_WORKER_FIELD_NATIVE_LIBS = "nativeLibs"
        const val LOG_WORKER_FIELD_SIZE = "size"
        const val LOG_WORKER_FIELD_MEMORY_LIMIT = "memoryLimit"
        const val LOG_WORKER_FIELD_ELAPSED = "elapsed"
        const val LOG_WORKER_FIELD_ANDROID = "android"
        const val LOG_WORKER_FIELD_API = "api"
        const val LOG_WORKER_FIELD_RAM_AVAIL = "ramAvail"
        const val LOG_WORKER_FIELD_RAM_TOTAL = "ramTotal"
        const val LOG_WORKER_FIELD_STORAGE_AVAIL = "storageAvail"
        const val LOG_WORKER_FIELD_STORAGE_TOTAL = "storageTotal"
    }
}
