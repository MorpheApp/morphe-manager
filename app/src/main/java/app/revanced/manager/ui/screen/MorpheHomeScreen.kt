package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.revanced.manager.ui.component.AvailableUpdateDialog
import app.revanced.manager.ui.component.bundle.BundleChangelogDialog
import app.revanced.manager.ui.component.bundle.BundlePatchesDialog
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.HomeAndPatcherMessages
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.RequestInstallAppsContract
import app.revanced.manager.util.tag
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
private const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

private enum class BundleUpdateStatus {
    Updating,    // Update in progress
    Success,     // Update completed successfully
    Error        // Error occurred (including no internet)
}

data class QuickPatchParams(
    val selectedApp: SelectedApp,
    val patches: PatchSelection,
    val options: Options
)

data class UnsupportedVersionDialogState(
    val packageName: String,
    val version: String,
    val recommendedVersion: String?
)

data class WrongPackageDialogState(
    val expectedPackage: String,
    val actualPackage: String
)

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
    val uriHandler = LocalUriHandler.current

    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val sources by dashboardViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val patchCounts by dashboardViewModel.patchBundleRepository.patchCountsFlow.collectAsStateWithLifecycle(emptyMap())
    val manualUpdateInfo by dashboardViewModel.patchBundleRepository.manualUpdateInfo.collectAsStateWithLifecycle(emptyMap())

    val useMorpheHomeScreen by prefs.useMorpheHomeScreen.getAsState()
    val isRootMode by prefs.useRootMode.getAsState()

    val hasRootAccess by remember {
        derivedStateOf {
            dashboardViewModel.rootInstaller?.requestRootAccessIfNotAskedYet(context) ?: false
        }
    }

    // Get only the API bundle (uid = 0)
    val apiBundle = remember(sources) { sources.firstOrNull { it.uid == 0 } }

    // Get bundle info to extract recommended versions
    val bundleInfo by dashboardViewModel.patchBundleRepository.bundleInfoFlow.collectAsStateWithLifecycle(emptyMap())

    // Get recommended versions for YouTube and YouTube Music from bundle info
    val recommendedVersions = remember(bundleInfo) {
        bundleInfo[0]?.let { apiBundleInfo ->
            mapOf(
                PACKAGE_YOUTUBE to apiBundleInfo.patches
                    .filter { patch ->
                        patch.compatiblePackages?.any { pkg -> pkg.packageName == PACKAGE_YOUTUBE } == true
                    }
                    .flatMap { patch ->
                        patch.compatiblePackages
                            ?.firstOrNull { pkg -> pkg.packageName == PACKAGE_YOUTUBE }
                            ?.versions
                            ?: emptyList()
                    }
                    .maxByOrNull { it },
                PACKAGE_YOUTUBE_MUSIC to apiBundleInfo.patches
                    .filter { patch ->
                        patch.compatiblePackages?.any { pkg -> pkg.packageName == PACKAGE_YOUTUBE_MUSIC } == true
                    }
                    .flatMap { patch ->
                        patch.compatiblePackages
                            ?.firstOrNull { pkg -> pkg.packageName == PACKAGE_YOUTUBE_MUSIC }
                            ?.versions
                            ?: emptyList()
                    }
                    .maxByOrNull { it }
            ).filterValues { it != null }
        } ?: emptyMap()
    }

    var showAndroid11Dialog by rememberSaveable { mutableStateOf(false) }
    var showBundlesSheet by remember { mutableStateOf(false) }
    var isRefreshingBundle by remember { mutableStateOf(false) }
    var showPatchesDialog by rememberSaveable { mutableStateOf(false) }
    var showChangelogDialog by rememberSaveable { mutableStateOf(false) }

    var showUnsupportedVersionDialog by rememberSaveable { mutableStateOf<UnsupportedVersionDialogState?>(null) }
    var showWrongPackageDialog by rememberSaveable { mutableStateOf<WrongPackageDialogState?>(null) }

    // APK availability dialog states
    var showApkAvailabilityDialog by rememberSaveable { mutableStateOf(false) }      // Dialog 1
    var showDownloadInstructionsDialog by rememberSaveable { mutableStateOf(false) } // Dialog 2
    var showFilePickerPromptDialog by rememberSaveable { mutableStateOf(false) }     // Dialog 3
    var pendingPackageName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAppName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRecommendedVersion by rememberSaveable { mutableStateOf<String?>(null) }

    // Store the loaded app for unsupported version dialog
    var pendingSelectedApp by remember { mutableStateOf<SelectedApp?>(null) }

    // Helper function to get app name
    fun getAppName(packageName: String): String {
        return when (packageName) {
            PACKAGE_YOUTUBE -> context.getString(R.string.morphe_home_youtube)
            PACKAGE_YOUTUBE_MUSIC -> context.getString(R.string.morphe_home_youtube_music)
            else -> packageName
        }
    }

    val optionsRepository: PatchOptionsRepository = koinInject()

    // Helper function to start patching with selected app
    suspend fun startPatchingWithApp(selectedApp: SelectedApp, allowIncompatible: Boolean) {
        val bundles = withContext(Dispatchers.IO) {
            dashboardViewModel.patchBundleRepository
                .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
                .first()
        }

        // Exclude GmsCore support patch in root mode
        val patches = if (hasRootAccess && isRootMode) {
            bundles.toPatchSelection(allowIncompatible) { _, patch ->
                patch.include && !patch.name.contains("GmsCore", ignoreCase = true)
            }
        } else {
            bundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }
        }

        val bundlePatches = bundles.associate { scoped ->
            scoped.uid to scoped.patches.associateBy { it.name }
        }

        val options = withContext(Dispatchers.IO) {
            optionsRepository.getOptions(selectedApp.packageName, bundlePatches)
        }

        val params = QuickPatchParams(
            selectedApp = selectedApp,
            patches = patches,
            options = options
        )

        onStartQuickPatch(params)
    }

    // File picker launcher for APK selection
    val storagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && pendingPackageName != null) {
            scope.launch {
                val selectedApp = withContext(Dispatchers.IO) {
                    loadLocalApk(context, uri, pendingPackageName!!)
                }

                if (selectedApp != null) {
                    // Check for wrong package
                    if (selectedApp.packageName != pendingPackageName) {
                        showWrongPackageDialog = WrongPackageDialogState(
                            expectedPackage = pendingPackageName!!,
                            actualPackage = selectedApp.packageName
                        )
                        pendingPackageName = null
                        pendingAppName = null
                        pendingRecommendedVersion = null
                        pendingSelectedApp = null
                        return@launch
                    }

                    val allowIncompatible = dashboardViewModel.prefs.disablePatchVersionCompatCheck.getBlocking()

                    val bundles = withContext(Dispatchers.IO) {
                        dashboardViewModel.patchBundleRepository
                            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
                            .first()
                    }

                    val patches = bundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }
                    val totalPatches = patches.values.sumOf { it.size }

                    // Check for no patches (unsupported version)
                    if (totalPatches == 0) {
                        val recommendedVersion = recommendedVersions[selectedApp.packageName]
                        pendingSelectedApp = selectedApp
                        showUnsupportedVersionDialog = UnsupportedVersionDialogState(
                            packageName = selectedApp.packageName,
                            version = selectedApp.version,
                            recommendedVersion = recommendedVersion
                        )
                        pendingPackageName = null
                        pendingAppName = null
                        pendingRecommendedVersion = null
                        return@launch
                    }

                    // Start patching normally
                    startPatchingWithApp(selectedApp, allowIncompatible)

                    // Cleanup all
                    pendingPackageName = null
                    pendingAppName = null
                    pendingRecommendedVersion = null
                    pendingSelectedApp = null
                } else {
                    context.toast(context.getString(R.string.failed_to_load_apk))
                }
            }
        } else {
            pendingPackageName = null
            pendingAppName = null
            pendingRecommendedVersion = null
            showDownloadInstructionsDialog = false
            showFilePickerPromptDialog = false
        }
    }

    // Manager update dialog state
    var hasCheckedForUpdates by rememberSaveable { mutableStateOf(false) }
    val showDialogOnLaunch by dashboardViewModel.prefs.showManagerUpdateDialogOnLaunch.getAsState()
    val updatedManagerVersion = dashboardViewModel.updatedManagerVersion

    // Show dialog only if: (1) not checked yet, (2) dialog enabled, (3) update available
    val shouldShowUpdateDialog =
        !hasCheckedForUpdates && showDialogOnLaunch && !updatedManagerVersion.isNullOrEmpty()

    if (shouldShowUpdateDialog) {
        AvailableUpdateDialog(
            onDismiss = {
                hasCheckedForUpdates = true
            },
            setShowManagerUpdateDialogOnLaunch = dashboardViewModel::setShowManagerUpdateDialogOnLaunch,
            onConfirm = {
                hasCheckedForUpdates = true
                onUpdateClick()
            },
            newVersion = updatedManagerVersion
        )
    }

    val installAppsPermissionLauncher =
        rememberLauncherForActivityResult(RequestInstallAppsContract) {
            showAndroid11Dialog = false
        }

    if (showAndroid11Dialog) {
        Android11Dialog(
            onDismissRequest = { showAndroid11Dialog = false },
            onContinue = { installAppsPermissionLauncher.launch(context.packageName) }
        )
    }

    // State for bundle update snackbar
    var showBundleUpdateSnackbar by remember { mutableStateOf(false) }
    var snackbarStatus by remember { mutableStateOf<BundleUpdateStatus>(BundleUpdateStatus.Updating) }

    // Control snackbar visibility based on progress
    LaunchedEffect(bundleUpdateProgress) {
        val progress = bundleUpdateProgress

        if (progress == null) {
            // Progress cleared - hide snackbar
            showBundleUpdateSnackbar = false
            return@LaunchedEffect
        }

        // We have progress - decide what to show
        when {
            progress.result != null -> {
                // Update completed with a result
                showBundleUpdateSnackbar = true
                snackbarStatus = when (progress.result) {
                    PatchBundleRepository.UpdateResult.Success -> BundleUpdateStatus.Success
                    PatchBundleRepository.UpdateResult.NoInternet,
                    PatchBundleRepository.UpdateResult.Error -> BundleUpdateStatus.Error
                }
            }
            progress.completed < progress.total -> {
                // Still updating
                showBundleUpdateSnackbar = true
                snackbarStatus = BundleUpdateStatus.Updating
            }
            else -> {
                // Completed == total but no result yet - keep showing updating
                showBundleUpdateSnackbar = true
                snackbarStatus = BundleUpdateStatus.Updating
            }
        }
    }

    // Bottom Sheet with scrolling
    if (showBundlesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sheetScrollState = rememberScrollState()

        ModalBottomSheet(
            onDismissRequest = { showBundlesSheet = false },
            sheetState = sheetState,
            scrimColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(sheetScrollState)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // API Bundle Card
                if (apiBundle != null) {
                    ApiPatchBundleCard(
                        bundle = apiBundle,
                        patchCount = patchCounts[apiBundle.uid] ?: 0,
                        updateInfo = manualUpdateInfo[apiBundle.uid],
                        isRefreshing = isRefreshingBundle,
                        onRefresh = {
                            scope.launch {
                                isRefreshingBundle = true
                                try {
                                    dashboardViewModel.patchBundleRepository.updateOfficialBundle(
                                        showProgress = true,  // Show progress in UI
                                        showToast = false     // Don't show toast
                                    )
                                } finally {
                                    delay(500)
                                    isRefreshingBundle = false
                                }
                            }
                        },
                        onOpenInBrowser = {
                            val pageUrl = manualUpdateInfo[apiBundle.uid]?.pageUrl
                                ?: "https://github.com/HundEdFeteTree/HappyFunTest/releases/latest" // FIXME
                            try {
                                uriHandler.openUri(pageUrl)
                            } catch (e: Exception) {
                                context.toast(context.getString(R.string.morphe_home_failed_to_open_url))
                            }
                        },
                        onPatchesClick = { showPatchesDialog = true },
                        onVersionClick = { showChangelogDialog = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    // Dialog 1: Initial "Do you have the APK?" dialog
    if (showApkAvailabilityDialog && pendingPackageName != null && pendingAppName != null) {
        ApkAvailabilityDialog(
            appName = pendingAppName!!,
            packageName = pendingPackageName!!,
            recommendedVersion = pendingRecommendedVersion,
            isRootMode = isRootMode,
            onDismiss = {
                showApkAvailabilityDialog = false
                pendingPackageName = null
                pendingAppName = null
                pendingRecommendedVersion = null
            },
            onHaveApk = {
                // User has APK - open file picker
                showApkAvailabilityDialog = false
                storagePickerLauncher.launch(APK_MIMETYPE)
            },
            onNeedApk = {
                // User needs APK - show download instructions
                showApkAvailabilityDialog = false
                showDownloadInstructionsDialog = true
            }
        )
    }

    // Dialog 2: Download instructions dialog
    if (showDownloadInstructionsDialog && pendingPackageName != null && pendingAppName != null) {
        DownloadInstructionsDialog(
            appName = pendingAppName!!,
            packageName = pendingPackageName!!,
            recommendedVersion = pendingRecommendedVersion,
            onDismiss = {
                showDownloadInstructionsDialog = false
                pendingPackageName = null
                pendingAppName = null
                pendingRecommendedVersion = null
            },
            onContinue = {
                val baseQuery =
                    if (pendingPackageName == PACKAGE_YOUTUBE) {
                        pendingPackageName
                    } else {
                        // Some versions of YT Music don't show when the package name is used, use the app name instead
                        "YouTube Music"
                    }
                val architecture =
                    if (pendingPackageName == PACKAGE_YOUTUBE_MUSIC) {
                        // YT Music requires architecture. This logic could be improved
                        " (${Build.SUPPORTED_ABIS.first()})"
                    } else {
                        ""
                    }

                val version = pendingRecommendedVersion ?: ""
                // Backslash search parameter opens the first search result
                // Use quotes to ensure it's an exact match of all search terms
                val searchQuery = "\\$baseQuery $version $architecture (nodpi) site:apkmirror.com".replace("  ", " ")
                val searchUrl = "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}"
                Log.d(tag, "Using search query: $searchQuery")

                try {
                    uriHandler.openUri(searchUrl)
                    // After opening browser, show file picker prompt
                    showDownloadInstructionsDialog = false
                    showFilePickerPromptDialog = true
                } catch (e: Exception) {
                    context.toast(context.getString(R.string.morphe_home_failed_to_open_url))
                    showDownloadInstructionsDialog = false
                    pendingPackageName = null
                    pendingAppName = null
                    pendingRecommendedVersion = null
                }
            }
        )
    }

    // Dialog 3: File picker prompt dialog
    if (showFilePickerPromptDialog && pendingPackageName != null && pendingAppName != null) {
        FilePickerPromptDialog(
            appName = pendingAppName!!,
            onDismiss = {
                showFilePickerPromptDialog = false
                pendingPackageName = null
                pendingAppName = null
                pendingRecommendedVersion = null
            },
            onOpenFilePicker = {
                showFilePickerPromptDialog = false
                storagePickerLauncher.launch(APK_MIMETYPE)
            }
        )
    }

    // Unsupported Version Dialog
    showUnsupportedVersionDialog?.let { state ->
        UnsupportedVersionWarningDialog(
            packageName = state.packageName,
            version = state.version,
            recommendedVersion = state.recommendedVersion,
            onDismiss = {
                showUnsupportedVersionDialog = null
                // Clean up the pending app
                pendingSelectedApp?.let { app ->
                    if (app is SelectedApp.Local && app.temporary) {
                        app.file.delete()
                    }
                }
                pendingSelectedApp = null
            },
            onProceed = {
                showUnsupportedVersionDialog = null
                // Start patching with the already loaded app
                pendingSelectedApp?.let { app ->
                    scope.launch {
                        startPatchingWithApp(app, true)
                        pendingSelectedApp = null
                    }
                }
            }
        )
    }

    // Wrong Package Dialog
    showWrongPackageDialog?.let { state ->
        WrongPackageDialog(
            expectedPackage = state.expectedPackage,
            actualPackage = state.actualPackage,
            onDismiss = { showWrongPackageDialog = null }
        )
    }

    if (showPatchesDialog && apiBundle != null) {
        BundlePatchesDialog(
            src = apiBundle,
            onDismissRequest = { showPatchesDialog = false },
            showTopBar = !useMorpheHomeScreen
        )
    }

    if (showChangelogDialog && apiBundle != null) {
        val remoteBundle = apiBundle.asRemoteOrNull
        if (remoteBundle != null) {
            BundleChangelogDialog(
                src = remoteBundle,
                onDismissRequest = { showChangelogDialog = false },
                showTopBar = !useMorpheHomeScreen
            )
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Bundle update snackbar
            AnimatedVisibility(
                visible = showBundleUpdateSnackbar,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(durationMillis = 500)
                ) + fadeIn(animationSpec = tween(durationMillis = 500)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(durationMillis = 500)
                ) + fadeOut(animationSpec = tween(durationMillis = 500)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                BundleUpdateSnackbar(
                    status = snackbarStatus,
                    progress = bundleUpdateProgress
                )
            }

            // Main centered content
            MainContent(
                onYouTubeClick = {
                    // Check if patches are being fetched or if no patches available
                    if (bundleUpdateProgress != null || availablePatches < 1) {
                        val message = if (bundleUpdateProgress != null) {
                            context.getString(R.string.morphe_home_patches_are_loading)
                        } else {
                            context.getString(R.string.no_patch_found)
                        }
                        context.toast(message)
                        return@MainContent
                    }
                    if (dashboardViewModel.android11BugActive) {
                        showAndroid11Dialog = true
                        return@MainContent
                    }
                    // Show APK availability dialog
                    pendingPackageName = PACKAGE_YOUTUBE
                    pendingAppName = getAppName(PACKAGE_YOUTUBE)
                    pendingRecommendedVersion = recommendedVersions[PACKAGE_YOUTUBE]
                    showApkAvailabilityDialog = true
                },
                onYouTubeMusicClick = {
                    // Check if patches are being fetched or if no patches available
                    if (bundleUpdateProgress != null || availablePatches < 1) {
                        val message = if (bundleUpdateProgress != null) {
                            context.getString(R.string.morphe_home_patches_are_loading)
                        } else {
                            context.getString(R.string.no_patch_found)
                        }
                        context.toast(message)
                        return@MainContent
                    }
                    if (dashboardViewModel.android11BugActive) {
                        showAndroid11Dialog = true
                        return@MainContent
                    }
                    // Show APK availability dialog
                    pendingPackageName = PACKAGE_YOUTUBE_MUSIC
                    pendingAppName = getAppName(PACKAGE_YOUTUBE_MUSIC)
                    pendingRecommendedVersion = recommendedVersions[PACKAGE_YOUTUBE_MUSIC]
                    showApkAvailabilityDialog = true
                }
            )

            // Floating Action Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Update FAB with badge for manager updates
                if (!dashboardViewModel.updatedManagerVersion.isNullOrEmpty()) {
                    FloatingActionButton(
                        onClick = onUpdateClick,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        BadgedBox(
                            badge = {
                                Badge(modifier = Modifier.size(6.dp))
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Update,
                                contentDescription = stringResource(R.string.update)
                            )
                        }
                    }
                }

                // Source FAB
                FloatingActionButton(
                    onClick = { showBundlesSheet = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        Icons.Outlined.Source,
                        contentDescription = stringResource(R.string.morphe_home_bundles)
                    )
                }

                FloatingActionButton(
                    onClick = onMorpheSettingsClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings)
                    )
                }
            }
        }
    }
}

// Helper function to load local APK
private suspend fun loadLocalApk(
    context: Context,
    uri: Uri,
    expectedPackageName: String
): SelectedApp.Local? = withContext(Dispatchers.IO) {
    try {
        val tempFile = File(context.cacheDir, "temp_apk_${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val pm = context.packageManager
        val packageInfo = pm.getPackageArchiveInfo(
            tempFile.absolutePath,
            android.content.pm.PackageManager.GET_META_DATA
        )

        if (packageInfo == null) {
            tempFile.delete()
            return@withContext null
        }

        // Don't validate here - let caller handle it
        SelectedApp.Local(
            packageName = packageInfo.packageName,
            version = packageInfo.versionName ?: "unknown",
            file = tempFile,
            temporary = true
        )
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun UnsupportedVersionWarningDialog(
    packageName: String,
    version: String,
    recommendedVersion: String?,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.morphe_patcher_unsupported_version_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.morphe_patcher_unsupported_version_dialog_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Package Name
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.package_name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = packageName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // Selected Version
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.morphe_patcher_selected_version),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = version,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            // Recommended Version (if available)
                            if (recommendedVersion != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.morphe_home_recommended_version),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = recommendedVersion,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.morphe_patcher_unsupported_version_dialog_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onProceed,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(stringResource(R.string.morphe_patcher_unsupported_version_dialog_proceed))
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun WrongPackageDialog(
    expectedPackage: String,
    actualPackage: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.morphe_patcher_wrong_package_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.morphe_patcher_wrong_package_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.morphe_patcher_expected_package),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = expectedPackage,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    lineHeight = 20.sp
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.morphe_patcher_selected_package),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = actualPackage,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun BundleUpdateSnackbar(
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?
) {
    val fraction = if (progress?.total == 0 || progress == null) {
        0f
    } else {
        progress.completed.toFloat() / progress.total
    }

    val containerColor = when (status) {
        BundleUpdateStatus.Success -> MaterialTheme.colorScheme.primaryContainer
        BundleUpdateStatus.Error   -> MaterialTheme.colorScheme.errorContainer
        BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (status) {
        BundleUpdateStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        BundleUpdateStatus.Error   -> MaterialTheme.colorScheme.onErrorContainer
        BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon based on status
            when (status) {
                BundleUpdateStatus.Success -> {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                BundleUpdateStatus.Error -> {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
                BundleUpdateStatus.Updating -> {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (status) {
                        BundleUpdateStatus.Updating -> stringResource(R.string.morphe_home_updating_patches)
                        BundleUpdateStatus.Success -> stringResource(R.string.morphe_home_update_success)
                        BundleUpdateStatus.Error -> stringResource(R.string.morphe_home_update_error)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = when (status) {
                        BundleUpdateStatus.Updating -> {
                            if (progress != null && progress.total > 0) {
                                stringResource(
                                    R.string.bundle_update_progress,
                                    progress.completed,
                                    progress.total
                                )
                            } else {
                                stringResource(R.string.morphe_home_please_wait)
                            }
                        }
                        BundleUpdateStatus.Success -> stringResource(R.string.morphe_home_patches_updated)
                        BundleUpdateStatus.Error -> {
                            // Check if it's a no internet error
                            if (progress?.result == PatchBundleRepository.UpdateResult.NoInternet) {
                                stringResource(R.string.morphe_home_no_internet)
                            } else {
                                stringResource(R.string.morphe_home_update_error_subtitle)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }

        // Progress bar only for updating status
        if (status == BundleUpdateStatus.Updating) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun ModernNotificationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiPatchBundleCard(
    bundle: PatchBundleSource,
    patchCount: Int,
    updateInfo: PatchBundleRepository.ManualBundleUpdateInfo?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onPatchesClick: () -> Unit,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Source,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bundle.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.morphe_home_bundle_type_api),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = bundle.updatedAt?.let { getRelativeTimeString(it) }
                                ?: stringResource(R.string.morphe_home_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onOpenInBrowser) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.morphe_home_open_in_browser),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatChip(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.patches),
                    value = patchCount.toString(),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onPatchesClick),
                    clickable = patchCount > 0
                )

                StatChip(
                    icon = Icons.Outlined.Update,
                    label = stringResource(R.string.version),
                    value = updateInfo?.latestVersion?.removePrefix("v")
                        ?: bundle.patchBundle?.manifestAttributes?.version?.removePrefix("v")
                        ?: "N/A",
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onVersionClick),
                    clickable = true
                )
            }

            // Expandable dates section
            var showDates by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDates = !showDates },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.morphe_home_timeline),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (showDates)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = showDates) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimelineItem(
                                icon = Icons.Outlined.CalendarToday,
                                label = stringResource(R.string.morphe_home_date_added),
                                time = bundle.createdAt ?: 0L,
                            )

                            TimelineItem(
                                icon = Icons.Outlined.Refresh,
                                label = stringResource(R.string.morphe_home_date_updated),
                                time = bundle.updatedAt ?: 0L,
                                isLast = true
                            )
                        }
                    }
                }
            }

            // Update button
            Button(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (updateInfo != null) stringResource(R.string.update)
                    else stringResource(R.string.morphe_home_check_updates),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    clickable: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TimelineItem(
    icon: ImageVector,
    label: String,
    time: Long?,
    isLast: Boolean = false
) {
    val dateTimeFormatter = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = time?.let { dateTimeFormatter.format(it) }
                    ?: stringResource(R.string.morphe_home_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = time?.let { getRelativeTimeString(it) }
                    ?: stringResource(R.string.morphe_home_unknown),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun getRelativeTimeString(timestamp: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

@Composable
private fun MainContent(
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
) {
    val greeting = HomeAndPatcherMessages.getHomeMessage(LocalContext.current)

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Maximum width for buttons in landscape mode
    val maxButtonWidth = if (isLandscape) 500.dp else Dp.Infinity

    // Get theme colors outside of Canvas
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // Create animations for each circle
    val infiniteTransition = rememberInfiniteTransition(label = "circles")

    // Circle 1 - large top left
    val circle1X = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle1X"
    )
    val circle1Y = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle1Y"
    )

    // Circle 2 - medium top right
    val circle2X = infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle2X"
    )
    val circle2Y = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle2Y"
    )

    // Circle 3 - small center right
    val circle3X = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle3X"
    )
    val circle3Y = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle3Y"
    )

    // Circle 4 - medium bottom right
    val circle4X = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(9500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle4X"
    )
    val circle4Y = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle4Y"
    )

    // Circle 5 - small bottom left
    val circle5X = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(8200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle5X"
    )
    val circle5Y = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.73f,
        animationSpec = infiniteRepeatable(
            animation = tween(6800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle5Y"
    )

    // Circle 6 - bottom center
    val circle6X = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(8800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle6X"
    )
    val circle6Y = infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.87f,
        animationSpec = infiniteRepeatable(
            animation = tween(7800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle6Y"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Animated circles in background
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Circle 1 - large top left
            drawCircle(
                color = primaryColor.copy(alpha = 0.03f),
                radius = 400f,
                center = Offset(size.width * circle1X.value, size.height * circle1Y.value)
            )

            // Circle 2 - medium top right
            drawCircle(
                color = tertiaryColor.copy(alpha = 0.025f),
                radius = 280f,
                center = Offset(size.width * circle2X.value, size.height * circle2Y.value)
            )

            // Circle 3 - small center right
            drawCircle(
                color = tertiaryColor.copy(alpha = 0.02f),
                radius = 200f,
                center = Offset(size.width * circle3X.value, size.height * circle3Y.value)
            )

            // Circle 4 - medium bottom right
            drawCircle(
                color = secondaryColor.copy(alpha = 0.025f),
                radius = 320f,
                center = Offset(size.width * circle4X.value, size.height * circle4Y.value)
            )

            // Circle 5 - small bottom left
            drawCircle(
                color = primaryColor.copy(alpha = 0.02f),
                radius = 180f,
                center = Offset(size.width * circle5X.value, size.height * circle5Y.value)
            )

            // Circle 6 - bottom center
            drawCircle(
                color = secondaryColor.copy(alpha = 0.02f),
                radius = 220f,
                center = Offset(size.width * circle6X.value, size.height * circle6Y.value)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(32.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(greeting),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(32.dp))

            // Buttons container with constrained width
            Column(
                modifier = Modifier.widthIn(max = maxButtonWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // YouTube Button
                AppButton(
                    text = stringResource(R.string.morphe_home_youtube),
                    backgroundColor = Color(0xFFFF0033),
                    contentColor = Color.White,
                    onClick = onYouTubeClick
                )

                // YouTube Music Button
                AppButton(
                    text = stringResource(R.string.morphe_home_youtube_music),
                    backgroundColor = Color(0xFF121212),
                    contentColor = Color.White,
                    gradientColors = listOf(Color(0xFFFF3E5A), Color(0xFFFF8C3E), Color(0xFFFFD23E)),
                    onClick = onYouTubeMusicClick
                )
            }
        }
    }
}

@Composable
private fun AppButton(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    gradientColors: List<Color>? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (gradientColors != null) {
                        Brush.horizontalGradient(gradientColors)
                    } else {
                        Brush.horizontalGradient(listOf(backgroundColor, backgroundColor))
                    },
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}

// Dialog 1: Initial "Do you have the APK?" dialog
@Composable
private fun ApkAvailabilityDialog(
    appName: String,
    packageName: String,
    recommendedVersion: String?,
    isRootMode: Boolean,
    onDismiss: () -> Unit,
    onHaveApk: () -> Unit,
    onNeedApk: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.morphe_home_apk_availability_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Description with root warning
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.morphe_home_apk_availability_dialog_description_simple,
                            appName,
                            recommendedVersion ?: stringResource(R.string.any_version)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Root mode warning
                    if (isRootMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.morphe_root_install_apk_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Info Card with package and version
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Package Name
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.package_name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = packageName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // Version (if specified)
                            if (recommendedVersion != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.morphe_home_recommended_version),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = recommendedVersion,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "Yes, I have it" button
                    Button(
                        onClick = onHaveApk,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.morphe_home_apk_availability_yes))
                    }

                    // "No, I need to download" button
                    OutlinedButton(
                        onClick = onNeedApk,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.morphe_home_apk_availability_no))
                    }
                }
            }
        }
    }
}

