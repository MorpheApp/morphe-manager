/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.BatchRunState
import app.morphe.manager.domain.batch.SavedBatchQueue
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.domain.installer.BatchUninstaller
import app.morphe.manager.domain.manager.*
import app.morphe.manager.domain.repository.*
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.ui.model.*
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

/** Bundle update status for snackbar display. */
enum class BundleUpdateStatus {
    Updating, // Update in progress
    Success,  // Update completed successfully
    Offline,  // Could not run without internet
    Error     // Error occurred
}

/**
 * The home screen: its app list, the sources and their updates, and the patch flow its apps are
 * patched through, see [PatchFlow].
 */
class HomeViewModel(
    app: Application,
    private val messages: UserMessages,
    val patchBundleRepository: PatchBundleRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val managerUpdateRepository: ManagerUpdateRepository,
    val prefs: PreferencesManager,
    private val pm: PM,
    private val batchPatchCoordinator: BatchPatchCoordinator,
    versionCatalog: AppVersionCatalog,
    private val batchUninstaller: BatchUninstaller,
    networkInfo: NetworkInfo
) : ViewModel(), KoinComponent {

    /** The app list on the home screen. */
    val apps: HomeApps = get { parametersOf(viewModelScope) }

    /** The patch sources: adding, importing and changing them, and the dialogs that go with it. */
    val sources: HomeSources = get { parametersOf(viewModelScope, apps) }

    val availablePatches = patchBundleRepository.bundleInfoFlow.map { it.values.sumOf { bundle -> bundle.patches.size } }
    val bundleUpdateProgress = patchBundleRepository.bundleUpdateProgress

    /** Whether a source download is held up by the device being offline. */
    val bundleDownloadWaiting: StateFlow<Boolean> = networkInfo
        .offlineWhile(bundleUpdateProgress.map { it?.phase == PatchBundleRepository.BundleUpdatePhase.Downloading })
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val contentResolver: ContentResolver = app.contentResolver

    /** Android 11 kills the app process after granting the "install apps" permission. */
    val android11BugActive get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !pm.canInstallPackages()

    var updatedManagerVersion: String? by mutableStateOf(null)
        private set

    // Dialog visibility states
    var showAndroid11Dialog by mutableStateOf(false)

    /**
     * Opens a file another app handed over. Messengers rename what they pass on, so a patch source is
     * told by its contents; anything else shared goes on as an APK, and anything else opened is refused.
     */
    fun openIncomingFile(uri: Uri, shared: Boolean) {
        viewModelScope.launch {
            when {
                withContext(Dispatchers.IO) { uri.isPatchBundle(contentResolver) } -> {
                    dismissOpenDialogs()
                    sources.openMpp(uri)
                }
                shared -> handleExternalApkUri(uri)
                else -> messages.show(R.string.incoming_file_not_patch_source)
            }
        }
    }

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    /** Pull-to-refresh state. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Loading state for installed apps
    var installedAppsLoading by mutableStateOf(true)

    // Bundle data - reactive StateFlows derived directly from bundleInfoFlow
    private val compatibleVersionsFlow: StateFlow<Map<String, List<BundledAppTarget>>> =
        versionCatalog.compatibleVersions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Convenience accessor - reads the current value synchronously for non-reactive call sites
    val compatibleVersions: Map<String, List<BundledAppTarget>> get() = compatibleVersionsFlow.value

    /**
     * Sets up a patching run for one app, and the dialogs it goes through. Given the scope of this
     * screen to run in and the version data the app list reads too, the rest comes from Koin.
     */
    val patchFlow: PatchFlow = get { parametersOf(viewModelScope, compatibleVersionsFlow) }

    init {
        observeManagerUpdate()
        // As the sources are checked on launch, see PatchBundleRepository.updateCheck
        viewModelScope.launch {
            if (prefs.autoUpdates.get().checksOnLaunch) checkForManagerUpdates()
        }
        observeLoadingState()
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
                // An update that ended without patches, offline on a first launch for one, brings
                // nothing more to wait for
                val updateEnded = progress?.result in setOf(
                    PatchBundleRepository.BundleUpdateResult.NoInternet,
                    PatchBundleRepository.BundleUpdateResult.Error,
                    PatchBundleRepository.BundleUpdateResult.UpdatesAvailable
                )
                // If no sources are enabled (and we know the DB has been read), there is nothing
                // to load - this is a valid terminal state, not a loading state.
                val hasLoadedData = sourcesInitialized &&
                        (!hasEnabledSources || installedApps.isNotEmpty() || patchCount > 0 || updateEnded)
                isBundleUpdateInProgress || !hasLoadedData
            }
                .distinctUntilChanged()
                .collect { loading ->
                    installedAppsLoading = loading
                }
        }
    }

    /**
     * Reactively maps bundle update progress to snackbar visibility and status.
     */
    private fun observeSnackbarState() {
        viewModelScope.launch {
            combine(patchBundleRepository.bundleUpdateProgress, _isRefreshing, ::Pair).collect { (progress, refreshing) ->
                // A pull spins its own indicator while the check runs, so the alert only tells how it ended,
                // and a quiet check shows nothing until something is downloading.
                // Updates left to download stay on their own alert, which outlasts this one
                val checkingQuietly = progress?.quiet == true &&
                        progress.phase == PatchBundleRepository.BundleUpdatePhase.Checking
                val status = when (progress?.result) {
                    null, PatchBundleRepository.BundleUpdateResult.UpdatesAvailable -> null
                    PatchBundleRepository.BundleUpdateResult.None ->
                        BundleUpdateStatus.Updating.takeUnless { refreshing || checkingQuietly }
                    PatchBundleRepository.BundleUpdateResult.Success,
                    PatchBundleRepository.BundleUpdateResult.NoUpdates -> BundleUpdateStatus.Success
                    PatchBundleRepository.BundleUpdateResult.NoInternet -> BundleUpdateStatus.Offline
                    PatchBundleRepository.BundleUpdateResult.Error -> BundleUpdateStatus.Error
                }
                showBundleUpdateSnackbar = status != null
                status?.let { snackbarStatus = it }
            }
        }
    }

    /**
     * Triggers a manual refresh: updates bundles and checks for manager updates.
     * Guard against double-trigger if user swipes while refresh is in progress.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        // Reported by its own banner, so offline its timeout does not hold the indicator up
        triggerUpdateCheck()
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Awaited, so the indicator spins for as long as the check runs and stands in for
                // its result when nothing was new
                patchBundleRepository.updateCheckAndAwait(reportUpToDate = false)
                delay(500.milliseconds)
            } finally {
                _isRefreshing.value = false
            }
            apps.reload()
        }
    }

    /** True while a batch queue is patching, so callers can explain why a start was ignored. */
    val batchPatchRunning: Boolean get() = batchPatchCoordinator.isRunning

    /**
     * The batch queue, which outlives its screen: a deep link or shortcut returns to home while
     * it keeps patching, and home is then the only way back to it.
     */
    val batchRun: StateFlow<BatchRunState?> = batchPatchCoordinator.state

    /** The queue waiting to be started, which apps are added to from here. */
    val savedBatchQueue: StateFlow<SavedBatchQueue?> = batchPatchCoordinator.savedQueue

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
        messages.reportingFailure(R.string.failed_to_check_updates, "Failed to check for updates") {
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

    /** Checks the sources and the manager for updates, as a notification asked to. */
    suspend fun checkForUpdates() {
        patchBundleRepository.updateCheck()
        checkForManagerUpdates()
    }

    /**
     * Called when the app is opened via a deep link containing a source URL.
     * Shows a confirmation dialog instead of adding silently.
     */
    fun handleDeepLinkAddSource(source: MainViewModel.DeepLinkSource) {
        dismissOpenDialogs()
        sources.offerDeepLinkSource(source)
    }

    /**
     * Closes every open dialog and sheet so an incoming source confirmation is not stacked behind
     * one, and so the flow the user resumes afterward is rebuilt from the new set of sources.
     */
    private fun dismissOpenDialogs() {
        patchFlow.cancel()
        showAndroid11Dialog = false
        sources.dismissDialogs()
    }

    /**
     * Metadata for all apps across enabled bundles - display names, icon colors, signatures, etc.
     * Delegates to [PatchBundleRepository.appMetadata] as the single source of truth.
     */
    val bundleAppMetadataFlow: StateFlow<Map<String, BundleAppMetadata>> =
        patchBundleRepository.appMetadata

    /** What the sources call each app, see [PatchBundleRepository.appDisplayNames]. */
    val appDisplayNamesFlow: StateFlow<Map<String, String>> = patchBundleRepository.appDisplayNames

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

    /**
     * The package [item] opens as on the device, patched or not, or null when it is not there or
     * has nothing to open. Lets the user see what the app already does before patching it.
     */
    fun launchablePackage(item: HomeAppItem): String? =
        (item.installedApp?.currentPackageName ?: item.packageName)
            .takeIf { item.isInstalledOnDevice && pm.canLaunch(it) }

    fun openApp(packageName: String) = pm.launch(packageName)

    suspend fun persistReinstalledApp(
        app: InstalledApp,
        packageName: String,
        installType: InstallType
    ): Boolean = withContext(Dispatchers.IO) {
        installedAppRepository.recordReinstall(app, packageName, installType)
        apps.notifyAppStateChanged(packageName)
        if (packageName != app.currentPackageName) apps.notifyAppStateChanged(app.currentPackageName)
        true
    }

    fun uninstallApps(items: Collection<HomeAppItem>) {
        val installs = items.mapNotNull { it.installedApp }.distinctBy { it.currentPackageName }
        if (installs.isEmpty()) return

        viewModelScope.launch {
            batchUninstaller.uninstall(
                installs.map { BatchUninstaller.Target(it.currentPackageName, it.installType, tracked = it) }
            ) { target -> apps.notifyAppStateChanged(target.packageName) }
        }
    }

    /**
     * Handle app button click.
     */
    fun handleAppClick(
        packageName: String,
        availablePatches: Int,
        android11BugActive: Boolean,
        installedApp: InstalledApp?
    ) {
        // If app is installed, allow click even during updates
        if (installedApp != null) {
            return // Caller will handle navigation
        }

        // Check if patches are being fetched
        if (availablePatches <= 0) {
            messages.show(R.string.home_sources_are_loading)
            return
        }

        // Check for Android 11 installation bug
        if (android11BugActive) {
            showAndroid11Dialog = true
            return
        }

        patchFlow.showPatchDialog(packageName)
    }

    /**
     * Called when an APK is shared to Morphe via the system share sheet.
     * Shows a message and returns early if expert mode is disabled.
     * Otherwise, waits until [installedAppsLoading] is false before handing it to the patch flow.
     */
    private suspend fun handleExternalApkUri(uri: Uri) {
        if (!prefs.useExpertMode.get()) {
            messages.show(R.string.home_external_apk_expert_mode_required)
            return
        }
        // Wait for patches to be ready, capped in case the sources never answer
        withTimeoutOrNull(30.seconds) {
            snapshotFlow { installedAppsLoading }.first { !it }
        }
        patchFlow.handleApkSelection(uri)
    }

    override fun onCleared() {
        apps.close()
        // The system can destroy the ViewModel while a temporary APK is still held for a run
        patchFlow.close()
    }
}
