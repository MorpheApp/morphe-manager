package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val composableScope = rememberCoroutineScope()

    val availablePatches by dashboardViewModel.availablePatches.collectAsStateWithLifecycle(0)
    val showNewDownloaderPluginsNotification by dashboardViewModel.newDownloaderPluginsAvailable.collectAsStateWithLifecycle(false)
    val bundleUpdateProgress by dashboardViewModel.bundleUpdateProgress.collectAsStateWithLifecycle(null)

    var isNavigating by rememberSaveable { mutableStateOf(false) }
    var showAndroid11Dialog by rememberSaveable { mutableStateOf(false) }
    val installAppsPermissionLauncher = rememberLauncherForActivityResult(
        RequestInstallAppsContract
    ) { granted ->
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
            onContinue = {
                installAppsPermissionLauncher.launch(context.packageName)
            }
        )
    }

    var showBundlesSheet by remember { mutableStateOf(false) }
    if (showBundlesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBundlesSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            modifier = Modifier.fillMaxSize()
        ) {
            BundleListScreen(
                eventsFlow = dashboardViewModel.bundleListEventsFlow,
                setSelectedSourceCount = { },
                showOrderDialog = false,
                onDismissOrderDialog = { },
                onScrollStateChange = { }
            )
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { showBundlesSheet = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Outlined.Source, stringResource(R.string.custom_home_bundles))
                }

                SmallFloatingActionButton(
                    onClick = onSettingsClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MainContent(
                availablePatches = availablePatches,
                showNewDownloaderPluginsNotification = showNewDownloaderPluginsNotification,
                bundleUpdateProgress = bundleUpdateProgress,
                dashboardViewModel = dashboardViewModel,
                showBundlesSheet = showBundlesSheet,
                onShowBundlesSheet = { showBundlesSheet = it },
                onSettingsClick = onSettingsClick,
                onAllAppsClick = onAllAppsClick,
                onDownloaderPluginClick = onDownloaderPluginClick,
                onYouTubeClick = {
                    if (availablePatches < 1) {
                        context.toast(context.getString(R.string.no_patch_found))
                        composableScope.launch {
                            showBundlesSheet = true
                        }
                        return@MainContent
                    }
                    if (dashboardViewModel.android11BugActive) {
                        showAndroid11Dialog = true
                        return@MainContent
                    }
                    isNavigating = true
                    composableScope.launch {
                        delay(100)
                        onAppSelected("com.google.android.youtube")
                    }
                },
                onYouTubeMusicClick = {
                    if (availablePatches < 1) {
                        context.toast(context.getString(R.string.no_patch_found))
                        composableScope.launch {
                            showBundlesSheet = true
                        }
                        return@MainContent
                    }
                    if (dashboardViewModel.android11BugActive) {
                        showAndroid11Dialog = true
                        return@MainContent
                    }
                    isNavigating = true
                    composableScope.launch {
                        delay(100)
                        onAppSelected("com.google.android.apps.youtube.music")
                    }
                },
                enabled = !isNavigating
            )

            // Fullscreen loading indicator on top of everything.
            if (isNavigating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) { } // Blocking interaction.
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 4.dp
                                )
                                Text(
                                    text = stringResource(R.string.loading),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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
    showNewDownloaderPluginsNotification: Boolean,
    bundleUpdateProgress: BundleUpdateProgress?,
    dashboardViewModel: DashboardViewModel,
    showBundlesSheet: Boolean,
    onShowBundlesSheet: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onAllAppsClick: () -> Unit,
    onDownloaderPluginClick: () -> Unit,
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Notifications area - fixed at top.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Battery optimization warning.
            if (dashboardViewModel.showBatteryOptimizationsWarning) {
                val batteryOptimizationsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    dashboardViewModel.updateBatteryOptimizationsWarning()
                }
                NotificationCard(
                    isWarning = true,
                    icon = Icons.Default.BatteryAlert,
                    text = stringResource(R.string.battery_optimization_notification),
                    onClick = {
                        batteryOptimizationsLauncher.launch(
                            Intent(
                                AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                )
            }

            // New downloader plugins notification.
            if (showNewDownloaderPluginsNotification) {
                NotificationCard(
                    text = stringResource(R.string.new_downloader_plugins_notification),
                    icon = Icons.Outlined.Download,
                    modifier = Modifier.clickable(onClick = onDownloaderPluginClick),
                    actions = {
                        TextButton(onClick = dashboardViewModel::ignoreNewDownloaderPlugins) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                )
            }

            // Bundle update progress banner.
            bundleUpdateProgress?.let { progress ->
                val progressFraction = if (progress.total == 0) 0f
                else progress.completed.toFloat() / progress.total

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Main content - centered.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
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
            LargeAppButton(
                text = stringResource(R.string.custom_home_youtube),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "YouTube",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                },
                backgroundColor = Color(0xFFFF0033), // YouTube Red.
                contentColor = Color.White,
                enabled = enabled,
                onClick = onYouTubeClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // YouTube Music Button.
            LargeAppButton(
                text = stringResource(R.string.custom_home_youtube_music),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = "YouTube Music",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                },
                backgroundColor = Color(0xFF121212), // YT Music dark base.
                contentColor = Color.White,
                gradientColors = listOf(
                    Color(0xFFFF3E5A),
                    Color(0xFFFF8C3E),
                    Color(0xFFFFD23E)
                ),
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
                .padding(bottom = 32.dp)
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
private fun LargeAppButton(
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
                    if (gradientColors != null) {
                        Modifier.background(
                            brush = Brush.horizontalGradient(gradientColors),
                            shape = RoundedCornerShape(28.dp)
                        )
                    } else {
                        Modifier.background(backgroundColor, RoundedCornerShape(28.dp))
                    }
                )
                .alpha(if (enabled) 1f else 0.6f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = contentColor, strokeWidth = 3.dp)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp), // Consistent left padding
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon fixed on the left side
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }

                    Spacer(modifier = Modifier.width(20.dp))

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
