package app.morphe.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.filterGmsCore
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder.encode
import java.util.zip.ZipInputStream

/**
 * Bundle update status for snackbar display
 */
enum class BundleUpdateStatus {
    Updating,    // Update in progress
    Success,     // Update completed successfully
    Error        // Error occurred (including no internet)
}

/**
 * Dialog state for unsupported version warning
 */
data class UnsupportedVersionDialogState(
    val packageName: String,
    val version: String,
    val recommendedVersion: String?,
    val allCompatibleVersions: List<String> = emptyList()
)

/**
 * Dialog state for wrong package warning
 */
data class WrongPackageDialogState(
    val expectedPackage: String,
    val actualPackage: String
)

/**
 * Quick patch parameters
 */
data class QuickPatchParams(
    val selectedApp: SelectedApp,
    val patches: PatchSelection,
    val options: Options
)

/**
 * Saved APK information for display in APK selection dialog
 */
data class SavedApkInfo(
    val fileName: String,
    val filePath: String,
    val version: String
)

/**
 * Combined ViewModel for Home and Dashboard functionality
 * Manages all dialogs, user interactions, APK processing, and bundle management
 */
class HomeViewModel(
    private val app: Application,
    val patchBundleRepository: PatchBundleRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val originalApkRepository: OriginalApkRepository,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val morpheAPI: MorpheAPI,
    private val networkInfo: NetworkInfo,
    val prefs: PreferencesManager,
    private val pm: PM,
    val rootInstaller: RootInstaller,
    private val filesystem: Filesystem
) : ViewModel() {

    val availablePatches =
        patchBundleRepository.bundleInfoFlow.map { it.values.sumOf { bundle -> bundle.patches.size } }
    val bundleUpdateProgress = patchBundleRepository.bundleUpdateProgress
    private val contentResolver: ContentResolver = app.contentResolver

    // App data resolver for getting app info from APK files
    private val appDataResolver by lazy {
        AppDataResolver(app, pm, originalApkRepository, installedAppRepository, filesystem)
    }

    /**
     * Android 11 kills the app process after granting the "install apps" permission
     */
    val android11BugActive get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !pm.canInstallPackages()

    var updatedManagerVersion: String? by mutableStateOf(null)
        private set

    // Dialog visibility states
    var showAndroid11Dialog by mutableStateOf(false)
    var showBundleManagementSheet by mutableStateOf(false)
    var showAddBundleDialog by mutableStateOf(false)
    var bundleToRename by mutableStateOf<PatchBundleSource?>(null)
    var showRenameBundleDialog by mutableStateOf(false)

    // Expert mode state
    var showExpertModeDialog by mutableStateOf(false)
    var expertModeSelectedApp by mutableStateOf<SelectedApp?>(null)
    var expertModeBundles by mutableStateOf<List<PatchBundleInfo.Scoped>>(emptyList())
    var expertModePatches by mutableStateOf<PatchSelection>(emptyMap())
    var expertModeOptions by mutableStateOf<Options>(emptyMap())

    // Bundle file selection
    var selectedBundleUri by mutableStateOf<Uri?>(null)
    var selectedBundlePath by mutableStateOf<String?>(null)

    // APK selection flow dialogs
    var showApkAvailabilityDialog by mutableStateOf(false)
    var showDownloadInstructionsDialog by mutableStateOf(false)
    var showFilePickerPromptDialog by mutableStateOf(false)

    // Error/warning dialogs
    var showUnsupportedVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
    var showWrongPackageDialog by mutableStateOf<WrongPackageDialogState?>(null)

    // Pending data during APK selection
    var pendingPackageName by mutableStateOf<String?>(null)
    var pendingAppName by mutableStateOf<String?>(null)
    var pendingRecommendedVersion by mutableStateOf<String?>(null)
    var pendingCompatibleVersions by mutableStateOf<List<String>>(emptyList())
    var pendingSelectedApp by mutableStateOf<SelectedApp?>(null)
    var resolvedDownloadUrl by mutableStateOf<String?>(null)
    var pendingSavedApkInfo by mutableStateOf<SavedApkInfo?>(null)

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Loading state for installed apps
    var installedAppsLoading by mutableStateOf(true)

    // Bundle data
    var recommendedVersions: Map<String, String> = emptyMap()
        private set
    var compatibleVersions: Map<String, List<String>> = emptyMap()
        private set

    // Track available updates for installed apps
    var appUpdatesAvailable by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    // Track deleted apps
    var appsDeletedStatus by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    // Package info for display
    var youtubePackageInfo by mutableStateOf<PackageInfo?>(null)
        private set
    var youtubeMusicPackageInfo by mutableStateOf<PackageInfo?>(null)
        private set
    var redditPackageInfo by mutableStateOf<PackageInfo?>(null)
        private set

    // Using mount install (set externally)
    var usingMountInstall: Boolean = false

    // Controls the pre-patching installer selection dialog for root-capable devices.
    var showPrePatchInstallerDialog by mutableStateOf(false)

    // Stores the pending arguments while the pre-patching installer dialog is visible.
    private var pendingPatchApp: SelectedApp? = null
    private var pendingPatchAllowIncompatible: Boolean = false

    /**
     * Called when a root-capable device triggers patching. Instead of starting immediately,
     * opens the pre-patching installer dialog so the user can choose Root Mount vs Standard.
     */
    fun requestPrePatchInstallerSelection(
        selectedApp: SelectedApp,
        allowIncompatible: Boolean
    ) {
        pendingPatchApp = selectedApp
        pendingPatchAllowIncompatible = allowIncompatible
        showPrePatchInstallerDialog = true
    }

    /**
     * Called when the user selects an installation method from the pre-patching dialog.
     * Sets [usingMountInstall] and starts patching with the correct patch configuration.
     */
    fun resolvePrePatchInstallerChoice(useMount: Boolean) {
        showPrePatchInstallerDialog = false
        usingMountInstall = useMount

        val selectedApp = pendingPatchApp ?: return
        val allowIncompatible = pendingPatchAllowIncompatible
        pendingPatchApp = null

        viewModelScope.launch {
            startPatchingWithApp(selectedApp, allowIncompatible)
        }
    }

    /**
     * Dismisses the pre-patching installer dialog without starting patching.
     */
    fun dismissPrePatchInstallerDialog() {
        showPrePatchInstallerDialog = false
        pendingPatchApp = null
    }

    // Callback for starting patch
    var onStartQuickPatch: ((QuickPatchParams) -> Unit)? = null

    init {
        viewModelScope.launch {
            checkForManagerUpdates()
        }
    }

    suspend fun checkForManagerUpdates() {
        if (!prefs.managerAutoUpdates.get() || !networkInfo.isConnected()) return

        uiSafe(app, R.string.failed_to_check_updates, "Failed to check for updates") {
            updatedManagerVersion = morpheAPI.getAppUpdate()?.version
        }
    }

    /**
     * Check for bundle updates for installed apps
     */
    suspend fun checkInstalledAppsForUpdates(
        installedApps: List<InstalledApp>,
        currentBundleVersion: String?
    ) = withContext(Dispatchers.IO) {
        if (currentBundleVersion == null) {
            appUpdatesAvailable = emptyMap()
            return@withContext
        }

        val updates = mutableMapOf<String, Boolean>()

        installedApps.forEach { app ->
            // Get stored bundle versions for this app
            val storedVersions = installedAppRepository.getBundleVersionsForApp(app.currentPackageName)

            // Check if any bundle used for this app has been updated
            val hasUpdate = storedVersions.any { (bundleUid, storedVersion) ->
                // Only check default bundle (UID 0) for main apps
                if (bundleUid == DEFAULT_SOURCE_UID) {
                    // Compare stored version with current version
                    isNewerVersion(storedVersion, currentBundleVersion)
                } else {
                    false // For now, only track default bundle updates
                }
            }

            updates[app.currentPackageName] = hasUpdate
        }

        appUpdatesAvailable = updates
    }

    @SuppressLint("ShowToast")
    private suspend fun <T> withPersistentImportToast(block: suspend () -> T): T = coroutineScope {
        val progressToast = withContext(Dispatchers.Main) {
            Toast.makeText(
                app,
                app.getString(R.string.importing_ellipsis),
                Toast.LENGTH_SHORT
            )
        }
        withContext(Dispatchers.Main) { progressToast.show() }

        val toastRepeater = launch(Dispatchers.Main) {
            try {
                while (isActive) {
                    delay(1_750)
                    progressToast.show()
                }
            } catch (_: CancellationException) {
                // Ignore cancellation.
            }
        }

        try {
            block()
        } finally {
            toastRepeater.cancel()
            withContext(Dispatchers.Main) { progressToast.cancel() }
        }
    }

    @SuppressLint("Recycle")
    fun createLocalSource(patchBundle: Uri) = viewModelScope.launch {
        withContext(NonCancellable) {
            withPersistentImportToast {
                val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                var persistedPermission = false
                val size = runCatching {
                    contentResolver.openFileDescriptor(patchBundle, "r")
                        ?.use { it.statSize.takeIf { sz -> sz > 0 } }
                        ?: contentResolver.query(
                            patchBundle,
                            arrayOf(OpenableColumns.SIZE),
                            null,
                            null,
                            null
                        )
                            ?.use { cursor ->
                                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (index != -1 && cursor.moveToFirst()) cursor.getLong(index) else null
                            }
                }.getOrNull()?.takeIf { it > 0L }
                try {
                    contentResolver.takePersistableUriPermission(patchBundle, permissionFlags)
                    persistedPermission = true
                } catch (_: SecurityException) {
                    // Provider may not support persistable permissions; fall back to transient grant.
                }

                try {
                    patchBundleRepository.createLocal(size) {
                        contentResolver.openInputStream(patchBundle)
                            ?: throw FileNotFoundException("Unable to open $patchBundle")
                    }
                } finally {
                    if (persistedPermission) {
                        try {
                            contentResolver.releasePersistableUriPermission(
                                patchBundle,
                                permissionFlags
                            )
                        } catch (_: SecurityException) {
                            // Ignore if provider revoked or already released.
                        }
                    }
                }
            }
        }
    }

    fun createRemoteSource(apiUrl: String, autoUpdate: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            patchBundleRepository.createRemote(apiUrl, autoUpdate)
        }
    }

    suspend fun updateMorpheBundleWithChangelogClear() {
        patchBundleRepository.updateOnlyMorpheBundle(
            force = false,
            showToast = false
        )
        // Clear changelog cache
        val sources = patchBundleRepository.sources.first()
        val apiBundle = sources.firstOrNull() as? RemotePatchBundle
        apiBundle?.clearChangelogCache()
    }

    // Main app packages that use default bundle only
    private val mainAppPackages = setOf(
        AppPackages.YOUTUBE,
        AppPackages.YOUTUBE_MUSIC,
        AppPackages.REDDIT
    )

    /**
     * Update bundle data when sources or bundle info changes
     */
    fun updateBundleData(sources: List<PatchBundleSource>, bundleInfo: Map<Int, Any>) {
        // Get set of enabled bundle UIDs
        val enabledBundleUids = sources.filter { it.enabled }.map { it.uid }.toSet()

        // Extract versions from all enabled bundles
        val versionData = extractCompatibleVersions(bundleInfo, enabledBundleUids)

        recommendedVersions = versionData.mapValues { it.value.firstOrNull() ?: "" }
        compatibleVersions = versionData
    }

    /**
     * Update loading state
     */
    fun updateLoadingState(bundleUpdateInProgress: Boolean, hasInstalledApps: Boolean) {
        installedAppsLoading = bundleUpdateInProgress || !hasInstalledApps
    }

    /**
     * Update deleted apps status
     */
    fun updateDeletedAppsStatus(installedApps: List<InstalledApp>) {
        appsDeletedStatus = installedApps.associate { app ->
            val hasSavedCopy = listOf(
                filesystem.getPatchedAppFile(app.currentPackageName, app.version),
                filesystem.getPatchedAppFile(app.originalPackageName, app.version)
            ).distinctBy { it.absolutePath }.any { it.exists() }

            app.currentPackageName to pm.isAppDeleted(
                packageName = app.currentPackageName,
                hasSavedCopy = hasSavedCopy,
                wasInstalledOnDevice = app.installType != InstallType.SAVED
            )
        }
    }

    /**
     * Update installed apps info
     */
    fun updateInstalledAppsInfo(
        youtubeApp: InstalledApp?,
        youtubeMusicApp: InstalledApp?,
        redditApp: InstalledApp?,
        allInstalledApps: List<InstalledApp>
    ) = viewModelScope.launch(Dispatchers.IO) {
        // Load package info in parallel
        val youtubeJob = async { loadDisplayPackageInfo(youtubeApp) }
        val musicJob = async { loadDisplayPackageInfo(youtubeMusicApp) }
        val redditJob = async { loadDisplayPackageInfo(redditApp) }

        youtubePackageInfo = youtubeJob.await()
        youtubeMusicPackageInfo = musicJob.await()
        redditPackageInfo = redditJob.await()

        // Update deleted status
        updateDeletedAppsStatus(allInstalledApps)
    }

    /**
     * Load package info for display
     */
    private fun loadDisplayPackageInfo(installedApp: InstalledApp?): PackageInfo? {
        installedApp ?: return null

        return pm.getPackageInfo(installedApp.currentPackageName)
            ?: run {
                val candidates = listOf(
                    filesystem.getPatchedAppFile(installedApp.currentPackageName, installedApp.version),
                    filesystem.getPatchedAppFile(installedApp.originalPackageName, installedApp.version)
                ).distinctBy { it.absolutePath }

                candidates.firstOrNull { it.exists() }?.let { file ->
                    pm.getPackageInfo(file)
                }
            }
    }

    /**
     * Handle app button click
     */
    fun handleAppClick(
        packageName: String,
        availablePatches: Int,
        bundleUpdateInProgress: Boolean,
        android11BugActive: Boolean,
        installedApp: InstalledApp?
    ) {
        // If app is installed, allow click even during updates
        if (installedApp != null) {
            return // Caller will handle navigation
        }

        // Check if patches are being fetched
        if (availablePatches <= 0 || bundleUpdateInProgress) {
            app.toast(app.getString(R.string.home_sources_are_loading))
            return
        }

        // Check for Android 11 installation bug
        if (android11BugActive) {
            showAndroid11Dialog = true
            return
        }

        showPatchDialog(packageName)
    }

    /**
     * Show patch dialog
     *
     * Dialog logic:
     * - SHOW dialog when:
     *   1. New app (not installed yet) - shows download button, no saved APK button
     *   2. Expert mode - always show with all options
     *   3. Simple mode + no saved APK - shows download button, no saved APK button
     *   4. Simple mode + saved APK != recommended - shows all options
     *
     * - SKIP dialog and auto-use saved APK when:
     *   - Simple mode + saved APK == recommended version
     */
    fun showPatchDialog(packageName: String) {
        pendingPackageName = packageName
        pendingAppName = AppPackages.getAppName(app, packageName)
        pendingRecommendedVersion = recommendedVersions[packageName]
        pendingCompatibleVersions = compatibleVersions[packageName] ?: emptyList()

        // Load saved APK info if it exists
        viewModelScope.launch {
            val savedInfo = withContext(Dispatchers.IO) {
                loadSavedApkInfo(packageName)
            }
            pendingSavedApkInfo = savedInfo

            // Check if we should auto-use saved APK in simple mode
            val isExpertMode = prefs.useExpertMode.getBlocking()
            val recommendedVersion = recommendedVersions[packageName]

            val shouldAutoUseSaved = !isExpertMode &&
                    savedInfo != null &&
                    recommendedVersion != null &&
                    savedInfo.version == recommendedVersion

            if (shouldAutoUseSaved) {
                // Skip dialog and use saved APK directly
                handleSavedApkSelection()
            } else {
                // Show dialog
                showApkAvailabilityDialog = true
            }
        }
    }

    /**
     * Load information about saved original APK for a package
     */
    private suspend fun loadSavedApkInfo(packageName: String): SavedApkInfo? {
        try {
            val originalApk = originalApkRepository.get(packageName) ?: return null
            val file = File(originalApk.filePath)
            if (!file.exists()) return null

            // Use AppDataResolver to get accurate version from APK file
            val resolvedData = appDataResolver.resolveAppData(
                packageName = packageName,
                preferredSource = AppDataSource.ORIGINAL_APK
            )

            // Use resolved version
            val version = resolvedData.version
                ?: originalApk.version

            return SavedApkInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                version = version
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to load saved APK info", e)
            return null
        }
    }

    /**
     * Handle APK file selection
     */
    fun handleApkSelection(uri: Uri?) {
        if (uri == null) {
            cleanupPendingData()
            return
        }

        viewModelScope.launch {
            val selectedApp = withContext(Dispatchers.IO) {
                loadLocalApk(app, uri)
            }

            if (selectedApp != null) {
                processSelectedApp(selectedApp)
            } else {
                app.toast(app.getString(R.string.home_invalid_apk))
            }
        }
    }

    /**
     * Handle selection of saved APK from APK availability dialog
     */
    fun handleSavedApkSelection() {
        val savedInfo = pendingSavedApkInfo
        val packageName = pendingPackageName

        if (savedInfo == null || packageName == null) {
            app.toast(app.getString(R.string.home_app_info_repatch_no_original_apk))
            cleanupPendingData()
            return
        }

        viewModelScope.launch {
            showApkAvailabilityDialog = false

            // Create SelectedApp from saved APK file
            val selectedApp = withContext(Dispatchers.IO) {
                try {
                    val file = File(savedInfo.filePath)
                    if (!file.exists()) {
                        app.toast(app.getString(R.string.home_app_info_repatch_no_original_apk))
                        return@withContext null
                    }

                    // Mark as used
                    originalApkRepository.markUsed(packageName)

                    SelectedApp.Local(
                        packageName = packageName,
                        version = savedInfo.version,
                        file = file,
                        temporary = false // Don't delete saved APK files
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to load saved APK", e)
                    null
                }
            }

            if (selectedApp != null) {
                processSelectedApp(selectedApp)
            } else {
                cleanupPendingData()
            }
        }
    }

    /**
     * Process selected APK file
     */
    private suspend fun processSelectedApp(selectedApp: SelectedApp) {
        // Validate package name if expected
        if (pendingPackageName != null && selectedApp.packageName != pendingPackageName) {
            showWrongPackageDialog = WrongPackageDialogState(
                expectedPackage = pendingPackageName!!,
                actualPackage = selectedApp.packageName
            )
            if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                selectedApp.file.delete()
            }
            cleanupPendingData()
            return
        }

        val allowIncompatible = prefs.disablePatchVersionCompatCheck.getBlocking()

        // Get available patches
        val bundles = withContext(Dispatchers.IO) {
            patchBundleRepository
                .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
                .first()
        }

        val patches = bundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }
        val totalPatches = patches.values.sumOf { it.size }

        // Check if any patches available
        if (totalPatches == 0) {
            // First check if we have version info for this package
            val recommendedVersion = recommendedVersions[selectedApp.packageName]
            val allVersions = compatibleVersions[selectedApp.packageName] ?: emptyList()

            if (recommendedVersion != null || allVersions.isNotEmpty()) {
                // Patches exist for this package, but not for this version
                pendingSelectedApp = selectedApp
                showUnsupportedVersionDialog = UnsupportedVersionDialogState(
                    packageName = selectedApp.packageName,
                    version = selectedApp.version ?: "unknown",
                    recommendedVersion = recommendedVersion,
                    allCompatibleVersions = allVersions
                )
                cleanupPendingData(keepSelectedApp = true)
                return
            } else {
                // No patches at all for this package
                app.toast(app.getString(R.string.home_no_patches_for_app))
                if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                    selectedApp.file.delete()
                }
                cleanupPendingData()
                return
            }
        }

        // For root-capable devices, we must know the installation method BEFORE patching
        // because it affects which patches are included (GmsCore is excluded for mount install).
        // Show the pre-patching installer dialog so the user can choose.
        // For non-root devices, just proceed - installer selection happens after patching.
        if (rootInstaller.isDeviceRooted()) {
            requestPrePatchInstallerSelection(selectedApp, allowIncompatible)
        } else {
            usingMountInstall = false
            startPatchingWithApp(selectedApp, allowIncompatible)
        }
    }

    /**
     * Start patching flow
     */
    suspend fun startPatchingWithApp(
        selectedApp: SelectedApp,
        allowIncompatible: Boolean
    ) {
        val expertModeEnabled = prefs.useExpertMode.getBlocking()

        val allBundles = patchBundleRepository
            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
            .first()

        if (allBundles.isEmpty()) {
            app.toast(app.getString(R.string.home_no_patches_available))
            cleanupPendingData()
            return
        }

        // Create bundles map for validation
        val bundlesMap = allBundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }

        // Helper function to apply GmsCore filter if needed
        fun PatchSelection.applyGmsCoreFilter(): PatchSelection =
            if (usingMountInstall) this.filterGmsCore() else this

        if (expertModeEnabled) {
            // Expert Mode: Load saved selections and options
            val savedSelections = withContext(Dispatchers.IO) {
                // Try to load from original package name first
                var selections = patchSelectionRepository.getSelection(selectedApp.packageName)

                // If no selections found, try patched package name
                if (selections.isEmpty()) {
                    // Get all installed apps to find patched package name
                    val installedApps = installedAppRepository.getAll().first()
                    val patchedPackage = installedApps
                        .find { it.originalPackageName == selectedApp.packageName }
                        ?.currentPackageName

                    if (patchedPackage != null && patchedPackage != selectedApp.packageName) {
                        selections = patchSelectionRepository.getSelection(patchedPackage)
                    }
                }

                selections
            }

            // Load saved options
            val savedOptions = withContext(Dispatchers.IO) {
                optionsRepository.getOptions(selectedApp.packageName, bundlesMap)
            }

            // Use saved selections or create new ones
            val patches = if (savedSelections.isNotEmpty()) {
                // Count patches before validation
                val patchesBeforeValidation = savedSelections.values.sumOf { it.size }

                // Validate saved selections against available patches
                val validatedPatches = validatePatchSelection(savedSelections, bundlesMap)

                // Count patches after validation
                val patchesAfterValidation = validatedPatches.values.sumOf { it.size }

                // Show toast if patches were removed
                val removedCount = patchesBeforeValidation - patchesAfterValidation
                if (removedCount > 0) {
                    app.toast(app.resources.getQuantityString(
                        R.plurals.home_app_info_repatch_cleaned_invalid_data,
                        removedCount,
                        removedCount
                    ))

                    // Save validated selection
                    withContext(Dispatchers.IO) {
                        patchSelectionRepository.updateSelection(
                            packageName = selectedApp.packageName,
                            selection = validatedPatches
                        )
                    }
                }

                validatedPatches
            } else {
                // No saved selections - use default
                allBundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }
            }.applyGmsCoreFilter()

            // Validate options
            val validatedOptions = validatePatchOptions(savedOptions, bundlesMap)

            // Save validated options if anything changed
            if (validatedOptions != savedOptions) {
                withContext(Dispatchers.IO) {
                    optionsRepository.saveOptions(selectedApp.packageName, validatedOptions)
                }
            }

            expertModeSelectedApp = selectedApp
            expertModeBundles = allBundles
            expertModePatches = patches.toMutableMap()
            expertModeOptions = validatedOptions.toMutableMap()
            showExpertModeDialog = true
        } else {
            // Simple Mode: check if this is a main app or "other app"
            val isMainApp = selectedApp.packageName in mainAppPackages

            if (isMainApp) {
                // For main apps: use only default bundle
                val defaultBundle = allBundles.find { it.uid == DEFAULT_SOURCE_UID }

                if (defaultBundle == null || !defaultBundle.enabled) {
                    app.toast(app.getString(R.string.home_default_source_disabled))
                    cleanupPendingData()
                    return
                }

                // Always use default selection in Simple Mode
                val patchNames = defaultBundle.patchSequence(allowIncompatible)
                    .filter { it.include }
                    .mapTo(mutableSetOf()) { it.name }

                if (patchNames.isEmpty()) {
                    app.toast(app.getString(R.string.home_no_patches_available))
                    cleanupPendingData()
                    return
                }

                val patches = mapOf(defaultBundle.uid to patchNames).applyGmsCoreFilter()

                proceedWithPatching(selectedApp, patches, emptyMap())
            } else {
                // For "Other Apps": search all enabled bundles for patches
                val bundleWithPatches = allBundles
                    .filter { it.enabled }
                    .map { bundle ->
                        val patchNames = bundle.patchSequence(allowIncompatible)
                            .filter { it.include }
                            .mapTo(mutableSetOf()) { it.name }
                        bundle to patchNames
                    }
                    .filter { (_, patches) -> patches.isNotEmpty() }

                if (bundleWithPatches.isEmpty()) {
                    app.toast(app.getString(R.string.home_no_patches_available))
                    cleanupPendingData()
                    return
                }

                // Use all available patches from all bundles
                val patches = bundleWithPatches
                    .associate { (bundle, patches) -> bundle.uid to patches }
                    .applyGmsCoreFilter()

                proceedWithPatching(selectedApp, patches, emptyMap())
            }
        }
    }

    /**
     * Proceed with patching
     */
    fun proceedWithPatching(
        selectedApp: SelectedApp,
        patches: PatchSelection,
        options: Options
    ) {
        onStartQuickPatch?.invoke(
            QuickPatchParams(
                selectedApp = selectedApp,
                patches = patches,
                options = options
            )
        )

        // Clean only UI state
        pendingPackageName = null
        pendingAppName = null
        pendingRecommendedVersion = null
        pendingCompatibleVersions = emptyList()
        resolvedDownloadUrl = null
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
    }

    /**
     * Toggle patch in expert mode.
     * Supports adding patches from bundles not yet in the selection.
     */
    fun togglePatchInExpertMode(bundleUid: Int, patchName: String) {
        expertModePatches = expertModePatches.togglePatch(bundleUid, patchName)
    }

    /**
     * Update option in expert mode
     */
    fun updateOptionInExpertMode(
        bundleUid: Int,
        patchName: String,
        optionKey: String,
        value: Any?
    ) {
        expertModeOptions = expertModeOptions.updateOption(bundleUid, patchName, optionKey, value)
    }

    /**
     * Reset options for a patch in expert mode
     */
    fun resetOptionsInExpertMode(bundleUid: Int, patchName: String) {
        expertModeOptions = expertModeOptions.resetOptionsForPatch(bundleUid, patchName)
    }

    /**
     * Clean up expert mode data
     */
    fun cleanupExpertModeData() {
        showExpertModeDialog = false
        expertModeSelectedApp = null
        expertModeBundles = emptyList()
        expertModePatches = emptyMap()
        expertModeOptions = emptyMap()
    }

    /**
     * Resolve download redirect
     */
    fun resolveDownloadRedirect() {
        fun resolveUrlRedirect(url: String): String {
            return try {
                val originalUrl = URL(url)
                val connection = originalUrl.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000

                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")

                    if (location.isNullOrBlank()) {
                        Log.d(tag, "Location tag is blank: ${connection.responseMessage}")
                        getApiOfflineWebSearchUrl()
                    } else {
                        val resolved =
                            if (location.startsWith("http://") || location.startsWith("https://")) {
                                location
                            } else {
                                val prefix = "${originalUrl.protocol}://${originalUrl.host}"
                                if (location.startsWith("/")) "$prefix$location" else "$prefix/$location"
                            }
                        Log.d(tag, "Result: $resolved")
                        resolved
                    }
                } else {
                    Log.d(tag, "Unexpected response code: $responseCode")
                    getApiOfflineWebSearchUrl()
                }
            } catch (ex: SocketTimeoutException) {
                Log.d(tag, "Timeout while resolving search redirect: $ex")
                url
            } catch (ex: Exception) {
                Log.d(tag, "Exception while resolving search redirect: $ex")
                getApiOfflineWebSearchUrl()
            }
        }

        // Handle null pendingRecommendedVersion
        val escapedVersion = pendingRecommendedVersion?.let { encode(it, "UTF-8") } ?: ""
        val searchQuery = "$pendingPackageName:$escapedVersion:${Build.SUPPORTED_ABIS.first()}"
        val searchUrl = "$MORPHE_API_URL/v2/web-search/$searchQuery"
        Log.d(tag, "Using search url: $searchUrl")

        resolvedDownloadUrl = searchUrl

        viewModelScope.launch(Dispatchers.IO) {
            var resolved = resolveUrlRedirect(searchUrl)

            if (resolved.startsWith(MORPHE_API_URL)) {
                Log.d(tag, "Redirect still on API host, resolving again")
                resolved = resolveUrlRedirect(resolved)
            }

            withContext(Dispatchers.Main) {
                resolvedDownloadUrl = resolved
            }
        }
    }

    fun getApiOfflineWebSearchUrl(): String {
        val architecture = if (pendingPackageName == AppPackages.YOUTUBE_MUSIC) {
            " (${Build.SUPPORTED_ABIS.first()})"
        } else {
            "nodpi"
        }

        // Handle null pendingRecommendedVersion
        val versionPart = pendingRecommendedVersion?.let { "\"$it\"" } ?: ""
        val searchQuery = "\"$pendingPackageName\" $versionPart \"$architecture\" site:APKMirror.com"
        val searchUrl = "https://google.com/search?q=${encode(searchQuery, "UTF-8")}"
        Log.d(tag, "Using search query: $searchQuery")
        return searchUrl
    }

    /**
     * Handle download instructions continue
     */
    fun handleDownloadInstructionsContinue(onOpenUrl: (String) -> Boolean) {
        val urlToOpen = resolvedDownloadUrl!!

        if (onOpenUrl(urlToOpen)) {
            showDownloadInstructionsDialog = false
            showFilePickerPromptDialog = true
        } else {
            Log.d(tag, "Failed to open URL")
            app.toast(app.getString(R.string.sources_management_failed_to_open_url))
            showDownloadInstructionsDialog = false
            cleanupPendingData()
        }
    }

    /**
     * Clean up pending data
     */
    fun cleanupPendingData(keepSelectedApp: Boolean = false) {
        pendingPackageName = null
        pendingAppName = null
        pendingRecommendedVersion = null
        pendingCompatibleVersions = emptyList()
        resolvedDownloadUrl = null
        pendingSavedApkInfo = null
        if (!keepSelectedApp) {
            pendingSelectedApp?.let { app ->
                if (app is SelectedApp.Local && app.temporary) {
                    app.file.delete()
                }
            }
            pendingSelectedApp = null
        }
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
    }

    /**
     * Save options to repository
     */
    fun saveOptions(packageName: String, options: Options) {
        viewModelScope.launch(Dispatchers.IO) {
            optionsRepository.saveOptions(packageName, options)
        }
    }

    /**
     * Extract compatible versions for each package from bundle info
     * Returns a map of package name to sorted list of versions (newest first)
     */
    private fun extractCompatibleVersions(
        bundleInfo: Map<Int, Any>,
        enabledBundleUids: Set<Int> = emptySet()
    ): Map<String, List<String>> {
        // Collect versions from all enabled bundles
        val allVersionsByPackage = mutableMapOf<String, MutableSet<String>>()

        bundleInfo.forEach { (bundleUid, bundleData) ->
            // Skip disabled bundles if we have the enabled list
            if (enabledBundleUids.isNotEmpty() && bundleUid !in enabledBundleUids) {
                return@forEach
            }

            val info = bundleData as? PatchBundleInfo ?: return@forEach

            // Collect all unique package names from all patches in this bundle
            val packagesInBundle = info.patches
                .flatMap { patch ->
                    patch.compatiblePackages?.map { it.packageName } ?: emptyList()
                }
                .distinct()

            // For each package, collect all compatible versions
            packagesInBundle.forEach { packageName ->
                val versions = info.patches
                    .flatMap { patch ->
                        patch.compatiblePackages
                            ?.firstOrNull { it.packageName == packageName }
                            ?.versions
                            ?: emptyList()
                    }
                    .distinct()

                if (versions.isNotEmpty()) {
                    allVersionsByPackage.getOrPut(packageName) { mutableSetOf() }
                        .addAll(versions)
                }
            }
        }

        // Convert to sorted lists (newest first)
        return allVersionsByPackage.mapValues { (_, versions) ->
            versions.toList().sortedDescending()
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * Load local APK and extract package info
     * Supports both single APK and split APK archives (apkm, apks, xapk)
     */
    private suspend fun loadLocalApk(
        context: Context,
        uri: Uri
    ): SelectedApp.Local? = withContext(Dispatchers.IO) {
        try {
            // Copy file to cache with original extension detection
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "temp_${System.currentTimeMillis()}"

            val extension = fileName.substringAfterLast('.', "apk").lowercase()
            val tempFile = File(context.cacheDir, "temp_apk_${System.currentTimeMillis()}.$extension")

            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Check if it's a split APK archive
            val isSplitArchive = SplitApkPreparer.isSplitArchive(tempFile)

            val packageInfo = if (isSplitArchive) {
                // Extract base APK from archive and get package info
                extractPackageInfoFromSplitArchive(context, tempFile)
            } else {
                // Regular APK - parse directly
                context.packageManager.getPackageArchiveInfo(
                    tempFile.absolutePath,
                    PackageManager.GET_META_DATA
                )
            }

            if (packageInfo == null) {
                tempFile.delete()
                return@withContext null
            }

            SelectedApp.Local(
                packageName = packageInfo.packageName,
                version = packageInfo.versionName ?: "unknown",
                file = tempFile,
                temporary = true
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to load APK", e)
            null
        }
    }

    /**
     * Extract package info from split APK archive (apkm, apks, xapk)
     */
    private fun extractPackageInfoFromSplitArchive(
        context: Context,
        archiveFile: File
    ): PackageInfo? {
        return try {
            ZipInputStream(archiveFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    // Look for base APK (usually named base.apk, or the main APK without split suffix)
                    if (name.endsWith(".apk") &&
                        (name.contains("base") || !name.contains("split") && !name.contains("config"))) {

                        // Extract base APK to temp file
                        val tempBaseApk = File(context.cacheDir, "temp_base_${System.currentTimeMillis()}.apk")
                        tempBaseApk.outputStream().use { output ->
                            zip.copyTo(output)
                        }

                        val packageInfo = context.packageManager.getPackageArchiveInfo(
                            tempBaseApk.absolutePath,
                            PackageManager.GET_META_DATA
                        )

                        tempBaseApk.delete()

                        if (packageInfo != null) {
                            return packageInfo
                        }
                    }
                    entry = zip.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to extract package info from split archive", e)
            null
        }
    }
}
