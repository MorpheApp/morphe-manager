package app.revanced.manager.ui.screen.home

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.screen.shared.*
import app.revanced.manager.ui.viewmodel.BundleUpdateStatus
import app.revanced.manager.util.AppPackages
import app.revanced.manager.util.formatMegabytes
import kotlinx.coroutines.delay

/**
 * Home screen layout with 5 sections and adaptive landscape support:
 * 1. Notifications section (messages/updates)
 * 2. Greeting message section
 * 3. App buttons section (YouTube, YouTube Music, Reddit)
 * 4. Other apps button
 * 5. Bottom action bar (Bundles, Settings)
 */
@Composable
fun SectionsLayout(
    // Notifications section
    showBundleUpdateSnackbar: Boolean,
    snackbarStatus: BundleUpdateStatus,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    hasManagerUpdate: Boolean,
    onShowUpdateDetails: () -> Unit,

    // App update indicators
    appUpdatesAvailable: Map<String, Boolean> = emptyMap(),

    // Greeting section
    greetingMessage: String,

    // App buttons section
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    onRedditClick: () -> Unit,

    // Installed apps data
    youtubeInstalledApp: InstalledApp? = null,
    youtubeMusicInstalledApp: InstalledApp? = null,
    redditInstalledApp: InstalledApp? = null,
    youtubePackageInfo: PackageInfo? = null,
    youtubeMusicPackageInfo: PackageInfo? = null,
    redditPackageInfo: PackageInfo? = null,
    youtubeIsDeleted: Boolean = false,
    youtubeMusicIsDeleted: Boolean = false,
    redditIsDeleted: Boolean = false,
    onInstalledAppClick: (InstalledApp) -> Unit,
    installedAppsLoading: Boolean = false,

    // Other apps button
    onOtherAppsClick: () -> Unit,
    showOtherAppsButton: Boolean = true,

    // Bottom action bar
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,

    // Expert mode
    isExpertModeEnabled: Boolean = false
) {
    val windowSize = rememberWindowSize()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main layout structure
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AdaptiveContent(
                    windowSize = windowSize,
                    greetingMessage = greetingMessage,
                    appUpdatesAvailable = appUpdatesAvailable,
                    onYouTubeClick = onYouTubeClick,
                    onYouTubeMusicClick = onYouTubeMusicClick,
                    onRedditClick = onRedditClick,
                    youtubeInstalledApp = youtubeInstalledApp,
                    youtubeMusicInstalledApp = youtubeMusicInstalledApp,
                    redditInstalledApp = redditInstalledApp,
                    youtubePackageInfo = youtubePackageInfo,
                    youtubeMusicPackageInfo = youtubeMusicPackageInfo,
                    redditPackageInfo = redditPackageInfo,
                    youtubeIsDeleted = youtubeIsDeleted,
                    youtubeMusicIsDeleted = youtubeMusicIsDeleted,
                    redditIsDeleted = redditIsDeleted,
                    onInstalledAppClick = onInstalledAppClick,
                    installedAppsLoading = installedAppsLoading,
                    onOtherAppsClick = onOtherAppsClick,
                    showOtherAppsButton = showOtherAppsButton
                )
            }

            // Section 5: Bottom action bar
            HomeBottomActionBar(
                onBundlesClick = onBundlesClick,
                onSettingsClick = onSettingsClick,
                isExpertModeEnabled = isExpertModeEnabled
            )
        }

        // Section 1: Notifications overlay
        NotificationsOverlay(
            hasManagerUpdate = hasManagerUpdate,
            onShowUpdateDetails = onShowUpdateDetails,
            showBundleUpdateSnackbar = showBundleUpdateSnackbar,
            snackbarStatus = snackbarStatus,
            bundleUpdateProgress = bundleUpdateProgress,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
    }
}

/**
 * Adaptive content layout that switches between portrait and landscape modes
 */
