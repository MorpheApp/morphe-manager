package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.manager.InstallerPreferenceTokens
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE_MUSIC
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.home.*
import app.revanced.manager.ui.component.morphe.shared.MorpheFloatingButtons
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.MorpheThemeSettingsViewModel
import app.revanced.manager.ui.viewmodel.UpdateViewModel
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

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
    usingMountInstallState: MutableState<Boolean>,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    themeViewModel: MorpheThemeSettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current

    // Collect state flows
    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val sources by dashboardViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val bundleInfo by dashboardViewModel.patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())

    // Install type is needed for UI components.
    // Ideally this logic is part of some other code, but for now this is simple and works.
    val usingMountInstall = prefs.installerPrimary.getBlocking() == InstallerPreferenceTokens.AUTO_SAVED &&
            dashboardViewModel.rootInstaller.hasRootAccess()
    usingMountInstallState.value = usingMountInstall

    // Remember home state
    val homeState = rememberMorpheHomeState(
        dashboardViewModel = dashboardViewModel,
        sources = sources,
        bundleInfo = bundleInfo,
        onStartQuickPatch = onStartQuickPatch,
        usingMountInstall = usingMountInstall
    )

    var bundleUpdateInProgress by remember { mutableStateOf(false) }
    var showUpdateDetailsDialog by remember { mutableStateOf(false) }

    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()

    // Show manager update available dialog
    if (homeState.shouldShowUpdateDialog) {
        ManagerUpdateAvailableDialog(
            onDismiss = { homeState.hasCheckedForUpdates = true },
            onShowDetails = {
                homeState.hasCheckedForUpdates = true
                showUpdateDetailsDialog = true
            },
            setShowManagerUpdateDialogOnLaunch = dashboardViewModel::setShowManagerUpdateDialogOnLaunch,
            newVersion = dashboardViewModel.updatedManagerVersion ?: "unknown"
        )
    }

    // Show manager update details dialog
    if (showUpdateDetailsDialog) {
        // Create UpdateViewModel with downloadOnScreenEntry = false
        // We don't want auto-download when dialog opens
        val updateViewModel: UpdateViewModel = koinViewModel(
            parameters = { parametersOf(false) }
        )
        ManagerUpdateDetailsDialog(
            onDismiss = { showUpdateDetailsDialog = false },
            updateViewModel = updateViewModel
        )
    }

    // Android 11 Dialog
    if (homeState.showAndroid11Dialog) {
        HomeAndroid11Dialog(
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

        homeState.showBundleUpdateSnackbar = true
        homeState.snackbarStatus = when (progress.result) {
            PatchBundleRepository.BundleUpdateResult.Success,
            PatchBundleRepository.BundleUpdateResult.NoUpdates -> BundleUpdateStatus.Success

            PatchBundleRepository.BundleUpdateResult.NoInternet,
            PatchBundleRepository.BundleUpdateResult.Error -> BundleUpdateStatus.Error

            PatchBundleRepository.BundleUpdateResult.None -> BundleUpdateStatus.Updating
        }
    }

    // All dialogs
    HomeDialogs(
        state = homeState
    )

    // Main scaffold
    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content with app buttons
            HomeMainContent(
                onYouTubeClick = {
                    homeState.handleAppClick(
                        packageName = PACKAGE_YOUTUBE,
                        availablePatches = availablePatches,
                        bundleUpdateInProgress = bundleUpdateInProgress || bundleUpdateProgress != null,
                        android11BugActive = dashboardViewModel.android11BugActive
                    )
                },
                onYouTubeMusicClick = {
                    homeState.handleAppClick(
                        packageName = PACKAGE_YOUTUBE_MUSIC,
                        availablePatches = availablePatches,
                        bundleUpdateInProgress = bundleUpdateInProgress || bundleUpdateProgress != null,
                        android11BugActive = dashboardViewModel.android11BugActive
                    )
                },
                backgroundType = backgroundType
            )

            val hasManagerUpdate = !dashboardViewModel.updatedManagerVersion.isNullOrEmpty()

            // Floating Action Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Settings FAB
                MorpheFloatingButtons(
                    onClick = onMorpheSettingsClick,
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Update FAB
                    if (hasManagerUpdate) {
                        MorpheFloatingButtons(
                            onClick = { showUpdateDetailsDialog = true },
                            icon = Icons.Outlined.Update,
                            contentDescription = stringResource(R.string.update),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    // Bundles FAB
                    MorpheFloatingButtons(
                        onClick = { homeState.showBundleManagementSheet = true },
                        icon = Icons.Outlined.Source,
                        contentDescription = stringResource(R.string.morphe_home_bundles),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Bundle update snackbar
            HomeBundleUpdateSnackbar(
                visible = homeState.showBundleUpdateSnackbar,
                status = homeState.snackbarStatus,
                progress = bundleUpdateProgress,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
