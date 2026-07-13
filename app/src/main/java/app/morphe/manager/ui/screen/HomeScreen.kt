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
import app.morphe.manager.domain.manager.HomeAppCategoryState
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.settings.system.PrePatchInstallerDialog
import app.morphe.manager.ui.viewmodel.HomeAndPatcherMessages
import app.morphe.manager.ui.viewmodel.HomeAppSourceGroup
import app.morphe.manager.ui.viewmodel.HomeViewModel
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
    usingMountInstallState: MutableState<Boolean>,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    onboardingState: OnboardingState? = null,
    globalOnboardingState: GlobalOnboardingState? = null,
    patchTriggerPackage: String? = null,
    onPatchTriggerHandled: () -> Unit = {}
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
    val homeAppSourceGroups = homeAppState?.sourceGroups ?: emptyList<HomeAppSourceGroup>()
    val bundlePipelineLoading = homeAppState == null
    val showOtherAppsButton by homeViewModel.showOtherAppsButton.collectAsStateWithLifecycle()
    val showSearchButton by homeViewModel.showSearchButton.collectAsStateWithLifecycle()
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

    // Handle patch trigger from dialog
    LaunchedEffect(patchTriggerPackage) {
        patchTriggerPackage?.let { packageName ->
            homeViewModel.showPatchDialog(packageName)
            onPatchTriggerHandled()
        }
    }

    // Check for manager update
    val hasManagerUpdate = !homeViewModel.updatedManagerVersion.isNullOrEmpty()

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
                    hasManagerUpdate = hasManagerUpdate,
                    showBundleUpdateSnackbar = homeViewModel.showBundleUpdateSnackbar,
                    snackbarStatus = homeViewModel.snackbarStatus,
                    bundleUpdateProgress = bundleUpdateProgress,
                    onShowUpdateDetails = { showUpdateDetailsDialog.value = true }
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
                    onResetOrder = { homeViewModel.resetAppOrder() },
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
                    showSortButton = showSearchButton,
                    showOtherAppsButton = showOtherAppsButton,
                    isExpertModeEnabled = useExpertMode
                ),
                greetingMessage = greetingMessage,
                onboardingState = onboardingState
            )
        }
    }
}
