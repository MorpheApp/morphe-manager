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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.morphe.manager.util.KnownApps
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

    // Search
    showSearchButton: Boolean = false,

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

    // Search state hoisted here so both AdaptiveContent and HomeBottomActionBar share it
    var searchVisible by remember { mutableStateOf(false) }
    val searchQuery = remember { mutableStateOf("") }
    LaunchedEffect(searchVisible) { if (!searchVisible) searchQuery.value = "" }
    // Auto-close search if the button disappears
    LaunchedEffect(showSearchButton) { if (!showSearchButton) searchVisible = false }

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
                    showSearchButton = showSearchButton,
                    searchVisible = searchVisible,
                    searchQuery = searchQuery.value,
                    onSearchQueryChange = { searchQuery.value = it },
                    onSearchToggle = { searchVisible = !searchVisible },
                    onOtherAppsClick = onOtherAppsClick,
                    showOtherAppsButton = showOtherAppsButton,
                    onBundlesClick = onBundlesClick,
                    onSettingsClick = onSettingsClick,
                    isExpertModeEnabled = isExpertModeEnabled
                )
            }

            // Section 5: Bottom action bar — тільки для одноколонкового (portrait) режиму
            if (!windowSize.useTwoColumnLayout) {
                HomeBottomActionBar(
                    onBundlesClick = onBundlesClick,
                    onSettingsClick = onSettingsClick,
                    isExpertModeEnabled = isExpertModeEnabled,
                    showSearchButton = showSearchButton,
                    searchActive = searchVisible,
                    onSearchClick = { searchVisible = !searchVisible }
                )
            }
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
    showSearchButton: Boolean = false,
    searchVisible: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchToggle: () -> Unit = {},
    onOtherAppsClick: () -> Unit,
    showOtherAppsButton: Boolean = true,
    onBundlesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isExpertModeEnabled: Boolean = false
) {
    val contentPadding = windowSize.contentPadding
    val itemSpacing = windowSize.itemSpacing
    val useTwoColumns = windowSize.useTwoColumnLayout

    // True empty state: loaded and no items from any bundle: all disabled or no sources
    val isAppsEmpty by remember(homeAppItems, installedAppsLoading) {
        derivedStateOf { !installedAppsLoading && homeAppItems.isEmpty() }
    }

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
                // Left column: Greeting + Bottom action bar
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GreetingSection(message = greetingMessage)
                    }

                    // Section 5: Bottom action bar
                    HomeBottomActionBar(
                        onBundlesClick = onBundlesClick,
                        onSettingsClick = onSettingsClick,
                        isExpertModeEnabled = isExpertModeEnabled,
                        showSearchButton = showSearchButton && !isAppsEmpty,
                        searchActive = searchVisible,
                        onSearchClick = onSearchToggle,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Right column: App buttons + Other apps
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
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
                        searchVisible = searchVisible,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onBundlesClick = onBundlesClick,
                        modifier = Modifier.weight(1f)
                    )

                    // Section 4: Other apps - hidden when no apps available or bundles loading
                    AnimatedVisibility(
                        visible = !isAppsEmpty && showOtherAppsButton,
                        enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)) + expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)),
                        exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION)) + shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION))
                    ) {
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
                        searchVisible = searchVisible,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onBundlesClick = onBundlesClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section 4: Other apps - hidden when no apps available or bundles loading
                AnimatedVisibility(
                    visible = !isAppsEmpty && showOtherAppsButton,
                    enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)) + expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)),
                    exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION)) + shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(itemSpacing))
                        OtherAppsSection(
                            onClick = onOtherAppsClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerUpdateSnackbar(
    visible: Boolean,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) dismissed = false }

    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissed = true
        }
    }

    AnimatedVisibility(
        visible = visible && !dismissed,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(MorpheDefaults.ANIMATION_DURATION)) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(MorpheDefaults.ANIMATION_DURATION)) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION)),
        modifier = modifier
    ) {
        SwipeToDismissBox(
            state = swipeState,
            backgroundContent = {},
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
}

/**
 * Bundle update snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleUpdateSnackbar(
    visible: Boolean,
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?,
    modifier: Modifier = Modifier
) {
    var dismissed by remember { mutableStateOf(false) }
    // Reset when a new update cycle starts
    LaunchedEffect(visible, status) {
        if (visible && status == BundleUpdateStatus.Updating) dismissed = false
    }

    // Allow swipe only for terminal states - don't let user dismiss an in-progress update
    val swipeable = status != BundleUpdateStatus.Updating

    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    LaunchedEffect(swipeState.currentValue) {
        if (swipeable && swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissed = true
        }
    }

    AnimatedVisibility(
        visible = visible && !dismissed,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(MorpheDefaults.ANIMATION_DURATION)) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(MorpheDefaults.ANIMATION_DURATION)) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION)),
        modifier = modifier
    ) {
        SwipeToDismissBox(
            state = swipeState,
            backgroundContent = {},
            enableDismissFromStartToEnd = swipeable,
            enableDismissFromEndToStart = swipeable
        ) {
            BundleUpdateSnackbarContent(status = status, progress = progress)
        }
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
        BundleUpdateStatus.Warning -> MaterialTheme.colorScheme.secondaryContainer
        BundleUpdateStatus.Error -> MaterialTheme.colorScheme.errorContainer
        BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (status) {
        BundleUpdateStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        BundleUpdateStatus.Warning -> MaterialTheme.colorScheme.onSecondaryContainer
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
                    BundleUpdateStatus.Warning -> Icon(
                        imageVector = Icons.Outlined.SignalCellularAlt,
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
                            BundleUpdateStatus.Warning -> stringResource(R.string.home_update_skipped_metered)
                            BundleUpdateStatus.Error -> stringResource(R.string.home_update_error)
                            BundleUpdateStatus.Updating -> stringResource(R.string.home_updating_sources)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )

                    if (status == BundleUpdateStatus.Warning) {
                        Text(
                            text = stringResource(R.string.home_update_skipped_metered_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }

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
        AnimatedContent(
            targetState = message,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) +
                        slideInVertically(animationSpec = tween(400)) { it / 4 })
                    .togetherWith(
                        fadeOut(animationSpec = tween(200)) +
                                slideOutVertically(animationSpec = tween(200)) { -it / 4 }
                    )
            },
            label = "greeting_transition"
        ) { targetMessage ->
            Text(
                text = targetMessage,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
    searchVisible: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onBundlesClick: () -> Unit = {},
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
    val placeholderGradients = remember { KnownApps.DEFAULT_SHIMMER_GRADIENTS }

    // Hidden apps dialog state
    val showHiddenAppsDialog = remember { mutableStateOf(false) }

    if (showHiddenAppsDialog.value) {
        HiddenAppsDialog(
            hiddenAppItems = hiddenAppItems,
            onUnhide = onUnhideApp,
            onDismiss = { showHiddenAppsDialog.value = false }
        )
    }

    // Filtered items based on search query
    val filteredItems = remember(homeAppItems, searchQuery) {
        if (searchQuery.isBlank()) homeAppItems
        else homeAppItems.filter { item ->
            item.displayName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()
    val fadeSize = 24.dp

    // True empty state: loaded but no apps at all: all bundles disabled or no sources
    val isEmptyState = !stableLoadingState && homeAppItems.isEmpty()
    // Search empty state: items exist but nothing matches query
    val isSearchEmpty = !stableLoadingState && homeAppItems.isNotEmpty() &&
            searchQuery.isNotBlank() && filteredItems.isEmpty()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isEmptyState,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            },
            label = "home_empty_state"
        ) { empty ->
            if (empty) {
                HomeEmptyState(onBundlesClick = onBundlesClick)
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .fillMaxWidth()
                ) {
                    // Search bar
                    AnimatedVisibility(
                        visible = searchVisible,
                        enter = expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
                        exit = shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
                    ) {
                        HomeSearchTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
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
                                items = filteredItems,
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

                            // Search empty result
                            if (isSearchEmpty) {
                                item(key = "search_empty") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp)
                                            .animateItem(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.SearchOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = stringResource(R.string.search_no_results),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = stringResource(R.string.home_no_apps_search_subtitle, searchQuery),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            // "Show hidden apps" button if there are hidden apps
                            if (hiddenAppItems.isNotEmpty()) {
                                item(key = "show_hidden") {
                                    TextButton(
                                        onClick = { showHiddenAppsDialog.value = true },
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
        }
    }
}

/**
 * Empty state shown when no apps are available from any bundle.
 * Typically, seen when all sources are disabled or none added yet.
 */
@Composable
private fun HomeEmptyState(
    onBundlesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        Text(
            text = stringResource(R.string.home_no_apps_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.home_no_apps_subtitle, stringResource(R.string.sources_management_title)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        FilledTonalButton(onClick = onBundlesClick) {
            Icon(
                imageVector = Icons.Outlined.Source,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sources_management_title))
        }
    }
}

/**
 * Standalone search field for the home screen.
 * Wraps [MorpheDialogTextField] with [LocalDialogTextColor] set to onSurface
 * so it renders correctly outside a dialog context.
 */
@Composable
private fun HomeSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalDialogTextColor provides MaterialTheme.colorScheme.onSurface) {
        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.home_search_apps)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.home_search_apps)
                )
            },
            showClearButton = true,
            modifier = modifier
        )
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
        },
        compactPadding = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Original app card preview
            AppCardLayout(
                gradientColors = item.gradientColors,
                enabled = true,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                AppCardContent(
                    packageName = item.packageName,
                    packageInfo = item.packageInfo,
                    displayName = item.displayName,
                    subtitle = stringResource(R.string.home_app_will_be_hidden),
                    gradientColors = item.gradientColors,
                    iconSource = AppDataSource.PATCHED_APK
                )
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
        },
        compactPadding = true
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                hiddenAppItems.forEach { item ->
                    // Original app card preview
                    AppCardLayout(
                        gradientColors = item.gradientColors,
                        enabled = true,
                        onClick = { onUnhide(item.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppCardContent(
                            packageName = item.packageName,
                            packageInfo = item.packageInfo,
                            displayName = item.displayName,
                            subtitle = stringResource(R.string.home_app_hidden_apps_hint),
                            gradientColors = item.gradientColors,
                            iconSource = AppDataSource.PATCHED_APK
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shared icon + text content for [AppCardLayout] rows.
 *
 * @param packageName   Package name used for icon lookup when [packageInfo] is null.
 * @param packageInfo   Resolved [PackageInfo]; when non-null [packageName] is ignored for the icon.
 * @param displayName   Primary label shown in bold.
 * @param subtitle      Secondary line shown below [displayName]; null → not rendered.
 * @param gradientColors Gradient palette forwarded to [AppIcon] placeholder.
 * @param iconSource    [AppDataSource] preference for [AppIcon].
 */
@Composable
private fun RowScope.AppCardContent(
    packageName: String,
    packageInfo: PackageInfo?,
    displayName: String,
    subtitle: String?,
    gradientColors: List<Color>,
    iconSource: AppDataSource
) {
    val textColor = Color.White
    val subtitleColor = Color.White.copy(alpha = 0.75f)
    val titleShadow = Shadow(
        color = Color.Black.copy(alpha = 0.4f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    )
    val subtitleShadow = Shadow(
        color = Color.Black.copy(alpha = 0.4f),
        offset = Offset(0f, 1f),
        blurRadius = 2f
    )

    AppIcon(
        packageInfo = packageInfo,
        packageName = if (packageInfo == null) packageName else null,
        contentDescription = null,
        modifier = Modifier.size(60.dp),
        preferredSource = iconSource,
        placeholderGradientColors = gradientColors,
        placeholderInnerPadding = 6.dp
    )

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                shadow = titleShadow
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = subtitleShadow),
                color = subtitleColor
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
            modifier = Modifier.size(60.dp),
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
            @Suppress("RemoveRedundantQualifierName")
            androidx.compose.animation.AnimatedVisibility(
                visible = hasUpdate && !isAppDeleted,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp, end = 0.dp),
                enter = fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)) + scaleIn(initialScale = 0.8f, animationSpec = tween(MorpheDefaults.ANIMATION_DURATION)),
                exit = fadeOut(tween(MorpheDefaults.ANIMATION_DURATION)) + scaleOut(targetScale = 0.8f, animationSpec = tween(MorpheDefaults.ANIMATION_DURATION))
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
        AppCardContent(
            packageName = packageName,
            packageInfo = packageInfo,
            displayName = displayName,
            subtitle = notPatchedText,
            gradientColors = gradientColors,
            iconSource = AppDataSource.PATCHED_APK
        )
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

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "other_apps_press_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
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
 *
 * Uses a multi-layer frosted glass effect:
 * - radial gradient base tinted from card colors
 * - top-left specular shine
 * - bottom-right warm glow from card accent color
 * - diagonal sweep highlight
 * - subtle horizontal frost band
 * - gradient border
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

    val contentAlpha = if (enabled) 1f else 0.45f
    val baseColor = gradientColors.firstOrNull() ?: Color.White
    val midColor = gradientColors.getOrElse(1) { baseColor }
    val endColor = gradientColors.lastOrNull() ?: baseColor

    // Disabled state fades everything
    val glassAlpha  = if (enabled) 1f else 0.5f
    val borderAlpha = if (enabled) 1f else 0.4f

    // Press scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "card_press_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .drawWithContent {
                val w  = size.width
                val h  = size.height
                val cr = CornerRadius(24.dp.toPx())

                // Layer 1: radial base - color blooms from bottom-left
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.80f * glassAlpha),
                            midColor.copy(alpha = 0.60f * glassAlpha),
                            endColor.copy(alpha = 0.40f * glassAlpha)
                        ),
                        center = Offset(w * 0.15f, h * 0.85f),
                        radius = w * 1.1f
                    ),
                    cornerRadius = cr
                )

                // Layer 2: secondary radial bloom from top-right (accent)
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            endColor.copy(alpha = 0.55f * glassAlpha),
                            midColor.copy(alpha = 0.25f * glassAlpha),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.88f, h * 0.12f),
                        radius = w * 0.75f
                    ),
                    cornerRadius = cr
                )

                // Layer 3: frosted white overlay - very subtle, just adds glass texture
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f * glassAlpha),
                            Color.White.copy(alpha = 0.01f * glassAlpha),
                            Color.White.copy(alpha = 0.02f * glassAlpha)
                        ),
                        startY = 0f,
                        endY = h
                    ),
                    cornerRadius = cr
                )

                // Layer 4: diagonal sweep highlight (top-left → mid) - thin specular only
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f * glassAlpha),
                            Color.White.copy(alpha = 0.02f * glassAlpha),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(w * 0.5f, h)
                    ),
                    cornerRadius = cr
                )

                // Layer 5: bottom edge warm reflection
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            endColor.copy(alpha = 0.22f * glassAlpha)
                        ),
                        center = Offset(w * 0.5f, h),
                        radius = w * 0.65f
                    ),
                    cornerRadius = cr
                )

                drawContent()

                // Border: bright top-left → faded bottom-right
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f * borderAlpha),
                            midColor.copy(alpha = 0.30f * borderAlpha),
                            endColor.copy(alpha = 0.15f * borderAlpha),
                            Color.White.copy(alpha = 0.20f * borderAlpha)
                        ),
                        start = Offset(0f, 0f),
                        end   = Offset(w, h)
                    ),
                    cornerRadius = cr,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
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
                .padding(horizontal = 16.dp)
                .graphicsLayer { alpha = contentAlpha },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
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
                modifier = Modifier
                    .size(60.dp)
                    .padding(6.dp),
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
