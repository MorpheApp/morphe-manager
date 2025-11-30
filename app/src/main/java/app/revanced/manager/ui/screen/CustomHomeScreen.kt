package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.domain.repository.PatchBundleRepository.BundleUpdateProgress
import app.revanced.manager.ui.component.NotificationCard
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.util.RequestInstallAppsContract
import app.revanced.manager.util.toast
import app.universal.revanced.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import android.provider.Settings as AndroidSettings

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHomeScreen(
    onSettingsClick: () -> Unit,
    onAllAppsClick: () -> Unit,
    onDownloaderPluginClick: () -> Unit,
    onAppSelected: (String) -> Unit,
    dashboardViewModel: DashboardViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val showNewDownloaderPluginsNotification by dashboardViewModel.newDownloaderPluginsAvailable.collectAsStateWithLifecycle(false)
    val bundleUpdateProgress by dashboardViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)

    // Any notification in the sheet?
    val hasSheetNotifications by remember {
        derivedStateOf {
            dashboardViewModel.showBatteryOptimizationsWarning || showNewDownloaderPluginsNotification
        }
    }

    var isNavigating by rememberSaveable { mutableStateOf(false) }
    var showAndroid11Dialog by rememberSaveable { mutableStateOf(false) }
    var showBundlesSheet by remember { mutableStateOf(false) }

    val installAppsPermissionLauncher = rememberLauncherForActivityResult(RequestInstallAppsContract) {
        showAndroid11Dialog = false
    }

    // Reset navigation state after a short delay.
    LaunchedEffect(isNavigating) {
        if (isNavigating) {
            delay(1000) // Delay for smooth animation.
            isNavigating = false
        }
    }

    if (showAndroid11Dialog) {
        Android11Dialog(
            onDismissRequest = { showAndroid11Dialog = false },
            onContinue = { installAppsPermissionLauncher.launch(context.packageName) }
        )
    }

    // Bottom Sheet.
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

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sources FAB.
                Box {
                    SmallFloatingActionButton(
                        onClick = { showBundlesSheet = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            Icons.Outlined.Source,
                            contentDescription = stringResource(R.string.custom_home_bundles)
                        )
                    }

                    // Red dot.
                    if (hasSheetNotifications) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.Center)
                                    .background(Color.Red, CircleShape)
                            )
                        }
                    }
                }

                SmallFloatingActionButton(
                    onClick = onSettingsClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Bundle update progress — fixed at top, doesn't push content.
            bundleUpdateProgress?.let { progress ->
                val fraction = if (progress.total == 0) 0f else progress.completed.toFloat() / progress.total

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.bundle_update_banner_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.bundle_update_progress, progress.completed, progress.total),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Main centered content.
            MainContent(
                availablePatches = availablePatches,
                dashboardViewModel = dashboardViewModel,
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
                    isNavigating = true
                    scope.launch {
                        delay(100)
                        onAppSelected("com.google.android.youtube")
                    }
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
                    isNavigating = true
                    scope.launch {
                        delay(100)
                        onAppSelected("com.google.android.apps.youtube.music")
                    }
                },
                enabled = !isNavigating
            )

            // Loading overlay.
            if (isNavigating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) { },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainContent(
    availablePatches: Int,
    dashboardViewModel: DashboardViewModel,
    onAllAppsClick: () -> Unit,
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    enabled: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.custom_home_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.custom_home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // YouTube Button.
            AppButton(
                text = stringResource(R.string.custom_home_youtube),
                icon = { Icon(Icons.Filled.PlayArrow, "YouTube", tint = Color.White, modifier = Modifier.size(56.dp)) },
                backgroundColor = Color(0xFFFF0033),
                contentColor = Color.White,
                enabled = enabled,
                onClick = onYouTubeClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // YouTube Music Button.
            AppButton(
                text = stringResource(R.string.custom_home_youtube_music),
                icon = { Icon(Icons.Outlined.MusicNote, "YouTube Music", tint = Color.White, modifier = Modifier.size(56.dp)) },
                backgroundColor = Color(0xFF121212),
                contentColor = Color.White,
                gradientColors = listOf(Color(0xFFFF3E5A), Color(0xFFFF8C3E), Color(0xFFFFD23E)),
                enabled = enabled,
                onClick = onYouTubeMusicClick
            )
        }

        // Bottom "Other apps" button.
        TextButton(
            onClick = onAllAppsClick,
            enabled = enabled,
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
                .alpha(if (enabled) 1f else 0.6f)
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
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
