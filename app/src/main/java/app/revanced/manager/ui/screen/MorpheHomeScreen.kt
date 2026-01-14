package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.manager.InstallerPreferenceTokens
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE_MUSIC
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.home.*
import app.revanced.manager.ui.component.morphe.shared.ManagerUpdateDetailsDialog
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.navigation.MorpheInstalledApps
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages
import app.revanced.manager.ui.viewmodel.MorpheThemeSettingsViewModel
import app.revanced.manager.ui.viewmodel.UpdateViewModel
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.toast
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
 * MorpheHomeScreen 5-section layout
 * Sections:
 * 1. Notifications
 * 2. Greeting message
 * 3. Main app buttons
 * 4. Other apps button
 * 5. Bottom action bar
 */
@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheHomeScreen(
    onMorpheSettingsClick: () -> Unit,
    onMorpheInstalledAppsClick: () -> Unit,
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

    // Collect expert mode state
    val useExpertMode by prefs.useExpertMode.getAsState()

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

    var showUpdateDetailsDialog by remember { mutableStateOf(false) }

    val backgroundType by themeViewModel.prefs.backgroundType.getAsState()

    // Get greeting message
    val greetingMessage = stringResource(HomeAndPatcherMessages.getHomeMessage(context))

    // Check for manager update
    val hasManagerUpdate = !dashboardViewModel.updatedManagerVersion.isNullOrEmpty()

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
        HomeSectionsLayout(
            // Notifications section
            showBundleUpdateSnackbar = homeState.showBundleUpdateSnackbar,
            snackbarStatus = homeState.snackbarStatus,
            bundleUpdateProgress = bundleUpdateProgress,
            hasManagerUpdate = hasManagerUpdate,
            onShowUpdateDetails = { showUpdateDetailsDialog = true },

            // Greeting section
            greetingMessage = greetingMessage,

            // App buttons section
            onYouTubeClick = {
                homeState.handleAppClick(
                    packageName = PACKAGE_YOUTUBE,
                    availablePatches = availablePatches,
                    bundleUpdateInProgress = bundleUpdateProgress != null,
                    android11BugActive = dashboardViewModel.android11BugActive
                )
            },
            onYouTubeMusicClick = {
                homeState.handleAppClick(
                    packageName = PACKAGE_YOUTUBE_MUSIC,
                    availablePatches = availablePatches,
                    bundleUpdateInProgress = bundleUpdateProgress != null,
                    android11BugActive = dashboardViewModel.android11BugActive
                )
            },
            onRedditClick = {
                // TODO: Implement Reddit patching when ready
                context.toast(context.getString(R.string.morphe_home_reddit_coming_soon))
            },
            backgroundType = backgroundType,

            // Other apps button (only show in expert mode)
            showOtherAppsButton = useExpertMode,
            onOtherAppsClick = {
                if (availablePatches <= 0 || bundleUpdateProgress != null) {
                    context.toast(context.getString(R.string.morphe_home_patches_are_loading))
                    return@HomeSectionsLayout
                }

                // Open file picker directly for any APK selection
                homeState.pendingPackageName = null
                homeState.pendingAppName = context.getString(R.string.morphe_home_other_apps)
                homeState.pendingRecommendedVersion = null
                homeState.showFilePickerPromptDialog = true
            },

            // Bottom action bar
            onInstalledAppsClick = onMorpheInstalledAppsClick,
            onBundlesClick = { homeState.showBundleManagementSheet = true },
            onSettingsClick = onMorpheSettingsClick,

            modifier = Modifier.padding(paddingValues)
        )
    }
}
