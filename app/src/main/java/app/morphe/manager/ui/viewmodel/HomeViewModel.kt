/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.apk.*
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.avatarUrls
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.UninstallCancelledException
import app.morphe.manager.domain.manager.*
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.model.displayedHomePackageInfo
import app.morphe.manager.ui.model.trackedInstallPresentation
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.util.*
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability
import app.morphe.manager.util.PatchSelectionUtils.bulkEnableHoldsUniversal
import app.morphe.manager.util.PatchSelectionUtils.bulkEnablePatches
import app.morphe.manager.util.PatchSelectionUtils.filterGmsCore
import app.morphe.manager.util.PatchSelectionUtils.resetOptionsForPatch
import app.morphe.manager.util.PatchSelectionUtils.sanitizeForPatcher
import app.morphe.manager.util.PatchSelectionUtils.togglePatch
import app.morphe.manager.util.PatchSelectionUtils.updateOption
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.InstallerType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Bundle update status for snackbar display. */
enum class BundleUpdateStatus {
    Updating, // Update in progress
    Success,  // Update completed successfully
    Warning,  // Patches may be outdated (on metered network, updates disabled)
    Error     // Error occurred (including no internet)
}

/** Keys whose evidence was added, removed, or replaced between two snapshots. */
internal fun <K, V> changedMapKeys(previous: Map<K, V>, current: Map<K, V>): Set<K> =
    (previous.keys + current.keys).filterTo(mutableSetOf()) { previous[it] != current[it] }

