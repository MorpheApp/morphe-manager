/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.settings.system.PrePatchInstallerDialog
import app.morphe.manager.ui.viewmodel.*
import app.morphe.manager.util.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Home Screen with 5-section layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onStartQuickPatch: (QuickPatchParams) -> Unit,
    homeViewModel: HomeViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    usingMountInstallState: MutableState<Boolean>,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    patchTriggerPackage: String? = null,
    onPatchTriggerHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Dialog states
    val showInstalledAppDialog = remember { mutableStateOf<String?>(null) }
    val showUpdateDetailsDialog = remember { mutableStateOf(false) }

    // Pull to refresh state
    val isRefreshing by homeViewModel.isRefreshing.collectAsStateWithLifecycle()

    // Get greeting message
    var greetingMessage by remember { mutableStateOf(context.getString(HomeAndPatcherMessages.getHomeMessage(context))) }

    // Handle refresh with haptic feedback
    val onRefresh: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        HomeAndPatcherMessages.resetHomeMessage()
        greetingMessage = context.getString(HomeAndPatcherMessages.getHomeMessage(context))
        homeViewModel.refresh()
    }

    // Collect state flows
    val availablePatches by homeViewModel.availablePatches.collectAsStateWithLifecycle(0)
    // Dynamic app items from bundles
    val homeAppItems by homeViewModel.homeAppItems.collectAsStateWithLifecycle()
    // Hidden packages filtered to only active-bundle packages (reactive)
    val hiddenAppItems by homeViewModel.hiddenAppItems.collectAsStateWithLifecycle()
    val showOtherAppsButton by homeViewModel.showOtherAppsButton.collectAsStateWithLifecycle()
    val showSearchButton by homeViewModel.showSearchButton.collectAsStateWithLifecycle()
    val useExpertMode by prefs.useExpertMode.getAsState()

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

    // Initialize launchers
    // GetContent is used instead of OpenDocument so that third-party file managers
    // appear as available options in Android's picker
    val openApkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { homeViewModel.handleApkSelection(it) }
    }

    val openBundlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            homeViewModel.selectedBundleUri = it
            homeViewModel.selectedBundlePath = it.toString()
        }
    }

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

    // Installed App Info Dialog
    showInstalledAppDialog.value?.let { packageName ->
        key(packageName) {
            InstalledAppInfoDialog(
                packageName = packageName,
                onDismiss = { showInstalledAppDialog.value = null },
                onTriggerPatchFlow = { originalPackageName ->
                    showInstalledAppDialog.value = null
                    homeViewModel.showPatchDialog(originalPackageName)
                },
                homeViewModel = homeViewModel
            )
        }
    }

    // All dialogs
    HomeDialogs(
        homeViewModel = homeViewModel,
        storagePickerLauncher = { openApkPicker.launch("*/*") },
        openBundlePicker = { openBundlePicker.launch("*/*") }
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
                // Notifications section
                showBundleUpdateSnackbar = homeViewModel.showBundleUpdateSnackbar,
                snackbarStatus = homeViewModel.snackbarStatus,
                bundleUpdateProgress = bundleUpdateProgress,
                hasManagerUpdate = hasManagerUpdate,
                onShowUpdateDetails = { showUpdateDetailsDialog.value = true },

                // Greeting section
                greetingMessage = greetingMessage,

                // Dynamic app items
                homeAppItems = homeAppItems,
                onAppClick = { item ->
                    homeViewModel.handleAppClick(
                        packageName = item.packageName,
                        availablePatches = availablePatches,
                        bundleUpdateInProgress = false,
                        android11BugActive = homeViewModel.android11BugActive,
                        installedApp = item.installedApp
                    )
                    item.installedApp?.let { showInstalledAppDialog.value = it.currentPackageName }
                },
                onInstalledAppClick = { app -> showInstalledAppDialog.value = app.currentPackageName },
                onHideApp = { packageName -> homeViewModel.hideApp(packageName) },
                onUnhideApp = { packageName -> homeViewModel.unhideApp(packageName) },
                hiddenAppItems = hiddenAppItems,
                installedAppsLoading = homeViewModel.installedAppsLoading,

                // Search
                showSearchButton = showSearchButton,

                // Other apps button
                onOtherAppsClick = {
                    if (availablePatches <= 0) {
                        context.toast(context.getString(R.string.home_sources_are_loading))
                        return@SectionsLayout
                    }
                    homeViewModel.pendingPackageName = null
                    homeViewModel.pendingAppName = context.getString(R.string.home_other_apps)
                    homeViewModel.pendingRecommendedVersion = null
                    homeViewModel.showFilePickerPromptDialog = true
                },
                showOtherAppsButton = showOtherAppsButton,

                // Bottom action bar
                onBundlesClick = { homeViewModel.showBundleManagementSheet = true },
                onSettingsClick = onSettingsClick,

                // Expert mode
                isExpertModeEnabled = useExpertMode
            )
        }
    }
}
