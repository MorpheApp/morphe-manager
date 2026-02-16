package app.morphe.manager.patcher.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.morphe.manager.R
import app.morphe.manager.MainActivity
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.worker.Worker
import app.morphe.manager.domain.worker.WorkerRepository
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.runtime.CoroutineRuntime
import app.morphe.manager.patcher.runtime.ProcessRuntime
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.util.NativeLibStripper
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.model.State
import app.morphe.manager.util.Options
import app.morphe.manager.util.PM
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.tag
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

typealias ProgressEventHandler = (name: String?, state: State?, message: String?) -> Unit

class PatcherWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker<PatcherWorker.Args>(context, parameters), KoinComponent {
    private val workerRepository: WorkerRepository by inject()
    private val prefs: PreferencesManager by inject()
    private val keystoreManager: KeystoreManager by inject()
    private val pm: PM by inject()
    private val fs: Filesystem by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val rootInstaller: RootInstaller by inject()

    class Args(
        val input: SelectedApp,
        val output: String,
        val selectedPatches: PatchSelection,
        val options: Options,
        val logger: Logger,
        val onPatchCompleted: suspend () -> Unit,
        val setInputFile: suspend (File, Boolean, Boolean) -> Unit,
        val onProgress: ProgressEventHandler
    ) {
        val packageName get() = input.packageName
    }

    override suspend fun getForegroundInfo() =
        ForegroundInfo(
            1,
            createNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

    private fun createNotification(): Notification {
        val notificationIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val channel = NotificationChannel(
            "morphe-patcher-patching", "Patching", NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager =
            applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        return Notification.Builder(applicationContext, channel.id)
            .setContentTitle(applicationContext.getText(R.string.patcher_notification_title))
            .setContentText(applicationContext.getText(R.string.patcher_notification_text))
            .setSmallIcon(Icon.createWithResource(applicationContext, R.drawable.ic_notification))
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override suspend fun doWork(): Result {
        if (runAttemptCount > 0) {
            Log.d(tag, "Android requested retrying but retrying is disabled.".logFmt())
            return Result.failure()
        }

        try {
            // This does not always show up for some reason.
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.d(tag, "Failed to set foreground info:", e)
        }

        val wakeLock: PowerManager.WakeLock =
            (applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$tag::Patcher")
                .apply {
                    acquire(10 * 60 * 1000L)
                    Log.d(tag, "Acquired wakelock.")
                }

        val args = workerRepository.claimInput(this)

        val result = try {
            runPatcher(args)
        } finally {
            wakeLock.release()
        }

        @SuppressLint("RestrictedApi") // FIXME
        if (result is Result.Success && args.input is SelectedApp.Local && args.input.temporary) {
            args.input.file.delete()
        }

        return result
    }

    private suspend fun runPatcher(args: Args): Result {

        fun updateProgress(name: String? = null, state: State? = null, message: String? = null) =
            args.onProgress(name, state, message)

        val patchedApk = fs.tempDir.resolve("patched.apk")

        return try {
            val startTime = System.currentTimeMillis()

            if (args.input is SelectedApp.Installed) {
                installedAppRepository.get(args.packageName)?.let {
                    if (it.installType == InstallType.MOUNT) {
                        rootInstaller.unmount(args.packageName)
                    }
                }
            }

            val inputFile = when (val selectedApp = args.input) {
                is SelectedApp.Download -> {
                    // Download type no longer supported after API module removal
                    throw UnsupportedOperationException("Download type is no longer supported")
                }

                is SelectedApp.Search -> {
                    // Search type no longer supported after API module removal
                    throw UnsupportedOperationException("Search type is no longer supported")
                }

                is SelectedApp.Local -> {
                    val needsSplit = SplitApkPreparer.isSplitArchive(selectedApp.file)
                    args.setInputFile(selectedApp.file, needsSplit, false)
                    selectedApp.file
                }

                is SelectedApp.Installed -> {
                    val source = File(pm.getPackageInfo(selectedApp.packageName)!!.applicationInfo!!.sourceDir)
                    args.setInputFile(source, false, false)
                    source
                }
            }

            val runtime = if (prefs.useProcessRuntime.get()) {
                ProcessRuntime(applicationContext)
            } else {
                CoroutineRuntime(applicationContext)
            }

            val stripNativeLibs = prefs.stripUnusedNativeLibs.get()
            val inputIsSplitArchive = SplitApkPreparer.isSplitArchive(inputFile)
            val selectedCount = args.selectedPatches.values.sumOf { it.size }

            args.logger.info(
                "Patching started at ${System.currentTimeMillis()} " +
                        "pkg=${args.packageName} version=${args.input.version} " +
                        "input=${inputFile.absolutePath} size=${inputFile.length()} " +
                        "split=$inputIsSplitArchive patches=$selectedCount"
            )

            runtime.execute(
                inputFile.absolutePath,
                patchedApk.absolutePath,
                args.packageName,
                args.selectedPatches,
                args.options,
                args.logger,
                args.onPatchCompleted,
                args.onProgress,
                stripNativeLibs
            )

            if (stripNativeLibs && !inputIsSplitArchive) {
                NativeLibStripper.strip(patchedApk)
            }

            keystoreManager.sign(patchedApk, File(args.output))
            updateProgress(state = State.COMPLETED) // Signing

            val elapsed = System.currentTimeMillis() - startTime
            val rt = Runtime.getRuntime()
            val usedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val totalMem = rt.totalMemory() / (1024 * 1024)

            args.logger.info(
                "Patching succeeded: output=${args.output} size=${File(args.output).length()} " +
                        "elapsed=${elapsed/1000}s memory=${usedMem}MB/${totalMem}MB"
            )

            Log.i(tag, "Patching succeeded".logFmt())
            Result.success()
        } catch (e: ProcessRuntime.ProcessExitException) {
            Log.e(
                tag,
                "Patcher process exited with code ${e.exitCode}".logFmt(),
                e
            )
            val message = applicationContext.getString(
                R.string.patcher_process_exit_message,
                e.exitCode
            )
            updateProgress(state = State.FAILED, message = message)
            val previousLimit = prefs.patcherProcessMemoryLimit.get()
            Result.failure(
                workDataOf(
                    PROCESS_EXIT_CODE_KEY to e.exitCode,
                    PROCESS_PREVIOUS_LIMIT_KEY to previousLimit,
                    PROCESS_FAILURE_MESSAGE_KEY to message
                )
            )
        } catch (e: ProcessRuntime.RemoteFailureException) {
            Log.e(
                tag,
                "An exception occurred in the remote process while patching. ${e.originalStackTrace}".logFmt()
            )
            updateProgress(state = State.FAILED, message = e.originalStackTrace)
            Result.failure(
                workDataOf(PROCESS_FAILURE_MESSAGE_KEY to e.originalStackTrace)
            )
        } catch (e: Exception) {
            Log.e(tag, "An exception occurred while patching".logFmt(), e)
            updateProgress(state = State.FAILED, message = e.stackTraceToString())
            Result.failure(
                workDataOf(PROCESS_FAILURE_MESSAGE_KEY to e.stackTraceToString())
            )
        } finally {
            patchedApk.delete()
        }
    }

    companion object {
        private const val LOG_PREFIX = "[Worker]"
        private fun String.logFmt() = "$LOG_PREFIX $this"
        const val PROCESS_EXIT_CODE_KEY = "process_exit_code"
        const val PROCESS_PREVIOUS_LIMIT_KEY = "process_previous_limit"
        const val PROCESS_FAILURE_MESSAGE_KEY = "process_failure_message"
    }
}
