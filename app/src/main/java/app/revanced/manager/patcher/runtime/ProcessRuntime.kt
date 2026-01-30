package app.revanced.manager.patcher.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.morphe.manager.BuildConfig
import app.revanced.manager.patcher.LibraryResolver
import app.revanced.manager.patcher.logger.Logger
import app.revanced.manager.patcher.runtime.process.IPatcherEvents
import app.revanced.manager.patcher.runtime.process.IPatcherProcess
import app.revanced.manager.patcher.runtime.process.Parameters
import app.revanced.manager.patcher.runtime.process.PatchConfiguration
import app.revanced.manager.patcher.runtime.process.PatcherProcess
import app.revanced.manager.patcher.worker.ProgressEventHandler
import app.revanced.manager.ui.model.State
import app.revanced.manager.util.Options
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.tag
import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.inject
import java.io.File
import kotlin.math.max

// Max memory value. Slightly higher values may work for some devices
// but patching YT is the same time with both 1200 and 1600 memory.
const val PROCESS_RUNTIME_MEMORY_MAX_LIMIT = 1536
const val PROCESS_RUNTIME_MEMORY_DEFAULT = 512
const val PROCESS_RUNTIME_MEMORY_DEFAULT_MINIMUM = 256
const val PROCESS_RUNTIME_MEMORY_LOW_WARNING = 384
const val PROCESS_RUNTIME_MEMORY_STEP = 128

/**
 * Runs the patcher in another process by using the app_process binary and IPC.
 */
class ProcessRuntime(private val context: Context) : Runtime(context) {
    private val pm: PM by inject()

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

        return try {
            withTimeout(10000L) {
                binderFuture.await()
            }
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
        onPatchCompleted: suspend () -> Unit,
        onProgress: ProgressEventHandler,
        stripNativeLibs: Boolean,
    ) = coroutineScope {
        val minMemoryLimit = 200
        var memoryMB = max(minMemoryLimit, prefs.patcherProcessMemoryLimit.get())
        var retried = false

        while (true) {
            try {
                executeWithMemory(
                    memoryMB,
                    inputFile,
                    outputFile,
                    packageName,
                    selectedPatches,
                    options,
                    stripNativeLibs,
                    logger,
                    onPatchCompleted,
                    onProgress
                )
                // Success - update preference and return.
                if (retried && prefs.patcherProcessMemoryLimit.get() != memoryMB) {
                    if (memoryMB < PROCESS_RUNTIME_MEMORY_DEFAULT) {
                        // Don't save a value lower than the expected minimum.
                        // Instead allow discovering the actually memory limit again next time.
                        memoryMB = PROCESS_RUNTIME_MEMORY_DEFAULT
                    }
                    Log.i(tag, "Updating process memory limit setting to: $memoryMB")
                    prefs.patcherProcessMemoryLimit.update(memoryMB)
                }

                return@coroutineScope
            } catch (e: Exception) {
                val isMemoryFailure = when (e) {
                    is ProcessExitException -> e.exitCode == OOM_EXIT_CODE || e.exitCode == SIGKILL_EXIT_CODE
                    is RemoteFailureException -> e.originalStackTrace.contains("OutOfMemoryError", ignoreCase = true)
                    else -> false
                }

                if (isMemoryFailure && memoryMB > minMemoryLimit) {
                    retried = true
                    memoryMB -= PROCESS_RUNTIME_MEMORY_STEP
                    Log.i(tag, "Process memory limit failed, retrying with: $memoryMB")
                    continue
                }
                throw e
            }
        }
    }

