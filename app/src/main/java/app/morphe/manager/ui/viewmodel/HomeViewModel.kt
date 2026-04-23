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
import androidx.annotation.RequiresApi
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
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.filterGmsCore
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.sanitizeForPatcher
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
import java.net.URLEncoder.encode
import java.security.MessageDigest
import kotlin.collections.emptyList
import kotlin.time.Clock

/** Bundle update status for snackbar display. */
enum class BundleUpdateStatus {
    Updating, // Update in progress
    Success,  // Update completed successfully
    Warning,  // Patches may be outdated (on metered network, updates disabled)
    Error     // Error occurred (including no internet)
}

/** * Dialog state for unsupported version warning. */
data class UnsupportedVersionDialogState(
    val packageName: String,
    val version: String,
    val recommendedVersion: AppTarget?,
    val allCompatibleVersions: List<AppTarget> = emptyList(),
    /** True if the selected version is marked as experimental in the patch bundle. */
    val isExperimental: Boolean = false
)

/**
 * An [AppTarget] annotated with the bundle it originates from.
 * Used to group versions by bundle in the APK availability dialog.
 */
data class BundledAppTarget(
    val target: AppTarget,
    val bundleUid: Int,
    val bundleName: String
)

/** Dialog state for wrong package warning. */
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

/** Quick patch parameters. */
data class QuickPatchParams(
    val selectedApp: SelectedApp,
    val patches: PatchSelection,
    val options: Options
)

/** Saved APK information for display in APK selection dialog. */
data class SavedApkInfo(
    val fileName: String,
    val filePath: String,
    val version: String
)

/**
 * Combined home screen app state — emitted atomically so visible and hidden lists
 * are always in sync and never cause a transient empty-state flash.
 */
data class HomeAppState(
    val visible: List<HomeAppItem>,
    val hidden: List<HomeAppItem>
)

