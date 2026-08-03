/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.batch

import android.app.Application
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.morphe.manager.ManagerApplication
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.worker.WorkerRepository
import app.morphe.manager.patcher.patch.PatchSourceRef
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.worker.PatcherWorker
import app.morphe.manager.ui.model.PatchRunProgress
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.CompletionSound
import app.morphe.manager.util.Options
import app.morphe.manager.util.PM
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.PatchSelectionUtils.sanitizeForPatcher
import app.morphe.manager.util.UpdateNotificationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "Morphe BatchPatcher"

/**
 * Drives a batch patch run: one app at a time, no user interaction between items.
 *
 * The coordinator lives on the application scope rather than in a ViewModel because a run
 * takes minutes per app and must survive navigating away from the batch screen. Only the
 * patcher itself runs in [PatcherWorker], which keeps the foreground service and wakelock
 * that the single-app flow already relies on.
 */
class BatchPatchCoordinator(
    private val app: Application,
    private val fs: Filesystem,
    private val pm: PM,
    private val prefs: PreferencesManager,
    private val resolver: BatchPlanResolver,
    private val workerRepository: WorkerRepository,
    private val patchBundleRepository: PatchBundleRepository,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val patchOptionsRepository: PatchOptionsRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val originalApkRepository: OriginalApkRepository,
    private val notificationManager: UpdateNotificationManager,
    private val scope: AppCoroutineScope
) {
    private val workManager = WorkManager.getInstance(app)

    private val _state = MutableStateFlow<BatchRunState?>(null)
    val state: StateFlow<BatchRunState?> = _state.asStateFlow()

    private var runJob: Job? = null
    private var activeWorkId: UUID? = null

    /** Workspace for APKs copied out of installed apps and for patcher output. */
    private val workspace: File get() = fs.uiTempDir.resolve("batch")

    /** True while the queue is patching, used to block a competing single-app run. */
    val isRunning: Boolean get() = _state.value?.isActive == true

    /**
     * Builds a plan for [packageNames] and parks it in the preflight phase so the user can
     * attach missing APKs or drop items before anything is patched.
     */
    fun plan(
        packageNames: List<String>,
        useMount: Boolean,
        policy: BatchInstallPolicy,
        scheduled: Boolean = false
    ) {
        if (isRunning) return
        runJob?.cancel()
        runJob = scope.launch {
            _state.value = BatchRunState(
                items = packageNames.map { placeholder(it) },
                phase = BatchPhase.PLANNING,
                policy = policy,
                useMount = useMount,
                scheduled = scheduled
            )
            val items = resolver.resolve(packageNames, useMount)
            _state.update { it.copy(items = items, phase = BatchPhase.PREFLIGHT) }
        }
    }

    /** Re-resolves a single item against a manually attached APK. */
    fun attachApk(packageName: String, file: File) {
        val current = _state.value ?: return
        if (current.phase != BatchPhase.PREFLIGHT) return
        val item = current.items.firstOrNull { it.packageName == packageName } ?: return

        scope.launch {
            val resolved = resolver.reattach(item, file, current.useMount)
            _state.update { state ->
                state.copy(items = state.items.map { if (it.packageName == packageName) resolved else it })
            }
        }
    }

    /** Re-resolves one item against the APK the user picked, saved original or installed app. */
    fun useSource(packageName: String, preferInstalled: Boolean) {
        val current = _state.value ?: return
        if (current.phase != BatchPhase.PREFLIGHT) return
        val item = current.items.firstOrNull { it.packageName == packageName } ?: return

        scope.launch {
            val resolved = resolver.useSource(item, current.useMount, preferInstalled)
            _state.update { state ->
                state.copy(items = state.items.map { if (it.packageName == packageName) resolved else it })
            }
        }
    }

    /** Toggles an item between excluded and its resolved state. */
    fun toggleExcluded(packageName: String) {
        _state.update { state ->
            if (state.phase != BatchPhase.PREFLIGHT) return@update state
            state.copy(
                items = state.items.map { item ->
                    if (item.packageName != packageName) return@map item
                    when (item.state) {
                        BatchItemState.EXCLUDED -> item.copy(
                            state = item.restoreState ?: BatchItemState.READY,
                            restoreState = null
                        )

                        else -> item.copy(
                            state = BatchItemState.EXCLUDED,
                            restoreState = item.state
                        )
                    }
                }
            )
        }
    }

    /**
     * Accepts an unsupported version for one item, mirroring the single-app warning dialog.
     *
     * The item is resolved again rather than just flipped to runnable, because accepting the
     * version is what brings in the patches that declare a different one. Without that the
     * app would be patched with nothing but the universal patches.
     */
    fun forceVersion(packageName: String) {
        val current = _state.value ?: return
        if (current.phase != BatchPhase.PREFLIGHT) return
        val item = current.items.firstOrNull { it.packageName == packageName } ?: return
        if (item.state != BatchItemState.VERSION_MISMATCH) return

        scope.launch {
            val resolved = resolver.forceVersion(item, current.useMount)
            _state.update { state ->
                state.copy(items = state.items.map { if (it.packageName == packageName) resolved else it })
            }
        }
    }

    /**
     * Replaces what one queued app will be patched with, after the user edited it on the
     * preflight screen. Deselecting everything leaves nothing to run, which is the same
     * situation as a source that contributes no patches.
     */
    fun updateSelection(packageName: String, selection: PatchSelection, options: Options) {
        _state.update { state ->
            if (state.phase != BatchPhase.PREFLIGHT) return@update state
            state.copy(
                items = state.items.map { item ->
                    if (item.packageName != packageName) return@map item

                    val empty = selection.values.sumOf { it.size } == 0
                    item.copy(
                        selection = selection,
                        options = options,
                        state = when {
                            empty -> BatchItemState.NO_PATCHES
                            item.state == BatchItemState.NO_PATCHES -> BatchItemState.READY
                            else -> item.state
                        }
                    )
                }
            )
        }
    }

    /**
     * Restricts one queued app to a single patch source, the question simple mode answers
     * before a single-app patch. The full plan is kept so another source can still be picked.
     *
     * Options are left alone: they are keyed by source, and the patcher ignores those it has
     * no selected patch for.
     */
    fun narrowToSource(packageName: String, bundleUid: Int) {
        _state.update { state ->
            if (state.phase != BatchPhase.PREFLIGHT) return@update state
            state.copy(
                items = state.items.map { item ->
                    if (item.packageName != packageName) return@map item

                    val resolved = item.resolvedSelection ?: item.selection
                    item.copy(
                        resolvedSelection = resolved,
                        selection = resolved.filterKeys { it == bundleUid }
                    )
                }
            )
        }
    }

    fun setPolicy(policy: BatchInstallPolicy) {
        _state.update { it.copy(policy = policy) }
    }

    /** Records what the installer did with one patched app, for the summary to show. */
    fun markInstallResult(
        packageName: String,
        outcome: BatchInstallOutcome,
        message: String? = null,
        installedPackageName: String? = null
    ) {
        _state.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.packageName == packageName) {
                        item.copy(
                            installOutcome = outcome,
                            installMessage = message,
                            installedPackageName = installedPackageName ?: item.installedPackageName
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    /** Starts the queue. Items that are not [BatchItemState.READY] are left untouched. */
    fun start() {
        val current = _state.value ?: return
        if (current.phase != BatchPhase.PREFLIGHT) return
        if (current.runnable.isEmpty()) return

        runJob?.cancel()
        runJob = scope.launch {
            _state.update { it.copy(phase = BatchPhase.RUNNING) }
            try {
                runQueue()
            } catch (e: CancellationException) {
                markRemainingCancelled()
                throw e
            } finally {
                withContext(NonCancellable) {
                    activeWorkId = null
                    _state.update { it.copy(phase = BatchPhase.FINISHED, activeIndex = null, activeRun = null) }
                    announceCompletion()
                }
            }
        }
    }

    /** Cancels the active item and marks everything still queued as canceled. */
    fun cancel() {
        activeWorkId?.let(workManager::cancelWorkById)
        runJob?.cancel()
        runJob = null
        markRemainingCancelled()
        _state.update { it.copy(phase = BatchPhase.FINISHED, activeIndex = null, activeRun = null) }
    }

    /**
     * Drops a finished run. The workspace is kept until the next run starts so patched APKs
     * stay installable while the summary is still open.
     */
    fun clear() {
        if (isRunning) return
        runJob?.cancel()
        runJob = null
        _state.value = null
    }

    /**
     * Reports a finished queue once, instead of once per app: a queue of eight would otherwise
     * ping eight times, and no single app finishing is something the user can act on.
     *
     * Scheduled runs stay silent. They are reported by their own worker and often finish at
     * night, which is the last time anyone wants a tone.
     */
    private suspend fun announceCompletion() {
        val state = _state.value ?: return
        if (state.scheduled) return
        if (state.succeeded == 0 && state.failed == 0) return

        // The summary screen already says all of this when the user is watching it, the same
        // rule a single run follows. The tone still plays, it is what draws them back
        if (!ManagerApplication.isInForeground) {
            notificationManager.showBatchCompletionNotification(
                patched = state.succeeded,
                failed = state.failed,
                skipped = state.skipped
            )
        }

        if (prefs.patcherCompletionSound.get()) {
            CompletionSound.play(
                context = app,
                succeeded = state.succeeded > 0,
                successSoundUri = prefs.patcherSuccessSoundUri.get(),
                errorSoundUri = prefs.patcherErrorSoundUri.get()
            )
        }
    }

    private suspend fun runQueue() {
        // Leftovers from a previous run are dropped here rather than when it finished, so its
        // patched APKs stayed installable for as long as the summary was on screen
        withContext(Dispatchers.IO) {
            runCatching { workspace.deleteRecursively() }
            workspace.mkdirs()
        }

        while (true) {
            val current = _state.value ?: return
            val index = current.items.indexOfFirst { it.state.isRunnable }
            if (index < 0) return

            updateItem(index) { it.copy(state = BatchItemState.RUNNING) }

            val item = _state.value?.items?.get(index) ?: return
            runItem(index, item)
        }
    }

    private suspend fun runItem(index: Int, item: BatchPatchItem) {
        // Published before the APK is prepared: copying one takes seconds, and until a run of
        // its own exists the screen would still be showing the previous app's finished steps
        val runProgress = withContext(Dispatchers.IO) {
            PatchRunProgress(
                context = app,
                scope = scope,
                totalPatches = item.patchCount,
                splitStepActive = item.source?.isSplitArchive() == true
            )
        }
        runProgress.startStallWatch()
        _state.update { it.copy(activeIndex = index, activeRun = runProgress) }

        val selectedApp = withContext(Dispatchers.IO) { materialize(item) }
        if (selectedApp == null) {
            runProgress.stopStallWatch()
            updateItem(index) {
                it.copy(
                    state = BatchItemState.FAILED,
                    message = app.getString(R.string.batch_patch_source_unavailable)
                )
            }
            return
        }

        val outputFile = workspace.resolve("${item.packageName}.apk")

        // Only the sources actually contributing patches, so narrowing an app to one source
        // is reflected in the log as well as on the card
        val patchSources = item.bundles
            .filter { it.uid in item.selection.keys }
            .map { PatchSourceRef(it.name, it.version) }

        val args = PatcherWorker.Args(
            input = selectedApp,
            output = outputFile.path,
            selectedPatches = item.selection,
            options = item.options.sanitizeForPatcher(),
            logger = runProgress.logger,
            onPatchCompleted = { runProgress.onPatchCompleted() },
            setInputFile = { file, needsSplit, merged ->
                runProgress.updateSplitRequirement(file, needsSplit, merged)
            },
            onProgress = runProgress::onProgress,
            patchSources = patchSources,
            announceCompletion = false,
            queuePosition = _state.value?.let { it.processed to it.total }
        )

        val workId = workerRepository.launchExpedited<PatcherWorker, PatcherWorker.Args>(args)
        activeWorkId = workId

        try {
            val finished = workManager.getWorkInfoByIdFlow(workId).first { it?.state?.isFinished == true }

            when (finished?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val retained = withContext(NonCancellable) {
                        finishSuccessfulItem(item, selectedApp, outputFile)
                    }
                    updateItem(index) {
                        it.copy(state = BatchItemState.SUCCEEDED, patchedFile = retained, message = null)
                    }
                }

                WorkInfo.State.CANCELLED -> updateItem(index) { it.copy(state = BatchItemState.CANCELLED) }

                else -> {
                    val failure = finished?.outputData
                        ?.getString(PatcherWorker.PROCESS_FAILURE_MESSAGE_KEY)
                        ?.lineSequence()
                        ?.firstOrNull { it.isNotBlank() }
                    updateItem(index) { it.copy(state = BatchItemState.FAILED, message = failure) }
                }
            }
        } finally {
            // Cancelling the queue cancels this coroutine mid-wait, and the stall watch runs on
            // the application scope, so it would otherwise poll forever
            withContext(NonCancellable) {
                activeWorkId = null
                runProgress.stopStallWatch()
                // Retaining the APKs takes seconds, and this run has nothing left to show but
                // completed steps. The screen switches to its between-apps state instead
                _state.update { it.copy(activeIndex = null, activeRun = null) }
                withContext(Dispatchers.IO) { cleanupInput(selectedApp) }
            }
        }
    }

    /**
     * Copies the source APK into the workspace when it lives outside it. Installed apps are
     * materialized here rather than during planning so excluded items never pay the cost.
     */
    private fun materialize(item: BatchPatchItem): SelectedApp? {
        val source = item.source ?: return null
        workspace.mkdirs()

        return when (source) {
            is BatchApkSource.SavedOriginal -> SelectedApp.Local(
                packageName = item.packageName,
                version = source.version,
                versionCode = source.versionCode,
                file = source.file,
                temporary = false
            )

            is BatchApkSource.UserFile -> SelectedApp.Local(
                packageName = item.packageName,
                version = source.version,
                versionCode = source.versionCode,
                file = source.file,
                temporary = false
            )

            is BatchApkSource.Installed -> try {
                val target = if (source.isSplit) {
                    workspace.resolve("${item.packageName}_installed.apks")
                        .also { createApksArchive(source, it) }
                } else {
                    workspace.resolve("${item.packageName}_installed.apk")
                        .also { File(source.apkPath).copyTo(it, overwrite = true) }
                }
                SelectedApp.Local(
                    packageName = item.packageName,
                    version = source.version,
                    versionCode = source.versionCode,
                    file = target,
                    temporary = true,
                    fromInstalledDevice = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to materialize installed APK for ${item.packageName}", e)
                null
            }
        }
    }

    /** Whether the source needs merging, known before it is copied into the workspace. */
    private fun BatchApkSource.isSplitArchive(): Boolean = when (this) {
        is BatchApkSource.Installed -> isSplit
        is BatchApkSource.SavedOriginal -> SplitApkPreparer.isSplitArchive(file)
        is BatchApkSource.UserFile -> SplitApkPreparer.isSplitArchive(file)
    }

    private fun cleanupInput(selectedApp: SelectedApp) {
        val local = selectedApp as? SelectedApp.Local ?: return
        if (local.temporary) local.file.delete()
    }

    /**
     * Retains the original and patched APKs and records the patched app, so the summary can
     * install it and the app shows up on the home screen exactly like a single-app run.
     *
     * @return the file the summary installs from, or null when the patched APK is gone.
     */
    private suspend fun finishSuccessfulItem(
        item: BatchPatchItem,
        selectedApp: SelectedApp,
        outputFile: File
    ): File? = withContext(Dispatchers.IO) {
        if (!outputFile.exists()) return@withContext null

        val patchedInfo = pm.getPackageInfo(outputFile)
        val version = patchedInfo?.versionName?.takeUnless { it.isBlank() }
            ?: item.version
            ?: "unspecified"
        val currentPackageName = patchedInfo?.packageName ?: item.packageName

        saveOriginalApk(item, selectedApp, version)

        // What the user chose to patch with is recorded whether the APK is kept: the
        // two are separate settings, and the summary can still install from the workspace
        patchSelectionRepository.updateSelection(
            item.packageName,
            item.selection,
            // Scoped to every source the plan considered, so a source the user ended up taking
            // nothing from has its stale selection cleared rather than left behind
            scope = item.bundles.mapTo(mutableSetOf()) { it.uid }.ifEmpty { item.selection.keys }
        )
        // Without this the next plan cannot tell a patch the user deselected from one that was
        // added since, and would keep re-enabling every deselected default
        item.bundles.forEach { bundle ->
            patchSelectionRepository.saveSeenPatches(
                packageName = item.packageName,
                bundleUid = bundle.uid,
                patchNames = bundle.patchNames
            )
        }
        // Simple mode derives its options from the per-app preference screen rather than the
        // database, so only an expert-mode selection is worth writing back
        if (prefs.useExpertMode.get()) {
            patchOptionsRepository.saveOptions(item.packageName, item.options)
        }

        if (!prefs.savePatchedApks.get()) {
            // Retention is off, so the patched APK only lives long enough for the summary
            // to install it and is dropped together with the workspace
            return@withContext outputFile
        }

        val retained = fs.getPatchedAppFile(currentPackageName, version)
        val stored = runCatching {
            retained.parentFile?.mkdirs()
            outputFile.copyTo(retained, overwrite = true)
            retained
        }.getOrElse {
            Log.w(TAG, "Failed to retain patched APK for ${item.packageName}", it)
            outputFile
        }

        val selectionPayload = patchBundleRepository.snapshotSelection(item.selection)
        installedAppRepository.addOrUpdate(
            currentPackageName,
            item.packageName,
            version,
            InstallType.SAVED,
            item.selection,
            selectionPayload
        )

        stored
    }

    /**
     * Keeps the unpatched APK for future runs. Split archives are skipped because the patcher
     * already stored the merged mono-APK it built from them, which is the better input for a
     * later run than the archive itself.
     */
    private suspend fun saveOriginalApk(
        item: BatchPatchItem,
        selectedApp: SelectedApp,
        version: String
    ) {
        val file = (selectedApp as? SelectedApp.Local)?.file?.takeIf { it.exists() } ?: return
        if (SplitApkPreparer.isSplitArchive(file)) return

        val existing = originalApkRepository.get(item.packageName)
        if (existing != null && existing.version == version && File(existing.filePath).exists()) return

        runCatching {
            originalApkRepository.saveOriginalApk(
                packageName = item.packageName,
                version = version,
                sourceFile = file
            )
        }.onFailure { Log.w(TAG, "Failed to save original APK for ${item.packageName}", it) }
    }

    /**
     * Packs a base APK and its splits into an APKS archive so the patcher can merge them.
     * Entries are stored uncompressed because APKs are already compressed archives.
     */
    private fun createApksArchive(source: BatchApkSource.Installed, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun addEntry(file: File) {
                val crc = CRC32()
                val buffer = ByteArray(65536)
                FileInputStream(file).use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) crc.update(buffer, 0, read)
                }
                val entry = ZipEntry(file.name).apply {
                    method = ZipEntry.STORED
                    size = file.length()
                    compressedSize = file.length()
                    this.crc = crc.value
                }
                zip.putNextEntry(entry)
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            addEntry(File(source.apkPath))
            source.splitPaths.forEach { addEntry(File(it)) }
        }
    }

    private fun markRemainingCancelled() {
        _state.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.state == BatchItemState.READY || item.state == BatchItemState.RUNNING) {
                        item.copy(state = BatchItemState.CANCELLED)
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun placeholder(packageName: String) = BatchPatchItem(
        packageName = packageName,
        appName = packageName,
        source = null,
        selection = emptyMap(),
        options = emptyMap(),
        bundles = emptyList(),
        state = BatchItemState.NEEDS_APK
    )

    private fun updateItem(index: Int, transform: (BatchPatchItem) -> BatchPatchItem) {
        _state.update { state ->
            state.copy(
                items = state.items.mapIndexed { i, item -> if (i == index) transform(item) else item }
            )
        }
    }

    /**
     * Applies [transform] only when a run exists. Progress callbacks arrive from the patcher
     * worker's own coroutines, so the compare-and-set loop below keeps concurrent updates
     * from overwriting each other.
     */
    private inline fun MutableStateFlow<BatchRunState?>.update(
        transform: (BatchRunState) -> BatchRunState
    ) {
        while (true) {
            val current = value ?: return
            val updated = transform(current)
            if (compareAndSet(current, updated)) return
        }
    }

}
