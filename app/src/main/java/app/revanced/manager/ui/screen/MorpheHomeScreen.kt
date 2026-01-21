package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import app.revanced.manager.ui.component.morphe.home.HomeAndroid11Dialog
import app.revanced.manager.ui.component.morphe.home.HomeDialogs
import app.revanced.manager.ui.component.morphe.home.HomeSectionsLayout
import app.revanced.manager.ui.component.morphe.shared.ManagerUpdateDetailsDialog
import app.revanced.manager.ui.component.morphe.utils.buildBundleSummaries
import app.revanced.manager.ui.component.morphe.utils.rememberFilePickerWithPermission
import app.revanced.manager.ui.component.morphe.utils.toFilePath
import app.revanced.manager.ui.viewmodel.*
import app.revanced.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * MorpheHomeScreen 5-section layout
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun MorpheHomeScreen(
    onMorpheSettingsClick: () -> Unit,
    onMorpheInstalledAppInfoClick: (String) -> Unit,
    onStartQuickPatch: (QuickPatchParams) -> Unit,
    dashboardViewModel: DashboardViewModel = koinViewModel(),
    homeViewModel: HomeViewModel = koinViewModel(),
    prefs: PreferencesManager = koinInject(),
    usingMountInstallState: MutableState<Boolean>,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    patchTriggerPackage: String? = null,
    onPatchTriggerHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val pm: PM = koinInject()
    val installedAppRepository: InstalledAppRepository = koinInject()

    // Collect state flows
    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val sources by dashboardViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val bundleInfo by dashboardViewModel.patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())

    // Calculate mount install state
    val usingMountInstall = prefs.installerPrimary.getBlocking() == InstallerPreferenceTokens.AUTO_SAVED &&
            dashboardViewModel.rootInstaller.hasRootAccess()
    usingMountInstallState.value = usingMountInstall

    // Set up HomeViewModel
    LaunchedEffect(Unit) {
        homeViewModel.usingMountInstall = usingMountInstall
        homeViewModel.onStartQuickPatch = onStartQuickPatch
    }

    // Load installed apps
    var youtubeInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }
    var youtubeMusicInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }
    var redditInstalledApp by remember { mutableStateOf<InstalledApp?>(null) }

    var youtubePackageInfo by remember { mutableStateOf<PackageInfo?>(null) }
    var youtubeMusicPackageInfo by remember { mutableStateOf<PackageInfo?>(null) }
    var redditPackageInfo by remember { mutableStateOf<PackageInfo?>(null) }

    // Bundle summaries
    val youtubeBundleSummaries = remember { mutableStateListOf<InstalledAppsViewModel.AppBundleSummary>() }
    val youtubeMusicBundleSummaries = remember { mutableStateListOf<InstalledAppsViewModel.AppBundleSummary>() }
    val redditBundleSummaries = remember { mutableStateListOf<InstalledAppsViewModel.AppBundleSummary>() }

    // Observe all installed apps
    val allInstalledApps by installedAppRepository.getAll().collectAsStateWithLifecycle(emptyList())

    // Initialize launchers
    val storagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> homeViewModel.handleApkSelection(uri) }

    val installAppsPermissionLauncher = rememberLauncherForActivityResult(
        RequestInstallAppsContract
    ) { homeViewModel.showAndroid11Dialog = false }

    val openBundlePicker = rememberFilePickerWithPermission(
        mimeTypes = MPP_FILE_MIME_TYPES,
        onFilePicked = { uri ->
            homeViewModel.selectedBundleUri = uri
            homeViewModel.selectedBundlePath = uri.toFilePath()
        }
    )

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

    // Handle patch trigger from installed app screen
    LaunchedEffect(patchTriggerPackage) {
        patchTriggerPackage?.let { packageName ->
            homeViewModel.showPatchDialog(packageName)
            onPatchTriggerHandled()
        }
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
        val updateViewModel: UpdateViewModel = koinViewModel(
            parameters = { parametersOf(false) }
        )
        ManagerUpdateDetailsDialog(
            onDismiss = { showUpdateDetailsDialog = false },
            updateViewModel = updateViewModel
        )
    }

    // Android 11 Dialog
    if (homeViewModel.showAndroid11Dialog) {
        HomeAndroid11Dialog(
            onDismissRequest = { homeViewModel.showAndroid11Dialog = false },
            onContinue = { installAppsPermissionLauncher.launch(context.packageName) }
        )
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
        viewModel = homeViewModel,
        dashboardViewModel = dashboardViewModel,
        storagePickerLauncher = { storagePickerLauncher.launch(APK_MIMETYPE) },
        openBundlePicker = openBundlePicker
    )

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        HomeSectionsLayout(
            // Notifications section
            showBundleUpdateSnackbar = homeViewModel.showBundleUpdateSnackbar,
            snackbarStatus = homeViewModel.snackbarStatus,
            bundleUpdateProgress = bundleUpdateProgress,
            hasManagerUpdate = hasManagerUpdate,
            onShowUpdateDetails = { showUpdateDetailsDialog = true },

            // Greeting section
            greetingMessage = greetingMessage,

            // App buttons section
            onYouTubeClick = {
                homeViewModel.handleAppClick(
                    packageName = PACKAGE_YOUTUBE,
                    availablePatches = availablePatches,
                    bundleUpdateInProgress = false,
                    android11BugActive = dashboardViewModel.android11BugActive,
                    installedApp = youtubeInstalledApp
                )
                youtubeInstalledApp?.let {
                    onMorpheInstalledAppInfoClick(it.currentPackageName)
                }
            },
            onYouTubeMusicClick = {
                homeViewModel.handleAppClick(
                    packageName = PACKAGE_YOUTUBE_MUSIC,
                    availablePatches = availablePatches,
                    bundleUpdateInProgress = false,
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
            installedAppsLoading = homeViewModel.installedAppsLoading,

            // Other apps button
            onOtherAppsClick = {
                if (availablePatches <= 0) {
                    context.toast(context.getString(R.string.morphe_home_sources_are_loading))
                    return@HomeSectionsLayout
                }

                homeViewModel.pendingPackageName = null
                homeViewModel.pendingAppName = context.getString(R.string.morphe_home_other_apps)
                homeViewModel.pendingRecommendedVersion = null
                homeViewModel.showFilePickerPromptDialog = true
            },

            // Bottom action bar
            onBundlesClick = { homeViewModel.showBundleManagementSheet = true },
            onSettingsClick = onMorpheSettingsClick
        )
    }
}
