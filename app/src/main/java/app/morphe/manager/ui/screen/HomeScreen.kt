/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.trackingKey
import app.morphe.manager.domain.batch.BatchTarget
import app.morphe.manager.domain.manager.*
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.settings.SettingsPage
import app.morphe.manager.ui.screen.settings.SettingsNavigation
import app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs
import app.morphe.manager.ui.screen.shared.AppPullToRefreshBox
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.InstallQueueRequest
import app.morphe.manager.ui.screen.shared.MainPage
import app.morphe.manager.ui.screen.shared.MainPagerScaffold
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.ui.screen.shared.rememberInstallQueue
import app.morphe.manager.ui.screen.shared.turnTo
import app.morphe.manager.ui.screen.sources.SourcesPage
import app.morphe.manager.ui.screen.sources.rememberSourcesPageState
import app.morphe.manager.ui.theme.SeasonalEvent
import app.morphe.manager.ui.viewmodel.*
import app.morphe.manager.util.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The main screen: the sources, the apps and the settings as pages side by side, see
 * [MainPagerScaffold], with the dialogs any of them can raise.
 *
 * @param settingsNavigation Where the settings page leads out to.
 */
@Composable
fun HomeScreen(
    settingsNavigation: SettingsNavigation,
    onStartQuickPatch: (Patcher.ViewModelParams) -> Unit,
    onStartBatchPatch: (List<BatchTarget>) -> Unit,
    homeViewModel: HomeViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    homeAppButtonPrefs: HomeAppButtonPreferences = koinInject(),
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    onboardingState: OnboardingState? = null,
    patchTriggerPackage: String? = null,
    onPatchTriggerHandled: () -> Unit = {},
    appInfoTriggerPackage: String? = null,
    onAppInfoTriggerHandled: () -> Unit = {},
    installViewModel: InstallViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val messages = koinInject<UserMessages>()
    val scope = rememberCoroutineScope()
    val sourcesLoadingText = stringResource(R.string.home_sources_are_loading)
    val otherAppsText = stringResource(R.string.home_other_apps)
    val settingsLabel = stringResource(R.string.settings)
    val expertModeLabel = stringResource(R.string.settings_advanced_expert_mode)

    val pagerState = rememberPagerState(initialPage = MainPage.APPS.ordinal) { MainPage.entries.size }
    val openPage: (MainPage) -> Unit = { page ->
        scope.launch { pagerState.turnTo(page) }
    }
    // Turns to each step's page, and back to the apps once the tour ends
    val tourPage = onboardingState?.page
    val touring = onboardingState != null
    var tourShown by remember { mutableStateOf(false) }
    LaunchedEffect(touring, tourPage) {
        when {
            touring -> {
                tourShown = true
                onboardingState.pageShown = null
                tourPage?.let {
                    pagerState.turnTo(it)
                    onboardingState.pageShown = it
                }
            }
            tourShown -> {
                tourShown = false
                pagerState.turnTo(MainPage.APPS)
            }
        }
    }

    // Dialog states
    val showUpdateDetailsDialog = remember { mutableStateOf(false) }

    // Patches dialog state (swipe-right on app card)
    val patchesSheetItem = remember { mutableStateOf<HomeAppItem?>(null) }

    // Pull to refresh state
    val isRefreshing by homeViewModel.isRefreshing.collectAsStateWithLifecycle()

    // Reactively observe the preference so the greeting updates immediately
    val showGreetingPhrases by prefs.showGreetingPhrases.getAsState()
    val showRepatchNotice by prefs.showRepatchNotice.getAsState()
    val showMoreAppsCard by prefs.showMoreAppsCard.getAsState()
    val seasonalThemes by prefs.seasonalThemes.getAsState()
    val seasonalEvent = remember(seasonalThemes) { SeasonalEvent.on().takeIf { seasonalThemes } }

    // Re-evaluated whenever showPatchingPhrases or the seasonal event changes
    var greetingResId by remember(showGreetingPhrases, seasonalEvent) {
        mutableStateOf(
            if (showGreetingPhrases) HomeAndPatcherMessages.getHomeMessage(context, seasonalEvent) else null
        )
    }
    val greetingMessage = greetingResId?.let { stringResource(it) }

    // showPatchingPhrases is read from the reactive state captured in the
    // outer scope so the lambda always uses the current value at invocation.
    val onRefresh: () -> Unit = {
        HomeAndPatcherMessages.resetHomeMessage()
        greetingResId = if (showGreetingPhrases) HomeAndPatcherMessages.getHomeMessage(context, seasonalEvent) else null
        homeViewModel.refresh()
    }

    // Collect state flows
    val availablePatches by homeViewModel.availablePatches.collectAsStateWithLifecycle(0)
    // Atomic home state - null means pipeline is still initializing (shimmer)
    val homeAppState by homeViewModel.apps.homeAppState.collectAsStateWithLifecycle()
    val homeAppItems = homeAppState?.visible ?: emptyList()
    val hiddenAppItems = homeAppState?.hidden ?: emptyList()
    val homeAppCategoryState = homeAppState?.categoryState ?: HomeAppCategoryState(emptyList(), emptyMap())
    val homeAppCategoryViewMode = homeAppState?.categoryViewMode ?: HomeAppCategoryViewMode.ALL_APPS
    val homeAppSourceGroups = homeAppState?.sourceGroups ?: emptyList()
    val bundlePipelineLoading = homeAppState == null
    val batchRun by homeViewModel.batchRun.collectAsStateWithLifecycle()
    val savedBatchQueue by homeViewModel.savedBatchQueue.collectAsStateWithLifecycle()
    // Read from the preferences rather than the built cards, so the options show a pick at once
    // while the cards are still being sorted by it
    val homeAppSortMode by homeAppButtonPrefs.sortMode.collectAsStateWithLifecycle()
    val homeAppFilterModePref by homeAppButtonPrefs.filterMode.collectAsStateWithLifecycle()
    val useExpertMode by prefs.useExpertMode.getAsState()

    // Held here rather than in the list, since the bars it docks over the list stand in for the
    // navigation, which gives way to them
    val sectionState = rememberHomeAppsSectionState(
        initialOrder = homeAppItems.map { it.id },
        initialSourceGroupOrder = homeAppSourceGroups.map { it.uid },
        initialCategoryOrder = homeAppCategoryState.categories.map { it.id },
        hasContent = homeAppItems.isNotEmpty() || hiddenAppItems.isNotEmpty()
    )

    // Held here for the same reason, the sources' panels docking over their page's lower edge
    val sourcesPageState = rememberSourcesPageState()

    // Gesture hint: shown once per bundle addition, in-memory
    val showGestureHint by homeViewModel.apps.showSwipeGestureHint.collectAsStateWithLifecycle()

    // Set up HomeViewModel
    LaunchedEffect(Unit) {
        homeViewModel.patchFlow.onStartQuickPatch = onStartQuickPatch
    }

    val openApkPicker = rememberAdaptiveFilePicker(
        mimeTypes = APK_FILE_MIME_TYPES,
        onResult = homeViewModel.patchFlow::handleApkSelection
    )

    val installAppsPermissionLauncher = rememberLauncherForActivityResult(
        contract = RequestInstallAppsContract
    ) { homeViewModel.showAndroid11Dialog = false }

    val startInstallQueue = rememberInstallQueue(
        installViewModel = installViewModel,
        completedPluralRes = R.plurals.batch_reinstall_summary
    )

    val startBatchReinstall: (List<HomeAppItem>) -> Unit = { items ->
        val requests = items.mapNotNull { item ->
            val installed = item.installedApp ?: return@mapNotNull null
            val savedFile = item.savedApkFile ?: return@mapNotNull null
            InstallQueueRequest(
                file = savedFile,
                originalPackageName = installed.originalPackageName,
                installedApp = installed,
                onPersistApp = { packageName, installType ->
                    homeViewModel.persistReinstalledApp(installed, packageName, installType)
                },
                onInstalled = { packageName ->
                    homeViewModel.apps.notifyAppStateChanged(packageName)
                }
            )
        }
        startInstallQueue(requests)
    }

    // A card that stands for an install queues that install, so cloned copies are each
    // rebuilt as themselves rather than collapsing into the app they came from
    fun HomeAppItem.batchTarget() = BatchTarget(
        packageName = packageName,
        repatchedPackageName = installedApp?.trackingKey
    )

    // Only the apps a rebuild actually moves on: one the sources still cover at its installed
    // version comes back from patching exactly as it went in, however its card is badged.
    // Those already queued are left to the queue's own alert
    val repatchableApps = remember(homeAppItems, savedBatchQueue) {
        val queued = savedBatchQueue?.targets.orEmpty().mapTo(HashSet()) { it.id }
        homeAppItems.filter { it.showsUpdateBadge && it.batchTarget().id !in queued }
    }

    val batchInProgressText = stringResource(R.string.batch_patch_in_progress)
    val openBatchQueue: (List<BatchTarget>) -> Unit = { targets ->
        when {
            availablePatches <= 0 -> messages.show(sourcesLoadingText)
            homeViewModel.android11BugActive -> homeViewModel.showAndroid11Dialog = true
            targets.isEmpty() -> Unit
            // A live queue keeps its own selection, so open it instead of swapping the apps
            homeViewModel.batchPatchRunning -> {
                messages.show(batchInProgressText)
                onStartBatchPatch(targets)
            }
            else -> onStartBatchPatch(targets)
        }
    }
    val startBatchPatch: (List<HomeAppItem>) -> Unit = { items ->
        openBatchQueue(items.map { it.batchTarget() })
    }

    // Handle patch trigger from dialog
    LaunchedEffect(patchTriggerPackage) {
        patchTriggerPackage?.let { packageName ->
            homeViewModel.patchFlow.showPatchDialog(packageName)
            onPatchTriggerHandled()
        }
    }

    // Handle app info trigger (e.g. from mount replaced notification)
    LaunchedEffect(appInfoTriggerPackage) {
        appInfoTriggerPackage?.let { packageName ->
            homeViewModel.patchFlow.openInstalledAppInfo(packageName)
            onAppInfoTriggerHandled()
        }
    }

    // Check for manager update
    val hasManagerUpdate = !homeViewModel.updatedManagerVersion.isNullOrEmpty()

    val blockedSources by homeViewModel.patchBundleRepository.blockedSources.collectAsStateWithLifecycle(emptyMap())
    val hasBlockedSources = blockedSources.isNotEmpty()

    val metadataFetchErrors by homeViewModel.patchBundleRepository.metadataFetchErrors.collectAsStateWithLifecycle(emptyMap())
    val hasMetadataErrors = metadataFetchErrors.isNotEmpty()

    // Sources built for a newer patcher than this manager ships. They cannot be loaded or patched
    // with until the app is updated, so surface it instead of leaving the source silently broken
    val hasOutdatedManagerSources by homeViewModel.patchBundleRepository.hasOutdatedManagerSources.collectAsStateWithLifecycle()

    // Reading these took the process down, so they are skipped until the file changes. Nothing
    // else on this screen would explain why their patches are suddenly gone
    val hasHeldBackSources by homeViewModel.patchBundleRepository.hasHeldBackSources.collectAsStateWithLifecycle()

    // Manager update details dialog
    DialogHost(visible = showUpdateDetailsDialog.value) {
        ManagerChangelogDialog(
            onDismiss = { showUpdateDetailsDialog.value = false },
            expectsUpdate = true
        )
    }

    // Android 11 Dialog
    DialogHost(visible = homeViewModel.showAndroid11Dialog) {
        Android11Dialog(
            onDismissRequest = { homeViewModel.showAndroid11Dialog = false },
            onContinue = { installAppsPermissionLauncher.launch(context.packageName) }
        )
    }

    // All dialogs
    HomeDialogs(
        homeViewModel = homeViewModel,
        storagePickerLauncher = { openApkPicker() },
        patchesItem = patchesSheetItem,
        settingsNavigation = settingsNavigation
    )

    InstallerFlowDialogs(installViewModel = installViewModel)

    val notifications = HomeNotificationsUi(
        managerUpdate = AlertState(hasManagerUpdate) { showUpdateDetailsDialog.value = true },
        outdatedManager = AlertState(hasOutdatedManagerSources) { openPage(MainPage.SOURCES) },
        heldBackSources = AlertState(hasHeldBackSources) { openPage(MainPage.SOURCES) },
        blockedSources = AlertState(hasBlockedSources) { openPage(MainPage.SOURCES) },
        metadataErrors = AlertState(hasMetadataErrors) { openPage(MainPage.SOURCES) },
        meteredSkipped = AlertState(homeViewModel.updatesSkippedDueToMetered) { openPage(MainPage.SETTINGS) },
        repatchAvailable = RepatchAlertState(
            count = repatchableApps.size,
            visible = showRepatchNotice,
            onShow = { startBatchPatch(repatchableApps) }
        ),
        // Reopened on its own apps, so the batch screen keeps the run instead of
        // planning a new one. A queue left by an earlier process is only stored
        batchQueue = BatchQueueAlertState(batchRun, savedBatchQueue) {
            val run = batchRun
            val saved = savedBatchQueue
            when {
                run != null -> onStartBatchPatch(run.targets)
                saved != null -> openBatchQueue(saved.targets)
            }
        },
        bundleUpdate = BundleUpdateState(
            visible = homeViewModel.showBundleUpdateSnackbar,
            status = homeViewModel.snackbarStatus,
            progress = bundleUpdateProgress
        )
    )
    val alerts = rememberHomeAlerts(notifications)
    // Each page's header, which the alerts hang under on whichever page is on screen
    val pageHeaderHeights = remember { mutableStateMapOf<MainPage, Dp>() }
    // How far down the alerts reach under the header, which the lists keep clear of. Read only
    // where a list is laid out, so the cards easing in and out do not recompose the screen
    val alertsHeight = remember { mutableStateOf(0.dp) }

    MainPagerScaffold(
        pagerState = pagerState,
        // By whatever the page on screen docks over its lower edge
        navigationCovered = {
            when (MainPage.entries[pagerState.currentPage]) {
                MainPage.APPS -> sectionState.isFooterBarVisible
                MainPage.SOURCES -> sourcesPageState.isFooterBarVisible
                MainPage.SETTINGS -> false
            }
        },
        // The tour moves between the pages itself, and a swipe would leave its spotlight behind
        swipeEnabled = onboardingState == null,
        settingsIcon = if (useExpertMode) MaterialSymbols.Rounded.Engineering else MainPage.SETTINGS.icon,
        settingsDescription = if (useExpertMode) "$settingsLabel, $expertModeLabel" else null,
        onItemPositioned = onboardingState?.let { tour -> { page, bounds -> tour.navigationBounds[page] = bounds } },
        overlay = {
            val alertsTopTarget = pageHeaderHeights[MainPage.entries[pagerState.currentPage]]
            // Held back until the header is measured, so on a cold start the alerts open right
            // under it rather than drop down to it from the top of the screen
            if (alertsTopTarget != null) {
                val alertsTop by animateDpAsState(targetValue = alertsTopTarget, label = "alertsTop")
                NotificationsOverlay(
                    alerts = alerts,
                    // Only an update under way follows to the other pages
                    showAlerts = pagerState.currentPage == MainPage.APPS.ordinal,
                    modifier = Modifier
                        .widthIn(max = Defaults.ContentMaxWidth)
                        .align(Alignment.TopCenter)
                        .padding(top = alertsTop),
                    onHeightChange = { alertsHeight.value = it }
                )
            }
        }
    ) { page, bottomBarHeight ->
        when (page) {
            MainPage.SOURCES -> SourcesPage(
                homeViewModel = homeViewModel,
                bottomBarHeight = bottomBarHeight,
                state = sourcesPageState,
                onboardingState = onboardingState,
                alertsHeight = { alertsHeight.value },
                onHeaderHeightChange = { pageHeaderHeights[MainPage.SOURCES] = it },
                // The new source's apps come up on the list, so that is where adding one ends
                onSourceAdded = { openPage(MainPage.APPS) }
            )

            MainPage.SETTINGS -> SettingsPage(
                homeViewModel = homeViewModel,
                navigation = settingsNavigation,
                bottomBarHeight = bottomBarHeight,
                onboardingState = onboardingState,
                onHeaderHeightChange = { pageHeaderHeights[MainPage.SETTINGS] = it }
            )

            MainPage.APPS -> AppPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                // Drawn out from under the header and the alerts rather than the status bar, as the list is
                indicatorTop = { (pageHeaderHeights[MainPage.APPS] ?: 0.dp) + alertsHeight.value }
            ) {
                SectionsLayout(
                    apps = HomeAppListUi(
                        visible = homeAppItems,
                        hidden = hiddenAppItems,
                        installedAppsLoading = bundlePipelineLoading || homeViewModel.installedAppsLoading,
                        showGestureHint = showGestureHint,
                        sortMode = homeAppSortMode,
                        filterMode = HomeAppFilterMode.fromPreference(homeAppFilterModePref),
                        categoryState = homeAppCategoryState,
                        categoryViewMode = homeAppCategoryViewMode,
                        sourceGroups = homeAppSourceGroups,
                        batchQueuePending = savedBatchQueue != null,
                        showMoreAppsCard = showMoreAppsCard
                    ),
                    appActions = HomeAppActions(
                        onAppClick = { item ->
                            homeViewModel.handleAppClick(
                                packageName = item.packageName,
                                availablePatches = availablePatches,
                                android11BugActive = homeViewModel.android11BugActive,
                                installedApp = item.installedApp
                            )
                            item.installedApp?.let {
                                homeViewModel.patchFlow.openInstalledAppInfo(it.currentPackageName)
                            }
                        },
                        onHideApp = { packageName -> homeViewModel.apps.hideApp(packageName) },
                        onHideMultiple = { packageNames -> packageNames.forEach { homeViewModel.apps.hideApp(it) } },
                        onUninstallMultiple = { items -> homeViewModel.uninstallApps(items) },
                        onReinstallMultiple = { items -> startBatchReinstall(items) },
                        onPatchMultiple = { items -> startBatchPatch(items) },
                        onUnhideApp = { packageName -> homeViewModel.apps.unhideApp(packageName) },
                        onShowPatches = { item -> patchesSheetItem.value = item },
                        onGestureHintShown = {
                            homeViewModel.apps.markSwipeGestureHintShown()
                            if (onboardingState != null && onboardingState.swipeActive) {
                                scope.launch {
                                    delay(600.milliseconds)
                                    if (onboardingState.swipeActive) homeViewModel.apps.triggerSwipeGestureHint()
                                }
                            }
                        },
                        onSaveOrder = { packageNames -> homeViewModel.apps.saveAppOrder(packageNames) },
                        onSaveSourceOrder = { sourceUid, packageNames ->
                            homeViewModel.apps.saveAppSourceOrder(sourceUid, packageNames)
                        },
                        onResetOrder = { homeViewModel.apps.resetAppOrder() },
                        onResetSourceOrder = { sourceUid -> homeViewModel.apps.resetAppSourceOrder(sourceUid) },
                        onSaveSourceGroupOrder = { sourceUids ->
                            homeViewModel.apps.saveAppSourceGroupOrder(sourceUids)
                        },
                        onSortModeChange = { mode -> homeViewModel.apps.setAppSortMode(mode) },
                        onFilterModeChange = { mode ->
                            homeAppButtonPrefs.setFilterMode(mode.name.takeIf { mode.isActive })
                        },
                        onCategoryViewModeChange = { mode -> homeViewModel.apps.setAppCategoryViewMode(mode) },
                        onCreateCategory = { name -> homeViewModel.apps.createAppCategory(name) },
                        onRenameCategory = { categoryId, name ->
                            homeViewModel.apps.renameAppCategory(categoryId, name)
                        },
                        onDeleteCategory = { categoryId -> homeViewModel.apps.deleteAppCategory(categoryId) },
                        onSaveCategoryOrder = { categoryIds ->
                            homeViewModel.apps.saveAppCategoryOrder(categoryIds)
                        },
                        onToggleCategoryCollapsed = { categoryId ->
                            homeViewModel.apps.toggleAppCategoryCollapsed(categoryId)
                        },
                        onToggleSourceGroupCollapsed = { sourceUid ->
                            homeViewModel.apps.toggleAppSourceGroupCollapsed(sourceUid)
                        },
                        onAssignAppsToCategory = { packageNames, categoryId ->
                            homeViewModel.apps.assignAppsToCategory(packageNames, categoryId)
                        }
                    ),
                    chromeActions = HomeChromeActions(
                        onOtherAppsClick = {
                            if (availablePatches <= 0) {
                                messages.show(sourcesLoadingText)
                            } else {
                                homeViewModel.patchFlow.startOtherAppsFlow(otherAppsText)
                            }
                        },
                        onOpenSources = { openPage(MainPage.SOURCES) },
                        onRefreshGreeting = onRefresh,
                        onHideMoreAppsCard = { scope.launch { prefs.showMoreAppsCard.update(false) } }
                    ),
                    greetingMessage = greetingMessage,
                    state = sectionState,
                    bottomBarHeight = bottomBarHeight,
                    alerts = alerts,
                    alertsHeight = { alertsHeight.value },
                    onboardingState = onboardingState,
                    onHeaderHeightChange = { pageHeaderHeights[MainPage.APPS] = it }
                )
            }
        }
    }
}
