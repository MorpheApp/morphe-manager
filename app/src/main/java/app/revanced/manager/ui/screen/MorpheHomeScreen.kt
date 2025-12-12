package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.AvailableUpdateDialog
import app.revanced.manager.ui.component.morphe.home.*
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Data class for quick patch parameters
 */
data class QuickPatchParams(
    val selectedApp: SelectedApp,
    val patches: PatchSelection,
    val options: Options
)

/**
 * MorpheHomeScreen - Simplified home screen for patching YouTube and YouTube Music
 * Provides streamlined interface with two main app buttons and floating action buttons
 */
@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheHomeScreen(
    onMorpheSettingsClick: () -> Unit,
    onDownloaderPluginClick: () -> Unit,
    onStartQuickPatch: (QuickPatchParams) -> Unit,
    onUpdateClick: () -> Unit = {},
    dashboardViewModel: DashboardViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect state flows
    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val sources by dashboardViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val patchCounts by dashboardViewModel.patchBundleRepository.patchCountsFlow.collectAsStateWithLifecycle(emptyMap())
    val manualUpdateInfo by dashboardViewModel.patchBundleRepository.manualUpdateInfo.collectAsStateWithLifecycle(emptyMap())
    val bundleInfo by dashboardViewModel.patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())

    val useMorpheHomeScreen by prefs.useMorpheHomeScreen.getAsState()
    val isRootMode by prefs.useRootMode.getAsState()

    val hasRootAccess by remember {
        derivedStateOf {
            dashboardViewModel.rootInstaller?.isDeviceRooted() /*.requestRootAccessIfNotAskedYet(context)*/ ?: false
        }
    }

    // Remember home state
    val homeState = rememberMorpheHomeState(
        dashboardViewModel = dashboardViewModel,
        sources = sources,
        bundleInfo = bundleInfo,
        isRootMode = isRootMode,
        hasRootAccess = hasRootAccess,
        onStartQuickPatch = onStartQuickPatch
    )

    var hasDoneAppLaunchBundleUpdate by remember { mutableStateOf(false) }
    var bundleUpdateInProgress by remember { mutableStateOf(false) }

    if (!hasDoneAppLaunchBundleUpdate) {
        hasDoneAppLaunchBundleUpdate = true
        bundleUpdateInProgress = true
        scope.launch {
            homeState.isRefreshingBundle = true
            try {
                dashboardViewModel.patchBundleRepository.updateMorpheBundle(
                    showProgress = false,
                    showToast = false
                )
            } finally {
                homeState.isRefreshingBundle = false
                bundleUpdateInProgress = false
            }
        }
    }

    // Show manager update dialog
    if (homeState.shouldShowUpdateDialog) {
        AvailableUpdateDialog(
            onDismiss = { homeState.hasCheckedForUpdates = true },
            setShowManagerUpdateDialogOnLaunch = dashboardViewModel::setShowManagerUpdateDialogOnLaunch,
            onConfirm = {
                homeState.hasCheckedForUpdates = true
                onUpdateClick()
            },
            newVersion = dashboardViewModel.updatedManagerVersion ?: "unknown"
        )
    }

    // Android 11 Dialog
    if (homeState.showAndroid11Dialog) {
        Android11Dialog(
            onDismissRequest = { homeState.showAndroid11Dialog = false },
            onContinue = { homeState.installAppsPermissionLauncher.launch(context.packageName) }
        )
    }

    // Control snackbar visibility based on progress
    LaunchedEffect(bundleUpdateProgress) {
        val progress = bundleUpdateProgress

        if (progress == null) {
            // Progress cleared - hide snackbar
            homeState.showBundleUpdateSnackbar = false
            return@LaunchedEffect
        }

        // We have progress - decide what to show
        when {
            progress.result != null -> {
                // Update completed with a result
                homeState.showBundleUpdateSnackbar = true
                homeState.snackbarStatus = when (progress.result) {
                    PatchBundleRepository.UpdateResult.Success -> BundleUpdateStatus.Success
                    PatchBundleRepository.UpdateResult.NoInternet,
                    PatchBundleRepository.UpdateResult.Error -> BundleUpdateStatus.Error
                }
            }
            progress.completed < progress.total -> {
                // Still updating
                homeState.showBundleUpdateSnackbar = true
                homeState.snackbarStatus = BundleUpdateStatus.Updating
            }
            else -> {
                // Completed == total but no result yet - keep showing updating
                homeState.showBundleUpdateSnackbar = true
                homeState.snackbarStatus = BundleUpdateStatus.Updating
            }
        }
    }

    // All dialogs
    MorpheHomeDialogs(
        state = homeState,
        useMorpheHomeScreen = useMorpheHomeScreen
    )

    // Main scaffold
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Bundle update snackbar
            MorpheBundleUpdateSnackbar(
                visible = homeState.showBundleUpdateSnackbar,
                status = homeState.snackbarStatus,
                progress = bundleUpdateProgress,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Main content with app buttons
            MorpheMainContent(
                onYouTubeClick = {
                    homeState.handleAppClick(
                        packageName = "com.google.android.youtube",
                        availablePatches = availablePatches,
                        bundleUpdateInProgress = bundleUpdateInProgress || bundleUpdateProgress != null,
                        android11BugActive = dashboardViewModel.android11BugActive
                    )
                },
                onYouTubeMusicClick = {
                    homeState.handleAppClick(
                        packageName = "com.google.android.apps.youtube.music",
                        availablePatches = availablePatches,
                        bundleUpdateInProgress = bundleUpdateInProgress || bundleUpdateProgress != null,
                        android11BugActive = dashboardViewModel.android11BugActive
                    )
                }
            )

            // Floating Action Buttons
            MorpheFloatingButtons(
                onUpdateClick = onUpdateClick,
                onBundlesClick = { homeState.showBundlesSheet = true },
                onSettingsClick = onMorpheSettingsClick,
                hasManagerUpdate = !dashboardViewModel.updatedManagerVersion.isNullOrEmpty(),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }

    // Bundle sheet
    if (homeState.showBundlesSheet) {
        MorpheBundleSheet(
            apiBundle = homeState.apiBundle,
            patchCounts = patchCounts,
            manualUpdateInfo = manualUpdateInfo,
            isRefreshing = homeState.isRefreshingBundle,
            onDismiss = { homeState.showBundlesSheet = false },
            onRefresh = {
                scope.launch {
                    homeState.isRefreshingBundle = true
                    try {
                        dashboardViewModel.patchBundleRepository.updateMorpheBundle(
                            showProgress = true,
                            showToast = false
                        )
                    } finally {
                        delay(500)
                        homeState.isRefreshingBundle = false
                    }
                }
            },
            onPatchesClick = { homeState.showPatchesDialog = true },
            onVersionClick = { homeState.showChangelogDialog = true }
        )
    }
}
