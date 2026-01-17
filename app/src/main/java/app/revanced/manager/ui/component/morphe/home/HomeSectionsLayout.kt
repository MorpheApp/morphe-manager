package app.revanced.manager.ui.component.morphe.home

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.component.morphe.shared.*

/**
 * Home screen layout with 5 sections and adaptive landscape support:
 * 1. Notifications section (messages/updates)
 * 2. Greeting message section
 * 3. App buttons section (YouTube, YouTube Music, Reddit)
 * 4. Other apps button (only in expert mode)
 * 5. Bottom action bar (Installed Apps, Bundles, Settings)
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

    // Other apps button
    showOtherAppsButton: Boolean = false,
    onOtherAppsClick: () -> Unit,

    // Bottom action bar
    onInstalledAppsClick: () -> Unit,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val windowSize = rememberWindowSize()

    // Main content sections
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Adaptive content layout based on window size
            HomeAdaptiveContent(
                windowSize = windowSize,
                greetingMessage = greetingMessage,
                onYouTubeClick = onYouTubeClick,
                onYouTubeMusicClick = onYouTubeMusicClick,
                onRedditClick = onRedditClick,
                showOtherAppsButton = showOtherAppsButton,
                onOtherAppsClick = onOtherAppsClick,
                modifier = Modifier.weight(1f)
            )

            // Section 5: Bottom action bar
            HomeBottomActionBar(
                onInstalledAppsClick = onInstalledAppsClick,
                onBundlesClick = onBundlesClick,
                onSettingsClick = onSettingsClick
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
    showOtherAppsButton: Boolean,
    onOtherAppsClick: () -> Unit,
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
                    modifier = Modifier.fillMaxWidth()
                )

                // Other apps button
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Section 4: Other apps button (only in Expert mode)
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
    modifier: Modifier = Modifier
) {
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
            // YouTube button
            HomeAppButton(
                text = stringResource(R.string.morphe_home_youtube),
                backgroundColor = Color(0xFFFF0033),
                contentColor = Color.White,
                gradientColors = listOf(
                    Color(0xFFFF0033), // YouTube red
                    Color(0xFF1E5AA8), // Brand blue
                    Color(0xFF00AFAE)  // Brand teal
                ),
                onClick = onYouTubeClick
            )

            // YouTube Music button
            HomeAppButton(
                text = stringResource(R.string.morphe_home_youtube_music),
                backgroundColor = Color(0xFFFF8C3E),
                contentColor = Color.White,
                gradientColors = listOf(
                    Color(0xFFFF8C3E), // Orange
                    Color(0xFF1E5AA8), // Brand blue
                    Color(0xFF00AFAE)  // Brand teal
                ),
                onClick = onYouTubeMusicClick
            )

            // Reddit button
            HomeAppButton(
                text = stringResource(R.string.morphe_home_reddit),
                backgroundColor = Color(0xFFFF4500),
                contentColor = Color.White,
                gradientColors = listOf(
                    Color(0xFFFF4500), // Reddit orange
                    Color(0xFF1E5AA8), // Brand blue
                    Color(0xFF00AFAE)  // Brand teal
                ),
                onClick = onRedditClick,
//                enabled = false
            )
        }
    }
}

/**
 * App button with gradient background and haptic feedback
 */
@Composable
fun HomeAppButton(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradientColors: List<Color>? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = gradientColors ?: listOf(backgroundColor, backgroundColor)
    val view = LocalView.current
    val isDarkMode = isSystemInDarkTheme()

    // Adaptive alpha values - higher opacity in light mode for better contrast
    val backgroundAlpha = if (enabled) {
        if (isDarkMode) 0.6f else 0.85f
    } else {
        0.3f // Much lower opacity for disabled state
    }

    val borderAlpha = if (enabled) {
        if (isDarkMode) 0.8f else 0.9f
    } else {
        0.4f
    }

    val finalContentColor = if (enabled) {
        contentColor
    } else {
        contentColor.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        // Main button
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
                        colors = colors.map { it.copy(alpha = borderAlpha) },
                        start = Offset(0f, 0f), // Top-left
                        end = Offset.Infinite   // Bottom-right
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
                            colors = colors.map { it.copy(alpha = backgroundAlpha) },
                            start = Offset(0f, 0f), // Top-left
                            end = Offset.Infinite   // Bottom-right
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        // Text shadow for better readability
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = if (isDarkMode) 0.3f else 0.5f),
                            offset = Offset(0f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    color = finalContentColor
                )
            }
        }
    }
}

/**
 * Section 4: Other apps button (Expert mode only)
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
