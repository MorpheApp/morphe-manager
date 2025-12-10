package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.installer.ShizukuInstaller
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.domain.repository.PatchSelectionRepository
import app.revanced.manager.domain.worker.WorkerRepository
import app.revanced.manager.patcher.logger.LogLevel
import app.revanced.manager.patcher.logger.Logger
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.runtime.MemoryLimitConfig
import app.revanced.manager.patcher.runtime.ProcessRuntime
import app.revanced.manager.patcher.worker.PatcherWorker
import app.revanced.manager.plugin.downloader.PluginHostApi
import app.revanced.manager.plugin.downloader.UserInteractionException
import app.revanced.manager.service.InstallService
import app.revanced.manager.service.UninstallService
import app.revanced.manager.ui.model.InstallerModel
import app.revanced.manager.ui.model.ProgressKey
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.State
import app.revanced.manager.ui.model.Step
import app.revanced.manager.ui.model.StepCategory
import app.revanced.manager.ui.model.StepProgressProvider
import app.revanced.manager.ui.model.navigation.Patcher
import app.revanced.manager.util.Options
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.PatchedAppExportData
import app.revanced.manager.util.saveableVar
import app.revanced.manager.util.saver.snapshotStateListSaver
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.tag
import app.revanced.manager.util.toast
import app.revanced.manager.util.uiSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.util.UUID