    private suspend fun executeWithMemory(
        memoryLimit: Int,
        inputFile: String,
        outputFile: String,
        packageName: String,
        selectedPatches: PatchSelection,
        options: Options,
        stripNativeLibs: Boolean,
        logger: Logger,
        onPatchCompleted: suspend () -> Unit,
        onProgress: ProgressEventHandler,
    ) = coroutineScope {
        // Get the location of our own Apk.
        val managerBaseApk = pm.getPackageInfo(context.packageName)!!.applicationInfo!!.sourceDir

        if (false) { // Morphe begin. Memory limit is handled automatically
        val requestedLimit = prefs.patcherProcessMemoryLimit.get()
        val sanitizedLimit = MemoryLimitConfig.clampLimitMb(context, requestedLimit)
        if (sanitizedLimit != requestedLimit) {
            Log.w(tag, "Requested process memory limit ${requestedLimit}MB exceeded device capabilities; clamped to ${sanitizedLimit}MB")
        }
        val limit = "${sanitizedLimit}M"
        } // Morphe end

        val propOverride = resolvePropOverride(context)?.absolutePath
            ?: throw Exception("Couldn't find prop override library")

        val heapSizeString = "${memoryLimit}M"
        val env =
            System.getenv().toMutableMap().apply {
                put("CLASSPATH", managerBaseApk)
                if (propOverride != null) {
                    // Override the props used by ART to set the memory limit.
                    put("LD_PRELOAD", propOverride)
                    put("PROP_dalvik.vm.heapgrowthlimit", heapSizeString)
                    put("PROP_dalvik.vm.heapsize", heapSizeString)
                } else {
                    Log.w(tag, "Skipping prop override on Android ${Build.VERSION.SDK_INT}")
                }
            }

        val appProcessBin = resolveAppProcessBin(context)

        launch(Dispatchers.IO) {
            val result = process(
                appProcessBin,
                "-Djava.io.tmpdir=$cacheDir", // The process will use /tmp if this isn't set, which is a problem because that folder is not accessible on Android.
                "/", // The unused cmd-dir parameter
                "--nice-name=${context.packageName}:Patcher",
                PatcherProcess::class.java.name, // The class with the main function.
                context.packageName,
                env = env,
                stdout = Redirect.CAPTURE,
                stderr = Redirect.CAPTURE,
            ) { line ->
                // The process shouldn't generally be writing to stdio. Log any lines we get as warnings.
                logger.warn("[STDIO]: $line")
            }

            Log.d(tag, "Process finished with exit code ${result.resultCode}")

            if (result.resultCode != 0) throw ProcessExitException(result.resultCode)
        }

        val patching = CompletableDeferred<Unit>()
        val scope = this

        launch(Dispatchers.IO) {
            val binder = awaitBinderConnection()
//            binderRef.set(binder)

            // Android Studio's fast deployment feature causes an issue where the other process will be running older code compared to the main process.
            // The patcher process is running outdated code if the randomly generated BUILD_ID numbers don't match.
            // To fix it, clear the cache in the Android settings or disable fast deployment (Run configurations -> Edit Configurations -> app -> Enable "always deploy with package manager").
            if (binder.buildId() != BuildConfig.BUILD_ID) throw Exception("app_process is running outdated code. Clear the app cache or disable disable Android 11 deployment optimizations in your IDE")

            val eventHandler = object : IPatcherEvents.Stub() {
                override fun log(level: String, msg: String) = logger.log(enumValueOf(level), msg)

                override fun patchSucceeded() {
                    scope.launch { onPatchCompleted() }
                }

                override fun progress(name: String?, state: String?, msg: String?) =
                    onProgress(name, state?.let { enumValueOf<State>(it) }, msg)

                override fun finished(exceptionStackTrace: String?) {
                    runCatching { binder.exit() }

                    exceptionStackTrace?.let {
                        patching.completeExceptionally(RemoteFailureException(it))
                        return
                    }
                    patching.complete(Unit)
                }
            }

            val parameters = Parameters(
                aaptPath = aaptPath,
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
                stripNativeLibs = stripNativeLibs
            )

            binder.start(parameters, eventHandler)
        }

        // Wait until patching finishes.
        patching.await()
    }

    companion object : LibraryResolver() {
        private const val APP_PROCESS_BIN_PATH = "/system/bin/app_process"
        private const val APP_PROCESS_BIN_PATH_64 = "/system/bin/app_process64"
        private const val APP_PROCESS_BIN_PATH_32 = "/system/bin/app_process32"
        const val OOM_EXIT_CODE = 134
        const val SIGKILL_EXIT_CODE = 137

        const val CONNECT_TO_APP_ACTION = "CONNECT_TO_APP_ACTION"
        const val INTENT_BUNDLE_KEY = "BUNDLE"
        const val BUNDLE_BINDER_KEY = "BINDER"

        private fun resolvePropOverride(context: Context) = findLibrary(context, "prop_override")
        private fun resolveAppProcessBin(context: Context): String {
            val is64Bit = context.applicationInfo.nativeLibraryDir.contains("64")
            val preferred = if (is64Bit) APP_PROCESS_BIN_PATH_64 else APP_PROCESS_BIN_PATH_32
            return if (File(preferred).exists()) preferred else APP_PROCESS_BIN_PATH
        }
    }

    /**
     * An [Exception] occurred in the remote process while patching.
     *
     * @param originalStackTrace The stack trace of the original [Exception].
     */
    class RemoteFailureException(val originalStackTrace: String) : Exception()

    class ProcessExitException(val exitCode: Int) :
        Exception("Process exited with nonzero exit code $exitCode")
}
