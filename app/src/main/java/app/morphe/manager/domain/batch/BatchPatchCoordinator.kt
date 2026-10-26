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
import app.morphe.manager.domain.installer.InstallResult
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.SessionInstaller
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
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val sourceMuteRepository: SourceMuteRepository,
    private val notificationManager: UpdateNotificationManager,
    private val installerManager: InstallerManager,
    private val sessionInstaller: SessionInstaller,
    private val json: Json,
    private val scope: AppCoroutineScope
) {
    private val workManager = WorkManager.getInstance(app)

    private val _state = MutableStateFlow<BatchRunState?>(null)
    val state: StateFlow<BatchRunState?> = _state.asStateFlow()

    private var runJob: Job? = null
    private var activeWorkId: UUID? = null

    // The latest re-resolve per item, so an older one finishing last cannot undo the user's newer pick
    private val resolveJobs = ConcurrentHashMap<String, Job>()

    // Once this process has planned, storage only mirrors its state and may lag behind a clear
    @Volatile
    private var hasPlanned = false

    /** Workspace for APKs copied out of installed apps and for patcher output. */
    private val workspace: File get() = fs.uiTempDir.resolve("batch")

    /** True while the queue is patching, used to block a competing single-app run. */
    val isRunning: Boolean get() = _state.value?.isActive == true

    /** The queue waiting to be started as last stored, which outlives the process unlike [state]. */
    val savedQueue: StateFlow<SavedBatchQueue?> = prefs.batchQueue.flow
        .map(::decodeQueue)
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // Leading empty states are skipped: they only mean nothing was planned in this process,
        // and a queue stored by an earlier one has to survive until it is picked back up
        scope.launch {
            _state
                .map { it?.savedQueue }
                .distinctUntilChanged()
                .dropWhile { it == null }
                .collect { queue -> prefs.batchQueue.update(queue?.let { json.encodeToString(it) }.orEmpty()) }
        }

        // The plan offers each app the sources it is not kept from, so a change to that, made
        // from the queue or anywhere else, is answered by planning the affected apps again
        scope.launch {
            var previous: Map<String, Set<Int>>? = null
            sourceMuteRepository.mutedSources.collect { muted ->
                val last = previous
                previous = muted
                if (last == null) return@collect
                val changed = (last.keys + muted.keys).filterTo(mutableSetOf()) { last[it] != muted[it] }
                if (changed.isNotEmpty()) refreshApps(changed)
            }
        }
    }

    /**
     * Adds [targets] to the queue being prepared, or plans a new one around them. A queue stored
     * before the process died is planned again first, so the apps added to it earlier stay.
     */
    fun enqueue(targets: List<BatchTarget>) {
        if (isRunning) return
        val pending = runJob
        runJob = scope.launch {
            // Planning still under way finishes first, so its apps are there to add to
            pending?.join()
            val current = _state.value
            when (current?.phase) {
                BatchPhase.PREFLIGHT -> append(current, targets)
                BatchPhase.PLANNING, BatchPhase.RUNNING -> Unit
                null, BatchPhase.FINISHED -> {
                    val saved = if (hasPlanned) null else decodeQueue(prefs.batchQueue.get())
                    planFresh(
                        targets = (saved?.targets.orEmpty() + targets).distinctBy { it.id },
                        policy = saved?.policy ?: BatchInstallPolicy.SAVE_ONLY,
                        excludedIds = saved?.excluded.orEmpty()
                    )
                }
            }
        }
    }

    private suspend fun planFresh(
        targets: List<BatchTarget>,
        policy: BatchInstallPolicy,
        excludedIds: Set<String>
    ) {
        fun BatchPatchItem.withExclusion() = if (id in excludedIds) excluded() else this

        hasPlanned = true
        _state.value = BatchRunState(
            items = targets.map { placeholder(it).withExclusion() },
            phase = BatchPhase.PLANNING,
            policy = policy
        )
        val items = resolver.resolve(targets).map { it.withExclusion() }
        _state.update { it.copy(items = items, phase = BatchPhase.PREFLIGHT) }
    }

    /** Resolves only the apps [current] does not hold yet, leaving what the user set up on the rest. */
    private suspend fun append(current: BatchRunState, targets: List<BatchTarget>) {
        val queued = current.items.mapTo(mutableSetOf()) { it.id }
        val added = targets.distinctBy { it.id }.filter { it.id !in queued }
        if (added.isEmpty()) return

        _state.update { it.copy(items = it.items + added.map(::placeholder), phase = BatchPhase.PLANNING) }
        val resolved = resolver.resolve(added).associateBy { it.id }
        _state.update { state ->
            state.copy(items = state.items.map { resolved[it.id] ?: it }, phase = BatchPhase.PREFLIGHT)
        }
    }

    private fun decodeQueue(stored: String): SavedBatchQueue? {
        if (stored.isBlank()) return null
        return runCatching { json.decodeFromString<SavedBatchQueue>(stored) }
            .onFailure { Log.w(TAG, "Failed to decode the saved batch queue", it) }
            .getOrNull()
            ?.takeIf { it.targets.isNotEmpty() }
    }

    /**
     * Plans the queued apps among [packageNames] again while the queue is being prepared. An app
     * whose patches were edited by hand keeps them, since planning it again would drop the edit.
     */
    private suspend fun refreshApps(packageNames: Set<String>) {
        val current = _state.value ?: return
        if (current.phase != BatchPhase.PREFLIGHT) return
        val stale = current.items.filter {
            it.packageName in packageNames && !it.selectionEdited && resolveJobs[it.id]?.isActive != true
        }
        if (stale.isEmpty()) return

        val refreshed = coroutineScope {
            stale.map { async { replan(it) } }.awaitAll()
        }.associateBy { it.id }

        _state.update { state ->
            if (state.phase != BatchPhase.PREFLIGHT) return@update state
            state.copy(items = state.items.map { refreshed[it.id] ?: it })
        }
    }

    /** Plans [item] again, still left out of the run if the user left it out. */
    private suspend fun replan(item: BatchPatchItem): BatchPatchItem {
        val resolved = resolver.refresh(item)
        return if (item.state == BatchItemState.EXCLUDED) resolved.excluded() else resolved
    }

    /** Plans one queued app again with [resolve], provided it is still in [expectedState] when given. */
    private fun reresolveItem(
        itemId: String,
        expectedState: BatchItemState? = null,
        resolve: suspend (item: BatchPatchItem) -> BatchPatchItem
    ): Job? {
        val current = _state.value ?: return null
        if (current.phase != BatchPhase.PREFLIGHT) return null
        val item = current.items.firstOrNull { it.id == itemId } ?: return null
        if (expectedState != null && item.state != expectedState) return null

        val job = scope.launch {
            val resolved = resolve(item)
            // The queue may have started meanwhile, and an item it runs must not be swapped out
            _state.update { state ->
                if (state.phase != BatchPhase.PREFLIGHT) return@update state
                state.copy(items = state.items.map { if (it.id == itemId) resolved else it })
            }
        }
        resolveJobs.put(itemId, job)?.cancel()
        return job
    }

    /** Re-resolves a single item against a manually attached APK. */
    fun attachApk(itemId: String, file: File) =
        reresolveItem(itemId) { item -> resolver.reattach(item, file) }

    /** Re-resolves one item against the APK the user picked, saved original or installed app. */
    fun useSource(itemId: String, preferInstalled: Boolean) =
        reresolveItem(itemId) { item -> resolver.useSource(item, preferInstalled) }

    /**
     * Patches one queued app for the install target the user picked, the question a rooted device
     * asks before a single-app patch. Planned again, since the patches available differ per target.
     */
    fun setInstallTarget(itemId: String, useMount: Boolean) =
        reresolveItem(itemId) { item -> replan(item.copy(useMount = useMount)) }

    /** Toggles an item between excluded and its resolved state. */
    fun toggleExcluded(itemId: String) {
        _state.update { state ->
            if (state.phase != BatchPhase.PREFLIGHT) return@update state
            state.copy(
                items = state.items.map { item ->
                    when {
                        item.id != itemId -> item
                        item.state == BatchItemState.EXCLUDED -> item.included()
                        else -> item.excluded()
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
    fun forceVersion(itemId: String) =
        reresolveItem(itemId, BatchItemState.VERSION_MISMATCH, resolver::forceVersion)

    /**
     * Accepts an APK the bundles do not vouch for, mirroring the single-app warning dialog.
     *
     * The item is resolved again rather than just flipped to runnable, because everything the
     * run needs was left unresolved while the file was in question.
     */
    fun acceptUnverifiedSignature(itemId: String) =
        reresolveItem(itemId, BatchItemState.UNVERIFIED_SIGNATURE, resolver::acceptUnverifiedSignature)

    /**
     * Replaces what one queued app will be patched with, after the user edited it on the
     * preflight screen. Deselecting everything leaves nothing to run until a patch is picked again.
     */
    fun updateSelection(itemId: String, selection: PatchSelection, options: Options) {
        _state.update { state ->
            if (state.phase != BatchPhase.PREFLIGHT) return@update state
            state.copy(
                items = state.items.map { item ->
                    if (item.id != itemId) return@map item

                    val edited = item.copy(selection = selection, options = options, selectionEdited = true)
                    edited.copy(state = edited.stateForSelection(item.state))
                }
            )
        }
    }

    /**
     * Patches one queued app from a single source, the question simple mode answers before a
     * single-app patch. The app is planned again from it, so its version warnings and patches
     * are that source's own.
     *
     * The choice is written before planning starts, so the refresh that remembering it sets off
     * reads it as well.
     */
    fun chooseSource(itemId: String, bundleUid: Int) {
        _state.update { state ->
            if (state.phase != BatchPhase.PREFLIGHT) return@update state
            state.copy(items = state.items.map { if (it.id == itemId) it.copy(chosenBundleUid = bundleUid) else it })
        }
        reresolveItem(itemId, resolve = ::replan)
    }

    fun setPolicy(policy: BatchInstallPolicy) {
        _state.update { it.copy(policy = policy) }
    }

    fun markAutoInstallStarted() {
        _state.update { it.copy(autoInstallStarted = true) }
    }

    /** Records what the installer did with one patched app, for the summary to show. */
    fun markInstallResult(
        itemId: String,
        outcome: BatchInstallOutcome,
        message: String? = null,
        installedPackageName: String? = null
    ) {
        _state.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id == itemId) {
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
                    installUnattendedIfRequested()
                    _state.update {
                        it.copy(
                            phase = BatchPhase.FINISHED,
                            activeIndex = null,
                            activeRun = null,
                            finishedInForeground = ManagerApplication.isInForeground
                        )
                    }
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
     * Drops a finished run or a queue that was never started. The workspace is kept until the
     * next run starts so patched APKs stay installable while the summary is still open.
     */
    fun clear() {
        if (isRunning) return
        runJob?.cancel()
        runJob = null
        _state.value = null
    }

    /**
     * Plans the apps that failed or were canceled again as a queue of their own, keeping what the
     * user set up on each: the APK, the source, an accepted warning and an edited selection.
     */
    fun retryUnfinished() {
        if (isRunning) return
        val current = _state.value ?: return
        val unfinished = current.items.filter {
            it.state == BatchItemState.FAILED || it.state == BatchItemState.CANCELLED
        }
        if (unfinished.isEmpty()) return

        runJob?.cancel()
        runJob = scope.launch {
            _state.value = BatchRunState(
                items = unfinished.map { placeholder(it.target) },
                phase = BatchPhase.PLANNING,
                policy = current.policy
            )
            val items = unfinished.map { item ->
                val resolved = resolver.refresh(item)
                if (!item.selectionEdited) return@map resolved
                resolved.copy(
                    selection = item.selection,
                    options = item.options,
                    selectionEdited = true,
                    // The edited selection is what ran, so it has patches even where the defaults have none
                    state = item.stateForSelection(resolved.state)
                )
            }
            _state.update { it.copy(items = items, phase = BatchPhase.PREFLIGHT) }
        }
    }

    /**
     * Installs what the run produced before the queue reports itself finished, for a run the
     * user left. The batch screen installs from its own summary, but only while it is on
     * screen: it stops collecting the run once the activity is stopped, so a queue that ended
     * in the background would sit there until the manager came back.
     *
     * Installing here rather than there is only possible without a confirmation dialog, so a
     * run the user is watching keeps going through the summary, which shows its progress.
     */
    private suspend fun installUnattendedIfRequested() {
        val state = _state.value ?: return
        if (state.policy != BatchInstallPolicy.INSTALL_AFTER) return
        if (ManagerApplication.isInForeground) return

        installPatchedUnattended(state.patchedItems)
    }

    /**
     * Installs [items] through Shizuku, the only installer that never asks, and records what
     * happened on the run. Whatever does not go in stays on the summary, where the user
     * installs it through the interactive installer.
     */
    private suspend fun installPatchedUnattended(items: List<BatchPatchItem>) {
        val token = installerManager.getPrimaryToken()
        val asPlayStore = token == InstallerManager.Token.ShizukuPlayStore
        if (token != InstallerManager.Token.Shizuku && !asPlayStore) {
            Log.d(TAG, "Primary installer cannot install unattended")
            return
        }

        for (item in items) {
            if (item.installOutcome == BatchInstallOutcome.INSTALLED) continue
            // A mount build is only of use mounted over its app, which the summary does
            if (item.useMount) continue
            val file = item.patchedFile?.takeIf { it.exists() } ?: continue
            val info = pm.getPackageInfo(file)
            val targetPackage = info?.packageName ?: item.packageName

            val result = runCatching {
                if (asPlayStore) {
                    sessionInstaller.installShizukuAsPlayStore(file, targetPackage)
                } else {
                    sessionInstaller.installShizuku(file, targetPackage)
                }
            }.getOrElse { error ->
                Log.w(TAG, "Unattended install failed for $targetPackage", error)
                null
            }

            if (result !is InstallResult.Success) continue

            val version = info?.versionName?.takeUnless { it.isBlank() } ?: item.version ?: continue
            installedAppRepository.addOrUpdate(
                currentPackageName = targetPackage,
                originalPackageName = item.packageName,
                isClone = item.producesCloneAs(targetPackage),
                version = version,
                installType = if (asPlayStore) InstallType.SHIZUKU_PLAY_STORE else InstallType.SHIZUKU,
                patchSelection = item.selection,
                selectionPayload = patchBundleRepository.snapshotSelection(item.selection)
            )
            markInstallResult(
                itemId = item.id,
                outcome = BatchInstallOutcome.INSTALLED,
                installedPackageName = targetPackage
            )
        }
    }

    /**
     * Reports a finished queue once, instead of once per app: a queue of eight would otherwise
     * ping eight times, and no single app finishing is something the user can act on.
     */
    private suspend fun announceCompletion() {
        val state = _state.value ?: return
        if (!state.hasOutcome) return

        // The summary screen already says all of this when the user is watching it, the same
        // rule a single run follows. The tone still plays, it is what draws them back
        if (!ManagerApplication.isInForeground) {
            notificationManager.showBatchCompletionNotification(
                patched = state.succeeded,
                failed = state.failed,
                skipped = state.skipped
            )
        }

        // A queue stopped before its end closes quietly rather than sounding like a result
        if (prefs.patcherCompletionSound.get() && !state.wasStopped) {
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
            runProgress.finish()
            updateItem(index) {
                it.copy(
                    state = BatchItemState.FAILED,
                    message = app.getString(R.string.batch_patch_source_unavailable)
                )
            }
            return
        }

        // Named after the item rather than the app, so two clones of it never share a path
        val outputFile = workspace.resolve("${item.id}.apk")

        // Only the sources actually contributing patches, so a source the user took nothing
        // from is left out of the log as well as off the card
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
            onPatchFailed = runProgress::onPatchFailed,
            onPatchingRestarted = { runProgress.onRestart() },
            // Called on the worker's thread, while the steps are only ever changed on the main one
            setInputFile = { file, needsSplit, merged ->
                withContext(Dispatchers.Main) { runProgress.updateSplitRequirement(file, needsSplit, merged) }
            },
            onProgress = runProgress::onProgress,
            patchSources = patchSources,
            announceCompletion = false,
            queuePosition = _state.value?.let { it.position to it.total },
            useMount = item.useMount
        )

        val workId = workerRepository.launchExpedited<PatcherWorker, PatcherWorker.Args>(args)
        activeWorkId = workId
        var succeeded = false

        try {
            val finished = workManager.getWorkInfoByIdFlow(workId).first { it?.state?.isFinished == true }

            when (finished?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val retained = withContext(NonCancellable) {
                        finishSuccessfulItem(item, selectedApp, outputFile)
                    }
                    succeeded = true
                    updateItem(index) {
                        it.copy(state = BatchItemState.SUCCEEDED, patchedFile = retained, message = null)
                    }
                }

                WorkInfo.State.CANCELLED -> updateItem(index) { it.copy(state = BatchItemState.CANCELLED) }

                else -> {
                    val failure = finished?.outputData
                        ?.getString(PatcherWorker.FAILURE_MESSAGE_KEY)
                        ?.lineSequence()
                        ?.firstOrNull { it.isNotBlank() }
                    updateItem(index) {
                        it.copy(state = BatchItemState.FAILED, message = failure, failedPatch = runProgress.failedPatch)
                    }
                }
            }
        } finally {
            // Cancelling the queue cancels this coroutine mid-wait, and the stall watch runs on
            // the application scope, so it would otherwise poll forever
            withContext(NonCancellable) {
                activeWorkId = null
                runProgress.finish()
                // Retaining the APKs takes seconds, and this run has nothing left to show but
                // completed steps. The screen switches to its between-apps state instead
                _state.update { it.copy(activeIndex = null, activeRun = null) }
                withContext(Dispatchers.IO) { cleanupInput(item, selectedApp, succeeded) }
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
                val target = SplitApkPreparer.copyInstalledApp(
                    item.packageName, source.apkPath, source.splitPaths, workspace
                )
                SelectedApp.Local(
                    packageName = item.packageName,
                    version = source.version,
                    versionCode = source.versionCode,
                    file = target,
                    // Not the worker's to delete: the original APK is saved from it after the run
                    temporary = false,
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

    /** Deletes the copy [materialize] made of an installed app, the only input the queue owns. */
    private fun cleanupInput(item: BatchPatchItem, selectedApp: SelectedApp, succeeded: Boolean) {
        // An attached file is kept for a retry until its app is patched
        val owned = item.source is BatchApkSource.Installed ||
                (succeeded && item.source is BatchApkSource.UserFile)
        if (owned) (selectedApp as? SelectedApp.Local)?.file?.delete()
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

        val isClone = item.producesCloneAs(currentPackageName)
        // A configuration belongs to the card it was built from, which is the app itself unless
        // the run produced a copy, whatever package name the patches settled on
        val configurationKey = if (isClone) currentPackageName else item.configurationKey

        // What the user chose to patch with is recorded whether the APK is kept: the
        // two are separate settings, and the summary can still install from the workspace
        patchSelectionRepository.updateSelection(
            configurationKey,
            item.selection,
            // Scoped to every source the plan considered, so a source the user ended up taking
            // nothing from has its stale selection cleared rather than left behind. A chosen
            // source turned every other one down, which the next plan would otherwise merge back
            scope = if (item.chosenBundleUid != null) {
                patchBundleRepository.enabledBundlesInfoFlow.first().keys
            } else {
                item.bundles.mapTo(mutableSetOf()) { it.uid }.ifEmpty { item.selection.keys }
            }
        )
        // Without this the next plan cannot tell a patch the user deselected from one that was
        // added since, and would keep re-enabling every deselected default
        item.bundles.forEach { bundle ->
            patchSelectionRepository.saveSeenPatches(
                packageName = configurationKey,
                bundleUid = bundle.uid,
                patchNames = bundle.patchNames
            )
        }
        // Simple mode derives its options from the per-app preference screen rather than the
        // database, so only an expert-mode selection is worth writing back
        if (prefs.useExpertMode.get()) {
            patchOptionsRepository.saveOptions(configurationKey, item.options)
        }

        if (!prefs.savePatchedApks.get()) {
            // Retention is off, so the patched APK only lives long enough for the summary
            // to install it and is dropped together with the workspace
            return@withContext outputFile
        }

        val retained = fs.getPatchedAppFile(currentPackageName, version)
        val stored = runCatching {
            retained.parentFile?.mkdirs()
            // Staged, so a reader that refreshes while the copy runs never opens a half
            // written archive at the path the app already reports as the saved build
            copyThroughStaging(outputFile, retained)
            retained
        }.getOrElse {
            Log.w(TAG, "Failed to retain patched APK for ${item.packageName}", it)
            outputFile
        }

        val selectionPayload = patchBundleRepository.snapshotSelection(item.selection)
        installedAppRepository.addOrUpdate(
            currentPackageName,
            item.packageName,
            isClone,
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
     * later run than the archive itself. A mount keeps the archive, as the stock app it restores
     * is installed from its signed splits.
     */
    private suspend fun saveOriginalApk(
        item: BatchPatchItem,
        selectedApp: SelectedApp,
        patchedVersion: String
    ) {
        val file = (selectedApp as? SelectedApp.Local)?.file?.takeIf { it.exists() } ?: return
        if (!item.useMount && SplitApkPreparer.isSplitArchive(file)) return
        // Recorded under its own version, which a patch may have renamed in the patched build
        val version = pm.getPackageInfo(file)?.versionName?.takeUnless { it.isBlank() }
            ?: item.version
            ?: patchedVersion

        if (originalApkRepository.hasOriginal(item.packageName, version, file)) return

        runCatching {
            originalApkRepository.saveOriginalApk(
                packageName = item.packageName,
                version = version,
                sourceFile = file,
                moveSource = item.source is BatchApkSource.Installed
            )
        }.onFailure { Log.w(TAG, "Failed to save original APK for ${item.packageName}", it) }
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

    private fun placeholder(target: BatchTarget) = BatchPatchItem(
        target = target,
        appName = target.packageName,
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
