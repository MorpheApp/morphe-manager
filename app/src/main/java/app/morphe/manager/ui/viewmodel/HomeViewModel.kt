/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.patcher.patch.BundleAppMetadata
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.filterGmsCore
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import app.morphe.patcher.patch.AppTarget
import io.ktor.http.encodeURLPath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder.encode
import java.security.MessageDigest
import javax.net.ssl.SSLException
import kotlin.time.Clock

/**
 * Bundle update status for snackbar display.
 */
enum class BundleUpdateStatus {
    Updating,    // Update in progress
    Success,     // Update completed successfully
    Warning,     // Patches may be outdated (on metered network, updates disabled)
    Error        // Error occurred (including no internet)
}

/**
 * Dialog state for unsupported version warning.
 */
data class UnsupportedVersionDialogState(
    val packageName: String,
    val version: String,
    val recommendedVersion: AppTarget?,
    val allCompatibleVersions: List<AppTarget> = emptyList(),
    /** True if the selected version is marked as experimental in the patch bundle. */
    val isExperimental: Boolean = false
)

/**
 * Dialog state for wrong package warning.
 */
data class WrongPackageDialogState(
    val expectedPackage: String,
    val actualPackage: String
)

/**
 * Dialog state for APK signature mismatch warning.
 * Shown when the selected APK's signing certificate does not match
 * the expected signatures declared in the patch bundle.
 */
data class InvalidSignatureDialogState(
    val packageName: String,
    val appName: String,
)

/**
 * Quick patch parameters.
 */
data class QuickPatchParams(
    val selectedApp: SelectedApp,
    val patches: PatchSelection,
    val options: Options
)

/**
 * Saved APK information for display in APK selection dialog.
 */
data class SavedApkInfo(
    val fileName: String,
    val filePath: String,
    val version: String
)

