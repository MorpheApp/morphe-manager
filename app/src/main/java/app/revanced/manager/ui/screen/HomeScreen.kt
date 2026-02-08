package app.revanced.manager.ui.screen

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
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.manager.InstallerPreferenceTokens
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.screen.home.*
import app.revanced.manager.ui.viewmodel.*
import app.revanced.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Calculate mount install state
    val usingMountInstall = prefs.installerPrimary.getBlocking() == InstallerPreferenceTokens.AUTO_SAVED &&
            homeViewModel.rootInstaller.hasRootAccess()
    usingMountInstallState.value = usingMountInstall

    // Calculate if Other Apps button should be visible
    val useExpertMode by prefs.useExpertMode.getAsState()
    val showOtherAppsButton = remember(useExpertMode, sources) {
        if (useExpertMode) true // Always show in expert mode
        else sources.size > 1 // In simple mode, show only if there are multiple bundles
    }

    // Set up HomeViewModel
    LaunchedEffect(Unit) {
        homeViewModel.usingMountInstall = usingMountInstall
        homeViewModel.onStartQuickPatch = onStartQuickPatch
    }

    // Load installed apps
    var youtubeInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }
    var youtubeMusicInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }
    var redditInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }

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

    // Pass update info to sections layout
    val appUpdatesAvailable by remember { derivedStateOf { homeViewModel.appUpdatesAvailable } }

    // Handle patch trigger from dialog
    LaunchedEffect(patchTriggerPackage) {
        patchTriggerPackage?.let { packageName ->
            homeViewModel.showPatchDialog(packageName)
            onPatchTriggerHandled()
        }
    }

    // Update installed apps when data changes
    LaunchedEffect(allInstalledApps) {
        withContext(Dispatchers.IO) {
            youtubeInstalledApp = allInstalledApps.find { it.originalPackageName == AppPackages.YOUTUBE }
            youtubeMusicInstalledApp = allInstalledApps.find { it.originalPackageName == AppPackages.YOUTUBE_MUSIC }
            redditInstalledApp = allInstalledApps.find { it.originalPackageName == AppPackages.REDDIT }
        }

        // Update package info and deleted status
        homeViewModel.updateInstalledAppsInfo(
            youtubeInstalledApp,
            youtubeMusicInstalledApp,
            redditInstalledApp,
            allInstalledApps
        )
    }

    // Get deleted status
    val youtubePackageInfo by remember { derivedStateOf { homeViewModel.youtubePackageInfo } }
    val youtubeMusicPackageInfo by remember { derivedStateOf { homeViewModel.youtubeMusicPackageInfo } }
    val redditPackageInfo by remember { derivedStateOf { homeViewModel.redditPackageInfo } }

    val appsDeletedStatus by remember { derivedStateOf { homeViewModel.appsDeletedStatus } }
    val youtubeIsDeleted = youtubeInstalledApp?.currentPackageName?.let { appsDeletedStatus[it] } == true
    val youtubeMusicIsDeleted = youtubeMusicInstalledApp?.currentPackageName?.let { appsDeletedStatus[it] } == true
    val redditIsDeleted = redditInstalledApp?.currentPackageName?.let { appsDeletedStatus[it] } == true

    // Update on refresh
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && allInstalledApps.isNotEmpty()) {
            homeViewModel.updateInstalledAppsInfo(
                youtubeInstalledApp,
                youtubeMusicInstalledApp,
                redditInstalledApp,
                allInstalledApps
            )
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

                // App update indicators
                appUpdatesAvailable = appUpdatesAvailable,

                // Greeting section
                greetingMessage = greetingMessage,

                // App buttons section
                onYouTubeClick = {
                    homeViewModel.handleAppClick(
                        packageName = AppPackages.YOUTUBE,
                        availablePatches = availablePatches,
                        bundleUpdateInProgress = false,
                        android11BugActive = homeViewModel.android11BugActive,
                        installedApp = youtubeInstalledApp
                    )
                    youtubeInstalledApp?.let { showInstalledAppDialog = it.currentPackageName }
                },
                onYouTubeMusicClick = {
                    homeViewModel.handleAppClick(
                        packageName = AppPackages.YOUTUBE_MUSIC,
                        availablePatches = availablePatches,
                        bundleUpdateInProgress = false,
                        android11BugActive = homeViewModel.android11BugActive,
                        installedApp = youtubeMusicInstalledApp
                    )
                    youtubeMusicInstalledApp?.let { showInstalledAppDialog = it.currentPackageName }
                },
                onRedditClick = {
                    homeViewModel.handleAppClick(
                        packageName = AppPackages.REDDIT,
                        availablePatches = availablePatches,
                        bundleUpdateInProgress = false,
                        android11BugActive = homeViewModel.android11BugActive,
                        installedApp = redditInstalledApp
                    )
                    redditInstalledApp?.let { showInstalledAppDialog = it.currentPackageName }
                },

                // Installed apps data
                youtubeInstalledApp = youtubeInstalledApp,
                youtubeMusicInstalledApp = youtubeMusicInstalledApp,
                redditInstalledApp = redditInstalledApp,
                youtubePackageInfo = youtubePackageInfo,
                youtubeMusicPackageInfo = youtubeMusicPackageInfo,
                redditPackageInfo = redditPackageInfo,
                youtubeIsDeleted = youtubeIsDeleted,
                youtubeMusicIsDeleted = youtubeMusicIsDeleted,
                redditIsDeleted = redditIsDeleted,
                onInstalledAppClick = { app -> showInstalledAppDialog = app.currentPackageName },
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
