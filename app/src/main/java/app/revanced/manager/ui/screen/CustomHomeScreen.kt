package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.ui.component.NotificationCard
import app.revanced.manager.ui.component.QuickPatchSourceSelectorDialog
import app.revanced.manager.ui.component.AvailableUpdateDialog
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.QuickPatchViewModel
import app.revanced.manager.domain.repository.PatchBundleRepository
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

private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
private const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHomeScreen( // TODO: Rename to MorpheHomeScreen
    onSettingsClick: () -> Unit,
    onAllAppsClick: () -> Unit,
    onDownloaderPluginClick: () -> Unit,
    onStartQuickPatch: (QuickPatchViewModel.QuickPatchParams) -> Unit,
    onUpdateClick: () -> Unit = {},
    dashboardViewModel: DashboardViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val showNewDownloaderPluginsNotification by dashboardViewModel.newDownloaderPluginsAvailable.collectAsStateWithLifecycle(false)
    val bundleUpdateProgress by dashboardViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)

    // Check if bundles are ready and loaded
    val isBundlesReady by remember {
        derivedStateOf { availablePatches > 0 }
    }

    val hasSheetNotifications by remember {
        derivedStateOf {
            dashboardViewModel.showBatteryOptimizationsWarning || showNewDownloaderPluginsNotification
        }
    }

    var showAndroid11Dialog by rememberSaveable { mutableStateOf(false) }
    var showBundlesSheet by remember { mutableStateOf(false) }
    var showQuickPatchDialog by rememberSaveable { mutableStateOf(false) }
    var selectedPackageName by rememberSaveable { mutableStateOf<String?>(null) }

    // Manager update dialog state
    var hasCheckedForUpdates by rememberSaveable { mutableStateOf(false) }
    val showDialogOnLaunch by dashboardViewModel.prefs.showManagerUpdateDialogOnLaunch.getAsState()
    val updatedManagerVersion = dashboardViewModel.updatedManagerVersion

    // Show dialog only if: (1) not checked yet, (2) dialog enabled, (3) update available
    val shouldShowUpdateDialog = !hasCheckedForUpdates && showDialogOnLaunch && !updatedManagerVersion.isNullOrEmpty()

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
    var lastBundleUpdateProgress by remember { mutableStateOf<PatchBundleRepository.BundleUpdateProgress?>(null) }

    val installAppsPermissionLauncher = rememberLauncherForActivityResult(RequestInstallAppsContract) {
        showAndroid11Dialog = false
    }

    // Control snackbar visibility
    LaunchedEffect(bundleUpdateProgress) {
        if (bundleUpdateProgress != null) {
            lastBundleUpdateProgress = bundleUpdateProgress
            showBundleUpdateSnackbar = true
        } else if (showBundleUpdateSnackbar) {
            // Wait minimum 3 seconds before allowing hide
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

    // Bottom Sheet
    if (showBundlesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBundlesSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    dashboardViewModel.updateBatteryOptimizationsWarning()
                }

                if (dashboardViewModel.showBatteryOptimizationsWarning) {
                    NotificationCard(
                        isWarning = true,
                        icon = Icons.Default.BatteryAlert,
                        text = stringResource(R.string.battery_optimization_notification),
                        onClick = {
                            batteryLauncher.launch(
                                Intent(
                                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (showNewDownloaderPluginsNotification) {
                    NotificationCard(
                        text = stringResource(R.string.new_downloader_plugins_notification),
                        icon = Icons.Outlined.Download,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onDownloaderPluginClick() },
                        actions = {
                            TextButton(onClick = dashboardViewModel::ignoreNewDownloaderPlugins) {
                                Text(stringResource(R.string.dismiss))
                            }
                        }
                    )
                }

                BundleListScreen(
                    eventsFlow = dashboardViewModel.bundleListEventsFlow,
                    setSelectedSourceCount = { },
                    showOrderDialog = false,
                    onDismissOrderDialog = { },
                    onScrollStateChange = { }
                )
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
                visible = showBundleUpdateSnackbar && lastBundleUpdateProgress != null && isBundlesReady,
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
                lastBundleUpdateProgress?.let { progress ->
                    val fraction = if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.bundle_update_banner_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.bundle_update_progress, progress.completed, progress.total),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
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

                // Sources FAB with notification dot
                Box {
                    FloatingActionButton(
                        onClick = { showBundlesSheet = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            Icons.Outlined.Source,
                            contentDescription = stringResource(R.string.custom_home_bundles)
                        )
                    }

                    if (hasSheetNotifications) {
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color.White, CircleShape))
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
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
    }
}

@Composable
private fun MainContent(
    availablePatches: Int,
    onAllAppsClick: () -> Unit,
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Funny rotating greeting message
            val greetingMessages = listOf(
                R.string.home_greeting_1,
                R.string.home_greeting_2,
                R.string.home_greeting_3,
                R.string.home_greeting_4,
                R.string.home_greeting_5,
                R.string.home_greeting_6,
                R.string.home_greeting_7,
            )
            var currentGreetingIndex by rememberSaveable { mutableIntStateOf((0..<greetingMessages.size).random()) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.TopCenter
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
                text = stringResource(R.string.custom_home_youtube),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = stringResource(R.string.custom_home_youtube),
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
                text = stringResource(R.string.custom_home_youtube_music),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = stringResource(R.string.custom_home_youtube_music),
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                },
                backgroundColor = Color(0xFF121212),
                contentColor = Color.White,
                gradientColors = listOf(Color(0xFFFF3E5A), Color(0xFFFF8C3E), Color(0xFFFFD23E)),
                onClick = onYouTubeMusicClick
            )

            Spacer(Modifier.height(100.dp))
        }

        // Bottom "Other apps" button.
        TextButton(
            onClick = onAllAppsClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.custom_home_other_apps),
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
        modifier = Modifier.fillMaxWidth().height(100.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = contentColor),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (gradientColors != null)
                        Modifier.background(Brush.horizontalGradient(gradientColors), RoundedCornerShape(28.dp))
                    else
                        Modifier.background(backgroundColor, RoundedCornerShape(28.dp))
                )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = contentColor, strokeWidth = 3.dp)
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { icon() }
                    Spacer(Modifier.width(20.dp))
                    Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
