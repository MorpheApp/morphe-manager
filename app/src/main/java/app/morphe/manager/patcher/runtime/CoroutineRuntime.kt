/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/runtime/CoroutineRuntime.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.runtime

import android.content.Context
import app.morphe.manager.patcher.Session
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.patch.PatchBundle
import app.morphe.manager.patcher.patch.applyPatchOptions
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.worker.ProgressEventHandler
import app.morphe.manager.ui.model.State
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import java.io.File

/**
 * Simple [Runtime] implementation that runs the patcher using coroutines.
 */
class CoroutineRuntime(private val context: Context) : Runtime(context) {
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
        // This runtime patches in the app's own process and gets one attempt at it
        onRestart: suspend () -> Unit
    ): Int? {
        ResourceMonitor.startPolling(logger)

        try {
            val selectedBundles = selectedPatches.keys
            val bundles = bundles()

            // Only the selected bundles are read, one at a time, so a native death inside any of
            // them is attributed to it instead of leaving the next launch to crash the same way
            val allPatches = bundles
                .filterKeys { it in selectedBundles }
                .mapValues { (uid, bundle) ->
                    bundleLoadGuard.read(uid, File(bundle.patchesJar)) {
                        PatchBundle.Loader.patches(setOf(bundle), packageName).getValue(bundle)
                    }
                }

            val patchList = selectedPatches.flatMap { (bundle, selected) ->
                allPatches[bundle]?.filterKeys { it in selected }?.values
                    ?: throw IllegalArgumentException("Patch bundle $bundle does not exist")
            }

            // Set all patch options
            options.forEach { (bundle, bundlePatchOptions) ->
                val patchesByName = allPatches[bundle] ?: return@forEach

                patchesByName.applyPatchOptions(bundlePatchOptions, logger)
            }

            onProgress(null, State.COMPLETED, null) // Loading patches

            val preparation = SplitApkPreparer.prepareIfNeeded(
                source = File(inputFile),
                workspace = File(cacheDir),
                logger = logger,
                skipUnneededSplits = stripUnusedNativeLibs,
                onEvent = { event ->
                    val message = event.toLocalizedString(context)
                    logger.info(message)
                    onProgress(message, State.RUNNING, null)
                }
            )

            try {
                if (preparation.merged) {
                    onProgress(null, State.COMPLETED, null)
                }

                Session(
                    cacheDir = cacheDir,
                    frameworkDir = frameworkPath,
                    androidContext = context,
                    logger = logger,
                    input = preparation.file,
                    stripUnusedNativeLibs = stripUnusedNativeLibs,
                    onPatchCompleted = onPatchCompleted,
                    onProgress = onProgress
                ).use { session ->
                    session.run(
                        File(outputFile),
                        patchList
                    )
                }

                // Handed over only once the session has closed its input, as ProcessRuntime does
                if (preparation.merged) {
                    onMergedApkReady?.invoke(preparation.file)
                }
            } finally {
                preparation.cleanup()
            }
        } finally {
            ResourceMonitor.stopPolling(logger)
        }

        // Patching in the app's own process leaves no heap limit of its own to lower
        return null
    }
}
