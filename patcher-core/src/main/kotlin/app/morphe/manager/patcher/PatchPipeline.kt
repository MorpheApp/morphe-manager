/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher

import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.patch.applyPatchOptions
import app.morphe.manager.patcher.patch.keyedFor
import app.morphe.manager.patcher.patch.optionValueFromJson
import app.morphe.manager.patcher.runtime.ResourceMonitor
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_HEAP_LIMIT_PREFIX
import app.morphe.manager.patcher.runtime.heapLimitMebibytes
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.split.SplitPreparationEvent
import app.morphe.manager.patcher.util.NativeLibs
import app.morphe.patcher.patch.Patch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One run from the patch bundles to the patched APK, the same in whichever VM it happens.
 *
 * @param loadPatches Reads the patches of a bundle, which is the one step that depends on the VM:
 *                    ART loads them from dex, the JVM from class files.
 */
class PatchPipeline(
    private val request: PatchRequest,
    private val logger: Logger,
    private val listener: PatchingListener,
    private val loadPatches: suspend (PatchRequest.Bundle) -> Collection<Patch<*>>
) {
    suspend fun run() {
        ResourceMonitor.startPolling(logger)
        try {
            logger.info("$LOG_HEAP_LIMIT_PREFIX ${heapLimitMebibytes()}MB")
            patch(selectedPatches())
        } finally {
            ResourceMonitor.stopPolling(logger)
        }
    }

    private suspend fun selectedPatches(): List<Patch<*>> {
        val selected = request.bundles.flatMap { bundle ->
            val patches = loadPatches(bundle).keyedFor(request.packageName)
            patches.applyPatchOptions(
                bundle.options.mapValues { (_, options) -> options.mapValues { (_, value) -> optionValueFromJson(value) } },
                logger
            )
            patches.filterKeys { it in bundle.patches }.values
        }
        listener.stepCompleted() // Loading patches
        return selected
    }

    private suspend fun patch(patches: List<Patch<*>>) {
        val preparation = prepareInput()
        try {
            val input = if (preparation.merged) {
                // Moved rather than patched where it was merged, so it outlives the cleanup below
                // and the app can keep it for repatching
                withContext(Dispatchers.IO) {
                    Files.move(
                        preparation.file.toPath(),
                        File(request.mergedApkFile).toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    ).toFile()
                }.also { listener.stepCompleted() }
            } else {
                preparation.file
            }

            val keepArchitectures =
                if (request.stripUnusedNativeLibs) withContext(Dispatchers.IO) {
                    NativeLibs.keptArchitectures(input, request.device.supportedAbis)
                } else {
                    emptySet()
                }

            Session(
                temporaryDir = File(request.temporaryDir),
                workspaceDir = File(request.workspaceDir),
                logger = logger,
                input = input,
                keepArchitectures = keepArchitectures,
                listener = listener
            ).use { it.run(File(request.outputFile), patches) }
        } finally {
            preparation.cleanup()
        }
    }

    // The preparation reports from the threads it blocks on, the listener is only called from here
    private suspend fun prepareInput() = coroutineScope {
        val events = Channel<SplitPreparationEvent>(Channel.UNLIMITED)
        val forwarding = launch { for (event in events) listener.splitPreparation(event) }

        try {
            SplitApkPreparer.prepareIfNeeded(
                source = File(request.inputFile),
                workspace = File(request.temporaryDir),
                device = request.device,
                logger = logger,
                skipUnneededSplits = request.stripUnusedNativeLibs,
                onEvent = { events.trySend(it) }
            )
        } finally {
            events.close()
            forwarding.join()
        }
    }
}