/**
 * Combined ViewModel for Home and Dashboard functionality.
 * Manages all dialogs, user interactions, APK processing, and bundle management.
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
    private val filesystem: Filesystem,
    val homeAppButtonPrefs: HomeAppButtonPreferences
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
     * Android 11 kills the app process after granting the "install apps" permission.
     */
    val android11BugActive get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !pm.canInstallPackages()

    var updatedManagerVersion: String? by mutableStateOf(null)
        private set

    // Dialog visibility states
    var showAndroid11Dialog by mutableStateOf(false)
    var showBundleManagementSheet by mutableStateOf(false)
    var showAddSourceDialog by mutableStateOf(false)
    var bundleToRename by mutableStateOf<PatchBundleSource?>(null)
    var showRenameBundleDialog by mutableStateOf(false)

    // Deep link: pending bundle to add via confirmation dialog
    var deepLinkPendingBundle by mutableStateOf<DeepLinkBundle?>(null)
        private set

    data class DeepLinkBundle(val url: String, val name: String?)

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
    var showExperimentalVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
    var showWrongPackageDialog by mutableStateOf<WrongPackageDialogState?>(null)
    var showSplitApkWarningDialog by mutableStateOf(false)
    var showInvalidSignatureDialog by mutableStateOf<InvalidSignatureDialogState?>(null)

    // Pending data during APK selection
    var pendingPackageName by mutableStateOf<String?>(null)
    var pendingAppName by mutableStateOf<String?>(null)
    var pendingRecommendedVersion by mutableStateOf<AppTarget?>(null)
    var pendingCompatibleVersions by mutableStateOf<List<AppTarget>>(emptyList())
    var pendingSelectedApp by mutableStateOf<SelectedApp?>(null)
    var resolvedDownloadUrl by mutableStateOf<String?>(null)
    var pendingSavedApkInfo by mutableStateOf<SavedApkInfo?>(null)

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Metered network dialog: shown when user tries to patch on mobile data with updates disabled
    var showMeteredPatchingDialog by mutableStateOf(false)
        private set

    // Low disk space warning dialog: shown when < 1 GB free before patching starts
    var showLowDiskSpaceDialog by mutableStateOf(false)
        private set
    var lowDiskSpaceFreeGb by mutableFloatStateOf(0f)
        private set

    // Pending patching action captured when the guard dialog is shown
    private var pendingPatchAction: (suspend () -> Unit)? = null

    // Loading state for installed apps
    var installedAppsLoading by mutableStateOf(true)

    // Bundle data — reactive StateFlows derived directly from bundleInfoFlow
    val compatibleVersionsFlow: StateFlow<Map<String, List<AppTarget>>> =
        patchBundleRepository.bundleInfoFlow
            .combine(patchBundleRepository.sources) { bundleInfo, sources ->
                val enabledUids = sources.filter { it.enabled }.map { it.uid }.toSet()
                extractCompatibleVersions(bundleInfo, enabledUids)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val recommendedVersionsFlow: StateFlow<Map<String, AppTarget>> =
        combine(
            compatibleVersionsFlow,
            prefs.bundleExperimentalVersionsEnabled.flow,
            patchBundleRepository.bundleInfoFlow,
            patchBundleRepository.sources
        ) { versionData, experimentalEnabledUids, bundleInfo, sources ->
            val enabledUids = sources.filter { it.enabled }.map { it.uid }.toSet()
            // Packages for which at least one enabled bundle has experimental toggle on
            val experimentalEnabledPackages = bundleInfo
                .filterKeys { it in enabledUids && it.toString() in experimentalEnabledUids }
                .values
                .flatMap { it.patches }
                .flatMap { it.compatiblePackages.orEmpty() }
                .mapNotNull { it.packageName }
                .toSet()

            versionData.mapValues { (packageName, targets) ->
                if (packageName in experimentalEnabledPackages) {
                    // Experimental mode: prefer the highest experimental version, fallback to first
                    targets.firstOrNull { it.isExperimental } ?: targets.first()
                } else {
                    // Normal mode: prefer the highest stable version, fallback to first
                    targets.firstOrNull { !it.isExperimental } ?: targets.first()
                }
            }
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Convenience accessors — read current value synchronously for non-reactive call sites
    val recommendedVersions: Map<String, AppTarget> get() = recommendedVersionsFlow.value
    val compatibleVersions: Map<String, List<AppTarget>> get() = compatibleVersionsFlow.value

    // Track available updates for installed apps
    private val _appUpdatesAvailable = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val appUpdatesAvailable: StateFlow<Map<String, Boolean>> = _appUpdatesAvailable.asStateFlow()

    // Track deleted apps
    var appsDeletedStatus by mutableStateOf<Map<String, Boolean>>(emptyMap())
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
        triggerUpdateCheck()
    }

    /**
     * Returns `true` when the user has disabled metered updates AND is currently on
     * a metered (mobile data) connection - meaning patches may not be up to date.
     */
    fun isOnMeteredWithUpdatesDisabled(): Boolean {
        if (prefs.allowMeteredUpdates.getBlocking()) return false
        return networkInfo.isMetered()
    }

    /**
     * Guard entry-point for all patching flows.
     * Shows MeteredPatchingDialog when on metered network with updates disabled,
     * so the user can choose to update patches first or patch anyway.
     * Otherwise, launches [action] immediately.
     */
    fun guardPatching(action: suspend () -> Unit) {
        // Check available storage first — low disk space is the most common cause of
        // cryptic "file not found" errors and corrupt output APKs during patching.
        val lowDiskSpaceThresholdGb = 1f // Minimum free storage in GB required before patching
        val freeBytes = StatFs(app.filesDir.absolutePath).availableBytes
        val freeGb = freeBytes / (1024f * 1024f * 1024f)
        if (freeGb < lowDiskSpaceThresholdGb) {
            pendingPatchAction = action
            lowDiskSpaceFreeGb = freeGb
            showLowDiskSpaceDialog = true
            return
        }
        if (isOnMeteredWithUpdatesDisabled()) {
            pendingPatchAction = action
            showMeteredPatchingDialog = true
        } else {
            viewModelScope.launch { action() }
        }
    }

    /**
     * User chose to update patches first, then automatically continue patching.
     */
    fun refreshBundlesAndContinuePatching() {
        showMeteredPatchingDialog = false
        val action = pendingPatchAction ?: return
        pendingPatchAction = null
        viewModelScope.launch {
            // User explicitly requested update - bypass metered check and wait for completion
            patchBundleRepository.updateCheckAndAwait(allowUnsafeNetwork = true)
            action()
        }
    }

    /**
     * User chose to patch with the currently cached patches despite being on metered network.
     */
    fun dismissMeteredPatchingDialogAndProceed() {
        showMeteredPatchingDialog = false
        val action = pendingPatchAction ?: return
        pendingPatchAction = null
        viewModelScope.launch { action() }
    }

    /**
     * User canceled patching from the metered network dialog.
     */
    fun dismissMeteredPatchingDialog() {
        showMeteredPatchingDialog = false
        pendingPatchAction = null
    }

    /**
     * User chose to proceed with patching despite low disk space.
     * Continues to the metered network check if applicable, then launches the action.
     */
    fun dismissLowDiskSpaceDialogAndProceed() {
        showLowDiskSpaceDialog = false
        val action = pendingPatchAction ?: return
        pendingPatchAction = null
        if (isOnMeteredWithUpdatesDisabled()) {
            pendingPatchAction = action
            showMeteredPatchingDialog = true
        } else {
            viewModelScope.launch { action() }
        }
    }

    /**
     * User canceled patching from the low disk space dialog.
     */
    fun dismissLowDiskSpaceDialog() {
        showLowDiskSpaceDialog = false
        pendingPatchAction = null
    }

    /**
     * Checks for a manager update and defers showing the banner until the APK
     * is likely fully uploaded. If the release is newer than [MANAGER_UPDATE_SHOW_DELAY_SECONDS],
     * the banner is shown immediately; otherwise we wait out the remaining time.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    suspend fun checkForManagerUpdates() {
        uiSafe(app, R.string.failed_to_check_updates, "Failed to check for updates") {
            val update = morpheAPI.getAppUpdate() ?: return@uiSafe

            val releaseAgeSeconds = (Clock.System.now().toEpochMilliseconds() -
                    update.createdAt.toInstant(TimeZone.UTC).toEpochMilliseconds()) / 1_000L

            if (releaseAgeSeconds < MANAGER_UPDATE_SHOW_DELAY_SECONDS) {
                val remainingMs = (MANAGER_UPDATE_SHOW_DELAY_SECONDS - releaseAgeSeconds) * 1_000L
                Log.d(tag, "Manager update ${update.version} is ${releaseAgeSeconds}s old, waiting ${remainingMs / 1000}s before showing banner")
                delay(remainingMs)
            }

            updatedManagerVersion = update.version
        }
    }

    /**
     * Launches [checkForManagerUpdates] on [viewModelScope] so it survives composition changes.
     * Safe to call from UI without a coroutine scope.
     */
    fun triggerUpdateCheck() {
        viewModelScope.launch {
            checkForManagerUpdates()
        }
    }

    /**
     * Check for bundle updates for installed apps.
     *
     * Iterates all active bundles. For each [RemotePatchBundle], if a changelog is
     * available and uses conventional-changelog scopes, only apps with explicit
     * changes in newer entries receive an update badge.
     *
     * Falls back to showing the badge when changelog is unavailable or the app
     * name cannot be resolved.
     */
    suspend fun checkInstalledAppsForUpdates(
        installedApps: List<InstalledApp>,
    ) = withContext(Dispatchers.IO) {
        val sources = patchBundleRepository.sources.first()
        if (sources.isEmpty()) {
            _appUpdatesAvailable.value = emptyMap()
            return@withContext
        }

        // Pre-fetch changelog entries for every remote bundle, keyed by uid.
        // runCatching per bundle so a network failure in one doesn't block others.
        val changelogByUid: Map<Int, List<ChangelogEntry>?> = sources.associate { source ->
            source.uid to runCatching {
                source.asRemoteOrNull?.fetchChangelogEntries(sinceVersion = null)
            }.getOrNull()
        }

        val currentVersionByUid: Map<Int, String?> = sources.associate { it.uid to it.version }

        val updates = mutableMapOf<String, Boolean>()

        installedApps.forEach { app ->
            // Get stored bundle versions for this app
            val storedVersions = installedAppRepository.getBundleVersionsForApp(app.currentPackageName)
            val appName = resolveChangelogName(app.originalPackageName)

            // Check if any bundle used for this app has been updated
            val hasUpdate = storedVersions.any { (bundleUid, storedVersion) ->
                val currentVersion = currentVersionByUid[bundleUid] ?: return@any false
                if (!isNewerVersion(storedVersion, currentVersion)) return@any false

                // Bundle is newer — refine with changelog if available.
                // No changelog (null) → show badge (network error or local bundle).
                // Unknown app name (null) → show badge (can't match scopes).
                // Known name, no matching scope → no badge.
                val entries = changelogByUid[bundleUid] ?: return@any true
                if (appName == null) return@any true
                ChangelogParser.hasChangesFor(
                    entries = entries,
                    installedVersion = storedVersion,
                    appName = appName,
                )
            }

            updates[app.currentPackageName] = hasUpdate
        }

        _appUpdatesAvailable.value = updates
    }

    /**
     * Resolves the changelog scope name for [packageName].
     * 1. [KnownApps.fallbackName] — static registry (offline, reliable).
     * 2. [PM] label — system label for any installed app not in the registry.
     * Returns null when neither source yields a name.
     */
    private fun resolveChangelogName(packageName: String): String? =
        KnownApps.fallbackName(packageName)
            ?: pm.getPackageInfo(packageName)?.let { with(pm) { it.label() } }

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

    /**
     * Called when the app is opened via a deep link containing a bundle URL.
     * Shows a confirmation dialog instead of adding silently.
     */
    fun handleDeepLinkAddSource(url: String, name: String?) {
        deepLinkPendingBundle = DeepLinkBundle(url = url, name = name)
    }

    /** User confirmed adding the bundle from the deep link confirmation dialog. */
    fun confirmDeepLinkBundle() {
        val bundle = deepLinkPendingBundle ?: return
        deepLinkPendingBundle = null
        createRemoteSource(bundle.url, autoUpdate = true)
    }

    /** User dismissed the deep link confirmation dialog. */
    fun dismissDeepLinkBundle() {
        deepLinkPendingBundle = null
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

    /**
     * Per-package metadata aggregated from all enabled patch bundles.
     * Provides display names, accent colors, APK type requirements, and valid signatures
     * without relying on hardcoded constants for non-KnownApps packages.
     */
    val bundleAppMetadataFlow: StateFlow<Map<String, BundleAppMetadata>> =
        patchBundleRepository.allBundlesInfoFlow
            .map { bundleInfoMap -> BundleAppMetadata.buildFrom(bundleInfoMap) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Set of all unique package names that have patches across all enabled bundles.
     * Derived from [bundleAppMetadataFlow] keys — no need to re-iterate all patches.
     */
    val patchablePackagesFlow: StateFlow<Set<String>> =
        bundleAppMetadataFlow
            .map { it.keys }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Hidden packages filtered to only those present in currently active bundles.
     * When a bundle is disabled/removed, its packages disappear from this flow automatically.
     */
    val filteredHiddenPackages: StateFlow<Set<String>> = combine(
        homeAppButtonPrefs.hiddenPackages,
        patchablePackagesFlow
    ) { hidden, active ->
        hidden.filter { it in active }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Hidden app items with resolved display names and package info.
     */
    val hiddenAppItems: StateFlow<List<HomeAppItem>> = combine(
        filteredHiddenPackages,
        bundleAppMetadataFlow
    ) { hiddenPackages, metadata ->
        hiddenPackages.map { packageName ->
            val bundleMeta = metadata[packageName]
            val knownApp = KnownApps.fromPackage(packageName)
            val resolvedData = appDataResolver.resolveAppData(
                packageName = packageName,
                preferredSource = AppDataSource.PATCHED_APK
            )
            val displayName = resolvedData.displayName.takeIf {
                resolvedData.source == AppDataSource.INSTALLED || resolvedData.source == AppDataSource.PATCHED_APK
            } ?: bundleMeta?.displayName ?: KnownApps.getAppName(packageName)
            val gradientColors = bundleMeta?.gradientColors ?: KnownApps.DEFAULT_COLORS
            HomeAppItem(
                packageName = packageName,
                displayName = displayName,
                gradientColors = gradientColors,
                installedApp = null,
                packageInfo = resolvedData.packageInfo,
                isPinnedByDefault = knownApp?.isPinnedByDefault == true,
                isDeleted = false,
                hasUpdate = false,
                patchCount = 0
            )
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Combined flow that produces the sorted list of home app items.
     *
     * Sorting order by display name:
     * 1. Patched (installed) apps first
     * 2. Non-patched apps
     * Hidden apps are excluded.
     */
    val homeAppItems: StateFlow<List<HomeAppItem>> = combine(
        patchablePackagesFlow,
        homeAppButtonPrefs.hiddenPackages,
        installedAppRepository.getAll(),
        _appUpdatesAvailable,
        bundleAppMetadataFlow
    ) { packages, hidden, installedApps, updatesMap, metadata ->
        val installedMap = installedApps.associateBy { it.originalPackageName }

        packages
            .filter { it !in hidden }
            .map { packageName ->
                val installedApp = installedMap[packageName]
                val bundleMeta = metadata[packageName]
                val knownApp = KnownApps.fromPackage(packageName)

                // Gradient colors: bundle declared → default (colors come from patch bundle)
                val gradientColors = bundleMeta?.gradientColors ?: KnownApps.DEFAULT_COLORS

                // Priority: PATCHED_APK → ORIGINAL_APK → INSTALLED → CONSTANTS
                val resolvedData = appDataResolver.resolveAppData(
                    packageName = packageName,
                    preferredSource = AppDataSource.PATCHED_APK
                )

                // Display name priority: installed/patched APK label → bundle declared → KnownApps fallback
                val displayName = resolvedData.displayName.takeIf {
                    resolvedData.source == AppDataSource.INSTALLED || resolvedData.source == AppDataSource.PATCHED_APK
                } ?: bundleMeta?.displayName ?: KnownApps.getAppName(packageName)

                // Determine deleted status
                val isDeleted = installedApp?.let { installed ->
                    val hasSavedCopy = listOf(
                        filesystem.getPatchedAppFile(installed.currentPackageName, installed.version),
                        filesystem.getPatchedAppFile(installed.originalPackageName, installed.version)
                    ).distinctBy { it.absolutePath }.any { it.exists() }
                    pm.isAppDeleted(
                        packageName = installed.currentPackageName,
                        hasSavedCopy = hasSavedCopy,
                        wasInstalledOnDevice = installed.installType != InstallType.SAVED
                    )
                } == true

                // Determine update status
                val hasUpdate = installedApp?.let {
                    updatesMap[it.currentPackageName] == true
                } == true

                HomeAppItem(
                    packageName = packageName,
                    displayName = displayName,
                    gradientColors = gradientColors,
                    installedApp = installedApp,
                    packageInfo = resolvedData.packageInfo,
                    isPinnedByDefault = knownApp?.isPinnedByDefault == true,
                    isDeleted = isDeleted,
                    hasUpdate = hasUpdate,
                    patchCount = 0
                )
            }
            .sortedWith(
                compareByDescending<HomeAppItem> { it.installedApp != null }
                    .thenByDescending { it.isPinnedByDefault }
                    .thenByDescending { it.packageInfo != null }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Hide an app from the home screen.
     */
    fun hideApp(packageName: String) {
        homeAppButtonPrefs.hide(packageName)
    }

    /**
     * Unhide an app on the home screen.
     */
    fun unhideApp(packageName: String) {
        homeAppButtonPrefs.unhide(packageName)
    }

    /**
     * Returns the set of experimental version strings for a package from all currently enabled bundles.
     * Derived directly from [compatibleVersions] which already contains [AppTarget] objects.
     * Used by the UI to show "Experimental" badges on specific versions.
     */
    fun getExperimentalVersionsForPackage(packageName: String): Set<String> =
        compatibleVersions[packageName]
            ?.filter { it.isExperimental }
            ?.mapNotNull { it.version }
            ?.toSet()
            ?: emptySet()

    /**
     * Update loading state.
     */
    fun updateLoadingState(bundleUpdateInProgress: Boolean, hasInstalledApps: Boolean) {
        installedAppsLoading = bundleUpdateInProgress || !hasInstalledApps
    }

    /**
     * Update deleted apps status.
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
     * Handle app button click.
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
     * Show patch dialog.
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
        pendingAppName = KnownApps.getAppName(packageName)
        pendingRecommendedVersion = recommendedVersions[packageName]
        pendingCompatibleVersions = compatibleVersions[packageName] ?: emptyList()

        // Guard: if there is a pending bundle update on metered data, show the outdated-patches
        // dialog before proceeding with the actual APK selection flow.
        guardPatching { showPatchDialogInternal(packageName) }
    }

    private suspend fun showPatchDialogInternal(packageName: String) {
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
                savedInfo.version == recommendedVersion.version

        if (shouldAutoUseSaved) {
            // Skip dialog and use saved APK directly
            handleSavedApkSelection()
        } else {
            // Show dialog
            showApkAvailabilityDialog = true
        }
    }

    /**
     * Load information about saved original APK for a package.
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
     * Handle APK file selection.
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
     * Handle selection of saved APK from APK availability dialog.
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
     * Process selected APK file.
     *
     * This function only answers: "do any patches EXIST for this APK?"
     * The include/selection logic is handled in [startPatchingWithApp].
     */
    private suspend fun processSelectedApp(selectedApp: SelectedApp) {
        // Validate package name if expected (known-app flow sets pendingPackageName)
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

        // Check if the selected file is a split APK while the bundle requires a full APK.
        // This must happen BEFORE signature verification — split archives (.apkm/.apks/.xapk)
        // are not valid APKs so PackageManager cannot read their signature, which would cause
        // a false "invalid signature" dialog instead of the correct "split APK" warning.
        if (selectedApp is SelectedApp.Local) {
            val requiredApkFileType = bundleAppMetadataFlow.value[selectedApp.packageName]?.apkFileType

            val isSplitFile = SplitApkPreparer.isSplitArchive(selectedApp.file)

            if (isSplitFile && requiredApkFileType?.isApk == true && requiredApkFileType.isRequired) {
                pendingSelectedApp = selectedApp
                showSplitApkWarningDialog = true
                cleanupPendingData(keepSelectedApp = true)
                return
            }

            // Verify APK signature against the expected signatures declared in the patch bundle.
            // For split archives (.apkm/.apks/.xapk) PackageManager cannot read the signature
            // from the zip container directly — extract the representative base APK first and
            // verify against that. The extracted file is cleaned up immediately after.
            val expectedSignatures = bundleAppMetadataFlow.value[selectedApp.packageName]?.signatures
            if (!expectedSignatures.isNullOrEmpty()) {
                val signatureMatch = withContext(Dispatchers.IO) {
                    if (isSplitFile) {
                        val extracted = SplitApkInspector.extractRepresentativeApk(
                            source = selectedApp.file,
                            workspace = filesystem.uiTempDir
                        )
                        if (extracted == null) {
                            // Cannot extract base APK — skip verification rather than false-block
                            true
                        } else {
                            try {
                                verifyApkSignature(extracted.file.absolutePath, expectedSignatures)
                            } finally {
                                extracted.cleanup()
                            }
                        }
                    } else {
                        verifyApkSignature(selectedApp.file.absolutePath, expectedSignatures)
                    }
                }
                if (!signatureMatch) {
                    pendingSelectedApp = selectedApp
                    showInvalidSignatureDialog = InvalidSignatureDialogState(
                        packageName = selectedApp.packageName,
                        appName = pendingAppName ?: KnownApps.getAppName(selectedApp.packageName)
                    )
                    cleanupPendingData(keepSelectedApp = true)
                    return
                }
            }
        }

        val allowIncompatible = prefs.disablePatchVersionCompatCheck.getBlocking()

        // Get scoped bundles for this APK (package + version).
        // Scoped.patches contains every patch that is compatible with this packageName
        // (including universal patches where compatiblePackages == null).
        // Scoped.compatible = version matches, Scoped.incompatible = package matches but wrong version,
        // Scoped.universal = compatiblePackages == null (applies to any package/version).
        val bundles = withContext(Dispatchers.IO) {
            patchBundleRepository
                .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
                .first()
        }

        val enabledBundles = bundles.filter { it.enabled }

        // Categorize what exists across all enabled bundles for this APK:
        val hasCompatible   = enabledBundles.any { it.compatible.isNotEmpty() }   // right pkg + right version
        val hasIncompatible = enabledBundles.any { it.incompatible.isNotEmpty() } // right pkg, wrong version
        val hasUniversal    = enabledBundles.any { it.universal.isNotEmpty() }    // no pkg restriction
        val hasAnything     = hasCompatible || hasIncompatible || hasUniversal

        if (!hasAnything) {
            // Truly no patches exist for this package in any enabled bundle
            app.toast(app.getString(R.string.home_no_patches_available))
            if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                selectedApp.file.delete()
            }
            cleanupPendingData()
            return
        }

        // Show the unsupported-version warning when:
        //   - version-specific patches exist for this package (incompatible list is non-empty)
        //   - BUT none of them match this APK version (compatible list is empty)
        //   - AND the user has NOT disabled the version compat check
        // Universal patches do not suppress this warning — the user should still be informed
        // that the APK version is not officially supported.
        // Note: experimental versions are compatible (they pass the version check) but show an
        // additional "Experimental" badge in the warning dialog.
        val versionMismatch = !hasCompatible && hasIncompatible
        // Experimental check is independent — a version can be experimental AND compatible
        val isVersionExperimental = enabledBundles.any { it.isVersionExperimental }

        // Check if the user has enabled experimental-version mode for this package's bundle
        val experimentalEnabledUids = prefs.bundleExperimentalVersionsEnabled.getBlocking()
        val isExperimentalModeEnabled = enabledBundles.any { bundle ->
            bundle.uid.toString() in experimentalEnabledUids
        }

        if (versionMismatch && !allowIncompatible) {
            val recommendedVersion = recommendedVersions[selectedApp.packageName]
            val allVersions = compatibleVersions[selectedApp.packageName] ?: emptyList()
            pendingSelectedApp = selectedApp
            showUnsupportedVersionDialog = UnsupportedVersionDialogState(
                packageName = selectedApp.packageName,
                version = selectedApp.version ?: "unknown",
                recommendedVersion = recommendedVersion,
                allCompatibleVersions = allVersions,
                isExperimental = isVersionExperimental
            )
            cleanupPendingData(keepSelectedApp = true)
            return
        }

        // If the version is experimental, show the appropriate warning:
        // - Experimental mode ON → ExperimentalVersionWarningDialog
        // - Experimental mode OFF → UnsupportedVersionWarningDialog
        if (isVersionExperimental && !allowIncompatible) {
            val recommendedVersion = recommendedVersions[selectedApp.packageName]
            val allVersions = compatibleVersions[selectedApp.packageName] ?: emptyList()
            pendingSelectedApp = selectedApp
            val state = UnsupportedVersionDialogState(
                packageName = selectedApp.packageName,
                version = selectedApp.version ?: "unknown",
                recommendedVersion = recommendedVersion,
                allCompatibleVersions = allVersions,
                isExperimental = true
            )
            if (isExperimentalModeEnabled) {
                showExperimentalVersionDialog = state
            } else {
                showUnsupportedVersionDialog = state
            }
            cleanupPendingData(keepSelectedApp = true)
            return
        }

        // Patches exist and are applicable → proceed.
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
     * Called when the user confirms proceeding despite an APK signature mismatch.
     * Skips the signature verification step and continues with the normal flow.
     */
    suspend fun processSelectedAppIgnoringSignature(selectedApp: SelectedApp) {
        val allowIncompatible = prefs.disablePatchVersionCompatCheck.getBlocking()
        if (rootInstaller.isDeviceRooted()) {
            requestPrePatchInstallerSelection(selectedApp, allowIncompatible)
        } else {
            usingMountInstall = false
            startPatchingWithApp(selectedApp, allowIncompatible)
        }
    }

    /**
     * Start patching flow.
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
            // Expert Mode: Load saved selections and options only for current bundles
            val currentBundleUids = allBundles.map { it.uid }.toSet()

            // Load selections
            val savedSelections = withContext(Dispatchers.IO) {
                patchSelectionRepository.getAllSelectionsForPackage(selectedApp.packageName)
                    .filterKeys { it in currentBundleUids }
            }

            // Load options
            val savedOptions = withContext(Dispatchers.IO) {
                optionsRepository.getAllOptionsForPackage(selectedApp.packageName, bundlesMap)
                    .filterKeys { it in currentBundleUids }
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
                // No saved selections - use default for all current bundles
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
            // Prefer the default bundle if it is enabled and has patches for this package.
            // If the default bundle is disabled or has no patches, fall through to use
            // all enabled bundles — this allows third-party bundles to work for known apps too.
            val defaultBundle = allBundles.find { it.uid == DEFAULT_SOURCE_UID }
            val defaultPatchNames = if (defaultBundle != null && defaultBundle.enabled) {
                defaultBundle.patchSequence(allowIncompatible)
                    .filter { it.include }
                    .mapTo(mutableSetOf()) { it.name }
            } else {
                emptySet()
            }

            if (defaultPatchNames.isNotEmpty()) {
                // Default bundle is active and has patches → use it (simple mode behavior)
                val patches = mapOf(defaultBundle!!.uid to defaultPatchNames).applyGmsCoreFilter()
                proceedWithPatching(selectedApp, patches, emptyMap())
            } else {
                // For "Other Apps": collect patches from all enabled bundles.
                // A patch is applicable if:
                //   - compatiblePackages == null (universal), OR
                //   - compatiblePackages contains this packageName
                // We use allowIncompatible=true here because the user explicitly chose
                // this APK file, so version mismatches should not block patching.
                val bundleWithPatches = allBundles
                    .filter { it.enabled }
                    .map { bundle ->
                        // patchSequence(true) = all patches in Scoped.patches
                        // which already contains only compatible+incompatible+universal for this pkg.
                        val patchNames = bundle.patchSequence(allowIncompatible = true)
                            .filter { it.include }
                            .mapTo(mutableSetOf()) { it.name }
                        bundle to patchNames
                    }
                    .filter { (_, patches) -> patches.isNotEmpty() }

                if (bundleWithPatches.isEmpty()) {
                    // No patches have include=true (use=true in the bundle JSON).
                    // This is the case for third-party bundles where all universal patches
                    // ship with use=false and require explicit user configuration.
                    // Fall through to expert mode so the user can select and configure patches.
                    val currentBundleUids = allBundles.map { it.uid }.toSet()

                    val savedSelections = withContext(Dispatchers.IO) {
                        patchSelectionRepository.getAllSelectionsForPackage(selectedApp.packageName)
                            .filterKeys { it in currentBundleUids }
                    }
                    val savedOptions = withContext(Dispatchers.IO) {
                        optionsRepository.getAllOptionsForPackage(selectedApp.packageName, bundlesMap)
                            .filterKeys { it in currentBundleUids }
                    }

                    expertModeSelectedApp = selectedApp
                    expertModeBundles = allBundles
                    expertModePatches = savedSelections.toMutableMap()
                    expertModeOptions = savedOptions.toMutableMap()
                    showExpertModeDialog = true
                    return
                }

                // Use all include=true patches from all bundles
                val patches = bundleWithPatches
                    .associate { (bundle, patches) -> bundle.uid to patches }
                    .applyGmsCoreFilter()

                proceedWithPatching(selectedApp, patches, emptyMap())
            }
        }
    }

    /**
     * Save options to repository.
     */
    fun saveOptions(packageName: String, options: Options) {
        viewModelScope.launch(Dispatchers.IO) {
            optionsRepository.saveOptions(packageName , options)
        }
    }

    /**
     * Proceed with patching.
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
     * Update option in expert mode.
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
     * Reset options for a patch in expert mode.
     */
    fun resetOptionsInExpertMode(bundleUid: Int, patchName: String) {
        expertModeOptions = expertModeOptions.resetOptionsForPatch(bundleUid, patchName)
    }

    /**
     * Clean up expert mode data.
     */
    fun cleanupExpertModeData() {
        showExpertModeDialog = false
        expertModeSelectedApp = null
        expertModeBundles = emptyList()
        expertModePatches = emptyMap()
        expertModeOptions = emptyMap()
    }

    /**
     * Resolve download redirect.
     */
    fun resolveDownloadRedirect() {
        fun resolveUrlRedirect(url: String): String {
            return try {
                // TODO: Use HttpModule instead of simple URL connections.
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
                        Log.i(tag, "Location tag is blank: ${connection.responseMessage}")
                        getApiOfflineWebSearchUrl()
                    } else {
                        val resolved =
                            if (location.startsWith("http://") || location.startsWith("https://")) {
                                location
                            } else {
                                val prefix = "${originalUrl.protocol}://${originalUrl.host}"
                                if (location.startsWith("/")) "$prefix$location" else "$prefix/$location"
                            }
                        Log.i(tag, "Result: $resolved")
                        resolved
                    }
                } else {
                    Log.w(tag, "Unexpected response code: $responseCode")
                    getApiOfflineWebSearchUrl()
                }
            } catch (ex: SocketTimeoutException) {
                Log.w(tag, "Timeout while resolving search redirect: $ex")
                url
            } catch (ex: SSLException) {
                Log.w(tag, "SSL exception while resolving search redirect: $ex")
                getApiOfflineWebSearchUrl()
            } catch (ex: Exception) {
                Log.w(tag, "Exception while resolving search redirect: $ex")
                getApiOfflineWebSearchUrl()
            }
        }

        // Handle null pendingRecommendedVersion
        val escapedVersion = pendingRecommendedVersion?.let { encode(it.version, "UTF-8") } ?: "any"
        val searchQuery = "$pendingPackageName~$escapedVersion~${Build.SUPPORTED_ABIS.first()}".encodeURLPath()
        val searchUrl = "$MORPHE_API_URL/v2/web-search/$searchQuery"
        Log.d(tag, "Using search url: $searchUrl")

        resolvedDownloadUrl = searchUrl

        viewModelScope.launch(Dispatchers.IO) {
            var resolved = resolveUrlRedirect(searchUrl)

            if (resolved.startsWith(MORPHE_API_URL)) {
                Log.i(tag, "Redirect still on API host, resolving again")
                resolved = resolveUrlRedirect(resolved)
            }

            withContext(Dispatchers.Main) {
                resolvedDownloadUrl = resolved
            }
        }
    }

    fun getApiOfflineWebSearchUrl(): String {
        val architecture = if (pendingPackageName == KnownApps.YOUTUBE_MUSIC) {
            " (${Build.SUPPORTED_ABIS.first()})"
        } else {
            "nodpi"
        }

        // Handle null pendingRecommendedVersion
        val versionPart = pendingRecommendedVersion?.version?.let { "\"$it\"" } ?: ""
        val searchQuery = "\"$pendingPackageName\" $versionPart $architecture site:APKMirror.com"
        val searchUrl = "https://google.com/search?q=${encode(searchQuery, "UTF-8")}"
        Log.d(tag, "Using search query: $searchQuery")
        return searchUrl
    }

    /**
     * Handle download instructions continue.
     */
    fun handleDownloadInstructionsContinue(onOpenUrl: (String) -> Boolean) {
        val urlToOpen = resolvedDownloadUrl!!

        if (onOpenUrl(urlToOpen)) {
            showDownloadInstructionsDialog = false
            showFilePickerPromptDialog = true
        } else {
            Log.w(tag, "Failed to open URL")
            app.toast(app.getString(R.string.sources_management_failed_to_open_url))
            showDownloadInstructionsDialog = false
            cleanupPendingData()
        }
    }

    /**
     * Clean up pending data.
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
     * Extract compatible versions for each package from bundle info.
     * Returns a map of package name to sorted list of AppTargets — newest first regardless
     * of experimental status. The [AppTarget.isExperimental] flag is preserved for badge display.
     * Single pass per bundle — O(patches) instead of O(packages × patches).
     */
    private fun extractCompatibleVersions(
        bundleInfo: Map<Int, PatchBundleInfo>,
        enabledBundleUids: Set<Int> = emptySet()
    ): Map<String, List<AppTarget>> {
        val targetsByPackage = mutableMapOf<String, MutableMap<String, AppTarget>>()

        bundleInfo.forEach { (bundleUid, info) ->
            if (enabledBundleUids.isNotEmpty() && bundleUid !in enabledBundleUids) return@forEach

            info.patches.forEach { patch ->
                patch.compatiblePackages?.forEach { pkg ->
                    val packageName = pkg.packageName ?: return@forEach
                    val map = targetsByPackage.getOrPut(packageName) { mutableMapOf() }

                    pkg.versions?.forEach { version ->
                        val isExperimental = pkg.experimentalVersions?.contains(version) == true
                        // If a version appears in multiple patches, prefer stable over experimental
                        if (version !in map || isExperimental.not()) {
                            map[version] = AppTarget(version = version, isExperimental = isExperimental)
                        }
                    }
                }
            }
        }

        // Sort all versions together newest→oldest regardless of experimental flag
        return targetsByPackage
            .mapValues { (_, map) -> map.values.sortedDescending() }
            .filterValues { it.isNotEmpty() }
    }

    /**
     * Verify that the APK at [apkPath] is signed with one of the [expectedSha256Signatures].
     *
     * Uses [PackageManager.GET_SIGNING_CERTIFICATES] (API 28+) with fallback to
     * [PackageManager.GET_SIGNATURES] for older devices. Returns true if at least one
     * certificate fingerprint matches, false if none match or the APK cannot be read.
     *
     * An empty / null [expectedSha256Signatures] is treated as "no verification required" → true.
     */
    @Suppress("DEPRECATION")
    private fun verifyApkSignature(apkPath: String, expectedSha256Signatures: Set<String>): Boolean {
        if (expectedSha256Signatures.isEmpty()) return true
        return try {
            val signatures: Array<android.content.pm.Signature> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val flags = PackageManager.GET_SIGNING_CERTIFICATES
                    val info = app.packageManager.getPackageArchiveInfo(apkPath, flags)
                        ?: return false
                    val signingInfo = info.signingInfo ?: return false
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else {
                    val flags = PackageManager.GET_SIGNATURES
                    val info = app.packageManager.getPackageArchiveInfo(apkPath, flags)
                        ?: return false
                    @Suppress("DEPRECATION")
                    info.signatures ?: return false
                }

            val digest = MessageDigest.getInstance("SHA-256")
            signatures.any { sig ->
                // Reset before each use — MessageDigest is stateful
                digest.reset()
                val fingerprint = digest.digest(sig.toByteArray())
                    .joinToString("") { b -> "%02x".format(b) }
                fingerprint in expectedSha256Signatures
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to verify APK signature for $apkPath", e)
            false
        }
    }

    /**
     * Load local APK and extract package info.
     * Supports both single APK and split APK archives (apkm, apks, xapk).
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
                // Extract the representative base APK and read package info from it.
                // SplitApkInspector uses a smarter entry-selection algorithm than a naive
                // name search: base.apk → main/master → largest non-config → fallback.
                val extracted = SplitApkInspector.extractRepresentativeApk(
                    source = tempFile,
                    workspace = filesystem.uiTempDir
                )
                try {
                    extracted?.let {
                        context.packageManager.getPackageArchiveInfo(
                            it.file.absolutePath,
                            PackageManager.GET_META_DATA
                        )
                    }
                } finally {
                    extracted?.cleanup()
                }
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
}
