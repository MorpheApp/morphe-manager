package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
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
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE_MUSIC
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.OriginalApkRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.domain.repository.PatchSelectionRepository
import app.revanced.manager.domain.worker.WorkerRepository
import app.revanced.manager.patcher.logger.LogLevel
import app.revanced.manager.patcher.logger.Logger
import app.revanced.manager.patcher.runtime.MemoryLimitConfig
import app.revanced.manager.patcher.runtime.ProcessRuntime
import app.revanced.manager.patcher.split.SplitApkPreparer
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
import app.revanced.manager.ui.model.StepId
import app.revanced.manager.ui.model.StepProgressProvider
import app.revanced.manager.ui.model.navigation.Patcher
import app.revanced.manager.util.Options
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.PatchedAppExportData
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.util.saveableVar
import app.revanced.manager.util.saver.snapshotStateListSaver
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.tag
import app.revanced.manager.util.toast
import app.revanced.manager.util.toastHandle
import app.revanced.manager.util.uiSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
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
import kotlin.math.max
import kotlin.math.min

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
    private val patchOptionsPrefs: PatchOptionsPreferencesManager by inject()
    private val originalApkRepository: OriginalApkRepository by inject()
    private val savedStateHandle: SavedStateHandle = get()

    private var pendingExternalInstall: InstallerManager.InstallPlan.External? = null
    private var externalInstallBaseline: Pair<Long?, Long?>? = null
    private var externalInstallStartTime: Long? = null
    private var externalPackageWasPresentAtStart: Boolean = false
    private var externalInstallTimeoutJob: Job? = null
    private var externalInstallPresenceJob: Job? = null
    private var expectedInstallSignature: ByteArray? = null
    private var baselineInstallSignature: ByteArray? = null
    private var internalInstallBaseline: Pair<Long?, Long?>? = null
    private var internalInstallMonitorJob: Job? = null
    private var postTimeoutGraceJob: Job? = null
    private var installProgressToastJob: Job? = null
    private var installProgressToast: Toast? = null
    private var uninstallProgressToastJob: Job? = null
    private var uninstallProgressToast: Toast? = null
    private var pendingSignatureMismatchPlan: InstallerManager.InstallPlan? = null
    private var pendingSignatureMismatchPackage: String? = null
    private var signatureMismatchUninstallJob: Job? = null

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
    var signatureMismatchPackage by mutableStateOf<String?>(null)
        private set
    var activeInstallType by mutableStateOf<InstallType?>(null)
        private set
    var lastInstallType by mutableStateOf<InstallType?>(null)
        private set

    private fun updateInstallingState(value: Boolean) {
        ongoingPmSession = value
        isInstalling = value
        if (!value) {
            awaitingPackageInstall = null
            externalInstallTimeoutJob?.cancel()
            externalInstallTimeoutJob = null
            externalInstallPresenceJob?.cancel()
            externalInstallPresenceJob = null
            externalInstallBaseline = null
            internalInstallMonitorJob?.cancel()
            internalInstallMonitorJob = null
            internalInstallBaseline = null
            stopInstallProgressToasts()
            activeInstallType = null
            suppressFailureAfterSuccess = false
            packageInstallerStatus = null
            expectedInstallSignature = null
            baselineInstallSignature = null
            pendingSignatureMismatchPlan = null
            pendingSignatureMismatchPackage = null
            signatureMismatchPackage = null
            signatureMismatchUninstallJob?.cancel()
            signatureMismatchUninstallJob = null
            stopUninstallProgressToasts()
        } else {
            postTimeoutGraceJob?.cancel()
            postTimeoutGraceJob = null
            startInstallProgressToasts()
            suppressFailureAfterSuccess = false
        }
    }

    private fun markInstallSuccess(packageName: String?) {
        if (installStatus is InstallCompletionStatus.Success) return
        installStatus = InstallCompletionStatus.Success(packageName)
        app.toast(app.getString(R.string.install_app_success))
    }

    private fun handleUninstallFailure(message: String) {
        pendingSignatureMismatchPlan = null
        pendingSignatureMismatchPackage = null
        signatureMismatchPackage = null
        stopUninstallProgressToasts()
        showInstallFailure(message)
    }

    private fun uninstallTimeoutMessage(): String =
        app.getString(R.string.uninstall_timeout_message)
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

    fun dismissMissingPatchWarning() {
        missingPatchWarning = null
    }

    fun proceedAfterMissingPatchWarning() {
        if (missingPatchWarning == null) return
        viewModelScope.launch {
            missingPatchWarning = null
            startWorker()
        }
    }

    fun removeMissingPatchesAndStart() {
        val warning = missingPatchWarning ?: return
        viewModelScope.launch {
            val scopedBundles = gatherScopedBundles()
            val sanitizedSelection = sanitizeSelection(appliedSelection, scopedBundles)
            val sanitizedOptions = sanitizeOptions(appliedOptions, scopedBundles)
            appliedSelection = sanitizedSelection
            appliedOptions = sanitizedOptions
            missingPatchWarning = null
            startWorker()
        }
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
    private var suppressFailureAfterSuccess = false
    private var lastSuccessInstallType: InstallType? = null
    private var lastSuccessAtMs: Long = 0L

    private fun showInstallFailure(message: String) {
        val now = System.currentTimeMillis()
        if (activeInstallType == InstallType.SHIZUKU && suppressFailureAfterSuccess) return
        if (lastSuccessInstallType == InstallType.SHIZUKU && now - lastSuccessAtMs < SUPPRESS_FAILURE_AFTER_SUCCESS_MS) return
        if (lastSuccessInstallType == InstallType.SHIZUKU) return
        if (installStatus is InstallCompletionStatus.Success || suppressFailureAfterSuccess) return
        val adjusted = if (activeInstallType == InstallType.MOUNT) {
            val replaced = message.replace("install", "mount", ignoreCase = true)
            replaced.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        } else message
        if (activeInstallType != null) {
            lastInstallType = activeInstallType
        }
        installFailureMessage = adjusted
        installStatus = InstallCompletionStatus.Failure(adjusted)
        updateInstallingState(false)
        stopInstallProgressToasts()
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallBaseline = null
        externalInstallStartTime = null
        externalPackageWasPresentAtStart = false
        expectedInstallSignature = null
        baselineInstallSignature = null
        packageInstallerStatus = null
    }

    private fun showSignatureMismatchPrompt(
        packageName: String,
        plan: InstallerManager.InstallPlan
    ) {
        stopInstallProgressToasts()
        if (isInstalling || installStatus != null) {
            updateInstallingState(false)
        } else {
            installStatus = null
            packageInstallerStatus = null
            installFailureMessage = null
        }
        pendingSignatureMismatchPlan = plan
        pendingSignatureMismatchPackage = packageName
        signatureMismatchPackage = packageName
    }

    private fun startSignatureMismatchUninstallTimeout(targetPackage: String) {
        signatureMismatchUninstallJob?.cancel()
        signatureMismatchUninstallJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + SIGNATURE_MISMATCH_UNINSTALL_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val pendingPlan = pendingSignatureMismatchPlan
                val pendingPackage = pendingSignatureMismatchPackage
                if (pendingPlan == null || pendingPackage.isNullOrBlank() || pendingPackage != targetPackage) {
                    return@launch
                }
                if (pm.getPackageInfo(targetPackage) == null) {
                    pendingSignatureMismatchPlan = null
                    pendingSignatureMismatchPackage = null
                    signatureMismatchPackage = null
                    signatureMismatchUninstallJob = null
                    stopUninstallProgressToasts()
                    executeInstallPlan(pendingPlan)
                    return@launch
                }
                delay(SIGNATURE_MISMATCH_UNINSTALL_POLL_MS)
            }
            if (pendingSignatureMismatchPackage == targetPackage) {
                stopUninstallProgressToasts()
                val failureMessage = app.getString(
                    R.string.uninstall_app_fail,
                    uninstallTimeoutMessage()
                )
                handleUninstallFailure(failureMessage)
            }
        }
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
                val baselineSnapshot = internalInstallBaseline ?: externalInstallBaseline
                val startTimeSnapshot = externalInstallStartTime
                val expectedSignatureSnapshot = expectedInstallSignature
                val baselineSignatureSnapshot = baselineInstallSignature
                val packageWasPresentAtStartSnapshot = externalPackageWasPresentAtStart
                val installTypeSnapshot = pendingExternalInstall
                    ?.takeIf { it.expectedPackage == packageName }
                    ?.let { plan ->
                        if (plan.token is InstallerManager.Token.Component) InstallType.CUSTOM else InstallType.DEFAULT
                    }
                    ?: activeInstallType
                    ?: InstallType.DEFAULT

                packageInstallerStatus = null
                if (!tryMarkInstallIfPresent(packageName)) {
                    val message = timeoutMessage?.invoke() ?: app.getString(R.string.install_timeout_message)
                    showInstallFailure(message)
                    startPostTimeoutGraceWatch(
                        packageName = packageName,
                        installType = installTypeSnapshot,
                        baseline = baselineSnapshot,
                        startTimeMs = startTimeSnapshot,
                        expectedSignature = expectedSignatureSnapshot,
                        baselineSignature = baselineSignatureSnapshot,
                        packageWasPresentAtStart = packageWasPresentAtStartSnapshot
                    )
                }
            }
        }
    }

    private fun startPostTimeoutGraceWatch(
        packageName: String,
        installType: InstallType,
        baseline: Pair<Long?, Long?>?,
        startTimeMs: Long?,
        expectedSignature: ByteArray?,
        baselineSignature: ByteArray?,
        packageWasPresentAtStart: Boolean
    ) {
        postTimeoutGraceJob?.cancel()
        postTimeoutGraceJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + POST_TIMEOUT_GRACE_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val info = pm.getPackageInfo(packageName)
                if (info != null) {
                    val updated = isUpdatedSinceBaseline(info, baseline, startTimeMs)
                    val signatureChangedToExpected = if (expectedSignature != null) {
                        val current = readInstalledSignatureBytes(packageName)
                        current != null &&
                                current.contentEquals(expectedSignature) &&
                                (!packageWasPresentAtStart || baselineSignature != null) &&
                                (baselineSignature == null || !baselineSignature.contentEquals(current))
                    } else {
                        false
                    }

                    if (updated || signatureChangedToExpected) {
                        forceMarkInstallSuccess(packageName, installType)
                        return@launch
                    }
                }
                delay(INSTALL_MONITOR_POLL_MS)
            }
        }
    }

    private fun monitorExternalInstall(plan: InstallerManager.InstallPlan.External) {
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = viewModelScope.launch {
            val timeoutAt = System.currentTimeMillis() + EXTERNAL_INSTALL_TIMEOUT_MS
            var presentSeen = false
            while (isActive) {
                if (pendingExternalInstall != plan) return@launch

                val currentInfo = pm.getPackageInfo(plan.expectedPackage)
                if (currentInfo != null) {
                    if (tryHandleExternalInstallSuccess(plan, currentInfo)) {
                        return@launch
                    }
                }

                val remaining = timeoutAt - System.currentTimeMillis()
                if (remaining <= 0L) break
                delay(INSTALL_MONITOR_POLL_MS)
            }

            if (pendingExternalInstall == plan && installStatus is InstallCompletionStatus.InProgress) {
                val info = pm.getPackageInfo(plan.expectedPackage)
                if (info != null && tryHandleExternalInstallSuccess(plan, info)) return@launch
                showInstallFailure(app.getString(R.string.installer_external_timeout, plan.installerLabel))
            }
        }
        startExternalPresenceWatch(plan.expectedPackage)
    }

    private fun monitorInternalInstall(packageName: String) {
        internalInstallMonitorJob?.cancel()
        internalInstallMonitorJob = viewModelScope.launch {
            val timeoutAt = System.currentTimeMillis() + SYSTEM_INSTALL_TIMEOUT_MS
            while (isActive) {
                if (installStatus !is InstallCompletionStatus.InProgress) return@launch
                if (handleDetectedInstall(packageName)) return@launch

                val remaining = timeoutAt - System.currentTimeMillis()
                if (remaining <= 0L) break
                delay(INSTALL_MONITOR_POLL_MS)
            }
        }
    }

    private fun isUpdatedSinceBaseline(
        info: PackageInfo,
        baseline: Pair<Long?, Long?>?,
        startTime: Long?
    ): Boolean {
        val vc = pm.getVersionCode(info)
        val updated = info.lastUpdateTime
        val baseVc = baseline?.first
        val baseUpdated = baseline?.second
        val versionChanged = baseVc != null && vc != baseVc
        val timestampChanged = baseUpdated != null && updated > baseUpdated
        val started = startTime ?: 0L
        val updatedSinceStart = updated >= started && started > 0L
        return baseline == null || versionChanged || timestampChanged || updatedSinceStart
    }

    private fun forceMarkInstallSuccess(packageName: String, installType: InstallType = InstallType.DEFAULT) {
        if (installStatus is InstallCompletionStatus.Success) return
        suppressFailureAfterSuccess = true
        postTimeoutGraceJob?.cancel()
        postTimeoutGraceJob = null
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        externalInstallBaseline = null
        externalInstallStartTime = null
        externalPackageWasPresentAtStart = false
        expectedInstallSignature = null
        baselineInstallSignature = null
        internalInstallMonitorJob?.cancel()
        internalInstallMonitorJob = null
        internalInstallBaseline = null
        awaitingPackageInstall = null
        installedPackageName = packageName
        installFailureMessage = null
        packageInstallerStatus = null
        markInstallSuccess(packageName)
        updateInstallingState(false)
        stopInstallProgressToasts()
        lastSuccessInstallType = installType
        lastSuccessAtMs = System.currentTimeMillis()
        viewModelScope.launch {
            val persisted = persistPatchedApp(packageName, installType)
            if (!persisted) {
                Log.w(TAG, "Failed to persist installed patched app metadata (detected)")
            }
        }
    }

    private fun handleDetectedInstall(packageName: String): Boolean {
        val info = pm.getPackageInfo(packageName) ?: return false
        val externalPlan = pendingExternalInstall?.takeIf { it.expectedPackage == packageName }
        val updated =
            if (externalPlan != null) {
                isUpdatedSinceExternalBaseline(info, externalInstallBaseline, externalInstallStartTime)
            } else {
                val baseline = internalInstallBaseline ?: externalInstallBaseline
                isUpdatedSinceBaseline(info, baseline, externalInstallStartTime)
            }
        val signatureChangedToExpected =
            if (externalPlan != null) {
                shouldTreatAsInstalledBySignature(packageName, externalPackageWasPresentAtStart)
            } else {
                false
            }
        if (!updated && !signatureChangedToExpected) return false

        val installType = pendingExternalInstall
            ?.takeIf { it.expectedPackage == packageName }
            ?.let { plan ->
                if (plan.token is InstallerManager.Token.Component) InstallType.CUSTOM else InstallType.DEFAULT
            }
            ?: activeInstallType
            ?: InstallType.DEFAULT

        forceMarkInstallSuccess(packageName, installType)
        return true
    }

    private fun startExternalPresenceWatch(packageName: String) {
        externalInstallPresenceJob?.cancel()
        externalInstallPresenceJob = viewModelScope.launch {
            while (isActive) {
                val plan = pendingExternalInstall ?: return@launch
                if (plan.expectedPackage != packageName) return@launch

                val info = pm.getPackageInfo(packageName)
                if (info != null) {
                    if (tryHandleExternalInstallSuccess(plan, info)) {
                        return@launch
                    }
                }
                delay(INSTALL_MONITOR_POLL_MS)
            }
        }
    }

    private fun shouldTreatAsInstalledBySignature(packageName: String, packageWasPresentAtStart: Boolean): Boolean {
        val expected = expectedInstallSignature ?: return false
        val current = readInstalledSignatureBytes(packageName) ?: return false
        if (!current.contentEquals(expected)) return false
        val baseline = baselineInstallSignature
        if (packageWasPresentAtStart && baseline == null) return false
        return baseline == null || !baseline.contentEquals(current)
    }

    private fun readInstalledSignatureBytes(packageName: String): ByteArray? = runCatching {
        pm.getSignature(packageName).toByteArray()
    }.getOrNull()

    private fun readArchiveSignatureBytes(file: File): ByteArray? = runCatching {
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val pkgInfo = app.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        @Suppress("DEPRECATION")
        val signature: Signature? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()
                    ?: pkgInfo.signatures?.firstOrNull()
            } else {
                pkgInfo.signatures?.firstOrNull()
            }

        signature?.toByteArray()
    }.getOrNull()

    private fun hasSignatureMismatch(packageName: String, file: File): Boolean {
        val installed = readInstalledSignatureBytes(packageName) ?: return false
        val expected = readArchiveSignatureBytes(file) ?: return false
        return !installed.contentEquals(expected)
    }
    private fun tryMarkInstallIfPresent(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val externalPlan = pendingExternalInstall?.takeIf { it.expectedPackage == packageName }
        val info = if (externalPlan != null) pm.getPackageInfo(packageName) else null
        if (externalPlan != null && info != null) {
            return tryHandleExternalInstallSuccess(externalPlan, info)
        }
        return handleDetectedInstall(packageName)
    }

    private fun isUpdatedSinceExternalBaseline(
        info: PackageInfo,
        baseline: Pair<Long?, Long?>?,
        startTime: Long?
    ): Boolean {
        val vc = pm.getVersionCode(info)
        val updated = info.lastUpdateTime
        val baseVc = baseline?.first
        val baseUpdated = baseline?.second
        val versionChanged = baseVc != null && vc != baseVc
        val timestampChanged = baseUpdated != null && updated > baseUpdated
        val started = startTime ?: 0L
        val updatedSinceStart = updated >= started && started > 0L
        return versionChanged || timestampChanged || updatedSinceStart
    }

    private fun tryHandleExternalInstallSuccess(
        plan: InstallerManager.InstallPlan.External,
        info: PackageInfo
    ): Boolean {
        if (pendingExternalInstall != plan) return false
        val updatedSinceStart = isUpdatedSinceExternalBaseline(info, externalInstallBaseline, externalInstallStartTime)
        val signatureChangedToExpected =
            shouldTreatAsInstalledBySignature(plan.expectedPackage, externalPackageWasPresentAtStart)
        if (updatedSinceStart || signatureChangedToExpected) {
            handleExternalInstallSuccess(plan.expectedPackage)
            return true
        }
        return false
    }

    private fun startInstallProgressToasts() {
        if (installProgressToastJob?.isActive == true) return
        installProgressToastJob = viewModelScope.launch {
            while (isActive) {
                val messageRes =
                    if (activeInstallType == InstallType.MOUNT) R.string.mounting_ellipsis
                    else R.string.installing_ellipsis
                installProgressToast?.cancel()
                installProgressToast = app.toastHandle(app.getString(messageRes))
                delay(INSTALL_PROGRESS_TOAST_INTERVAL_MS)
            }
        }
    }

    private fun stopInstallProgressToasts() {
        installProgressToastJob?.cancel()
        installProgressToastJob = null
        installProgressToast?.cancel()
        installProgressToast = null
    }

    private fun startUninstallProgressToasts() {
        if (uninstallProgressToastJob?.isActive == true) return
        uninstallProgressToastJob = viewModelScope.launch {
            while (isActive) {
                uninstallProgressToast?.cancel()
                uninstallProgressToast = app.toastHandle(app.getString(R.string.uninstalling_ellipsis))
                delay(INSTALL_PROGRESS_TOAST_INTERVAL_MS)
            }
        }
    }

    private fun stopUninstallProgressToasts() {
        uninstallProgressToastJob?.cancel()
        uninstallProgressToastJob = null
        uninstallProgressToast?.cancel()
        uninstallProgressToast = null
    }

    fun suppressInstallProgressToasts() = stopInstallProgressToasts()

    private val tempDir = savedStateHandle.saveable(key = "tempDir") {
        fs.uiTempDir.resolve("installer").also {
            it.deleteRecursively()
            it.mkdirs()
        }
    }

    var inputFile: File? by savedStateHandle.saveableVar()
    private var requiresSplitPreparation by savedStateHandle.saveableVar {
        initialSplitRequirement(input.selectedApp)
    }
    val outputFile = tempDir.resolve("output.apk")

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
        // Morphe
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
    private var awaitingPackageInstall: String? = null

    private var patcherWorkerId: ParcelUuid?
        get() = savedStateHandle.get("patcher_worker_id")
        set(value) {
            if (value == null) {
                savedStateHandle.remove<ParcelUuid>("patcher_worker_id")
            } else {
                savedStateHandle["patcher_worker_id"] = value
            }
        }

    init {
        val existingId = patcherWorkerId?.uuid
        if (existingId != null) {
            observeWorker(existingId)
        } else {
            viewModelScope.launch {
                runPreflightCheck()
            }
        }

        // Fallback: if inputFile is null and we have SelectedApp.Installed,
        // try to get the original APK from repository (for repatch feature)
        if (inputFile == null && input.selectedApp is SelectedApp.Installed) {
            viewModelScope.launch(Dispatchers.IO) {
                val originalApk = originalApkRepository.get(packageName)
                if (originalApk != null) {
                    val file = File(originalApk.filePath)
                    if (file.exists()) {
                        inputFile = file
                    }
                }
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
     */
    private suspend fun saveOriginalApkIfNeeded() {
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
                return
            }

            // Get version from the package info
            // Use outputFile (patched APK) because inputFile might be deleted by worker!
            // For split archives: selected.file (archive) won't have valid PackageInfo
            // For regular APKs: inputFile might be deleted
            val apkPackageInfo = pm.getPackageInfo(outputFile)
            if (apkPackageInfo == null) {
                Log.w(TAG, "Cannot get package info from output APK, skipping save")
                return
            }

            val originalVersion = apkPackageInfo.versionName?.takeUnless { it.isBlank() }
                ?: input.selectedApp.version
                ?: "unknown"

            // Does original already exist in repository?
            val existing = originalApkRepository.get(packageName)
            if (existing != null && existing.version == originalVersion) {
                Log.d(TAG, "Original APK already exists in repository (version $originalVersion), skipping duplicate save")
                return
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
                        internalInstallBaseline = null
                        internalInstallMonitorJob?.cancel()
                        internalInstallMonitorJob = null
                        val installType = activeInstallType ?: InstallType.DEFAULT
                        viewModelScope.launch {
                            val persisted = persistPatchedApp(pkg, installType)
                            if (!persisted) {
                                Log.w(TAG, "Failed to persist installed patched app metadata (package added broadcast)")
                            }
                        }
                        updateInstallingState(false)
                    } else {
                        // If we still have an external plan, mark success.
                        if (handleExternalInstallSuccess(pkg)) return
                        // Fallback: if we’re mid-install, mark any added package as success.
                        if (installStatus is InstallCompletionStatus.InProgress) {
                            forceMarkInstallSuccess(pkg)
                        }
                    }
                }

                InstallService.APP_INSTALL_ACTION -> {
                    val pmStatus = intent.getIntExtra(
                        InstallService.EXTRA_INSTALL_STATUS,
                        PackageInstaller.STATUS_FAILURE
                    )

                    intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_STATUS_MESSAGE)
                        ?.let(logger::trace)

                    if (pmStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        updateInstallingState(true)
                        return
                    }

                    if (pmStatus == PackageInstaller.STATUS_SUCCESS) {
                        val packageName = intent.getStringExtra(InstallService.EXTRA_PACKAGE_NAME)
                        awaitingPackageInstall = null
                        installedPackageName = packageName
                        internalInstallBaseline = null
                        internalInstallMonitorJob?.cancel()
                        internalInstallMonitorJob = null
                        val installType = activeInstallType ?: InstallType.DEFAULT
                        installFailureMessage = null
                        viewModelScope.launch {
                            val persisted = persistPatchedApp(installedPackageName, installType)
                            if (!persisted) {
                                Log.w(TAG, "Failed to persist installed patched app metadata")
                            }
                        }
                        markInstallSuccess(packageName)
                        lastSuccessInstallType = installType
                        lastSuccessAtMs = System.currentTimeMillis()
                        updateInstallingState(false)
                        packageInstallerStatus = null
                    } else {
                        val now = System.currentTimeMillis()
                        val recentShizukuSuccess = lastSuccessInstallType == InstallType.SHIZUKU &&
                                now - lastSuccessAtMs < SUPPRESS_FAILURE_AFTER_SUCCESS_MS * 2
                        if (activeInstallType == InstallType.SHIZUKU || recentShizukuSuccess || installStatus is InstallCompletionStatus.Success) {
                            packageInstallerStatus = null
                            installFailureMessage = null
                            if (installStatus !is InstallCompletionStatus.Success) {
                                installStatus = null
                            }
                            updateInstallingState(false)
                            return
                        }
                        // Hard block failure surfacing for Shizuku even if state was cleared.
                        if (lastSuccessInstallType == InstallType.SHIZUKU) {
                            packageInstallerStatus = null
                            installFailureMessage = null
                            installStatus = null
                            updateInstallingState(false)
                            return
                        }
                        awaitingPackageInstall = null
                        packageInstallerStatus = pmStatus
                        val expectedPkg = intent.getStringExtra(InstallService.EXTRA_PACKAGE_NAME) ?: packageName
                        if (tryMarkInstallIfPresent(expectedPkg)) return
                        val rawMessage = intent.getStringExtra(InstallService.EXTRA_INSTALL_STATUS_MESSAGE)
                            ?.takeIf { it.isNotBlank() }
                        if (activeInstallType != InstallType.MOUNT &&
                            installerManager.isSignatureMismatch(rawMessage)
                        ) {
                            val plan = installerManager.resolvePlan(
                                InstallerManager.InstallTarget.PATCHER,
                                outputFile,
                                expectedPkg,
                                null
                            )
                            showSignatureMismatchPrompt(expectedPkg, plan)
                            return
                        }
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
                    val targetPackage = intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_PACKAGE_NAME)
                    val statusMessage = intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_STATUS_MESSAGE)

                    statusMessage?.let(logger::trace)

                    val pendingPlan = pendingSignatureMismatchPlan
                    val pendingPackage = pendingSignatureMismatchPackage
                    if (pendingPlan != null && !pendingPackage.isNullOrBlank() && pendingPackage == targetPackage) {
                        signatureMismatchUninstallJob?.cancel()
                        signatureMismatchUninstallJob = null
                        pendingSignatureMismatchPlan = null
                        pendingSignatureMismatchPackage = null
                        stopUninstallProgressToasts()
                        if (pmStatus == PackageInstaller.STATUS_SUCCESS) {
                            val stillPresent = pm.getPackageInfo(targetPackage) != null
                            if (stillPresent) {
                                val failureMessage = app.getString(
                                    R.string.uninstall_app_fail,
                                    app.getString(R.string.uninstall_timeout_message)
                                )
                                handleUninstallFailure(failureMessage)
                                return
                            }
                            viewModelScope.launch {
                                executeInstallPlan(pendingPlan)
                            }
                        } else {
                            val failureMessage = app.getString(
                                R.string.uninstall_app_fail,
                                statusMessage ?: pmStatus.toString()
                            )
                            handleUninstallFailure(failureMessage)
                        }
                        return
                    }

                    if (pmStatus != PackageInstaller.STATUS_SUCCESS) {
                        stopUninstallProgressToasts()
                        val failureMessage = app.getString(
                            R.string.uninstall_app_fail,
                            statusMessage ?: pmStatus.toString()
                        )
                        handleUninstallFailure(failureMessage)
                        return
                    }
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
        patcherWorkerId?.uuid?.let(workManager::cancelWorkById)
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        externalInstallStartTime = null

        if (input.selectedApp is SelectedApp.Installed &&
            installedApp?.installType == InstallType.MOUNT &&
            installerManager.getPrimaryToken() == InstallerManager.Token.AutoSaved
        ) {
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
            updateSplitStepRequirement(null)
        }
    }

    fun onBack() {
        // tempDir cannot be deleted inside onCleared because it gets called on system-initiated process death.
        tempDir.deleteRecursively()
    }

    fun isDeviceRooted() = rootInstaller.isDeviceRooted()

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
            activeInstallType = installType
            updateInstallingState(true)
            installStatus = InstallCompletionStatus.InProgress

            Log.d(TAG, "performInstall(type=$installType, outputExists=${outputFile.exists()}, output=${outputFile.absolutePath})")
            val currentPackageInfo = pm.getPackageInfo(outputFile)
                ?: throw Exception("Failed to load application info")

            // If the app is currently installed
            val existingPackageInfo = pm.getPackageInfo(currentPackageInfo.packageName)
            if (existingPackageInfo != null) {
                // Check if the app version is less than the installed version
                if (pm.getVersionCode(currentPackageInfo) < pm.getVersionCode(existingPackageInfo)) {
                    val hint = installerManager.formatFailureHint(PackageInstaller.STATUS_FAILURE_CONFLICT, null)
                        ?: app.getString(R.string.installer_hint_conflict)
                    showInstallFailure(app.getString(R.string.install_app_fail, hint))
                    return
                }
            }

            when (installType) {
                InstallType.DEFAULT, InstallType.CUSTOM, InstallType.SAVED, InstallType.SHIZUKU -> {
                    // Check if the app is mounted as root
                    // If it is, unmount it first, silently
                    if (rootInstaller.hasRootAccess() && rootInstaller.isAppMounted(packageName)) {
                        rootInstaller.unmount(packageName)
                    }

                    // Install regularly
                    internalInstallBaseline = pm.getPackageInfo(currentPackageInfo.packageName)?.let { info ->
                        pm.getVersionCode(info) to info.lastUpdateTime
                    }
                    awaitingPackageInstall = currentPackageInfo.packageName
                    try {
                        Log.d(TAG, "Starting PackageInstaller session for ${currentPackageInfo.packageName}")
                        pm.installApp(listOf(outputFile))
                        pmInstallStarted = true
                        installStatus = InstallCompletionStatus.InProgress
                        scheduleInstallTimeout(currentPackageInfo.packageName)
                        monitorInternalInstall(currentPackageInfo.packageName)
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
                        val patchedVersion = packageInfo.versionName ?: ""
                        val stockVersion = pm.getPackageInfo(packageName)?.versionName
                        if (stockVersion != null && stockVersion != patchedVersion) {
                            showInstallFailure(
                                app.getString(
                                    R.string.mount_version_mismatch_message,
                                    patchedVersion,
                                    stockVersion
                                )
                            )
                            return
                        }

                        // Check for base APK, first check if the app is already installed
                        if (existingPackageInfo == null) {
                            // If the app is not installed, check if the output file is a base apk
                            if (currentPackageInfo.splitNames.isNotEmpty()) {
                                val hint =
                                    installerManager.formatFailureHint(PackageInstaller.STATUS_FAILURE_INVALID, null)
                                        ?: app.getString(R.string.installer_hint_invalid)
                                showInstallFailure(app.getString(R.string.install_app_fail, hint))
                                return
                            }
                            // If the original input is a split APK, bail out because mount cannot install splits.
                            val inputInfo = inputFile?.let(pm::getPackageInfo)
                            if (inputInfo?.splitNames?.isNotEmpty() == true) {
                                showInstallFailure(app.getString(R.string.mount_split_not_supported))
                                return
                            }
                        }

                        val inputVersion = input.selectedApp.version
                            ?: inputFile?.let(pm::getPackageInfo)?.versionName
                            ?: throw Exception("Failed to determine input APK version")

                        // Only reinstall stock when the app is not currently installed.
                        val stockForMount = if (existingPackageInfo == null) {
                            inputFile ?: run {
                                showInstallFailure(app.getString(R.string.install_app_fail, "Missing original APK for mount install"))
                                return
                            }
                        } else {
                            null
                        }

                        // Install as root
                        rootInstaller.install(
                            outputFile,
                            stockForMount,
                            packageName,
                            inputVersion,
                            label
                        )

                        if (!persistPatchedApp(packageInfo.packageName, InstallType.MOUNT)) {
                            Log.w(TAG, "Failed to persist mounted patched app metadata")
                        }

                        rootInstaller.mount(packageName)

                        installedPackageName = packageName
                        markInstallSuccess(packageName)
                        updateInstallingState(false)
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
        activeInstallType = InstallType.SHIZUKU
        updateInstallingState(true)
        installStatus = InstallCompletionStatus.InProgress
        packageInstallerStatus = null
        try {

            val currentPackageInfo = pm.getPackageInfo(outputFile)
                ?: throw Exception("Failed to load application info")

            val existingPackageInfo = pm.getPackageInfo(currentPackageInfo.packageName)
            if (existingPackageInfo != null) {
                if (pm.getVersionCode(currentPackageInfo) < pm.getVersionCode(existingPackageInfo)) {
                    val hint = installerManager.formatFailureHint(PackageInstaller.STATUS_FAILURE_CONFLICT, null)
                        ?: app.getString(R.string.installer_hint_conflict)
                    showInstallFailure(app.getString(R.string.install_app_fail, hint))
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

            val persisted = persistPatchedApp(currentPackageInfo.packageName, InstallType.SHIZUKU)
            if (!persisted) {
                Log.w(TAG, "Failed to persist installed patched app metadata")
            }

            installedPackageName = currentPackageInfo.packageName
            packageInstallerStatus = null
            installFailureMessage = null
            installStatus = InstallCompletionStatus.Success(currentPackageInfo.packageName)
            awaitingPackageInstall = null
            updateInstallingState(false)
            suppressFailureAfterSuccess = true
            lastSuccessInstallType = InstallType.SHIZUKU
            lastSuccessAtMs = System.currentTimeMillis()
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
            awaitingPackageInstall = null
            if (packageInstallerStatus == PackageInstaller.STATUS_SUCCESS && installStatus !is InstallCompletionStatus.Success) {
                markInstallSuccess(installedPackageName ?: packageName)
            }
            updateInstallingState(false)
        }
    }

    private suspend fun executeInstallPlan(plan: InstallerManager.InstallPlan) {
        Log.d(TAG, "executeInstallPlan(plan=${plan::class.java.simpleName})")
        when (plan) {
            is InstallerManager.InstallPlan.Internal -> {
                pendingExternalInstall?.let(installerManager::cleanup)
                pendingExternalInstall = null
                externalInstallTimeoutJob?.cancel()
                externalInstallTimeoutJob = null
                performInstall(installTypeFor(plan.target))
            }

            is InstallerManager.InstallPlan.Mount -> {
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

    private suspend fun launchExternalInstaller(plan: InstallerManager.InstallPlan.External) {
        pendingExternalInstall?.let { installerManager.cleanup(it) }
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null

        pendingExternalInstall = plan
        externalInstallStartTime = System.currentTimeMillis()
        val baselineInfo = pm.getPackageInfo(plan.expectedPackage)
        externalPackageWasPresentAtStart = baselineInfo != null
        externalInstallBaseline = baselineInfo?.let { info ->
            pm.getVersionCode(info) to info.lastUpdateTime
        }
        baselineInstallSignature = readInstalledSignatureBytes(plan.expectedPackage)
        expectedInstallSignature = readArchiveSignatureBytes(plan.sharedFile)
        internalInstallBaseline = null
        internalInstallMonitorJob?.cancel()
        internalInstallMonitorJob = null
        activeInstallType = InstallType.DEFAULT
        updateInstallingState(true)
        installStatus = InstallCompletionStatus.InProgress
        scheduleInstallTimeout(
            packageName = plan.expectedPackage,
            durationMs = EXTERNAL_INSTALL_TIMEOUT_MS,
            timeoutMessage = { app.getString(R.string.installer_external_timeout, plan.installerLabel) }
        )

        if (isInstallerX(plan) && launchedActivity == null) {
            val activityDeferred = CompletableDeferred<ActivityResult>()
            launchedActivity = activityDeferred
            val launchIntent = Intent(plan.intent).apply { removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            launchActivityChannel.send(launchIntent)
            monitorExternalInstall(plan)
            viewModelScope.launch {
                try {
                    activityDeferred.await()
                    delay(EXTERNAL_INSTALLER_RESULT_GRACE_MS)
                    if (pendingExternalInstall != plan) return@launch
                    val deadline = System.currentTimeMillis() + EXTERNAL_INSTALLER_POST_CLOSE_TIMEOUT_MS
                    while (pendingExternalInstall == plan && System.currentTimeMillis() < deadline) {
                        if (tryMarkInstallIfPresent(plan.expectedPackage)) return@launch
                        delay(INSTALL_MONITOR_POLL_MS)
                    }
                    if (pendingExternalInstall != plan) return@launch
                    showInstallFailure(
                        app.getString(
                            R.string.install_app_fail,
                            app.getString(R.string.installer_external_finished_no_change, plan.installerLabel)
                        )
                    )
                } finally {
                    if (launchedActivity === activityDeferred) launchedActivity = null
                }
            }
            return
        }

        try {
            @Suppress("DEPRECATION")
            ContextCompat.startActivity(app, plan.intent, null)
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

        monitorExternalInstall(plan)
    }

    private fun isInstallerX(plan: InstallerManager.InstallPlan.External): Boolean {
        fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
        val label = normalize(plan.installerLabel)
        val tokenPkg = (plan.token as? InstallerManager.Token.Component)?.componentName?.packageName.orEmpty()
        val componentPkg = plan.intent.component?.packageName.orEmpty()
        val pkg = normalize(if (tokenPkg.isNotBlank()) tokenPkg else componentPkg)
        return "installerx" in label || "installerx" in pkg || pkg.startsWith("comrosaninstaller")
    }

    private fun handleExternalInstallSuccess(packageName: String): Boolean {
        val plan = pendingExternalInstall ?: return false
        if (plan.expectedPackage != packageName) return false

        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        externalInstallBaseline = null
        externalInstallStartTime = null
        externalPackageWasPresentAtStart = false
        expectedInstallSignature = null
        baselineInstallSignature = null
        installerManager.cleanup(plan)
        updateInstallingState(false)
        stopInstallProgressToasts()
        val installType = if (plan.token is InstallerManager.Token.Component) InstallType.CUSTOM else InstallType.DEFAULT
        markInstallSuccess(packageName)
        suppressFailureAfterSuccess = true

        when (plan.target) {
            InstallerManager.InstallTarget.PATCHER -> {
                installedPackageName = packageName
                viewModelScope.launch {
                    val persisted = persistPatchedApp(packageName, installType)
                    if (!persisted) {
                        Log.w(TAG, "Failed to persist installed patched app metadata (external installer)")
                    }
                }
            }

            InstallerManager.InstallTarget.SAVED_APP,
            InstallerManager.InstallTarget.MANAGER_UPDATE -> {
            }
        }
        suppressFailureAfterSuccess = true
        lastSuccessInstallType = installType
        lastSuccessAtMs = System.currentTimeMillis()
        return true
    }

    override fun install() {
        if (isInstalling) return
        viewModelScope.launch {
            runCatching {
                val expectedPackage = pm.getPackageInfo(outputFile)?.packageName ?: packageName
                Log.d(TAG, "install() requested, expected=$expectedPackage, outputExists=${outputFile.exists()}")
                val plan = installerManager.resolvePlan(
                    InstallerManager.InstallTarget.PATCHER,
                    outputFile,
                    expectedPackage,
                    packageName,
                )
                Log.d(TAG, "install() resolved plan=${plan::class.java.simpleName}")
                if (plan !is InstallerManager.InstallPlan.Mount &&
                    hasSignatureMismatch(expectedPackage, outputFile)
                ) {
                    showSignatureMismatchPrompt(expectedPackage, plan)
                    return@runCatching
                }
                executeInstallPlan(plan)
            }.onFailure { error ->
                Log.e(TAG, "install() failed to start", error)
                showInstallFailure(
                    app.getString(
                        R.string.install_app_fail,
                        error.simpleMessage() ?: error.javaClass.simpleName.orEmpty()
                    )
                )
            }
        }
    }

    override fun reinstall() {
        if (isInstalling) return
        viewModelScope.launch {
            val expectedPackage = pm.getPackageInfo(outputFile)?.packageName ?: packageName
            val plan = installerManager.resolvePlan(
                InstallerManager.InstallTarget.PATCHER,
                outputFile,
                expectedPackage,
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
                is InstallerManager.InstallPlan.Mount -> {
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

    fun dismissSignatureMismatchPrompt() {
        signatureMismatchPackage = null
        pendingSignatureMismatchPlan = null
        pendingSignatureMismatchPackage = null
        signatureMismatchUninstallJob?.cancel()
        signatureMismatchUninstallJob = null
    }

    fun confirmSignatureMismatchInstall() {
        val targetPackage = pendingSignatureMismatchPackage ?: return
        signatureMismatchPackage = null
        stopInstallProgressToasts()
        startUninstallProgressToasts()
        startSignatureMismatchUninstallTimeout(targetPackage)
        pm.uninstallPackage(targetPackage)
    }

    fun shouldSuppressPackageInstallerDialog(): Boolean {
        if (activeInstallType == InstallType.SHIZUKU) return true
        val lastType = lastSuccessInstallType
        if (lastType != InstallType.SHIZUKU) return false
        val now = System.currentTimeMillis()
        return now - lastSuccessAtMs < SUPPRESS_FAILURE_AFTER_SUCCESS_MS
    }

    fun dismissInstallFailureMessage() {
        installFailureMessage = null
        packageInstallerStatus = null
        awaitingPackageInstall = null
        installStatus = null
    }

    fun shouldSuppressInstallFailureDialog(): Boolean {
        if (activeInstallType == InstallType.SHIZUKU) return true
        val lastType = lastSuccessInstallType
        if (lastType != InstallType.SHIZUKU) return false
        val now = System.currentTimeMillis()
        return now - lastSuccessAtMs < SUPPRESS_FAILURE_AFTER_SUCCESS_MS
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

        // Determine which patches and options to use based on mode
        val useExpertMode = prefs.useExpertMode.getBlocking()

        val mergedOptions = if (useExpertMode) {
            // Expert mode: Use options from input
            input.options
        } else {
            // Simple mode: Use options from preferences manager
            runBlocking {
                when (packageName) {
                    PACKAGE_YOUTUBE_MUSIC -> patchOptionsPrefs.exportYouTubeMusicPatchOptions()
                    else -> patchOptionsPrefs.exportYouTubePatchOptions()
                }
            }
        }

        return PatcherWorker.Args(
            selectedForRun,
            outputFile.path,
            input.selectedPatches,
            // input.options,
            mergedOptions,
            logger,
            onDownloadProgress = {
                withContext(Dispatchers.Main) {
                    downloadProgress = it
                }
            },
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

        // Missing patch issues are handled during preflight validation.
    }

    fun dismissMemoryAdjustmentDialog() {
        memoryAdjustmentDialog = null
    }

    fun retryAfterMemoryAdjustment() {
        viewModelScope.launch {
            memoryAdjustmentDialog = null
            handledFailureIds.clear()
            resetStateForRetry()
            patcherWorkerId?.uuid?.let(workManager::cancelWorkById)
            val newId = launchWorker()
            patcherWorkerId = ParcelUuid(newId)
            observeWorker(newId)
        }
    }

    private fun resetStateForRetry() {
        completedPatchCount = 0
        downloadProgress = null
        val newSteps = generateSteps(app, input.selectedApp, requiresSplitPreparation).toMutableStateList()
        steps.clear()
        steps.addAll(newSteps)
        currentStepIndex = newSteps.indexOfFirst { it.state == State.RUNNING }.takeIf { it >= 0 } ?: 0
        _patcherSucceeded.value = null
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

    private companion object {
        private const val TAG = "Morphe Patcher"
        private const val SYSTEM_INSTALL_TIMEOUT_MS = 60_000L
        private const val EXTERNAL_INSTALL_TIMEOUT_MS = 60_000L
        private const val POST_TIMEOUT_GRACE_MS = 5_000L
        private const val EXTERNAL_INSTALLER_RESULT_GRACE_MS = 1500L
        private const val EXTERNAL_INSTALLER_POST_CLOSE_TIMEOUT_MS = 30_000L
        private const val INSTALL_MONITOR_POLL_MS = 500L
        private const val INSTALL_PROGRESS_TOAST_INTERVAL_MS = 2500L
        private const val SIGNATURE_MISMATCH_UNINSTALL_TIMEOUT_MS = 30_000L
        private const val SIGNATURE_MISMATCH_UNINSTALL_POLL_MS = 750L
        private const val MEMORY_ADJUSTMENT_MB = 200
        private const val SUPPRESS_FAILURE_AFTER_SUCCESS_MS = 5000L

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
    // Morphe
    progressPercentage = 0.1
)
