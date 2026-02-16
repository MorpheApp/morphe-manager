package app.morphe.manager.patcher.runtime

import android.content.Context
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.aapt.Aapt
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.worker.ProgressEventHandler
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.FileNotFoundException

sealed class Runtime(context: Context) : KoinComponent {
    private val fs: Filesystem by inject()
    private val patchBundlesRepo: PatchBundleRepository by inject()
    protected val prefs: PreferencesManager by inject()

    protected val cacheDir: String = fs.tempDir.absolutePath
    protected val aaptPath = Aapt.binary(context)?.absolutePath
        ?: throw FileNotFoundException("Could not resolve aapt.")
    protected val frameworkPath: String =
        context.cacheDir.resolve("framework").also { it.mkdirs() }.absolutePath

    protected suspend fun bundles() = patchBundlesRepo.bundles.first()

    abstract suspend fun execute(
        inputFile: String,
        outputFile: String,
        packageName: String,
        selectedPatches: PatchSelection,
        options: Options,
        logger: Logger,
        onPatchCompleted: suspend () -> Unit,
        onProgress: ProgressEventHandler,
        stripNativeLibs: Boolean,
    )
}
