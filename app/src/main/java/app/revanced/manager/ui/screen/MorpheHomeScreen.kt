package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.manager.InstallerPreferenceTokens
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_REDDIT
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE_MUSIC
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.home.*
import app.revanced.manager.ui.component.morphe.shared.ManagerUpdateDetailsDialog
import app.revanced.manager.ui.component.morphe.utils.buildBundleSummaries
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages
import app.revanced.manager.ui.viewmodel.InstalledAppsViewModel
import app.revanced.manager.ui.viewmodel.UpdateViewModel
import app.revanced.manager.util.Options
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun MorpheHomeScreen(
    onMorpheSettingsClick: () -> Unit,
    onMorpheInstalledAppInfoClick: (String) -> Unit,
    onStartQuickPatch: (QuickPatchParams) -> Unit,
    dashboardViewModel: DashboardViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    usingMountInstallState: MutableState<Boolean>,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?
) {
    val context = LocalContext.current
    val pm: PM = koinInject()
    val installedAppRepository: InstalledAppRepository = koinInject()

    // Collect state flows
    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val sources by dashboardViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val bundleInfo by dashboardViewModel.patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())

    // Install type is needed for UI components.
    // Ideally this logic is part of some other code, but for now this is simple and works.
    val usingMountInstall = prefs.installerPrimary.getBlocking() == InstallerPreferenceTokens.AUTO_SAVED &&
            dashboardViewModel.rootInstaller.hasRootAccess()
    usingMountInstallState.value = usingMountInstall

    // Load installed apps
    var youtubeInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }
    var youtubeMusicInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }
    var redditInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }

    var youtubePackageInfo by remember { mutableStateOf<PackageInfo?>(null) }
    var youtubeMusicPackageInfo by remember { mutableStateOf<PackageInfo?>(null) }
    var redditPackageInfo by remember { mutableStateOf<PackageInfo?>(null) }

    // Bundle summaries for displaying patch info on installed app cards
    val youtubeBundleSummaries = remember { mutableStateListOf<InstalledAppsViewModel.AppBundleSummary>() }
    val youtubeMusicBundleSummaries = remember { mutableStateListOf<InstalledAppsViewModel.AppBundleSummary>() }
    val redditBundleSummaries = remember { mutableStateListOf<InstalledAppsViewModel.AppBundleSummary>() }

    // Observe all installed apps from repository
    val allInstalledApps by installedAppRepository.getAll().collectAsStateWithLifecycle(emptyList())

    // Remember home state
    val homeState = rememberMorpheHomeState(
        dashboardViewModel = dashboardViewModel,
        sources = sources,
        bundleInfo = bundleInfo,
        onStartQuickPatch = onStartQuickPatch,
        usingMountInstall = usingMountInstall
    )

    // Update loading state
    LaunchedEffect(bundleUpdateProgress, allInstalledApps, availablePatches) {
        val hasLoadedApps = allInstalledApps.isNotEmpty() || availablePatches > 0
        homeState.updateLoadingState(
            bundleUpdateInProgress = bundleUpdateProgress != null,
            hasInstalledApps = hasLoadedApps
        )
    }

    // Update installed apps when data changes
    LaunchedEffect(allInstalledApps, sources, bundleInfo) {
        withContext(Dispatchers.IO) {
            val sourceMap = sources.associateBy { it.uid }

            // Load YouTube
            youtubeInstalledApp = allInstalledApps.find { it.originalPackageName == PACKAGE_YOUTUBE }
            youtubePackageInfo = youtubeInstalledApp?.currentPackageName?.let {
                pm.getPackageInfo(it)
            }
            youtubeBundleSummaries.clear()
            youtubeInstalledApp?.let { app ->
                if (app.installType == InstallType.SAVED) {
                    val selection = installedAppRepository.getAppliedPatches(app.currentPackageName)
                    youtubeBundleSummaries.addAll(
                        buildBundleSummaries(app, selection, bundleInfo, sourceMap)
                    )
                }
            }

            // Load YouTube Music
            youtubeMusicInstalledApp = allInstalledApps.find { it.originalPackageName == PACKAGE_YOUTUBE_MUSIC }
            youtubeMusicPackageInfo = youtubeMusicInstalledApp?.currentPackageName?.let {
                pm.getPackageInfo(it)
            }
            youtubeMusicBundleSummaries.clear()
            youtubeMusicInstalledApp?.let { app ->
                if (app.installType == InstallType.SAVED) {
                    val selection = installedAppRepository.getAppliedPatches(app.currentPackageName)
                    youtubeMusicBundleSummaries.addAll(
                        buildBundleSummaries(app, selection, bundleInfo, sourceMap)
                    )
                }
            }

            // Load Reddit
            redditInstalledApp = allInstalledApps.find { it.originalPackageName == PACKAGE_REDDIT }
            redditPackageInfo = redditInstalledApp?.currentPackageName?.let {
                pm.getPackageInfo(it)
            }
            redditBundleSummaries.clear()
            redditInstalledApp?.let { app ->
                if (app.installType == InstallType.SAVED) {
                    val selection = installedAppRepository.getAppliedPatches(app.currentPackageName)
                    redditBundleSummaries.addAll(
                        buildBundleSummaries(app, selection, bundleInfo, sourceMap)
                    )
                }
            }
        }
    }

    var showUpdateDetailsDialog by remember { mutableStateOf(false) }

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
        if (bundleUpdateProgress == null) {
            // Progress cleared - hide snackbar
            homeState.showBundleUpdateSnackbar = false
            return@LaunchedEffect
        }

        homeState.showBundleUpdateSnackbar = true
        homeState.snackbarStatus = when (bundleUpdateProgress.result) {
            PatchBundleRepository.BundleUpdateResult.Success,
            PatchBundleRepository.BundleUpdateResult.NoUpdates -> BundleUpdateStatus.Success

            PatchBundleRepository.BundleUpdateResult.NoInternet,
            PatchBundleRepository.BundleUpdateResult.Error -> BundleUpdateStatus.Error

            PatchBundleRepository.BundleUpdateResult.None -> BundleUpdateStatus.Updating
        }
    }

    // All dialogs
    HomeDialogs(state = homeState)

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
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
                    android11BugActive = dashboardViewModel.android11BugActive,
                    installedApp = youtubeInstalledApp
                )
                youtubeInstalledApp?.let {
                    onMorpheInstalledAppInfoClick(it.currentPackageName)
                }
            },
            onYouTubeMusicClick = {
                homeState.handleAppClick(
                    packageName = PACKAGE_YOUTUBE_MUSIC,
                    availablePatches = availablePatches,
                    bundleUpdateInProgress = bundleUpdateProgress != null,
                    android11BugActive = dashboardViewModel.android11BugActive,
                    installedApp = youtubeMusicInstalledApp
                )
                youtubeMusicInstalledApp?.let {
                    onMorpheInstalledAppInfoClick(it.currentPackageName)
                }
            },
            onRedditClick = {
                redditInstalledApp?.let {
                    onMorpheInstalledAppInfoClick(it.currentPackageName)
                } ?: run {
                    // TODO: Implement Reddit patching when ready
                    context.toast(context.getString(R.string.morphe_home_reddit_coming_soon))
                }
            },

            // Installed apps data
            youtubeInstalledApp = youtubeInstalledApp,
            youtubeMusicInstalledApp = youtubeMusicInstalledApp,
            redditInstalledApp = redditInstalledApp,
            youtubePackageInfo = youtubePackageInfo,
            youtubeMusicPackageInfo = youtubeMusicPackageInfo,
            redditPackageInfo = redditPackageInfo,
            youtubeBundleSummaries = youtubeBundleSummaries,
            youtubeMusicBundleSummaries = youtubeMusicBundleSummaries,
            redditBundleSummaries = redditBundleSummaries,
            onInstalledAppClick = { app ->
                onMorpheInstalledAppInfoClick(app.currentPackageName)
            },
            installedAppsLoading = homeState.installedAppsLoading,

            // Other apps button
            onOtherAppsClick = {
                if (availablePatches <= 0 || bundleUpdateProgress != null) {
                    context.toast(context.getString(R.string.morphe_home_sources_are_loading))
                    return@HomeSectionsLayout
                }

                // Open file picker directly for any APK selection
                homeState.pendingPackageName = null
                homeState.pendingAppName = context.getString(R.string.morphe_home_other_apps)
                homeState.pendingRecommendedVersion = null
                homeState.showFilePickerPromptDialog = true
            },

            // Bottom action bar
            onBundlesClick = { homeState.showBundleManagementSheet = true },
            onSettingsClick = onMorpheSettingsClick
        )
    }
}
