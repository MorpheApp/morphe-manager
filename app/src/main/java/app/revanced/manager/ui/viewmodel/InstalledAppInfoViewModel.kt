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
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.installer.ShizukuInstaller
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.remapAndExtractSelection
import app.revanced.manager.domain.repository.toSignatureMap
import app.revanced.manager.service.InstallService
import app.revanced.manager.service.UninstallService
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.tag
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InstalledAppInfoViewModel(
    packageName: String
) : ViewModel(), KoinComponent {
    enum class MountOperation { UNMOUNTING, MOUNTING }

    private val context: Application by inject()
    private val pm: PM by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    val rootInstaller: RootInstaller by inject()
    private val installerManager: InstallerManager by inject()
    private val shizukuInstaller: ShizukuInstaller by inject()
    private val filesystem: Filesystem by inject()
    private var pendingExternalInstall: InstallerManager.InstallPlan.External? = null
    private var externalInstallTimeoutJob: Job? = null
    private var internalInstallTimeoutJob: Job? = null
    private var externalInstallBaseline: Pair<Long?, Long?>? = null
    private var externalInstallStartTime: Long? = null
    private var installProgressToastJob: Job? = null
    private var pendingInternalInstallPackage: String? = null
    var isInstalling by mutableStateOf(false)
        private set

    lateinit var onBackClick: () -> Unit

    var installedApp: InstalledApp? by mutableStateOf(null)
        private set
    var appInfo: PackageInfo? by mutableStateOf(null)
        private set
    var appliedPatches: PatchSelection? by mutableStateOf(null)
    var isMounted by mutableStateOf(false)
        private set
    var isInstalledOnDevice by mutableStateOf(false)
        private set
    var hasSavedCopy by mutableStateOf(false)
        private set
    var mountOperation: MountOperation? by mutableStateOf(null)
        private set
    var mountWarning: MountWarningState? by mutableStateOf(null)
        private set
    var mountVersionMismatchMessage: String? by mutableStateOf(null)
        private set
    var installResult: InstallResult? by mutableStateOf(null)
        private set

    val primaryInstallerIsMount: Boolean
        get() = installerManager.getPrimaryToken() == InstallerManager.Token.AutoSaved
    val primaryInstallerToken: InstallerManager.Token
        get() = installerManager.getPrimaryToken()

    init {
        viewModelScope.launch {
            val app = installedAppRepository.get(packageName)
            installedApp = app
            if (app != null) {
                isMounted = rootInstaller.isAppMounted(app.currentPackageName)
                refreshAppState(app)
                appliedPatches = resolveAppliedSelection(app)
            }
        }
    }

    fun showMountWarning(action: MountWarningAction, reason: MountWarningReason) {
        mountWarning = MountWarningState(action, reason)
    }

    fun clearMountWarning() {
        mountWarning = null
    }

    fun cancelOngoingInstall() {
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        pendingInternalInstallPackage = null
        externalInstallTimeoutJob?.cancel()
        internalInstallTimeoutJob?.cancel()
        externalInstallBaseline = null
        externalInstallStartTime = null
        stopInstallProgressToasts()
        installResult = null
        isInstalling = false
    }

    fun performMountWarningAction() {
        when (val warning = mountWarning) {
            null -> Unit
            else -> when (warning.reason) {
                MountWarningReason.PRIMARY_IS_MOUNT_FOR_NON_MOUNT_APP -> when (warning.action) {
                    MountWarningAction.INSTALL,
                    MountWarningAction.UPDATE -> installSavedApp()
                    MountWarningAction.UNINSTALL -> {
                        val app = installedApp
                        if (app?.installType == InstallType.MOUNT || isMounted) {
                            mountOrUnmount()
                        } else {
                            uninstallSavedInstallation()
                        }
                    }
                }

                MountWarningReason.PRIMARY_NOT_MOUNT_FOR_MOUNT_APP -> when (warning.action) {
                    MountWarningAction.INSTALL,
                    MountWarningAction.UPDATE -> installSavedApp()
                    MountWarningAction.UNINSTALL -> uninstallSavedInstallation()
                }
            }
        }
        mountWarning = null
    }

    private suspend fun resolveAppliedSelection(app: InstalledApp) = withContext(Dispatchers.IO) {
        val selection = installedAppRepository.getAppliedPatches(app.currentPackageName)
        if (selection.isNotEmpty()) return@withContext selection
        val payload = app.selectionPayload ?: return@withContext emptyMap()
        val sources = patchBundleRepository.sources.first()
        val sourceIds = sources.map { it.uid }.toSet()
        val signatures = patchBundleRepository.bundleInfoFlow.first().toSignatureMap()
        val (remappedPayload, remappedSelection) = payload.remapAndExtractSelection(sources, signatures)
        val persistableSelection = remappedSelection.filterKeys { it in sourceIds }
        if (persistableSelection.isNotEmpty()) {
            installedAppRepository.addOrUpdate(
                app.currentPackageName,
                app.originalPackageName,
                app.version,
                app.installType,
                persistableSelection,
                remappedPayload
            )
        }
        if (remappedSelection.isNotEmpty()) return@withContext remappedSelection

        payload.bundles.associate { bundle ->
            bundle.bundleUid to bundle.patches.filter { it.isNotBlank() }.toSet()
        }.filterValues { it.isNotEmpty() }
    }

    fun launch() {
        val app = installedApp ?: return
        if (app.installType == InstallType.SAVED) {
            context.toast(context.getString(R.string.saved_app_launch_unavailable))
        } else {
            pm.launch(app.currentPackageName)
        }
    }

    fun dismissMountVersionMismatch() {
        mountVersionMismatchMessage = null
    }

    private fun markInstallSuccess(message: String) {
        stopInstallProgressToasts()
        internalInstallTimeoutJob?.cancel()
        installResult = InstallResult.Success(message)
        isInstalling = false
    }

    private suspend fun persistInstallMetadata(
        installType: InstallType,
        versionName: String? = null,
        packageNameOverride: String? = null
    ) {
        val app = installedApp ?: return
        val selection = appliedPatches ?: resolveAppliedSelection(app)
        val selectionPayload = app.selectionPayload
        val targetPackage = packageNameOverride ?: app.currentPackageName
        val resolvedVersion = versionName
            ?: pm.getPackageInfo(targetPackage)?.versionName
            ?: app.version

        installedAppRepository.addOrUpdate(
            currentPackageName = targetPackage,
            originalPackageName = app.originalPackageName,
            version = resolvedVersion,
            installType = installType,
            patchSelection = selection,
            selectionPayload = selectionPayload
        )

        val updatedApp = app.copy(
            version = resolvedVersion,
            installType = installType
        )
        installedApp = updatedApp
        refreshAppState(updatedApp)
    }

    private fun markInstallFailure(message: String) {
        stopInstallProgressToasts()
        internalInstallTimeoutJob?.cancel()
        installResult = InstallResult.Failure(message)
        isInstalling = false
    }

    private fun scheduleInternalInstallTimeout(packageName: String) {
        internalInstallTimeoutJob?.cancel()
        internalInstallTimeoutJob = viewModelScope.launch {
            delay(EXTERNAL_INSTALL_TIMEOUT_MS)
            if (pendingInternalInstallPackage == packageName) {
                pendingInternalInstallPackage = null
                markInstallFailure(context.getString(R.string.install_timeout_message))
            }
        }
    }

    private fun startInstallProgressToasts() {
        if (installProgressToastJob?.isActive == true) return
        isInstalling = true
        installProgressToastJob = viewModelScope.launch {
            while (isActive) {
                context.toast(context.getString(R.string.installing_ellipsis))
                delay(INSTALL_PROGRESS_TOAST_INTERVAL_MS)
            }
        }
    }

    private fun stopInstallProgressToasts() {
        installProgressToastJob?.cancel()
        installProgressToastJob = null
        internalInstallTimeoutJob?.cancel()
        if (pendingExternalInstall == null && pendingInternalInstallPackage == null) {
            isInstalling = false
        }
    }

    fun installSavedApp() = viewModelScope.launch {
        val app = installedApp ?: return@launch

        val apk = savedApkFile(app)
        if (apk == null) {
            markInstallFailure(context.getString(R.string.saved_app_install_missing))
            return@launch
        }

        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallBaseline = null
        externalInstallStartTime = null
        startInstallProgressToasts()
        isInstalling = true
        val plan = installerManager.resolvePlan(
            InstallerManager.InstallTarget.SAVED_APP,
            apk,
            app.currentPackageName,
            appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
        )
        if (plan is InstallerManager.InstallPlan.External) {
            runCatching { apk.copyTo(plan.sharedFile, overwrite = true) }
        }
        when (plan) {
            is InstallerManager.InstallPlan.Internal -> {
                pendingInternalInstallPackage = app.currentPackageName
                val success = runCatching {
                    pm.installApp(listOf(apk))
                }.onFailure {
                    Log.e(tag, "Failed to install saved app", it)
                }.isSuccess

                if (!success) {
                    pendingInternalInstallPackage = null
                    internalInstallTimeoutJob?.cancel()
                    markInstallFailure(context.getString(R.string.saved_app_install_failed))
                } else {
                    scheduleInternalInstallTimeout(app.currentPackageName)
                }
            }

            is InstallerManager.InstallPlan.Mount -> {
                try {
                    val packageInfo = pm.getPackageInfo(apk)
                        ?: throw Exception("Failed to load application info")
                    val versionName = packageInfo.versionName ?: ""
                    val label = with(pm) { packageInfo.label() }
                    val stockVersion = pm.getPackageInfo(app.originalPackageName)?.versionName
                    if (stockVersion != null && stockVersion != versionName) {
                        mountVersionMismatchMessage = context.getString(
                            R.string.mount_version_mismatch_message,
                            versionName,
                            stockVersion
                        )
                        return@launch
                    }

                    rootInstaller.install(
                        patchedAPK = apk,
                        stockAPK = null,
                        packageName = packageInfo.packageName,
                        version = versionName,
                        label = label
                    )
                    rootInstaller.mount(packageInfo.packageName)

                    val refreshedVersion = packageInfo.versionName ?: app.version
                    persistInstallMetadata(InstallType.MOUNT, refreshedVersion, packageInfo.packageName)
                    isMounted = rootInstaller.isAppMounted(app.currentPackageName)
                    markInstallSuccess(context.getString(R.string.saved_app_install_success))
                } catch (e: Exception) {
                    Log.e(tag, "Failed to install saved app with root", e)
                    markInstallFailure(context.getString(R.string.saved_app_install_failed))
                }
            }

            is InstallerManager.InstallPlan.Shizuku -> {
                try {
                    shizukuInstaller.install(apk, app.currentPackageName)
                    val selection = appliedPatches ?: resolveAppliedSelection(app)
                    withContext(Dispatchers.IO) {
                        val payload = app.selectionPayload
                        installedAppRepository.addOrUpdate(
                            app.currentPackageName,
                            app.originalPackageName,
                            app.version,
                            InstallType.SHIZUKU,
                            selection,
                            payload
                        )
                    }
                    persistInstallMetadata(InstallType.SHIZUKU, app.version)
                    isMounted = false
                    markInstallSuccess(context.getString(R.string.saved_app_install_success))
                } catch (error: ShizukuInstaller.InstallerOperationException) {
                    val message = error.message ?: context.getString(R.string.installer_hint_generic)
                    Log.e(tag, "Failed to install saved app with Shizuku", error)
                    markInstallFailure(context.getString(R.string.install_app_fail, message))
                } catch (error: Exception) {
                    Log.e(tag, "Failed to install saved app with Shizuku", error)
                    markInstallFailure(context.getString(R.string.install_app_fail, error.simpleMessage().orEmpty()))
                }
            }

            is InstallerManager.InstallPlan.External -> launchExternalInstaller(plan)
        }
    }

    private fun launchExternalInstaller(plan: InstallerManager.InstallPlan.External) {
        pendingExternalInstall?.let(installerManager::cleanup)
        externalInstallTimeoutJob?.cancel()
        internalInstallTimeoutJob?.cancel()

        pendingExternalInstall = plan
        externalInstallStartTime = System.currentTimeMillis()
        externalInstallBaseline = pm.getPackageInfo(plan.expectedPackage)?.let { info ->
            pm.getVersionCode(info) to info.lastUpdateTime
        }
        // Ensure the staged APK still exists; if not, fail fast.
        if (!plan.sharedFile.exists()) {
            installerManager.cleanup(plan)
            pendingExternalInstall = null
            markInstallFailure(context.getString(R.string.install_app_fail, context.getString(R.string.saved_app_install_missing)))
            return
        }
        startInstallProgressToasts()
        try {
            ContextCompat.startActivity(context, plan.intent, null)
            context.toast(context.getString(R.string.installer_external_launched, plan.installerLabel))
        } catch (error: ActivityNotFoundException) {
            installerManager.cleanup(plan)
            pendingExternalInstall = null
            externalInstallTimeoutJob = null
            externalInstallBaseline = null
            internalInstallTimeoutJob = null
            externalInstallStartTime = null
            markInstallFailure(context.getString(R.string.install_app_fail, error.simpleMessage()))
            return
        }

        monitorExternalInstall(plan)
    }

    private fun monitorExternalInstall(plan: InstallerManager.InstallPlan.External) {
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = viewModelScope.launch {
            val timeoutAt = System.currentTimeMillis() + EXTERNAL_INSTALL_TIMEOUT_MS
            var presentSeen = false
            while (isActive) {
                if (pendingExternalInstall != plan) return@launch

                val info = pm.getPackageInfo(plan.expectedPackage)
                if (info != null) {
                    presentSeen = true
                    val baseline = externalInstallBaseline
                    val updatedSinceStart = isUpdatedSinceBaseline(
                        info,
                        baseline,
                        externalInstallStartTime
                    )
                    val baselineMissing = baseline == null
                    if (updatedSinceStart || baselineMissing) {
                        handleExternalInstallSuccess(plan.expectedPackage)
                        return@launch
                    }
                }

                val remaining = timeoutAt - System.currentTimeMillis()
                if (remaining <= 0L) break
                delay(INSTALL_MONITOR_POLL_MS)
            }

            if (pendingExternalInstall == plan) {
                val baseline = externalInstallBaseline
                val startTime = externalInstallStartTime
                val info = pm.getPackageInfo(plan.expectedPackage)
                val updatedSinceStart = info?.let {
                    isUpdatedSinceBaseline(it, baseline, startTime)
                } ?: false

                installerManager.cleanup(plan)
                pendingExternalInstall = null
                externalInstallBaseline = null
                externalInstallStartTime = null
                internalInstallTimeoutJob = null

                if (info != null && (baseline == null || updatedSinceStart)) {
                    handleExternalInstallSuccess(plan.expectedPackage)
                } else if (presentSeen && updatedSinceStart) {
                    handleExternalInstallSuccess(plan.expectedPackage)
                } else {
                    markInstallFailure(context.getString(R.string.installer_external_timeout, plan.installerLabel))
                }
                externalInstallTimeoutJob = null
            }
        }
    }

    private fun handleExternalInstallSuccess(packageName: String) {
        val plan = pendingExternalInstall ?: return
        if (plan.expectedPackage != packageName) return

        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        internalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        externalInstallBaseline = null
        externalInstallStartTime = null
        installerManager.cleanup(plan)

        when (plan.target) {
            InstallerManager.InstallTarget.SAVED_APP -> {
                val app = installedApp ?: return
                val installType = if (plan.token is InstallerManager.Token.Component) InstallType.CUSTOM else InstallType.DEFAULT
                viewModelScope.launch {
                    persistInstallMetadata(installType)
                    markInstallSuccess(context.getString(R.string.installer_external_success, plan.installerLabel))
                }
            }

            else -> Unit
        }
        isInstalling = false
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

    fun uninstallSavedInstallation() = viewModelScope.launch {
        val app = installedApp ?: return@launch
        if (!isInstalledOnDevice) return@launch
        pm.uninstallPackage(app.currentPackageName)
    }

    fun remountSavedInstallation() = viewModelScope.launch {
        val pkgName = installedApp?.currentPackageName ?: return@launch
        val app = installedApp ?: return@launch
        val stockVersion = pm.getPackageInfo(app.originalPackageName)?.versionName
        if (stockVersion != null && stockVersion != app.version) {
            mountVersionMismatchMessage = context.getString(
                R.string.mount_version_mismatch_message,
                app.version,
                stockVersion
            )
            return@launch
        }
        // Reflect state immediately while the remount sequence runs.
        mountOperation = MountOperation.UNMOUNTING
        isMounted = false
        try {
            context.toast(context.getString(R.string.unmounting))
            rootInstaller.unmount(pkgName)
            context.toast(context.getString(R.string.unmounted))
            mountOperation = MountOperation.MOUNTING
            context.toast(context.getString(R.string.mounting_ellipsis))
            rootInstaller.mount(pkgName)
            isMounted = rootInstaller.isAppMounted(pkgName)
            context.toast(context.getString(R.string.mounted))
        } catch (e: Exception) {
            context.toast(context.getString(R.string.failed_to_mount, e.simpleMessage()))
            Log.e(tag, "Failed to remount", e)
        } finally {
            if (mountOperation == MountOperation.UNMOUNTING) {
                isMounted = false
            }
            if (mountOperation == MountOperation.MOUNTING) {
                isMounted = rootInstaller.isAppMounted(pkgName)
            }
            mountOperation = null
        }
    }

    fun unmountSavedInstallation() = viewModelScope.launch {
        val pkgName = installedApp?.currentPackageName ?: return@launch
        try {
            context.toast(context.getString(R.string.unmounting))
            rootInstaller.unmount(pkgName)
            isMounted = false
            context.toast(context.getString(R.string.unmounted))
        } catch (e: Exception) {
            context.toast(context.getString(R.string.failed_to_unmount, e.simpleMessage()))
            Log.e(tag, "Failed to unmount", e)
        }
    }

    fun mountOrUnmount() = viewModelScope.launch {
        val pkgName = installedApp?.currentPackageName ?: return@launch
        val app = installedApp ?: return@launch
        try {
            if (isMounted) {
                mountOperation = MountOperation.UNMOUNTING
                context.toast(context.getString(R.string.unmounting))
                rootInstaller.unmount(pkgName)
                isMounted = false
                context.toast(context.getString(R.string.unmounted))
            } else {
                val stockVersion = pm.getPackageInfo(app.originalPackageName)?.versionName
                if (stockVersion != null && stockVersion != app.version) {
                    mountVersionMismatchMessage = context.getString(
                        R.string.mount_version_mismatch_message,
                        app.version,
                        stockVersion
                    )
                    return@launch
                }
                mountOperation = MountOperation.MOUNTING
                context.toast(context.getString(R.string.mounting_ellipsis))
                rootInstaller.mount(pkgName)
                isMounted = rootInstaller.isAppMounted(pkgName)
                context.toast(context.getString(R.string.mounted))
            }
        } catch (e: Exception) {
            if (isMounted) {
                context.toast(context.getString(R.string.failed_to_unmount, e.simpleMessage()))
                Log.e(tag, "Failed to unmount", e)
            } else {
                context.toast(context.getString(R.string.failed_to_mount, e.simpleMessage()))
                Log.e(tag, "Failed to mount", e)
            }
        } finally {
            mountOperation = null
        }
    }

    fun uninstall() {
        val app = installedApp ?: return
        when (app.installType) {
            InstallType.DEFAULT, InstallType.CUSTOM -> pm.uninstallPackage(app.currentPackageName)
            InstallType.SHIZUKU -> pm.uninstallPackage(app.currentPackageName)

            InstallType.MOUNT -> viewModelScope.launch {
                rootInstaller.uninstall(app.currentPackageName)
                installedAppRepository.delete(app)
                onBackClick()
            }

            InstallType.SAVED -> uninstallSavedInstallation()
        }
    }

    fun exportSavedApp(uri: Uri?) = viewModelScope.launch {
        if (uri == null) return@launch
        val file = savedApkFile()
        if (file == null) {
            context.toast(context.getString(R.string.saved_app_export_failed))
            return@launch
        }

        val success = runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)
                    ?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Could not open output stream for saved app export")
            }
        }.isSuccess

        context.toast(
            context.getString(
                if (success) R.string.saved_app_export_success else R.string.saved_app_export_failed
            )
        )
    }

    fun removeSavedApp() = viewModelScope.launch {
        val app = installedApp ?: return@launch
        if (app.installType != InstallType.SAVED) return@launch
        clearSavedData(app, deleteRecord = true)
        installedApp = null
        appInfo = null
        appliedPatches = null
        isInstalledOnDevice = false
        context.toast(context.getString(R.string.saved_app_removed_toast))
        onBackClick()
    }

    fun deleteSavedEntry() = viewModelScope.launch {
        val app = installedApp ?: return@launch
        clearSavedData(app, deleteRecord = true)
        installedApp = null
        appInfo = null
        appliedPatches = null
        isInstalledOnDevice = false
        context.toast(context.getString(R.string.saved_app_removed_toast))
        onBackClick()
    }

    fun deleteSavedCopy() = viewModelScope.launch {
        val app = installedApp ?: return@launch
        clearSavedData(app, deleteRecord = false)
        context.toast(context.getString(R.string.saved_app_copy_removed_toast))
    }

    private suspend fun clearSavedData(app: InstalledApp, deleteRecord: Boolean) {
        if (deleteRecord) {
            installedAppRepository.delete(app)
        }
        withContext(Dispatchers.IO) {
            savedApkFile(app)?.delete()
        }
        hasSavedCopy = false
    }

    private fun savedApkFile(app: InstalledApp? = this.installedApp): File? {
        val target = app ?: return null
        val candidates = listOf(
            filesystem.getPatchedAppFile(target.currentPackageName, target.version),
            filesystem.getPatchedAppFile(target.originalPackageName, target.version)
        ).distinct()
        return candidates.firstOrNull { it.exists() }
    }

    private suspend fun refreshAppState(app: InstalledApp) {
        val installedInfo = withContext(Dispatchers.IO) {
            pm.getPackageInfo(app.currentPackageName)
        }
        hasSavedCopy = withContext(Dispatchers.IO) { savedApkFile(app) != null }

        if (installedInfo != null) {
            isInstalledOnDevice = true
            appInfo = installedInfo
        } else {
            isInstalledOnDevice = false
            appInfo = withContext(Dispatchers.IO) {
                savedApkFile(app)?.let(pm::getPackageInfo)
            }
        }
    }

    private val installBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    val currentApp = installedApp ?: return
                    if (pkg != currentApp.currentPackageName) return

                    if (pendingInternalInstallPackage == pkg) {
                        pendingInternalInstallPackage = null
                        internalInstallTimeoutJob?.cancel()
                        viewModelScope.launch {
                            persistInstallMetadata(InstallType.DEFAULT)
                            markInstallSuccess(this@InstalledAppInfoViewModel.context.getString(R.string.saved_app_install_success))
                        }
                        return
                    }

                    if (pendingExternalInstall != null) {
                        handleExternalInstallSuccess(pkg)
                    } else {
                        viewModelScope.launch { refreshAppState(currentApp) }
                    }
                }

                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true) return
                    val pkg = intent?.data?.schemeSpecificPart ?: return
                    val currentApp = installedApp ?: return
                    if (pkg != currentApp.currentPackageName) return
                    viewModelScope.launch {
                        refreshAppState(currentApp)
                        isMounted = false
                    }
                }

                InstallService.APP_INSTALL_ACTION -> {
                    val pkg = intent.getStringExtra(InstallService.EXTRA_PACKAGE_NAME) ?: return
                    val status = intent.getIntExtra(
                        InstallService.EXTRA_INSTALL_STATUS,
                        PackageInstaller.STATUS_FAILURE
                    )
                    val statusMessage = intent.getStringExtra(InstallService.EXTRA_INSTALL_STATUS_MESSAGE)
                    val currentApp = installedApp ?: return
                    if (pkg != currentApp.currentPackageName) return

                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            viewModelScope.launch {
                                persistInstallMetadata(InstallType.DEFAULT)
                                markInstallSuccess(this@InstalledAppInfoViewModel.context.getString(R.string.saved_app_install_success))
                            }
                        }

                        PackageInstaller.STATUS_FAILURE_ABORTED -> Unit

                        else -> {
                            val reason = installerManager.formatFailureHint(status, statusMessage)
                            markInstallFailure(
                                this@InstalledAppInfoViewModel.context.getString(
                                    R.string.install_app_fail,
                                    reason ?: statusMessage ?: status.toString()
                                )
                            )
                        }
                    }
                }
            }
        }
    }.also {
        ContextCompat.registerReceiver(
            context,
            it,
            IntentFilter().apply {
                addAction(InstallService.APP_INSTALL_ACTION)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private val uninstallBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UninstallService.APP_UNINSTALL_ACTION -> {
                    val targetPackage =
                        intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_PACKAGE_NAME)
                            ?: return
                    val extraStatus =
                        intent.getIntExtra(UninstallService.EXTRA_UNINSTALL_STATUS, -999)
                    val extraStatusMessage =
                        intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_STATUS_MESSAGE)
                    val currentApp = installedApp ?: return
                    if (targetPackage != currentApp.currentPackageName) return

                    if (extraStatus == PackageInstaller.STATUS_SUCCESS) {
                        viewModelScope.launch {
                            if (currentApp.installType == InstallType.SAVED) {
                                refreshAppState(currentApp)
                                return@launch
                            }

                            val hasLocalCopy = withContext(Dispatchers.IO) {
                                savedApkFile(currentApp) != null
                            }

                            if (!hasLocalCopy) {
                                installedAppRepository.delete(currentApp)
                                onBackClick()
                                return@launch
                            }

                            val selection = appliedPatches ?: resolveAppliedSelection(currentApp)

                            withContext(Dispatchers.IO) {
                                val sourcesSnapshot = patchBundleRepository.sources.first()
                                val availableIds = sourcesSnapshot.map { it.uid }.toSet()
                                val persistableSelection = selection.filterKeys { it in availableIds }
                                val payload = patchBundleRepository.snapshotSelection(selection)
                                installedAppRepository.addOrUpdate(
                                    currentApp.currentPackageName,
                                    currentApp.originalPackageName,
                                    currentApp.version,
                                    InstallType.SAVED,
                                    persistableSelection,
                                    payload
                                )
                            }

                            val updatedApp = currentApp.copy(installType = InstallType.SAVED)
                            installedApp = updatedApp
                            appliedPatches = selection
                            isMounted = false
                            hasSavedCopy = true
                            refreshAppState(updatedApp)
                        }
                    } else if (extraStatus != PackageInstaller.STATUS_FAILURE_ABORTED) {
                        this@InstalledAppInfoViewModel.context.toast(
                            this@InstalledAppInfoViewModel.context.getString(
                                R.string.uninstall_app_fail,
                                extraStatusMessage
                            )
                        )
                    }
                }
            }
        }
    }.also {
        ContextCompat.registerReceiver(
            context,
            it,
            IntentFilter(UninstallService.APP_UNINSTALL_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(installBroadcastReceiver)
        context.unregisterReceiver(uninstallBroadcastReceiver)
        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        internalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        internalInstallTimeoutJob = null
        externalInstallBaseline = null
        externalInstallStartTime = null
        stopInstallProgressToasts()
    }

    fun clearInstallResult() {
        installResult = null
    }

    companion object {
        private const val EXTERNAL_INSTALL_TIMEOUT_MS = 60_000L
        private const val INSTALL_MONITOR_POLL_MS = 1000L
        private const val INSTALL_PROGRESS_TOAST_INTERVAL_MS = 2500L
    }
}

enum class MountWarningAction {
    INSTALL,
    UPDATE,
    UNINSTALL
}

enum class MountWarningReason {
    PRIMARY_IS_MOUNT_FOR_NON_MOUNT_APP,
    PRIMARY_NOT_MOUNT_FOR_MOUNT_APP
}

data class MountWarningState(
    val action: MountWarningAction,
    val reason: MountWarningReason
)

sealed class InstallResult {
    data class Success(val message: String) : InstallResult()
    data class Failure(val message: String) : InstallResult()
}
