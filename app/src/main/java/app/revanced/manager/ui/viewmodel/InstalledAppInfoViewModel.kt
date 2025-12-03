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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InstalledAppInfoViewModel(
    packageName: String
) : ViewModel(), KoinComponent {
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
        remappedSelection
    }

    fun launch() {
        val app = installedApp ?: return
        if (app.installType == InstallType.SAVED) {
            context.toast(context.getString(R.string.saved_app_launch_unavailable))
        } else {
            pm.launch(app.currentPackageName)
        }
    }

    fun installSavedApp() = viewModelScope.launch {
        val app = installedApp ?: return@launch

        val apk = savedApkFile(app)
        if (apk == null) {
            context.toast(context.getString(R.string.saved_app_install_missing))
            return@launch
        }

        pendingExternalInstall?.let(installerManager::cleanup)
        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()

        context.toast(context.getString(R.string.installing_saved_app))
        val plan = installerManager.resolvePlan(
            InstallerManager.InstallTarget.SAVED_APP,
            apk,
            app.currentPackageName,
            appInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
        )
        when (plan) {
            is InstallerManager.InstallPlan.Internal -> {
                val success = runCatching {
                    pm.installApp(listOf(apk))
                }.onFailure {
                    Log.e(tag, "Failed to install saved app", it)
                }.isSuccess

                if (!success) {
                    context.toast(context.getString(R.string.saved_app_install_failed))
                } else {
                    viewModelScope.launch { refreshAppState(app) }
                    isMounted = false
                }
            }

            is InstallerManager.InstallPlan.Root -> {
                try {
                    val packageInfo = pm.getPackageInfo(apk)
                        ?: throw Exception("Failed to load application info")
                    val versionName = packageInfo.versionName ?: ""
                    val label = with(pm) { packageInfo.label() }

                    rootInstaller.install(
                        patchedAPK = apk,
                        stockAPK = null,
                        packageName = packageInfo.packageName,
                        version = versionName,
                        label = label
                    )
                    rootInstaller.mount(packageInfo.packageName)

                    refreshAppState(app)
                    isMounted = rootInstaller.isAppMounted(app.currentPackageName)
                    context.toast(context.getString(R.string.saved_app_install_success))
                } catch (e: Exception) {
                    Log.e(tag, "Failed to install saved app with root", e)
                    context.toast(context.getString(R.string.saved_app_install_failed))
                }
            }

            is InstallerManager.InstallPlan.Shizuku -> {
                try {
                    shizukuInstaller.install(apk, app.currentPackageName)
                    refreshAppState(app)
                    isMounted = false
                    context.toast(context.getString(R.string.saved_app_install_success))
                } catch (error: ShizukuInstaller.InstallerOperationException) {
                    val message = error.message ?: context.getString(R.string.installer_hint_generic)
                    Log.e(tag, "Failed to install saved app with Shizuku", error)
                    context.toast(context.getString(R.string.install_app_fail, message))
                } catch (error: Exception) {
                    Log.e(tag, "Failed to install saved app with Shizuku", error)
                    context.toast(context.getString(R.string.install_app_fail, error.simpleMessage()))
                }
            }

            is InstallerManager.InstallPlan.External -> launchExternalInstaller(plan)
        }
    }

    private fun launchExternalInstaller(plan: InstallerManager.InstallPlan.External) {
        pendingExternalInstall?.let(installerManager::cleanup)
        externalInstallTimeoutJob?.cancel()

        pendingExternalInstall = plan
        try {
            ContextCompat.startActivity(context, plan.intent, null)
            context.toast(context.getString(R.string.installer_external_launched, plan.installerLabel))
        } catch (error: ActivityNotFoundException) {
            installerManager.cleanup(plan)
            pendingExternalInstall = null
            externalInstallTimeoutJob = null
            context.toast(context.getString(R.string.install_app_fail, error.simpleMessage()))
            return
        }

        externalInstallTimeoutJob = viewModelScope.launch {
            delay(EXTERNAL_INSTALL_TIMEOUT_MS)
            if (pendingExternalInstall == plan) {
                installerManager.cleanup(plan)
                pendingExternalInstall = null
                context.toast(context.getString(R.string.installer_external_timeout, plan.installerLabel))
                externalInstallTimeoutJob = null
            }
        }
    }

    private fun handleExternalInstallSuccess(packageName: String) {
        val plan = pendingExternalInstall ?: return
        if (plan.expectedPackage != packageName) return

        pendingExternalInstall = null
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
        installerManager.cleanup(plan)

        when (plan.target) {
            InstallerManager.InstallTarget.SAVED_APP -> {
                val app = installedApp ?: return
                viewModelScope.launch { refreshAppState(app) }
                context.toast(context.getString(R.string.installer_external_success, plan.installerLabel))
            }

            else -> Unit
        }
    }

    fun uninstallSavedInstallation() = viewModelScope.launch {
        val app = installedApp ?: return@launch
        if (!isInstalledOnDevice) return@launch
        pm.uninstallPackage(app.currentPackageName)
    }

    fun mountOrUnmount() = viewModelScope.launch {
        val pkgName = installedApp?.currentPackageName ?: return@launch
        try {
            if (isMounted)
                rootInstaller.unmount(pkgName)
            else
                rootInstaller.mount(pkgName)
        } catch (e: Exception) {
            if (isMounted) {
                context.toast(context.getString(R.string.failed_to_unmount, e.simpleMessage()))
                Log.e(tag, "Failed to unmount", e)
            } else {
                context.toast(context.getString(R.string.failed_to_mount, e.simpleMessage()))
                Log.e(tag, "Failed to mount", e)
            }
        } finally {
            isMounted = rootInstaller.isAppMounted(pkgName)
        }
    }

    fun uninstall() {
        val app = installedApp ?: return
        when (app.installType) {
            InstallType.DEFAULT -> pm.uninstallPackage(app.currentPackageName)

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
        val file = filesystem.getPatchedAppFile(target.currentPackageName, target.version)
        return if (file.exists()) file else null
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
                            viewModelScope.launch { refreshAppState(currentApp) }
                            this@InstalledAppInfoViewModel.context.toast(
                                this@InstalledAppInfoViewModel.context.getString(
                                    R.string.saved_app_install_success
                                )
                            )
                        }

                        PackageInstaller.STATUS_FAILURE_ABORTED -> Unit

                        else -> {
                            val reason = installerManager.formatFailureHint(status, statusMessage)
                            this@InstalledAppInfoViewModel.context.toast(
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
        externalInstallTimeoutJob?.cancel()
        externalInstallTimeoutJob = null
    }

    companion object {
        private const val EXTERNAL_INSTALL_TIMEOUT_MS = 60_000L
    }
}
