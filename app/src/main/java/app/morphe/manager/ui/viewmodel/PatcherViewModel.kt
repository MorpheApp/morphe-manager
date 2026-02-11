package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.autoSaver
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.worker.WorkerRepository
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.runtime.ProcessRuntime
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.patcher.worker.PatcherWorker
import app.morphe.manager.ui.model.*
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.util.*
import app.morphe.manager.util.saver.snapshotStateListSaver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

@OptIn(SavedStateHandleSaveableApi::class)
class PatcherViewModel(
    private val input: Patcher.ViewModelParams
) : ViewModel(), KoinComponent, StepProgressProvider {
    private val app: Application by inject()
    private val fs: Filesystem by inject()
    private val pm: PM by inject()
    private val workerRepository: WorkerRepository by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val patchSelectionRepository: PatchSelectionRepository by inject()
    private val patchOptionsRepository: PatchOptionsRepository by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    val prefs: PreferencesManager by inject()
    private val patchOptionsPrefs: PatchOptionsPreferencesManager by inject()
    private val originalApkRepository: OriginalApkRepository by inject()
    private val savedStateHandle: SavedStateHandle = get()

    private var savedPatchedApp by savedStateHandle.saveableVar { false }

    private val saveOriginalApkMutex = Mutex()

    var exportMetadata by mutableStateOf<PatchedAppExportData?>(null)
        private set
    private var appliedSelection: PatchSelection = input.selectedPatches.mapValues { it.value.toSet() }
    private var appliedOptions: Options = input.options
    val currentSelectedApp: SelectedApp get() = selectedApp

    private var currentActivityRequest: Pair<CompletableDeferred<Boolean>, String>? by mutableStateOf(
        null
    )
    val activityPromptDialog by derivedStateOf { currentActivityRequest?.second }

    private var launchedActivity: CompletableDeferred<ActivityResult>? = null
    private val launchActivityChannel = Channel<Intent>()
    val launchActivityFlow = launchActivityChannel.receiveAsFlow()

    private val selectedApp = input.selectedApp
    val packageName = selectedApp.packageName
    val version = selectedApp.version

    data class MemoryAdjustmentDialogState(
        val previousLimit: Int,
        val newLimit: Int,
        val adjusted: Boolean
    )

    var memoryAdjustmentDialog by mutableStateOf<MemoryAdjustmentDialogState?>(null)
        private set

    data class MissingPatchWarningState(
        val patchNames: List<String>
    )
    var missingPatchWarning by mutableStateOf<MissingPatchWarningState?>(null)
        private set

    private suspend fun gatherScopedBundles(): Map<Int, PatchBundleInfo.Scoped> =
        patchBundleRepository.scopedBundleInfoFlow(
            packageName,
            input.selectedApp.version
        ).first().associateBy { it.uid }

    private suspend fun collectSelectedBundleMetadata(): Pair<List<String>, List<String>> {
        val globalBundles = patchBundleRepository.bundleInfoFlow.first()
        val scopedBundles = gatherScopedBundles()
        val sanitizedSelection = sanitizeSelection(appliedSelection, scopedBundles)
        val versions = mutableListOf<String>()
        val names = mutableListOf<String>()
        val displayNames = patchBundleRepository.sources.first().associate { it.uid to it.displayTitle }
        sanitizedSelection.keys.forEach { uid ->
            val scoped = scopedBundles[uid]
            val global = globalBundles[uid]
            val displayName = displayNames[uid]
                ?: scoped?.name
                ?: global?.name
            global?.version?.takeIf { it.isNotBlank() }?.let(versions::add)
            displayName?.takeIf { it.isNotBlank() }?.let(names::add)
        }
        return versions.distinct() to names.distinct()
    }

    private suspend fun buildExportMetadata(packageInfo: PackageInfo?): PatchedAppExportData? {
        val info = packageInfo ?: pm.getPackageInfo(outputFile) ?: return null
        val (bundleVersions, bundleNames) = collectSelectedBundleMetadata()
        val label = runCatching { with(pm) { info.label() } }.getOrNull()
        val versionName = info.versionName?.takeUnless { it.isBlank() } ?: version ?: "unspecified"
        return PatchedAppExportData(
            appName = label,
            packageName = info.packageName,
            appVersion = versionName,
            patchBundleVersions = bundleVersions,
            patchBundleNames = bundleNames
        )
    }

    private fun refreshExportMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            val metadata = buildExportMetadata(null)
            withContext(Dispatchers.Main) {
                exportMetadata = metadata
            }
        }
    }

    private suspend fun ensureExportMetadata() {
        if (exportMetadata != null) return
        val metadata = buildExportMetadata(null) ?: return
        withContext(Dispatchers.Main) {
            exportMetadata = metadata
        }
    }

    private val tempDir = savedStateHandle.saveable(key = "tempDir") {
        fs.uiTempDir.resolve("installer").also {
            it.deleteRecursively()
            it.mkdirs()
        }
    }

    private var _inputFile: File? = null
    var inputFile: File?
        get() = _inputFile
        set(value) { _inputFile = value }

    private var requiresSplitPreparation by savedStateHandle.saveableVar {
        initialSplitRequirement(input.selectedApp)
    }
    val outputFile = tempDir.resolve("output.apk")

    /**
     * How much of the progress is allocated to executing patches.
     */
    private var patchesPercentage = 0.0

    val steps by savedStateHandle.saveable(saver = snapshotStateListSaver()) {
        val stepsList = generateSteps(
            app,
            input.selectedApp,
            requiresSplitPreparation
        ).toMutableStateList()

        // Patches use the remaining unallocated percentage.
        patchesPercentage = max(0.0, 1.0 - stepsList.sumOf { it.progressPercentage })

        stepsList
    }

    private var currentStepIndex = 0

    /**
     * [0, 1.0] progress value.
     */
    val progress by derivedStateOf {
        val currentProgress = steps.sumOf {
            if (it.state == State.COMPLETED && it.category != StepCategory.PATCHING) {
                it.progressPercentage
            } else {
                0.0
            }
        } + ((completedPatchCount / patchCount.toDouble()) * patchesPercentage)

        min(1.0, currentProgress).toFloat()
    }

    private val workManager = WorkManager.getInstance(app)
    private val _patcherSucceeded = MediatorLiveData<Boolean?>()
    val patcherSucceeded: LiveData<Boolean?> get() = _patcherSucceeded
    private var currentWorkSource: LiveData<WorkInfo?>? = null
    private val handledFailureIds = mutableSetOf<UUID>()
    private var forceKeepLocalInput = false

    private var patcherWorkerId: ParcelUuid?
        get() = savedStateHandle["patcher_worker_id"]
        set(value) {
            if (value == null) {
                savedStateHandle.remove<ParcelUuid>("patcher_worker_id")
            } else {
                savedStateHandle["patcher_worker_id"] = value
            }
        }

    private val logs = mutableListOf<Pair<LogLevel, String>>()
    private val logger = object : Logger() {
        override fun log(level: LogLevel, message: String) {
            level.androidLog(message)
            if (level == LogLevel.TRACE) return

            viewModelScope.launch {
                logs.add(level to message)
            }
        }
    }

    init {
        val existingId = patcherWorkerId?.uuid
        if (existingId != null) {
            observeWorker(existingId)
        } else {
            viewModelScope.launch {
                // Resolve inputFile before preflight check to prevent race condition
                // where the worker could start before inputFile is set.
                if (inputFile == null && input.selectedApp is SelectedApp.Installed) {
                    withContext(Dispatchers.IO) {
                        val originalApk = originalApkRepository.get(packageName)
                        if (originalApk != null) {
                            val file = File(originalApk.filePath)
                            if (file.exists()) {
                                inputFile = file
                            }
                        }
                    }
                }
                runPreflightCheck()
            }
        }
    }

    private suspend fun runPreflightCheck() {
        val scopedBundles = gatherScopedBundles()
        val sanitizedSelection = sanitizeSelection(appliedSelection, scopedBundles)
        val missing = mutableListOf<String>()
        appliedSelection.forEach { (uid, patches) ->
            val kept = sanitizedSelection[uid] ?: emptySet()
            patches.filterNot { it in kept }.forEach { missing += it }
        }
        if (missing.isNotEmpty()) {
            missingPatchWarning = MissingPatchWarningState(
                patchNames = missing.distinct().sorted()
            )
        } else {
            startWorker()
        }
    }

    private fun startWorker() {
        val workId = launchWorker()
        patcherWorkerId = ParcelUuid(workId)
        observeWorker(workId)
    }

    /**
     * Save original APK file for future repatching.
     * Called after successful patching, independent of installation method.
     * For split APK archives (apkm, apks, xapk), saves the original archive.
     * For regular APK files, saves the APK itself.
     *
     * Thread-safe: uses mutex to prevent concurrent saves from observeWorker and persistPatchedApp.
     */
    private suspend fun saveOriginalApkIfNeeded() = saveOriginalApkMutex.withLock {
        try {
            // Determine which file to save:
            // - For SelectedApp.Local with split archives: save the original user file
            // - For other cases: save the inputFile (which might be a downloaded/extracted APK)
            val fileToSave = when (val selected = input.selectedApp) {
                is SelectedApp.Local -> {
                    // Check if original file is a split archive
                    if (SplitApkPreparer.isSplitArchive(selected.file)) {
                        // Save the original split archive, not the merged APK
                        selected.file
                    } else {
                        // For regular APK, use inputFile (might be same as selected.file)
                        inputFile ?: selected.file
                    }
                }
                else -> {
                    // For non-local apps (Download, Search, Installed), use inputFile
                    inputFile
                }
            }

            if (fileToSave == null || !fileToSave.exists()) {
                Log.w(TAG, "File to save doesn't exist, skipping original APK save")
                return@withLock
            }

            // Get version from the package info
            // Use outputFile (patched APK) because inputFile might be deleted by worker!
            // For split archives: selected.file (archive) won't have valid PackageInfo
            // For regular APKs: inputFile might be deleted
            val apkPackageInfo = pm.getPackageInfo(outputFile)
            if (apkPackageInfo == null) {
                Log.w(TAG, "Cannot get package info from output APK, skipping save")
                return@withLock
            }

            val originalVersion = apkPackageInfo.versionName?.takeUnless { it.isBlank() }
                ?: input.selectedApp.version
                ?: "unknown"

            // Does original already exist in repository?
            val existing = originalApkRepository.get(packageName)
            if (existing != null && existing.version == originalVersion) {
                Log.d(TAG, "Original APK already exists in repository (version $originalVersion), skipping duplicate save")
                return@withLock
            }

            // If we got here, we need to save the original
            val savedFile = originalApkRepository.saveOriginalApk(
                packageName = packageName,
                version = originalVersion,
                sourceFile = fileToSave
            )

            if (savedFile != null) {
                Log.i(TAG, "Original APK/archive saved: ${savedFile.name}")
            }
        } catch (e: Exception) {
            // Don't fail patching if save fails
            Log.w(TAG, "Failed to save original APK", e)
        }
    }

    suspend fun persistPatchedApp(
        currentPackageName: String?,
        installType: InstallType
    ): Boolean = withContext(Dispatchers.IO) {
        val installedPackageInfo = currentPackageName?.let(pm::getPackageInfo)
        val patchedPackageInfo = pm.getPackageInfo(outputFile)
        val packageInfo = patchedPackageInfo ?: installedPackageInfo
        if (packageInfo == null) {
            Log.e(TAG, "Failed to resolve package info for patched APK")
            return@withContext false
        }

        // This call is safe, it will skip if already saved
        saveOriginalApkIfNeeded()

        val finalPackageName = packageInfo.packageName
        val finalVersion = packageInfo.versionName?.takeUnless { it.isBlank() } ?: version ?: "unspecified"

        // Delete old version file if it exists and is different
        val existingApp = installedAppRepository.get(finalPackageName)
        if (existingApp != null && existingApp.version != finalVersion) {
            val oldFile = fs.getPatchedAppFile(finalPackageName, existingApp.version)
            if (oldFile.exists()) {
                oldFile.delete()
                Log.d(TAG, "Deleted old patched app file: ${oldFile.name}")
            }
        }

        // Save new version
        val savedCopy = fs.getPatchedAppFile(finalPackageName, finalVersion)
        try {
            savedCopy.parentFile?.mkdirs()
            outputFile.copyTo(savedCopy, overwrite = true)
        } catch (error: IOException) {
            if (installType == InstallType.SAVED) {
                Log.e(TAG, "Failed to copy patched APK for later", error)
                return@withContext false
            } else {
                Log.w(TAG, "Failed to update saved copy for $finalPackageName", error)
            }
        }

        val metadata = buildExportMetadata(patchedPackageInfo ?: packageInfo)
        withContext(Dispatchers.Main) {
            exportMetadata = metadata
        }

        // Use original package name to get scoped bundles for selection persistence
        // This ensures all applied patches are correctly saved
        val scopedBundlesForSelection = patchBundleRepository.scopedBundleInfoFlow(
            packageName,
            input.selectedApp.version
        ).first().associateBy { it.uid }
        val sanitizedSelection = sanitizeSelection(appliedSelection, scopedBundlesForSelection)
        val sanitizedOptions = sanitizeOptions(appliedOptions, scopedBundlesForSelection)

        val selectionPayload = patchBundleRepository.snapshotSelection(sanitizedSelection)

        installedAppRepository.addOrUpdate(
            finalPackageName,
            packageName,
            finalVersion,
            installType,
            sanitizedSelection,
            selectionPayload
        )

        if (finalPackageName != packageName) {
            patchSelectionRepository.updateSelection(finalPackageName, sanitizedSelection)
            patchOptionsRepository.saveOptions(finalPackageName, sanitizedOptions)
        }
        patchSelectionRepository.updateSelection(packageName, sanitizedSelection)
        patchOptionsRepository.saveOptions(packageName, sanitizedOptions)
        appliedSelection = sanitizedSelection
        appliedOptions = sanitizedOptions

        savedPatchedApp = savedPatchedApp || installType == InstallType.SAVED || savedCopy.exists()
        true
    }

    private val patchCount = input.selectedPatches.values.sumOf { it.size }
    private var completedPatchCount by savedStateHandle.saveable {
        // SavedStateHandle.saveable only supports the boxed version.
        @Suppress("AutoboxingStateCreation") mutableStateOf(
            0
        )
    }
    val patchesProgress get() = completedPatchCount to patchCount
    override var downloadProgress by savedStateHandle.saveable(
        key = "downloadProgress",
        stateSaver = autoSaver()
    ) {
        mutableStateOf<Pair<Long, Long?>?>(null)
    }
        private set

    fun export(uri: Uri?) = viewModelScope.launch {
        uri?.let { targetUri ->
            ensureExportMetadata()
            val exportSucceeded = runCatching {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(targetUri)
                        ?.use { stream -> Files.copy(outputFile.toPath(), stream) }
                        ?: throw IOException("Could not open output stream for export")
                }
            }.isSuccess

            if (!exportSucceeded) {
                app.toast(app.getString(R.string.saved_app_export_failed))
                return@launch
            }

            // Save metadata to database
            val saved = persistPatchedApp(null, InstallType.SAVED)

            // Show appropriate success message
            if (!saved) {
                app.toast(app.getString(R.string.patched_app_save_failed_toast))
            } else {
                app.toast(app.getString(R.string.save_apk_success))
            }
        }
    }

    fun rejectInteraction() {
        currentActivityRequest?.first?.complete(false)
    }

    fun allowInteraction() {
        currentActivityRequest?.first?.complete(true)
    }

    fun handleActivityResult(result: ActivityResult) {
        launchedActivity?.complete(result)
    }

    private fun launchWorker(): UUID =
        workerRepository.launchExpedited<PatcherWorker, PatcherWorker.Args>(
            "patching",
            buildWorkerArgs()
        )

    private fun buildWorkerArgs(): PatcherWorker.Args {
        val selectedForRun = when (val selected = input.selectedApp) {
            is SelectedApp.Local -> {
                val reuseFile = inputFile ?: selected.file
                val temporary = if (forceKeepLocalInput) false else selected.temporary
                selected.copy(file = reuseFile, temporary = temporary)
            }

            else -> selected
        }

        val shouldPreserveInput =
            selectedForRun is SelectedApp.Local && (selectedForRun.temporary || forceKeepLocalInput)

        // Determine which patches and options to use based on mode
        val useExpertMode = prefs.useExpertMode.getBlocking()

        val mergedOptions = if (useExpertMode) {
            // Expert mode: Use options from input
            input.options
        } else {
            // Simple mode: Use options from preferences manager
            runBlocking {
                when (packageName) {
                    AppPackages.YOUTUBE_MUSIC -> patchOptionsPrefs.exportYouTubeMusicPatchOptions()
                    else -> patchOptionsPrefs.exportYouTubePatchOptions()
                }
            }
        }

        return PatcherWorker.Args(
            selectedForRun,
            outputFile.path,
            input.selectedPatches,
            mergedOptions,
            logger,
            onPatchCompleted = {
                withContext(Dispatchers.Main) { completedPatchCount += 1 }
            },
            setInputFile = { file, needsSplit, merged ->
                val storedFile = if (shouldPreserveInput) {
                    val existing = inputFile
                    if (existing?.exists() == true) {
                        existing
                    } else withContext(Dispatchers.IO) {
                        val destination = File(fs.tempDir, "input-${System.currentTimeMillis()}.apk")
                        file.copyTo(destination, overwrite = true)
                        destination
                    }
                } else file

                withContext(Dispatchers.Main) {
                    inputFile = storedFile
                    updateSplitStepRequirement(storedFile, needsSplit, merged)
                }
            },
            onProgress = { name, state, message ->
                viewModelScope.launch {
                    steps[currentStepIndex] = steps[currentStepIndex].run {
                        copy(
                            name = name ?: this.name,
                            state = state ?: this.state,
                            message = message ?: this.message
                        )
                    }

                    if (state == State.COMPLETED && currentStepIndex != steps.lastIndex) {
                        currentStepIndex++
                        steps[currentStepIndex] =
                            steps[currentStepIndex].copy(state = State.RUNNING)
                    }
                }
            }
        )
    }

    private fun observeWorker(id: UUID) {
        val source = workManager.getWorkInfoByIdLiveData(id)
        currentWorkSource?.let { _patcherSucceeded.removeSource(it) }
        currentWorkSource = source
        _patcherSucceeded.addSource(source) { workInfo ->
            when (workInfo?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    forceKeepLocalInput = false

                    // Save original APK before deleting temporary file (blocking)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            saveOriginalApkIfNeeded()
                        } finally {
                            withContext(Dispatchers.Main) {
                                // Delete temporary file after saving
                                if (input.selectedApp is SelectedApp.Local && input.selectedApp.temporary) {
                                    inputFile?.takeIf { it.exists() }?.delete()
                                    inputFile = null
                                    updateSplitStepRequirement(null)
                                }
                                refreshExportMetadata()
                                _patcherSucceeded.value = true
                            }
                        }
                    }
                }

                WorkInfo.State.FAILED -> {
                    handleWorkerFailure(workInfo)
                    _patcherSucceeded.value = false
                }

                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED -> _patcherSucceeded.value = null
                else -> _patcherSucceeded.value = null
            }
        }
    }

    private fun handleWorkerFailure(workInfo: WorkInfo) {
        if (!handledFailureIds.add(workInfo.id)) return
        val exitCode = workInfo.outputData.getInt(PatcherWorker.PROCESS_EXIT_CODE_KEY, Int.MIN_VALUE)
        if (exitCode == ProcessRuntime.OOM_EXIT_CODE) {
            viewModelScope.launch {
                if (!prefs.useProcessRuntime.get()) return@launch
                forceKeepLocalInput = true
                val previousFromWorker = workInfo.outputData.getInt(
                    PatcherWorker.PROCESS_PREVIOUS_LIMIT_KEY,
                    -1
                )
                val previousLimit = if (previousFromWorker > 0) previousFromWorker else prefs.patcherProcessMemoryLimit.get()
                val newLimit = (previousLimit - MEMORY_ADJUSTMENT_MB).coerceAtLeast(MIN_LIMIT_MB)
                val adjusted = newLimit < previousLimit
                if (adjusted) {
                    prefs.patcherProcessMemoryLimit.update(newLimit)
                }
                memoryAdjustmentDialog = MemoryAdjustmentDialogState(
                    previousLimit = previousLimit,
                    newLimit = if (adjusted) newLimit else previousLimit,
                    adjusted = adjusted
                )
            }
        }
    }

    private fun initialSplitRequirement(selectedApp: SelectedApp): Boolean =
        when (selectedApp) {
            is SelectedApp.Local -> SplitApkPreparer.isSplitArchive(selectedApp.file)
            else -> false
        }

    private fun updateSplitStepRequirement(
        file: File?,
        needsSplitOverride: Boolean? = null,
        merged: Boolean = false
    ) {
        val needsSplit = needsSplitOverride
                ?: merged
                || file?.let(SplitApkPreparer::isSplitArchive) == true
        when {
            needsSplit && !requiresSplitPreparation -> {
                requiresSplitPreparation = true
                addSplitStep()
            }

            !needsSplit && requiresSplitPreparation -> {
                requiresSplitPreparation = false
                removeSplitStep()
                return
            }
        }

        if (needsSplit && merged) {
            val index = steps.indexOfFirst { it.id == StepId.PREPARE_SPLIT_APK }
            if (index >= 0) {
                steps[index] = steps[index].copy(state = State.COMPLETED)
                if (currentStepIndex == index && index < steps.lastIndex) {
                    currentStepIndex++
                    steps[currentStepIndex] = steps[currentStepIndex].copy(state = State.RUNNING)
                }
            }
        }
    }

    private fun addSplitStep() {
        if (steps.any { it.id == StepId.PREPARE_SPLIT_APK }) return

        val loadIndex = steps.indexOfFirst { it.id == StepId.LOAD_PATCHES }
        val insertIndex = when {
            loadIndex >= 0 -> loadIndex + 1
            else -> steps.indexOfFirst { it.id == StepId.READ_APK }.takeIf { it >= 0 } ?: steps.size
        }
        val state = if (insertIndex <= currentStepIndex) State.COMPLETED else State.WAITING

        steps.add(insertIndex, buildSplitStep(app, state = state))

        if (insertIndex <= currentStepIndex) {
            currentStepIndex++
        }
    }

    private fun removeSplitStep() {
        val index = steps.indexOfFirst { it.id == StepId.PREPARE_SPLIT_APK }
        if (index == -1) return

        val removingCurrent = index == currentStepIndex
        steps.removeAt(index)

        when {
            currentStepIndex > index -> currentStepIndex--
            removingCurrent -> {
                currentStepIndex = index.coerceAtMost(steps.lastIndex).coerceAtLeast(0)
                if (steps.isNotEmpty()) {
                    val current = steps[currentStepIndex]
                    if (current.state == State.WAITING) {
                        steps[currentStepIndex] = current.copy(state = State.RUNNING)
                    }
                }
            }
        }
    }

    private fun sanitizeSelection(
        selection: PatchSelection,
        bundles: Map<Int, PatchBundleInfo.Scoped>
    ): PatchSelection = buildMap {
        selection.forEach { (uid, patches) ->
            val bundle = bundles[uid]
            if (bundle == null) {
                // Keep unknown bundles so applied patches stay visible even if the source is missing.
                if (patches.isNotEmpty()) put(uid, patches.toSet())
                return@forEach
            }

            val valid = bundle.patches.map { it.name }.toSet()
            val kept = patches.filter { it in valid }.toSet()
            if (kept.isNotEmpty()) {
                put(uid, kept)
            } else if (patches.isNotEmpty()) {
                // If everything was filtered out by compatibility, still keep the original set so
                // the app info screen can show the applied bundle/patch names.
                put(uid, patches.toSet())
            }
        }
    }

    private fun sanitizeOptions(
        options: Options,
        bundles: Map<Int, PatchBundleInfo.Scoped>
    ): Options = buildMap {
        options.forEach { (uid, patchOptions) ->
            val bundle = bundles[uid] ?: return@forEach
            val patches = bundle.patches.associateBy { it.name }
            val filtered = buildMap<String, Map<String, Any?>> {
                patchOptions.forEach { (patchName, values) ->
                    val patch = patches[patchName] ?: return@forEach
                    val validKeys = patch.options?.map { it.key }?.toSet() ?: emptySet()
                    val kept = if (validKeys.isEmpty()) values else values.filterKeys { it in validKeys }
                    if (kept.isNotEmpty()) put(patchName, kept)
                }
            }
            if (filtered.isNotEmpty()) put(uid, filtered)
        }
    }

    override fun onCleared() {
        super.onCleared()
        patcherWorkerId?.uuid?.let(workManager::cancelWorkById)

        if (input.selectedApp is SelectedApp.Local && input.selectedApp.temporary) {
            inputFile?.takeIf { it.exists() }?.delete()
            inputFile = null
            updateSplitStepRequirement(null)
        }
    }

    private companion object {
        private const val TAG = "Morphe Patcher"
        private const val MEMORY_ADJUSTMENT_MB = 200
        private const val MIN_LIMIT_MB = 200

        fun LogLevel.androidLog(msg: String) = when (this) {
            LogLevel.TRACE -> Log.v(TAG, msg)
            LogLevel.INFO -> Log.i(TAG, msg)
            LogLevel.WARN -> Log.w(TAG, msg)
            LogLevel.ERROR -> Log.e(TAG, msg)
        }

        fun generateSteps(
            context: Context,
            selectedApp: SelectedApp,
            splitStepActive: Boolean
        ): List<Step> {
            val needsDownload =
                selectedApp is SelectedApp.Download || selectedApp is SelectedApp.Search

            return listOfNotNull(
                Step(
                    id = StepId.DOWNLOAD_APK,
                    name = context.getString(R.string.download_apk),
                    category = StepCategory.PREPARING,
                    state = State.RUNNING,
                    progressKey = ProgressKey.DOWNLOAD,
                    progressPercentage = 0.1
                ).takeIf { needsDownload },
                Step(
                    id = StepId.LOAD_PATCHES,
                    name = context.getString(R.string.patcher_step_load_patches),
                    category = StepCategory.PREPARING,
                    state = if (needsDownload) State.WAITING else State.RUNNING,
                    progressPercentage = 0.05
                ),
                buildSplitStep(context).takeIf { splitStepActive },
                Step(
                    id = StepId.READ_APK,
                    name = context.getString(R.string.patcher_step_unpack),
                    category = StepCategory.PREPARING,
                    progressPercentage = 0.05
                ),

                Step(
                    id = StepId.EXECUTE_PATCHES,
                    name = context.getString(R.string.applying_patches),
                    category = StepCategory.PATCHING,
                    // progress percentage is calculated as all remaining percentages not declared here.
                    progressPercentage = 0.0
                ),

                Step(
                    id = StepId.WRITE_PATCHED_APK,
                    name = context.getString(R.string.patcher_step_write_patched),
                    category = StepCategory.SAVING,
                    progressPercentage = 0.4
                ),
                Step(
                    id = StepId.SIGN_PATCHED_APK,
                    name = context.getString(R.string.patcher_step_sign_apk),
                    category = StepCategory.SAVING,
                    progressPercentage = 0.1
                )
            )
        }

    }
}

private fun buildSplitStep(
    context: Context,
    state: State = State.WAITING,
    message: String? = null
) = Step(
    id = StepId.PREPARE_SPLIT_APK,
    name = context.getString(R.string.patcher_step_prepare_split_apk),
    category = StepCategory.PREPARING,
    state = state,
    message = message,
    progressPercentage = 0.1
)
