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

import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.worker.ProgressEventHandler
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.bytesToMebibytes
import kotlinx.coroutines.flow.first
import java.io.File

/** The heap limit ART granted the calling process, in mebibytes. */
fun heapLimitMebibytes() = bytesToMebibytes(java.lang.Runtime.getRuntime().maxMemory()).toInt()

/** Outcome of a patcher runtime execution. */
data class ExecutionResult(
    /** The heap limit in MB the run only finished under after lowering it, null when original held. */
    val loweredMemoryLimit: Int? = null,
    /** Elapsed time taken by the actual patching execution, in milliseconds. */
    val elapsedTimeMs: Long = 0L
)

sealed class Runtime(
    fs: Filesystem,
    private val patchBundlesRepo: PatchBundleRepository,
    protected val prefs: PreferencesManager
) {
    protected val cacheDir: String = fs.tempDir.absolutePath

    protected suspend fun bundles() = patchBundlesRepo.bundles.first()

    /** Only of use to a runtime that reads bundles in this process, see [CoroutineRuntime]. */
    protected val bundleLoadGuard get() = patchBundlesRepo.loadGuard

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
     * @param onRestart        Called when the current attempt is abandoned and patching starts over,
     *                         so progress reported so far can be dropped instead of accumulating.
     * @return Execution result containing the lowered memory limit if lowered, and actual elapsed time.
     */
    abstract suspend fun execute(
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
        onMergedApkReady: (suspend (File) -> Unit)? = null,
        onRestart: suspend () -> Unit = {},
    ): ExecutionResult
}
