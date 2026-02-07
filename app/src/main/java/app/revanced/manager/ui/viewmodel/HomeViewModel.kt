package app.revanced.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.platform.NetworkInfo
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.domain.repository.PatchSelectionRepository
import app.revanced.manager.network.api.ReVancedAPI
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.patcher.split.SplitApkPreparer
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.io.FileInputStream
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
 * Combined ViewModel for Home and Dashboard functionality
 * Manages all dialogs, user interactions, APK processing, and bundle management
 */
class HomeViewModel(
    private val app: Application,
    val patchBundleRepository: PatchBundleRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val reVancedAPI: ReVancedAPI,
    private val networkInfo: NetworkInfo,
    val prefs: PreferencesManager,
    private val pm: PM,
    val rootInstaller: RootInstaller,
    private val filesystem: Filesystem
) : ViewModel() {

    val availablePatches =
        patchBundleRepository.bundleInfoFlow.map { it.values.sumOf { bundle -> bundle.patches.size } }
    val bundleUpdateProgress = patchBundleRepository.bundleUpdateProgress
    val bundleImportProgress = patchBundleRepository.bundleImportProgress
    private val contentResolver: ContentResolver = app.contentResolver
    private val powerManager = app.getSystemService<PowerManager>()!!

    /**
     * Android 11 kills the app process after granting the "install apps" permission
     */
    val android11BugActive get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !pm.canInstallPackages()

    var updatedManagerVersion: String? by mutableStateOf(null)
        private set

    private val bundleListEventsChannel = Channel<BundleListViewModel.Event>()
    val bundleListEventsFlow = bundleListEventsChannel.receiveAsFlow()

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

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Loading state for installed apps
    var installedAppsLoading by mutableStateOf(true)

    // Bundle data
    private var apiBundle: PatchBundleSource? = null
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
            updatedManagerVersion = reVancedAPI.getAppUpdate()?.version
        }
    }

    fun setShowManagerUpdateDialogOnLaunch(value: Boolean) {
        viewModelScope.launch {
            prefs.showManagerUpdateDialogOnLaunch.update(value)
        }
    }

    fun applyAutoUpdatePrefs(manager: Boolean, patches: Boolean) = viewModelScope.launch {
        prefs.firstLaunch.update(false)

        prefs.managerAutoUpdates.update(manager)

        if (manager) checkForManagerUpdates()

        if (patches) {
            with(patchBundleRepository) {
                sources
                    .first()
                    .find { it.uid == DEFAULT_SOURCE_UID }
                    ?.asRemoteOrNull
                    ?.setAutoUpdate(true)

                updateCheck()
            }
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

    private fun sendEvent(event: BundleListViewModel.Event) {
        viewModelScope.launch { bundleListEventsChannel.send(event) }
    }

    fun cancelSourceSelection() = sendEvent(BundleListViewModel.Event.CANCEL)
    fun updateSources() = sendEvent(BundleListViewModel.Event.UPDATE_SELECTED)
    fun deleteSources() = sendEvent(BundleListViewModel.Event.DELETE_SELECTED)
    fun disableSources() = sendEvent(BundleListViewModel.Event.DISABLE_SELECTED)

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

    fun createLocalSourceFromFile(path: String) = viewModelScope.launch {
        withContext(NonCancellable) {
            withPersistentImportToast {
                val file = File(path)
                val length = file.length().takeIf { it > 0L }
                patchBundleRepository.createLocal(length) {
                    FileInputStream(file)
                }
            }
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
        apiBundle = sources.firstOrNull { it.uid == DEFAULT_SOURCE_UID }
        val versionData = extractCompatibleVersions(bundleInfo)
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
     */
    fun showPatchDialog(packageName: String) {
        pendingPackageName = packageName
        pendingAppName = AppPackages.getAppName(app, packageName)
        pendingRecommendedVersion = recommendedVersions[packageName]
        pendingCompatibleVersions = compatibleVersions[packageName] ?: emptyList()
        showApkAvailabilityDialog = true
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
            val recommendedVersion = pendingPackageName?.let { recommendedVersions[it] }
            val allVersions = pendingPackageName?.let { compatibleVersions[it] } ?: emptyList()

            if (recommendedVersion != null) {
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
                app.toast(app.getString(R.string.home_no_patches_for_app))
                if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                    selectedApp.file.delete()
                }
                cleanupPendingData()
                return
            }
        }

        startPatchingWithApp(selectedApp, allowIncompatible)
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

        // Patch filter: exclude GmsCore support in root mode
        val shouldIncludePatch: (Int, PatchInfo) -> Boolean = { _, patch ->
            patch.include && (!usingMountInstall || !patch.name.equals("GmsCore support", ignoreCase = true))
        }

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
            val bundlesMap = allBundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
            val savedOptions = withContext(Dispatchers.IO) {
                optionsRepository.getOptions(selectedApp.packageName, bundlesMap)
            }

            // Use saved selections or create new ones
            val patches = if (savedSelections.isNotEmpty()) {
                // Validate saved selections against available patches
                savedSelections.mapNotNull { (bundleUid, patchNames) ->
                    val bundle = allBundles.find { it.uid == bundleUid } ?: return@mapNotNull null
                    val validPatches = patchNames.filter { patchName ->
                        bundle.patches.any { patch ->
                            patch.name == patchName && shouldIncludePatch(bundleUid, patch)
                        }
                    }.toSet()

                    if (validPatches.isEmpty()) null
                    else bundleUid to validPatches
                }.toMap()
            } else {
                // No saved selections - use default
                allBundles.toPatchSelection(true, shouldIncludePatch)
            }

            expertModeSelectedApp = selectedApp
            expertModeBundles = allBundles
            expertModePatches = patches.toMutableMap()
            expertModeOptions = savedOptions.toMutableMap()
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
                    .filter { shouldIncludePatch(defaultBundle.uid, it) }
                    .mapTo(mutableSetOf()) { it.name }

                if (patchNames.isEmpty()) {
                    app.toast(app.getString(R.string.home_no_patches_available))
                    cleanupPendingData()
                    return
                }

                proceedWithPatching(selectedApp, mapOf(defaultBundle.uid to patchNames), emptyMap())
            } else {
                // For "Other Apps": search all enabled bundles for patches
                val bundleWithPatches = allBundles
                    .filter { it.enabled }
                    .map { bundle ->
                        val patchNames = bundle.patchSequence(allowIncompatible)
                            .filter { shouldIncludePatch(bundle.uid, it) }
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
                val allPatches = bundleWithPatches.associate { (bundle, patches) ->
                    bundle.uid to patches
                }

                proceedWithPatching(selectedApp, allPatches, emptyMap())
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
     * Toggle patch in expert mode
     */
    fun togglePatchInExpertMode(bundleUid: Int, patchName: String) {
        val currentPatches = expertModePatches.toMutableMap()
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

        expertModePatches = currentPatches
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
        val currentOptions = expertModeOptions.toMutableMap()
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

        expertModeOptions = currentOptions
    }

    /**
     * Reset options for a patch in expert mode
     */
    fun resetOptionsInExpertMode(bundleUid: Int, patchName: String) {
        val currentOptions = expertModeOptions.toMutableMap()
        val bundleOptions = currentOptions[bundleUid]?.toMutableMap() ?: return

        bundleOptions.remove(patchName)

        if (bundleOptions.isEmpty()) {
            currentOptions.remove(bundleUid)
        } else {
            currentOptions[bundleUid] = bundleOptions
        }

        expertModeOptions = currentOptions
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
    private fun extractCompatibleVersions(bundleInfo: Map<Int, Any>): Map<String, List<String>> {
        return bundleInfo[0]?.let { apiBundleInfo ->
            val info = apiBundleInfo as? PatchBundleInfo
            info?.let {
                mapOf(
                    AppPackages.YOUTUBE to it.patches
                        .filter { patch ->
                            patch.compatiblePackages?.any { pkg -> pkg.packageName == AppPackages.YOUTUBE } == true
                        }
                        .flatMap { patch ->
                            patch.compatiblePackages
                                ?.firstOrNull { pkg -> pkg.packageName == AppPackages.YOUTUBE }
                                ?.versions
                                ?: emptyList()
                        }
                        .distinct()
                        .sortedDescending(),
                    AppPackages.YOUTUBE_MUSIC to it.patches
                        .filter { patch ->
                            patch.compatiblePackages?.any { pkg -> pkg.packageName == AppPackages.YOUTUBE_MUSIC } == true
                        }
                        .flatMap { patch ->
                            patch.compatiblePackages
                                ?.firstOrNull { pkg -> pkg.packageName == AppPackages.YOUTUBE_MUSIC }
                                ?.versions
                                ?: emptyList()
                        }
                        .distinct()
                        .sortedDescending(),
                    AppPackages.REDDIT to it.patches
                        .filter { patch ->
                            patch.compatiblePackages?.any { pkg -> pkg.packageName == AppPackages.REDDIT } == true
                        }
                        .flatMap { patch ->
                            patch.compatiblePackages
                                ?.firstOrNull { pkg -> pkg.packageName == AppPackages.REDDIT }
                                ?.versions
                                ?: emptyList()
                        }
                        .distinct()
                        .sortedDescending()
                ).filterValues { it.isNotEmpty() }
            } ?: emptyMap()
        } ?: emptyMap()
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
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
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
