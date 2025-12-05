package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.ui.component.QuickPatchSourceSelectorDialog
import app.revanced.manager.ui.component.AvailableUpdateDialog
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.QuickPatchViewModel
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.EventEffect
import app.revanced.manager.util.RequestInstallAppsContract
import app.revanced.manager.util.toast
import app.morphe.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import android.provider.Settings as AndroidSettings
import java.text.SimpleDateFormat
import java.util.Locale

private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
private const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheHomeScreen(
    onSettingsClick: () -> Unit,
    onAllAppsClick: () -> Unit,
    onDownloaderPluginClick: () -> Unit,
    onStartQuickPatch: (QuickPatchViewModel.QuickPatchParams) -> Unit,
    onUpdateClick: () -> Unit = {},
    dashboardViewModel: DashboardViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val showNewDownloaderPluginsNotification by dashboardViewModel.newDownloaderPluginsAvailable.collectAsStateWithLifecycle(false)
    val bundleUpdateProgress by dashboardViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)
    val sources by dashboardViewModel.patchBundleRepository.sources.collectAsStateWithLifecycle(emptyList())
    val patchCounts by dashboardViewModel.patchBundleRepository.patchCountsFlow.collectAsStateWithLifecycle(emptyMap())
    val manualUpdateInfo by dashboardViewModel.patchBundleRepository.manualUpdateInfo.collectAsStateWithLifecycle(emptyMap())

    // Get only the API bundle (uid = 0)
    val apiBundle = remember(sources) { sources.firstOrNull { it.uid == 0 } }

    val hasSheetNotifications by remember {
        derivedStateOf {
            dashboardViewModel.showBatteryOptimizationsWarning || showNewDownloaderPluginsNotification
        }
    }

    var showAndroid11Dialog by rememberSaveable { mutableStateOf(false) }
    var showBundlesSheet by remember { mutableStateOf(false) }
    var showQuickPatchDialog by rememberSaveable { mutableStateOf(false) }
    var selectedPackageName by rememberSaveable { mutableStateOf<String?>(null) }
    var isRefreshingBundle by remember { mutableStateOf(false) }

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

    // State for bundle update snackbar with minimum display time
    var showBundleUpdateSnackbar by remember { mutableStateOf(false) }
    var bundleUpdateCompleted by remember { mutableStateOf(false) }

    val installAppsPermissionLauncher =
        rememberLauncherForActivityResult(RequestInstallAppsContract) {
            showAndroid11Dialog = false
        }

    LaunchedEffect(Unit) {
        dashboardViewModel.patchBundleRepository.checkManualUpdates(0)
    }

    // Control snackbar visibility
    LaunchedEffect(bundleUpdateProgress) {
        val progress = bundleUpdateProgress
        if (progress != null) {
            showBundleUpdateSnackbar = true
            bundleUpdateCompleted = false

            // Check if update is complete
            if (progress.completed >= progress.total && progress.total > 0) {
                bundleUpdateCompleted = true
                delay(3000) // Show completed state for 3 seconds minimum
                showBundleUpdateSnackbar = false
            }
        } else if (showBundleUpdateSnackbar && !bundleUpdateCompleted) {
            // Progress became null but wasn't completed
            delay(3000)
            showBundleUpdateSnackbar = false
        }
    }

    if (showAndroid11Dialog) {
        Android11Dialog(
            onDismissRequest = { showAndroid11Dialog = false },
            onContinue = { installAppsPermissionLauncher.launch(context.packageName) }
        )
    }
    // Bottom Sheet with scrolling
    if (showBundlesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sheetScrollState = rememberScrollState()

        ModalBottomSheet(
            onDismissRequest = { showBundlesSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(sheetScrollState)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val batteryLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        dashboardViewModel.updateBatteryOptimizationsWarning()
                    }

                // API Bundle Card
                if (apiBundle != null) {
                    Text(
                        text = stringResource(R.string.morphe_home_patches_source),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    ApiPatchBundleCard(
                        bundle = apiBundle,
                        patchCount = patchCounts[apiBundle.uid] ?: 0,
                        updateInfo = manualUpdateInfo[apiBundle.uid],
                        isRefreshing = isRefreshingBundle,
                        onRefresh = {
                            scope.launch {
                                isRefreshingBundle = true
                                try {
                                    if (apiBundle is RemotePatchBundle) {
                                        dashboardViewModel.patchBundleRepository.update(
                                            apiBundle,
                                            showToast = true
                                        )
                                    }
                                } finally {
                                    delay(500)
                                    isRefreshingBundle = false
                                }
                            }
                        },
                        onCardClick = {
                            // TODO: add onBundleSettingsClick(apiBundle.uid)
                        },
                        onOpenInBrowser = {
                            val pageUrl = manualUpdateInfo[apiBundle.uid]?.pageUrl
                                ?: "https://github.com/LisoUseInAIKyrios/revanced-patches/releases/latest" // FIXME
                            try {
                                uriHandler.openUri(pageUrl)
                            } catch (e: Exception) {
                                context.toast(context.getString(R.string.morphe_home_failed_to_open_url))
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Notifications section
                if (dashboardViewModel.showBatteryOptimizationsWarning || showNewDownloaderPluginsNotification) {
                    Text(
                        text = stringResource(R.string.morphe_home_notifications),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Battery Optimization Warning
                if (dashboardViewModel.showBatteryOptimizationsWarning) {
                    ModernNotificationCard(
                        icon = Icons.Default.BatteryAlert,
                        title = stringResource(R.string.morphe_home_battery_optimization_warning_title),
                        message = stringResource(R.string.battery_optimization_notification),
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                showBundlesSheet = false
                            }
                            batteryLauncher.launch(
                                Intent(
                                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Downloader Plugins Notification
                if (showNewDownloaderPluginsNotification) {
                    ModernNotificationCard(
                        icon = Icons.Outlined.Download,
                        title = stringResource(R.string.morphe_home_new_plugins_available),
                        message = stringResource(R.string.new_downloader_plugins_notification),
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                showBundlesSheet = false
                            }
                            onDownloaderPluginClick()
                        },
                        onDismiss = dashboardViewModel::ignoreNewDownloaderPlugins,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
    // Quick Patch Dialog
    if (showQuickPatchDialog && selectedPackageName != null) {
        // Use key to create a new ViewModel for each packageName
        val quickPatchViewModel: QuickPatchViewModel = koinViewModel(
            key = selectedPackageName
        ) {
            parametersOf(selectedPackageName)
        }

        val plugins by quickPatchViewModel.plugins.collectAsStateWithLifecycle(emptyList())
        val requiredVersion by quickPatchViewModel.bundleRepository.suggestedVersions
            .collectAsStateWithLifecycle(emptyMap())

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = quickPatchViewModel::handlePluginActivityResult
        )
        EventEffect(flow = quickPatchViewModel.launchActivityFlow) { intent ->
            launcher.launch(intent)
        }

        val storagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = quickPatchViewModel::handleStorageResult
        )
        EventEffect(flow = quickPatchViewModel.requestStorageSelection) {
            storagePickerLauncher.launch(APK_MIMETYPE)
        }

        EventEffect(flow = quickPatchViewModel.startPatchingFlow) { params ->
            showQuickPatchDialog = false
            selectedPackageName = null
            onStartQuickPatch(params)
        }

        QuickPatchSourceSelectorDialog(
            packageName = selectedPackageName!!,
            suggestedVersion = quickPatchViewModel.suggestedVersion,
            plugins = plugins,
            installedApp = quickPatchViewModel.installedAppData,
            downloadedApps = quickPatchViewModel.downloadedApps,
            hasRoot = quickPatchViewModel.hasRoot,
            activeSearchJob = quickPatchViewModel.activePluginAction,
            requiredVersion = requiredVersion[selectedPackageName],
            onDismissRequest = {
                showQuickPatchDialog = false
                selectedPackageName = null
            },
            onSelectAuto = { quickPatchViewModel.selectAuto() },
            onSelectInstalled = { quickPatchViewModel.selectInstalledApp(it) },
            onSelectDownloaded = { quickPatchViewModel.selectDownloadedApp(it) },
            onSelectLocal = { quickPatchViewModel.requestLocalSelection() },
            onSelectPlugin = { quickPatchViewModel.searchUsingPlugin(it) }
        )
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Bundle update snackbar
            AnimatedVisibility(
                visible = showBundleUpdateSnackbar && bundleUpdateProgress != null,
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
                bundleUpdateProgress?.let { progress ->
                    BundleUpdateSnackbar(
                        progress = progress,
                        isCompleted = bundleUpdateCompleted
                    )
                }
            }

            // Main centered content
            MainContent(
                availablePatches = availablePatches,
                onAllAppsClick = onAllAppsClick,
                onYouTubeClick = {
                    if (availablePatches < 1) {
                        context.toast(context.getString(R.string.no_patch_found))
                        scope.launch { showBundlesSheet = true }
                        return@MainContent
                    }
                    if (dashboardViewModel.android11BugActive) {
                        showAndroid11Dialog = true
                        return@MainContent
                    }
                    selectedPackageName = PACKAGE_YOUTUBE
                    showQuickPatchDialog = true
                },
                onYouTubeMusicClick = {
                    if (availablePatches < 1) {
                        context.toast(context.getString(R.string.no_patch_found))
                        scope.launch { showBundlesSheet = true }
                        return@MainContent
                    }
                    if (dashboardViewModel.android11BugActive) {
                        showAndroid11Dialog = true
                        return@MainContent
                    }
                    selectedPackageName = PACKAGE_YOUTUBE_MUSIC
                    showQuickPatchDialog = true
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

                // Source FAB with notification dot
                Box {
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

                    if (hasSheetNotifications) {
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.Center)
                                    .background(Color.Red, CircleShape)
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onSettingsClick,
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

@Composable
private fun BundleUpdateSnackbar(
    progress: PatchBundleRepository.BundleUpdateProgress,
    isCompleted: Boolean
) {
    val fraction = if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
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
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCompleted) {
                        stringResource(R.string.morphe_home_bundle_update_completed)
                    } else {
                        stringResource(R.string.bundle_update_banner_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.bundle_update_progress,
                        progress.completed,
                        progress.total
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isCompleted) {
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
    onCardClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
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
                        Icons.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.morphe_home_open_in_browser),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Stats Row - компактніше
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatChip(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.patches),
                    value = patchCount.toString(),
                    modifier = Modifier.weight(1f)
                )

                StatChip(
                    icon = Icons.Outlined.Update,
                    label = stringResource(R.string.version),
                    value = updateInfo?.latestVersion?.removePrefix("v")
                        ?: bundle.patchBundle?.manifestAttributes?.version?.removePrefix("v")
                        ?: "N/A",
                    modifier = Modifier.weight(1f)
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

// Helper composables
@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    time: Long?, // Зробити nullable
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

// Helper function for relative time
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
    availablePatches: Int,
    onAllAppsClick: () -> Unit,
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 32.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Funny rotating greeting message
        val greetingMessages = listOf(
            R.string.morphe_home_greeting_1,
            R.string.morphe_home_greeting_2,
            R.string.morphe_home_greeting_3,
            R.string.morphe_home_greeting_4,
            R.string.morphe_home_greeting_5,
            R.string.morphe_home_greeting_6,
            R.string.morphe_home_greeting_7,
        )
        var currentGreetingIndex by rememberSaveable { mutableIntStateOf((0..<greetingMessages.size).random()) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = greetingMessages[currentGreetingIndex],
                transitionSpec = {
                    fadeIn(tween(800)) togetherWith fadeOut(tween(800))
                },
                label = "greeting_animation"
            ) { resId ->
                Text(
                    text = stringResource(resId),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // YouTube Button
        AppButton(
            text = stringResource(R.string.morphe_home_youtube),
            icon = {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = stringResource(R.string.morphe_home_youtube),
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            },
            backgroundColor = Color(0xFFFF0033),
            contentColor = Color.White,
            onClick = onYouTubeClick
        )

        Spacer(Modifier.height(24.dp))

        // YouTube Music Button
        AppButton(
            text = stringResource(R.string.morphe_home_youtube_music),
            icon = {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = stringResource(R.string.morphe_home_youtube_music),
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            },
            backgroundColor = Color(0xFF121212),
            contentColor = Color.White,
            gradientColors = listOf(Color(0xFFFF3E5A), Color(0xFFFF8C3E), Color(0xFFFFD23E)),
            onClick = onYouTubeMusicClick
        )

        Spacer(Modifier.height(24.dp))

        // Other apps button
        androidx.compose.material3.TextButton(onClick = onAllAppsClick) {
            Text(
                text = stringResource(R.string.morphe_home_advanced_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AppButton(
    text: String,
    icon: @Composable () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    gradientColors: List<Color>? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (gradientColors != null)
                        Modifier.background(
                            Brush.horizontalGradient(gradientColors),
                            RoundedCornerShape(28.dp)
                        )
                    else
                        Modifier.background(backgroundColor, RoundedCornerShape(28.dp))
                )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = contentColor, strokeWidth = 3.dp)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { icon() }
                    Spacer(Modifier.width(20.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
