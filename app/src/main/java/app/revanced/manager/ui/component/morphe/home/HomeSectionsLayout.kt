package app.revanced.manager.ui.component.morphe.home

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.AppIcon
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.BundleUpdateStatus
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
fun HomeSectionsLayout(
    // Notifications section
    showBundleUpdateSnackbar: Boolean,
    snackbarStatus: BundleUpdateStatus,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    hasManagerUpdate: Boolean,
    onShowUpdateDetails: () -> Unit,

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

    // Main content sections
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Adaptive content layout based on window size
            HomeAdaptiveContent(
                windowSize = windowSize,
                greetingMessage = greetingMessage,
                onYouTubeClick = onYouTubeClick,
                onYouTubeMusicClick = onYouTubeMusicClick,
                onRedditClick = onRedditClick,
                youtubeInstalledApp = youtubeInstalledApp,
                youtubeMusicInstalledApp = youtubeMusicInstalledApp,
                redditInstalledApp = redditInstalledApp,
                youtubePackageInfo = youtubePackageInfo,
                youtubeMusicPackageInfo = youtubeMusicPackageInfo,
                redditPackageInfo = redditPackageInfo,
                onInstalledAppClick = onInstalledAppClick,
                installedAppsLoading = installedAppsLoading,
                onOtherAppsClick = onOtherAppsClick,
                showOtherAppsButton = showOtherAppsButton,
                modifier = Modifier.weight(1f)
            )

            // Section 5: Bottom action bar
            HomeBottomActionBar(
                onBundlesClick = onBundlesClick,
                onSettingsClick = onSettingsClick,
                isExpertModeEnabled = isExpertModeEnabled
            )
        }

        // Section 1: Notifications
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // Manager update notification
            if (hasManagerUpdate) {
                HomeManagerUpdateNotification(
                    onShowDetails = onShowUpdateDetails,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bundle update snackbar
            HomeBundleUpdateSnackbar(
                visible = showBundleUpdateSnackbar,
                status = snackbarStatus,
                progress = bundleUpdateProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Adaptive content layout that switches between portrait and landscape modes
 */
@Composable
private fun HomeAdaptiveContent(
    windowSize: WindowSize,
    greetingMessage: String,
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    onRedditClick: () -> Unit,
    youtubeInstalledApp: InstalledApp?,
    youtubeMusicInstalledApp: InstalledApp?,
    redditInstalledApp: InstalledApp?,
    youtubePackageInfo: PackageInfo?,
    youtubeMusicPackageInfo: PackageInfo?,
    redditPackageInfo: PackageInfo?,
    onInstalledAppClick: (InstalledApp) -> Unit,
    installedAppsLoading: Boolean,
    onOtherAppsClick: () -> Unit,
    showOtherAppsButton: Boolean = true,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    val contentPadding = windowSize.contentPadding
    val itemSpacing = windowSize.itemSpacing

    // Use two-column layout for medium/expanded windows (landscape)
    if (windowSize.useTwoColumnLayout) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing * 2)
        ) {
            // Left column: Greeting
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                HomeGreetingSection(
                    message = greetingMessage,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right column: App buttons + Other apps
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = contentPadding),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                // App buttons section
                HomeMainAppsSection(
                    onYouTubeClick = onYouTubeClick,
                    onYouTubeMusicClick = onYouTubeMusicClick,
                    onRedditClick = onRedditClick,
                    youtubeInstalledApp = youtubeInstalledApp,
                    youtubeMusicInstalledApp = youtubeMusicInstalledApp,
                    redditInstalledApp = redditInstalledApp,
                    youtubePackageInfo = youtubePackageInfo,
                    youtubeMusicPackageInfo = youtubeMusicPackageInfo,
                    redditPackageInfo = redditPackageInfo,
                    onInstalledAppClick = onInstalledAppClick,
                    installedAppsLoading = installedAppsLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                // Other apps button (hidden in simple mode with single bundle)
                if (showOtherAppsButton) {
                    HomeOtherAppsSection(
                        onClick = onOtherAppsClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    } else {
        // Single-column layout for compact windows (portrait)
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = contentPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            // Section 2: Greeting message
            HomeGreetingSection(
                message = greetingMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )

            // Section 3: Main app buttons
            HomeMainAppsSection(
                onYouTubeClick = onYouTubeClick,
                onYouTubeMusicClick = onYouTubeMusicClick,
                onRedditClick = onRedditClick,
                youtubeInstalledApp = youtubeInstalledApp,
                youtubeMusicInstalledApp = youtubeMusicInstalledApp,
                redditInstalledApp = redditInstalledApp,
                youtubePackageInfo = youtubePackageInfo,
                youtubeMusicPackageInfo = youtubeMusicPackageInfo,
                redditPackageInfo = redditPackageInfo,
                onInstalledAppClick = onInstalledAppClick,
                installedAppsLoading = installedAppsLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Section 4: Other apps button (hidden in simple mode with single bundle)
            if (showOtherAppsButton) {
                HomeOtherAppsSection(
                    onClick = onOtherAppsClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Section 1: Manager update notification
 */
@Composable
fun HomeManagerUpdateNotification(
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(vertical = 8.dp, horizontal = 16.dp),
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
                    text = stringResource(R.string.morphe_home_update_available),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.morphe_home_update_available_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Bundle update snackbar with animations
 */
@Composable
fun HomeBundleUpdateSnackbar(
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
    val fraction = if (progress?.total == 0 || progress == null) {
        0f
    } else {
        progress.completed.toFloat() / progress.total
    }

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
            .padding(vertical = 8.dp, horizontal = 16.dp),
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
                            BundleUpdateStatus.Updating -> stringResource(R.string.morphe_home_updating_sources)
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
                                        R.string.morphe_home_update_progress,
                                        progress.completed,
                                        progress.total
                                    )
                                } else {
                                    stringResource(R.string.morphe_home_please_wait)
                                }
                            }

                            BundleUpdateStatus.Success -> stringResource(R.string.morphe_home_update_success_subtitle)
                            BundleUpdateStatus.Error -> stringResource(R.string.morphe_home_update_error_subtitle)
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
}

/**
 * Section 2: Greeting message
 */
@Composable
fun HomeGreetingSection(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(top = 64.dp)
            .height(128.dp),
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
fun HomeMainAppsSection(
    onYouTubeClick: () -> Unit,
    onYouTubeMusicClick: () -> Unit,
    onRedditClick: () -> Unit,
    youtubeInstalledApp: InstalledApp?,
    youtubeMusicInstalledApp: InstalledApp?,
    redditInstalledApp: InstalledApp?,
    youtubePackageInfo: PackageInfo?,
    youtubeMusicPackageInfo: PackageInfo?,
    redditPackageInfo: PackageInfo?,
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

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        // App buttons
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // YouTube
            AppCardWithLoading(
                isLoading = stableLoadingState,
                installedApp = youtubeInstalledApp,
                packageInfo = youtubePackageInfo,
                gradientColors = listOf(
                    Color(0xFFFF0033),
                    Color(0xFF1E5AA8),
                    Color(0xFF00AFAE)
                ),
                buttonText = stringResource(R.string.morphe_home_youtube),
                onInstalledAppClick = onInstalledAppClick,
                onButtonClick = onYouTubeClick
            )

            // YouTube Music
            AppCardWithLoading(
                isLoading = stableLoadingState,
                installedApp = youtubeMusicInstalledApp,
                packageInfo = youtubeMusicPackageInfo,
                gradientColors = listOf(
                    Color(0xFFFF8C3E),
                    Color(0xFF1E5AA8),
                    Color(0xFF00AFAE)
                ),
                buttonText = stringResource(R.string.morphe_home_youtube_music),
                onInstalledAppClick = onInstalledAppClick,
                onButtonClick = onYouTubeMusicClick
            )

            // Reddit
            AppCardWithLoading(
                isLoading = stableLoadingState,
                installedApp = redditInstalledApp,
                packageInfo = redditPackageInfo,
                gradientColors = listOf(
                    Color(0xFFFF4500),
                    Color(0xFF1E5AA8),
                    Color(0xFF00AFAE)
                ),
                buttonText = stringResource(R.string.morphe_home_reddit),
                onInstalledAppClick = onInstalledAppClick,
                onButtonClick = onRedditClick
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
    onButtonClick: () -> Unit
) {
    Crossfade(
        targetState = isLoading,
        animationSpec = tween(300),
        label = "app_card_crossfade"
    ) { loading ->
        if (loading) {
            HomeAppLoadingCard(gradientColors = gradientColors)
        } else {
            if (installedApp != null) {
                HomeInstalledAppCard(
                    installedApp = installedApp,
                    packageInfo = packageInfo,
                    gradientColors = gradientColors,
                    onClick = { onInstalledAppClick(installedApp) }
                )
            } else {
                HomeAppButton(
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
fun HomeInstalledAppCard(
    installedApp: InstalledApp,
    packageInfo: PackageInfo?,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = Color.White

    AppCardLayout(
        gradientColors = gradientColors,
        enabled = true,
        onClick = onClick,
        modifier = modifier
    ) {
        // App icon
        AppIcon(
            packageInfo = packageInfo,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )

        // App info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // App name
            Text(
                text = packageInfo?.applicationInfo?.loadLabel(
                    LocalContext.current.packageManager
                )?.toString() ?: installedApp.currentPackageName,
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

            // Show version
            packageInfo?.versionName?.let { version ->
                Text(
                    text = if (version.startsWith("v")) version else "v$version",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            offset = Offset(0f, 1f),
                            blurRadius = 2f
                        )
                    ),
                    color = textColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * App button with gradient background
 */
@Composable
fun HomeAppButton(
    text: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val textColor = Color.White
    val finalTextColor = if (enabled) textColor else textColor.copy(alpha = 0.5f)

    AppCardLayout(
        gradientColors = gradientColors,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
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
                text = stringResource(R.string.morphe_home_not_patched_yet),
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
fun HomeOtherAppsSection(
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
            .padding(top = 16.dp, bottom = 24.dp)
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
            text = stringResource(R.string.morphe_home_other_apps),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