/**
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
    val availablePatches = patchBundleRepository.bundleInfoFlow.map { it.values.sumOf { bundle -> bundle.patches.size } }
    val bundleUpdateProgress = patchBundleRepository.bundleUpdateProgress
    private val contentResolver: ContentResolver = app.contentResolver

    /** Becomes true once the bundle repository has finished its initial DB load. */
    private val isBundlePipelineLoaded: StateFlow<Boolean> =
        patchBundleRepository.isBundlePipelineLoaded
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // App data resolver for getting app info from APK files
    private val appDataResolver by lazy {
        AppDataResolver(app, pm, originalApkRepository, installedAppRepository, filesystem)
    }

    /** Android 11 kills the app process after granting the "install apps" permission. */
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
    /** Snapshot of the selection at the moment the ExpertMode dialog was opened. Used by "Restore saved". */
    var expertModeInitialPatches by mutableStateOf<PatchSelection>(emptyMap())
        private set
    var expertModeOptions by mutableStateOf<Options>(emptyMap())
    // Patches that are new in the current bundle version relative to the last saved selection
    var expertModeNewPatches by mutableStateOf<Map<Int, Set<String>>>(emptyMap())

    /**
     * Set when ExpertModeDialog is opened from InstalledAppInfoDialog (repatch flow).
     * Called with the final patches/options when the user confirms, so the info dialog
     * can persist selections and navigate to the patcher without holding any patch state itself.
     * Null when the dialog is opened from the normal home-screen patching flow.
     */
    var onRepatchProceed: ((patches: PatchSelection, options: Options) -> Unit)? = null
    /** Package name captured for the repatch flow, used to save seen-patch snapshots. */
    private var repatchPackageName: String? = null

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
    var pendingCompatibleVersions by mutableStateOf<List<BundledAppTarget>>(emptyList())
    // Per-bundle recommended versions for multi-bundle display in ApkAvailabilityDialog
    var pendingRecommendedBundleVersions by mutableStateOf<Map<Int, AppTarget>>(emptyMap())
    // Version selected by the user in Dialog 1 for the APK search query. Defaults to pendingRecommendedVersion
    var pendingSelectedDownloadVersion by mutableStateOf<AppTarget?>(null)
    var pendingSelectedApp by mutableStateOf<SelectedApp?>(null)
    var resolvedDownloadUrl by mutableStateOf<String?>(null)
    var pendingSavedApkInfo by mutableStateOf<SavedApkInfo?>(null)

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Metered network dialog: shown when user tries to patch on mobile data with updates disabled
    var showMeteredPatchingDialog by mutableStateOf(false)
        private set

    // Low disk space warning dialog: shown when free storage is below the threshold before patching starts
    val lowDiskSpaceThresholdGb = 1f // Minimum free storage in GB required before patching
    var showLowDiskSpaceDialog by mutableStateOf(false)
        private set
    var lowDiskSpaceFreeGb by mutableFloatStateOf(0f)
        private set

    // Pending patching action captured when the guard dialog is shown
    private var pendingPatchAction: (suspend () -> Unit)? = null

    // Loading state for installed apps
    var installedAppsLoading by mutableStateOf(true)

    // Bundle data - reactive StateFlows derived directly from bundleInfoFlow
    val compatibleVersionsFlow: StateFlow<Map<String, List<BundledAppTarget>>> =
        patchBundleRepository.bundleInfoFlow
            .combine(patchBundleRepository.sources) { bundleInfo, sources ->
                val enabledSources = sources.filter { it.enabled }
                val enabledUids = enabledSources.map { it.uid }.toSet()
                val bundleNames = enabledSources.associate { it.uid to it.displayTitle }
                extractCompatibleVersions(bundleInfo, bundleNames, enabledUids)
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

            versionData.mapValues { (packageName, bundledTargets) ->
                val targets = bundledTargets.map { it.target }
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

    // Convenience accessors - read current value synchronously for non-reactive call sites
    val recommendedVersions: Map<String, AppTarget> get() = recommendedVersionsFlow.value
    val compatibleVersions: Map<String, List<BundledAppTarget>> get() = compatibleVersionsFlow.value

    /**
     * Per-bundle recommended version for each package.
     * Returns Map<PackageName, Map<BundleUid, AppTarget>> so the APK availability dialog
     * can show the correct "Recommended" badge independently for each bundle section.
     */
    val recommendedBundleVersionsFlow: StateFlow<Map<String, Map<Int, AppTarget>>> =
        combine(
            compatibleVersionsFlow,
            prefs.bundleExperimentalVersionsEnabled.flow,
            patchBundleRepository.bundleInfoFlow,
            patchBundleRepository.sources
        ) { versionData, experimentalEnabledUids, bundleInfo, sources ->
            val enabledUids = sources.filter { it.enabled }.map { it.uid }.toSet()
            // Per-bundle set of packages that have experimental mode enabled.
            // Key: bundleUid, Value: set of packageNames with experimental toggle on for that bundle
            val experimentalPackagesByBundle: Map<Int, Set<String>> = bundleInfo
                .filterKeys { it in enabledUids && it.toString() in experimentalEnabledUids }
                .mapValues { (_, info) ->
                    info.patches
                        .flatMap { it.compatiblePackages.orEmpty() }
                        .mapNotNull { it.packageName }
                        .toSet()
                }

            versionData.mapValues { (packageName, bundledTargets) ->
                bundledTargets
                    .groupBy { it.bundleUid }
                    .mapValues { (bundleUid, targets) ->
                        val appTargets = targets.map { it.target }
                        val preferExperimental = experimentalPackagesByBundle[bundleUid]
                            ?.contains(packageName) == true
                        if (preferExperimental) {
                            appTargets.firstOrNull { it.isExperimental } ?: appTargets.first()
                        } else {
                            appTargets.firstOrNull { !it.isExperimental } ?: appTargets.first()
                        }
                    }
            }
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val recommendedBundleVersions: Map<String, Map<Int, AppTarget>> get() = recommendedBundleVersionsFlow.value

    // Track available updates for installed apps
    private val _appUpdatesAvailable = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val appUpdatesAvailable: StateFlow<Map<String, Boolean>> = _appUpdatesAvailable.asStateFlow()

    // Track when at least one third-party source is enabled
    val hasThirdPartySource: StateFlow<Boolean> =
        patchBundleRepository.sources
            .map { sources -> sources.any { it.enabled && it.uid != DEFAULT_SOURCE_UID } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    /**
     * User chose to proceed with patching despite the split APK warning.
     * Resumes [processSelectedApp] with the split check skipped.
     */
    fun proceedWithSplitApk() {
        val app = pendingSelectedApp ?: return
        showSplitApkWarningDialog = false
        pendingSelectedApp = null
        viewModelScope.launch {
            processSelectedApp(app, skipSplitCheck = true)
        }
    }

    /**
     * User dismissed the split APK warning without proceeding.
     * Cleans up the temporary file if needed.
     */
    fun dismissSplitApkWarning() {
        val app = pendingSelectedApp
        showSplitApkWarningDialog = false
        pendingSelectedApp = null
        if (app is SelectedApp.Local && app.temporary) {
            app.file.delete()
        }
    }

    /**
     * User dismissed the unsupported version dialog.
     * Discards the pending selection and cleans up the temporary file if needed.
     */
    fun dismissUnsupportedVersionDialog() {
        showUnsupportedVersionDialog = null
        val app = pendingSelectedApp
        pendingSelectedApp = null
        if (app is SelectedApp.Local && app.temporary) {
            app.file.delete()
        }
    }

    /**
     * User chose to proceed patching with an unsupported app version.
     * Starts patching with allowIncompatible=true so version-incompatible patches are included.
     */
    fun proceedWithUnsupportedVersion() {
        showUnsupportedVersionDialog = null
        val app = pendingSelectedApp ?: return
        pendingSelectedApp = null
        viewModelScope.launch {
            if (rootInstaller.isDeviceRooted()) {
                requestPrePatchInstallerSelection(app, allowIncompatible = true)
            } else {
                usingMountInstall = false
                startPatchingWithApp(app, allowIncompatible = true)
            }
        }
    }

    /**
     * User dismissed the experimental version warning dialog.
     * Discards the pending selection and cleans up the temporary file if needed.
     */
    fun dismissExperimentalVersionDialog() {
        showExperimentalVersionDialog = null
        val app = pendingSelectedApp
        pendingSelectedApp = null
        if (app is SelectedApp.Local && app.temporary) {
            app.file.delete()
        }
    }

    /**
     * User acknowledged the experimental version warning and chose to proceed.
     * Starts patching with allowIncompatible=false - the version is supported,
     * just flagged as experimental in the patch bundle.
     */
    fun proceedWithExperimentalVersion() {
        showExperimentalVersionDialog = null
        val app = pendingSelectedApp ?: return
        pendingSelectedApp = null
        viewModelScope.launch {
            if (rootInstaller.isDeviceRooted()) {
                requestPrePatchInstallerSelection(app, allowIncompatible = false)
            } else {
                usingMountInstall = false
                startPatchingWithApp(app, allowIncompatible = false)
            }
        }
    }

    /**
     * User dismissed the wrong package dialog.
     */
    fun dismissWrongPackageDialog() {
        showWrongPackageDialog = null
    }

    /**
     * User dismissed the invalid signature dialog.
     * Discards the pending selection and cleans up the temporary file if needed.
     */
    fun dismissInvalidSignatureDialog() {
        showInvalidSignatureDialog = null
        val app = pendingSelectedApp
        pendingSelectedApp = null
        if (app is SelectedApp.Local && app.temporary) {
            app.file.delete()
        }
    }

    /**
     * User chose to proceed patching despite the signature mismatch warning.
     * Skips signature verification and resumes the patching flow.
     */
    fun proceedIgnoringSignature() {
        showInvalidSignatureDialog = null
        val app = pendingSelectedApp ?: return
        pendingSelectedApp = null
        viewModelScope.launch {
            processSelectedAppIgnoringSignature(app)
        }
    }

    // Callback for starting patch
    var onStartQuickPatch: ((QuickPatchParams) -> Unit)? = null

    init {
        triggerUpdateCheck()
        observeLoadingState()
        observeInstalledAppUpdates()
        observeDeletedAppsStatus()
        observeSnackbarState()
    }

    /**
     * Reactively updates [installedAppsLoading] based on bundle update progress and app list state.
     */
    private fun observeLoadingState() {
        viewModelScope.launch {
            combine(
                patchBundleRepository.bundleUpdateProgress,
                patchBundleRepository.sources,
                installedAppRepository.getAll(),
                availablePatches
            ) { progress, sources, installedApps, patchCount ->
                val isBundleUpdateInProgress =
                    progress?.result == PatchBundleRepository.BundleUpdateResult.None
                val hasEnabledSources = sources.any { it.enabled }
                // Guard: sources list is empty on the very first emission before the DB is read.
                // Treat that transient state as "still loading" so we never flash the empty-state
                // UI before the real bundle configuration is known.
                val sourcesInitialized = sources.isNotEmpty() || patchCount > 0
                // If no sources are enabled (and we know the DB has been read), there is nothing
                // to load - this is a valid terminal state, not a loading state.
                val hasLoadedData = sourcesInitialized &&
                        (!hasEnabledSources || installedApps.isNotEmpty() || patchCount > 0)
                isBundleUpdateInProgress || !hasLoadedData
            }
                .distinctUntilChanged()
                .collect { loading ->
                    installedAppsLoading = loading
                }
        }
    }

    /**
     * Reactively checks installed apps for available bundle updates.
     * Triggered on initial load and after each completed bundle update.
     */
    private fun observeInstalledAppUpdates() {
        // Check on initial load and when sources or installed apps change
        viewModelScope.launch {
            combine(
                installedAppRepository.getAll(),
                patchBundleRepository.sources,
                patchBundleRepository.bundleUpdateProgress
            ) { installedApps, sources, progress ->
                // Only trigger after a completed update (Success/NoUpdates) or on initial load
                // (progress == null). Never trigger mid-update (None) to avoid checking against
                // incomplete bundle data.
                val updateCompleted = progress == null ||
                        progress.result == PatchBundleRepository.BundleUpdateResult.Success ||
                        progress.result == PatchBundleRepository.BundleUpdateResult.NoUpdates
                Triple(installedApps, sources, updateCompleted)
            }
                .filter { (installedApps, sources, updateCompleted) ->
                    installedApps.isNotEmpty() && sources.isNotEmpty() && updateCompleted
                }
                .conflate() // drop intermediate emissions, process only the latest
                .collect { (installedApps, _, _) ->
                    checkInstalledAppsForUpdates(installedApps)
                }
        }
    }

    /**
     * Reactively keeps [appsDeletedStatus] up to date when the installed apps list changes.
     */
    private fun observeDeletedAppsStatus() {
        viewModelScope.launch {
            installedAppRepository.getAll()
                .filter { it.isNotEmpty() }
                .collect { installedApps -> updateDeletedAppsStatus(installedApps) }
        }
    }

    /**
     * Reactively maps bundle update progress to snackbar visibility and status.
     */
    private fun observeSnackbarState() {
        viewModelScope.launch {
            patchBundleRepository.bundleUpdateProgress.collect { progress ->
                if (progress == null) {
                    showBundleUpdateSnackbar = false
                    return@collect
                }
                showBundleUpdateSnackbar = true
                snackbarStatus = when (progress.result) {
                    PatchBundleRepository.BundleUpdateResult.Success,
                    PatchBundleRepository.BundleUpdateResult.NoUpdates -> BundleUpdateStatus.Success
                    PatchBundleRepository.BundleUpdateResult.NoInternet,
                    PatchBundleRepository.BundleUpdateResult.Error -> BundleUpdateStatus.Error
                    PatchBundleRepository.BundleUpdateResult.None -> BundleUpdateStatus.Updating
                    PatchBundleRepository.BundleUpdateResult.SkippedMetered -> BundleUpdateStatus.Warning
                }
            }
        }
    }

    /** Pull-to-refresh state. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Triggers a manual refresh: updates bundles and checks for manager updates.
     * Guard against double-trigger if user swipes while refresh is in progress.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                patchBundleRepository.updateCheck()
                checkForManagerUpdates()
                delay(500)
            } finally {
                _isRefreshing.value = false
            }
        }
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
        // Check available storage first - low disk space is the most common cause of
        // cryptic "file not found" errors and corrupt output APKs during patching.
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

                // Bundle is newer - refine with changelog if available.
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
     * 1. [KnownApps.fallbackName] - static registry (offline, reliable).
     * 2. [PM] label - system label for any installed app not in the registry.
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
                // Ignore cancellation
            }
        }

        try {
            val result = block()
            withContext(Dispatchers.Main) {
                app.toast(app.getString(R.string.imported_successfully))
            }
            result
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
                    // Provider may not support persistable permissions; fall back to transient grant
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
                            // Ignore if provider revoked or already released
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
     * Derived from [bundleAppMetadataFlow] keys - no need to re-iterate all patches.
     */
    val patchablePackagesFlow: StateFlow<Set<String>> =
        bundleAppMetadataFlow
            .map { it.keys }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Combined flow that produces the sorted list of home app items.
     *
     * Sorting order by display name:
     * 1. Patched (installed) apps first
     * 2. Non-patched apps
     * Hidden apps are excluded.
     */
    val homeAppState: StateFlow<HomeAppState?> = combine(
        patchablePackagesFlow,
        homeAppButtonPrefs.hiddenPackages,
        installedAppRepository.getAll(),
        _appUpdatesAvailable,
        bundleAppMetadataFlow
    ) { packages, hiddenPackages, installedApps, updatesMap, metadata ->
        if (!isBundlePipelineLoaded.value) return@combine null

        val installedMap = installedApps.associateBy { it.originalPackageName }

        suspend fun buildItem(packageName: String): HomeAppItem {
            val installedApp = installedMap[packageName]
            val bundleMeta = metadata[packageName]
            val knownApp = KnownApps.fromPackage(packageName)
            val gradientColors = bundleMeta?.gradientColors ?: KnownApps.DEFAULT_COLORS
            val resolvedData = appDataResolver.resolveAppData(
                packageName = packageName,
                preferredSource = AppDataSource.PATCHED_APK
            )
            val displayName = resolvedData.displayName.takeIf {
                resolvedData.source == AppDataSource.INSTALLED || resolvedData.source == AppDataSource.PATCHED_APK
            } ?: bundleMeta?.displayName ?: KnownApps.getAppName(packageName)
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
            val hasUpdate = installedApp?.let {
                updatesMap[it.currentPackageName] == true
            } == true
            return HomeAppItem(
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

        // Active bundle packages filtered to those in patchablePackages
        val activeHidden = hiddenPackages.filter { it in packages }

        val visiblePackages = packages.filter { it !in hiddenPackages }
        val visibleItems = ArrayList<HomeAppItem>(visiblePackages.size)
        for (pkg in visiblePackages) visibleItems.add(buildItem(pkg))
        val visible = visibleItems.sortedWith(
            compareByDescending<HomeAppItem> { it.installedApp != null }
                .thenByDescending { it.isPinnedByDefault }
                .thenByDescending { it.packageInfo != null }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        )

        val hiddenItems = ArrayList<HomeAppItem>(activeHidden.size)
        for (pkg in activeHidden) hiddenItems.add(buildItem(pkg))

        HomeAppState(visible = visible, hidden = hiddenItems)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Marks the swipe gesture hint as shown so it is never replayed.
     */
    fun markSwipeGestureHintShown() {
        viewModelScope.launch {
            prefs.swipeGestureHintShown.update(true)
        }
    }

    /**
     * Snapshot of all bundle info (including disabled) as a [StateFlow] for synchronous reads.
     * Used by [getPatchesForPackage] which is called from Compose (non-suspend context).
     */
    private val allBundlesInfoState: StateFlow<Map<Int, PatchBundleInfo.Global>> =
        patchBundleRepository.allBundlesInfoFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Returns all patches available for [packageName] across all enabled bundles.
     * Groups them as Map<BundleUid, List<PatchInfo>> for the swipe-right patches dialog.
     */
    fun getPatchesForPackage(packageName: String): Map<Int, List<PatchInfo>> {
        val bundleInfo = allBundlesInfoState.value
        return buildMap {
            bundleInfo
                .filter { (_, info) -> info.enabled }
                .forEach { (uid, info) ->
                    val patches = info.patches.filter { patch ->
                        patch.compatiblePackages == null ||
                                patch.compatiblePackages.any { it.packageName == packageName }
                    }
                    if (patches.isNotEmpty()) put(uid, patches)
                }
        }
    }

    /**
     * Returns the display name of the bundle with [uid], or null.
     */
    fun getBundleDisplayName(uid: Int): String? =
        allBundlesInfoState.value[uid]?.name

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
            ?.filter { it.target.isExperimental }
            ?.mapNotNull { it.target.version }
            ?.toSet()
            ?: emptySet()

    /**
     * Whether the "Other apps" button should be visible.
     * Hidden while no apps are loaded; shown in expert mode or when a third-party source is active.
     */
    val showOtherAppsButton: StateFlow<Boolean> =
        combine(
            homeAppState,
            hasThirdPartySource,
            prefs.useExpertMode.flow
        ) { state, thirdParty, expertMode ->
            if (state?.visible.isNullOrEmpty()) false
            else expertMode || thirdParty
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Whether the search button should be visible.
     * Shown when there are more than 4 app buttons or a third-party source is active.
     */
    val showSearchButton: StateFlow<Boolean> =
        combine(
            homeAppState,
            hasThirdPartySource
        ) { state, thirdParty ->
            (state?.visible?.size ?: 0) > 4 || thirdParty
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
        pendingAppName = bundleAppMetadataFlow.value[packageName]?.displayName
            ?: KnownApps.getAppName(packageName)
        pendingRecommendedVersion = recommendedVersions[packageName]
        pendingCompatibleVersions = compatibleVersions[packageName] ?: emptyList()
        pendingRecommendedBundleVersions = recommendedBundleVersions[packageName] ?: emptyMap()
        pendingSelectedDownloadVersion = pendingRecommendedVersion

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
            val result = withContext(Dispatchers.IO) {
                loadLocalApk(app, uri)
            }

            when (result) {
                is ApkLoadResult.Success -> processSelectedApp(result.app)
                is ApkLoadResult.Unreadable -> app.toast(app.getString(R.string.home_invalid_apk_unreadable))
                is ApkLoadResult.NotAnApk -> app.toast(app.getString(R.string.home_invalid_apk_not_an_apk))
                is ApkLoadResult.IoError -> app.toast(app.getString(R.string.home_invalid_apk_io_error))
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
                // The saved file is a merged mono-APK signed with our keystore.
                // Skip signature verification to avoid a false "invalid signature" dialog
                processSelectedAppIgnoringSignature(selectedApp)
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
    private suspend fun processSelectedApp(
        selectedApp: SelectedApp,
        skipSplitCheck: Boolean = false
    ) {
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

        // Warn when the selected file is a split APK while the bundle requires a full APK.
        // This must happen BEFORE signature verification - split archives (.apkm/.apks/.xapk)
        // are not valid APKs so PackageManager cannot read their signature, which would cause
        // a false "invalid signature" dialog instead of the correct "split APK" warning.
        if (selectedApp is SelectedApp.Local && !skipSplitCheck) {
            val requiredApkFileType = bundleAppMetadataFlow.value[selectedApp.packageName]?.apkFileType

            val isSplitFile = SplitApkPreparer.isSplitArchive(selectedApp.file)

            if (isSplitFile && requiredApkFileType?.isApk == true && requiredApkFileType.isRequired) {
                pendingSelectedApp = selectedApp
                showSplitApkWarningDialog = true
                cleanupPendingData(keepSelectedApp = true)
                return
            }

            // Verify APK signature against the expected signatures declared in the patch bundle.
            // GET_SIGNING_CERTIFICATES (API 28+) is required for reliable archive signature reads.
            // On Android 8–10 the legacy GET_SIGNATURES path cannot read signatures from
            // archive files correctly, so we skip verification there to avoid false-blocking users.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                val expectedSignatures = bundleAppMetadataFlow.value[selectedApp.packageName]?.signatures
                if (!expectedSignatures.isNullOrEmpty()) {
                    val signatureMatch = withContext(Dispatchers.IO) {
                        if (isSplitFile) {
                            val extracted = SplitApkInspector.extractRepresentativeApk(
                                source = selectedApp.file,
                                workspace = filesystem.uiTempDir
                            )
                            if (extracted == null) {
                                // Cannot extract base APK - skip verification rather than false-block
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
        // Universal patches do not suppress this warning - the user should still be informed
        // that the APK version is not officially supported.
        // Note: experimental versions are compatible (they pass the version check) but show an
        // additional "Experimental" badge in the warning dialog.
        val versionMismatch = !hasCompatible && hasIncompatible
        // Experimental check is independent - a version can be experimental AND compatible
        val isVersionExperimental = enabledBundles.any { it.isVersionExperimental }

        // Check if the user has enabled experimental-version mode for this package's bundle
        val experimentalEnabledUids = prefs.bundleExperimentalVersionsEnabled.getBlocking()
        val isExperimentalModeEnabled = enabledBundles.any { bundle ->
            bundle.uid.toString() in experimentalEnabledUids
        }

        if (versionMismatch && !allowIncompatible) {
            val recommendedVersion = recommendedVersions[selectedApp.packageName]
            val allVersions = compatibleVersions[selectedApp.packageName]?.map { it.target } ?: emptyList()
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
            val allVersions = compatibleVersions[selectedApp.packageName]?.map { it.target } ?: emptyList()
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
                }

                // Merge newly added patches (present in bundle but absent from saved selection)
                // into the validated selection, respecting each patch's include=true default.
                // This runs after validation so removed patches never sneak back in.
                val mergedPatches = buildMap {
                    // Start from the validated (post-removal) selection
                    putAll(validatedPatches)
                    allBundles.forEach { bundle ->
                        // Use seen-patch snapshot to determine what's genuinely new.
                        // Comparing against savedForBundle (only selected patches) would
                        // incorrectly re-enable patches the user explicitly deselected.
                        val seenForBundle = withContext(Dispatchers.IO) {
                            patchSelectionRepository.getSeenPatches(selectedApp.packageName, bundle.uid)
                        }
                        val knownNames = seenForBundle
                            ?: savedSelections[bundle.uid] // fallback for first run (no snapshot yet)
                            ?: return@forEach
                        val currentPatchNames = bundle.patches.map { it.name }.toSet()
                        val newPatchNames = currentPatchNames - knownNames
                        if (newPatchNames.isEmpty()) return@forEach

                        // Among the genuinely new patches, auto-select those with include=true
                        val newDefaultEnabled = bundle.patches
                            .filter { it.name in newPatchNames && it.include }
                            .mapTo(mutableSetOf()) { it.name }

                        if (newDefaultEnabled.isNotEmpty()) {
                            val existing = getOrDefault(bundle.uid, emptySet())
                            put(bundle.uid, existing + newDefaultEnabled)
                        }
                    }
                }

                mergedPatches
            } else {
                // No saved selections - use default for all current bundles
                allBundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }
            }.applyGmsCoreFilter()

            // Compute new patches map for the dialog to highlight.
            // Only populated when a previous selection exists - on first run there is nothing
            // to compare against so we keep it empty to avoid false "New" badges
            // A patch is genuinely "new" if it was absent from the seen-patches snapshot
            // saved at the end of the previous patching session. Comparing against savedSelections
            // (which contains only *selected* patches) would incorrectly flag deselected patches
            // as new on every subsequent open.
            val newPatchesMap: Map<Int, Set<String>> = if (savedSelections.isNotEmpty()) {
                buildMap {
                    allBundles.forEach { bundle ->
                        val seenForBundle = withContext(Dispatchers.IO) {
                            patchSelectionRepository.getSeenPatches(selectedApp.packageName, bundle.uid)
                        }
                        // No snapshot yet → first time opening expert mode for this package,
                        // nothing to flag as new.
                        val seen = seenForBundle ?: return@forEach
                        val currentPatchNames = bundle.patches.map { it.name }.toSet()
                        val newForBundle = currentPatchNames - seen
                        if (newForBundle.isNotEmpty()) put(bundle.uid, newForBundle)
                    }
                }
            } else {
                emptyMap()
            }

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
            patches.toMutableMap().also { expertModePatches = it; expertModeInitialPatches = it }
            expertModeOptions = validatedOptions.toMutableMap()
            expertModeNewPatches = newPatchesMap
            showExpertModeDialog = true
        } else {
            // Simple Mode: check if this is a main app or "other app"
            // Prefer the default bundle if it is enabled and has patches for this package.
            // If the default bundle is disabled or has no patches, fall through to use
            // all enabled bundles - this allows third-party bundles to work for known apps too.
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
                    savedSelections.toMutableMap().also { expertModePatches = it; expertModeInitialPatches = it }
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
        pendingRecommendedBundleVersions = emptyMap()
        pendingSelectedDownloadVersion = null
        resolvedDownloadUrl = null
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
    }

    /**
     * All patches per bundle with their enabled state, sorted for display.
     * Recomputed whenever [expertModeBundles] or [expertModePatches] change.
     * Each bundle entry contains (PatchInfo, isEnabled) pairs sorted alphabetically -
     * the final per-section ordering (new patches first) is applied in the UI layer.
     */
    val expertModeAllPatchesInfo: List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>
        get() = expertModeBundles.map { bundle ->
            val selected = expertModePatches[bundle.uid] ?: emptySet()
            val patches = bundle.patchSequence(true)
                .map { patch -> patch to (patch.name in selected) }
                .sortedBy { (patch, _) -> patch.name }
                .toList()
            bundle to patches
        }.filter { it.second.isNotEmpty() }
            .sortedByDescending { (bundle, _) -> bundle.compatible.size }

    /** Total number of currently selected patches across all bundles. */
    val expertModeTotalSelectedCount: Int
        get() = expertModePatches.values.sumOf { it.size }

    /** Total number of available patches across all bundles. */
    val expertModeTotalPatchesCount: Int
        get() = expertModeAllPatchesInfo.sumOf { it.second.size }

    /** True when patches from more than one bundle are selected (triggers warning on proceed). */
    val expertModeHasMultipleBundles: Boolean
        get() = expertModePatches.count { (_, patches) -> patches.isNotEmpty() } > 1

    /**
     * Toggle patch in expert mode.
     * Supports adding patches from bundles not yet in the selection.
     */
    fun togglePatchInExpertMode(bundleUid: Int, patchName: String) {
        expertModePatches = expertModePatches.togglePatch(bundleUid, patchName)
    }

    /**
     * Select all given patches for a bundle.
     * Only adds patches that are not already selected.
     */
    fun expertModeSelectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val current = expertModePatches.toMutableMap()
        val set = current[bundleUid]?.toMutableSet() ?: mutableSetOf()
        patches.forEach { (patch, enabled) -> if (!enabled) set.add(patch.name) }
        current[bundleUid] = set
        expertModePatches = current
    }

    /**
     * Deselect all given patches for a bundle.
     * Removes the bundle entry entirely if nothing remains selected.
     */
    fun expertModeDeselectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val current = expertModePatches.toMutableMap()
        val set = current[bundleUid]?.toMutableSet() ?: mutableSetOf()
        patches.forEach { (patch, enabled) -> if (enabled) set.remove(patch.name) }
        if (set.isEmpty()) current.remove(bundleUid) else current[bundleUid] = set
        expertModePatches = current
    }

    /**
     * Reset a bundle's selection to the default (include=true) patches.
     * [allPatches] is the full unfiltered list for that bundle so defaults
     * are computed from the complete set, not just search results.
     */
    fun expertModeResetToDefault(bundleUid: Int, allPatches: List<Pair<PatchInfo, Boolean>>) {
        val defaults = allPatches
            .filter { (patch, _) -> patch.include }
            .mapTo(mutableSetOf()) { (patch, _) -> patch.name }
        val current = expertModePatches.toMutableMap()
        if (defaults.isEmpty()) current.remove(bundleUid) else current[bundleUid] = defaults
        expertModePatches = current
    }

    /**
     * Restores the DB-persisted patch selection for a bundle in expert mode.
     * No-op if there is no saved selection for the given bundle.
     */
    fun expertModeRestoreSaved(bundleUid: Int) {
        val savedForBundle = expertModeInitialPatches[bundleUid] ?: return
        val current = expertModePatches.toMutableMap()
        if (savedForBundle.isEmpty()) current.remove(bundleUid) else current[bundleUid] = savedForBundle
        expertModePatches = current
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
        expertModeInitialPatches = emptyMap()
        expertModeOptions = emptyMap()
        expertModeNewPatches = emptyMap()
        onRepatchProceed = null
        repatchPackageName = null
    }

    /**
     * Called when the user confirms the ExpertModeDialog.
     * Routes to the repatch flow (via [onRepatchProceed]) or the normal patching flow
     * (via [proceedWithPatching]) depending on how the dialog was opened.
     * Saving options and cleaning up state is handled here so HomeDialogs stays thin.
     */
    fun proceedExpertMode() {
        val finalPatches = expertModePatches
        val finalOptions = expertModeOptions
        // Strip UI-only empty strings (fields cleared via ✕) so the patcher engine
        // receives null / no key for those options and falls back to its own default,
        // rather than receiving a literal empty string.
        val patcherOptions = finalOptions.sanitizeForPatcher()
        val repatchCallback = onRepatchProceed
        val selectedApp = expertModeSelectedApp

        showExpertModeDialog = false

        viewModelScope.launch(Dispatchers.IO) {
            if (repatchCallback != null) {
                // Repatch flow: delegate fully to the callback set by InstalledAppInfoViewModel.
                // Persisting selections/options is the callback's responsibility.
                // Snapshot seen patches before cleanup clears expertModeBundles.
                val pkgName = repatchPackageName
                if (pkgName != null) {
                    expertModeBundles.forEach { bundle ->
                        patchSelectionRepository.saveSeenPatches(
                            packageName = pkgName,
                            bundleUid = bundle.uid,
                            patchNames = bundle.patches.map { it.name }.toSet()
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    repatchCallback(finalPatches, patcherOptions)
                    cleanupExpertModeData()
                }
            } else if (selectedApp != null) {
                // Persist the final selection (already validated + merged with new patches)
                patchSelectionRepository.updateSelection(
                    packageName = selectedApp.packageName,
                    selection = finalPatches
                )
                saveOptions(selectedApp.packageName, finalOptions)
                // Snapshot all bundle patch names so next open can detect genuinely new patches.
                expertModeBundles.forEach { bundle ->
                    patchSelectionRepository.saveSeenPatches(
                        packageName = selectedApp.packageName,
                        bundleUid = bundle.uid,
                        patchNames = bundle.patches.map { it.name }.toSet()
                    )
                }
                withContext(Dispatchers.Main) {
                    proceedWithPatching(selectedApp, finalPatches, patcherOptions)
                    cleanupExpertModeData()
                }
            }
        }
    }

    /**
     * Resolve download redirect.
     */
    fun resolveDownloadRedirect() {
        suspend fun resolveUrlRedirect(url: String): String {
            val location = morpheAPI.resolveRedirect(url)
            return when {
                location == null -> {
                    Log.w(tag, "No redirect location for: $url")
                    getApiOfflineWebSearchUrl()
                }
                else -> {
                    Log.i(tag, "Result: $location")
                    location
                }
            }
        }

        // Use the version selected by the user in Dialog 1; fall back to recommended
        val versionForSearch = pendingSelectedDownloadVersion ?: pendingRecommendedVersion
        val escapedVersion = versionForSearch?.version ?: "any"
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

        // Use the version selected by the user in Dialog 1; fall back to recommended
        val versionForSearch = pendingSelectedDownloadVersion ?: pendingRecommendedVersion
        val versionPart = versionForSearch?.version?.let { "\"$it\"" } ?: ""
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
        pendingRecommendedBundleVersions = emptyMap()
        pendingSelectedDownloadVersion = null
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
     * Returns a map of package name to a list of [BundledAppTarget] - versions are grouped by
     * bundle (ordered by bundle display name) and sorted newest→oldest within each bundle.
     * Versions are NOT deduplicated across bundles so the UI can show per-bundle sections.
     */
    private fun extractCompatibleVersions(
        bundleInfo: Map<Int, PatchBundleInfo>,
        bundleNames: Map<Int, String>,
        enabledBundleUids: Set<Int> = emptySet()
    ): Map<String, List<BundledAppTarget>> {
        // packageName → bundleUid → version → AppTarget
        val targetsByPackage = mutableMapOf<String, MutableMap<Int, MutableMap<String, AppTarget>>>()

        bundleInfo.forEach { (bundleUid, info) ->
            if (enabledBundleUids.isNotEmpty() && bundleUid !in enabledBundleUids) return@forEach

            info.patches.forEach { patch ->
                patch.compatiblePackages?.forEach { pkg ->
                    val packageName = pkg.packageName ?: return@forEach
                    val bundleMap = targetsByPackage
                        .getOrPut(packageName) { mutableMapOf() }
                        .getOrPut(bundleUid) { mutableMapOf() }

                    pkg.versions?.forEach { version ->
                        val isExperimental = pkg.experimentalVersions?.contains(version) == true
                        // If a version appears in multiple patches of the same bundle, prefer stable
                        if (version !in bundleMap || !isExperimental) {
                            val description = pkg.versionDescriptions?.get(version)
                            bundleMap[version] = AppTarget(
                                version = version,
                                isExperimental = isExperimental,
                                description = description
                            )
                        }
                    }
                }
            }
        }

        // Flatten: bundles ordered by display name, versions newest→oldest within each bundle
        return targetsByPackage
            .mapValues { (_, byBundle) ->
                byBundle.entries
                    .sortedWith(compareBy({ it.key != DEFAULT_SOURCE_UID }, { bundleNames[it.key] ?: "" }))
                    .flatMap { (uid, versionMap) ->
                        versionMap.values
                            .sortedDescending()
                            .map { target ->
                                BundledAppTarget(
                                    target = target,
                                    bundleUid = uid,
                                    bundleName = bundleNames[uid] ?: "Bundle $uid"
                                )
                            }
                    }
            }
            .filterValues { it.isNotEmpty() }
    }

    /**
     * Verify that the APK at [apkPath] is signed with one of the [expectedSha256Signatures].
     *
     * Returns true if at least one certificate fingerprint matches.
     * An empty [expectedSha256Signatures] is treated as "no verification required" → true.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun verifyApkSignature(apkPath: String, expectedSha256Signatures: Set<String>): Boolean {
        if (expectedSha256Signatures.isEmpty()) return true
        return try {
            val info = app.packageManager.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_SIGNING_CERTIFICATES
            ) ?: return false

            info.applicationInfo?.apply {
                sourceDir = apkPath
                publicSourceDir = apkPath
            }

            val signingInfo = info.signingInfo ?: return false
            val signatures = if (signingInfo.hasMultipleSigners())
                signingInfo.apkContentsSigners
            else
                signingInfo.signingCertificateHistory

            val digest = MessageDigest.getInstance("SHA-256")
            signatures.any { sig ->
                // Reset before each use - MessageDigest is stateful
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
     * Clean up any pending temporary APK when the ViewModel is destroyed.
     * This handles the edge case where the user navigates away or the system destroys
     * the ViewModel while a temporary APK file is still held in pendingSelectedApp.
     */
    override fun onCleared() {
        super.onCleared()
        val pending = pendingSelectedApp
        if (pending is SelectedApp.Local && pending.temporary) {
            pending.file.delete()
        }
    }

    /**
     * Load local APK and extract package info.
     * Supports both single APK and split APK archives (apkm, apks, xapk).
     *
     * The file is stored in [Filesystem.uiTempDir] (app_ui_ephemeral).
     * CacheDir can be cleared by Android at any time - even while the app is running and
     * patching is in progress - which would cause a FileNotFoundException mid-patch.
     * uiTempDir uses getDir() which is part of the app's private files and is never
     * cleared by the system automatically.
     */
    private suspend fun loadLocalApk(
        context: Context,
        uri: Uri
    ): ApkLoadResult = withContext(Dispatchers.IO) {
        try {
            // Copy file to uiTempDir with original extension detection
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else null
            } ?: "temp_${System.currentTimeMillis()}"

            val extension = fileName.substringAfterLast('.', "apk").lowercase()
            val tempFile = filesystem.uiTempDir.resolve("temp_apk_${System.currentTimeMillis()}.$extension")

            // openInputStream can return null when the provider is unavailable
            // e.g. Samsung External Storage restricted by Battery Optimization
            val bytesCopied = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (bytesCopied == null || bytesCopied == 0L) {
                tempFile.delete()
                return@withContext ApkLoadResult.Unreadable
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
                    extracted?.let { pm.getPackageInfo(it.file) }
                } finally {
                    extracted?.cleanup()
                }
            } else {
                // Regular APK - parse directly
                pm.getPackageInfo(tempFile)
            }

            if (packageInfo == null) {
                tempFile.delete()
                return@withContext ApkLoadResult.NotAnApk
            }

            ApkLoadResult.Success(
                SelectedApp.Local(
                    packageName = packageInfo.packageName,
                    version = packageInfo.versionName ?: "unknown",
                    file = tempFile,
                    temporary = true
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to load APK", e)
            ApkLoadResult.IoError
        }
    }
}

/** Result of attempting to load a local APK file. */
private sealed interface ApkLoadResult {
    /** File was read and parsed successfully. */
    data class Success(val app: SelectedApp.Local) : ApkLoadResult
    /** File could not be read - provider returned null stream or zero bytes. */
    data object Unreadable : ApkLoadResult
    /** File was read but is not a valid APK/split archive. */
    data object NotAnApk : ApkLoadResult
    /** An unexpected IO or system exception occurred while copying or parsing. */
    data object IoError : ApkLoadResult
}