/** * Dialog state for unsupported version warning. */
data class UnsupportedVersionDialogState(
    val packageName: String,
    val version: String,
    val versionCode: Long? = null,
    val recommendedVersion: AppTarget?,
    val compatibleVersionNames: List<String> = emptyList(),
    val compatibleVersionDescriptions: Map<String, String> = emptyMap(),
    val compatibleVersionCodes: Map<String, Set<Int>> = emptyMap(),
    /** True if the selected version is marked as experimental in the patch bundle. */
    val isExperimental: Boolean = false
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

/** An installed app entry shown in the universal-patch app picker. */
data class InstalledAppPickerItem(
    val packageName: String,
    val label: String,
    val packageInfo: PackageInfo,
    val isSystemApp: Boolean,
    val info: InstalledApkInfo
)

/**
 * Combined home screen app state - emitted atomically so visible and hidden lists
 * are always in sync and never cause a transient empty-state flash.
 */
data class HomeAppState(
    val visible: List<HomeAppItem>,
    val hidden: List<HomeAppItem>,
    val sortMode: HomeAppSortMode,
    val categoryState: HomeAppCategoryState,
    val categoryViewMode: HomeAppCategoryViewMode,
    val showCategoryViewSwitcher: Boolean,
    val sourceGroups: List<HomeAppSourceGroup>
)

/**
 * Apps grouped by the enabled patch source that declares them. A package can appear in
 * multiple source groups when multiple sources declare compatible patches for it.
 *
 * The default (Morphe) source is treated specially: it can never be collapsed by the user
 * ([collapsible] is false and [isDefault] is true), so its group always stays open.
 */
data class HomeAppSourceGroup(
    val uid: Int,
    val name: String,
    val packageNames: Set<String>,
    val packageOrder: List<String>,
    val collapsed: Boolean,
    val avatarUrl: String?,
    val fallbackAvatarUrl: String?
) {
    val isDefault: Boolean get() = uid == DEFAULT_SOURCE_UID
    val collapsible: Boolean get() = !isDefault
}

private data class HomePrefs(
    val hiddenPackages: Set<String>,
    val customOrder: List<String>,
    val sourceOrders: Map<Int, List<String>>,
    val sortMode: HomeAppSortMode,
    val categoryState: HomeAppCategoryState,
    val categoryViewMode: HomeAppCategoryViewMode,
    val showCategoryViewSwitcher: Boolean,
    val expandedSourceGroups: Set<Int>
)

private data class HomeCategoryPrefs(
    val categoryState: HomeAppCategoryState,
    val categoryViewMode: HomeAppCategoryViewMode,
    val showCategoryViewSwitcher: Boolean,
    val expandedSourceGroups: Set<Int>
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
    private val managerUpdateRepository: ManagerUpdateRepository,
    private val networkInfo: NetworkInfo,
    val prefs: PreferencesManager,
    private val pm: PM,
    val rootInstaller: RootInstaller,
    private val installerManager: InstallerManager,
    private val filesystem: Filesystem,
    private val homeAppButtonPrefs: HomeAppButtonPreferences,
    private val appDataResolver: AppDataResolver,
    private val batchPatchCoordinator: BatchPatchCoordinator,
    private val downloadUrlResolver: DownloadUrlResolver,
    versionCatalog: AppVersionCatalog,
    private val localApkSources: LocalApkSources
) : ViewModel() {
    val availablePatches = patchBundleRepository.bundleInfoFlow.map { it.values.sumOf { bundle -> bundle.patches.size } }
    val bundleUpdateProgress = patchBundleRepository.bundleUpdateProgress
    private val contentResolver: ContentResolver = app.contentResolver

    /** Becomes true once the bundle repository has finished its initial DB load. */
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

    // Installed App Info dialog state
    var showInstalledAppInfoDialog: String? by mutableStateOf(null)
        private set
    var installedAppDialogToken by mutableIntStateOf(0)
        private set

    fun openInstalledAppInfo(packageName: String) {
        showInstalledAppInfoDialog = packageName
        installedAppDialogToken++
    }

    fun dismissInstalledAppInfo() {
        showInstalledAppInfoDialog = null
    }

    // Deep link: pending bundle to add via confirmation dialog
    var deepLinkPendingBundle by mutableStateOf<DeepLinkBundle?>(null)
        private set

    data class DeepLinkBundle(val url: String, val name: String?)

    // .mpp file opened from file manager: pending confirmation dialog
    var pendingMppUri by mutableStateOf<Uri?>(null)
    var pendingMppFileName by mutableStateOf<String?>(null)
    var pendingMppManifest by mutableStateOf<MppManifest?>(null)

    fun setPendingMpp(uri: Uri) {
        dismissOpenDialogs()
        pendingMppUri = uri
        pendingMppFileName = uri.displayName(contentResolver)
        pendingMppManifest = null
        viewModelScope.launch(Dispatchers.IO) {
            pendingMppManifest = uri.readMppManifest(contentResolver)
        }
    }

    fun confirmMppImport() {
        val uri = pendingMppUri ?: return
        pendingMppUri = null
        pendingMppFileName = null
        pendingMppManifest = null
        createLocalSource(uri)
    }

    fun dismissMppImport() {
        pendingMppUri = null
        pendingMppFileName = null
        pendingMppManifest = null
    }

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
    // Bundle and selection left behind by the last "Enable all". Universal patches are applied
    // only while this still matches the live selection, so any other edit disarms them again
    private var expertModeUniversalArmedFor by mutableStateOf<Pair<Int, Set<String>>?>(null)

    /** Target bundle uid for the in-flight copy-from-another-bundle picker; null while the picker is closed. */
    var expertModeCopyTargetBundleUid by mutableStateOf<Int?>(null)
        private set
    /** Loaded candidates for the picker; null while the initial load is in progress. */
    var expertModeCopyCandidates by mutableStateOf<List<CopySelectionCandidate>?>(null)
        private set

    // Bundle file selection
    var selectedBundleUri by mutableStateOf<Uri?>(null)
    var selectedBundlePath by mutableStateOf<String?>(null)

    /** Local source waiting for a replacement file, so the picker result knows what it updates. */
    var localBundleUpdateUid by mutableStateOf<Int?>(null)

    // APK selection flow dialogs
    var showApkAvailabilityDialog by mutableStateOf(false)
    var showDownloadInstructionsDialog by mutableStateOf(false)
    var showFilePickerPromptDialog by mutableStateOf(false)
    var showInstalledAppPickerDialog by mutableStateOf(false)
    var loadingInstalledApps by mutableStateOf(false)
    var installedAppsForPicker by mutableStateOf<List<InstalledAppPickerItem>>(emptyList())
    // True while APK loading/processing runs in the background
    var processingApkSelection by mutableStateOf(false)

    // Error/warning dialogs
    var showUnsupportedVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
    var showExperimentalVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
    var showWrongPackageDialog by mutableStateOf<WrongPackageDialogState?>(null)
    var showSplitApkWarningDialog by mutableStateOf(false)
    var showInvalidSignatureDialog by mutableStateOf<InvalidSignatureDialogState?>(null)
    var showNoCompatibleVersionsDialog by mutableStateOf<String?>(null) // packageName

    // Pending data during APK selection
    var pendingPackageName by mutableStateOf<String?>(null)
    var pendingAppName by mutableStateOf<String?>(null)
    var pendingRecommendedVersion by mutableStateOf<AppTarget?>(null)
    var pendingCompatibleVersions by mutableStateOf<List<BundledAppTarget>>(emptyList())
    // Per-bundle recommended versions for multi-bundle display in ApkAvailabilityDialog
    var pendingRecommendedBundleVersions by mutableStateOf<Map<Int, BundleRecommendation>>(emptyMap())
    // Version selected by the user in Dialog 1 for the APK search query. Defaults to pendingRecommendedVersion
    var pendingSelectedDownloadVersion by mutableStateOf<AppTarget?>(null)
    var pendingSelectedApp by mutableStateOf<SelectedApp?>(null)
    var resolvedDownloadUrl by mutableStateOf<String?>(null)
    var pendingSavedApkInfo by mutableStateOf<SavedApkInfo?>(null)
    var pendingInstalledApkInfo by mutableStateOf<InstalledApkInfo?>(null)
    // null = not yet loaded, true/false = loaded result
    var pendingTargetAppInstalled by mutableStateOf<Boolean?>(null)

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Latches when an update cycle was skipped due to metered network; cleared on the next
    // successful/no-change update. Independent of the transient BundleUpdateSnackbar so the
    // user still sees a persistent alert after the transient snackbar fades
    var updatesSkippedDueToMetered by mutableStateOf(false)

    // Simple mode bundle selection dialog: shown when 2+ bundles have patches for the same app
    var showSimpleBundleSelectDialog by mutableStateOf(false)
    var simpleBundleSelectApp by mutableStateOf<SelectedApp?>(null)
    var simpleBundleSelectCandidates by mutableStateOf<List<Pair<PatchBundleInfo.Scoped, Set<String>>>>(emptyList())
    // Bundle pre-selected by the user before the APK selection flow (simple mode)
    var pendingSelectedBundleUid by mutableStateOf<Int?>(null)
        private set

    fun dismissSimpleBundleSelectDialog() {
        showSimpleBundleSelectDialog = false
        simpleBundleSelectApp = null
        simpleBundleSelectCandidates = emptyList()
        cleanupPendingData()
    }

    /**
     * Called when the user picks a bundle in [app.morphe.manager.ui.screen.home.SimpleBundleSelectDialog].
     * Instead of patching immediately, stores the chosen bundle uid and continues
     * to the APK selection flow so the correct recommended version is shown.
     */
    fun proceedWithSelectedBundle(bundleUid: Int) {
        val packageName = pendingPackageName ?: return
        showSimpleBundleSelectDialog = false
        simpleBundleSelectApp = null
        simpleBundleSelectCandidates = emptyList()

        pendingSelectedBundleUid = bundleUid

        // Update recommended version to the one the chosen bundle will be used at
        val bundleRecommended = recommendedBundleVersions[packageName]?.get(bundleUid)?.effective
        if (bundleRecommended != null) {
            pendingRecommendedVersion = bundleRecommended
            pendingSelectedDownloadVersion = bundleRecommended
        }

        viewModelScope.launch {
            continueApkSelectionFlow(packageName)
        }
    }

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
        versionCatalog.compatibleVersions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val recommendedVersionsFlow: StateFlow<Map<String, AppTarget>> =
        versionCatalog.recommendedVersions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Convenience accessors - read current value synchronously for non-reactive call sites
    val recommendedVersions: Map<String, AppTarget> get() = recommendedVersionsFlow.value
    val compatibleVersions: Map<String, List<BundledAppTarget>> get() = compatibleVersionsFlow.value

    /** Convenience accessor - reads expert mode preference without blocking. */
    private suspend fun isExpertMode() = prefs.useExpertMode.get()

    /**
     * Per-bundle recommended version for each package.
     * Returns Map<PackageName, Map<BundleUid, AppTarget>> so the APK availability dialog
     * can show the correct "Recommended" badge independently for each bundle section.
     */
    val recommendedBundleVersionsFlow: StateFlow<Map<String, Map<Int, BundleRecommendation>>> =
        versionCatalog.recommendedVersionsByBundle
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val recommendedBundleVersions: Map<String, Map<Int, BundleRecommendation>>
        get() = recommendedBundleVersionsFlow.value

    // Track available updates for installed apps
    private val _appUpdatesAvailable = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val appUpdatesAvailable: StateFlow<Map<String, Boolean>> = _appUpdatesAvailable.asStateFlow()

    // Ticker to force homeAppState recomputation after install/uninstall without changing DB state
    private val _appStateTicker = MutableStateFlow(0L)
    private val trackedAppInspectionSemaphore = Semaphore(4)

    private data class TrackedSnapshotEntry(
        val app: InstalledApp,
        val snapshot: TrackedAppSnapshot
    )

    private data class TrackedInspectionInputs(
        val apps: List<InstalledApp>,
        val originalEvidence: Map<String, String>,
        val bundleSignatures: Map<String, Set<String>>
    )

    // Verified tracked installs, keyed by the package the record currently occupies.
    // Resolved away from the home state so inspecting archives never holds the cards back.
    private val _trackedSnapshots = MutableStateFlow<Map<String, TrackedSnapshotEntry>>(emptyMap())

    // Counted per package, so invalidating one app never discards results already produced for
    // the others in the same pass
    private val trackedInspectionGenerations = ConcurrentHashMap<String, Long>()

    @Volatile
    private var activeTrackedApps: Map<String, InstalledApp> = emptyMap()

    // Both signals rebuild the same cards, so they reach the home state as one source
    private val appStateSignal = combine(_appStateTicker, _trackedSnapshots) { ticker, snapshots ->
        ticker to snapshots
    }

    // Package names worth reacting to, refreshed from the same flow that feeds the home cards
    @Volatile
    private var trackedPackageNames: Set<String> = emptySet()
    private val pendingPackageChanges = MutableStateFlow<Set<String>>(emptySet())

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val packageName = intent?.data?.schemeSpecificPart ?: return
            if (packageName !in trackedPackageNames) return
            // Stop presenting the previous verdict immediately, but keep the expensive refresh
            // debounced until the package manager finishes its add/remove/replace broadcast burst.
            markTrackedPackagesPending(setOf(packageName), invalidateCache = false)
            pendingPackageChanges.update { it + packageName }
        }
    }

    private fun trackedCurrentPackages(observedPackages: Set<String>): Set<String> {
        val matches = activeTrackedApps.values.asSequence()
            .filter {
                it.currentPackageName in observedPackages ||
                        it.originalPackageName in observedPackages
            }
            .mapTo(mutableSetOf()) { it.currentPackageName }
        // The records may not be loaded yet, so an unmatched package is treated as its own key
        if (matches.isEmpty()) matches += observedPackages
        return matches
    }

    private fun markTrackedPackagesPending(
        observedPackages: Set<String>,
        invalidateCache: Boolean
    ) {
        if (observedPackages.isEmpty()) return
        val currentPackages = trackedCurrentPackages(observedPackages)
        currentPackages.forEach(::bumpTrackedInspection)
        if (invalidateCache) currentPackages.forEach(localApkSources::invalidate)
        _trackedSnapshots.update { snapshots -> snapshots - currentPackages }
    }

    /** Claims the next inspection for [packageName], so any result in flight for it is dropped. */
    private fun bumpTrackedInspection(packageName: String): Long =
        trackedInspectionGenerations.merge(packageName, 1L, Long::plus)!!

    /**
     * Coalesces package broadcasts before rebuilding the home state.
     * A store updating apps in the background emits add, remove and replace in bursts, and every
     * one of them would otherwise re-inspect every tracked app.
     */
    private fun observePackageChanges() = viewModelScope.launch {
        pendingPackageChanges
            .filter { it.isNotEmpty() }
            .collectLatest { pending ->
                delay(PACKAGE_CHANGE_DEBOUNCE_MS.milliseconds)
                pending.forEach {
                    appDataResolver.invalidate(it)
                }
                markTrackedPackagesPending(pending, invalidateCache = true)
                _appStateTicker.update { it + 1 }
                pendingPackageChanges.value = emptySet()
            }
    }

    /** Rechecks only tracked evidence when storage management removes a retained patched APK. */
    private fun observeSavedPatchedApkChanges() = viewModelScope.launch {
        installedAppRepository.savedPatchedApkChanges.collect { packageNames ->
            packageNames.forEach(appDataResolver::invalidate)
            markTrackedPackagesPending(packageNames, invalidateCache = true)
            _appStateTicker.update { it + 1 }
        }
    }

    /**
     * Keeps [_trackedSnapshots] in step with the records and with anything that changed a package.
     *
     * Inspecting a record reads its archives, so it happens here rather than inside the home
     * state. Cards appear as soon as the bundles are known and adopt the verdict as it lands.
     */
    private fun observeTrackedApps() = viewModelScope.launch {
        val originalEvidence = originalApkRepository.getAll()
            .map { originals ->
                originals.associate { original ->
                    val file = File(original.filePath)
                    original.packageName to buildString {
                        append(original.version).append('|')
                        append(original.filePath).append('|')
                        append(file.length()).append(':').append(file.lastModified())
                    }
                }
            }
            .distinctUntilChanged()
        val bundleSignatures = patchBundleRepository.appMetadata
            .map { metadata ->
                metadata.mapValues { (_, appMetadata) -> appMetadata.signatures.orEmpty().toSet() }
            }
            .distinctUntilChanged()

        var previousOriginalEvidence: Map<String, String>? = null
        var previousBundleSignatures: Map<String, Set<String>>? = null

        combine(
            installedAppRepository.getAll(),
            _appStateTicker,
            originalEvidence,
            bundleSignatures
        ) { apps, _, originals, signatures ->
            TrackedInspectionInputs(apps, originals, signatures)
        }.collectLatest { inputs ->
            val appsByPackage = inputs.apps.associateBy { it.currentPackageName }
            activeTrackedApps = appsByPackage
            trackedPackageNames = inputs.apps.flatMapTo(mutableSetOf()) {
                listOf(it.currentPackageName, it.originalPackageName)
            }

            val changedEvidence = buildSet {
                previousOriginalEvidence?.let {
                    addAll(changedMapKeys(it, inputs.originalEvidence))
                }
                previousBundleSignatures?.let {
                    addAll(changedMapKeys(it, inputs.bundleSignatures))
                }
            }
            previousOriginalEvidence = inputs.originalEvidence
            previousBundleSignatures = inputs.bundleSignatures
            markTrackedPackagesPending(changedEvidence, invalidateCache = true)

            // A changed or removed database record is pending until its matching result arrives;
            // never keep presenting a snapshot produced for the previous record.
            _trackedSnapshots.update { snapshots ->
                snapshots.filter { (packageName, entry) ->
                    appsByPackage[packageName] == entry.app
                }
            }

            trackedInspectionGenerations.keys.retainAll(appsByPackage.keys)

            withContext(Dispatchers.IO) {
                coroutineScope {
                    inputs.apps.map { installed ->
                        val generation = bumpTrackedInspection(installed.currentPackageName)
                        launch {
                            val snapshot = trackedAppInspectionSemaphore.withPermit {
                                localApkSources.trackedAppSnapshot(installed)
                            }
                            if (trackedInspectionGenerations[installed.currentPackageName] != generation ||
                                activeTrackedApps[installed.currentPackageName] != installed
                            ) return@launch

                            _trackedSnapshots.update { snapshots ->
                                snapshots + (installed.currentPackageName to TrackedSnapshotEntry(
                                    app = installed,
                                    snapshot = snapshot
                                ))
                            }
                        }
                    }.joinAll()
                }
            }
        }
    }

    // Track when at least one third-party source is enabled
    val hasThirdPartySource: StateFlow<Boolean> =
        patchBundleRepository.sources
            .map { sources -> sources.any { it.enabled && it.uid != DEFAULT_SOURCE_UID } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Using mount install (set externally)
    var usingMountInstall: Boolean = false

    // Install target passed to patch availability resolvers
    val currentInstallerType: InstallerType
        get() = installerTypeFor(usingMountInstall)

    val currentApkArchitecture: ApkArchitecture
        get() = SELECTION_APK_ARCHITECTURE

    // Controls the pre-patching mode selection dialog for root-capable devices.
    var showPrePatchInstallerDialog by mutableStateOf(false)

    // Stores the pending arguments while the pre-patching mode dialog is visible.
    private var pendingPatchApp: SelectedApp? = null
    private var pendingPatchAllowIncompatible: Boolean = false

    /**
     * Called when a root-capable device triggers patching. Instead of starting immediately,
     * opens the pre-patching mode dialog so the user can choose Root Mount vs Standard.
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
     * Called when the user selects a patch mode from the pre-patching dialog.
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
     * Dismisses the pre-patching mode dialog without starting patching.
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

    private fun clearPendingApp() {
        val app = pendingSelectedApp
        pendingSelectedApp = null
        if (app is SelectedApp.Local && app.temporary) {
            app.file.delete()
        }
    }

    /**
     * User dismissed the split APK warning without proceeding.
     * Cleans up the temporary file if needed.
     */
    fun dismissSplitApkWarning() {
        showSplitApkWarningDialog = false
        clearPendingApp()
    }

    /**
     * User dismissed the unsupported version dialog.
     * Discards the pending selection and cleans up the temporary file if needed.
     */
    fun dismissUnsupportedVersionDialog() {
        showUnsupportedVersionDialog = null
        clearPendingApp()
    }

    private fun proceedWithPendingApp(allowIncompatible: Boolean) {
        val app = pendingSelectedApp ?: return
        pendingSelectedApp = null
        viewModelScope.launch {
            if (rootInstaller.isDeviceRooted()) {
                requestPrePatchInstallerSelection(app, allowIncompatible = allowIncompatible)
            } else {
                usingMountInstall = false
                startPatchingWithApp(app, allowIncompatible = allowIncompatible)
            }
        }
    }

    /**
     * User chose to proceed patching with an unsupported app version.
     * Starts patching with allowIncompatible=true so version-incompatible patches are included.
     */
    fun proceedWithUnsupportedVersion() {
        showUnsupportedVersionDialog = null
        proceedWithPendingApp(allowIncompatible = true)
    }

    /**
     * User dismissed the experimental version warning dialog.
     * Discards the pending selection and cleans up the temporary file if needed.
     */
    fun dismissExperimentalVersionDialog() {
        showExperimentalVersionDialog = null
        clearPendingApp()
    }

    /**
     * User acknowledged the experimental version warning and chose to proceed.
     * Starts patching with allowIncompatible=false - the version is supported,
     * just flagged as experimental in the patch bundle.
     */
    fun proceedWithExperimentalVersion() {
        showExperimentalVersionDialog = null
        proceedWithPendingApp(allowIncompatible = false)
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
        clearPendingApp()
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
        ContextCompat.registerReceiver(
            app,
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            // Only the system can send these protected broadcasts, so the receiver never has to
            // be reachable by other apps.
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        observePackageChanges()
        observeSavedPatchedApkChanges()
        observeTrackedApps()
        observeManagerUpdate()
        triggerUpdateCheck()
        observeLoadingState()
        observeInstalledAppUpdates()
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
                updatesSkippedDueToMetered = when (progress.result) {
                    PatchBundleRepository.BundleUpdateResult.SkippedMetered -> true
                    PatchBundleRepository.BundleUpdateResult.Success,
                    PatchBundleRepository.BundleUpdateResult.NoUpdates -> false
                    else -> updatesSkippedDueToMetered
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
                delay(500.milliseconds)
            } finally {
                _isRefreshing.value = false
            }
            appDataResolver.invalidateAll()
            _appStateTicker.update { it + 1 }
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

    /** True while a batch queue is patching, so callers can explain why a start was ignored. */
    val batchPatchRunning: Boolean get() = batchPatchCoordinator.isRunning

    /**
     * Guard entry-point for all patching flows.
     * Shows MeteredPatchingDialog when on metered network with updates disabled,
     * so the user can choose to update patches first or patch anyway.
     * Otherwise, launches [action] immediately.
     */
    fun guardPatching(action: suspend () -> Unit) {
        // A batch run owns the patcher worker for its whole duration, so starting a single
        // patch would replace the app the queue is currently working on
        if (batchPatchCoordinator.isRunning) {
            app.toast(app.getString(R.string.batch_patch_in_progress))
            return
        }

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
     * Mirrors the resolved update into [updatedManagerVersion] so the banner also appears when
     * a background check finds the release. A failed check is ignored rather than hiding a
     * banner the user is already looking at.
     */
    private fun observeManagerUpdate() = viewModelScope.launch {
        managerUpdateRepository.availableUpdate.collect { update ->
            update?.let { updatedManagerVersion = it.version }
        }
    }

    /**
     * Checks for a manager update. The repository only reports releases whose APK is already
     * downloadable, so the banner can never point at an asset that is still uploading.
     */
    suspend fun checkForManagerUpdates() {
        uiSafe(app, R.string.failed_to_check_updates, "Failed to check for updates") {
            managerUpdateRepository.refresh()
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
            val appNames = resolveChangelogNames(app.originalPackageName)

            // Check if any bundle used for this app has been updated
            val hasUpdate = storedVersions.any { (bundleUid, storedVersion) ->
                val currentVersion = currentVersionByUid[bundleUid] ?: return@any false
                if (!isNewerVersion(storedVersion, currentVersion)) return@any false

                // Bundle is newer - refine with changelog if available.
                // No changelog (null) → show badge (network error or local bundle).
                // No resolvable app name → show badge (can't match scopes).
                // Known name, no matching scope → no badge.
                val entries = changelogByUid[bundleUid] ?: return@any true
                if (appNames.isEmpty()) return@any true
                ChangelogParser.hasChangesFor(
                    entries = entries,
                    installedVersion = storedVersion,
                    appNames = appNames,
                )
            }

            updates[app.currentPackageName] = hasUpdate
        }

        _appUpdatesAvailable.value = updates
    }

    /**
     * Resolves candidate changelog scope names for [packageName].
     *
     * Returns the union of:
     *  1. Bundle Compatibility declaration displayName (canonical, not localized,
     *     controlled by the same author who writes the changelog scopes).
     *     Falls back to [KnownApps.fallbackName] inside [BundleAppMetadata.buildFrom].
     *  2. System PM label (localized, may differ per user locale).
     *
     * Matching against any candidate is enough. This handles the common case where
     * the PM label is localized ("Шахи") while the changelog scope uses the
     * canonical English name ("Chess.com"), and also tolerates author drift when
     * the bundle displayName and the changelog scope diverge slightly.
     */
    private fun resolveChangelogNames(packageName: String): Set<String> {
        val names = mutableSetOf<String>()
        bundleAppMetadataFlow.value[packageName]?.displayName?.let { names += it }
        pm.getPackageInfo(packageName)?.let { with(pm) { it.label() } }?.let { names += it }
        return names
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
                    delay(1_750.milliseconds)
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

    fun createLocalSource(patchBundle: Uri) = importLocalSource(patchBundle, replacingUid = null)

    /**
     * Points an existing local source at a newly picked file. Adding the updated file instead
     * would create a second source and strand the patch selection on the old one.
     */
    fun updateLocalSource(uid: Int, patchBundle: Uri) = importLocalSource(patchBundle, replacingUid = uid)

    @SuppressLint("Recycle")
    private fun importLocalSource(patchBundle: Uri, replacingUid: Int?) = viewModelScope.launch {
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

                val openStream: suspend () -> InputStream = {
                    contentResolver.openInputStream(patchBundle)
                        ?: throw FileNotFoundException("Unable to open $patchBundle")
                }
                try {
                    if (replacingUid != null) {
                        patchBundleRepository.replaceLocal(replacingUid, size, openStream)
                    } else {
                        patchBundleRepository.createLocal(size, openStream)
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
        patchBundleRepository.bundleUpdateProgress
            .dropWhile { it == null }
            .first { it == null }
        delay(1.5.seconds)
        showSwipeGestureHint.value = true
    }

    /**
     * Called when the app is opened via a deep link containing a bundle URL.
     * Shows a confirmation dialog instead of adding silently.
     */
    fun handleDeepLinkAddSource(url: String, name: String?) {
        dismissOpenDialogs()
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

    /**
     * Closes every open dialog and sheet so an incoming source confirmation is not stacked behind
     * one, and so the flow the user resumes afterward is rebuilt from the new set of sources.
     */
    private fun dismissOpenDialogs() {
        cleanupExpertModeData()
        dismissSimpleBundleSelectDialog()
        dismissSplitApkWarning()
        dismissUnsupportedVersionDialog()
        dismissExperimentalVersionDialog()
        dismissWrongPackageDialog()
        dismissInvalidSignatureDialog()
        dismissPrePatchInstallerDialog()
        dismissMeteredPatchingDialog()
        dismissLowDiskSpaceDialog()
        dismissInstalledAppInfo()
        showNoCompatibleVersionsDialog = null
        showAndroid11Dialog = false
        showBundleManagementSheet = false
        showRenameBundleDialog = false
        bundleToRename = null
        showAddSourceDialog = false
        selectedBundleUri = null
        selectedBundlePath = null
        cleanupPendingData()
    }

    /**
     * Metadata for all apps across enabled bundles - display names, icon colors, signatures, etc.
     * Delegates to [PatchBundleRepository.appMetadata] as the single source of truth.
     */
    val bundleAppMetadataFlow: StateFlow<Map<String, BundleAppMetadata>> =
        patchBundleRepository.appMetadata

    private val _homeCategoryPrefsFlow = combine(
        homeAppButtonPrefs.categoryState,
        homeAppButtonPrefs.categoryViewMode,
        homeAppButtonPrefs.showCategoryViewSwitcher,
        homeAppButtonPrefs.expandedSourceGroups,
    ) { categoryState, categoryViewMode, showCategoryViewSwitcher, expandedSourceGroups ->
        HomeCategoryPrefs(
            categoryState = categoryState,
            categoryViewMode = categoryViewMode,
            showCategoryViewSwitcher = showCategoryViewSwitcher,
            expandedSourceGroups = expandedSourceGroups
        )
    }

    private val _homePrefsFlow = combine(
        homeAppButtonPrefs.hiddenPackages,
        homeAppButtonPrefs.customOrder,
        homeAppButtonPrefs.sourceOrders,
        homeAppButtonPrefs.sortMode,
        _homeCategoryPrefsFlow,
    ) { hidden, order, sourceOrders, sortMode, categoryPrefs ->
        HomePrefs(
            hiddenPackages = hidden,
            customOrder = order,
            sourceOrders = sourceOrders,
            sortMode = sortMode,
            categoryState = categoryPrefs.categoryState,
            categoryViewMode = categoryPrefs.categoryViewMode,
            showCategoryViewSwitcher = categoryPrefs.showCategoryViewSwitcher,
            expandedSourceGroups = categoryPrefs.expandedSourceGroups
        )
    }

    /**
    * Sorted list of visible and hidden home app items.
    *
    * Sort mode is persisted with the home app button preferences. Custom mode applies
    * the user's saved manual order and falls back to Morphe ordering when no saved order exists.
    * Hidden apps are excluded from [HomeAppState.visible].
    */
    val homeAppState: StateFlow<HomeAppState?> = combine(
        patchBundleRepository.bundleState,
        _homePrefsFlow,
        installedAppRepository.getAll().onEach { apps ->
            trackedPackageNames = apps.flatMapTo(mutableSetOf()) {
                listOf(it.currentPackageName, it.originalPackageName)
            }
            apps.forEach { app ->
                appDataResolver.invalidate(app.currentPackageName)
                if (app.originalPackageName != app.currentPackageName) {
                    appDataResolver.invalidate(app.originalPackageName)
                }
            }
        },
        _appUpdatesAvailable,
        appStateSignal,
    ) { bundleState, homePrefs, installedApps, updatesMap, (_, trackedSnapshots) ->
        val ready = bundleState as? PatchBundleRepository.BundleState.Ready
            ?: return@combine null

        val enabledInfo = ready.info.filter { (_, info) -> info.enabled }
        val metadata = BundleAppMetadata.buildFrom(enabledInfo)
        val packages = metadata.keys
        val sourceGroups = buildHomeAppSourceGroups(
            enabledInfo = enabledInfo,
            sources = ready.sources,
            sortMode = homePrefs.sortMode,
            sourceOrders = homePrefs.sourceOrders,
            expandedSourceGroups = homePrefs.expandedSourceGroups
        )

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
            val trackedEntry = installedApp?.let { tracked ->
                trackedSnapshots[tracked.currentPackageName]?.takeIf { it.app == tracked }
            }
            val trackedSnapshot = trackedEntry?.snapshot
            val savedPatchedApk = trackedSnapshot?.savedPatchedApk
            val savedPackageInfo = trackedSnapshot?.savedPatchedApkInfo
            // Inspection resolves on its own, so a record without a snapshot has not been judged
            // yet and must not be presented as any of the resolved states
            val trackedPresentation = if (installedApp != null && trackedSnapshot != null) {
                trackedInstallPresentation(installedApp.installType, trackedSnapshot.patchState)
            } else {
                null
            }
            // An unjudged record is described the way the package manager sees it
            val isUninspectedInstall = installedApp != null &&
                    trackedSnapshot == null &&
                    pm.getPackageInfo(installedApp.currentPackageName) != null
            val isInstallStatePending = installedApp != null && trackedSnapshot == null
            val isInstalledOnDevice = trackedPresentation?.isPatched == true
            val hasUpdate = installedApp?.let {
                updatesMap[it.currentPackageName] == true
            } == true

            if (installedApp != null && trackedSnapshot != null && isInstalledOnDevice) {
                reconcileInstalledVersion(installedApp, trackedSnapshot.installedPackageInfo)
            }

            return HomeAppItem(
                packageName = packageName,
                displayName = displayName,
                gradientColors = gradientColors,
                installedApp = installedApp,
                // Confirmed installs and replacements use the package actually on the device.
                // Unknown packages keep showing what Morphe retained rather than attributing the
                // record to whichever package currently owns the name.
                packageInfo = displayedHomePackageInfo(
                    trackedPresentation = trackedPresentation,
                    installedPackageInfo = trackedSnapshot?.installedPackageInfo,
                    savedPackageInfo = savedPackageInfo,
                    untrackedPackageInfo = resolvedData.packageInfo
                ),
                isPinnedByDefault = knownApp?.isPinnedByDefault == true,
                isInstalledOnDevice = (trackedPresentation?.showsInstalledPackage == true) ||
                        isUninspectedInstall ||
                        (installedApp == null && resolvedData.source == AppDataSource.INSTALLED),
                isDeleted = trackedPresentation?.isDeleted == true,
                isInstallStateNotPatched = trackedPresentation?.isNotPatched == true,
                isInstallStateUnknown = trackedPresentation?.isUnknown == true,
                isInstallStatePending = isInstallStatePending,
                savedApkFile = savedPatchedApk,
                hasUpdate = hasUpdate,
                patchCount = 0
            )
        }

        // Include apps patched with universal patches through "Other apps": they have no bundle
        // metadata but must still appear as cards so users can reinstall/uninstall/see updates
        val universalOnlyPackages = installedApps
            .map { it.originalPackageName }
            .filter { it !in packages }
            .toSet()
        val allPackages = packages + universalOnlyPackages

        // Active bundle packages filtered to those in patchablePackages
        val activeHidden = homePrefs.hiddenPackages.filter { it in allPackages }

        val visiblePackages = allPackages.filter { it !in homePrefs.hiddenPackages }

        // Fan out per-package resolution: buildItem is IO-bound and stalls at 400+ apps sequentially.
        val builtItems = coroutineScope {
            (visiblePackages + activeHidden)
                .map { pkg -> async { buildItem(pkg) } }
                .awaitAll()
        }
        val visibleItems = builtItems.subList(0, visiblePackages.size)
        val hiddenItems = builtItems.subList(visiblePackages.size, builtItems.size)

        val visible = sortHomeAppItems(
            items = visibleItems,
            sortMode = homePrefs.sortMode,
            customOrder = homePrefs.customOrder
        )

        val hidden = sortHomeAppItems(
            items = hiddenItems,
            sortMode = homePrefs.sortMode,
            customOrder = homePrefs.customOrder
        )

        HomeAppState(
            visible = visible,
            hidden = hidden,
            sortMode = homePrefs.sortMode,
            categoryState = homePrefs.categoryState,
            categoryViewMode = homePrefs.categoryViewMode,
            showCategoryViewSwitcher = homePrefs.showCategoryViewSwitcher,
            sourceGroups = sourceGroups
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Aligns the recorded version with the running one after an in-place update.
     *
     * Only a confirmed tracked install may do this: reconciling against a package that merely
     * shares the name would rewrite the record to describe a build Morphe never produced.
     * Skipped for MOUNT (the package manager reports the stock APK) and SAVED (no live install).
     */
    private suspend fun reconcileInstalledVersion(app: InstalledApp, installedPackageInfo: PackageInfo?) {
        if (app.installType == InstallType.MOUNT || app.installType == InstallType.SAVED) return

        val liveVersion = installedPackageInfo?.versionName
        if (liveVersion.isNullOrBlank() || liveVersion == app.version) return

        installedAppRepository.updateInstalledVersion(app, liveVersion)
    }

    private fun buildHomeAppSourceGroups(
        enabledInfo: Map<Int, PatchBundleInfo.Global>,
        sources: Map<Int, PatchBundleSource>,
        sortMode: HomeAppSortMode,
        sourceOrders: Map<Int, List<String>>,
        expandedSourceGroups: Set<Int>
    ): List<HomeAppSourceGroup> {
        // enabledInfo is already filtered to enabled entries by the caller
        // Keep source sections in repository order. Home sorting should reorder app cards
        // inside each source section, not move source headers around.
        val sourceOrder = sources.keys.mapIndexed { index, uid -> uid to index }.toMap()
        return enabledInfo.values
            .sortedWith(
                compareBy(
                    { sourceOrder[it.uid] ?: Int.MAX_VALUE },
                    { it.uid }
                )
            )
            .mapNotNull { info ->
                val packageNames = info.patches
                    .asSequence()
                    .flatMap { patch -> patch.compatiblePackages.orEmpty().asSequence() }
                    .mapNotNull { compatiblePackage -> compatiblePackage.packageName }
                    .distinct()
                    .toSet()

                if (packageNames.isEmpty()) {
                    null
                } else {
                    val packageOrder = if (sortMode == HomeAppSortMode.MANUAL) {
                        sourceOrders[info.uid]
                            .orEmpty()
                            .filter { packageName -> packageName in packageNames }
                    } else {
                        emptyList()
                    }
                    val source = sources[info.uid]
                    val avatarUrls = source?.avatarUrls
                    val sourceName = source?.displayTitle
                        ?.takeUnless { it.isBlank() }
                        ?: info.name.takeUnless { it.isBlank() }
                        ?: "#${info.uid}"
                    HomeAppSourceGroup(
                        uid = info.uid,
                        name = sourceName,
                        packageNames = packageNames,
                        packageOrder = packageOrder,
                        collapsed = info.uid != DEFAULT_SOURCE_UID && info.uid !in expandedSourceGroups,
                        avatarUrl = avatarUrls?.primary,
                        fallbackAvatarUrl = avatarUrls?.fallback
                    )
                }
            }
    }

    private fun sortHomeAppItems(
        items: List<HomeAppItem>,
        sortMode: HomeAppSortMode,
        customOrder: List<String>
    ): List<HomeAppItem> {
        val morpheComparator = compareByDescending<HomeAppItem> { it.installedApp != null }
            .thenByDescending { it.isPinnedByDefault }
            .thenByDescending { it.packageInfo != null }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }

        return when (sortMode) {
            HomeAppSortMode.MANUAL -> {
                val defaultSorted = items.sortedWith(morpheComparator)
                if (customOrder.isEmpty()) {
                    defaultSorted
                } else {
                    val indexMap = customOrder.mapIndexed { index, packageName -> packageName to index }.toMap()
                    defaultSorted.sortedBy { indexMap[it.packageName] ?: Int.MAX_VALUE }
                }
            }
            HomeAppSortMode.RECOMMENDED -> items.sortedWith(morpheComparator)
            HomeAppSortMode.NAME_ASC -> items.sortedWith(
                compareBy<HomeAppItem, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
            )
            HomeAppSortMode.NAME_DESC -> items.sortedWith(
                compareBy<HomeAppItem, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
                    .reversed()
            )
            HomeAppSortMode.UPDATES_FIRST -> items.sortedWith(
                compareByDescending<HomeAppItem> { it.showsUpdateBadge }
                    .then(morpheComparator)
            )
        }
    }

    /**
     * Resets the swipe gesture hint after it has been shown.
     */
    fun markSwipeGestureHintShown() {
        showSwipeGestureHint.value = false
    }

    /** Triggers the swipe gesture hint animation on the first card. */
    fun triggerSwipeGestureHint() {
        showSwipeGestureHint.value = true
    }

    /**
     * Invalidates AppDataResolver cache for [packageName] and forces homeAppState recomputation.
     * Call this after any install/uninstall operation that doesn't change the DB record.
     */
    fun notifyAppStateChanged(packageName: String) {
        appDataResolver.invalidate(packageName)
        markTrackedPackagesPending(setOf(packageName), invalidateCache = true)
        _appStateTicker.update { it + 1 }
    }

    /**
     * Snapshot of all bundle info (including disabled) as a [StateFlow] for synchronous reads.
     * Used by [getPatchesForPackage] which is called from Compose (non-suspend context).
     */
    private val allBundlesInfoState: StateFlow<Map<Int, PatchBundleInfo.Global>> =
        patchBundleRepository.allBundlesInfoFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val patchSourcesState: StateFlow<Map<Int, PatchBundleSource>> =
        patchBundleRepository.sources
            .map { list -> list.associateBy { it.uid } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Returns the [PatchBundleSource] with [uid], or null if it has not been loaded yet. */
    fun getPatchSource(uid: Int): PatchBundleSource? = patchSourcesState.value[uid]

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
                    val patches = info.forPackage(packageName, null).patches
                    if (patches.isNotEmpty()) put(uid, patches)
                }
        }
    }

    /**
     * Returns patches actually applied to [packageName], grouped as Map<BundleUid, List<PatchInfo>>.
     * Used by the swipe-right dialog for cards patched via "Other apps" (universal patches),
     * where getPatchesForPackage would return empty because no bundle declares them compatible.
     * Names that reference bundles no longer available are silently dropped.
     */
    suspend fun getAppliedPatchesForPackage(packageName: String): Map<Int, List<PatchInfo>> {
        val bundleInfo = allBundlesInfoState.value
        val applied = installedAppRepository.getAppliedPatches(packageName)
        return applied.entries.mapNotNull { (uid, patchNames) ->
            if (patchNames.isEmpty()) return@mapNotNull null
            val patchInfos = bundleInfo[uid]?.forPackage(packageName, null)?.patches
                ?.filter { it.name in patchNames }
                ?.sortedBy { it.name }
                ?: return@mapNotNull null
            if (patchInfos.isEmpty()) null else uid to patchInfos
        }.toMap()
    }

    /**
     * Returns the display name of the bundle with [uid], or null.
     */
    fun getBundleDisplayName(uid: Int): String? =
        allBundlesInfoState.value[uid]?.name

    fun saveAppOrder(packageNames: List<String>) {
        homeAppButtonPrefs.saveOrder(packageNames)
    }

    fun saveAppSourceOrder(sourceUid: Int, packageNames: List<String>) {
        homeAppButtonPrefs.saveSourceOrder(sourceUid, packageNames)
    }

    fun resetAppOrder() {
        homeAppButtonPrefs.resetOrder()
    }

    fun resetAppSourceOrder(sourceUid: Int) {
        homeAppButtonPrefs.resetSourceOrder(sourceUid)
    }

    fun saveAppSourceGroupOrder(sourceUids: List<Int>) {
        viewModelScope.launch {
            val visibleUids = sourceUids.distinct()
            val visibleUidSet = visibleUids.toSet()
            val currentUids = patchBundleRepository.sources.first().map { it.uid }
            val mergedUids = visibleUids + currentUids.filter { it !in visibleUidSet }
            prefs.sourceBundleSortMode.update(SourceBundleSortMode.MANUAL.name)
            patchBundleRepository.reorderBundles(mergedUids)
        }
    }

    fun setAppSortMode(sortMode: HomeAppSortMode) {
        homeAppButtonPrefs.setSortMode(sortMode)
    }

    fun setAppCategoryViewMode(viewMode: HomeAppCategoryViewMode) {
        homeAppButtonPrefs.setCategoryViewMode(viewMode)
    }

    fun createAppCategory(name: String): String =
        homeAppButtonPrefs.createCategory(name)

    fun renameAppCategory(categoryId: String, name: String) {
        homeAppButtonPrefs.renameCategory(categoryId, name)
    }

    fun deleteAppCategory(categoryId: String) {
        homeAppButtonPrefs.deleteCategory(categoryId)
    }

    fun saveAppCategoryOrder(categoryIds: List<String>) {
        homeAppButtonPrefs.saveCategoryOrder(categoryIds)
    }

    fun toggleAppCategoryCollapsed(categoryId: String?) {
        homeAppButtonPrefs.toggleCategoryCollapsed(categoryId)
    }

    fun toggleAppSourceGroupCollapsed(sourceUid: Int) {
        homeAppButtonPrefs.toggleSourceGroupCollapsed(sourceUid)
    }

    fun assignAppsToCategory(packageNames: Set<String>, categoryId: String?) {
        homeAppButtonPrefs.assignToCategory(packageNames, categoryId)
    }

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
        compatibleVersions[packageName].orEmpty().experimentalVersions()

    /** Triggers the swipe gesture hint whenever a custom bundle is added. */
    val showSwipeGestureHint = MutableStateFlow(false)

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
     * Whether the search and sort buttons should be visible.
     * Shown when there are more than 4 app buttons or a third-party source is active: fewer
     * apps than that fit on screen at a glance, so both search and reordering are noise.
     */
    val showSearchButton: StateFlow<Boolean> =
        combine(
            homeAppState,
            hasThirdPartySource
        ) { state, thirdParty ->
            (state?.visible?.size ?: 0) > 4 || thirdParty
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun persistReinstalledApp(
        app: InstalledApp,
        packageName: String,
        installType: InstallType
    ): Boolean = withContext(Dispatchers.IO) {
        val appliedPatches = installedAppRepository.getAppliedPatches(app.currentPackageName)
        installedAppRepository.addOrUpdate(
            currentPackageName = packageName,
            originalPackageName = app.originalPackageName,
            version = app.version,
            installType = installType,
            patchSelection = appliedPatches,
            selectionPayload = app.selectionPayload,
            patchedAt = app.patchedAt
        )
        notifyAppStateChanged(packageName)
        if (packageName != app.currentPackageName) notifyAppStateChanged(app.currentPackageName)
        true
    }

    fun uninstallApps(items: Collection<HomeAppItem>) {
        val apps = items.mapNotNull { it.installedApp }.distinctBy { it.currentPackageName }
        if (apps.isEmpty()) return

        viewModelScope.launch {
            var completed = 0
            var skipped = 0
            var unverified = 0
            for (installed in apps) {
                runCatching {
                    when (installed.installType) {
                        // Unmounting only removes the bind mount and the module files, so it is
                        // safe even when the underlying package can no longer be identified
                        InstallType.MOUNT -> {
                            installerManager.uninstallPackage(installed.currentPackageName, installed.installType)
                            installedAppRepository.delete(installed)
                        }
                        else -> {
                            if (localApkSources.trackedPatchState(installed) != InstalledPatchState.Patched) {
                                unverified++
                                return@runCatching false
                            }
                            val removed = withTimeoutOrNull(BATCH_UNINSTALL_TIMEOUT) {
                                installerManager.uninstallPackage(installed.currentPackageName, installed.installType)
                                true
                            } == true
                            if (!removed) error(app.getString(R.string.uninstall_timeout))
                        }
                    }
                    true
                }.onSuccess { removed ->
                    if (removed) completed++ else skipped++
                    notifyAppStateChanged(installed.currentPackageName)
                }.onFailure { error ->
                    skipped++
                    if (error !is UninstallCancelledException) {
                        app.toast(app.getString(R.string.uninstall_app_fail, error.simpleMessage()))
                    }
                }
            }

            if (unverified > 0) app.toast(app.getString(R.string.uninstall_app_unverified))
            app.batchActionSummary(R.plurals.batch_uninstall_summary, completed, skipped)
                ?.let { app.toast(it) }
        }
    }

    /** Copies left on the device by a patch that renamed the package. */
    val orphanedInstalls = installedAppRepository.orphanedInstalls

    /**
     * Uninstalls a copy that patching left behind under its previous package name.
     * The prompt is cleared either way: a refused or failed uninstall must not keep reappearing.
     * A copy that cannot be identified is left on the device, so the user is told about it.
     */
    fun uninstallOrphanedInstall(orphan: InstalledApp) {
        viewModelScope.launch {
            runCatching {
                if (localApkSources.trackedPatchState(orphan) != InstalledPatchState.Patched) {
                    app.toast(app.getString(R.string.uninstall_app_unverified))
                    return@runCatching false
                }
                val removed = withTimeoutOrNull(BATCH_UNINSTALL_TIMEOUT) {
                    installerManager.uninstallPackage(orphan.currentPackageName, orphan.installType)
                    true
                } == true
                if (!removed) error(app.getString(R.string.uninstall_timeout))
                true
            }.onSuccess { removed ->
                if (removed) notifyAppStateChanged(orphan.currentPackageName)
            }.onFailure { error ->
                if (error !is UninstallCancelledException) {
                    app.toast(app.getString(R.string.uninstall_app_fail, error.simpleMessage()))
                }
            }
            installedAppRepository.forgetOrphanedInstall(orphan)
        }
    }

    fun keepOrphanedInstall(orphan: InstalledApp) =
        installedAppRepository.forgetOrphanedInstall(orphan)

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
        preparePendingFlow(packageName)

        // Guard: if there is a pending bundle update on metered data, show the outdated-patches
        // dialog before proceeding with the actual APK selection flow.
        guardPatching { showPatchDialogInternal(packageName) }
    }

    /** Seeds the pending APK selection state for [packageName] from the current bundle data. */
    private fun preparePendingFlow(packageName: String) {
        pendingPackageName = packageName
        pendingAppName = bundleAppMetadataFlow.value[packageName]?.displayName
            ?: KnownApps.getAppName(packageName)
        pendingRecommendedVersion = recommendedVersions[packageName]
        pendingCompatibleVersions = compatibleVersions[packageName] ?: emptyList()
        pendingRecommendedBundleVersions = recommendedBundleVersions[packageName] ?: emptyMap()
        pendingSelectedDownloadVersion = pendingRecommendedVersion
        // Reset per-package cached state so a new flow always loads fresh data
        pendingSavedApkInfo = null
        pendingInstalledApkInfo = null
        pendingTargetAppInstalled = null
    }

    private suspend fun showPatchDialogInternal(packageName: String) {
        val savedInfo = withContext(Dispatchers.IO) {
            localApkSources.saved(packageName)
        }
        pendingSavedApkInfo = savedInfo

        // Check if every declared version is incompatible with the current device SDK
        val versions = compatibleVersions[packageName] ?: emptyList()
        val deviceSdk = Build.VERSION.SDK_INT
        val allIncompatible = versions.isNotEmpty() &&
                versions.all { b ->
                    val minSdk = b.target.minSdk
                    minSdk != null && deviceSdk < minSdk
                }
        if (allIncompatible) {
            showNoCompatibleVersionsDialog = packageName
            return
        }

        // In simple mode: if multiple bundles cover this package, ask the user to pick one
        // before showing the APK selection dialog so the correct recommended version is used
        if (!isExpertMode() && pendingSelectedBundleUid == null) {
            val allBundlesForCheck = withContext(Dispatchers.IO) {
                patchBundleRepository
                    .scopedBundleInfoFlow(packageName, version = null)
                    .first()
            }
            val candidates = allBundlesForCheck
                .filter { it.enabled }
                .map { bundle ->
                    val patchNames = bundle.patchSequence(allowIncompatible = true)
                        .filter { it.include }
                        .mapTo(mutableSetOf()) { it.name }
                    bundle to patchNames
                }
                .filter { (_, patches) -> patches.isNotEmpty() }

            if (candidates.size > 1) {
                simpleBundleSelectCandidates = candidates
                showSimpleBundleSelectDialog = true
                return
            }
        }

        continueApkSelectionFlow(packageName)
    }

    /**
     * Second half of the patch-dialog flow: runs after bundle selection (or immediately when
     * no bundle disambiguation is needed). Decides whether to auto-use the saved APK or to
     * open the APK availability dialog.
     */
    private suspend fun continueApkSelectionFlow(packageName: String) {
        // Load saved APK (Room + AppDataResolver) and, in expert mode only, installed APK
        // (PackageManager) in parallel. In simple mode the installed-APK button is hidden,
        // so we skip the PM lookup entirely to keep simple-mode behavior unchanged
        val expertMode = isExpertMode()
        coroutineScope {
            val savedJob = if (pendingSavedApkInfo == null) {
                async(Dispatchers.IO) { localApkSources.saved(packageName) }
            } else null
            val installedJob = if (expertMode && pendingTargetAppInstalled == null) {
                async(Dispatchers.IO) { localApkSources.installed(packageName) }
            } else null
            savedJob?.await()?.let { pendingSavedApkInfo = it }
            installedJob?.await()?.let { (installed, info) -> applyInstalledApkInfo(installed, info) }
        }

        val recommendedVersion = pendingRecommendedVersion

        val shouldAutoUseSaved = !expertMode &&
                pendingSavedApkInfo != null &&
                recommendedVersion != null &&
                pendingSavedApkInfo!!.version == recommendedVersion.version

        if (shouldAutoUseSaved) {
            // Skip dialog and use saved APK directly
            handleSavedApkSelection()
        } else {
            // Show dialog
            showApkAvailabilityDialog = true
        }
    }

    /**
     * Stores the installed-app lookup, keeping the APK only when its version can be patched
     * so every caller offers the installed source under the same condition.
     */
    private fun applyInstalledApkInfo(installed: Boolean, info: InstalledApkInfo?) {
        pendingTargetAppInstalled = installed
        pendingInstalledApkInfo = info?.takeIf { isInstalledVersionCompatible(it.version, it.versionCode) }
    }

    /**
     * Returns true if [installedVersion] is listed in [pendingCompatibleVersions],
     * or if the compatible list is empty / contains an "any version" target.
     */
    private fun isInstalledVersionCompatible(installedVersion: String, installedVersionCode: Long?): Boolean {
        val compatible = pendingCompatibleVersions
        if (compatible.isEmpty() || compatible.any { it.target.version == null }) return true
        return compatible.any { entry ->
            entry.target.version == installedVersion &&
                (entry.buildCodes == null || installedVersionCode == null || installedVersionCode.toInt() in entry.buildCodes)
        }
    }

    /**
     * Handle selection of the currently installed APK from the APK availability dialog.
     * For single APKs: copies to a temp file. For split APKs: packs into a temp .apks archive.
     * The source is NOT saved to the original-APK repository.
     */
    fun handleInstalledApkSelection() {
        val installedInfo = pendingInstalledApkInfo
        val packageName = pendingPackageName

        if (installedInfo == null || packageName == null) {
            cleanupPendingData()
            return
        }

        viewModelScope.launch {
            showApkAvailabilityDialog = false
            processingApkSelection = true
            try {
                val selectedApp = withContext(Dispatchers.IO) {
                    try {
                        if (installedInfo.isSplit) {
                            val archive = File(filesystem.uiTempDir, "${packageName}_installed.apks")
                            createApksArchive(installedInfo, archive)
                            SelectedApp.Local(
                                packageName = packageName,
                                version = installedInfo.version,
                                versionCode = installedInfo.versionCode,
                                file = archive,
                                temporary = true,
                                fromInstalledDevice = true
                            )
                        } else {
                            val source = File(installedInfo.apkPath)
                            if (!source.exists()) return@withContext null
                            val tempFile = File(filesystem.uiTempDir, "${packageName}_installed.apk")
                            source.copyTo(tempFile, overwrite = true)
                            SelectedApp.Local(
                                packageName = packageName,
                                version = installedInfo.version,
                                versionCode = installedInfo.versionCode,
                                file = tempFile,
                                temporary = true,
                                fromInstalledDevice = true
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to prepare installed APK", e)
                        null
                    }
                }

                if (selectedApp != null) {
                    // Skip signature check: the installed APK may already be patched with our key
                    processSelectedAppIgnoringSignature(selectedApp)
                } else {
                    app.toast(app.getString(R.string.home_invalid_apk_io_error))
                    cleanupPendingData()
                }
            } finally {
                processingApkSelection = false
            }
        }
    }

    /**
     * Handles a helper result where the user installed the target app outside Morphe.
     *
     * Reached in simple mode too, where the installed source is otherwise hidden: the helper is
     * opt-in and the hand-off was confirmed, so the app store is the whole point of the request.
     */
    fun handleHelperInstalledAppSelection(packageName: String) {
        viewModelScope.launch {
            val pending = pendingPackageName
            if (pending != null && pending != packageName) {
                // The helper answered about an app the user never asked to patch
                stopHelperHandoff(pending, R.string.home_apk_helper_wrong_package)
                return@launch
            }

            // A hand-off through an app store can outlive Morphe, so the flow is rebuilt from the
            // bundle data. Unknown packages are refused because without their compatible versions
            // there is nothing left to check the installed build against
            if (pending == null) {
                if (packageName !in compatibleVersions) {
                    app.toast(app.getString(R.string.home_apk_helper_installed_unavailable))
                    cleanupPendingData()
                    return@launch
                }
                preparePendingFlow(packageName)
            }

            processingApkSelection = true
            val (installed, info) = withContext(Dispatchers.IO) {
                localApkSources.installed(packageName)
            }
            applyInstalledApkInfo(installed, info)

            val installedInfo = pendingInstalledApkInfo
            if (installedInfo == null) {
                // An app store hands out the newest build, which is regularly one no bundle covers
                stopHelperHandoff(
                    packageName,
                    if (info == null) {
                        R.string.home_apk_helper_installed_unavailable
                    } else {
                        R.string.home_apk_helper_installed_incompatible
                    }
                )
                return@launch
            }

            // Continuing here would swallow the warning the availability dialog carries when the
            // certificate could not be read, so that case goes back to the user
            if (installedInfo.patchStateUnknown) {
                stopHelperHandoff(packageName)
                return@launch
            }

            handleInstalledApkSelection()
        }
    }

    /**
     * Ends a helper hand-off at the APK availability dialog, so the remaining sources stay one tap
     * away instead of the user landing back on the home screen with nothing to act on.
     */
    private suspend fun stopHelperHandoff(packageName: String, @StringRes message: Int? = null) {
        processingApkSelection = false
        message?.let { app.toast(app.getString(it)) }

        if (pendingSavedApkInfo == null) {
            pendingSavedApkInfo = withContext(Dispatchers.IO) { localApkSources.saved(packageName) }
        }
        showApkAvailabilityDialog = true
    }

    /**
     * Loads all user-installed apps and opens the picker dialog.
     * Called from the "Other apps" file-picker prompt when the user taps "Use installed app".
     */
    fun loadInstalledAppsForPicker() {
        if (loadingInstalledApps) return
        viewModelScope.launch {
            installedAppsForPicker = emptyList()
            loadingInstalledApps = true
            showFilePickerPromptDialog = false
            showInstalledAppPickerDialog = true
            try {
                val items = withContext(Dispatchers.IO) {
                    try {
                        pm.getInstalledPackages()
                            .mapNotNull { pkgInfo ->
                                if (pkgInfo.packageName == app.packageName) return@mapNotNull null
                                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                                val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
                                if (!File(sourceDir).exists()) return@mapNotNull null
                                val version = pkgInfo.versionName?.takeUnless { it.isBlank() }
                                    ?: return@mapNotNull null
                                val label = with(pm) { pkgInfo.label() }
                                val splitPaths = appInfo.splitSourceDirs
                                    ?.filter { File(it).exists() }
                                    .orEmpty()
                                InstalledAppPickerItem(
                                    packageName = pkgInfo.packageName,
                                    label = label,
                                    packageInfo = pkgInfo,
                                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                                    info = InstalledApkInfo(
                                        version = version,
                                        versionCode = pm.getVersionCode(pkgInfo),
                                        apkPath = sourceDir,
                                        splitPaths = splitPaths
                                    )
                                )
                            }
                            .sortedBy { it.label.lowercase() }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to load installed apps for picker", e)
                        emptyList()
                    }
                }
                installedAppsForPicker = items
            } finally {
                loadingInstalledApps = false
            }
        }
    }

    /**
     * Handles selection of an app from the universal-patch installed-app picker.
     * Extracts the APK (or packs splits into an archive) and sends it through the patch flow.
     */
    fun handleInstalledAppPickerSelection(item: InstalledAppPickerItem) {
        showInstalledAppPickerDialog = false
        pendingAppName = item.label
        viewModelScope.launch {
            processingApkSelection = true
            try {
                val selectedApp = withContext(Dispatchers.IO) {
                    try {
                        if (item.info.isSplit) {
                            val archive = File(filesystem.uiTempDir, "${item.packageName}_installed.apks")
                            createApksArchive(item.info, archive)
                            SelectedApp.Local(
                                packageName = item.packageName,
                                version = item.info.version,
                                versionCode = item.info.versionCode,
                                file = archive,
                                temporary = true,
                                fromInstalledDevice = true
                            )
                        } else {
                            val source = File(item.info.apkPath)
                            if (!source.exists()) return@withContext null
                            val tempFile = File(filesystem.uiTempDir, "${item.packageName}_installed.apk")
                            source.copyTo(tempFile, overwrite = true)
                            SelectedApp.Local(
                                packageName = item.packageName,
                                version = item.info.version,
                                versionCode = item.info.versionCode,
                                file = tempFile,
                                temporary = true,
                                fromInstalledDevice = true
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to prepare APK from picker", e)
                        null
                    }
                }
                if (selectedApp != null) {
                    // Installed APK may be signed with our keystore - skip signature check.
                    // Version/versionCode check still runs via processSelectedApp.
                    processSelectedApp(selectedApp, skipSplitCheck = true)
                } else {
                    app.toast(app.getString(R.string.home_invalid_apk_io_error))
                    cleanupPendingData()
                }
            } finally {
                processingApkSelection = false
            }
        }
    }

    /**
     * Packs [info]'s base APK and all split APKs into an APKS archive (ZIP).
     * Entry names preserve the original filenames so [SplitApkPreparer] can
     * identify the base entry by the "base" substring and filter ABI/density splits.
     */
    private fun createApksArchive(info: InstalledApkInfo, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            fun addEntry(file: File) {
                // APKs are already compressed ZIPs - use STORED to avoid wasting CPU on deflate.
                // STORED requires CRC32 and size known upfront, so we read the file twice.
                val crc = CRC32()
                val buf = ByteArray(65536)
                FileInputStream(file).use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } >= 0) crc.update(buf, 0, n)
                }
                val entry = ZipEntry(file.name).apply {
                    method = ZipEntry.STORED
                    size = file.length()
                    compressedSize = file.length()
                    this.crc = crc.value
                }
                zip.putNextEntry(entry)
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            addEntry(File(info.apkPath))
            info.splitPaths.forEach { addEntry(File(it)) }
        }
    }

    /**
     * Called when an APK is shared to Morphe via the system share sheet.
     * Shows a toast and returns early if expert mode is disabled.
     * Otherwise, waits until [installedAppsLoading] is false before triggering [handleApkSelection].
     */
    fun handleExternalApkUri(uri: Uri) {
        viewModelScope.launch {
            if (!isExpertMode()) {
                app.toast(app.getString(R.string.home_external_apk_expert_mode_required))
                return@launch
            }
            // Wait for patches to be ready. Cap at 30 s to avoid hanging forever when
            // no patch sources are configured (installedAppsLoading never clears in that case)
            withTimeoutOrNull(30.seconds) {
                snapshotFlow { installedAppsLoading }.first { !it }
            }
            handleApkSelection(uri)
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
            processingApkSelection = true
            try {
                val result = withContext(Dispatchers.IO) {
                    loadLocalApk(app, uri)
                }

                when (result) {
                    is ApkLoadResult.Success -> processSelectedApp(result.app)
                    is ApkLoadResult.Unreadable -> app.toast(app.getString(R.string.home_invalid_apk_unreadable))
                    is ApkLoadResult.NotAnApk -> app.toast(app.getString(R.string.home_invalid_apk_not_an_apk))
                    is ApkLoadResult.IoError -> app.toast(app.getString(R.string.home_invalid_apk_io_error))
                }
            } finally {
                processingApkSelection = false
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
            processingApkSelection = true
            try {
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
                            versionCode = savedInfo.versionCode,
                            file = file,
                            temporary = false // Don't delete saved APK files
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to load saved APK", e)
                        null
                    }
                }

                if (selectedApp != null) {
                    // Saved file may be signed with our keystore - skip signature check.
                    // Version/versionCode check still runs via processSelectedApp.
                    processSelectedApp(selectedApp, skipSplitCheck = true)
                } else {
                    cleanupPendingData()
                }
            } finally {
                processingApkSelection = false
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
                cleanupPendingData(keepSelectedApp = true, keepBundleUid = true)
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
                                    pm.getApkFileSignatureHashes(extracted.file).any { it in expectedSignatures }
                                } finally {
                                    extracted.cleanup()
                                }
                            }
                        } else {
                            pm.getApkFileSignatureHashes(selectedApp.file).any { it in expectedSignatures }
                        }
                    }
                    if (!signatureMatch) {
                        pendingSelectedApp = selectedApp
                        showInvalidSignatureDialog = InvalidSignatureDialogState(
                            packageName = selectedApp.packageName,
                            appName = pendingAppName ?: KnownApps.getAppName(selectedApp.packageName)
                        )
                        cleanupPendingData(keepSelectedApp = true, keepBundleUid = true)
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
                .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version, selectedApp.versionCode)
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
            val allBundled = compatibleVersions[selectedApp.packageName] ?: emptyList()
            pendingSelectedApp = selectedApp
            showUnsupportedVersionDialog = UnsupportedVersionDialogState(
                packageName = selectedApp.packageName,
                version = selectedApp.version ?: "unknown",
                versionCode = selectedApp.versionCode,
                recommendedVersion = recommendedVersion,
                compatibleVersionNames = allBundled.mapNotNull { it.target.version },
                compatibleVersionDescriptions = allBundled.mapNotNull { b ->
                    val v = b.target.version ?: return@mapNotNull null
                    val d = b.target.description ?: return@mapNotNull null
                    v to d
                }.toMap(),
                compatibleVersionCodes = allBundled.mapNotNull { b ->
                    val v = b.target.version ?: return@mapNotNull null
                    val codes = b.buildCodes ?: return@mapNotNull null
                    v to codes
                }.toMap(),
                isExperimental = isVersionExperimental
            )
            cleanupPendingData(keepSelectedApp = true, keepBundleUid = true)
            return
        }

        // If the version is experimental, show the appropriate warning:
        // - Experimental mode ON → ExperimentalVersionWarningDialog
        // - Experimental mode OFF → UnsupportedVersionWarningDialog
        if (isVersionExperimental && !allowIncompatible) {
            val recommendedVersion = recommendedVersions[selectedApp.packageName]
            val allBundled = compatibleVersions[selectedApp.packageName] ?: emptyList()
            pendingSelectedApp = selectedApp
            val state = UnsupportedVersionDialogState(
                packageName = selectedApp.packageName,
                version = selectedApp.version ?: "unknown",
                versionCode = selectedApp.versionCode,
                recommendedVersion = recommendedVersion,
                compatibleVersionNames = allBundled.mapNotNull { it.target.version },
                compatibleVersionDescriptions = allBundled.mapNotNull { b ->
                    val v = b.target.version ?: return@mapNotNull null
                    val d = b.target.description ?: return@mapNotNull null
                    v to d
                }.toMap(),
                compatibleVersionCodes = allBundled.mapNotNull { b ->
                    val v = b.target.version ?: return@mapNotNull null
                    val codes = b.buildCodes ?: return@mapNotNull null
                    v to codes
                }.toMap(),
                isExperimental = true
            )
            if (isExperimentalModeEnabled) {
                showExperimentalVersionDialog = state
            } else {
                showUnsupportedVersionDialog = state
            }
            cleanupPendingData(keepSelectedApp = true, keepBundleUid = true)
            return
        }

        // Patches exist and are applicable → proceed.
        // For root-capable devices, we must know the patch mode BEFORE patching
        // because it is the install target patches declare their availability against.
        // Show the pre-patching mode dialog so the user can choose.
        // For non-root devices, just proceed - installer selection happens after patching.
        processSelectedAppIgnoringSignature(selectedApp)
    }

    /**
     * Skips all preliminary checks (signature, version, bundle) and routes directly to patching.
     * Used when the user confirms proceeding despite a signature mismatch, or when patching
     * from the installed app where checks are not applicable.
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
        val allBundles = patchBundleRepository
            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version, selectedApp.versionCode)
            .first()

        if (allBundles.isEmpty()) {
            app.toast(app.getString(R.string.home_no_patches_available))
            cleanupPendingData()
            return
        }

        // Create bundles map for validation
        val bundlesMap = allBundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }

        // Apply patch-declared rules first, then keep the legacy GmsCore filter as a safety
        // net for bundles that have not adopted the availability API
        // TODO: Drop this fallback together with PatchSelectionUtils.filterGmsCore
        @Suppress("DEPRECATION")
        fun PatchSelection.applyInstallerRules(): PatchSelection =
            applyAvailability(currentInstallerType, currentApkArchitecture, bundlesMap)
                .let { if (usingMountInstall) it.filterGmsCore() else it }

        if (isExpertMode()) {
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

                        // Among the genuinely new patches, auto-select those enabled by default
                        val newDefaultEnabled = bundle.patches
                            .filter { it.name in newPatchNames && it.defaultSelected(currentInstallerType, currentApkArchitecture) }
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
                allBundles.toPatchSelection(allowIncompatible) { _, patch ->
                    patch.defaultSelected(currentInstallerType, currentApkArchitecture)
                }
            }.applyInstallerRules()

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
            // Simple Mode: collect patches from all enabled bundles, then either use the sole result directly
            // or ask the user to pick one if multiple bundles have applicable patches.
            // A patch is applicable if:
            //   - compatiblePackages == null (universal), OR
            //   - compatiblePackages contains this packageName
            val bundleWithPatches = allBundles
                .filter { it.enabled }
                .map { bundle ->
                    val patchNames = bundle.patchSequence(allowIncompatible)
                        .filter { it.defaultSelected(currentInstallerType, currentApkArchitecture) }
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
                savedSelections.applyInstallerRules().toMutableMap()
                    .also { expertModePatches = it; expertModeInitialPatches = it }
                expertModeOptions = savedOptions.toMutableMap()
                showExpertModeDialog = true
                return
            }

            // If the user pre-selected a bundle via SimpleBundleSelectDialog, use it directly.
            // Use allowIncompatible=true unconditionally: the user explicitly picked this bundle
            // AND explicitly picked the APK version, so we must respect both choices regardless
            // of whether the version is in the bundle's supported list
            val preSelectedUid = pendingSelectedBundleUid
            if (preSelectedUid != null) {
                pendingSelectedBundleUid = null
                val bundle = allBundles.find { it.uid == preSelectedUid }
                if (bundle != null) {
                    val patchNames = bundle.patchSequence(allowIncompatible = true)
                        .filter { it.defaultSelected(currentInstallerType, currentApkArchitecture) }
                        .mapTo(mutableSetOf()) { it.name }
                    if (patchNames.isNotEmpty()) {
                        val patches = mapOf(bundle.uid to patchNames).applyInstallerRules()
                        proceedWithPatching(selectedApp, patches, emptyMap())
                        return
                    }
                }
                // Pre-selected bundle has no patches at all - fall through
            }

            // Simple mode: if more than one bundle has applicable patches, ask the user which single bundle to use
            if (bundleWithPatches.size > 1) {
                simpleBundleSelectApp = selectedApp
                simpleBundleSelectCandidates = bundleWithPatches
                showSimpleBundleSelectDialog = true
                return
            }

            // Only one bundle has patches - use it directly (no prompt needed)
            val patches = bundleWithPatches
                .associate { (bundle, patches) -> bundle.uid to patches }
                .applyInstallerRules()

            proceedWithPatching(selectedApp, patches, emptyMap())
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
        // Dismiss InstalledAppInfoDialog here, right before navigating to PatcherScreen.
        // This ensures there is never a gap between the info dialog closing and the next screen appearing
        dismissInstalledAppInfo()

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
        pendingSelectedBundleUid = null
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
        // Locked patches are toggled only through availability rules; no-op here
        val patch = expertModeBundles
            .firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.firstOrNull { it.name == patchName }
        if (patch != null && patch.lockState(currentInstallerType, currentApkArchitecture) != PatchLockState.NONE) return

        expertModePatches = expertModePatches.togglePatch(bundleUid, patchName)
    }

    /**
     * Select all given patches for a bundle.
     * Only adds patches that are not already selected. LOCKED_OFF patches are skipped.
     *
     * Universal patches are staged behind the regular ones and need a second call, see
     * [PatchSelectionUtils.bulkEnablePatches]. [patches] is the list the dialog currently
     * shows, so an active search narrows the scope of both stages.
     */
    fun expertModeSelectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val selected = expertModePatches[bundleUid].orEmpty()
        val updated = bulkEnablePatches(
            patches,
            selected,
            expertModeUniversalArmed(bundleUid, selected),
            ::expertModeLockState
        )

        expertModePatches = expertModePatches.toMutableMap().apply { put(bundleUid, updated) }
        expertModeUniversalArmedFor = bundleUid to updated
    }

    /** True when the next [expertModeSelectAll] holds universal patches back for another tap. */
    fun expertModeSelectAllHoldsUniversal(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>): Boolean {
        val selected = expertModePatches[bundleUid].orEmpty()
        return bulkEnableHoldsUniversal(
            patches,
            expertModeUniversalArmed(bundleUid, selected),
            ::expertModeLockState
        )
    }

    private fun expertModeLockState(patch: PatchInfo) =
        patch.lockState(currentInstallerType, currentApkArchitecture)

    private fun expertModeUniversalArmed(bundleUid: Int, selected: Set<String>) =
        expertModeUniversalArmedFor == (bundleUid to selected)

    /**
     * Deselect all given patches for a bundle.
     * Removes the bundle entry entirely if nothing remains selected. LOCKED_ON patches are kept.
     */
    fun expertModeDeselectAll(bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) {
        val current = expertModePatches.toMutableMap()
        val set = current[bundleUid]?.toMutableSet() ?: mutableSetOf()
        patches.forEach { (patch, enabled) ->
            if (patch.lockState(currentInstallerType, currentApkArchitecture) == PatchLockState.LOCKED_ON) return@forEach
            if (enabled) set.remove(patch.name)
        }
        if (set.isEmpty()) current.remove(bundleUid) else current[bundleUid] = set
        expertModePatches = current
    }

    /**
     * Reset a bundle's selection to the default patches for the current install target.
     * [allPatches] is the full unfiltered list for that bundle so defaults
     * are computed from the complete set, not just search results.
     */
    fun expertModeResetToDefault(bundleUid: Int, allPatches: List<Pair<PatchInfo, Boolean>>) {
        val defaults = allPatches
            .filter { (patch, _) -> patch.defaultSelected(currentInstallerType, currentApkArchitecture) }
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
        expertModeUniversalArmedFor = null
        closeExpertModeCopyDialog()
    }

    /**
     * Open the copy-from-another-bundle picker for [targetBundleUid] inside the current
     * expert-mode session. Candidates are loaded off the main thread and published to
     * [expertModeCopyCandidates] once ready.
     */
    fun openExpertModeCopyDialog(targetBundleUid: Int) {
        val selectedApp = expertModeSelectedApp ?: return
        expertModeCopyTargetBundleUid = targetBundleUid
        expertModeCopyCandidates = null
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = loadCopySelectionCandidates(
                patchSelectionRepository = patchSelectionRepository,
                patchBundleRepository = patchBundleRepository,
                appDataResolver = appDataResolver,
                targetPackageName = selectedApp.packageName,
                targetBundleUid = targetBundleUid,
                targetPatchNames = targetBundlePatchNames(targetBundleUid)
            )
            withContext(Dispatchers.Main) {
                // Discard the result if the user closed or retargeted the dialog while loading.
                if (expertModeCopyTargetBundleUid == targetBundleUid) {
                    expertModeCopyCandidates = candidates
                }
            }
        }
    }

    fun closeExpertModeCopyDialog() {
        expertModeCopyTargetBundleUid = null
        expertModeCopyCandidates = null
    }

    /**
     * Apply a picked [candidate] to the in-memory expert-mode selection.
     * Patches and options are filtered against the target bundle's schema so the
     * copy silently drops entries that no longer exist under the new bundle uid.
     * Changes are persisted to the database only when the user proceeds to patching.
     */
    fun applyExpertModeCopy(candidate: CopySelectionCandidate) {
        expertModeSelectedApp ?: return
        val targetBundleUid = expertModeCopyTargetBundleUid ?: return

        viewModelScope.launch {
            val (patches, options) = withContext(Dispatchers.IO) {
                val targetPatches = targetBundlePatchInfos(targetBundleUid)
                val sourcePatchNames = patchSelectionRepository.exportForPackageAndBundle(
                    candidate.packageName,
                    candidate.bundleUid
                )
                val filteredPatches = sourcePatchNames
                    .filter { it in targetPatches }
                    .toSet()

                // Read as live values rather than through the raw export, which hands back
                // JSON encoded strings
                val filteredOptions = optionsRepository.getOptionsForBundle(
                    packageName = candidate.packageName,
                    bundleUid = candidate.bundleUid,
                    bundlePatchInfo = targetPatches
                ).filterValues { it.isNotEmpty() }

                filteredPatches to filteredOptions
            }

            if (patches.isEmpty() && options.isEmpty()) {
                app.toast(app.getString(R.string.expert_mode_copy_from_bundle_no_patches))
                closeExpertModeCopyDialog()
                return@launch
            }

            val updatedSelection = expertModePatches.toMutableMap()
            if (patches.isEmpty()) updatedSelection.remove(targetBundleUid)
            else updatedSelection[targetBundleUid] = patches
            // The copy comes from a run that may have targeted another installer, so the patches
            // it carries are put through the availability rules of this one
            expertModePatches = updatedSelection.applyExpertModeAvailability()

            val currentOptions = expertModeOptions.toMutableMap()
            val bundleOptions = currentOptions[targetBundleUid]?.toMutableMap() ?: mutableMapOf()
            options.forEach { (patchName, patchOptions) ->
                bundleOptions[patchName] = patchOptions
            }
            if (bundleOptions.isEmpty()) currentOptions.remove(targetBundleUid)
            else currentOptions[targetBundleUid] = bundleOptions
            expertModeOptions = currentOptions

            app.toast(
                app.resources.getQuantityString(
                    R.plurals.expert_mode_copy_from_bundle_done,
                    patches.size,
                    patches.size
                )
            )
            closeExpertModeCopyDialog()
        }
    }

    /** Availability rules of the current install target, scoped to the bundles the dialog shows. */
    private fun PatchSelection.applyExpertModeAvailability(): PatchSelection =
        applyAvailability(
            currentInstallerType,
            currentApkArchitecture,
            expertModeBundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
        )

    private fun targetBundlePatchNames(bundleUid: Int): Set<String> =
        expertModeBundles.firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.mapTo(mutableSetOf()) { it.name }
            ?: emptySet()

    private fun targetBundlePatchInfos(bundleUid: Int): Map<String, PatchInfo> =
        expertModeBundles.firstOrNull { it.uid == bundleUid }
            ?.patches
            ?.associateBy { it.name }
            ?: emptyMap()

    private suspend fun saveSeenPatchesForBundles(packageName: String) {
        expertModeBundles.forEach { bundle ->
            patchSelectionRepository.saveSeenPatches(
                packageName = packageName,
                bundleUid = bundle.uid,
                patchNames = bundle.patches.map { it.name }.toSet()
            )
        }
    }

    /**
     * Called when the user confirms the ExpertModeDialog. Persists the final selection
     * and options, then navigates to the patcher.
     */
    fun proceedExpertMode() {
        val selectedApp = expertModeSelectedApp ?: return
        val finalPatches = expertModePatches
        val finalOptions = expertModeOptions
        // Only the bundles offered by the dialog are covered by this selection, so bundles that
        // were disabled at patch time keep whatever the user saved for them earlier.
        val bundleScope = expertModeBundles.mapTo(mutableSetOf()) { it.uid }
        // Strip UI-only empty strings (fields cleared via ✕) so the patcher engine
        // receives null / no key for those options and falls back to its own default,
        // rather than receiving a literal empty string.
        val patcherOptions = finalOptions.sanitizeForPatcher()

        showExpertModeDialog = false

        viewModelScope.launch(Dispatchers.IO) {
            patchSelectionRepository.updateSelection(
                packageName = selectedApp.packageName,
                selection = finalPatches,
                scope = bundleScope
            )
            saveOptions(selectedApp.packageName, finalOptions)
            // Snapshot all bundle patch names so next open can detect genuinely new patches.
            saveSeenPatchesForBundles(selectedApp.packageName)
            withContext(Dispatchers.Main) {
                proceedWithPatching(selectedApp, finalPatches, patcherOptions)
                cleanupExpertModeData()
            }
        }
    }

    /**
     * Resolve download redirect.
     */
    fun resolveDownloadRedirect() {
        val packageName = pendingPackageName ?: return
        // Use the version selected by the user in Dialog 1; fall back to recommended
        val version = (pendingSelectedDownloadVersion ?: pendingRecommendedVersion)?.version

        // Marks the destination as unknown, which is what the dialog waits on before it can
        // say anything about the download or let the user leave for it
        resolvedDownloadUrl = downloadUrlResolver.apiSearchUrl(packageName, version)

        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                downloadUrlResolver.resolve(packageName, version)
            }
            resolvedDownloadUrl = resolved
        }
    }

    /**
     * Whether the APK about to be selected will have its signature checked. False on Android 8-10,
     * where signatures cannot be read from an archive, and when the bundle declares none.
     */
    val pendingApkSignatureCheckAvailable: Boolean
        get() {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) return false
            val packageName = pendingPackageName ?: return false
            return !bundleAppMetadataFlow.value[packageName]?.signatures.isNullOrEmpty()
        }

    /**
     * Build the request for an APK download helper, describing the original APK of the pending app.
     *
     * @param component The helper activity the user picked, addressed explicitly.
     */
    fun createApkDownloadHelperIntent(component: ComponentName): Intent? {
        val packageName = pendingPackageName ?: return null
        val appName = pendingAppName ?: KnownApps.getAppName(packageName)
        // Use the version selected by the user in Dialog 1; fall back to recommended
        val requestedVersion = pendingSelectedDownloadVersion ?: pendingRecommendedVersion
        val apkFileType = bundleAppMetadataFlow.value[packageName]?.apkFileType

        val requestedVersionCodes = pendingCompatibleVersions
            .filter { it.target.version == requestedVersion?.version }
            .flatMap { it.buildCodes.orEmpty() }
            .distinct()
            .map(Int::toLong)
            .toLongArray()

        return ApkDownloadHelperContract.createRequestIntent(
            component = component,
            callerPackage = app.packageName,
            packageName = packageName,
            appName = appName,
            versionName = requestedVersion?.version,
            versionCodes = requestedVersionCodes,
            compatibleVersionNames = pendingCompatibleVersions.mapNotNull { it.target.version }.distinct(),
            supportedAbis = Build.SUPPORTED_ABIS,
            fileType = apkFileType?.toHelperFileType(),
            // Mirrors processSelectedApp - only a required plain APK rules split archives out
            allowSplitArchive = !(apkFileType?.isApk == true && apkFileType.isRequired),
            stockInstallRequired = usingMountInstall && pendingTargetAppInstalled != true,
            fallbackWebUrl = downloadUrlResolver.webSearchUrl(packageName, requestedVersion?.version)
        )
    }

    /** Map the bundle's file type onto the stable string constants of the helper protocol. */
    private fun ApkFileType.toHelperFileType() = when {
        isApk -> ApkDownloadHelperContract.FILE_TYPE_APK
        isApkM -> ApkDownloadHelperContract.FILE_TYPE_APKM
        isApkS -> ApkDownloadHelperContract.FILE_TYPE_APKS
        isXApk -> ApkDownloadHelperContract.FILE_TYPE_XAPK
        else -> null
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
    fun cleanupPendingData(keepSelectedApp: Boolean = false, keepBundleUid: Boolean = false) {
        pendingPackageName = null
        pendingAppName = null
        pendingRecommendedVersion = null
        pendingCompatibleVersions = emptyList()
        pendingRecommendedBundleVersions = emptyMap()
        pendingSelectedDownloadVersion = null
        if (!keepBundleUid) pendingSelectedBundleUid = null
        resolvedDownloadUrl = null
        pendingSavedApkInfo = null
        pendingInstalledApkInfo = null
        pendingTargetAppInstalled = null
        if (!keepSelectedApp) {
            pendingSelectedApp?.let { app ->
                if (app is SelectedApp.Local && app.temporary) {
                    app.file.delete()
                }
            }
            pendingSelectedApp = null
        }
        showApkAvailabilityDialog = false
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
        showInstalledAppPickerDialog = false
    }

    /**
     * Clean up any pending temporary APK when the ViewModel is destroyed.
     * This handles the edge case where the user navigates away or the system destroys
     * the ViewModel while a temporary APK file is still held in pendingSelectedApp.
     */
    override fun onCleared() {
        runCatching { app.unregisterReceiver(packageChangeReceiver) }
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
                    versionCode = pm.getVersionCode(packageInfo),
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
