package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.content.pm.PackageInfo
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.*
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    val primaryInstallerToken: InstallerManager.Token
        get() = installerManager.getPrimaryToken()

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
        val signatures = patchBundleRepository.allBundlesInfoFlow.first().toSignatureMap()
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
        installedAppRepository.addOrUpdate(
            packageName,
            app.originalPackageName,
            app.version,
            newInstallType,
            appliedPatches ?: emptyMap(),
            app.selectionPayload,
            app.patchedAt
        )
        // Refresh app state to update UI
        delay(500) // Small delay to let database update complete
        refreshAppState(app.copy(installType = newInstallType, currentPackageName = packageName))
    }

    fun deleteSavedCopy() = viewModelScope.launch {
        val app = installedApp ?: return@launch
        withContext(Dispatchers.IO) {
            savedApkFile(app)?.delete()
        }
        hasSavedCopy = false
        context.toast(context.getString(R.string.saved_app_copy_removed_toast))
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
            appInfo = installedInfo
        } else {
            isInstalledOnDevice = false
            appInfo = withContext(Dispatchers.IO) {
                savedApkFile(app)?.let(pm::getPackageInfo)
            }
        }

        // Update mounted state
        isMounted = rootInstaller.isAppMounted(app.currentPackageName)
    }

    val exportFormat: StateFlow<String> = prefs.patchedAppExportFormat.flow
        .stateIn(viewModelScope, SharingStarted.Lazily, prefs.patchedAppExportFormat.getBlocking())

    val allowIncompatiblePatches: StateFlow<Boolean> = prefs.disablePatchVersionCompatCheck.flow
        .stateIn(viewModelScope, SharingStarted.Lazily, prefs.disablePatchVersionCompatCheck.getBlocking())

    /**
     * Start repatch flow - Expert Mode or Simple Mode
     */
    fun startRepatch(
        onStartPatch: (String, File, PatchSelection, Options) -> Unit
    ) = viewModelScope.launch {
        val app = installedApp ?: return@launch

        // Check if original APK exists
        val originalApk = originalApkRepository.get(app.originalPackageName)
        if (originalApk == null) {
            context.toast(context.getString(R.string.home_app_info_repatch_no_original_apk))
            return@launch
        }

        // Check if file exists
        val originalFile = File(originalApk.filePath)
        if (!originalFile.exists()) {
            context.toast(context.getString(R.string.home_app_info_repatch_no_original_apk))
            return@launch
        }

        // Get current bundle info to validate against
        val currentBundleInfo = patchBundleRepository.bundleInfoFlow.first()
        val bundlePatches = currentBundleInfo.mapValues { (_, info) ->
            info.patches.associateBy { it.name }
        }

        // Get saved patches and options
        val savedPatches = appliedPatches ?: resolveAppliedSelection(app)
        val savedOptions = patchOptionsRepository.getOptions(
            app.originalPackageName,
            bundlePatches
        )

        // Validate and filter patches - only keep patches that exist in current bundles
        val validatedPatches = validatePatchSelection(savedPatches, bundlePatches)

        // Validate and filter options - only keep options for patches that exist
        val validatedOptions = validatePatchOptions(savedOptions, bundlePatches)

        // Log validation results and update database if needed
        val removedPatchCount = savedPatches.values.sumOf { it.size } - validatedPatches.values.sumOf { it.size }
        if (removedPatchCount > 0) {
            Log.w(tag, "Removed $removedPatchCount invalid patches from selection during repatch")

            // Save validated data back to database to clean up invalid records
            withContext(Dispatchers.IO) {
                // Update installed app with validated patches
                installedAppRepository.addOrUpdate(
                    app.currentPackageName,
                    app.originalPackageName,
                    app.version,
                    app.installType,
                    validatedPatches,
                    app.selectionPayload,
                    app.patchedAt
                )

                // Update options with validated options
                patchOptionsRepository.saveOptions(app.originalPackageName, validatedOptions)

                Log.i(tag, "Cleaned up invalid patches and options in database")
            }
        }

        // Check if Expert Mode is enabled
        val useExpertMode = prefs.useExpertMode.getBlocking()

        if (useExpertMode) {
            // Expert Mode: Show dialog for patch selection
            // Load all available bundles
            val allBundles = patchBundleRepository
                .scopedBundleInfoFlow(app.originalPackageName, originalApk.version)
                .first()

            // Filter to show only bundles that were used during patching
            val usedBundleUids = validatedPatches.keys
            repatchBundles = allBundles.filter { bundle ->
                bundle.uid in usedBundleUids
            }

            repatchPatches = validatedPatches.toMutableMap()
            repatchOptions = validatedOptions.toMutableMap()
            showRepatchDialog = true
        } else {
            // Simple Mode: Start patching immediately with original APK file
            originalApkRepository.markUsed(app.originalPackageName)
            onStartPatch(app.originalPackageName, originalFile, validatedPatches, validatedOptions)
        }
    }

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
        val currentPatches = repatchPatches.toMutableMap()
        val bundlePatches = currentPatches[bundleUid]?.toMutableSet() ?: return

        if (patchName in bundlePatches) {
            bundlePatches.remove(patchName)
        } else {
            bundlePatches.add(patchName)
        }

        if (bundlePatches.isEmpty()) {
            currentPatches.remove(bundleUid)
        } else {
            currentPatches[bundleUid] = bundlePatches
        }

        repatchPatches = currentPatches
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
        val currentOptions = repatchOptions.toMutableMap()
        val bundleOptions = currentOptions[bundleUid]?.toMutableMap() ?: mutableMapOf()
        val patchOptions = bundleOptions[patchName]?.toMutableMap() ?: mutableMapOf()

        if (value == null) {
            patchOptions.remove(optionKey)
        } else {
            patchOptions[optionKey] = value
        }

        if (patchOptions.isEmpty()) {
            bundleOptions.remove(patchName)
        } else {
            bundleOptions[patchName] = patchOptions
        }

        if (bundleOptions.isEmpty()) {
            currentOptions.remove(bundleUid)
        } else {
            currentOptions[bundleUid] = bundleOptions
        }

        repatchOptions = currentOptions
    }

    /**
     * Reset options for patch in repatch dialog
     */
    fun resetRepatchOptions(bundleUid: Int, patchName: String) {
        val currentOptions = repatchOptions.toMutableMap()
        val bundleOptions = currentOptions[bundleUid]?.toMutableMap() ?: return

        bundleOptions.remove(patchName)

        if (bundleOptions.isEmpty()) {
            currentOptions.remove(bundleUid)
        } else {
            currentOptions[bundleUid] = bundleOptions
        }

        repatchOptions = currentOptions
    }

    /**
     * Validate patch selection against current bundle info
     * Removes patches that no longer exist in the current bundles
     */
    private fun validatePatchSelection(
        savedSelection: PatchSelection,
        bundlePatches: Map<Int, Map<String, PatchInfo>>
    ): PatchSelection {
        return savedSelection.mapNotNull { (bundleUid, patchNames) ->
            // Check if bundle still exists
            val currentBundlePatches = bundlePatches[bundleUid] ?: return@mapNotNull null

            // Filter patches that still exist in the current bundle
            val validPatches = patchNames.filter { patchName ->
                currentBundlePatches.containsKey(patchName)
            }.toSet()

            // Only include this bundle if it has valid patches
            if (validPatches.isEmpty()) null else bundleUid to validPatches
        }.toMap()
    }

    /**
     * Validate patch options against current bundle info
     * Removes options for patches that no longer exist or options that are no longer valid
     */
    private fun validatePatchOptions(
        savedOptions: Options,
        bundlePatches: Map<Int, Map<String, PatchInfo>>
    ): Options {
        return savedOptions.mapNotNull { (bundleUid, bundlePatchOptions) ->
            // Check if bundle still exists
            val currentBundlePatches = bundlePatches[bundleUid] ?: return@mapNotNull null

            // Filter options for patches that still exist
            val validOptions = bundlePatchOptions.mapNotNull { (patchName, patchOptions) ->
                // Check if patch still exists
                val patchInfo = currentBundlePatches[patchName] ?: return@mapNotNull null

                // Filter options that are still valid for this patch
                val validPatchOptions = patchOptions.filterKeys { optionKey ->
                    patchInfo.options?.any { it.key == optionKey } == true
                }

                // Only include this patch if it has valid options
                if (validPatchOptions.isEmpty()) null else patchName to validPatchOptions
            }.toMap()

            // Only include this bundle if it has valid options
            if (validOptions.isEmpty()) null else bundleUid to validOptions
        }.toMap()
    }

    override fun onCleared() {
        super.onCleared()
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