@OptIn(SavedStateHandleSaveableApi::class, PluginHostApi::class)
class PatcherViewModel(
    private val input: Patcher.ViewModelParams
) : ViewModel(), KoinComponent, StepProgressProvider, InstallerModel {
    private val app: Application by inject()
    private val fs: Filesystem by inject()
    private val pm: PM by inject()
    private val workerRepository: WorkerRepository by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val patchSelectionRepository: PatchSelectionRepository by inject()
    private val patchOptionsRepository: PatchOptionsRepository by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val rootInstaller: RootInstaller by inject()
    private val shizukuInstaller: ShizukuInstaller by inject()
    private val installerManager: InstallerManager by inject()
    val prefs: PreferencesManager by inject()
    private val savedStateHandle: SavedStateHandle = get()

    private var pendingExternalInstall: InstallerManager.InstallPlan.External? = null
    private var externalInstallTimeoutJob: Job? = null


    private var installedApp: InstalledApp? = null
    private val selectedApp = input.selectedApp
    val packageName = selectedApp.packageName
    val version = selectedApp.version

    var installedPackageName by savedStateHandle.saveable(
        key = "installedPackageName",
        // Force Kotlin to select the correct overload.
        stateSaver = autoSaver()
    ) {
        mutableStateOf<String?>(null)
    }
        private set
    private var ongoingPmSession: Boolean by savedStateHandle.saveableVar { false }
    var packageInstallerStatus: Int? by savedStateHandle.saveable(
        key = "packageInstallerStatus",
        stateSaver = autoSaver()
    ) {
        mutableStateOf(null)
    }
        private set

    var isInstalling by mutableStateOf(ongoingPmSession)
        private set
    var installStatus by mutableStateOf<InstallCompletionStatus?>(null)
        private set

    private fun updateInstallingState(value: Boolean) {
        ongoingPmSession = value
        isInstalling = value
        if (!value) {
            awaitingPackageInstall = null
            externalInstallTimeoutJob?.cancel()
            externalInstallTimeoutJob = null
        }
    }
    private var savedPatchedApp by savedStateHandle.saveableVar { false }
    val hasSavedPatchedApp get() = savedPatchedApp

    var exportMetadata by mutableStateOf<PatchedAppExportData?>(null)
        private set
    private var appliedSelection: PatchSelection = input.selectedPatches.mapValues { it.value.toSet() }
    private var appliedOptions: Options = input.options
    val currentSelectedApp: SelectedApp get() = selectedApp

    fun currentSelectionSnapshot(): PatchSelection =
        appliedSelection.mapValues { (_, patches) -> patches.toSet() }

    fun currentOptionsSnapshot(): Options =
        appliedOptions.mapValues { (_, bundleOptions) ->
            bundleOptions.mapValues { (_, patchOptions) -> patchOptions.toMap() }.toMap()
        }.toMap()

    fun dismissMissingPatchDialog() {
        missingPatchDialog = null
    }

    private var currentActivityRequest: Pair<CompletableDeferred<Boolean>, String>? by mutableStateOf(
        null
    )
    val activityPromptDialog by derivedStateOf { currentActivityRequest?.second }

    private var launchedActivity: CompletableDeferred<ActivityResult>? = null
    private val launchActivityChannel = Channel<Intent>()
    val launchActivityFlow = launchActivityChannel.receiveAsFlow()

    var installFailureMessage by mutableStateOf<String?>(null)
        private set

    private fun showInstallFailure(message: String) {
        installFailureMessage = message
        installStatus = InstallCompletionStatus.Failure(message)
        updateInstallingState(false)
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
    }

    private fun scheduleInstallTimeout(
        packageName: String,
        durationMs: Long = SYSTEM_INSTALL_TIMEOUT_MS,
        timeoutMessage: (() -> String)? = null
    ) {
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = viewModelScope.launch {
            delay(durationMs)
            if (installStatus is InstallCompletionStatus.InProgress) {
                logger.trace("install timeout for $packageName")
                packageInstallerStatus = null
                val message = when {
                    prefs.useMorpheHomeScreen.get() -> app.getString(R.string.morphe_patcher_install_conflict_message)
                    else -> timeoutMessage?.invoke() ?: app.getString(R.string.install_timeout_message)
                }
                showInstallFailure(message)
            }
        }
    }

    private val tempDir = savedStateHandle.saveable(key = "tempDir") {
        fs.uiTempDir.resolve("installer").also {
            it.deleteRecursively()
            it.mkdirs()
        }
    }

    private var inputFile: File? by savedStateHandle.saveableVar()
    private val outputFile = tempDir.resolve("output.apk")

    private val logs by savedStateHandle.saveable<MutableList<Pair<LogLevel, String>>> { mutableListOf() }
    private val logger = object : Logger() {
        override fun log(level: LogLevel, message: String) {
            level.androidLog(message)
            if (level == LogLevel.TRACE) return

            viewModelScope.launch {
                logs.add(level to message)
            }
        }
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
    data class MemoryAdjustmentDialogState(
        val previousLimit: Int,
        val newLimit: Int,
        val adjusted: Boolean
    )

    var memoryAdjustmentDialog by mutableStateOf<MemoryAdjustmentDialogState?>(null)
        private set

    data class MissingPatchDialogState(val patchNames: List<String>)
    var missingPatchDialog by mutableStateOf<MissingPatchDialogState?>(null)
        private set

    private suspend fun collectSelectedBundleMetadata(): Pair<List<String>, List<String>> {
        val globalBundles = patchBundleRepository.bundleInfoFlow.first()
        val scopedBundles = patchBundleRepository.scopedBundleInfoFlow(
            packageName,
            input.selectedApp.version
        ).first().associateBy { it.uid }
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
    val steps by savedStateHandle.saveable(saver = snapshotStateListSaver()) {
        generateSteps(
            app,
            input.selectedApp
        ).toMutableStateList()
    }
    private var currentStepIndex = 0

    val progress by derivedStateOf {
        // FIXME: Use step substep to track progress of individual patches.
        val current = steps.sumOf {
            if (it.state == State.COMPLETED && it.category != StepCategory.PATCHING) {
                it.subSteps.toLong()
            } else {
                0L
            }
        } + completedPatchCount

        val total = steps.sumOf{ it.subSteps } - 1 + patchCount

        current.toFloat() / total.toFloat()
    }

    private val workManager = WorkManager.getInstance(app)
    private val _patcherSucceeded = MediatorLiveData<Boolean?>()
    val patcherSucceeded: LiveData<Boolean?> get() = _patcherSucceeded
    private var currentWorkSource: LiveData<WorkInfo?>? = null
    private val handledFailureIds = mutableSetOf<UUID>()
    private var forceKeepLocalInput = false
    private var awaitingPackageInstall: String? = null

    private var patcherWorkerId: ParcelUuid by savedStateHandle.saveableVar {
        ParcelUuid(launchWorker())
    }

    init {
        observeWorker(patcherWorkerId.uuid)
    }

    private suspend fun persistPatchedApp(
        currentPackageName: String?,
        installType: InstallType
    ): Boolean = withContext(Dispatchers.IO) {
        val installedPackageInfo = currentPackageName?.let(pm::getPackageInfo)
        val patchedPackageInfo = pm.getPackageInfo(outputFile)
        val packageInfo = installedPackageInfo ?: patchedPackageInfo
        if (packageInfo == null) {
            Log.e(TAG, "Failed to resolve package info for patched APK")
            return@withContext false
        }

        val finalPackageName = packageInfo.packageName
        val finalVersion = packageInfo.versionName?.takeUnless { it.isBlank() } ?: version ?: "unspecified"

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

        val scopedBundlesFinal = patchBundleRepository.scopedBundleInfoFlow(finalPackageName, finalVersion)
            .first()
            .associateBy { it.uid }
        val sanitizedSelectionFinal = sanitizeSelection(appliedSelection, scopedBundlesFinal)
        val sanitizedOptionsFinal = sanitizeOptions(appliedOptions, scopedBundlesFinal)
        val scopedBundlesOriginal = patchBundleRepository.scopedBundleInfoFlow(
            packageName,
            input.selectedApp.version
        ).first().associateBy { it.uid }
        val sanitizedSelectionOriginal = sanitizeSelection(appliedSelection, scopedBundlesOriginal)
        val sanitizedOptionsOriginal = sanitizeOptions(appliedOptions, scopedBundlesOriginal)

        val selectionPayload = patchBundleRepository.snapshotSelection(sanitizedSelectionFinal)

        installedAppRepository.addOrUpdate(
            finalPackageName,
            packageName,
            finalVersion,
            installType,
            sanitizedSelectionFinal,
            selectionPayload
        )

        if (finalPackageName != packageName) {
            patchSelectionRepository.updateSelection(finalPackageName, sanitizedSelectionFinal)
            patchOptionsRepository.saveOptions(finalPackageName, sanitizedOptionsFinal)
        }
        patchSelectionRepository.updateSelection(packageName, sanitizedSelectionOriginal)
        patchOptionsRepository.saveOptions(packageName, sanitizedOptionsOriginal)
        appliedSelection = sanitizedSelectionOriginal
        appliedOptions = sanitizedOptionsOriginal

        savedPatchedApp = savedPatchedApp || installType == InstallType.SAVED || savedCopy.exists()
        true
    }

    fun savePatchedAppForLater(
        onResult: (Boolean) -> Unit = {},
        showToast: Boolean = true
    ) {
        if (!outputFile.exists()) {
            app.toast(app.getString(R.string.patched_app_save_failed_toast))
            onResult(false)
            return
        }

        viewModelScope.launch {
            val success = persistPatchedApp(null, InstallType.SAVED)
            if (success) {
                if (showToast) {
                    app.toast(app.getString(R.string.patched_app_saved_toast))
                }
            } else {
                app.toast(app.getString(R.string.patched_app_save_failed_toast))
            }
            onResult(success)
        }
    }

    private val installerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    if (pkg == awaitingPackageInstall) {
                        awaitingPackageInstall = null
                        installedPackageName = pkg
                        viewModelScope.launch {
                            val persisted = persistPatchedApp(pkg, InstallType.DEFAULT)
                            if (!persisted) {
                                Log.w(TAG, "Failed to persist installed patched app metadata (package added broadcast)")
                            }
                        }
                        app.toast(app.getString(R.string.install_app_success))
                        updateInstallingState(false)
                    } else {
                        handleExternalInstallSuccess(pkg)
                    }
                }

                InstallService.APP_INSTALL_ACTION -> {
                    val pmStatus = intent.getIntExtra(
                        InstallService.EXTRA_INSTALL_STATUS,
                        PackageInstaller.STATUS_FAILURE
                    )

                    intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_STATUS_MESSAGE)
                        ?.let(logger::trace)

                    updateInstallingState(false)

                    if (pmStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        updateInstallingState(true)
                        return
                    }

                    if (pmStatus == PackageInstaller.STATUS_SUCCESS) {
                        val packageName = intent.getStringExtra(InstallService.EXTRA_PACKAGE_NAME)
                        awaitingPackageInstall = null
                        installedPackageName = packageName
                        installFailureMessage = null
                        viewModelScope.launch {
                            val persisted = persistPatchedApp(installedPackageName, InstallType.DEFAULT)
                            if (!persisted) {
                                Log.w(TAG, "Failed to persist installed patched app metadata")
                            }
                        }
                        app.toast(app.getString(R.string.install_app_success))
                        installStatus = InstallCompletionStatus.Success(packageName)
                        updateInstallingState(false)
                        packageInstallerStatus = null
                    } else {
                        awaitingPackageInstall = null
                        packageInstallerStatus = pmStatus
                        val rawMessage = intent.getStringExtra(InstallService.EXTRA_INSTALL_STATUS_MESSAGE)
                            ?.takeIf { it.isNotBlank() }
                        val formatted = installerManager.formatFailureHint(pmStatus, rawMessage)
                        val message = formatted
                            ?: rawMessage
                            ?: app.getString(R.string.install_app_fail, pmStatus.toString())
                        packageInstallerStatus = null
                        showInstallFailure(message)
                    }
                }

                UninstallService.APP_UNINSTALL_ACTION -> {
                    val pmStatus = intent.getIntExtra(
                        UninstallService.EXTRA_UNINSTALL_STATUS,
                        PackageInstaller.STATUS_FAILURE
                    )

                    intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_STATUS_MESSAGE)
                        ?.let(logger::trace)

                    if (pmStatus != PackageInstaller.STATUS_SUCCESS)
                        packageInstallerStatus = pmStatus
                }
            }
        }
    }

    init {
        // TODO: detect system-initiated process death during the patching process.
        ContextCompat.registerReceiver(
            app,
            installerBroadcastReceiver,
            IntentFilter().apply {
                addAction(InstallService.APP_INSTALL_ACTION)
                addAction(UninstallService.APP_UNINSTALL_ACTION)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        viewModelScope.launch {
            installedApp = installedAppRepository.get(packageName)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        app.unregisterReceiver(installerBroadcastReceiver)
        workManager.cancelWorkById(patcherWorkerId.uuid)
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null

        if (input.selectedApp is SelectedApp.Installed && installedApp?.installType == InstallType.MOUNT) {
            GlobalScope.launch(Dispatchers.Main) {
                uiSafe(app, R.string.failed_to_mount, "Failed to mount") {
                    withTimeout(Duration.ofMinutes(1L)) {
                        rootInstaller.mount(packageName)
                    }
                }
            }
        }

        if (input.selectedApp is SelectedApp.Local && input.selectedApp.temporary) {
            inputFile?.takeIf { it.exists() }?.delete()
            inputFile = null
        }
    }

    fun onBack() {
        // tempDir cannot be deleted inside onCleared because it gets called on system-initiated process death.
        tempDir.deleteRecursively()
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

            val wasAlreadySaved = hasSavedPatchedApp
            val saved = persistPatchedApp(null, InstallType.SAVED)
            if (!saved) {
                app.toast(app.getString(R.string.patched_app_save_failed_toast))
            } else if (!wasAlreadySaved) {
                app.toast(app.getString(R.string.patched_app_saved_toast))
            }

            app.toast(app.getString(R.string.save_apk_success))
        }
    }

    fun exportLogs(context: Context) {
        val stepLines = steps.mapIndexed { index, step ->
            buildString {
                append(index + 1)
                append(". ")
                append(step.name)
                append(" [")
                append(context.getString(step.category.displayName))
                append("] - ")
                append(step.state.name)
                step.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it)
                }
            }
        }

        val logLines = logs.toList().map { (level, msg) -> "[${level.name}]: $msg" }

        val content = buildString {
            appendLine("=== Patcher Steps ===")
            if (stepLines.isEmpty()) {
                appendLine("No steps recorded.")
            } else {
                stepLines.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("=== Patcher Log ===")
            if (logLines.isEmpty()) {
                appendLine("No log messages recorded.")
            } else {
                logLines.forEach { appendLine(it) }
            }
        }

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    fun open() = installedPackageName?.let(pm::launch)

    private suspend fun performInstall(installType: InstallType) {
        var pmInstallStarted = false
        try {
            updateInstallingState(true)
            installStatus = InstallCompletionStatus.InProgress

            val currentPackageInfo = pm.getPackageInfo(outputFile)
                ?: throw Exception("Failed to load application info")

            // If the app is currently installed
            val existingPackageInfo = pm.getPackageInfo(currentPackageInfo.packageName)
            if (existingPackageInfo != null) {
                // Check if the app version is less than the installed version
                if (pm.getVersionCode(currentPackageInfo) < pm.getVersionCode(existingPackageInfo)) {
                    // Exit if the selected app version is less than the installed version
                    packageInstallerStatus = PackageInstaller.STATUS_FAILURE_CONFLICT
                    return
                }
            }

            when (installType) {
                InstallType.DEFAULT, InstallType.SAVED -> {
                    // Check if the app is mounted as root
                    // If it is, unmount it first, silently
                    if (rootInstaller.hasRootAccess() && rootInstaller.isAppMounted(packageName)) {
                        rootInstaller.unmount(packageName)
                    }

                    // Install regularly
                    awaitingPackageInstall = currentPackageInfo.packageName
                    try {
                        pm.installApp(listOf(outputFile))
                        pmInstallStarted = true
                        installStatus = InstallCompletionStatus.InProgress
                        scheduleInstallTimeout(currentPackageInfo.packageName)
                    } catch (installError: Exception) {
                        Log.e(TAG, "PackageInstaller.installApp failed", installError)
                        packageInstallerStatus = null
                        awaitingPackageInstall = null
                        showInstallFailure(
                            app.getString(
                                R.string.install_app_fail,
                                installError.simpleMessage() ?: installError.javaClass.simpleName.orEmpty()
                            )
                        )
                        return
                    }
                }

                InstallType.MOUNT -> {
                    try {
                        val packageInfo = pm.getPackageInfo(outputFile)
                            ?: throw Exception("Failed to load application info")
                        val label = with(pm) {
                            packageInfo.label()
                        }

                        // Check for base APK, first check if the app is already installed
                        if (existingPackageInfo == null) {
                            // If the app is not installed, check if the output file is a base apk
                            if (currentPackageInfo.splitNames.isNotEmpty()) {
                                // Exit if there is no base APK package
                                packageInstallerStatus = PackageInstaller.STATUS_FAILURE_INVALID
                                return
                            }
                        }

                        val inputVersion = input.selectedApp.version
                            ?: inputFile?.let(pm::getPackageInfo)?.versionName
                            ?: throw Exception("Failed to determine input APK version")

                        // Install as root
                        rootInstaller.install(
                            outputFile,
                            inputFile,
                            packageName,
                            inputVersion,
                            label
                        )

                        if (!persistPatchedApp(packageInfo.packageName, InstallType.MOUNT)) {
                            Log.w(TAG, "Failed to persist mounted patched app metadata")
                        }

                        rootInstaller.mount(packageName)

                        installedPackageName = packageName

                        app.toast(app.getString(R.string.install_app_success))
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to install as root", e)
                        packageInstallerStatus = null
                        showInstallFailure(
                            app.getString(
                                R.string.install_app_fail,
                                e.simpleMessage() ?: e.javaClass.simpleName.orEmpty()
                            )
                        )
                        try {
                            rootInstaller.uninstall(packageName)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
                    Log.e(tag, "Failed to install", e)
                    awaitingPackageInstall = null
                    packageInstallerStatus = null
                    showInstallFailure(
                        app.getString(
                            R.string.install_app_fail,
                            e.simpleMessage() ?: e.javaClass.simpleName.orEmpty()
                        )
                    )
                } finally {
                    if (!pmInstallStarted) updateInstallingState(false)
                }
            }

    private suspend fun performShizukuInstall() {
        updateInstallingState(true)
        installStatus = InstallCompletionStatus.InProgress
        packageInstallerStatus = null
        try {

            val currentPackageInfo = pm.getPackageInfo(outputFile)
                ?: throw Exception("Failed to load application info")

            val existingPackageInfo = pm.getPackageInfo(currentPackageInfo.packageName)
            if (existingPackageInfo != null) {
                if (pm.getVersionCode(currentPackageInfo) < pm.getVersionCode(existingPackageInfo)) {
                    packageInstallerStatus = PackageInstaller.STATUS_FAILURE_CONFLICT
                    return
                }
            }

            if (rootInstaller.hasRootAccess() && rootInstaller.isAppMounted(packageName)) {
                rootInstaller.unmount(packageName)
            }

            awaitingPackageInstall = currentPackageInfo.packageName
            val result = shizukuInstaller.install(outputFile, currentPackageInfo.packageName)
            packageInstallerStatus = result.status
            if (result.status != PackageInstaller.STATUS_SUCCESS) {
                throw ShizukuInstaller.InstallerOperationException(result.status, result.message)
            }

            val persisted = persistPatchedApp(currentPackageInfo.packageName, InstallType.DEFAULT)
            if (!persisted) {
                Log.w(TAG, "Failed to persist installed patched app metadata")
            }

            installedPackageName = currentPackageInfo.packageName
            app.toast(app.getString(R.string.install_app_success))
            updateInstallingState(false)
        } catch (error: ShizukuInstaller.InstallerOperationException) {
            Log.e(tag, "Failed to install via Shizuku", error)
            val message = error.message ?: app.getString(R.string.installer_hint_generic)
            packageInstallerStatus = null
            showInstallFailure(app.getString(R.string.install_app_fail, message))
        } catch (error: Exception) {
            Log.e(tag, "Failed to install via Shizuku", error)
            if (packageInstallerStatus == null) {
                packageInstallerStatus = PackageInstaller.STATUS_FAILURE
            }
            showInstallFailure(
                app.getString(
                    R.string.install_app_fail,
                    error.simpleMessage() ?: error.javaClass.simpleName.orEmpty()
                )
            )
        } finally {
            if (packageInstallerStatus != PackageInstaller.STATUS_SUCCESS) {
                awaitingPackageInstall = null
            }
            if (packageInstallerStatus == PackageInstaller.STATUS_SUCCESS) {
                updateInstallingState(false)
            }
        }
    }

    private suspend fun executeInstallPlan(plan: InstallerManager.InstallPlan) {
        when (plan) {
            is InstallerManager.InstallPlan.Internal -> {
                pendingExternalInstall?.let(installerManager::cleanup)
                pendingExternalInstall = null
                externalInstallTimeoutJob?.cancel()
                externalInstallTimeoutJob = null
                performInstall(installTypeFor(plan.target))
            }

            is InstallerManager.InstallPlan.Root -> {
                pendingExternalInstall?.let(installerManager::cleanup)
                pendingExternalInstall = null
                externalInstallTimeoutJob?.cancel()
                externalInstallTimeoutJob = null
                performInstall(InstallType.MOUNT)
            }

            is InstallerManager.InstallPlan.Shizuku -> {
                pendingExternalInstall?.let(installerManager::cleanup)
                pendingExternalInstall = null
                externalInstallTimeoutJob?.cancel()
                externalInstallTimeoutJob = null
                performShizukuInstall()
            }

            is InstallerManager.InstallPlan.External -> launchExternalInstaller(plan)
        }
    }

    private fun installTypeFor(target: InstallerManager.InstallTarget): InstallType = when (target) {
        InstallerManager.InstallTarget.PATCHER -> InstallType.DEFAULT
        InstallerManager.InstallTarget.SAVED_APP -> InstallType.DEFAULT
        InstallerManager.InstallTarget.MANAGER_UPDATE -> InstallType.DEFAULT
    }

    private fun launchExternalInstaller(plan: InstallerManager.InstallPlan.External) {
        pendingExternalInstall?.let { installerManager.cleanup(it) }
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null

        pendingExternalInstall = plan
        updateInstallingState(true)
        installStatus = InstallCompletionStatus.InProgress

        try {
            ContextCompat.startActivity(app, plan.intent, null)
            app.toast(app.getString(R.string.installer_external_launched, plan.installerLabel))
        } catch (error: ActivityNotFoundException) {
            installerManager.cleanup(plan)
            pendingExternalInstall = null
            updateInstallingState(false)
            externalInstallTimeoutJob = null
            showInstallFailure(
                app.getString(
                    R.string.install_app_fail,
                    error.simpleMessage() ?: error.javaClass.simpleName.orEmpty()
                )
            )
            return
        }

        scheduleInstallTimeout(plan.expectedPackage, EXTERNAL_INSTALL_TIMEOUT_MS)
    }

    private fun handleExternalInstallSuccess(packageName: String) {
        val plan = pendingExternalInstall ?: return
        if (plan.expectedPackage != packageName) return

        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        installerManager.cleanup(plan)
        updateInstallingState(false)
        installStatus = InstallCompletionStatus.Success(packageName)

        when (plan.target) {
            InstallerManager.InstallTarget.PATCHER -> {
                installedPackageName = packageName
                viewModelScope.launch {
                    val persisted = persistPatchedApp(packageName, InstallType.DEFAULT)
                    if (!persisted) {
                        Log.w(TAG, "Failed to persist installed patched app metadata (external installer)")
                    }
                }
                app.toast(app.getString(R.string.installer_external_success, plan.installerLabel))
            }

            InstallerManager.InstallTarget.SAVED_APP,
            InstallerManager.InstallTarget.MANAGER_UPDATE -> {
                app.toast(app.getString(R.string.installer_external_success, plan.installerLabel))
            }
        }
    }

    override fun install() {
        if (isInstalling) return
        viewModelScope.launch {
            val plan = installerManager.resolvePlan(
                InstallerManager.InstallTarget.PATCHER,
                outputFile,
                packageName,
                null
            )
            executeInstallPlan(plan)
        }
    }

    override fun reinstall() {
        if (isInstalling) return
        viewModelScope.launch {
            val plan = installerManager.resolvePlan(
                InstallerManager.InstallTarget.PATCHER,
                outputFile,
                packageName,
                null
            )
            when (plan) {
                is InstallerManager.InstallPlan.Internal -> {
                    pendingExternalInstall?.let(installerManager::cleanup)
                    pendingExternalInstall = null
                    externalInstallTimeoutJob?.cancel()
                    externalInstallTimeoutJob = null
                    try {
                        val pkg = pm.getPackageInfo(outputFile)?.packageName
                            ?: throw Exception("Failed to load application info")
                        pm.uninstallPackage(pkg)
                        performInstall(InstallType.DEFAULT)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to reinstall", e)
                        app.toast(app.getString(R.string.reinstall_app_fail, e.simpleMessage()))
                    }
                }
                is InstallerManager.InstallPlan.Root -> {
                    pendingExternalInstall?.let(installerManager::cleanup)
                    pendingExternalInstall = null
                    externalInstallTimeoutJob?.cancel()
                    externalInstallTimeoutJob = null
                    performInstall(InstallType.MOUNT)
                }
                is InstallerManager.InstallPlan.Shizuku -> {
                    pendingExternalInstall?.let(installerManager::cleanup)
                    pendingExternalInstall = null
                    externalInstallTimeoutJob?.cancel()
                    externalInstallTimeoutJob = null
                    performShizukuInstall()
                }
                is InstallerManager.InstallPlan.External -> launchExternalInstaller(plan)
            }
        }
    }

    fun dismissPackageInstallerDialog() {
        packageInstallerStatus = null
    }

    fun dismissInstallFailureMessage() {
        installFailureMessage = null
        packageInstallerStatus = null
        awaitingPackageInstall = null
        installStatus = null
    }

    fun clearInstallStatus() {
        installStatus = null
    }

    sealed class InstallCompletionStatus {
        data object InProgress : InstallCompletionStatus()
        data class Success(val packageName: String?) : InstallCompletionStatus()
        data class Failure(val message: String) : InstallCompletionStatus()
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

        return PatcherWorker.Args(
            selectedForRun,
            outputFile.path,
            input.selectedPatches,
            input.options,
            logger,
            onDownloadProgress = {
                withContext(Dispatchers.Main) {
                    downloadProgress = it
                }
            },
            onPatchCompleted = {
                withContext(Dispatchers.Main) { completedPatchCount += 1 }
            },
            setInputFile = { file ->
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

                withContext(Dispatchers.Main) { inputFile = storedFile }
            },
            handleStartActivityRequest = { plugin, intent ->
                withContext(Dispatchers.Main) {
                    if (currentActivityRequest != null) throw Exception("Another request is already pending.")
                    try {
                        val accepted = with(CompletableDeferred<Boolean>()) {
                            currentActivityRequest = this to plugin.name
                            await()
                        }
                        if (!accepted) throw UserInteractionException.RequestDenied()

                        try {
                            with(CompletableDeferred<ActivityResult>()) {
                                launchedActivity = this
                                launchActivityChannel.send(intent)
                                await()
                            }
                        } finally {
                            launchedActivity = null
                        }
                    } finally {
                        currentActivityRequest = null
                    }
                }
            },
            onProgress = { name, state, message ->
                viewModelScope.launch {
                    steps[currentStepIndex] = steps[currentStepIndex].run {
                        copy(
                            name = name ?: this.name,
                            state = state ?: this.state,
                            message = message ?: this.message,
                            subSteps = subSteps ?: this.subSteps
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
                    if (input.selectedApp is SelectedApp.Local && input.selectedApp.temporary) {
                        inputFile?.takeIf { it.exists() }?.delete()
                        inputFile = null
                    }
                    refreshExportMetadata()
                    _patcherSucceeded.value = true
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
                val newLimit = (previousLimit - MEMORY_ADJUSTMENT_MB).coerceAtLeast(MemoryLimitConfig.MIN_LIMIT_MB)
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

        val failureMessage = workInfo.outputData.getString(PatcherWorker.PROCESS_FAILURE_MESSAGE_KEY)
        if (!failureMessage.isNullOrBlank()) {
            val missing = MISSING_PATCH_REGEX.findAll(failureMessage)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
            if (missing.isNotEmpty()) {
                missingPatchDialog = MissingPatchDialogState(missing)
            }
        }
    }

    fun dismissMemoryAdjustmentDialog() {
        memoryAdjustmentDialog = null
    }

    fun retryAfterMemoryAdjustment() {
        viewModelScope.launch {
            memoryAdjustmentDialog = null
            handledFailureIds.clear()
            resetStateForRetry()
            workManager.cancelWorkById(patcherWorkerId.uuid)
            val newId = launchWorker()
            patcherWorkerId = ParcelUuid(newId)
            observeWorker(newId)
        }
    }

    private fun resetStateForRetry() {
        completedPatchCount = 0
        downloadProgress = null
        val newSteps = generateSteps(app, input.selectedApp).toMutableStateList()
        steps.clear()
        steps.addAll(newSteps)
        currentStepIndex = newSteps.indexOfFirst { it.state == State.RUNNING }.takeIf { it >= 0 } ?: 0
        _patcherSucceeded.value = null
    }

    private fun sanitizeSelection(
        selection: PatchSelection,
        bundles: Map<Int, PatchBundleInfo.Scoped>
    ): PatchSelection = buildMap {
        selection.forEach { (uid, patches) ->
            val bundle = bundles[uid] ?: return@forEach
            val valid = bundle.patches.map { it.name }.toSet()
            val kept = patches.filter { it in valid }.toSet()
            if (kept.isNotEmpty()) put(uid, kept)
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

    private companion object {
        const val TAG = "Morphe Patcher"
        private const val SYSTEM_INSTALL_TIMEOUT_MS = 15_000L
        private const val EXTERNAL_INSTALL_TIMEOUT_MS = 25_000L
        private const val MEMORY_ADJUSTMENT_MB = 200
        private val MISSING_PATCH_REGEX = Regex("Patch with name (.+?) does not exist")

        fun LogLevel.androidLog(msg: String) = when (this) {
            LogLevel.TRACE -> Log.v(TAG, msg)
            LogLevel.INFO -> Log.i(TAG, msg)
            LogLevel.WARN -> Log.w(TAG, msg)
            LogLevel.ERROR -> Log.e(TAG, msg)
        }

        fun generateSteps(context: Context, selectedApp: SelectedApp): List<Step> {
            val needsDownload =
                selectedApp is SelectedApp.Download || selectedApp is SelectedApp.Search

            return listOfNotNull(
                Step(
                    context.getString(R.string.download_apk),
                    StepCategory.PREPARING,
                    state = State.RUNNING,
                    progressKey = ProgressKey.DOWNLOAD,
                ).takeIf { needsDownload },
                Step(
                    context.getString(R.string.patcher_step_load_patches),
                    StepCategory.PREPARING,
                    state = if (needsDownload) State.WAITING else State.RUNNING,
                    subSteps = 2
                ),
                Step(
                    context.getString(R.string.patcher_step_unpack),
                    StepCategory.PREPARING
                ),

                Step(
                    context.getString(R.string.applying_patches),
                    StepCategory.PATCHING
                ),

                Step(
                    context.getString(R.string.patcher_step_write_patched),
                    StepCategory.SAVING,
                    subSteps = 4
                ),
                Step(
                    context.getString(R.string.patcher_step_sign_apk),
                    StepCategory.SAVING,
                    subSteps = 2
                )
            )
        }
    }
}
