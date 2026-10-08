/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/runtime/ProcessRuntime.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.morphe.manager.BuildConfig
import app.morphe.manager.patcher.LibraryResolver
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.runtime.process.*
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.split.SplitPreparationEvent
import app.morphe.manager.patcher.worker.ProgressEventHandler
import app.morphe.manager.ui.model.State
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.Options
import app.morphe.manager.util.PM
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.bytesToMebibytes
import app.morphe.manager.util.tag
import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.coroutines.*
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Memory value that is safe everywhere. Slightly higher values may work for some devices
// but patching YT is the same time with both 1024 and 1600 memory.
// If too much memory is requested then some devices become extremely slow
// for unknown reason (using flash memory as swap file?)
const val PROCESS_RUNTIME_MEMORY_MINIMUM = 512
const val PROCESS_RUNTIME_MEMORY_MAX_LIMIT = 1280
private const val PROCESS_RUNTIME_MEMORY_MAX_LIMIT_INITIALIZATION = 1024
private const val PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM = 640
const val PROCESS_RUNTIME_MEMORY_LOW_WARNING = 640
const val PROCESS_RUNTIME_MEMORY_STEP = 128

// Apps carrying tens of thousands of classes across splits need more than the safe maximum to
// patch at all. The slowdown above it is real, so this range is offered under a warning, only
// where a heap this size can be mapped, and never as a default
private const val PROCESS_RUNTIME_MEMORY_EXTENDED_LIMIT = 2048

// Two steps at a time above the safe maximum. A run started up there has the whole extended
// range to cross before the device can hold it, and every retry patches the app from scratch
private const val PROCESS_RUNTIME_MEMORY_EXTENDED_STEP = 256

// Every retry patches the app again from the beginning, so a long ladder of them costs the
// user minutes of work and a hot device for an outcome that keeps getting less likely
const val PROCESS_RUNTIME_MEMORY_MAX_RETRIES = 2

// Sentinel value indicating the memory limit has never been set
// triggers adaptive calculation on first use
const val PROCESS_RUNTIME_MEMORY_NOT_SET = -1

// ART grants the overridden heap limit to the megabyte, so a clear shortfall means the firmware
// read the props some other way and the process runs with the device default instead
private const val HEAP_LIMIT_IGNORED_RATIO = 0.9

/**
 * The share of total device RAM the patcher may take, rounded down to a whole
 * [PROCESS_RUNTIME_MEMORY_STEP] so every limit derived from it lands on a value the slider shows.
 */
private fun deviceMemoryShare(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    val totalRamMb = bytesToMebibytes(memInfo.totalMem).toInt()
    return ((totalRamMb * 0.25).toInt() / PROCESS_RUNTIME_MEMORY_STEP) * PROCESS_RUNTIME_MEMORY_STEP
}

/**
 * Whether the manager runs as a 64-bit process. A 32-bit app_process has nowhere near enough
 * address space for an extended heap, and asking it for one only fails later and slower.
 */
private fun is64BitRuntime(context: Context) = context.applicationInfo.nativeLibraryDir.contains("64")

/**
 * Calculates an adaptive memory limit based on total device RAM, clamped between
 * [PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM] and [PROCESS_RUNTIME_MEMORY_MAX_LIMIT].
 *
 * Example results:
 *  2 GB RAM  → 640 MB
 *  3 GB RAM  → 768 MB
 *  4 GB RAM  → 1024 MB
 *  6 GB+ RAM → 1280 MB (capped)
 */
fun calculateAdaptiveMemoryLimit(context: Context) = deviceMemoryShare(context)
    .coerceIn(PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM, PROCESS_RUNTIME_MEMORY_MAX_LIMIT)

/**
 * The limit stored on first launch. Held below the safe maximum so the value a device starts
 * with stays conservative no matter how much RAM it reports.
 */
fun initialMemoryLimit(context: Context) =
    calculateAdaptiveMemoryLimit(context).coerceAtMost(PROCESS_RUNTIME_MEMORY_MAX_LIMIT_INITIALIZATION)

