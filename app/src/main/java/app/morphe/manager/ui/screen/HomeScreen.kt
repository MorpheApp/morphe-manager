/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.domain.manager.HomeAppCategoryState
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs
import app.morphe.manager.ui.screen.settings.system.PrePatchInstallerDialog
import app.morphe.manager.ui.viewmodel.HomeAndPatcherMessages
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.QuickPatchParams
import app.morphe.manager.ui.viewmodel.UpdateViewModel
import app.morphe.manager.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds

/**
 * Home Screen with 5-section layout.
 */
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onStartQuickPatch: (QuickPatchParams) -> Unit,
    homeViewModel: HomeViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    homeAppButtonPrefs: HomeAppButtonPreferences = koinInject(),
    usingMountInstallState: MutableState<Boolean>,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    onboardingState: OnboardingState? = null,
    globalOnboardingState: GlobalOnboardingState? = null,
    patchTriggerPackage: String? = null,
    onPatchTriggerHandled: () -> Unit = {},
    installViewModel: InstallViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sourcesLoadingText = stringResource(R.string.home_sources_are_loading)
    val otherAppsText = stringResource(R.string.home_other_apps)

    // Dialog states
    val showUpdateDetailsDialog = remember { mutableStateOf(false) }

    // Patches dialog state (swipe-right on app card)
    val patchesSheetItem = remember { mutableStateOf<HomeAppItem?>(null) }

    // Pull to refresh state
    val isRefreshing by homeViewModel.isRefreshing.collectAsStateWithLifecycle()

    // Reactively observe the preference so the greeting updates immediately
    val showGreetingPhrases by prefs.showGreetingPhrases.getAsState()

    // Re-evaluated whenever showPatchingPhrases changes
    var greetingResId by remember(showGreetingPhrases) {
        mutableStateOf(if (showGreetingPhrases) HomeAndPatcherMessages.getHomeMessage(context) else null)
    }
    val greetingMessage = greetingResId?.let { stringResource(it) }

    // Handle refresh with haptic feedback.
    // showPatchingPhrases is read from the reactive state captured in the
    // outer scope so the lambda always uses the current value at invocation.
    val onRefresh: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        HomeAndPatcherMessages.resetHomeMessage()
        greetingResId = if (showGreetingPhrases) HomeAndPatcherMessages.getHomeMessage(context) else null
        homeViewModel.refresh()
    }

    // Collect state flows
    val availablePatches by homeViewModel.availablePatches.collectAsStateWithLifecycle(0)
    // Atomic home state - null means pipeline is still initializing (shimmer)
    val homeAppState by homeViewModel.homeAppState.collectAsStateWithLifecycle()
    val homeAppItems = homeAppState?.visible ?: emptyList()
    val hiddenAppItems = homeAppState?.hidden ?: emptyList()
    val homeAppSortMode = homeAppState?.sortMode ?: HomeAppSortMode.MANUAL
    val homeAppCategoryState = homeAppState?.categoryState ?: HomeAppCategoryState(emptyList(), emptyMap())
    val homeAppCategoryViewMode = homeAppState?.categoryViewMode ?: HomeAppCategoryViewMode.ALL_APPS
    val showCategoryViewSwitcher = homeAppState?.showCategoryViewSwitcher == true
    val homeAppSourceGroups = homeAppState?.sourceGroups ?: emptyList()
    val bundlePipelineLoading = homeAppState == null
    val showOtherAppsButton by homeViewModel.showOtherAppsButton.collectAsStateWithLifecycle()
    val showSearchButton by homeViewModel.showSearchButton.collectAsStateWithLifecycle()
    val showSortButtonPref by homeAppButtonPrefs.showSortButton.collectAsStateWithLifecycle()
    val useExpertMode by prefs.useExpertMode.getAsState()

    // Gesture hint: shown once per bundle addition, in-memory
    val showGestureHint by homeViewModel.showSwipeGestureHint.collectAsStateWithLifecycle()

    val isDeviceRooted = homeViewModel.rootInstaller.isDeviceRooted()
    if (!isDeviceRooted) {
        // Non-root: always standard install, sync the state
        usingMountInstallState.value = false
        homeViewModel.usingMountInstall = false
    } else {
        // Root: the value is set by resolvePrePatchInstallerChoice() via the dialog,
        // just keep usingMountInstallState in sync for PatcherScreen to read
        usingMountInstallState.value = homeViewModel.usingMountInstall
    }

    // Set up HomeViewModel
    LaunchedEffect(Unit) {
        homeViewModel.onStartQuickPatch = onStartQuickPatch
    }

    val openApkPicker = rememberAdaptiveFilePicker(
        mimeTypes = APK_FILE_MIME_TYPES,
        onResult = { uri -> uri?.let { homeViewModel.handleApkSelection(it) } }
    )

    val openBundlePicker = rememberAdaptiveFilePicker(
        mimeTypes = MPP_FILE_MIME_TYPES,
        onResult = { uri ->
            uri?.let {
                homeViewModel.selectedBundleUri = it
                homeViewModel.selectedBundlePath = it.displayName(context.contentResolver)
                    ?: it.lastPathSegment
                    ?: it.toString()
            }
        }
    )

    val installAppsPermissionLauncher = rememberLauncherForActivityResult(
        contract = RequestInstallAppsContract
    ) { homeViewModel.showAndroid11Dialog = false }

    var reinstallQueue by remember { mutableStateOf<List<HomeAppItem>>(emptyList()) }
    var activeReinstallItem by remember { mutableStateOf<HomeAppItem?>(null) }
    var activeReinstallStarted by remember { mutableStateOf(false) }
    var reinstallCompleted by remember { mutableIntStateOf(0) }
    var reinstallSkipped by remember { mutableIntStateOf(0) }

    fun showReinstallSummary() {
        context.batchActionSummary(R.string.batch_reinstall_summary, reinstallCompleted, reinstallSkipped)
            ?.let { context.toast(it) }
        reinstallCompleted = 0
        reinstallSkipped = 0
    }

    fun startNextReinstall() {
        val next = reinstallQueue.firstOrNull()
        if (next == null) {
            activeReinstallItem = null
            activeReinstallStarted = false
            showReinstallSummary()
            return
        }

        reinstallQueue = reinstallQueue.drop(1)
        val installed = next.installedApp
        val savedFile = installed?.let(homeViewModel::savedPatchedApkFile)
        if (installed == null || savedFile == null) {
            reinstallSkipped++
            startNextReinstall()
            return
        }

        activeReinstallItem = next
        activeReinstallStarted = true
        installViewModel.install(
            outputFile = savedFile,
            originalPackageName = installed.originalPackageName,
            onPersistApp = { packageName, installType ->
                homeViewModel.persistReinstalledApp(installed, packageName, installType)
            }
        )
    }

    fun startBatchReinstall(items: List<HomeAppItem>) {
        if (items.isEmpty()) return
        reinstallQueue = items
        activeReinstallItem = null
        activeReinstallStarted = false
        reinstallCompleted = 0
        reinstallSkipped = 0
        installViewModel.resetInstallState()
        startNextReinstall()
    }

    LaunchedEffect(
        installViewModel.installState,
        installViewModel.installerUnavailableDialog,
        installViewModel.showInstallerSelectionDialog
    ) {
        if (activeReinstallItem == null) return@LaunchedEffect
        when (val state = installViewModel.installState) {
            is InstallViewModel.InstallState.Ready -> {
                if (
                    activeReinstallStarted &&
                    installViewModel.installerUnavailableDialog == null &&
                    !installViewModel.showInstallerSelectionDialog
                ) {
                    reinstallSkipped++
                    activeReinstallItem = null
                    activeReinstallStarted = false
                    startNextReinstall()
                }
            }
            is InstallViewModel.InstallState.Installed -> {
                reinstallCompleted++
                homeViewModel.notifyAppStateChanged(state.packageName)
                activeReinstallItem = null
                activeReinstallStarted = false
                installViewModel.resetInstallState()
                startNextReinstall()
            }
            is InstallViewModel.InstallState.Error -> {
                reinstallSkipped++
                context.toast(state.message)
                activeReinstallItem = null
                activeReinstallStarted = false
                installViewModel.resetInstallState()
                startNextReinstall()
            }
            is InstallViewModel.InstallState.Conflict -> {
                reinstallSkipped++
                context.toast(context.getString(R.string.install_app_fail, context.getString(R.string.installer_hint_conflict)))
                activeReinstallItem = null
                activeReinstallStarted = false
                installViewModel.resetInstallState()
                startNextReinstall()
            }
            else -> Unit
        }
    }

    // Handle patch trigger from dialog
    LaunchedEffect(patchTriggerPackage) {
        patchTriggerPackage?.let { packageName ->
            homeViewModel.showPatchDialog(packageName)
            onPatchTriggerHandled()
        }
    }

    // Check for manager update
    val hasManagerUpdate = !homeViewModel.updatedManagerVersion.isNullOrEmpty()

    val blockedSources by homeViewModel.patchBundleRepository.blockedSources.collectAsStateWithLifecycle(emptyMap())
    val hasBlockedSources = blockedSources.isNotEmpty()

    val metadataFetchErrors by homeViewModel.patchBundleRepository.metadataFetchErrors.collectAsStateWithLifecycle(emptyMap())
    val hasMetadataErrors = metadataFetchErrors.isNotEmpty()

    // Manager update details dialog
    if (showUpdateDetailsDialog.value) {
        val updateViewModel: UpdateViewModel = koinViewModel(parameters = { parametersOf(false) })
        ManagerUpdateDetailsDialog(
            onDismiss = { showUpdateDetailsDialog.value = false },
            updateViewModel = updateViewModel
        )
    }

    // Android 11 Dialog
    if (homeViewModel.showAndroid11Dialog) {
        Android11Dialog(
            onDismissRequest = { homeViewModel.showAndroid11Dialog = false },
            onContinue = { installAppsPermissionLauncher.launch(context.packageName) }
        )
    }

    // All dialogs
    HomeDialogs(
        homeViewModel = homeViewModel,
        storagePickerLauncher = { openApkPicker() },
        openBundlePicker = { openBundlePicker() },
        patchesItem = patchesSheetItem,
        globalOnboardingState = globalOnboardingState
    )

    InstallerFlowDialogs(installViewModel = installViewModel)

    // Pre-patching installer selection dialog for root-capable devices.
    // This dialog must appear before patching starts because the installation method
    // determines which patches are applied
    if (homeViewModel.showPrePatchInstallerDialog) {
        PrePatchInstallerDialog(
            onSelectMount = { homeViewModel.resolvePrePatchInstallerChoice(useMount = true) },
            onSelectStandard = { homeViewModel.resolvePrePatchInstallerChoice(useMount = false) },
            onDismiss = homeViewModel::dismissPrePatchInstallerDialog
        )
    }

    // Main content with pull-to-refresh
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SectionsLayout(
                notifications = HomeNotificationsUi(
                    managerUpdate = AlertState(hasManagerUpdate) { showUpdateDetailsDialog.value = true },
                    blockedSources = AlertState(hasBlockedSources) { homeViewModel.showBundleManagementSheet = true },
                    metadataErrors = AlertState(hasMetadataErrors) { homeViewModel.showBundleManagementSheet = true },
                    meteredSkipped = AlertState(homeViewModel.updatesSkippedDueToMetered) { onSettingsClick() },
                    bundleUpdate = BundleUpdateState(
                        visible = homeViewModel.showBundleUpdateSnackbar,
                        status = homeViewModel.snackbarStatus,
                        progress = bundleUpdateProgress
                    )
                ),
                apps = HomeAppListUi(
                    visible = homeAppItems,
                    hidden = hiddenAppItems,
                    installedAppsLoading = bundlePipelineLoading || homeViewModel.installedAppsLoading,
                    showGestureHint = showGestureHint,
                    sortMode = homeAppSortMode,
                    categoryState = homeAppCategoryState,
                    categoryViewMode = homeAppCategoryViewMode,
                    showCategoryViewSwitcher = showCategoryViewSwitcher,
                    sourceGroups = homeAppSourceGroups
                ),
                appActions = HomeAppActions(
                    onAppClick = { item ->
                        homeViewModel.handleAppClick(
                            packageName = item.packageName,
                            availablePatches = availablePatches,
                            bundleUpdateInProgress = false,
                            android11BugActive = homeViewModel.android11BugActive,
                            installedApp = item.installedApp
                        )
                        item.installedApp?.let {
                            homeViewModel.openInstalledAppInfo(it.currentPackageName)
                        }
                    },
                    onHideApp = { packageName -> homeViewModel.hideApp(packageName) },
                    onHideMultiple = { packageNames -> packageNames.forEach { homeViewModel.hideApp(it) } },
                    onUninstallMultiple = { items -> homeViewModel.uninstallApps(items) },
                    onReinstallMultiple = { items -> startBatchReinstall(items) },
                    onUnhideApp = { packageName -> homeViewModel.unhideApp(packageName) },
                    onShowPatches = { item -> patchesSheetItem.value = item },
                    onGestureHintShown = {
                        homeViewModel.markSwipeGestureHintShown()
                        if (onboardingState != null && onboardingState.swipeActive) {
                            scope.launch {
                                delay(600.milliseconds)
                                if (onboardingState.swipeActive) homeViewModel.triggerSwipeGestureHint()
                            }
                        }
                    },
                    onSaveOrder = { packageNames -> homeViewModel.saveAppOrder(packageNames) },
                    onSaveSourceOrder = { sourceUid, packageNames ->
                        homeViewModel.saveAppSourceOrder(sourceUid, packageNames)
                    },
                    onResetOrder = { homeViewModel.resetAppOrder() },
                    onResetSourceOrder = { sourceUid -> homeViewModel.resetAppSourceOrder(sourceUid) },
                    onSaveSourceGroupOrder = { sourceUids ->
                        homeViewModel.saveAppSourceGroupOrder(sourceUids)
                    },
                    onSortModeChange = { mode -> homeViewModel.setAppSortMode(mode) },
                    onCategoryViewModeChange = { mode -> homeViewModel.setAppCategoryViewMode(mode) },
                    onCreateCategory = { name -> homeViewModel.createAppCategory(name) },
                    onRenameCategory = { categoryId, name ->
                        homeViewModel.renameAppCategory(categoryId, name)
                    },
                    onDeleteCategory = { categoryId -> homeViewModel.deleteAppCategory(categoryId) },
                    onSaveCategoryOrder = { categoryIds ->
                        homeViewModel.saveAppCategoryOrder(categoryIds)
                    },
                    onToggleCategoryCollapsed = { categoryId ->
                        homeViewModel.toggleAppCategoryCollapsed(categoryId)
                    },
                    onToggleSourceGroupCollapsed = { sourceUid ->
                        homeViewModel.toggleAppSourceGroupCollapsed(sourceUid)
                    },
                    onAssignAppsToCategory = { packageNames, categoryId ->
                        homeViewModel.assignAppsToCategory(packageNames, categoryId)
                    }
                ),
                chromeActions = HomeChromeActions(
                    onOtherAppsClick = {
                        if (availablePatches <= 0) {
                            context.toast(sourcesLoadingText)
                        } else {
                            homeViewModel.pendingPackageName = null
                            homeViewModel.pendingAppName = otherAppsText
                            homeViewModel.pendingRecommendedVersion = null
                            homeViewModel.showFilePickerPromptDialog = true
                        }
                    },
                    onBundlesClick = { homeViewModel.showBundleManagementSheet = true },
                    onSettingsClick = onSettingsClick,
                    onRefreshGreeting = onRefresh
                ),
                chromeFlags = HomeChromeFlags(
                    showSearchButton = showSearchButton,
                    showSortButton = showSearchButton && showSortButtonPref,
                    showOtherAppsButton = showOtherAppsButton,
                    isExpertModeEnabled = useExpertMode
                ),
                greetingMessage = greetingMessage,
                onboardingState = onboardingState
            )
        }
    }
}
