/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/Session.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher

import android.content.Context
import app.morphe.manager.R
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.util.NativeLibs
import app.morphe.manager.ui.model.State
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal typealias PatchList = List<Patch<*>>

class Session(
    cacheDir: String,
    frameworkDir: String,
    private val androidContext: Context,
    private val logger: Logger,
    private val input: File,
    stripUnusedNativeLibs: Boolean,
    private val onPatchCompleted: suspend (String) -> Unit,
    private val onPatchFailed: (String) -> Unit,
    private val onProgress: (name: String?, state: State?, message: String?) -> Unit
) : Closeable {
    private fun updateProgress(name: String? = null, state: State? = null, message: String? = null) =
        onProgress(name, state, message)

    private val tempDir = File(cacheDir).resolve("patcher").also { it.mkdirs() }

    // Scratch space for patches. Not under tempDir because the patcher wipes that directory
    // when it starts, and not the cache root to avoid colliding with the other caches there
    private val fileWorkspace =
        androidContext.cacheDir.resolve("patch-workspace").also { it.mkdirs() }

    private val patcher = Patcher(
        PatcherConfig(
            apkFile = input,
            temporaryFilesPath = tempDir,
            frameworkFileDirectory = frameworkDir,
            // The patcher drops every other ABI while it writes the output, sparing a second pass over it
            keepArchitectures = if (stripUnusedNativeLibs) NativeLibs.keptArchitectures(input) else emptySet(),
            fileWorkspacePath = fileWorkspace
        )
    )

    private suspend fun Patcher.applyPatchesVerbose(selectedPatches: PatchList) {
        updateProgress(state = State.RUNNING)

        this().collect { (patch, exception) ->
            if (patch !in selectedPatches) return@collect

            if (exception != null) {
                updateProgress(
                    name = androidContext.getString(R.string.failed_to_execute_patch, patch.name),
                    state = State.FAILED,
                    message = exception.stackTraceToString()
                )

                logger.error("${patch.name} failed:")
                logger.error(exception.stackTraceToString())
                patch.name?.let(onPatchFailed)
                throw exception
            }

            onPatchCompleted(patch.name.orEmpty())

            logger.info("${patch.name} succeeded")
        }

        updateProgress(
            state = State.COMPLETED,
            name = androidContext.resources.getQuantityString(
                R.plurals.patches_executed,
                selectedPatches.size,
                selectedPatches.size.toString()
            )
        )
    }

    suspend fun run(output: File, selectedPatches: PatchList) {
        updateProgress(state = State.COMPLETED) // Unpacking

        java.util.logging.Logger.getLogger("").apply {
            handlers.forEach {
                it.close()
                removeHandler(it)
            }

            addHandler(logger.handler)
        }

        val patchTime = measureTime {
            with(patcher) {
                this += selectedPatches.toSet()

                logger.info("Applying patches...")
                applyPatchesVerbose(selectedPatches.sortedBy { it.name })
            }
        }

        logger.info("Writing patched files...")
        val (result, compileTime) = withContext(Dispatchers.Default) {
            // patcher.get() writes dex files, then encodes resources, so run on default pool
            // instead of main thread.
            measureTimedValue { patcher.get() }
        }

        val writeTime = measureTime {
            val patched = result.resources.resourcesApk ?: tempDir.resolve("result.apk").also { fallback ->
                withContext(Dispatchers.IO) {
                    Files.copy(input.toPath(), fallback.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }

            withContext(Dispatchers.Default) {
                // Run on default pool instead of I/O since we're processing large files in our own code
                result.applyTo(patched)
            }

            withContext(Dispatchers.IO) {
                Files.move(patched.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        logger.info(
            "Patched apk saved to $output (patch=${patchTime.inWholeMilliseconds}ms " +
                "compile=${compileTime.inWholeMilliseconds}ms write=${writeTime.inWholeMilliseconds}ms)"
        )
        updateProgress(state = State.COMPLETED) // Saving
    }

    override fun close() {
        tempDir.deleteRecursively()
        fileWorkspace.deleteRecursively()
        patcher.close()
    }

    companion object {
        operator fun PatchResult.component1() = patch
        operator fun PatchResult.component2() = exception
    }
}
