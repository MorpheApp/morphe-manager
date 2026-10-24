/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.platform.NetworkInfo
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.apk.*
import app.morphe.manager.domain.batch.BatchPatchCoordinator
import app.morphe.manager.domain.batch.BatchRunState
import app.morphe.manager.domain.batch.SavedBatchQueue
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.installer.UninstallCancelledException
import app.morphe.manager.domain.manager.*
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.repository.PatchBundleRepository.LocalFileCheck
import app.morphe.manager.patcher.patch.*
import app.morphe.manager.patcher.runtime.PatcherProcesses
import app.morphe.manager.ui.model.*
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.*
import app.morphe.patcher.patch.AppTarget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** How long an added source is waited on before its app list is no longer opened for it. */
private val NEW_SOURCE_LOAD_TIMEOUT = 10.minutes

/** Bundle update status for snackbar display. */
enum class BundleUpdateStatus {
    Updating, // Update in progress
    Success,  // Update completed successfully
    Warning,  // Patches may be outdated (on metered network, updates disabled)
    Error     // Error occurred (including no internet)
}

/**
 * The home screen: its app list, the sources and their updates, and the patch flow its apps are
 * patched through, see [PatchFlow].
 */
class HomeViewModel(
    private val app: Application,
    private val messages: UserMessages,
    val patchBundleRepository: PatchBundleRepository,
    private val installedAppRepository: InstalledAppRepository,
    originalApkRepository: OriginalApkRepository,
    patchSelectionRepository: PatchSelectionRepository,
    private val sourceMuteRepository: SourceMuteRepository,
    optionsRepository: PatchOptionsRepository,
    private val managerUpdateRepository: ManagerUpdateRepository,
    networkInfo: NetworkInfo,
    val prefs: PreferencesManager,
    private val pm: PM,
    private val rootInstaller: RootInstaller,
    private val installerManager: InstallerManager,
    filesystem: Filesystem,
    homeAppButtonPrefs: HomeAppButtonPreferences,
    appDataResolver: AppDataResolver,
    private val batchPatchCoordinator: BatchPatchCoordinator,
    downloadUrlResolver: DownloadUrlResolver,
    versionCatalog: AppVersionCatalog,
    private val localApkSources: LocalApkSources,
    /** Picker behind the copy-from-another-bundle action of the expert-mode dialog. */
    expertModeCopy: CopySelectionController
) : ViewModel(), KoinComponent {
    // Past the parameters viewModelOf takes
    private val patcherProcesses: PatcherProcesses by inject()

    /** The app list on the home screen. */
    val apps = HomeApps(
        scope = viewModelScope,
        app = app,
        patchBundleRepository = patchBundleRepository,
        installedAppRepository = installedAppRepository,
        originalApkRepository = originalApkRepository,
        sourceMuteRepository = sourceMuteRepository,
        prefs = prefs,
        pm = pm,
        filesystem = filesystem,
        homeAppButtonPrefs = homeAppButtonPrefs,
        appDataResolver = appDataResolver,
        versionCatalog = versionCatalog,
        localApkSources = localApkSources,
        rootInstaller = rootInstaller
    )

    val availablePatches = patchBundleRepository.bundleInfoFlow.map { it.values.sumOf { bundle -> bundle.patches.size } }
    val bundleUpdateProgress = patchBundleRepository.bundleUpdateProgress
    private val contentResolver: ContentResolver = app.contentResolver

    /** Android 11 kills the app process after granting the "install apps" permission. */
    val android11BugActive get() = Build.VERSION.SDK_INT == Build.VERSION_CODES.R && !pm.canInstallPackages()

    var updatedManagerVersion: String? by mutableStateOf(null)
        private set

    // Dialog visibility states
    var showAndroid11Dialog by mutableStateOf(false)
    var showAddSourceDialog by mutableStateOf(false)
    var bundleToRename by mutableStateOf<PatchBundleSource?>(null)
    var showRenameBundleDialog by mutableStateOf(false)

    /** Sources added with their apps to be chosen, whose app lists open one after another. */
    private var sourceAppsQueue by mutableStateOf<List<Int>>(emptyList())

    /** Source whose app list is open, the first of [sourceAppsQueue]. */
    val sourceAppsDialogUid: Int? get() = sourceAppsQueue.firstOrNull()

    /** Closes the open app list, which brings up the next queued one. */
    fun dismissSourceApps() {
        sourceAppsQueue = sourceAppsQueue.drop(1)
    }

    // Deep link: pending source to add via confirmation dialog
    var deepLinkPendingSource by mutableStateOf<MainViewModel.DeepLinkSource?>(null)
        private set

    /**
     * A .mpp file opened from a file manager, with its manifest and what importing it would do,
     * both null while they are read.
     */
    data class PendingMpp(val uri: Uri, val fileName: String?, val manifest: MppManifest?, val check: LocalFileCheck?)

    // .mpp file opened from file manager: pending confirmation dialog
    var pendingMpp by mutableStateOf<PendingMpp?>(null)
        private set

    fun setPendingMpp(uri: Uri) {
        dismissOpenDialogs()
        val pending = PendingMpp(uri, uri.displayName(contentResolver), manifest = null, check = null)
        pendingMpp = pending
        viewModelScope.launch {
            val (manifest, check) = inspectLocalFile(uri)
            // A file opened in the meantime, or a dismissed prompt, is not described by this one
            if (pendingMpp == pending) pendingMpp = pending.copy(manifest = manifest, check = check)
        }
    }

    /** Imports the opened file the way the add source dialog would, updating the source it is a version of. */
    fun confirmMppImport(chooseApps: Boolean) {
        val pending = pendingMpp ?: return
        pendingMpp = null
        importOf(pending.uri, pending.check)?.let { importLocalSources(listOf(it), chooseApps) }
    }

    fun dismissMppImport() {
        pendingMpp = null
    }

    /**
     * A file picked in the add source dialog, with the bundle name its manifest declares and what
     * importing it would do, both null while they are read.
     */
    data class PickedBundle(val uri: Uri, val name: String, val bundleName: String?, val check: LocalFileCheck?)

    /** Files picked in the add source dialog, kept here so they outlive the picker's round trip. */
    private var pickedBundles by mutableStateOf<List<PickedBundle>>(emptyList())

    /**
     * [pickedBundles] as they would import together: a file landing on the same source as an
     * earlier one adds nothing of its own. Two versions of one new bundle would each add a source,
     * so they are told apart by the bundle name, and the first picked is the one kept.
     */
    val pickedBundleImports: List<PickedBundle>
        get() {
            val targets = mutableSetOf<String>()
            return pickedBundles.map { picked ->
                val target = when (val check = picked.check) {
                    is LocalFileCheck.New -> picked.bundleName?.lowercase(Locale.US) ?: "uid:${check.uid}"
                    is LocalFileCheck.Update -> "uid:${check.uid}"
                    else -> null
                }
                if (target != null && !targets.add(target)) picked.copy(check = LocalFileCheck.Duplicate) else picked
            }
        }

    /** Adds files to the add source dialog and reads what importing each would do. */
    fun pickBundles(uris: List<Uri>) {
        val fresh = uris.distinct().filter { uri -> pickedBundles.none { it.uri == uri } }.map { uri ->
            val name = uri.displayName(contentResolver) ?: uri.toString()
            val isBundle = name.endsWith(".mpp", ignoreCase = true)
            PickedBundle(uri, name, bundleName = null, check = if (isBundle) null else LocalFileCheck.NotBundle)
        }
        pickedBundles = pickedBundles + fresh
        fresh.filter { it.check == null }.forEach { picked ->
            viewModelScope.launch {
                val (manifest, check) = inspectLocalFile(picked.uri)
                pickedBundles = pickedBundles.map {
                    if (it.uri == picked.uri) it.copy(bundleName = manifest?.name, check = check) else it
                }
            }
        }
    }

    /** The manifest of [uri] and what importing it would do, a file that cannot be read being no bundle. */
    private suspend fun inspectLocalFile(uri: Uri): Pair<MppManifest?, LocalFileCheck> {
        val manifest = withContext(Dispatchers.IO) { uri.readMppManifest(contentResolver) }
        val check = runCatching {
            patchBundleRepository.checkLocal(manifest?.name) { openLocalFile(uri) }
        }.getOrElse { LocalFileCheck.NotBundle }
        return manifest to check
    }

    /** Opens [uri] for reading, closed by whoever reads it. */
    @SuppressLint("Recycle")
    private fun openLocalFile(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Unable to open $uri")

    /**
     * The import [check] calls for, as [importLocalSources] takes it: a new bundle as a source of its
     * own, another version of a local source into it. Null for a file that adds nothing.
     */
    private fun importOf(uri: Uri, check: LocalFileCheck?): Pair<Uri, Int?>? = when (check) {
        is LocalFileCheck.New -> uri to null
        is LocalFileCheck.Update -> uri to check.uid
        else -> null
    }

    fun unpickBundle(uri: Uri) {
        pickedBundles = pickedBundles.filterNot { it.uri == uri }
    }

    fun clearPickedBundles() {
        pickedBundles = emptyList()
    }

    /**
     * Imports the picked files: a new bundle as a source of its own, and another version of a local
     * source into it, as its update action would. Files already added are left out.
     */
    fun importPickedBundles(chooseApps: Boolean) {
        val imports = pickedBundleImports.mapNotNull { importOf(it.uri, it.check) }
        pickedBundles = emptyList()
        importLocalSources(imports, chooseApps)
    }

    /** Local source waiting for a replacement file, so the picker result knows what it updates. */
    var localBundleUpdateUid by mutableStateOf<Int?>(null)

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Latches when an update cycle was skipped due to metered network; cleared on the next
    // successful/no-change update. Independent of the transient bundle update alert so the
    // user still sees a persistent alert after the transient one fades
    var updatesSkippedDueToMetered by mutableStateOf(false)

    // Loading state for installed apps
    var installedAppsLoading by mutableStateOf(true)

    // Bundle data - reactive StateFlows derived directly from bundleInfoFlow
    private val compatibleVersionsFlow: StateFlow<Map<String, List<BundledAppTarget>>> =
        versionCatalog.compatibleVersions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val recommendedVersionsFlow: StateFlow<Map<String, AppTarget>> =
        versionCatalog.recommendedVersions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Convenience accessor - reads the current value synchronously for non-reactive call sites
    val compatibleVersions: Map<String, List<BundledAppTarget>> get() = compatibleVersionsFlow.value

    /** Sets up a patching run for one app, and the dialogs it goes through. */
    val patchFlow = PatchFlow(
        scope = viewModelScope,
        app = app,
        messages = messages,
        patchBundleRepository = patchBundleRepository,
        originalApkRepository = originalApkRepository,
        patchSelectionRepository = patchSelectionRepository,
        sourceMuteRepository = sourceMuteRepository,
        optionsRepository = optionsRepository,
        networkInfo = networkInfo,
        prefs = prefs,
        pm = pm,
        rootInstaller = rootInstaller,
        installerManager = installerManager,
        filesystem = filesystem,
        appDataResolver = appDataResolver,
        batchPatchCoordinator = batchPatchCoordinator,
        downloadUrlResolver = downloadUrlResolver,
        localApkSources = localApkSources,
        compatibleVersions = compatibleVersionsFlow,
        recommendedVersions = recommendedVersionsFlow,
        expertModeCopy = expertModeCopy,
        patcherProcesses = patcherProcesses
    )

    init {
        observeManagerUpdate()
        triggerUpdateCheck()
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
                    PatchBundleRepository.BundleUpdateResult.SkippedMetered
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

    /**
     * Imports each file, as a new local source or into the one its uid names, one after another
     * under one import message. With [chooseApps] the app list of each new source opens once its
     * patches have loaded.
     */
    private fun importLocalSources(imports: List<Pair<Uri, Int?>>, chooseApps: Boolean) = viewModelScope.launch {
        val imported = withContext(NonCancellable) {
            val importing = messages.showUntilDismissed(R.string.importing_ellipsis)
            try {
                imports.map { (uri, replacingUid) -> replacingUid to importLocalFile(uri, replacingUid) }
            } finally {
                importing.dismiss()
            }
        }
        // A file that failed has already said why
        if (imported.any { (_, uid) -> uid != null }) messages.show(R.string.imported_successfully)
        // Two files carrying the same bundle land on the same source
        val added = imported.mapNotNull { (replacingUid, uid) -> uid.takeIf { replacingUid == null } }
        onSourcesAdded(added.distinct(), chooseApps)
    }

    /**
     * Adds a remote source for each of [urls], downloaded together as one update. With
     * [chooseApps] the app list of each added source opens once its patches have loaded, and each
     * one brings as much as [scope] asks for.
     */
    fun createRemoteSources(
        urls: List<String>,
        chooseApps: Boolean = false,
        scope: SourceScope = SourceScope.Whole
    ) = viewModelScope.launch {
        val added = withContext(NonCancellable) {
            patchBundleRepository.createRemotes(urls, autoUpdate = true, keptApps = scope.keptApps)
        }
        onSourcesAdded(added, chooseApps, scope)
    }

    /**
     * Queues the app list of each of [uids] that has apps to list as its patches load, then hints
     * at the swipe gestures of the source cards.
     *
     * A source only appears among the loaded ones once its patches are in, which is also what the
     * list is built from. The sources load together, so they are taken as each one arrives rather
     * than in turn, and one that never loads holds back none of the others.
     *
     * A source added for less than the whole of it has left out its other apps as it loaded, see
     * [PatchBundleRepository.createRemotes]. They were never offered, so none of them is new to the
     * user, and a source that turns out not to have what [scope] asks for is added as any other.
     */
    private suspend fun onSourcesAdded(uids: List<Int>, chooseApps: Boolean, scope: SourceScope = SourceScope.Whole) {
        if (uids.isEmpty()) return

        val pending = uids.toMutableList()
        val loaded = mutableMapOf<Int, PatchBundleInfo>()
        withTimeoutOrNull(NEW_SOURCE_LOAD_TIMEOUT) {
            patchBundleRepository.allBundlesInfoFlow.first { info ->
                pending.removeAll { uid ->
                    val bundle = info[uid] ?: return@removeAll false
                    loaded[uid] = bundle
                    // Universal patches put no app on the home screen, so there would be nothing to list
                    if (chooseApps && bundle.listedApps().isNotEmpty()) sourceAppsQueue += uid
                    true
                }
                pending.isEmpty()
            }
        }
        if (scope != SourceScope.Whole) withContext(NonCancellable) {
            loaded.forEach { (uid, bundle) ->
                if (scope.isMetBy(bundle)) sourceMuteRepository.markNewAppsSeen(uid)
                else sourceMuteRepository.resetForBundle(uid)
            }
        }
        delay(1.5.seconds)
        apps.triggerSwipeGestureHint()
    }

    /**
     * Points an existing local source at a newly picked file. Adding the updated file instead
     * would create a second source and strand the patch selection on the old one.
     */
    fun updateLocalSource(uid: Int, patchBundle: Uri) = importLocalSources(listOf(patchBundle to uid), chooseApps = false)

    /** @return The uid of the source [patchBundle] landed under, or null when it could not be read. */
    private suspend fun importLocalFile(patchBundle: Uri, replacingUid: Int?): Int? {
        val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        var persistedPermission = false
        // A cloud provider may fetch the whole file to answer this
        val size = withContext(Dispatchers.IO) {
            runCatching {
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
            }.getOrNull()
        }?.takeIf { it > 0L }
        try {
            contentResolver.takePersistableUriPermission(patchBundle, permissionFlags)
            persistedPermission = true
        } catch (_: SecurityException) {
            // Provider may not support persistable permissions; fall back to transient grant
        }

        val openStream: suspend () -> InputStream = { openLocalFile(patchBundle) }
        return try {
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

    /**
     * Called when the app is opened via a deep link containing a source URL.
     * Shows a confirmation dialog instead of adding silently.
     */
    fun handleDeepLinkAddSource(source: MainViewModel.DeepLinkSource) {
        dismissOpenDialogs()
        deepLinkPendingSource = source
    }

    /**
     * User confirmed adding the source from the deep link confirmation dialog.
     *
     * A link for one app from a source that is already here only asks for that app, so it is
     * brought back to the source rather than the source being refused as a duplicate.
     */
    fun confirmDeepLinkSource(chooseApps: Boolean) {
        val source = deepLinkPendingSource ?: return
        deepLinkPendingSource = null
        val scope = source.scope
        val existingUid = patchBundleRepository.existingSourceUid(source.url)
        if (scope is SourceScope.App && existingUid != null) {
            viewModelScope.launch { sourceMuteRepository.unmuteApps(existingUid, listOf(scope.packageName)) }
        } else {
            createRemoteSources(listOf(source.url), chooseApps, scope)
        }
    }

    /** User dismissed the deep link confirmation dialog. */
    fun dismissDeepLinkSource() {
        deepLinkPendingSource = null
    }

    /**
     * Closes every open dialog and sheet so an incoming source confirmation is not stacked behind
     * one, and so the flow the user resumes afterward is rebuilt from the new set of sources.
     */
    private fun dismissOpenDialogs() {
        patchFlow.cancel()
        showAndroid11Dialog = false
        showRenameBundleDialog = false
        bundleToRename = null
        showAddSourceDialog = false
        sourceAppsQueue = emptyList()
        pickedBundles = emptyList()
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

    suspend fun persistReinstalledApp(
        app: InstalledApp,
        packageName: String,
        installType: InstallType
    ): Boolean = withContext(Dispatchers.IO) {
        val appliedPatches = installedAppRepository.getAppliedPatches(app.currentPackageName)
        installedAppRepository.addOrUpdate(
            currentPackageName = packageName,
            originalPackageName = app.originalPackageName,
            isClone = app.isClone,
            version = app.version,
            installType = installType,
            patchSelection = appliedPatches,
            selectionPayload = app.selectionPayload,
            patchedAt = app.patchedAt
        )
        apps.notifyAppStateChanged(packageName)
        if (packageName != app.currentPackageName) apps.notifyAppStateChanged(app.currentPackageName)
        true
    }

    fun uninstallApps(items: Collection<HomeAppItem>) {
        val installs = items.mapNotNull { it.installedApp }.distinctBy { it.currentPackageName }
        if (installs.isEmpty()) return

        viewModelScope.launch {
            var completed = 0
            var skipped = 0
            var unverified = 0
            for (installed in installs) {
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
                    apps.notifyAppStateChanged(installed.currentPackageName)
                }.onFailure { error ->
                    skipped++
                    if (error !is UninstallCancelledException) {
                        messages.show(R.string.uninstall_app_fail, error.simpleMessage())
                    }
                }
            }

            if (unverified > 0) messages.show(R.string.uninstall_app_unverified)
            app.batchActionSummary(R.plurals.batch_uninstall_summary, completed, skipped)
                ?.let { messages.show(it) }
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
    fun handleExternalApkUri(uri: Uri) {
        viewModelScope.launch {
            if (!prefs.useExpertMode.get()) {
                messages.show(R.string.home_external_apk_expert_mode_required)
                return@launch
            }
            // Wait for patches to be ready, capped in case the sources never answer
            withTimeoutOrNull(30.seconds) {
                snapshotFlow { installedAppsLoading }.first { !it }
            }
            patchFlow.handleApkSelection(uri)
        }
    }

    override fun onCleared() {
        apps.close()
        // The system can destroy the ViewModel while a temporary APK is still held for a run
        patchFlow.close()
    }
}