@Composable
private fun AdaptiveContent(
    windowSize: WindowSize,
    greetingMessage: String,
    appUpdatesAvailable: Map<String, Boolean> = emptyMap(),
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    onRedditClick: () -> Unit,
    youtubeInstalledApp: InstalledApp?,
    youtubeMusicInstalledApp: InstalledApp?,
    redditInstalledApp: InstalledApp?,
    youtubePackageInfo: PackageInfo?,
    youtubeMusicPackageInfo: PackageInfo?,
    redditPackageInfo: PackageInfo?,
    youtubeIsDeleted: Boolean,
    youtubeMusicIsDeleted: Boolean,
    redditIsDeleted: Boolean,
    onInstalledAppClick: (InstalledApp) -> Unit,
    installedAppsLoading: Boolean,
    onOtherAppsClick: () -> Unit,
    showOtherAppsButton: Boolean = true
) {
    val contentPadding = windowSize.contentPadding
    val itemSpacing = windowSize.itemSpacing
    val useTwoColumns = windowSize.useTwoColumnLayout
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = contentPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        if (useTwoColumns) {
            // Two-column layout for medium/expanded windows (landscape)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing * 2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Greeting
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GreetingSection(
                        message = greetingMessage,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Right column: App buttons + Other apps
                Column(
                    modifier = Modifier.weight(0.5f),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    MainAppsSection(
                        itemSpacing = itemSpacing,
                        appUpdatesAvailable = appUpdatesAvailable,
                        onYouTubeClick = onYouTubeClick,
                        onYouTubeMusicClick = onYouTubeMusicClick,
                        onRedditClick = onRedditClick,
                        youtubeInstalledApp = youtubeInstalledApp,
                        youtubeMusicInstalledApp = youtubeMusicInstalledApp,
                        redditInstalledApp = redditInstalledApp,
                        youtubePackageInfo = youtubePackageInfo,
                        youtubeMusicPackageInfo = youtubeMusicPackageInfo,
                        redditPackageInfo = redditPackageInfo,
                        youtubeIsDeleted = youtubeIsDeleted,
                        youtubeMusicIsDeleted = youtubeMusicIsDeleted,
                        redditIsDeleted = redditIsDeleted,
                        onInstalledAppClick = onInstalledAppClick,
                        installedAppsLoading = installedAppsLoading
                    )

                    // Section 4: Other apps
                    if (!showOtherAppsButton) {
                        Spacer(modifier = Modifier.height(48.dp + itemSpacing))
                    } else {
                        OtherAppsSection(
                            onClick = onOtherAppsClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            // Single-column layout for compact windows (portrait)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                // Section 2: Greeting
                GreetingSection(
                    message = greetingMessage,
                    modifier = Modifier.fillMaxWidth()
                )

                // Section 3: App buttons
                MainAppsSection(
                    itemSpacing = itemSpacing,
                    appUpdatesAvailable = appUpdatesAvailable,
                    onYouTubeClick = onYouTubeClick,
                    onYouTubeMusicClick = onYouTubeMusicClick,
                    onRedditClick = onRedditClick,
                    youtubeInstalledApp = youtubeInstalledApp,
                    youtubeMusicInstalledApp = youtubeMusicInstalledApp,
                    redditInstalledApp = redditInstalledApp,
                    youtubePackageInfo = youtubePackageInfo,
                    youtubeMusicPackageInfo = youtubeMusicPackageInfo,
                    redditPackageInfo = redditPackageInfo,
                    youtubeIsDeleted = youtubeIsDeleted,
                    youtubeMusicIsDeleted = youtubeMusicIsDeleted,
                    redditIsDeleted = redditIsDeleted,
                    onInstalledAppClick = onInstalledAppClick,
                    installedAppsLoading = installedAppsLoading
                )

                // Section 4: Other apps
                if (!showOtherAppsButton) {
                    Spacer(modifier = Modifier.height(48.dp + itemSpacing))
                } else {
                    OtherAppsSection(
                        onClick = onOtherAppsClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Section 1: Unified notifications overlay component
 * Handles both manager update and bundle update notifications
 */
@Composable
fun NotificationsOverlay(
    hasManagerUpdate: Boolean,
    onShowUpdateDetails: () -> Unit,
    showBundleUpdateSnackbar: Boolean,
    snackbarStatus: BundleUpdateStatus,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    modifier: Modifier = Modifier
) {
    val windowSize = rememberWindowSize()
    val useTwoColumns = windowSize.useTwoColumnLayout

    Box(
        modifier = modifier,
        contentAlignment = if (useTwoColumns) Alignment.TopStart else Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (useTwoColumns) {
                        Modifier.fillMaxWidth(0.5f) // 50% width in landscape
                    } else {
                        Modifier.fillMaxWidth() // Full width in portrait
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Manager update snackbar
            ManagerUpdateSnackbar(
                visible = hasManagerUpdate,
                onShowDetails = onShowUpdateDetails,
                modifier = Modifier.fillMaxWidth()
            )

            // Bundle update snackbar
            BundleUpdateSnackbar(
                visible = showBundleUpdateSnackbar,
                status = snackbarStatus,
                progress = bundleUpdateProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Manager update snackbar
 */
@Composable
fun ManagerUpdateSnackbar(
    visible: Boolean,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 500)
        ) + fadeIn(animationSpec = tween(durationMillis = 500)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 500)
        ) + fadeOut(animationSpec = tween(durationMillis = 500)),
        modifier = modifier
    ) {
        Card(
            modifier = modifier.padding(horizontal = 16.dp),
            onClick = onShowDetails,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                Icon(
                    imageVector = Icons.Outlined.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_update_available),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.home_update_available_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Bundle update snackbar
 */
@Composable
fun BundleUpdateSnackbar(
    visible: Boolean,
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 500)
        ) + fadeIn(animationSpec = tween(durationMillis = 500)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 500)
        ) + fadeOut(animationSpec = tween(durationMillis = 500)),
        modifier = modifier
    ) {
        BundleUpdateSnackbarContent(
            status = status,
            progress = progress
        )
    }
}

/**
 * Snackbar content with status indicator
 */
@Composable
private fun BundleUpdateSnackbarContent(
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?
) {
    // Calculate bundle processing progress
    val fraction = if (progress?.total == 0 || progress == null) {
        0f
    } else {
        progress.completed.toFloat() / progress.total
    }

    // Calculate download progress
    val downloadFraction = if (progress?.bytesTotal == null || progress.bytesTotal == 0L) {
        0f
    } else {
        progress.bytesRead.toFloat() / progress.bytesTotal.toFloat()
    }

    // Determine which progress to show
    val isDownloading = progress?.phase == PatchBundleRepository.BundleUpdatePhase.Downloading &&
            progress.bytesTotal != null &&
            progress.bytesTotal > 0L
    val displayProgress = if (isDownloading) downloadFraction else fraction

    val containerColor = when (status) {
        BundleUpdateStatus.Success -> MaterialTheme.colorScheme.primaryContainer
        BundleUpdateStatus.Error -> MaterialTheme.colorScheme.errorContainer
        BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (status) {
        BundleUpdateStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        BundleUpdateStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
        BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
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
                            progress = { displayProgress },
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
                            BundleUpdateStatus.Updating -> {
                                // Show bundle name if available and downloading
                                if (isDownloading && progress.currentBundleName != null) {
                                    progress.currentBundleName
                                } else {
                                    stringResource(R.string.home_updating_sources)
                                }
                            }
                            BundleUpdateStatus.Success -> stringResource(R.string.home_update_success)
                            BundleUpdateStatus.Error -> stringResource(R.string.home_update_error)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (status) {
                            BundleUpdateStatus.Updating -> {
                                if (progress != null) {
                                    when {
                                        // Show download progress in MB
                                        isDownloading -> {
                                            stringResource(
                                                R.string.home_update_download_progress,
                                                formatMegabytes(progress.bytesRead),
                                                formatMegabytes(progress.bytesTotal),
                                                (downloadFraction * 100).toInt()
                                            )
                                        }
                                        // Show source processing progress
                                        progress.total > 0 -> {
                                            stringResource(
                                                R.string.home_update_progress,
                                                progress.completed,
                                                progress.total
                                            )
                                        }
                                        // Loading state
                                        else -> {
                                            stringResource(R.string.home_please_wait)
                                        }
                                    }
                                } else {
                                    stringResource(R.string.home_please_wait)
                                }
                            }

                            BundleUpdateStatus.Success -> stringResource(R.string.home_update_success_subtitle)
                            BundleUpdateStatus.Error -> stringResource(R.string.home_update_error_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            // Progress bar only for updating status
            if (status == BundleUpdateStatus.Updating) {
                LinearProgressIndicator(
                    progress = { displayProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

/**
 * Section 2: Greeting message
 */
@Composable
fun GreetingSection(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Section 3: Main app buttons
 */
@Composable
fun MainAppsSection(
    itemSpacing: Dp = 16.dp,
    appUpdatesAvailable: Map<String, Boolean> = emptyMap(),
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    onRedditClick: () -> Unit,
    youtubeInstalledApp: InstalledApp?,
    youtubeMusicInstalledApp: InstalledApp?,
    redditInstalledApp: InstalledApp?,
    youtubePackageInfo: PackageInfo?,
    youtubeMusicPackageInfo: PackageInfo?,
    redditPackageInfo: PackageInfo?,
    youtubeIsDeleted: Boolean = false,
    youtubeMusicIsDeleted: Boolean = false,
    redditIsDeleted: Boolean = false,
    onInstalledAppClick: (InstalledApp) -> Unit,
    installedAppsLoading: Boolean = false,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    // Stable loading state with debounce to prevent flickering
    var stableLoadingState by remember { mutableStateOf(installedAppsLoading) }

    LaunchedEffect(installedAppsLoading) {
        if (installedAppsLoading) {
            // Immediately show loading
            stableLoadingState = true
        } else {
            // Add delay before hiding loading to ensure data is ready
            delay(300)
            stableLoadingState = false
        }
    }

    // App buttons
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        Column(
            modifier = Modifier.widthIn(max = 500.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            // YouTube
            AppCardWithLoading(
                isLoading = stableLoadingState,
                installedApp = youtubeInstalledApp,
                packageInfo = youtubePackageInfo,
                gradientColors = AppPackages.YOUTUBE_COLORS,
                buttonText = stringResource(R.string.home_youtube),
                onInstalledAppClick = onInstalledAppClick,
                onButtonClick = onYouTubeClick,
                hasUpdate = youtubeInstalledApp?.let {
                    appUpdatesAvailable[it.currentPackageName] == true
                } == true,
                isAppDeleted = youtubeIsDeleted
            )

            // YouTube Music
            AppCardWithLoading(
                isLoading = stableLoadingState,
                installedApp = youtubeMusicInstalledApp,
                packageInfo = youtubeMusicPackageInfo,
                gradientColors = AppPackages.YOUTUBE_MUSIC_COLORS,
                buttonText = stringResource(R.string.home_youtube_music),
                onInstalledAppClick = onInstalledAppClick,
                onButtonClick = onYouTubeMusicClick,
                hasUpdate = youtubeMusicInstalledApp?.let {
                    appUpdatesAvailable[it.currentPackageName] == true
                } == true,
                isAppDeleted = youtubeMusicIsDeleted
            )

            // Reddit
            AppCardWithLoading(
                isLoading = stableLoadingState,
                installedApp = redditInstalledApp,
                packageInfo = redditPackageInfo,
                gradientColors = AppPackages.REDDIT_COLORS,
                buttonText = stringResource(R.string.home_reddit),
                onInstalledAppClick = onInstalledAppClick,
                onButtonClick = onRedditClick,
                hasUpdate = redditInstalledApp?.let {
                    appUpdatesAvailable[it.currentPackageName] == true
                } == true,
                isAppDeleted = redditIsDeleted
            )
        }
    }
}

/**
 * App card component with loading state
 */
@Composable
private fun AppCardWithLoading(
    isLoading: Boolean,
    installedApp: InstalledApp?,
    packageInfo: PackageInfo?,
    gradientColors: List<Color>,
    buttonText: String,
    onInstalledAppClick: (InstalledApp) -> Unit,
    onButtonClick: () -> Unit,
    hasUpdate: Boolean = false,
    isAppDeleted: Boolean = false
) {
    Crossfade(
        targetState = isLoading,
        animationSpec = tween(300),
        label = "app_card_crossfade"
    ) { loading ->
        if (loading) {
            AppLoadingCard(gradientColors = gradientColors)
        } else {
            if (installedApp != null) {
                InstalledAppCard(
                    installedApp = installedApp,
                    packageInfo = packageInfo,
                    gradientColors = gradientColors,
                    onClick = { onInstalledAppClick(installedApp) },
                    hasUpdate = hasUpdate,
                    isAppDeleted = isAppDeleted
                )
            } else {
                AppButton(
                    text = buttonText,
                    gradientColors = gradientColors,
                    onClick = onButtonClick
                )
            }
        }
    }
}

/**
 * Shared content layout for app cards and buttons
 */
@Composable
private fun AppCardLayout(
    gradientColors: List<Color>,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val view = LocalView.current

    val backgroundAlpha = if (enabled) 0.7f else 0.3f
    val borderAlpha = if (enabled) 0.85f else 0.4f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        Surface(
            onClick = {
                if (enabled) {
                    // Trigger haptic feedback on click
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = gradientColors.map { it.copy(alpha = borderAlpha) },
                        start = Offset(0f, 0f),
                        end = Offset.Infinite
                    ),
                    shape = shape
                ),
            shape = shape,
            color = Color.Transparent,
            enabled = enabled
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = gradientColors.map { it.copy(alpha = backgroundAlpha) },
                            start = Offset(0f, 0f),
                            end = Offset.Infinite
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

/**
 * Installed app card with gradient background
 */
@Composable
fun InstalledAppCard(
    installedApp: InstalledApp,
    packageInfo: PackageInfo?,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasUpdate: Boolean = false,
    isAppDeleted: Boolean = false
) {
    val context = LocalContext.current
    val textColor = Color.White

    val versionLabel = stringResource(R.string.version)
    val installedLabel = stringResource(R.string.installed)
    val updateAvailableLabel = stringResource(R.string.update_available)
    val deletedLabel = stringResource(R.string.uninstalled)

    // Delayed visibility for badge to prevent showing during skeleton loading
    var showBadge by remember { mutableStateOf(false) }

    LaunchedEffect(hasUpdate) {
        if (hasUpdate) {
            delay(500) // Wait for skeleton animation to finish
            showBadge = true
        } else {
            showBadge = false
        }
    }

    // Build content description for accessibility
    val appName = remember(packageInfo, installedApp) {
        packageInfo?.applicationInfo?.loadLabel(context.packageManager)?.toString()
            ?: AppPackages.getAppName(context, installedApp.originalPackageName)
    }

    val version = remember(packageInfo, installedApp, isAppDeleted) {
        when {
            packageInfo != null -> {
                packageInfo.versionName?.let {
                    if (it.startsWith("v")) it else "v$it"
                } ?: installedApp.version.let { "v$it" }
            }
            isAppDeleted -> {
                val savedVersion = installedApp.version.let {
                    if (it.startsWith("v")) it else "v$it"
                }
                savedVersion
            }
            else -> installedApp.version.let {
                if (it.startsWith("v")) it else "v$it"
            }
        }
    }

    val contentDesc = remember(appName, version, versionLabel, installedLabel, hasUpdate, updateAvailableLabel, isAppDeleted, deletedLabel) {
        buildString {
            append(appName)
            if (version.isNotEmpty()) {
                append(", ")
                append(versionLabel)
                append(" ")
                append(version)
            }
            append(", ")
            if (isAppDeleted) {
                append(deletedLabel)
            } else {
                append(installedLabel)
            }
            if (hasUpdate && !isAppDeleted) {
                append(", ")
                append(updateAvailableLabel)
            }
        }
    }

    AppCardLayout(
        gradientColors = gradientColors,
        enabled = true,
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            this.contentDescription = contentDesc
        }
    ) {
        // App icon
        AppIcon(
            packageInfo = packageInfo,
            contentDescription = null, // Handled by card semantics
            modifier = Modifier.size(48.dp)
        )

        // App info
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // App name
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            offset = Offset(0f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    color = textColor
                )

                // Show version or deletion warning
                if (version.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = version,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            color = textColor.copy(alpha = 0.85f)
                        )

                        if (isAppDeleted) {
                            Text(
                                text = "• ${stringResource(R.string.uninstalled)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.4f),
                                        offset = Offset(0f, 1f),
                                        blurRadius = 2f
                                    )
                                ),
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Update badge
            androidx.compose.animation.AnimatedVisibility(
                visible = showBadge && !isAppDeleted,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(animationSpec = tween(durationMillis = 400)) +
                        scaleIn(initialScale = 0.8f, animationSpec = tween(durationMillis = 400)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300)) +
                        scaleOut(targetScale = 0.8f, animationSpec = tween(durationMillis = 300))
            ) {
                UpdateBadge()
            }
        }
    }
}

/**
 * Update badge for app cards
 */
@Composable
private fun UpdateBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Update,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.update),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * App button with gradient background
 */
@Composable
fun AppButton(
    text: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val textColor = Color.White
    val finalTextColor = if (enabled) textColor else textColor.copy(alpha = 0.5f)

    val notPatchedText = stringResource(R.string.home_not_patched_yet)
    val disabledText = stringResource(R.string.disabled)

    // Build content description for accessibility
    val contentDesc = remember(text, notPatchedText, disabledText, enabled) {
        buildString {
            append(text)
            append(", ")
            append(notPatchedText)
            if (!enabled) {
                append(", ")
                append(disabledText)
            }
        }
    }

    AppCardLayout(
        gradientColors = gradientColors,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            this.contentDescription = contentDesc
            if (!enabled) {
                stateDescription = disabledText
            }
        }
    ) {
        // Icon placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(finalTextColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(finalTextColor.copy(alpha = 0.4f))
            )
        }

        // Text info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // App name
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.4f),
                        offset = Offset(0f, 2f),
                        blurRadius = 4f
                    )
                ),
                color = finalTextColor
            )

            // Status text
            Text(
                text = notPatchedText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.4f),
                        offset = Offset(0f, 1f),
                        blurRadius = 2f
                    )
                ),
                color = finalTextColor.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Section 4: Other apps button
 */
@Composable
fun OtherAppsSection(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(20.dp)
    val isDark = isSystemInDarkTheme()

    val backgroundAlpha = if (isDark) 0.35f else 0.6f
    val borderAlpha = if (isDark) 0.4f else 0.6f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = backgroundAlpha)
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)
                ),
                shape = shape
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.home_other_apps),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