/**
 * Whether the extended range is offered at all. A heap that large needs a 64-bit address space,
 * and the props carrying the limit are only overridden on Android 11 and later, which is also
 * where a killed process is retried with less instead of simply failing.
 */
private fun supportsExtendedMemoryLimit(context: Context) =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && is64BitRuntime(context)

/**
 * The highest limit this device may be configured with. Devices that can hold a larger heap
 * reach into the extended range as far as their RAM allows, the rest stop at the safe maximum.
 */
fun maxMemoryLimit(context: Context) = deviceMemoryShare(context).coerceIn(
    PROCESS_RUNTIME_MEMORY_MAX_LIMIT_INITIALIZATION,
    if (supportsExtendedMemoryLimit(context)) PROCESS_RUNTIME_MEMORY_EXTENDED_LIMIT
    else PROCESS_RUNTIME_MEMORY_MAX_LIMIT
)

/**
 * Whether a limit is past the point where devices have been seen to crawl, which is what the
 * setting warns about and what makes the retry ladder take larger steps.
 */
fun isExtendedMemoryLimit(limit: Int) = limit > PROCESS_RUNTIME_MEMORY_MAX_LIMIT

/** Clamps a stored limit to what this device can be asked for, whatever an import carried. */
fun coerceMemoryLimit(context: Context, limit: Int) =
    limit.coerceIn(PROCESS_RUNTIME_MEMORY_MINIMUM, maxMemoryLimit(context))

/**
 * The limit to fall back to after a kill: one slider step down, or two in the extended range,
 * where a single step barely changes the footprint.
 */
fun lowerMemoryLimit(limit: Int): Int {
    val step = if (isExtendedMemoryLimit(limit)) PROCESS_RUNTIME_MEMORY_EXTENDED_STEP
    else PROCESS_RUNTIME_MEMORY_STEP

    return (limit - step).coerceAtLeast(PROCESS_RUNTIME_MEMORY_MINIMUM)
}

/**
 * Runs the patcher in another process by using the app_process binary and IPC.
 */
