/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import androidx.core.content.ContextCompat
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.apk.*
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.avatarUrls
import app.morphe.manager.domain.manager.*
import app.morphe.manager.domain.repository.*
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.patcher.patch.BundleAppMetadata
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.ui.model.*
import app.morphe.manager.util.*
import app.morphe.patcher.patch.AppTarget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/** Keys whose evidence was added, removed, or replaced between two snapshots. */
internal fun <K, V> changedMapKeys(previous: Map<K, V>, current: Map<K, V>): Set<K> =
    (previous.keys + current.keys).filterTo(mutableSetOf()) { previous[it] != current[it] }

/**
 * The patch update waiting for an installed app: the source carrying it and the version the
 * app was patched with. [appNames] is empty when no changelog narrowed the update down.
 */
data class AppPatchUpdate(
    val bundleUid: Int,
    val patchedWithVersion: String?,
    val appNames: Set<String> = emptySet()
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

/** The preferences the cards themselves are built from: which are shown and in what order. */
internal data class HomePrefs(
    val hiddenPackages: Set<String>,
    val customOrder: List<String>,
    val sourceOrders: Map<Int, List<String>>,
    val sortMode: HomeAppSortMode
)

/**
 * The cards of one build, before the view preferences arrange them into a [HomeAppState].
 * Source groups come out expanded and are folded by what the user collapsed afterward.
 */
internal data class HomeCards(
    val visible: List<HomeAppItem>,
    val hidden: List<HomeAppItem>,
    val sortMode: HomeAppSortMode,
    val sourceGroups: List<HomeAppSourceGroup>
)

private data class HomeCategoryPrefs(
    val categoryState: HomeAppCategoryState,
    val categoryViewMode: HomeAppCategoryViewMode,
    val showCategoryViewSwitcher: Boolean,
    val expandedSourceGroups: Set<Int>
)

/**
 * The home screen's app list: the cards built from the bundles, the records and the device,
 * the verdicts on tracked installs behind them, and the preferences arranging them.
 *
 * Owned by [HomeViewModel], which runs it in its own scope and closes it with itself.
 */
class HomeApps(
    private val scope: CoroutineScope,
    private val app: Application,
    private val patchBundleRepository: PatchBundleRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val originalApkRepository: OriginalApkRepository,
    sourceMuteRepository: SourceMuteRepository,
    private val prefs: PreferencesManager,
    private val pm: PM,
    filesystem: Filesystem,
    private val homeAppButtonPrefs: HomeAppButtonPreferences,
    private val appDataResolver: AppDataResolver,
    versionCatalog: AppVersionCatalog,
    private val localApkSources: LocalApkSources
) {
    // Updates available for installed apps, null until the first check lands
    private val _appUpdatesAvailable = MutableStateFlow<Map<String, AppPatchUpdate>?>(null)
    val appUpdatesAvailable: StateFlow<Map<String, AppPatchUpdate>?> = _appUpdatesAvailable.asStateFlow()

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
    private fun observePackageChanges() = scope.launch {
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
    private fun observeSavedPatchedApkChanges() = scope.launch {
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
    private fun observeTrackedApps() = scope.launch {
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
                // A first bundle load refines the verdict, so the old one stays until the new lands
                previousBundleSignatures?.takeIf { it.isNotEmpty() }?.let {
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
    private val hasThirdPartySource: StateFlow<Boolean> =
        patchBundleRepository.sources
            .map { sources -> sources.any { it.enabled && it.uid != DEFAULT_SOURCE_UID } }
            .stateIn(scope, SharingStarted.Eagerly, false)

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
        ::HomePrefs
    )

    /**
     * The bundle state, the versions derived from it, the ones the user turned down and the
     * sources each app is kept from. A card is built against one reading of them, not against
     * several arriving a frame apart.
     */
    private data class HomeBundleState(
        val bundleState: PatchBundleRepository.BundleState,
        val supportedVersions: Map<String, AppTarget>,
        val ignoredVersions: Map<String, String>,
        val keptFrom: Map<String, Set<Int>>
    )

    private val _homeBundleStateFlow = combine(
        patchBundleRepository.bundleState,
        versionCatalog.recommendedVersions,
        homeAppButtonPrefs.ignoredVersions,
        sourceMuteRepository.mutedSources,
        ::HomeBundleState
    )

    /** The supported version each app was told to stop offering, keyed by its original package. */
    val ignoredAppVersions: StateFlow<Map<String, String>> = homeAppButtonPrefs.ignoredVersions

    /** Answers the offer to move [packageName] up to [version], leaving later ones to be offered. */
    fun ignoreSupportedVersion(packageName: String, version: String) =
        homeAppButtonPrefs.ignoreVersion(packageName, version)

    /** Undoes [ignoreSupportedVersion], so whatever the sources support is offered again. */
    fun stopIgnoringSupportedVersion(packageName: String) =
        homeAppButtonPrefs.stopIgnoringVersion(packageName)

    private val homeCardCache = HomeCardCache(filesystem.homeCardCacheDir.resolve("cards.json"))

    // Read once, shown until the bundles load
    private val cachedHomeCards by lazy { homeCardCache.read() }
    private val cachedUpdateIds by lazy { cachedHomeCards?.idsWithUpdate().orEmpty() }

    // Serial writes, off the path to the screen
    private val homeCardCacheWrites = Dispatchers.IO.limitedParallelism(1)

    /** Everything the home cards are built from, read together. */
    private data class HomeInputs(
        val bundle: HomeBundleState,
        val prefs: HomePrefs,
        val installedApps: List<InstalledApp>,
        val updates: Map<String, AppPatchUpdate>?,
        val trackedSnapshots: Map<String, TrackedSnapshotEntry>
    )

    /**
    * Sorted list of visible and hidden home app items.
    *
    * Sort mode is persisted with the home app button preferences. Custom mode applies
    * the user's saved manual order and falls back to Morphe ordering when no saved order exists.
    * Hidden apps are excluded from [HomeCards.visible].
    */
    private val homeCards: Flow<HomeCards?> = combine(
        _homeBundleStateFlow,
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
    ) { bundle, prefs, installedApps, updates, (_, trackedSnapshots) ->
        HomeInputs(bundle, prefs, installedApps, updates, trackedSnapshots)
    }
        // Inputs arriving mid-build are coalesced, only the newest one is built
        .conflate()
        .map(::buildHomeCards)
        .flowOn(Dispatchers.IO)

    /**
     * The cards arranged by the view preferences. Collapsing a group or switching the grouping
     * only runs this step, so the cards are not built again for it.
     */
    val homeAppState: StateFlow<HomeAppState?> = combine(
        homeCards,
        _homeCategoryPrefsFlow
    ) { cards, categoryPrefs -> cards?.arrangedBy(categoryPrefs) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun HomeCards.arrangedBy(categoryPrefs: HomeCategoryPrefs) = HomeAppState(
        visible = visible,
        hidden = hidden,
        sortMode = sortMode,
        categoryState = categoryPrefs.categoryState,
        categoryViewMode = categoryPrefs.categoryViewMode,
        showCategoryViewSwitcher = categoryPrefs.showCategoryViewSwitcher,
        sourceGroups = sourceGroups.map { group ->
            group.copy(collapsed = group.collapsible && group.uid !in categoryPrefs.expandedSourceGroups)
        }
    )

    /**
     * What the enabled bundles say about each app, and what every bundle does. The cards are
     * rebuilt for every change to the apps and the preferences, while the bundles change far less
     * often, so this is only walked again when they do. Read by [buildHomeCards] alone, which
     * runs one build at a time.
     */
    private var homeMetadata: Pair<Map<Int, PatchBundleInfo.Global>, HomeMetadata>? = null

    private data class HomeMetadata(
        val enabled: Map<String, BundleAppMetadata>,
        /** Names only, for records whose bundle the user has since disabled. */
        val all: Map<String, BundleAppMetadata>
    )

    private fun homeMetadataFor(
        info: Map<Int, PatchBundleInfo.Global>,
        enabledInfo: Map<Int, PatchBundleInfo.Global>
    ): HomeMetadata {
        homeMetadata?.takeIf { it.first === info }?.let { return it.second }
        return HomeMetadata(
            enabled = BundleAppMetadata.buildFrom(enabledInfo),
            all = BundleAppMetadata.buildFrom(info)
        ).also { homeMetadata = info to it }
    }

    /** The home cards for [inputs], or the cached ones while the bundles load. */
    private suspend fun buildHomeCards(inputs: HomeInputs): HomeCards? {
        val (homeBundle, homePrefs, installedApps, updatesMap, trackedSnapshots) = inputs
        val (bundleState, supportedVersions, ignoredVersions, keptFrom) = homeBundle
        val ready = bundleState as? PatchBundleRepository.BundleState.Ready
            ?: return cachedHomeCards?.toCards(installedApps, homePrefs)

        val enabledInfo = ready.info.filter { (_, info) -> info.enabled }
        val (metadata, allMetadata) = homeMetadataFor(ready.info, enabledInfo)
        val appsBySource = enabledInfo.mapValues { (_, info) -> info.appsBrought(keptFrom) }
        val packages = appsBySource.values.flatMapTo(mutableSetOf()) { it }
        val sourceGroups = buildHomeAppSourceGroups(
            enabledInfo = enabledInfo,
            appsBySource = appsBySource,
            sources = ready.sources,
            sortMode = homePrefs.sortMode,
            sourceOrders = homePrefs.sourceOrders
        )

        // One query for installed packages instead of one per card
        val installedPackages = pm.getInstalledPackages().mapTo(HashSet()) { it.packageName }

        suspend fun buildItem(slot: HomeAppSlot): HomeAppItem {
            val packageName = slot.packageName
            val installedApp = slot.installedApp
            val bundleMeta = metadata[packageName]
            val knownApp = KnownApps.fromPackage(packageName)
            val gradientColors = bundleMeta?.gradientColors ?: KnownApps.DEFAULT_COLORS
            // Package manager data only, saved APKs are left to inspection and on-screen icons
            val installedData = (installedApp?.currentPackageName ?: slot.id)
                .takeIf { it in installedPackages }
                ?.let { appDataResolver.resolveInstalled(it) }
            // The record's patch-time label first, it outlives the artifacts and the bundle
            val displayName = installedApp?.appLabel
                ?: installedData?.displayName
                ?: bundleMeta?.displayName
                ?: allMetadata[packageName]?.displayName
                ?: KnownApps.getAppName(packageName)
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
                    installedApp.currentPackageName in installedPackages
            val isInstallStatePending = installedApp != null && trackedSnapshot == null
            val isInstalledOnDevice = trackedPresentation?.isPatched == true
            // Until the first check lands, a card keeps the badge it was cached with
            val hasUpdate = installedApp != null && if (updatesMap != null) {
                installedApp.currentPackageName in updatesMap
            } else {
                slot.id in cachedUpdateIds
            }

            if (installedApp != null && trackedSnapshot != null && isInstalledOnDevice) {
                reconcileInstalledVersion(installedApp, trackedSnapshot.installedPackageInfo)
            }

            // Confirmed installs and replacements use the package actually on the device.
            // Unknown packages keep showing what Morphe retained rather than attributing the
            // record to whichever package currently owns the name.
            val packageInfo = displayedHomePackageInfo(
                trackedPresentation = trackedPresentation,
                installedPackageInfo = trackedSnapshot?.installedPackageInfo,
                savedPackageInfo = savedPackageInfo,
                // A record not judged yet shows its own version rather than the device's
                untrackedPackageInfo = installedData?.packageInfo.takeIf { installedApp == null }
            )

            return HomeAppItem(
                id = slot.id,
                packageName = packageName,
                displayName = displayName,
                gradientColors = gradientColors,
                installedApp = installedApp,
                packageInfo = packageInfo,
                version = packageInfo?.versionName ?: installedApp?.version.orEmpty(),
                isPinnedByDefault = knownApp?.isPinnedByDefault == true,
                isInstalledOnDevice = (trackedPresentation?.showsInstalledPackage == true) ||
                        isUninspectedInstall ||
                        (installedApp == null && installedData != null),
                isDeleted = trackedPresentation?.isDeleted == true,
                isInstallStateNotPatched = trackedPresentation?.isNotPatched == true,
                isInstallStateUnknown = trackedPresentation?.isUnknown == true,
                isInstallStatePending = isInstallStatePending,
                savedApkFile = savedPatchedApk,
                hasUpdate = hasUpdate,
                // Keyed by the package the sources know: a clone shares that package with the
                // app it was copied from rather than carrying one of its own
                versionStatus = versionStatus(
                    installedVersion = packageInfo?.versionName ?: installedApp?.version,
                    supported = supportedVersions[packageName],
                    ignoredVersion = ignoredVersions[packageName]
                ),
                patchCount = 0,
                isClone = slot.isClone
            )
        }

        // Apps patched through "Other apps" and apps no source brings anymore are not in the
        // list, but still need cards so users can reinstall/uninstall/see updates
        val allSlots = homeAppSlots(packages, installedApps)

        val visibleSlots = allSlots.filter { it.id !in homePrefs.hiddenPackages }
        val hiddenSlots = allSlots.filter { it.id in homePrefs.hiddenPackages }

        // Fan out per-card resolution: buildItem is IO-bound and stalls at 400+ apps sequentially.
        val builtItems = coroutineScope {
            (visibleSlots + hiddenSlots)
                .map { slot -> async { buildItem(slot) } }
                .awaitAll()
        }.withNameSuffixes()
        val visibleItems = builtItems.subList(0, visibleSlots.size)
        val hiddenItems = builtItems.subList(visibleSlots.size, builtItems.size)

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

        val cards = HomeCards(
            visible = visible,
            hidden = hidden,
            sortMode = homePrefs.sortMode,
            sourceGroups = sourceGroups
        )
        scope.launch(homeCardCacheWrites) { homeCardCache.write(cards) }
        return cards
    }

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
        appsBySource: Map<Int, Set<String>>,
        sources: Map<Int, PatchBundleSource>,
        sortMode: HomeAppSortMode,
        sourceOrders: Map<Int, List<String>>
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
                // A source kept from an app does not list it, even while another source does
                val packageNames = appsBySource[info.uid].orEmpty()

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
                        collapsed = false,
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
        // Cards of the same app share a name and a package, so the card id decides between them
        val morpheComparator = compareByDescending<HomeAppItem> { it.installedApp != null }
            .thenByDescending { it.isPinnedByDefault }
            .thenByDescending { it.packageInfo != null }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.id }

        return when (sortMode) {
            HomeAppSortMode.MANUAL -> {
                val defaultSorted = items.sortedWith(morpheComparator)
                if (customOrder.isEmpty()) {
                    defaultSorted
                } else {
                    val indexMap = customOrder.mapIndexed { index, id -> id to index }.toMap()
                    defaultSorted.sortedBy { indexMap[it.id] ?: Int.MAX_VALUE }
                }
            }
            HomeAppSortMode.RECOMMENDED -> items.sortedWith(morpheComparator)
            HomeAppSortMode.NAME_ASC -> items.sortedWith(
                compareBy<HomeAppItem, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.id }
            )
            HomeAppSortMode.NAME_DESC -> items.sortedWith(
                compareBy<HomeAppItem, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.id }
                    .reversed()
            )
            HomeAppSortMode.UPDATES_FIRST -> items.sortedWith(
                compareByDescending<HomeAppItem> { it.showsUpdateBadge }
                    .then(morpheComparator)
            )
            HomeAppSortMode.RECENTLY_PATCHED -> items.sortedWith(
                // Newest patch first; apps never patched sort last, in recommended order
                compareByDescending<HomeAppItem> { it.installedApp?.patchedAt ?: Long.MIN_VALUE }
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
            !state?.visible.isNullOrEmpty() && (expertMode || thirdParty)
        }.stateIn(scope, SharingStarted.Eagerly, false)

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
        }.stateIn(scope, SharingStarted.Eagerly, false)

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
        observeInstalledAppUpdates()
    }

    /** Stops listening for package changes. */
    fun close() {
        runCatching { app.unregisterReceiver(packageChangeReceiver) }
    }

    /** Rebuilds every card from scratch, after the sources were refreshed. */
    fun reload() {
        appDataResolver.invalidateAll()
        _appStateTicker.update { it + 1 }
    }

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
        scope.launch {
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
     * Reactively checks installed apps for available bundle updates.
     * Triggered on initial load and after each completed bundle update.
     */
    private fun observeInstalledAppUpdates() {
        // Check on initial load and when sources or installed apps change
        scope.launch {
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
     * Check for bundle updates for installed apps.
     *
     * Iterates all active bundles. For each [RemotePatchBundle], if a changelog is
     * available and uses conventional-changelog scopes, only apps with explicit
     * changes in newer entries receive an update badge.
     *
     * Falls back to showing the badge when changelog is unavailable or the app
     * name cannot be resolved.
     */
    private suspend fun checkInstalledAppsForUpdates(
        installedApps: List<InstalledApp>,
    ) = withContext(Dispatchers.IO) {
        val sources = patchBundleRepository.sources.first()
        if (sources.isEmpty()) {
            _appUpdatesAvailable.value = emptyMap()
            return@withContext
        }

        val currentVersionByUid: Map<Int, String?> = sources.associate { it.uid to it.version }

        val storedVersionsByApp = installedApps.associateWith { app ->
            installedAppRepository.getBundleVersionsForApp(app.currentPackageName)
        }

        // A changelog only refines the badge of an app whose bundle is newer than the one it was
        // patched with, so no other bundle's changelog is worth downloading
        val outdatedUids = outdatedBundleUids(storedVersionsByApp.values, currentVersionByUid)
        if (outdatedUids.isEmpty()) {
            _appUpdatesAvailable.value = emptyMap()
            return@withContext
        }

        // Remote bundles with an outdated app whose changelog can be read, tried once each up front
        // so a failing one is not retried for every app. runCatching per bundle so a network failure
        // in one doesn't block others
        val readableByUid: Map<Int, RemotePatchBundle> = sources
            .filter { it.uid in outdatedUids }
            .mapNotNull { it.asRemoteOrNull }
            .filter { runCatching { it.fetchChangelogEntries() }.isSuccess }
            .associateBy { it.uid }

        val updates = mutableMapOf<String, AppPatchUpdate>()

        installedApps.forEach { app ->
            val storedVersions = storedVersionsByApp.getValue(app)
            val appNames = resolveChangelogNames(app.originalPackageName)

            // Take the first bundle used for this app that has been updated
            val update = storedVersions.firstNotNullOfOrNull { (bundleUid, storedVersion) ->
                val currentVersion = currentVersionByUid[bundleUid] ?: return@firstNotNullOfOrNull null
                if (!isNewerVersion(storedVersion, currentVersion)) return@firstNotNullOfOrNull null

                // Bundle is newer - refine with changelog if available.
                // No changelog → show badge (network error or local bundle).
                // No resolvable app name → show badge (can't match scopes).
                // Known name, no matching scope → no badge.
                val unscoped = AppPatchUpdate(bundleUid, storedVersion)
                val source = readableByUid[bundleUid] ?: return@firstNotNullOfOrNull unscoped
                if (appNames.isEmpty()) return@firstNotNullOfOrNull unscoped
                // The same releases the update's changelog lists, so the badge never promises
                // changes the dialog does not show, nor misses ones it does
                val entries = runCatching { source.fetchChangelogSince(storedVersion) }.getOrNull()
                    ?: return@firstNotNullOfOrNull unscoped

                AppPatchUpdate(bundleUid, storedVersion, appNames).takeIf {
                    ChangelogParser.hasChangesFor(
                        entries = entries,
                        installedVersion = storedVersion,
                        appNames = appNames,
                    )
                }
            }

            update?.let { updates[app.currentPackageName] = it }
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
        patchBundleRepository.appMetadata.value[packageName]?.displayName?.let { names += it }
        pm.getPackageInfo(packageName)?.let { with(pm) { it.label() } }?.let { names += it }
        return names
    }
}

/** Uids of the bundles that are newer than the version at least one of [storedVersions] was patched with. */
internal fun outdatedBundleUids(
    storedVersions: Collection<Map<Int, String?>>,
    currentVersionByUid: Map<Int, String?>
): Set<Int> = storedVersions.flatMapTo(mutableSetOf()) { versions ->
    versions.filter { (uid, storedVersion) ->
        val currentVersion = currentVersionByUid[uid] ?: return@filter false
        isNewerVersion(storedVersion, currentVersion)
    }.keys
}
