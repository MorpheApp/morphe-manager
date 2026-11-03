/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/runtime/Runtime.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.runtime

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.work.ForegroundInfo
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.DeviceConfig
import app.morphe.manager.patcher.PatchRequest
import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.patch.optionValueToJson
import app.morphe.manager.patcher.worker.ProgressEventHandler
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Patches in a process of its own on [vm], see [PatcherProcesses]. Everything around a run is the
 * same for each VM: the request it is given, what its reports turn into on screen, and the merged
 * APK it leaves behind.
 *
 * @param foreground The notification of the run, which its process holds as well.
 */
class Runtime(
    private val context: Context,
    private val fs: Filesystem,
    private val patchBundlesRepo: PatchBundleRepository,
    private val processes: PatcherProcesses,
    private val vm: PatcherVm,
    private val foreground: suspend () -> ForegroundInfo
) {
    /**
     * Patches [inputFile] into [outputFile].
     *
     * @param inputFile        Path of the APK or split archive to patch.
     * @param outputFile       Path the patched APK is written to.
     * @param packageName      Package of the app being patched.
     * @param selectedPatches  Patches to apply, per bundle.
     * @param options          Patch option values, per bundle.
     * @param logger           Sink for everything the run reports.
     * @param onPatchCompleted Called with the name of each patch that finished.
     * @param onPatchFailed    Called with the name of the patch the run failed on.
     * @param onProgress       Called as the run moves between steps.
     * @param stripUnusedNativeLibs Whether native libraries and split configurations the device
     *                         cannot use are dropped.
     * @param onMergedApkReady Called with the merged APK when a split archive patched successfully.
     *                         The runtime has no further use for the file, so it may be moved.
     * @return How long patching took.
     */
    suspend fun execute(
        inputFile: String,
        outputFile: String,
        packageName: String,
        selectedPatches: PatchSelection,
        options: Options,
        logger: Logger,
        onPatchCompleted: suspend (String) -> Unit,
        onPatchFailed: (String) -> Unit,
        onProgress: ProgressEventHandler,
        stripUnusedNativeLibs: Boolean,
        onMergedApkReady: (suspend (File) -> Unit)? = null
    ): Duration {
        val mergedApk = fs.tempDir.resolve("merged-${System.currentTimeMillis()}.apk")
        val request = PatchRequest(
            packageName = packageName,
            inputFile = inputFile,
            outputFile = outputFile,
            mergedApkFile = mergedApk.absolutePath,
            temporaryDir = fs.tempDir.absolutePath,
            workspaceDir = context.cacheDir.resolve("patch-workspace").absolutePath,
            bundles = requestedBundles(selectedPatches, options),
            stripUnusedNativeLibs = stripUnusedNativeLibs,
            device = deviceConfig()
        )
        val listener = ProgressReporter(context, logger, onProgress, onPatchCompleted, onPatchFailed)

        try {
            val elapsed = measureTime { patch(request, logger, listener) }
            if (mergedApk.exists()) onMergedApkReady?.invoke(mergedApk)
            return elapsed
        } finally {
            mergedApk.delete()
        }
    }

    /** Runs [request], reporting through [logger] and [listener], and throws if it fails. */
    private suspend fun patch(request: PatchRequest, logger: Logger, listener: PatchingListener) {
        // A file of its own, as the process of a canceled run may still be reading its request
        val requestFile = withContext(Dispatchers.IO) {
            File.createTempFile("patch-request", ".json", fs.tempDir).apply {
                writeText(Json.encodeToString(PatchRequest.serializer(), request))
            }
        }
        try {
            processes.run(requestFile, request.bundles.map { it.patchesFile }, vm, foreground(), logger, listener)
        } finally {
            requestFile.delete()
        }
    }

    private suspend fun requestedBundles(selectedPatches: PatchSelection, options: Options) =
        patchBundlesRepo.bundles.first().let { bundles ->
            selectedPatches.map { (uid, patches) ->
                val bundle = bundles[uid] ?: throw IllegalArgumentException("Patch bundle $uid does not exist")
                PatchRequest.Bundle(
                    uid = uid,
                    patchesFile = bundle.patchesJar,
                    patches = patches,
                    options = options[uid].orEmpty().mapValues { (_, patchOptions) ->
                        patchOptions.mapValues { (_, value) -> optionValueToJson(value) }
                    }
                )
            }
        }

    // The system's own configuration rather than the app's, which an in-app language overrides
    private fun deviceConfig(): DeviceConfig {
        val system = Resources.getSystem()
        val locales = system.configuration.locales
        return DeviceConfig(
            supportedAbis = Build.SUPPORTED_ABIS.filter { it.isNotBlank() },
            localeTags = (0 until locales.size()).map { locales[it].toLanguageTag() },
            densityDpi = system.displayMetrics.densityDpi
        )
    }
}