class ProcessRuntime(
    private val context: Context,
    // On Android Q and below, memory retry loop is unreliable - skip it and let the caller fall back
    private val skipMemoryRetry: Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
) : Runtime(context) {
    private val pm: PM by inject()
    private val appScope: AppCoroutineScope by inject()

    private suspend fun awaitBinderConnection(): IPatcherProcess {
        val binderFuture = CompletableDeferred<IPatcherProcess>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val binder =
                    intent.getBundleExtra(INTENT_BUNDLE_KEY)?.getBinder(BUNDLE_BINDER_KEY)!!

                binderFuture.complete(IPatcherProcess.Stub.asInterface(binder))
            }
        }

        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(CONNECT_TO_APP_ACTION)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Not withTimeout: its CancellationException would end the calling coroutine without failing
        // the run, which then waits forever on a process that never connected
        return try {
            withTimeoutOrNull(BINDER_CONNECTION_TIMEOUT) { binderFuture.await() }
                ?: throw ProcessConnectTimeoutException(BINDER_CONNECTION_TIMEOUT)
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    override suspend fun execute(
        inputFile: String,
        outputFile: String,
        packageName: String,
        selectedPatches: PatchSelection,
        options: Options,
        logger: Logger,
        onPatchCompleted: suspend (String) -> Unit,
        onProgress: ProgressEventHandler,
        stripUnusedNativeLibs: Boolean,
        onMergedApkReady: (suspend (File) -> Unit)?,
        onRestart: suspend () -> Unit
    ): Int? {
        var memoryMB = coerceMemoryLimit(context, prefs.patcherProcessMemoryLimit.get())
        var retries = 0

        while (true) {
            try {
                executeWithMemory(
                    memoryMB,
                    inputFile,
                    outputFile,
                    packageName,
                    selectedPatches,
                    options,
                    stripUnusedNativeLibs,
                    logger,
                    onPatchCompleted,
                    onProgress,
                    onMergedApkReady
                )

                return memoryMB.takeIf { retries > 0 }
            } catch (e: Exception) {
                val nextMemoryMB = lowerMemoryLimit(memoryMB)
                val retry = e.isReclaimableMemoryFailure() &&
                        !skipMemoryRetry &&
                        retries < PROCESS_RUNTIME_MEMORY_MAX_RETRIES &&
                        nextMemoryMB < memoryMB

                if (!retry) throw e

                memoryMB = nextMemoryMB
                retries++
                Log.i(tag, "Process memory limit failed, retrying with: $memoryMB")
                logger.warn(
                    "Patcher process was killed, restarting with a ${memoryMB}MB heap " +
                            "(attempt ${retries + 1} of ${PROCESS_RUNTIME_MEMORY_MAX_RETRIES + 1})"
                )
                // The attempt that just died reported patches and steps of its own. Everything
                // the next one reports starts from zero, so the listener has to as well
                onRestart()
            }
        }
    }

    /**
     * Whether a smaller heap stands a chance. These are kills from the outside: the pressure
     * came from the system, and giving the process less to hold makes it a smaller target.
     */
    private fun Exception.isReclaimableMemoryFailure() = this is ProcessExitException &&
            (exitCode == OOM_EXIT_CODE || exitCode == SIGKILL_EXIT_CODE || exitCode == SIGSEGV_EXIT_CODE)

    /**
     * Restates a heap the patcher filled on its own as [HeapExhaustedException]. Shrinking that
     * heap only reaches the same wall sooner, so it is reported rather than retried.
     */
    private fun remoteFailure(stackTrace: String, heapLimitMb: Int) =
        if (stackTrace.contains("OutOfMemoryError", ignoreCase = true)) {
            HeapExhaustedException(heapLimitMb, stackTrace)
        } else {
            RemoteFailureException(stackTrace)
        }

    private suspend fun executeWithMemory(
        memoryLimit: Int,
        inputFile: String,
        outputFile: String,
        packageName: String,
        selectedPatches: PatchSelection,
        options: Options,
        stripUnusedNativeLibs: Boolean,
        logger: Logger,
        onPatchCompleted: suspend (String) -> Unit,
        onProgress: ProgressEventHandler,
        onMergedApkReady: (suspend (File) -> Unit)?,
    ) = coroutineScope {
        // Get the location of our own Apk
        val managerBaseApk = pm.getPackageInfo(context.packageName)!!.applicationInfo!!.sourceDir
        val propOverride = resolvePropOverride(context)?.absolutePath

        val heapSizeString = "${memoryLimit}M"
        val env =
            System.getenv().toMutableMap().apply {
                put("CLASSPATH", managerBaseApk)
                if (propOverride != null) {
                    // Override the props used by ART to set the memory limit
                    put("LD_PRELOAD", propOverride)
                    put("PROP_dalvik.vm.heapgrowthlimit", heapSizeString)
                    put("PROP_dalvik.vm.heapsize", heapSizeString)
                } else {
                    Log.w(tag, "Skipping prop override on Android ${Build.VERSION.SDK_INT}")
                }
            }

        val appProcessBin = resolveAppProcessBin(context)

        // Determine merged APK path before launching the process so it is accessible
        // after patching.await() to invoke onMergedApkReady in the coroutineScope.
        val mergedInputPath = if (SplitApkPreparer.isSplitArchive(File(inputFile))) {
            File(cacheDir).resolve("merged-process-input-${System.currentTimeMillis()}.apk").absolutePath
        } else {
            null
        }

        // Listening before app_process starts, so a process that connects quickly is not missed
        val connection = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            awaitBinderConnection()
        }

        launch(Dispatchers.IO) {
            val result = process(
                appProcessBin,
                "-Djava.io.tmpdir=$cacheDir", // The process will use /tmp if this isn't set, which is a problem because that folder is not accessible on Android
                "/", // The unused cmd-dir parameter
                "--nice-name=${context.packageName}:Patcher",
                PatcherProcess::class.java.name, // The class with the main function
                context.packageName,
                env = env,
                stdout = Redirect.CAPTURE,
                stderr = Redirect.CAPTURE
            ) { line ->
                // The process shouldn't generally be writing to stdio. Log any lines we get as warnings
                logger.warn("[STDIO]: $line")
            }

            Log.d(tag, "Process finished with exit code ${result.resultCode}")

            if (result.resultCode != 0) throw ProcessExitException(result.resultCode, memoryLimit)
        }

        val patching = CompletableDeferred<Unit>()
        val scope = this
        // What ART actually granted, reported by the process once it is up. Zero until then
        val grantedHeapMb = AtomicInteger(0)
        // Held outside the launch so cancel() can tell app_process to exit and release its wakelock
        val binderRef = AtomicReference<IPatcherProcess?>()

        launch(Dispatchers.IO) {
            val binder = connection.await()
            binderRef.set(binder)

            // Android Studio's fast deployment feature causes an issue where the other process will be running older code compared to the main process.
            // The patcher process is running outdated code if the randomly generated BUILD_ID numbers don't match.
            // To fix it, clear the cache in the Android settings or disable fast deployment (Run configurations -> Edit Configurations -> app -> Enable "always deploy with package manager").
            if (binder.buildId() != BuildConfig.BUILD_ID)
                throw Exception("app_process is running outdated code. Clear the app cache or disable disable Android 11 deployment optimizations in your IDE")

            val eventHandler = object : IPatcherEvents.Stub() {
                override fun log(level: String, msg: String) = logger.log(enumValueOf(level), msg)

                override fun patchSucceeded(patchName: String) {
                    scope.launch { onPatchCompleted(patchName) }
                }

                override fun progress(name: String?, state: String?, msg: String?) =
                    onProgress(name, state?.let { enumValueOf<State>(it) }, msg)

                override fun splitProgress(eventType: String?, apkName: String?) {
                    val event = SplitPreparationEvent.fromWire(eventType, apkName) ?: return
                    val message = event.toLocalizedString(context)
                    logger.info(message)
                    onProgress(message, State.RUNNING, null)
                }

                override fun heapLimit(megabytes: Int) {
                    grantedHeapMb.set(megabytes)
                    // Without the override the process is meant to run with the device default
                    if (propOverride == null) return

                    val ignored = megabytes < memoryLimit * HEAP_LIMIT_IGNORED_RATIO
                    // Kept past this run, which may be torn down right below
                    appScope.launch { prefs.patcherHeapLimitIgnored.update(ignored) }
                    if (!ignored) return

                    // The app's own process gets its large heap the regular way, so a patcher
                    // left with less than that is better off patching there
                    if (megabytes < heapLimitMebibytes()) {
                        runCatching { binder.exit() }
                        patching.completeExceptionally(HeapLimitIgnoredException(memoryLimit, megabytes))
                    } else {
                        logger.warn(
                            "Patcher process got a ${megabytes}MB heap instead of ${memoryLimit}MB, " +
                                    "still more than the app's own"
                        )
                    }
                }

                override fun finished(exceptionStackTrace: String?) {
                    runCatching { binder.exit() }

                    exceptionStackTrace?.let {
                        val heapLimitMb = grantedHeapMb.get().takeIf { mb -> mb > 0 } ?: memoryLimit
                        patching.completeExceptionally(remoteFailure(it, heapLimitMb))
                        return
                    }
                    patching.complete(Unit)
                }
            }

            val parameters = Parameters(
                frameworkDir = frameworkPath,
                cacheDir = cacheDir,
                packageName = packageName,
                inputFile = inputFile,
                outputFile = outputFile,
                configurations = bundles().map { (uid, bundle) ->
                    PatchConfiguration(
                        bundle,
                        selectedPatches[uid].orEmpty(),
                        options[uid].orEmpty()
                    )
                },
                stripUnusedNativeLibs = stripUnusedNativeLibs,
                mergedInputFile = mergedInputPath
            )

            binder.start(parameters, eventHandler)
        }

        // Wait until patching finishes
        val mergedFile = mergedInputPath?.let { File(it) }
        try {
            patching.await()
            // If PatcherProcess merged a split archive, notify the caller so the merged APK
            // can be saved to originalApksDir for future repatching
            if (mergedFile?.exists() == true) {
                onMergedApkReady?.invoke(mergedFile)
            }
        } finally {
            // Tell app_process to exit on cancellation. After normal completion finished()
            // already called exit(), so runCatching swallows the DeadObjectException
            runCatching { binderRef.get()?.exit() }
            // Always clean up the temporary merged file regardless of success or failure
            mergedFile?.takeIf { it.exists() }?.delete()
        }
    }

    companion object : LibraryResolver() {
        private const val APP_PROCESS_BIN_PATH = "/system/bin/app_process"
        private const val APP_PROCESS_BIN_PATH_64 = "/system/bin/app_process64"
        private const val APP_PROCESS_BIN_PATH_32 = "/system/bin/app_process32"
        const val OOM_EXIT_CODE = 134
        const val SIGKILL_EXIT_CODE = 137
        const val SIGSEGV_EXIT_CODE = 139

        // The kernel kills a process over a system call the seccomp policy for apps forbids,
        // which firmware can drag in on its own, so such a run belongs in the app's own process
        const val SIGSYS_EXIT_CODE = 159

        // How long app_process gets to start and connect back to the app. Generous because a slow
        // device on a cold start can take many seconds, and giving up moves the run to the app's
        // own process, where the heap is usually smaller
        private val BINDER_CONNECTION_TIMEOUT = 30.seconds

        const val CONNECT_TO_APP_ACTION = "CONNECT_TO_APP_ACTION"
        const val INTENT_BUNDLE_KEY = "BUNDLE"
        const val BUNDLE_BINDER_KEY = "BINDER"

        private fun resolvePropOverride(context: Context) = findPropOverrideLibrary(context)
        private fun resolveAppProcessBin(context: Context): String {
            val preferred = if (is64BitRuntime(context)) APP_PROCESS_BIN_PATH_64 else APP_PROCESS_BIN_PATH_32
            return if (File(preferred).exists()) preferred else APP_PROCESS_BIN_PATH
        }
    }

    /**
     * An [Exception] occurred in the remote process while patching.
     *
     * @param originalStackTrace The stack trace of the original [Exception].
     */
    class RemoteFailureException(val originalStackTrace: String) : Exception()

    /**
     * @param exitCode The nonzero code the patcher process exited with.
     * @param heapLimitMb The limit the killed attempt ran with, which is not the stored setting
     *                    once the memory retries have lowered it.
     */
    class ProcessExitException(val exitCode: Int, val heapLimitMb: Int) :
        Exception("Process exited with nonzero exit code $exitCode")

    /**
     * The patcher process never connected back to the app, which slow devices have been seen to
     * do on a cold start, so the run cannot be handed to it.
     *
     * @param timeout How long the process was given to connect.
     */
    class ProcessConnectTimeoutException(timeout: Duration) :
        Exception("Patcher process did not connect within $timeout")

    /**
     * The patcher ran out of the heap it was given, which no smaller heap can fix. Carries the
     * heap the process actually had, since firmware that ignores the configured limit leaves it
     * with less than the number the user set.
     *
     * @param heapLimitMb The heap the run had, in megabytes.
     * @param originalStackTrace The stack trace of the [OutOfMemoryError].
     */
    class HeapExhaustedException(val heapLimitMb: Int, val originalStackTrace: String) :
        Exception("Patcher exhausted its ${heapLimitMb}MB heap")

    /**
     * The firmware ignored the heap limit the patcher process was started with and left it with
     * less memory than the app's own process has, so the run belongs there instead.
     *
     * @param requestedHeapMb The limit the process was started with, in megabytes.
     * @param grantedHeapMb The heap ART actually granted, in megabytes.
     */
    class HeapLimitIgnoredException(val requestedHeapMb: Int, val grantedHeapMb: Int) :
        Exception("Patcher process got a ${grantedHeapMb}MB heap instead of ${requestedHeapMb}MB")
}
