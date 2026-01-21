package app.revanced.manager.patcher

import android.content.Context
import app.morphe.library.ApkUtils.applyTo
import app.morphe.manager.R
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchResult
import app.revanced.manager.patcher.Session.Companion.component1
import app.revanced.manager.patcher.Session.Companion.component2
import app.revanced.manager.patcher.logger.Logger
import app.revanced.manager.ui.model.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal typealias PatchList = List<Patch<*>>

class Session(
    cacheDir: String,
    frameworkDir: String,
    aaptPath: String,
    private val androidContext: Context,
    private val logger: Logger,
    private val input: File,
    private val onPatchCompleted: suspend () -> Unit,
    private val onProgress: (name: String?, state: State?, message: String?) -> Unit
) : Closeable {
    private fun updateProgress(name: String? = null, state: State? = null, message: String? = null) =
        onProgress(name, state, message)

    private val tempDir = File(cacheDir).resolve("patcher").also { it.mkdirs() }
    private val patcher = Patcher(
        PatcherConfig(
            apkFile = input,
            temporaryFilesPath = tempDir,
            frameworkFileDirectory = frameworkDir,
            aaptBinaryPath = aaptPath
        )
    )

    private suspend fun Patcher.applyPatchesVerbose(selectedPatches: PatchList) {
        var nextPatchIndex = 0

        updateProgress(
            name = androidContext.getString(R.string.applying_patches),
            state = State.RUNNING
        )

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
                throw exception
            }

            nextPatchIndex++

            onPatchCompleted()


            if (false) // TODO: Enable this only when using advanced mode? Or never?
            selectedPatches.getOrNull(nextPatchIndex)?.let { nextPatch ->
                updateProgress(
                    name = androidContext.getString(R.string.applying_patch, nextPatch.name)
                )
            }

            logger.info("${patch.name} succeeded")
        }

        updateProgress(
            state = State.COMPLETED,
            name = androidContext.resources.getQuantityString(
                R.plurals.patches_executed,
                selectedPatches.size,
                selectedPatches.size
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

        with(patcher) {
            logger.info("Merging integrations")
            this += selectedPatches.toSet()

            logger.info("Applying patches...")
            applyPatchesVerbose(selectedPatches.sortedBy { it.name })
        }

        logger.info("Writing patched files...")
        val result = patcher.get()

        val patched = tempDir.resolve("result.apk")
        withContext(Dispatchers.IO) {
            Files.copy(input.toPath(), patched.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        result.applyTo(patched)

        logger.info("Patched apk saved to $patched")

        withContext(Dispatchers.IO) {
            Files.move(patched.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        updateProgress(state = State.COMPLETED) // Saving
    }

    override fun close() {
        tempDir.deleteRecursively()
        patcher.close()
    }

    companion object {
        operator fun PatchResult.component1() = patch
        operator fun PatchResult.component2() = exception
    }
}