// Dialog 2: Download instructions dialog
@Composable
private fun DownloadInstructionsDialog(
    appName: String,
    packageName: String,
    recommendedVersion: String?,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.morphe_home_download_instructions_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Instructions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.morphe_home_download_instructions_description,
                            appName,
                            recommendedVersion ?: stringResource(R.string.any_version)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Step-by-step instructions card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.morphe_home_download_instructions_steps_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Step 1
                            InstructionStep(
                                number = "1",
                                text = stringResource(R.string.morphe_home_download_instructions_step1)
                            )

                            // Step 2 - with styled "DOWNLOAD APK" text
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "2",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.morphe_home_download_instructions_step2_part1),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    // Styled "DOWNLOAD APK" button
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFE53935), // Red color matching APKMirror
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.morphe_home_download_instructions_download_button),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            // Step 3
                            InstructionStep(
                                number = "3",
                                text = stringResource(R.string.morphe_home_download_instructions_step3)
                            )

                            // Step 4
                            InstructionStep(
                                number = "4",
                                text = stringResource(R.string.morphe_home_download_instructions_step4)
                            )
                        }
                    }

                    // Important note
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.morphe_home_download_instructions_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "Continue" button - opens browser
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.morphe_home_download_instructions_continue))
                    }

                    // "Cancel" button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

// Helper composable for instruction steps
@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

// Dialog 3: File picker prompt dialog
@Composable
private fun FilePickerPromptDialog(
    appName: String,
    onDismiss: () -> Unit,
    onOpenFilePicker: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.morphe_home_file_picker_prompt_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Description
                Text(
                    text = stringResource(R.string.morphe_home_file_picker_prompt_description, appName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "Open file picker" button
                    Button(
                        onClick = onOpenFilePicker,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.morphe_home_file_picker_prompt_open))
                    }

                    // "Cancel" button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}
