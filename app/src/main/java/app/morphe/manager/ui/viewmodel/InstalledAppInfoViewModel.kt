package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.pm.PackageInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class InstalledAppInfoViewModel(
    packageName: String
) : ViewModel(), KoinComponent {

    private val context: Application by inject()
    private val pm: PM by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    val rootInstaller: RootInstaller by inject()
    private val installerManager: InstallerManager by inject()
    private val originalApkRepository: OriginalApkRepository by inject()
    private val patchOptionsRepository: PatchOptionsRepository by inject()
    private val prefs: PreferencesManager by inject()
    private val filesystem: Filesystem by inject()

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
    var hasOriginalApk by mutableStateOf(false)
        private set
    var isAppDeleted by mutableStateOf(false)
        private set
    var showRepatchDialog by mutableStateOf(false)
        private set
    var repatchBundles by mutableStateOf<List<PatchBundleInfo.Scoped>>(emptyList())
        private set
    var repatchPatches by mutableStateOf<PatchSelection>(emptyMap())
        private set
    var repatchOptions by mutableStateOf<Options>(emptyMap())
        private set
    var isLoading by mutableStateOf(true)
        private set

    val primaryInstallerIsMount: Boolean
        get() = installerManager.getPrimaryToken() == InstallerManager.Token.AutoSaved

    init {
        viewModelScope.launch {
            // Use Flow to automatically update when app data changes in database
            installedAppRepository.getAsFlow(packageName).collect { app ->
                installedApp = app

                if (app != null) {
                    // Run all checks in parallel
                    val deferredMounted = async { rootInstaller.isAppMounted(app.currentPackageName) }
                    val deferredOriginalApk = async { originalApkRepository.get(app.originalPackageName) != null }
                    val deferredAppState = async { refreshAppState(app) }
                    val deferredPatches = async { resolveAppliedSelection(app) }

                    // Wait for all to complete
                    isMounted = deferredMounted.await()
                    hasOriginalApk = deferredOriginalApk.await()
                    deferredAppState.await()
                    appliedPatches = deferredPatches.await()
                }

                // Mark as loaded
                isLoading = false
            }
        }
    }

    suspend fun getStoredBundleVersions(): Map<Int, String?> {
        val app = installedApp ?: return emptyMap()
        return installedAppRepository.getBundleVersionsForApp(app.currentPackageName)
    }

    private suspend fun resolveAppliedSelection(app: InstalledApp) = withContext(Dispatchers.IO) {
        val selection = installedAppRepository.getAppliedPatches(app.currentPackageName)
        if (selection.isNotEmpty()) return@withContext selection
        val payload = app.selectionPayload ?: return@withContext emptyMap()
        val sources = patchBundleRepository.sources.first()
        val sourceIds = sources.map { it.uid }.toSet()
        val (remappedPayload, remappedSelection) = payload.remapAndExtractSelection(sources)
        val persistableSelection = remappedSelection.filterKeys { it in sourceIds }
        if (persistableSelection.isNotEmpty()) {
            installedAppRepository.addOrUpdate(
                app.currentPackageName,
                app.originalPackageName,
                app.version,
                app.installType,
                persistableSelection,
                remappedPayload,
                app.patchedAt
            )
        }
        if (remappedSelection.isNotEmpty()) return@withContext remappedSelection

        // Fallback: convert payload directly to selection
        payload.toPatchSelection()
    }

    fun launch() {
        val app = installedApp ?: return
        if (app.installType == InstallType.SAVED) {
            context.toast(context.getString(R.string.saved_app_launch_unavailable))
        } else {
            pm.launch(app.currentPackageName)
        }
    }

    fun uninstall() {
        val app = installedApp ?: return
        when (app.installType) {
            InstallType.DEFAULT, InstallType.CUSTOM -> pm.uninstallPackage(app.currentPackageName)
            InstallType.SHIZUKU -> pm.uninstallPackage(app.currentPackageName)

            InstallType.MOUNT -> viewModelScope.launch {
                rootInstaller.uninstall(app.currentPackageName)
                // Delete record and APK but preserve selection and options
                deleteRecordAndApk(app)
                onBackClick()
            }

            InstallType.SAVED -> pm.uninstallPackage(app.currentPackageName)
        }
    }

    /**
     * Remove app completely: database record, patched APK and original APK
     * Patch selection and options are preserved for future patching
     */
    fun removeAppCompletely() = viewModelScope.launch {
        val app = installedApp ?: return@launch
        deleteRecordAndApk(app)

        // Also delete original APK if it exists
        withContext(Dispatchers.IO) {
            originalApkRepository.get(app.originalPackageName)?.let { originalApk ->
                originalApkRepository.delete(originalApk)
            }
        }

        installedApp = null
        appInfo = null
        appliedPatches = null
        isInstalledOnDevice = false
        context.toast(context.getString(R.string.saved_app_removed_toast))
        onBackClick()
    }

    /**
     * Delete database record and patched APK file
     * Note: Patch selection and options are NOT deleted - they remain for future patching
     */
    private suspend fun deleteRecordAndApk(app: InstalledApp) {
        // Delete database record
        installedAppRepository.delete(app)

        // Delete patched APK file
        withContext(Dispatchers.IO) {
            savedApkFile(app)?.delete()
        }
        hasSavedCopy = false
    }

    fun updateInstallType(packageName: String, newInstallType: InstallType) = viewModelScope.launch {
        val app = installedApp ?: return@launch
        // Update in database
        withContext(Dispatchers.IO) {
            installedAppRepository.addOrUpdate(
                packageName,
                app.originalPackageName,
                app.version,
                newInstallType,
                appliedPatches ?: emptyMap(),
                app.selectionPayload,
                app.patchedAt
            )
        }
        // Refresh app state to update UI
        refreshAppState(app.copy(installType = newInstallType, currentPackageName = packageName))
    }

    fun savedApkFile(app: InstalledApp? = this.installedApp): File? {
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
            isAppDeleted = false
            appInfo = installedInfo
        } else {
            isInstalledOnDevice = false
            // App is deleted if it was installed on device but now missing
            isAppDeleted = pm.isAppDeleted(
                packageName = app.currentPackageName,
                hasSavedCopy = hasSavedCopy,
                wasInstalledOnDevice = app.installType != InstallType.SAVED
            )
            appInfo = withContext(Dispatchers.IO) {
                savedApkFile(app)?.let(pm::getPackageInfo)
            }
        }

        // Update mounted state
        isMounted = rootInstaller.isAppMounted(app.currentPackageName)
    }

    /**
     * Manually refresh app state (e.g., after app installation/uninstallation)
     */
    fun refreshCurrentAppState() {
        val app = installedApp ?: return
        viewModelScope.launch {
            refreshAppState(app)
        }
    }

    val exportFormat: StateFlow<String> = prefs.patchedAppExportFormat.flow
        .stateIn(viewModelScope, SharingStarted.Lazily, prefs.patchedAppExportFormat.getBlocking())

    val allowIncompatiblePatches: StateFlow<Boolean> = prefs.disablePatchVersionCompatCheck.flow
        .stateIn(viewModelScope, SharingStarted.Lazily, prefs.disablePatchVersionCompatCheck.getBlocking())

    /**
     * Proceed with repatch after Expert Mode dialog
     */
    fun proceedWithRepatch(
        patches: PatchSelection,
        options: Options,
        onStartPatch: (String, File, PatchSelection, Options) -> Unit
    ) = viewModelScope.launch {
        val app = installedApp ?: return@launch

        // Get original APK file
        val originalApk = originalApkRepository.get(app.originalPackageName)
        if (originalApk == null) {
            context.toast(context.getString(R.string.home_app_info_repatch_no_original_apk))
            return@launch
        }

        val originalFile = File(originalApk.filePath)
        if (!originalFile.exists()) {
            context.toast(context.getString(R.string.home_app_info_repatch_no_original_apk))
            return@launch
        }

        // Update last used timestamp
        originalApkRepository.markUsed(app.originalPackageName)

        // Save updated options
        patchOptionsRepository.saveOptions(app.originalPackageName, options)

        // Start patching with original APK file
        onStartPatch(app.originalPackageName, originalFile, patches, options)

        // Close dialog
        showRepatchDialog = false
        cleanupRepatchDialog()
    }

    /**
     * Close repatch dialog
     */
    fun dismissRepatchDialog() {
        showRepatchDialog = false
        cleanupRepatchDialog()
    }

    private fun cleanupRepatchDialog() {
        repatchBundles = emptyList()
        repatchPatches = emptyMap()
        repatchOptions = emptyMap()
    }

    /**
     * Toggle patch in repatch dialog
     */
    fun toggleRepatchPatch(bundleUid: Int, patchName: String) {
        repatchPatches = repatchPatches.togglePatch(bundleUid, patchName)
    }

    /**
     * Update option in repatch dialog
     */
    fun updateRepatchOption(
        bundleUid: Int,
        patchName: String,
        optionKey: String,
        value: Any?
    ) {
        repatchOptions = repatchOptions.updateOption(bundleUid, patchName, optionKey, value)
    }

    /**
     * Reset options for patch in repatch dialog
     */
    fun resetRepatchOptions(bundleUid: Int, patchName: String) {
        repatchOptions = repatchOptions.resetOptionsForPatch(bundleUid, patchName)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
