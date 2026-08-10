package app.morphe.manager.patcher.runtime

import android.content.Context
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.worker.ProgressEventHandler
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

sealed class Runtime(context: Context) : KoinComponent {
    private val fs: Filesystem by inject()
    private val patchBundlesRepo: PatchBundleRepository by inject()
    protected val prefs: PreferencesManager by inject()

    protected val cacheDir: String = fs.tempDir.absolutePath
    protected val frameworkPath: String =
        context.cacheDir.resolve("framework").also { it.mkdirs() }.absolutePath

    protected suspend fun bundles() = patchBundlesRepo.bundles.first()

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
     * @param onProgress       Called as the run moves between steps.
     * @param skipUnneededSplits Whether split configurations the device cannot use are dropped.
     * @param onMergedApkReady Called with the merged APK when the input was a split archive.
     * @param onRestart        Called when the current attempt is abandoned and patching starts over,
     *                         so progress reported so far can be dropped instead of accumulating.
     */
    abstract suspend fun execute(
        inputFile: String,
        outputFile: String,
        packageName: String,
        selectedPatches: PatchSelection,
        options: Options,
        logger: Logger,
        onPatchCompleted: suspend (String) -> Unit,
        onProgress: ProgressEventHandler,
        skipUnneededSplits: Boolean,
        onMergedApkReady: (suspend (File) -> Unit)? = null,
        onRestart: suspend () -> Unit = {},
    )
}
