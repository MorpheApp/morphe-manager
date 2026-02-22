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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.settings.system.PrePatchInstallerDialog
import app.morphe.manager.ui.viewmodel.*
import app.morphe.manager.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Home Screen with 5-section layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onStartQuickPatch: (QuickPatchParams) -> Unit,
    onNavigateToPatcher: (packageName: String, version: String, filePath: String, patches: PatchSelection, options: Options) -> Unit,
    homeViewModel: HomeViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    usingMountInstallState: MutableState<Boolean>,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    patchTriggerPackage: String? = null,
    onPatchTriggerHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val installedAppRepository: InstalledAppRepository = koinInject()
    val view = LocalView.current

    // Dialog state for installed app info
    var showInstalledAppDialog by remember { mutableStateOf<String?>(null) }

    // Pull to refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    // Handle refresh with haptic feedback
    val onRefresh: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        isRefreshing = true
        homeViewModel.viewModelScope.launch {
            try {
                homeViewModel.patchBundleRepository.updateCheck()
                delay(500)
            } finally {
                isRefreshing = false
            }
        }
    }

    // Collect state flows
    val availablePatches by homeViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val sources by homeViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val bundleInfo by homeViewModel.patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())

    // Dynamic app items from bundles
    val homeAppItems by homeViewModel.homeAppItems.collectAsStateWithLifecycle()

    // Hidden packages filtered to only active-bundle packages (reactive)
    val hiddenAppItems by homeViewModel.hiddenAppItems.collectAsStateWithLifecycle()

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

    // Calculate if Other Apps button should be visible
    val useExpertMode by prefs.useExpertMode.getAsState()
    val showOtherAppsButton = remember(useExpertMode, sources) {
        if (useExpertMode) true // Always show in expert mode
        else sources.size > 1 // In simple mode, show only if there are multiple bundles
    }

    // Set up HomeViewModel
    LaunchedEffect(Unit) {
        homeViewModel.onStartQuickPatch = onStartQuickPatch
    }

    // Observe all installed apps
    val allInstalledApps by installedAppRepository.getAll().collectAsStateWithLifecycle(emptyList())

    // Initialize launchers
    val openApkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { homeViewModel.handleApkSelection(it) }
    }

    val openBundlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            homeViewModel.selectedBundleUri = it
            homeViewModel.selectedBundlePath = it.toString()
        }
    }

    val installAppsPermissionLauncher = rememberLauncherForActivityResult(
        contract = RequestInstallAppsContract
    ) { homeViewModel.showAndroid11Dialog = false }

    // Update bundle data
    LaunchedEffect(sources, bundleInfo) {
        homeViewModel.updateBundleData(sources, bundleInfo)
    }

    // Update loading state
    LaunchedEffect(bundleUpdateProgress, allInstalledApps, availablePatches) {
        val hasLoadedApps = allInstalledApps.isNotEmpty() || availablePatches > 0
        val isBundleUpdateInProgress = bundleUpdateProgress?.result == PatchBundleRepository.BundleUpdateResult.None
        homeViewModel.updateLoadingState(
            bundleUpdateInProgress = isBundleUpdateInProgress,
            hasInstalledApps = hasLoadedApps
        )
    }

    // Check for app updates after bundle update completes
    LaunchedEffect(bundleUpdateProgress, allInstalledApps, sources) {
        // Only check when bundle update is complete (not in progress)
        if (bundleUpdateProgress?.result == PatchBundleRepository.BundleUpdateResult.Success ||
            bundleUpdateProgress?.result == PatchBundleRepository.BundleUpdateResult.NoUpdates) {

            val defaultBundle = sources.firstOrNull { it.uid == 0 }
            homeViewModel.checkInstalledAppsForUpdates(
                installedApps = allInstalledApps,
                currentBundleVersion = defaultBundle?.version
            )
        }
    }

    // Also check on initial load
    LaunchedEffect(allInstalledApps, sources) {
        if (allInstalledApps.isNotEmpty() && sources.isNotEmpty()) {
            val defaultBundle = sources.firstOrNull { it.uid == 0 }
            homeViewModel.checkInstalledAppsForUpdates(
                installedApps = allInstalledApps,
                currentBundleVersion = defaultBundle?.version
            )
        }
    }

    // Handle patch trigger from dialog
    LaunchedEffect(patchTriggerPackage) {
        patchTriggerPackage?.let { packageName ->
            homeViewModel.showPatchDialog(packageName)
            onPatchTriggerHandled()
        }
    }

    // Update deleted status
    LaunchedEffect(allInstalledApps) {
        if (allInstalledApps.isNotEmpty()) {
            homeViewModel.updateDeletedAppsStatus(allInstalledApps)
        }
    }

    var showUpdateDetailsDialog by remember { mutableStateOf(false) }

    // Get greeting message
    val greetingMessage = stringResource(HomeAndPatcherMessages.getHomeMessage(context))

    // Check for manager update
    val hasManagerUpdate = !homeViewModel.updatedManagerVersion.isNullOrEmpty()

    // Manager update details dialog
    if (showUpdateDetailsDialog) {
        val updateViewModel: UpdateViewModel = koinViewModel(parameters = { parametersOf(false) })
        ManagerUpdateDetailsDialog(
            onDismiss = { showUpdateDetailsDialog = false },
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
    showInstalledAppDialog?.let { packageName ->
        key(packageName) {
            InstalledAppInfoDialog(
                packageName = packageName,
                onDismiss = { showInstalledAppDialog = null },
                onNavigateToPatcher = { pkg, version, filePath, patches, options ->
                    showInstalledAppDialog = null
                    onNavigateToPatcher(pkg, version, filePath, patches, options)
                },
                onTriggerPatchFlow = { originalPackageName ->
                    showInstalledAppDialog = null
                    homeViewModel.showPatchDialog(originalPackageName)
                }
            )
        }
    }

    // Control snackbar visibility
    LaunchedEffect(bundleUpdateProgress) {
        if (bundleUpdateProgress == null) {
            homeViewModel.showBundleUpdateSnackbar = false
            return@LaunchedEffect
        }
        homeViewModel.showBundleUpdateSnackbar = true
        homeViewModel.snackbarStatus = when (bundleUpdateProgress.result) {
            PatchBundleRepository.BundleUpdateResult.Success,
            PatchBundleRepository.BundleUpdateResult.NoUpdates -> BundleUpdateStatus.Success
            PatchBundleRepository.BundleUpdateResult.NoInternet,
            PatchBundleRepository.BundleUpdateResult.Error -> BundleUpdateStatus.Error
            PatchBundleRepository.BundleUpdateResult.None -> BundleUpdateStatus.Updating
        }
    }

    // All dialogs
    HomeDialogs(
        homeViewModel = homeViewModel,
        storagePickerLauncher = { openApkPicker.launch(APK_FILE_MIME_TYPES) },
        openBundlePicker = { openBundlePicker.launch(MPP_FILE_MIME_TYPES) }
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
                onShowUpdateDetails = { showUpdateDetailsDialog = true },

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
                    item.installedApp?.let { showInstalledAppDialog = it.currentPackageName }
                },
                onInstalledAppClick = { app -> showInstalledAppDialog = app.currentPackageName },
                onHideApp = { packageName -> homeViewModel.hideApp(packageName) },
                onUnhideApp = { packageName -> homeViewModel.unhideApp(packageName) },
                hiddenAppItems = hiddenAppItems,
                installedAppsLoading = homeViewModel.installedAppsLoading,

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
