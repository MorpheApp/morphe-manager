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

import app.morphe.manager.patcher.logger.Logger
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchResult
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal typealias PatchList = List<Patch<*>>

/**
 * @param keepArchitectures The native libraries the output keeps, every one of them when empty.
 */
class Session(
    temporaryDir: File,
    workspaceDir: File,
    private val logger: Logger,
    private val input: File,
    keepArchitectures: Set<CpuArchitecture>,
    private val listener: PatchingListener
) : Closeable {
    private val tempDir = temporaryDir.resolve("patcher").also { it.mkdirs() }

    // Scratch space for patches. Not under tempDir because the patcher wipes that directory
    // when it starts, and not the cache root to avoid colliding with the other caches there
    private val fileWorkspace = workspaceDir.also { it.mkdirs() }

    private val patcher = Patcher(
        PatcherConfig(
            apkFile = input,
            temporaryFilesPath = tempDir,
            // The patcher drops every other ABI while it writes the output, sparing a second pass over it
            keepArchitectures = keepArchitectures,
            fileWorkspacePath = fileWorkspace
        )
    )

    private suspend fun Patcher.applyPatchesVerbose(selectedPatches: PatchList) {
        listener.patchesStarted()

        this().collect { (patch, exception) ->
            if (patch !in selectedPatches) return@collect

            if (exception != null) {
                logger.error("${patch.name} failed:")
                logger.error(exception.stackTraceToString())
                listener.patchFailed(patch.name, exception.stackTraceToString())
                throw exception
            }

            listener.patchSucceeded(patch.name.orEmpty())

            logger.info("${patch.name} succeeded")
        }

        listener.patchesFinished(selectedPatches.size)
    }

    suspend fun run(output: File, selectedPatches: PatchList) {
        listener.stepCompleted() // Unpacking

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
        listener.stepCompleted() // Saving
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
