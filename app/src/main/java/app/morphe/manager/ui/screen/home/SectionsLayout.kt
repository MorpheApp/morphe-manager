/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BundleUpdateStatus
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.AppPackages
import kotlinx.coroutines.delay

/**
 * Home screen layout with dynamic app buttons:
 * 1. Notifications section
 * 2. Greeting message section
 * 3. Dynamic app buttons
 * 4. Other apps button
 * 5. Bottom action bar
 */
@Composable
fun SectionsLayout(
    // Notifications section
    showBundleUpdateSnackbar: Boolean,
    snackbarStatus: BundleUpdateStatus,
    bundleUpdateProgress: PatchBundleRepository.BundleUpdateProgress?,
    hasManagerUpdate: Boolean,
    onShowUpdateDetails: () -> Unit,

    // Greeting section
    greetingMessage: String,

    // Dynamic app items
    homeAppItems: List<HomeAppItem>,
    onAppClick: (HomeAppItem) -> Unit,
    onInstalledAppClick: (InstalledApp) -> Unit,
    onHideApp: (String) -> Unit,
    onUnhideApp: (String) -> Unit,
    hiddenAppItems: List<HomeAppItem> = emptyList(),
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
                    homeAppItems = homeAppItems,
                    onAppClick = onAppClick,
                    onInstalledAppClick = onInstalledAppClick,
                    onHideApp = onHideApp,
                    onUnhideApp = onUnhideApp,
                    hiddenAppItems = hiddenAppItems,
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
 * Adaptive content layout that switches between portrait and landscape modes.
 */
@Composable
private fun AdaptiveContent(
    windowSize: WindowSize,
    greetingMessage: String,
    homeAppItems: List<HomeAppItem>,
    onAppClick: (HomeAppItem) -> Unit,
    onInstalledAppClick: (InstalledApp) -> Unit,
    onHideApp: (String) -> Unit,
    onUnhideApp: (String) -> Unit,
    hiddenAppItems: List<HomeAppItem> = emptyList(),
    installedAppsLoading: Boolean,
    onOtherAppsClick: () -> Unit,
    showOtherAppsButton: Boolean = true
) {
    val contentPadding = windowSize.contentPadding
    val itemSpacing = windowSize.itemSpacing
    val useTwoColumns = windowSize.useTwoColumnLayout

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = contentPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        if (useTwoColumns) {
            // Two-column layout for medium/expanded windows (landscape)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                    GreetingSection(message = greetingMessage)
                }

                // Right column: App buttons + Other apps
                Column(
                    modifier = Modifier.weight(0.5f),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    MainAppsSection(
                        homeAppItems = homeAppItems,
                        itemSpacing = itemSpacing,
                        onAppClick = onAppClick,
                        onInstalledAppClick = onInstalledAppClick,
                        onHideApp = onHideApp,
                        onUnhideApp = onUnhideApp,
                        hiddenAppItems = hiddenAppItems,
                        installedAppsLoading = installedAppsLoading,
                        modifier = Modifier.weight(1f)
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
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                // Section 2: Greeting
                GreetingSection(message = greetingMessage)

                Spacer(modifier = Modifier.height(itemSpacing))

                // Section 3: Scrollable app buttons
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    MainAppsSection(
                        homeAppItems = homeAppItems,
                        itemSpacing = itemSpacing,
                        onAppClick = onAppClick,
                        onInstalledAppClick = onInstalledAppClick,
                        onHideApp = onHideApp,
                        onUnhideApp = onUnhideApp,
                        hiddenAppItems = hiddenAppItems,
                        installedAppsLoading = installedAppsLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(itemSpacing))

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
 * Section 1: Unified notifications overlay component.
 * Handles both manager update and bundle update notifications.
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
 * Manager update snackbar.
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
 * Bundle update snackbar.
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
 * Snackbar content with status indicator.
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
                    BundleUpdateStatus.Success -> Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    BundleUpdateStatus.Error -> Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    BundleUpdateStatus.Updating -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = contentColor
                    )
                }

                // Text content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (status) {
                            BundleUpdateStatus.Success -> stringResource(R.string.home_update_success)
                            BundleUpdateStatus.Error -> stringResource(R.string.home_update_error)
                            BundleUpdateStatus.Updating -> stringResource(R.string.home_updating_sources)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )

                    if (status == BundleUpdateStatus.Updating && progress != null) {
                        if (isDownloading) {
                            val readMb = progress.bytesRead.toFloat() / (1024 * 1024)
                            val totalMb = progress.bytesTotal.toFloat() / (1024 * 1024)
                            val percent = (downloadFraction * 100).toInt()
                            Text(
                                text = stringResource(
                                    R.string.home_update_download_progress,
                                    readMb, totalMb, percent.toString()
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        } else if (progress.currentBundleName != null) {
                            Text(
                                text = progress.currentBundleName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (status == BundleUpdateStatus.Success) {
                        Text(
                            text = stringResource(R.string.home_update_success_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                    if (status == BundleUpdateStatus.Error) {
                        Text(
                            text = stringResource(R.string.home_update_error_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Progress bar for updating state
            if (status == BundleUpdateStatus.Updating && displayProgress > 0f) {
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
 * Section 2: Greeting message.
 */
@Composable
fun GreetingSection(
    message: String
) {
    Box(contentAlignment = Alignment.Center) {
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
 * Section 3: Dynamic scrollable app buttons list.
 */
@Composable
fun MainAppsSection(
    homeAppItems: List<HomeAppItem>,
    itemSpacing: Dp = 16.dp,
    onAppClick: (HomeAppItem) -> Unit,
    onInstalledAppClick: (InstalledApp) -> Unit,
    onHideApp: (String) -> Unit,
    onUnhideApp: (String) -> Unit,
    hiddenAppItems: List<HomeAppItem> = emptyList(),
    installedAppsLoading: Boolean = false,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    // Track if data was ever loaded, never show shimmer again on resume
    var hasEverLoaded by remember { mutableStateOf(homeAppItems.isNotEmpty()) }

    // Stable loading state with debounce to prevent flickering.
    // Only shows shimmer on genuine cold start (data never arrived).
    var stableLoadingState by remember { mutableStateOf(!hasEverLoaded) }

    LaunchedEffect(installedAppsLoading, homeAppItems.isEmpty()) {
        if (homeAppItems.isNotEmpty()) {
            hasEverLoaded = true
        }
        // Once hasEverLoaded is true, never re-trigger shimmer regardless of list state
        val shouldLoad = !hasEverLoaded || installedAppsLoading
        if (shouldLoad) {
            stableLoadingState = true
        } else {
            delay(300)
            stableLoadingState = false
        }
    }

    // Placeholder gradients for cold-start shimmer
    val placeholderGradients = remember { AppPackages.DEFAULT_SHIMMER_GRADIENTS }

    // Hidden apps dialog state
    var showHiddenAppsDialog by remember { mutableStateOf(false) }

    if (showHiddenAppsDialog) {
        HiddenAppsDialog(
            hiddenAppItems = hiddenAppItems,
            onUnhide = onUnhideApp,
            onDismiss = { showHiddenAppsDialog = false }
        )
    }

    val listState = rememberLazyListState()
    val fadeSize = 24.dp

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 500.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadePx = fadeSize.toPx()
                    val canScrollUp = listState.firstVisibleItemIndex > 0 ||
                            listState.firstVisibleItemScrollOffset > 0
                    if (canScrollUp) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadePx
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    val canScrollDown = listState.canScrollForward
                    if (canScrollDown) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - fadePx,
                                endY = size.height
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Cold start: homeAppItems still empty - show placeholder shimmer cards
            if (stableLoadingState && homeAppItems.isEmpty()) {
                items(3, key = { "placeholder_$it" }) { index ->
                    AppLoadingCard(
                        gradientColors = placeholderGradients[index % placeholderGradients.size],
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                items(
                    items = homeAppItems,
                    key = { it.packageName }
                ) { item ->
                    DynamicAppCard(
                        item = item,
                        isLoading = stableLoadingState,
                        hasUpdate = item.hasUpdate,
                        onAppClick = { onAppClick(item) },
                        onInstalledAppClick = onInstalledAppClick,
                        onHide = { onHideApp(item.packageName) },
                        modifier = Modifier.animateItem()
                    )
                }

                // "Show hidden apps" button if there are hidden apps
                if (hiddenAppItems.isNotEmpty()) {
                    item(key = "show_hidden") {
                        TextButton(
                            onClick = { showHiddenAppsDialog = true },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.home_app_show_hidden),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single dynamic app card with long-press action.
 */
@Composable
private fun DynamicAppCard(
    item: HomeAppItem,
    isLoading: Boolean,
    hasUpdate: Boolean,
    onAppClick: () -> Unit,
    onInstalledAppClick: (InstalledApp) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Crossfade(
            targetState = isLoading,
            animationSpec = tween(300),
            label = "app_card_crossfade_${item.packageName}"
        ) { loading ->
            if (loading) {
                AppLoadingCard(gradientColors = item.gradientColors)
            } else {
                if (item.installedApp != null) {
                    InstalledAppCard(
                        installedApp = item.installedApp,
                        packageInfo = item.packageInfo,
                        displayName = item.displayName,
                        gradientColors = item.gradientColors,
                        onClick = { onInstalledAppClick(item.installedApp) },
                        hasUpdate = hasUpdate,
                        isAppDeleted = item.isDeleted,
                        onLongClick = { showContextMenu = true }
                    )
                } else {
                    AppButton(
                        packageName = item.packageName,
                        displayName = item.displayName,
                        packageInfo = item.packageInfo,
                        gradientColors = item.gradientColors,
                        onClick = onAppClick,
                        onLongClick = { showContextMenu = true }
                    )
                }
            }
        }

        // Hide confirmation dialog
        if (showContextMenu) {
            HideAppDialog(
                item = item,
                onDismiss = { showContextMenu = false },
                onHide = {
                    onHide()
                    showContextMenu = false
                }
            )
        }
    }
}

/**
 * Confirmation dialog asking user whether to hide the app.
 */
@Composable
internal fun HideAppDialog(
    item: HomeAppItem,
    onDismiss: () -> Unit,
    onHide: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_app_hide_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.hide),
                primaryIcon = Icons.Outlined.VisibilityOff,
                onPrimaryClick = onHide,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Original app card preview
            AppCardLayout(
                gradientColors = item.gradientColors,
                enabled = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIcon(
                    packageInfo = item.packageInfo,
                    packageName = if (item.packageInfo == null) item.packageName else null,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    preferredSource = AppDataSource.PATCHED_APK
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = stringResource(R.string.home_app_will_be_hidden),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            // Explanation text
            Text(
                text = stringResource(
                    R.string.home_app_hide_message,
                    stringResource(R.string.home_app_show_hidden)
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Card dialog that lists hidden apps.
 */
@Composable
internal fun HiddenAppsDialog(
    hiddenAppItems: List<HomeAppItem>,
    onUnhide: (String) -> Unit,
    onDismiss: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = true,
        title = stringResource(R.string.home_app_hidden_apps_title),
        footer = {
            MorpheDialogButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        if (hiddenAppItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.home_app_no_hidden),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoBadge(
                    text = stringResource(R.string.home_app_hidden_apps_hint),
                    icon = Icons.Outlined.TouchApp,
                    isExpanded = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hiddenAppItems.forEach { item ->
                        HiddenAppRow(
                            packageName = item.packageName,
                            displayName = item.displayName,
                            packageInfo = item.packageInfo,
                            onUnhide = { onUnhide(item.packageName) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single app row in [HiddenAppsDialog] rendered as a glassmorphism card.
 */
@Composable
private fun HiddenAppRow(
    packageName: String,
    displayName: String?,
    packageInfo: PackageInfo?,
    onUnhide: () -> Unit
) {
    val view = LocalView.current
    val textColor = LocalDialogTextColor.current
    val gradientColors = AppPackages.getGradientColors(packageName)
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = gradientColors.map { it.copy(alpha = 0.5f) }
                ),
                shape = shape
            )
            .background(
                brush = Brush.linearGradient(
                    colors = gradientColors.map { it.copy(alpha = 0.15f) }
                )
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onUnhide()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            AppIcon(
                packageInfo = packageInfo,
                packageName = if (packageInfo == null) packageName else null,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
                preferredSource = AppDataSource.PATCHED_APK
            )

            // App name
            Text(
                text = displayName ?: packageName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Eye icon
            MorpheIcon(
                icon = Icons.Outlined.Visibility,
                tint = textColor.copy(alpha = 0.45f),
                size = 20.dp
            )
        }
    }
}

/**
 * Installed app card with gradient background.
 */
@Composable
fun InstalledAppCard(
    installedApp: InstalledApp,
    packageInfo: PackageInfo?,
    displayName: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasUpdate: Boolean = false,
    isAppDeleted: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val textColor = Color.White

    val versionLabel = stringResource(R.string.version)
    val installedLabel = stringResource(R.string.installed)
    val updateAvailableLabel = stringResource(R.string.update_available)
    val deletedLabel = stringResource(R.string.uninstalled)

    val showBadge = hasUpdate

    val version = remember(packageInfo, installedApp, isAppDeleted) {
        val raw = packageInfo?.versionName ?: installedApp.version
        if (raw.startsWith("v")) raw else "v$raw"
    }

    val contentDesc = remember(displayName, version, versionLabel, installedLabel, hasUpdate, updateAvailableLabel, isAppDeleted, deletedLabel) {
        buildString {
            append(displayName)
            if (version.isNotEmpty()) {
                append(", $versionLabel $version")
            }
            append(", ")
            append(if (isAppDeleted) deletedLabel else installedLabel)
            if (hasUpdate && !isAppDeleted) append(", $updateAvailableLabel")
        }
    }

    AppCardLayout(
        gradientColors = gradientColors,
        enabled = true,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.semantics {
            role = Role.Button
            this.contentDescription = contentDesc
        }
    ) {
        // App icon
        AppIcon(
            packageInfo = packageInfo,
            packageName = installedApp.originalPackageName,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
            preferredSource = AppDataSource.INSTALLED
        )

        // App info with update badge
        Box(modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // App name
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            offset = Offset(0f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Version + deleted status
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

            // Update badge
            androidx.compose.animation.AnimatedVisibility(
                visible = showBadge && !isAppDeleted,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp, end = 0.dp),
                enter = fadeIn(animationSpec = tween(400)) +
                        scaleIn(initialScale = 0.8f, animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(300)) +
                        scaleOut(targetScale = 0.8f, animationSpec = tween(300))
            ) {
                UpdateBadge()
            }
        }
    }
}

/**
 * Update badge for app cards.
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
 * App button with gradient background.
 */
@Composable
fun AppButton(
    packageName: String,
    displayName: String,
    packageInfo: PackageInfo?,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null
) {
    val textColor = Color.White
    val finalTextColor = if (enabled) textColor else textColor.copy(alpha = 0.5f)

    val notPatchedText = stringResource(R.string.home_not_patched_yet)
    val disabledText = stringResource(R.string.disabled)

    // Build content description for accessibility
    val contentDesc = remember(displayName, notPatchedText, disabledText, enabled) {
        buildString {
            append(displayName)
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
        onLongClick = onLongClick,
        modifier = modifier.semantics {
            role = Role.Button
            this.contentDescription = contentDesc
            if (!enabled) {
                stateDescription = disabledText
            }
        }
    ) {
        // App icon
        AppIcon(
            packageInfo = packageInfo,
            packageName = if (packageInfo == null) packageName else null,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
            preferredSource = AppDataSource.PATCHED_APK
        )

        // Text info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Display name
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = finalTextColor,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.4f),
                        offset = Offset(0f, 2f),
                        blurRadius = 4f
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
 * Section 4: Other apps button.
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
            .padding(bottom = 12.dp)
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

/**
 * Shared content layout for app cards and buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCardLayout(
    gradientColors: List<Color>,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val view = LocalView.current

    val backgroundAlpha = if (enabled) 0.7f else 0.3f
    val borderAlpha = if (enabled) 0.85f else 0.4f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val gradientEnd = Offset(widthPx, heightPx)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = gradientColors.map { it.copy(alpha = borderAlpha) },
                        start = Offset.Zero,
                        end = gradientEnd
                    ),
                    shape = shape
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = gradientColors.map { it.copy(alpha = backgroundAlpha) },
                        start = Offset.Zero,
                        end = gradientEnd
                    )
                )
                .combinedClickable(
                    enabled = enabled,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    },
                    onLongClick = if (onLongClick != null) {
                        {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongClick()
                        }
                    } else null
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

/**
 * Shimmer loading animation for app cards.
 */
@Composable
fun AppLoadingCard(
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    // Pulse animation for gradient background
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Shimmer animation
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        // Base gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = gradientColors.map { it.copy(alpha = pulseAlpha) },
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 0f)
                    )
                )
        )

        // Shimmer overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0f)
                        ),
                        start = Offset(shimmerOffset * 1000, 0f),
                        end = Offset((shimmerOffset + 1f) * 1000, 0f)
                    )
                )
        )

        // Content skeleton
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon skeleton
            ShimmerBox(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                baseColor = Color.White.copy(alpha = 0.2f)
            )

            // Text skeleton
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(20.dp),
                    shape = RoundedCornerShape(4.dp),
                    baseColor = Color.White.copy(alpha = 0.25f)
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    baseColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }
    }
}
