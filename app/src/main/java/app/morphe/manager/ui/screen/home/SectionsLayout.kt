/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BundleUpdateStatus
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.KnownApps
import app.morphe.manager.util.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds

/** Data describing one side of a swipe action - icon, label, and colors. */
private data class SwipeActionConfig(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

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
    greetingMessage: String?,

    // Dynamic app items
    homeAppItems: List<HomeAppItem>,
    onAppClick: (HomeAppItem) -> Unit,
    onHideApp: (String) -> Unit,
    onHideMultiple: (Set<String>) -> Unit = {},
    onUnhideApp: (String) -> Unit,
    onShowPatches: (HomeAppItem) -> Unit,
    showGestureHint: Boolean,
    onGestureHintShown: () -> Unit,
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
    isExpertModeEnabled: Boolean = false,

    // Onboarding
    onboardingState: OnboardingState? = null,

    // Reorder
    onSaveOrder: (List<String>) -> Unit = {},
    onResetOrder: () -> Unit = {},

    // Accessibility
    onRefreshGreeting: (() -> Unit)? = null
) {
    val windowSize = rememberWindowSize()

    // Search state hoisted here so both AdaptiveContent and HomeBottomActionBar share it
    val searchVisible = remember { mutableStateOf(false) }
    val searchQuery = remember { mutableStateOf("") }
    LaunchedEffect(searchVisible.value) { if (!searchVisible.value) searchQuery.value = "" }
    // Auto-close search if the button disappears
    LaunchedEffect(showSearchButton) { if (!showSearchButton) searchVisible.value = false }

    // Back gesture closes search (registered before multiselect BackHandler so multiselect takes priority)
    BackHandler(enabled = searchVisible.value) { searchVisible.value = false }

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
                    onHideApp = onHideApp,
                    onHideMultiple = onHideMultiple,
                    onUnhideApp = onUnhideApp,
                    onShowPatches = onShowPatches,
                    showGestureHint = showGestureHint,
                    onGestureHintShown = onGestureHintShown,
                    hiddenAppItems = hiddenAppItems,
                    installedAppsLoading = installedAppsLoading,
                    showSearchButton = showSearchButton,
                    searchVisible = searchVisible.value,
                    searchQuery = searchQuery.value,
                    onSearchQueryChange = { searchQuery.value = it },
                    onSearchToggle = { searchVisible.value = !searchVisible.value },
                    onOtherAppsClick = onOtherAppsClick,
                    showOtherAppsButton = showOtherAppsButton,
                    onBundlesClick = onBundlesClick,
                    onSettingsClick = onSettingsClick,
                    isExpertModeEnabled = isExpertModeEnabled,
                    onboardingState = onboardingState,
                    onSaveOrder = onSaveOrder,
                    onResetOrder = onResetOrder,
                    onRefreshGreeting = onRefreshGreeting
                )
            }

            // Section 5: Bottom action bar - тільки для одноколонкового (portrait) режиму
            if (!isLandscape()) {
                HomeBottomActionBar(
                    onBundlesClick = onBundlesClick,
                    onSettingsClick = onSettingsClick,
                    isExpertModeEnabled = isExpertModeEnabled,
                    showSearchButton = showSearchButton,
                    searchActive = searchVisible.value,
                    onSearchClick = { searchVisible.value = !searchVisible.value },
                    onSourcesPositioned = onboardingState?.let { s -> { b -> s.sourcesButtonBounds = b } },
                    onSettingsPositioned = onboardingState?.let { s -> { b -> s.settingsButtonBounds = b } }
                )
            }
        }

        // Section 1: Notifications overlay - matches maxCardWidth in AdaptiveContent
        val maxCardWidth = if (isLandscape()) 700.dp else 560.dp
        NotificationsOverlay(
            hasManagerUpdate = hasManagerUpdate,
            onShowUpdateDetails = onShowUpdateDetails,
            showBundleUpdateSnackbar = showBundleUpdateSnackbar,
            snackbarStatus = snackbarStatus,
            bundleUpdateProgress = bundleUpdateProgress,
            modifier = Modifier
                .widthIn(max = maxCardWidth)
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
    greetingMessage: String?,
    homeAppItems: List<HomeAppItem>,
    onAppClick: (HomeAppItem) -> Unit,
    onHideApp: (String) -> Unit,
    onHideMultiple: (Set<String>) -> Unit = {},
    onUnhideApp: (String) -> Unit,
    onShowPatches: (HomeAppItem) -> Unit,
    showGestureHint: Boolean,
    onGestureHintShown: () -> Unit,
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
    isExpertModeEnabled: Boolean = false,
    onboardingState: OnboardingState? = null,
    onSaveOrder: (List<String>) -> Unit = {},
    onResetOrder: () -> Unit = {},
    onRefreshGreeting: (() -> Unit)? = null
) {
    val contentPadding = windowSize.contentPadding
    val itemSpacing = windowSize.itemSpacing
    val useTwoColumns = isLandscape()
    val maxCardWidth = if (useTwoColumns) 700.dp else 560.dp

    // True empty state: loaded and no items from any bundle: all disabled or no sources
    val isAppsEmpty by remember(homeAppItems, installedAppsLoading) {
        derivedStateOf { !installedAppsLoading && homeAppItems.isEmpty() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (useTwoColumns) {
            // Sidebar layout for landscape
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeSidebarPanel(
                    showSearchButton = showSearchButton && !isAppsEmpty,
                    searchActive = searchVisible,
                    isExpertModeEnabled = isExpertModeEnabled,
                    onSearchClick = onSearchToggle,
                    onBundlesClick = onBundlesClick,
                    onSettingsClick = onSettingsClick,
                    onSourcesPositioned = onboardingState?.let { s -> { b -> s.sourcesButtonBounds = b } },
                    onSettingsPositioned = onboardingState?.let { s -> { b -> s.settingsButtonBounds = b } }
                )
                VerticalDivider(modifier = Modifier.padding(vertical = 20.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = contentPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!greetingMessage.isNullOrEmpty()) {
                        GreetingSection(
                            message = greetingMessage,
                            modifier = Modifier.widthIn(max = maxCardWidth).fillMaxWidth(),
                            onRefresh = onRefreshGreeting
                        )
                        Spacer(modifier = Modifier.height(itemSpacing))
                    }
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        MainAppsSection(
                            homeAppItems = homeAppItems,
                            itemSpacing = itemSpacing,
                            maxCardWidth = maxCardWidth,
                            onAppClick = onAppClick,
                            onHideApp = onHideApp,
                            onHideMultiple = onHideMultiple,
                            onUnhideApp = onUnhideApp,
                            onShowPatches = onShowPatches,
                            showGestureHint = showGestureHint,
                            onGestureHintShown = onGestureHintShown,
                            hiddenAppItems = hiddenAppItems,
                            installedAppsLoading = installedAppsLoading,
                            searchVisible = searchVisible,
                            searchQuery = searchQuery,
                            onSearchQueryChange = onSearchQueryChange,
                            onBundlesClick = onBundlesClick,
                            onboardingState = onboardingState,
                            showFadeOverlay = false,
                            onSaveOrder = onSaveOrder,
                            onResetOrder = onResetOrder,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AnimatedVisibility(
                        visible = !isAppsEmpty && showOtherAppsButton,
                        enter = MorpheAnimations.expandFadeEnter,
                        exit = MorpheAnimations.shrinkFadeExit
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(itemSpacing))
                            OtherAppsSection(
                                onClick = onOtherAppsClick,
                                modifier = Modifier.widthIn(max = maxCardWidth).fillMaxWidth()
                            )
                        }
                    }
                }
            }
        } else {
            // Single-column layout for compact windows (portrait)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                // Section 2: Greeting - when disabled, show a small top spacer so
                // the app cards don't sit flush against the top of the screen
                if (!greetingMessage.isNullOrEmpty()) {
                    GreetingSection(
                        message = greetingMessage,
                        modifier = Modifier.padding(horizontal = contentPadding),
                        onRefresh = onRefreshGreeting
                    )
                    Spacer(modifier = Modifier.height(itemSpacing))
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Section 3: Scrollable app buttons
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    MainAppsSection(
                        homeAppItems = homeAppItems,
                        itemSpacing = itemSpacing,
                        horizontalPadding = contentPadding,
                        maxCardWidth = maxCardWidth,
                        onAppClick = onAppClick,
                        onHideApp = onHideApp,
                        onHideMultiple = onHideMultiple,
                        onUnhideApp = onUnhideApp,
                        onShowPatches = onShowPatches,
                        showGestureHint = showGestureHint,
                        onGestureHintShown = onGestureHintShown,
                        hiddenAppItems = hiddenAppItems,
                        installedAppsLoading = installedAppsLoading,
                        searchVisible = searchVisible,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onBundlesClick = onBundlesClick,
                        onboardingState = onboardingState,
                        onSaveOrder = onSaveOrder,
                        onResetOrder = onResetOrder,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section 4: Other apps - hidden when no apps available or bundles loading
                AnimatedVisibility(
                    visible = !isAppsEmpty && showOtherAppsButton,
                    enter = MorpheAnimations.expandFadeEnter,
                    exit = MorpheAnimations.shrinkFadeExit
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(itemSpacing))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = contentPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            OtherAppsSection(
                                onClick = onOtherAppsClick,
                                modifier = Modifier
                                    .widthIn(max = maxCardWidth - contentPadding * 2)
                                    .fillMaxWidth()
                            )
                        }
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
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
    val dismissed = remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) dismissed.value = false }

    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissed.value = true
        }
    }

    AnimatedVisibility(
        visible = visible && !dismissed.value,
        enter = MorpheAnimations.slideUpFadeEnter,
        exit = MorpheAnimations.slideUpFadeExit,
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
                    MorpheIcon(
                        icon = Icons.Outlined.Update,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
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
@Composable
fun BundleUpdateSnackbar(
    visible: Boolean,
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?,
    modifier: Modifier = Modifier
) {
    val dismissed = remember { mutableStateOf(false) }
    // Reset when a new update cycle starts
    LaunchedEffect(visible, status) {
        if (visible && status == BundleUpdateStatus.Updating) dismissed.value = false
    }

    // Allow swipe only for terminal states - don't let user dismiss an in-progress update
    val swipeable = status != BundleUpdateStatus.Updating

    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    LaunchedEffect(swipeState.currentValue) {
        if (swipeable && swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissed.value = true
        }
    }

    AnimatedVisibility(
        visible = visible && !dismissed.value,
        enter = MorpheAnimations.slideUpFadeEnter,
        exit = MorpheAnimations.slideUpFadeExit,
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
    val fraction = if (progress == null || progress.total == 0) 0f
                   else progress.completed.toFloat() / progress.total

    val downloadFraction = progress?.bytesTotal
        ?.takeIf { it > 0L }
        ?.let { progress.bytesRead.toFloat() / it }
        ?: 0f

    val isDownloading = progress?.phase == PatchBundleRepository.BundleUpdatePhase.Downloading &&
            downloadFraction > 0f
    val displayProgress = if (isDownloading) downloadFraction else fraction

    val containerColor by animateColorAsState(
        targetValue = when (status) {
            BundleUpdateStatus.Success -> MaterialTheme.colorScheme.primaryContainer
            BundleUpdateStatus.Warning -> MaterialTheme.colorScheme.secondaryContainer
            BundleUpdateStatus.Error -> MaterialTheme.colorScheme.errorContainer
            BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 300),
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when (status) {
            BundleUpdateStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
            BundleUpdateStatus.Warning -> MaterialTheme.colorScheme.onSecondaryContainer
            BundleUpdateStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
            BundleUpdateStatus.Updating -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 300),
        label = "contentColor"
    )

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
                Crossfade(targetState = status, label = "snackbarIcon") { s ->
                    when (s) {
                        BundleUpdateStatus.Success -> MorpheIcon(
                            icon = Icons.Outlined.CheckCircle,
                            tint = contentColor
                        )
                        BundleUpdateStatus.Warning -> MorpheIcon(
                            icon = Icons.Outlined.SignalCellularAlt,
                            tint = contentColor
                        )
                        BundleUpdateStatus.Error -> MorpheIcon(
                            icon = Icons.Outlined.Warning,
                            tint = contentColor
                        )
                        BundleUpdateStatus.Updating -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = contentColor
                        )
                    }
                }

                // Text content
                Column(modifier = Modifier.weight(1f)) {
                    Crossfade(targetState = status, label = "snackbarTitle") { s ->
                        Text(
                            text = when (s) {
                                BundleUpdateStatus.Success -> stringResource(R.string.home_update_success)
                                BundleUpdateStatus.Warning -> stringResource(R.string.home_update_skipped_metered)
                                BundleUpdateStatus.Error -> stringResource(R.string.home_update_error)
                                BundleUpdateStatus.Updating -> stringResource(R.string.home_updating_sources)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }

                    val subtitleColor = contentColor.copy(alpha = 0.8f)

                    if (status == BundleUpdateStatus.Warning) {
                        Text(
                            text = stringResource(R.string.home_update_skipped_metered_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtitleColor
                        )
                    }

                    if (status == BundleUpdateStatus.Updating && progress != null) {
                        val totalMb = (progress.bytesTotal ?: 0L).toFloat() / (1024 * 1024)
                        val readMb = progress.bytesRead.toFloat() / (1024 * 1024)
                        val percent = (downloadFraction * 100).toInt()
                        val (subtitleKey, subtitle) = when {
                            progress.total > 1 && isDownloading -> 1 to stringResource(
                                R.string.home_update_bundle_count_with_bytes,
                                progress.completed, progress.total, readMb, totalMb, percent
                            )
                            progress.total > 1 -> 2 to stringResource(
                                R.string.home_update_bundle_count,
                                progress.completed, progress.total
                            )
                            isDownloading -> 3 to stringResource(
                                R.string.home_update_download_progress,
                                readMb, totalMb, percent.toString()
                            )
                            progress.currentBundleName != null -> 4 to progress.currentBundleName
                            else -> 0 to null
                        }
                        AnimatedContent(
                            targetState = subtitleKey to subtitle,
                            contentKey = { it.first },
                            transitionSpec = MorpheAnimations.fadeCrossfade(200),
                            label = "subtitle"
                        ) { (_, text) ->
                            if (text != null) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = subtitleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        AnimatedVisibility(visible = progress.activeNames.isNotEmpty()) {
                            Crossfade(
                                targetState = progress.activeNames.joinToString(", "),
                                label = "activeNames"
                            ) { names ->
                                Text(
                                    text = names,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (status == BundleUpdateStatus.Success) {
                        Text(
                            text = stringResource(R.string.home_update_success_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtitleColor
                        )
                    }
                    if (status == BundleUpdateStatus.Error) {
                        Text(
                            text = stringResource(R.string.home_update_error_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtitleColor
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
    message: String?,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null
) {
    if (message.isNullOrEmpty()) return
    val refreshLabel = stringResource(R.string.refresh)
    Box(
        modifier = modifier.then(
            if (onRefresh != null) Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(refreshLabel) { onRefresh(); true }
                )
            } else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = message,
            transitionSpec = MorpheAnimations.slideUpContentTransitionSpec,
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
@SuppressLint("FrequentlyChangingValue")
@Composable
fun MainAppsSection(
    modifier: Modifier = Modifier,
    homeAppItems: List<HomeAppItem>,
    itemSpacing: Dp = 16.dp,
    horizontalPadding: Dp = 0.dp,
    maxCardWidth: Dp = 500.dp,
    onAppClick: (HomeAppItem) -> Unit,
    onHideApp: (String) -> Unit,
    onHideMultiple: (Set<String>) -> Unit = {},
    onUnhideApp: (String) -> Unit,
    onShowPatches: (HomeAppItem) -> Unit,
    showGestureHint: Boolean,
    onGestureHintShown: () -> Unit,
    hiddenAppItems: List<HomeAppItem> = emptyList(),
    installedAppsLoading: Boolean = false,
    searchVisible: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onBundlesClick: () -> Unit = {},
    onboardingState: OnboardingState? = null,
    showFadeOverlay: Boolean = true,
    onSaveOrder: (List<String>) -> Unit = {},
    onResetOrder: () -> Unit = {}
) {
    // Multi-select state - set of packageNames chosen for bulk hide
    val isMultiSelectMode = remember { mutableStateOf(false) }
    val selectedPackages = remember { mutableStateOf(emptySet<String>()) }

    // Reorder state
    val isReorderMode = remember { mutableStateOf(false) }
    var localOrder by remember { mutableStateOf(homeAppItems.map { it.packageName }) }
    val haptic = LocalHapticFeedback.current

    // True when the multibar (select or reorder action bar) is visible
    val isMultibarVisible = isMultiSelectMode.value || isReorderMode.value

    // Back gesture/button cancels multi-select instead of navigating back
    BackHandler(enabled = isMultiSelectMode.value) {
        isMultiSelectMode.value = false
        selectedPackages.value = emptySet()
    }

    // Back gesture/button exits reorder mode without saving
    BackHandler(enabled = isReorderMode.value) {
        isReorderMode.value = false
        localOrder = homeAppItems.map { it.packageName }
    }

    // Sync selection and local order with current item list
    LaunchedEffect(homeAppItems) {
        val filtered = selectedPackages.value.filter { pkg ->
            homeAppItems.any { it.packageName == pkg }
        }.toSet()
        selectedPackages.value = filtered
        if (filtered.isEmpty()) isMultiSelectMode.value = false

        if (!isReorderMode.value) {
            localOrder = homeAppItems.map { it.packageName }
        } else {
            val pkgSet = homeAppItems.map { it.packageName }.toSet()
            val kept = localOrder.filter { it in pkgSet }
            val keptSet = kept.toSet()
            val added = pkgSet.filter { it !in keptSet }
            localOrder = kept + added
        }
    }

    // Track if real content has ever arrived so we never re-show the shimmer on resume
    val hasEverLoaded = remember {
        mutableStateOf(homeAppItems.isNotEmpty() || hiddenAppItems.isNotEmpty())
    }

    // Stable loading state - drives shimmer visibility.
    // Starts as true when there is nothing to show yet; once content arrives it latches to false
    // and never goes back to true (we don't want shimmer on every recomposition).
    val stableLoadingState = remember { mutableStateOf(!hasEverLoaded.value) }

    LaunchedEffect(installedAppsLoading, homeAppItems.size, hiddenAppItems.size) {
        val hasItems = homeAppItems.isNotEmpty() || hiddenAppItems.isNotEmpty()
        if (hasItems) hasEverLoaded.value = true

        val shouldShowShimmer = !hasEverLoaded.value && installedAppsLoading
        if (shouldShowShimmer) {
            stableLoadingState.value = true
        } else {
            // Small delay so Compose has one frame to lay out the real cards before the
            // shimmer fades out - prevents a single-frame empty gap.
            if (stableLoadingState.value) delay(50.milliseconds)
            stableLoadingState.value = false
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
            onUnhideMultiple = { packages ->
                packages.forEach { onUnhideApp(it) }
            },
            onShowPatches = onShowPatches,
            onDismiss = { showHiddenAppsDialog.value = false }
        )
    }

    // Filtered visible items based on search query
    val filteredItems = remember(homeAppItems, searchQuery) {
        if (searchQuery.isBlank()) homeAppItems
        else homeAppItems.filter { item ->
            item.displayName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    // Hidden items that match the search query
    val filteredHiddenItems = remember(hiddenAppItems, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else hiddenAppItems.filter { item ->
            item.displayName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val newOrder = localOrder.toMutableList()
        val moved = newOrder.removeAt(from.index)
        newOrder.add(to.index, moved)
        localOrder = newOrder
    }
    val orderedItems = remember(localOrder, homeAppItems) {
        val byPackage = homeAppItems.associateBy { it.packageName }
        localOrder.mapNotNull { byPackage[it] }
    }

    // Polite TalkBack announcement after a screen-reader-triggered Move action.
    // Empty until the first move; cleared by the next compose if needed
    var moveAnnouncement by remember { mutableStateOf("") }
    val moveAnnouncementFormat = stringResource(R.string.accessibility_app_moved_announcement)

    // True empty state: loaded, no apps from any bundle (no sources / all disabled)
    val isNoSourcesState = !stableLoadingState.value && homeAppItems.isEmpty() && hiddenAppItems.isEmpty()
    // All-hidden state: apps exist but all are hidden
    val isAllHiddenState = !stableLoadingState.value && homeAppItems.isEmpty() && hiddenAppItems.isNotEmpty()
    val isEmptyState = isNoSourcesState || isAllHiddenState
    // Search empty state: items exist but nothing matches query (including hidden)
    val isSearchEmpty = !stableLoadingState.value && homeAppItems.isNotEmpty() &&
            searchQuery.isNotBlank() && filteredItems.isEmpty() && filteredHiddenItems.isEmpty()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Hidden polite live region used to announce the result of TalkBack Move up/down actions
        Spacer(
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = moveAnnouncement
            }
        )

        AnimatedContent(
            targetState = isEmptyState,
            transitionSpec = MorpheAnimations.fadeCrossfade(300),
            label = "home_empty_state"
        ) { empty ->
            if (empty) {
                if (isAllHiddenState) {
                    MorpheEmptyState(
                        icon = Icons.Outlined.VisibilityOff,
                        title = stringResource(R.string.home_all_apps_hidden_title),
                        subtitle = stringResource(R.string.home_all_apps_hidden_subtitle),
                        actionIcon = Icons.Outlined.Visibility,
                        actionLabel = pluralStringResource(R.plurals.home_app_show_hidden_count, hiddenAppItems.size, hiddenAppItems.size.toString()),
                        onAction = { showHiddenAppsDialog.value = true }
                    )
                } else {
                    MorpheEmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(R.string.home_no_apps_title),
                        subtitle = stringResource(R.string.home_no_apps_subtitle, stringResource(R.string.sources_management_title)),
                        actionIcon = Icons.Outlined.Source,
                        actionLabel = stringResource(R.string.sources_management_title),
                        onAction = onBundlesClick
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .widthIn(max = maxCardWidth)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Search bar
                        AnimatedVisibility(
                            visible = searchVisible,
                            enter = MorpheAnimations.expandFadeEnter,
                            exit = MorpheAnimations.shrinkFadeExit
                        ) {
                            HomeSearchTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                requestFocus = searchVisible,
                                modifier = Modifier
                                    .padding(horizontal = horizontalPadding)
                                    .padding(bottom = 8.dp)
                            )
                        }

                        // Vertical fade overlay drawn on top of LazyColumn.
                        // The overlay is pointer-transparent so swipe gestures pass through
                        Box(modifier = Modifier.fillMaxWidth()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                                contentPadding = PaddingValues(
                                    start = horizontalPadding,
                                    end = horizontalPadding,
                                    // Extra bottom padding so cards aren't hidden behind the action bar
                                    // MultiSelectBar surface height (100dp) minus bar's own
                                    // 8dp top padding, plus itemSpacing for consistent card gap
                                    bottom = if (isMultibarVisible) 92.dp + itemSpacing else 0.dp
                                )
                            ) {
                                // Cold start: homeAppItems still empty - show placeholder shimmer cards
                                if (stableLoadingState.value && homeAppItems.isEmpty()) {
                                    items(3, key = { "placeholder_$it" }) { index ->
                                        AppLoadingCard(
                                            gradientColors = placeholderGradients[index % placeholderGradients.size],
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                } else if (isReorderMode.value) {
                                    itemsIndexed(
                                        items = orderedItems,
                                        key = { _, item -> item.packageName }
                                    ) { _, item ->
                                        ReorderableItem(reorderableState, key = item.packageName) { _ ->
                                            DynamicAppCard(
                                                item = item,
                                                isLoading = false,
                                                hasUpdate = item.hasUpdate,
                                                onAppClick = {},
                                                onHide = {},
                                                onShowPatches = {},
                                                showGestureHint = false,
                                                onGestureHintShown = {},
                                                isSelected = false,
                                                isMultiSelectMode = true,
                                                onLongPress = {},
                                                dragHandleModifier = Modifier.draggableHandle(
                                                    onDragStarted = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                ),
                                                modifier = Modifier.animateItem()
                                            )
                                        }
                                    }
                                } else {
                                    // Direct reorder a11y actions are exposed only when there's no search
                                    // filter and no multi-select active so the indices match localOrder
                                    val directReorderAllowed = searchQuery.isBlank() && !isMultiSelectMode.value
                                    itemsIndexed(
                                        items = filteredItems,
                                        key = { _, item -> item.packageName }
                                    ) { index, item ->
                                        val isSelected = item.packageName in selectedPackages.value
                                        DynamicAppCard(
                                            item = item,
                                            isLoading = stableLoadingState.value,
                                            hasUpdate = item.hasUpdate,
                                            onAppClick = {
                                                if (isMultiSelectMode.value) {
                                                    // In multi-select mode taps toggle selection
                                                    selectedPackages.value = if (isSelected)
                                                        selectedPackages.value - item.packageName
                                                    else
                                                        selectedPackages.value + item.packageName
                                                } else {
                                                    onAppClick(item)
                                                }
                                            },
                                            onHide = { onHideApp(item.packageName) },
                                            onShowPatches = { onShowPatches(item) },
                                            // Hint plays only on the first card
                                            showGestureHint = index == 0 && showGestureHint,
                                            onGestureHintShown = onGestureHintShown,
                                            isSelected = isSelected,
                                            isMultiSelectMode = isMultiSelectMode.value,
                                            onLongPress = {
                                                // Long-press enters multi-select and toggles this card
                                                isMultiSelectMode.value = true
                                                selectedPackages.value = if (isSelected)
                                                    selectedPackages.value - item.packageName
                                                else
                                                    selectedPackages.value + item.packageName
                                            },
                                            onMoveUp = if (directReorderAllowed && index > 0) {
                                                {
                                                    val current = localOrder.toMutableList()
                                                    val from = current.indexOf(item.packageName)
                                                    if (from > 0) {
                                                        val moved = current.removeAt(from)
                                                        current.add(from - 1, moved)
                                                        localOrder = current
                                                        onSaveOrder(current)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        moveAnnouncement = moveAnnouncementFormat
                                                            .format(item.displayName, from, current.size)
                                                    }
                                                }
                                            } else null,
                                            onMoveDown = if (directReorderAllowed && index < filteredItems.size - 1) {
                                                {
                                                    val current = localOrder.toMutableList()
                                                    val from = current.indexOf(item.packageName)
                                                    if (from in 0 until current.size - 1) {
                                                        val moved = current.removeAt(from)
                                                        current.add(from + 1, moved)
                                                        localOrder = current
                                                        onSaveOrder(current)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        moveAnnouncement = moveAnnouncementFormat
                                                            .format(item.displayName, from + 2, current.size)
                                                    }
                                                }
                                            } else null,
                                            modifier = Modifier
                                                .animateItem()
                                                .then(
                                                    if (index == 0 && onboardingState != null)
                                                        Modifier.onGloballyPositioned { coords ->
                                                            onboardingState.firstAppCardBounds = coords.boundsInWindow()
                                                        }
                                                    else Modifier
                                                )
                                        )
                                    }

                                    // Hidden apps that match the search query
                                    if (filteredHiddenItems.isNotEmpty()) {
                                        item(key = "search_hidden_header") {
                                            Text(
                                                text = stringResource(R.string.hidden),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp, bottom = 4.dp)
                                                    .animateItem()
                                            )
                                        }
                                        itemsIndexed(
                                            items = filteredHiddenItems,
                                            key = { _, item -> "hidden_${item.packageName}" }
                                        ) { _, item ->
                                            HiddenSearchAppCard(
                                                item = item,
                                                onUnhide = { onUnhideApp(item.packageName) },
                                                onAppClick = { onAppClick(item) },
                                                onShowPatches = { onShowPatches(item) },
                                                modifier = Modifier.animateItem()
                                            )
                                        }
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

                                    // "Show hidden apps" button
                                    if (hiddenAppItems.isNotEmpty() && searchQuery.isBlank()) {
                                        item(key = "show_hidden") {
                                            ShowHiddenAppsButton(
                                                count = hiddenAppItems.size,
                                                onClick = { showHiddenAppsDialog.value = true },
                                                modifier = Modifier.animateItem(
                                                    fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                                                    fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Vertical fade overlay drawn on top of LazyColumn.
                            // The overlay is pointer-transparent so swipe gestures pass through
                            val canScrollUp = listState.firstVisibleItemIndex > 0 ||
                                    listState.firstVisibleItemScrollOffset > 0
                            val canScrollDown = listState.canScrollForward
                            val topAlpha by animateFloatAsState(
                                targetValue = if (canScrollUp) 1f else 0f,
                                animationSpec = tween(150),
                                label = "fade_top_alpha"
                            )
                            val bottomAlpha by animateFloatAsState(
                                targetValue = if (canScrollDown) 1f else 0f,
                                animationSpec = tween(150),
                                label = "fade_bottom_alpha"
                            )
                            if (showFadeOverlay && (topAlpha > 0f || bottomAlpha > 0f)) {
                                val bgColor = MaterialTheme.colorScheme.background
                                val fadePx = with(LocalDensity.current) { 8.dp.toPx() } // Fade size
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .drawWithContent {
                                            drawContent()
                                            if (topAlpha > 0f) {
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(bgColor, Color.Transparent),
                                                        startY = 0f,
                                                        endY = fadePx
                                                    ),
                                                    alpha = topAlpha
                                                )
                                            }
                                            if (bottomAlpha > 0f) {
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, bgColor),
                                                        startY = size.height - fadePx,
                                                        endY = size.height
                                                    ),
                                                    alpha = bottomAlpha
                                                )
                                            }
                                        }
                                )
                            }
                        }
                    }

                    // Multi-select / reorder bar - slides up from bottom
                    MultiSelectBar(
                        selectedCount = selectedPackages.value.size,
                        totalCount = homeAppItems.size,
                        visible = isMultibarVisible,
                        isReorderMode = isReorderMode.value,
                        onSelectAll = {
                            selectedPackages.value = homeAppItems.map { it.packageName }.toSet()
                        },
                        onDeselectAll = { selectedPackages.value = emptySet() },
                        onAction = {
                            onHideMultiple(selectedPackages.value)
                            isMultiSelectMode.value = false
                            selectedPackages.value = emptySet()
                        },
                        actionIcon = Icons.Outlined.VisibilityOff,
                        actionContentDescription = stringResource(R.string.hide),
                        actionDoneMessage = stringResource(R.string.hidden),
                        onCancel = {
                            isMultiSelectMode.value = false
                            selectedPackages.value = emptySet()
                        },
                        onEnterReorder = {
                            isMultiSelectMode.value = false
                            selectedPackages.value = emptySet()
                            isReorderMode.value = true
                        },
                        onSaveOrder = {
                            onSaveOrder(localOrder)
                            isReorderMode.value = false
                        },
                        onResetOrder = {
                            onResetOrder()
                            localOrder = homeAppItems.map { it.packageName }
                            isReorderMode.value = false
                        },
                        onCancelReorder = {
                            isReorderMode.value = false
                            localOrder = homeAppItems.map { it.packageName }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = horizontalPadding)
                    )
                }
            }
        }
    }
}

/**
 * Pill-shaped button that appears at the bottom of the app list when hidden apps exist.
 * Styled consistently with [OtherAppsSection] - frosted glass surface with border.
 */
@Composable
private fun ShowHiddenAppsButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeGlassPillButton(
        onClick = onClick,
        modifier = modifier,
        icon = Icons.Outlined.Visibility,
        text = pluralStringResource(R.plurals.home_app_show_hidden_count, count, count.toString())
    )
}

/**
 * Generic empty state with icon, title, optional subtitle and optional action button.
 */
@Composable
internal fun MorpheEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
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
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
        if (onAction != null && actionLabel != null) {
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(onClick = onAction) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(actionLabel)
            }
        }
    }
}

/**
 * Wraps [MorpheDialogTextField] with [LocalDialogTextColor] set to onSurface
 * so it renders correctly outside a dialog context.
 */
@Composable
private fun HomeSearchTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    requestFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

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
            modifier = modifier.focusRequester(focusRequester)
        )
    }
}

/**
 * App card with optional selection overlay - shared between [DynamicAppCard] and [HiddenAppsDialog].
 *
 * Renders [AppCardLayout] with the given [content], and overlays an animated checkmark badge
 * when [isSelected] is true. Dims the card when [isMultiSelectMode] is active but this card
 * is not selected.
 */
@Composable
private fun SelectableAppCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    content: @Composable () -> Unit
) {
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "check_scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isMultiSelectMode && !isSelected) 0.55f else 1f,
        animationSpec = tween(200),
        label = "card_alpha"
    )

    Box(modifier = modifier) {
        Box(modifier = Modifier.graphicsLayer { alpha = cardAlpha }) {
            content()
        }

        // Animated checkmark badge - top-right corner
        if (checkScale > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .graphicsLayer { scaleX = checkScale; scaleY = checkScale }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single dynamic app card with horizontal swipe gestures:
 * - Swipe LEFT  → reveal hide action
 * - Swipe RIGHT → reveal patches dialog
 *
 * On first appearance plays a one-time nudge hint animation.
 */
@Composable
private fun DynamicAppCard(
    modifier: Modifier = Modifier,
    item: HomeAppItem,
    isLoading: Boolean,
    hasUpdate: Boolean,
    onAppClick: () -> Unit,
    onHide: () -> Unit,
    onShowPatches: () -> Unit,
    showGestureHint: Boolean,
    onGestureHintShown: () -> Unit,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onLongPress: () -> Unit = {},
    dragHandleModifier: Modifier? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val showHideDialog = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current

    val actionThresholdPx = with(density) { 90.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    // When entering multi-select mode snap card back to center (no swipe visible)
    LaunchedEffect(isMultiSelectMode) {
        if (isMultiSelectMode) offsetX.animateTo(0f, tween(200))
    }

    // Hint animation: nudge right then left, once (only first card)
    LaunchedEffect(showGestureHint, isLoading) {
        if (!showGestureHint || isLoading) {
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        delay(800.milliseconds)
        val nudge = with(density) { 72.dp.toPx() }
        offsetX.animateTo(nudge,  tween(500, easing = FastOutSlowInEasing))
        offsetX.animateTo(0f,     tween(400, easing = FastOutSlowInEasing))
        delay(250.milliseconds)
        offsetX.animateTo(-nudge, tween(500, easing = FastOutSlowInEasing))
        offsetX.animateTo(0f,     tween(400, easing = FastOutSlowInEasing))
        onGestureHintShown()
    }

    val hideLabel = stringResource(R.string.hide)
    val patchesLabel = stringResource(R.string.patches)
    val moveUpLabel = stringResource(R.string.accessibility_move_up)
    val moveDownLabel = stringResource(R.string.accessibility_move_down)
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val leftConfig = remember(hideLabel, errorContainer, onErrorContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.VisibilityOff,
            label = hideLabel,
            containerColor = errorContainer,
            contentColor = onErrorContainer
        )
    }
    val rightConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    Box(modifier = modifier.fillMaxWidth().semantics {
        customActions = buildList {
            add(CustomAccessibilityAction(hideLabel) { showHideDialog.value = true; true })
            add(CustomAccessibilityAction(patchesLabel) { onShowPatches(); true })
            if (onMoveUp != null) {
                add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
            }
            if (onMoveDown != null) {
                add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
            }
        }
    }) {
        SwipeableCardContainer(
            offsetX = offsetX,
            actionThresholdPx = actionThresholdPx,
            onLeftSwipe = { showHideDialog.value = true },
            onRightSwipe = onShowPatches,
            enabled = !isMultiSelectMode,
            background = { leftProgress, rightProgress ->
                SwipeBackground(
                    leftProgress = leftProgress,
                    rightProgress = rightProgress,
                    leftConfig = leftConfig,
                    rightConfig = rightConfig,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        ) {
            SelectableAppCard(
                isSelected = isSelected,
                isMultiSelectMode = isMultiSelectMode
            ) {
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
                                onClick = onAppClick,
                                hasUpdate = hasUpdate,
                                isAppDeleted = item.isDeleted,
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onLongPress()
                                }
                            )
                        } else {
                            AppButton(
                                packageName = item.packageName,
                                displayName = item.displayName,
                                packageInfo = item.packageInfo,
                                gradientColors = item.gradientColors,
                                onClick = onAppClick,
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onLongPress()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (dragHandleModifier != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        if (showHideDialog.value) {
            HideAppDialog(
                item = item,
                onDismiss = { showHideDialog.value = false },
                onHide = {
                    onHide()
                    showHideDialog.value = false
                }
            )
        }
    }
}

/**
 * Animated confirmation bar that slides up from the bottom of the card list
 * when the user is in multi-select mode.
 */
@Composable
private fun MultiSelectBar(
    selectedCount: Int,
    totalCount: Int,
    visible: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onAction: () -> Unit,
    actionIcon: ImageVector,
    actionContentDescription: String,
    actionDoneMessage: String,
    onCancel: () -> Unit,
    onEnterReorder: () -> Unit,
    onSaveOrder: () -> Unit,
    onResetOrder: () -> Unit,
    onCancelReorder: () -> Unit,
    modifier: Modifier = Modifier,
    isReorderMode: Boolean = false,
    showReorderButton: Boolean = true,
    actionColors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )
) {
    val effectiveReorderMode = isReorderMode && showReorderButton

    val context = LocalContext.current
    fun withToast(doneMessage: String, action: () -> Unit): () -> Unit = {
        context.toast(doneMessage)
        action()
    }

    val selectAllLabel = stringResource(R.string.select_all)
    val selectAllDone = stringResource(R.string.select_all_done)
    val deselectAllLabel = stringResource(R.string.deselect_all)
    val deselectAllDone = stringResource(R.string.deselect_all_done)
    val cancelLabel = stringResource(android.R.string.cancel)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val reorderListHint = stringResource(R.string.reorder_list_hint)
    val reorderDone = stringResource(R.string.reorder_done)
    val resetOrderLabel = stringResource(R.string.reset_order)
    val resetOrderDone = stringResource(R.string.reset_order_done)
    val doneLabel = stringResource(R.string.done)

    AnimatedVisibility(
        visible = visible,
        enter = MorpheAnimations.springSlideUpEnter,
        exit = MorpheAnimations.springSlideDownExit,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            tonalElevation = 4.dp
        ) {
            AnimatedContent(
                targetState = effectiveReorderMode,
                transitionSpec = MorpheAnimations.fadeCrossfade(200),
                label = "multibar_mode"
            ) { inReorder ->
                if (inReorder) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = reorderListHint,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ActionPillRow {
                            ActionPillButton(
                                onClick = withToast(resetOrderDone, onResetOrder),
                                icon = Icons.Outlined.Restore,
                                contentDescription = resetOrderLabel,
                                tooltip = resetOrderLabel
                            )
                            ActionPillButton(
                                onClick = onCancelReorder,
                                icon = Icons.Outlined.Close,
                                contentDescription = cancelLabel,
                                tooltip = cancelLabel
                            )
                            ActionPillButton(
                                onClick = withToast(reorderDone, onSaveOrder),
                                icon = Icons.Outlined.Check,
                                contentDescription = doneLabel,
                                tooltip = doneLabel
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedContent(
                            targetState = selectedCount,
                            transitionSpec = MorpheAnimations.compactCounterTransitionSpec,
                            label = "selected_count"
                        ) { count ->
                            Text(
                                text = "$count ${stringResource(R.string.selected).lowercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ActionPillRow {
                            ActionPillButton(
                                onClick = withToast(selectAllDone, onSelectAll),
                                icon = Icons.Outlined.DoneAll,
                                contentDescription = selectAllLabel,
                                tooltip = selectAllLabel,
                                enabled = selectedCount < totalCount
                            )
                            ActionPillButton(
                                onClick = withToast(deselectAllDone, onDeselectAll),
                                icon = Icons.Outlined.RemoveDone,
                                contentDescription = deselectAllLabel,
                                tooltip = deselectAllLabel,
                                enabled = selectedCount > 0
                            )
                            ActionPillButton(
                                onClick = onCancel,
                                icon = Icons.Outlined.Close,
                                contentDescription = cancelLabel,
                                tooltip = cancelLabel
                            )
                            ActionPillButton(
                                onClick = withToast(actionDoneMessage, onAction),
                                icon = actionIcon,
                                contentDescription = actionContentDescription,
                                tooltip = actionContentDescription,
                                enabled = selectedCount > 0,
                                colors = actionColors
                            )
                            if (showReorderButton) {
                                ActionPillButton(
                                    onClick = onEnterReorder,
                                    icon = Icons.Outlined.Reorder,
                                    contentDescription = reorderListLabel,
                                    tooltip = reorderListLabel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Semi-transparent background that reveals contextual action icons as the user drags the card.
 */
@Composable
private fun SwipeBackground(
    leftProgress: Float,
    rightProgress: Float,
    leftConfig: SwipeActionConfig?,
    rightConfig: SwipeActionConfig?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Left edge
        if (leftConfig != null && leftProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterEnd)
                    .background(
                        Brush.horizontalGradient(
                            0f to leftConfig.containerColor.copy(alpha = 0f),
                            1f to leftConfig.containerColor.copy(alpha = 0.85f * leftProgress)
                        )
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .graphicsLayer { alpha = leftProgress },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = leftConfig.icon,
                        contentDescription = null,
                        tint = leftConfig.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = leftConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = leftConfig.contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Right edge
        if (rightConfig != null && rightProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            0f to rightConfig.containerColor.copy(alpha = 0.85f * rightProgress),
                            1f to rightConfig.containerColor.copy(alpha = 0f)
                        )
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .graphicsLayer { alpha = rightProgress },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = rightConfig.icon,
                        contentDescription = null,
                        tint = rightConfig.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = rightConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = rightConfig.contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Shared container that handles horizontal swipe gestures and drives the
 * [SwipeBackground] reveal animation.
 */
@Composable
private fun SwipeableCardContainer(
    modifier: Modifier = Modifier,
    offsetX: Animatable<Float, AnimationVector1D>,
    actionThresholdPx: Float,
    onLeftSwipe: () -> Unit,
    onRightSwipe: () -> Unit,
    leftHaptic: Int = HapticFeedbackConstants.LONG_PRESS,
    rightHaptic: Int = HapticFeedbackConstants.VIRTUAL_KEY,
    enabled: Boolean = true,
    background: @Composable BoxScope.(leftProgress: Float, rightProgress: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Progress values for background reveal [0..1]
    val leftProgress by remember { derivedStateOf { (-offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }
    val rightProgress by remember { derivedStateOf { (offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }

    Box(modifier = modifier.fillMaxWidth()) {
        background(leftProgress, rightProgress)

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value }
                .then(
                    if (enabled) Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offsetX.value < -actionThresholdPx -> {
                                            view.performHapticFeedback(leftHaptic)
                                            offsetX.animateTo(0f, tween(200))
                                            onLeftSwipe()
                                        }
                                        offsetX.value > actionThresholdPx -> {
                                            view.performHapticFeedback(rightHaptic)
                                            offsetX.animateTo(0f, tween(200))
                                            onRightSwipe()
                                        }
                                        else -> offsetX.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val clamped = (offsetX.value + dragAmount)
                                        .coerceIn(-actionThresholdPx * 1.5f, actionThresholdPx * 1.5f)
                                    offsetX.snapTo(clamped)
                                }
                            }
                        )
                    } else Modifier
                )
        ) {
            content()
        }
    }
}

/**
 * App card for hidden apps shown in search results.
 * - Swipe LEFT  → Patches dialog
 * - Swipe RIGHT → Unhide
 *
 * Rendered at reduced opacity to signal the hidden state.
 */
@Composable
private fun HiddenSearchAppCard(
    modifier: Modifier = Modifier,
    item: HomeAppItem,
    onUnhide: () -> Unit,
    onAppClick: () -> Unit,
    onShowPatches: () -> Unit
) {
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 90.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    val patchesLabel = stringResource(R.string.patches)
    val unhideLabel = stringResource(R.string.unhide)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val leftConfig = remember(unhideLabel, tertiaryContainer, onTertiaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Visibility,
            label = unhideLabel,
            containerColor = tertiaryContainer,
            contentColor = onTertiaryContainer
        )
    }
    val rightConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = 0.6f }
    ) {
        SwipeableCardContainer(
            offsetX = offsetX,
            actionThresholdPx = actionThresholdPx,
            onLeftSwipe = onUnhide,
            onRightSwipe = onShowPatches,
            leftHaptic = HapticFeedbackConstants.LONG_PRESS,
            rightHaptic = HapticFeedbackConstants.VIRTUAL_KEY,
            background = { leftProgress, rightProgress ->
                SwipeBackground(
                    leftProgress = leftProgress,
                    rightProgress = rightProgress,
                    leftConfig = leftConfig,
                    rightConfig = rightConfig,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        ) {
            if (item.installedApp != null) {
                InstalledAppCard(
                    installedApp = item.installedApp,
                    packageInfo = item.packageInfo,
                    displayName = item.displayName,
                    gradientColors = item.gradientColors,
                    onClick = onAppClick,
                    hasUpdate = item.hasUpdate,
                    isAppDeleted = item.isDeleted,
                    onLongClick = {}
                )
            } else {
                AppButton(
                    packageName = item.packageName,
                    displayName = item.displayName,
                    packageInfo = item.packageInfo,
                    gradientColors = item.gradientColors,
                    onClick = onAppClick,
                    onLongClick = {}
                )
            }
        }
    }
}

/**
 * Dialog that shows available patches for a specific app.
 * Shown when the user swipes right on a home app card.
 * Uses the shared [PatchItemCard] component from [BundlePatchesDialog]
 * to display rich patch info with search and (multi-bundle) filter support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPatchesDialog(
    item: HomeAppItem,
    patchesByBundle: Map<Int, List<PatchInfo>>,
    bundleNames: Map<Int, String>,
    onDismiss: () -> Unit
) {
    // Flatten to a list of (bundleUid, patch).
    // Bundle ordering: bundles with at least one specific patch come first (by name),
    // then bundles with only universal patches (by name).
    // Within each bundle: specific patches first (alphabetically), universal patches last (alphabetically).
    val allPatches = remember(patchesByBundle, bundleNames) {
        patchesByBundle.entries
            .sortedWith(
                compareBy(
                    { (_, patches) -> patches.all { it.compatiblePackages == null } },
                    { (uid, _) -> bundleNames[uid] ?: uid.toString() }
                )
            )
            .flatMap { (uid, patches) ->
                val (universal, specific) = patches.partition { it.compatiblePackages == null }
                (specific.sortedBy { it.name } + universal.sortedBy { it.name })
                    .map { patch -> uid to patch }
            }
    }

    val isMultiBundle = patchesByBundle.size > 1

    // Per-bundle accent color for multi-bundle mode only.
    // Generated deterministically from uid via multiplicative hash → HSL,
    // so the same uid always produces the same color.
    // Returns null for single-bundle (no coloring needed).
    val bundleAccentColors: Map<Int, Color> = remember(patchesByBundle, isMultiBundle) {
        if (!isMultiBundle) return@remember emptyMap()
        patchesByBundle.keys.associateWith { uid ->
            val hue = ((uid.hashCode() * 2654435761L) and 0xFFFFFFFFL).toFloat() % 360f
            Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.60f)
        }
    }
    val searchQuery = remember { mutableStateOf("") }
    val selectedBundle = remember { mutableStateOf<Int?>(null) }
    val showFilterSheet = remember { mutableStateOf(false) }

    val filteredPatches = remember(allPatches, searchQuery.value, selectedBundle.value) {
        allPatches.filter { (uid, patch) ->
            val bundleMatch = selectedBundle.value == null || uid == selectedBundle.value
            val queryMatch = searchQuery.value.isBlank() ||
                    patch.name.contains(searchQuery.value, ignoreCase = true) ||
                    patch.description?.contains(searchQuery.value, ignoreCase = true) == true
            bundleMatch && queryMatch
        }
    }

    val isFiltering = searchQuery.value.isNotBlank() || selectedBundle.value != null
    val totalCount = allPatches.size

    // Pre-compute per-bundle markers once so items{} can do O(1) lookups instead of O(n) scans
    val firstPatchPerBundle: Map<Int, PatchInfo> = remember(filteredPatches) {
        buildMap {
            filteredPatches.forEach { (uid, patch) -> putIfAbsent(uid, patch) }
        }
    }
    val firstUniversalPerBundle: Map<Int, PatchInfo> = remember(filteredPatches) {
        buildMap {
            filteredPatches.forEach { (uid, patch) ->
                if (patch.compatiblePackages == null) putIfAbsent(uid, patch)
            }
        }
    }
    val bundlesWithSpecificPatches: Set<Int> = remember(filteredPatches) {
        filteredPatches
            .filter { (_, patch) -> patch.compatiblePackages != null }
            .map { it.first }
            .toSet()
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = true,
        title = null,
        compactPadding = true,
        scrollable = false,
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App header
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = LocalDialogTextColor.current,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Widgets,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                val patchCountLabel = pluralStringResource(
                                    R.plurals.patch_count,
                                    totalCount,
                                    totalCount
                                )
                                val countText = if (isFiltering)
                                    "${filteredPatches.size}/$patchCountLabel"
                                else
                                    patchCountLabel
                                AnimatedContent(
                                    targetState = countText,
                                    transitionSpec = MorpheAnimations.counterTransitionSpec,
                                    label = "app_patch_count"
                                ) { count ->
                                    Text(
                                        text = count,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search + filter row (filter button visible only for multi-bundle)
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        MorpheDialogTextField(
                            value = searchQuery.value,
                            onValueChange = { searchQuery.value = it },
                            label = { Text(stringResource(R.string.expert_mode_search)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null
                                )
                            },
                            showClearButton = true,
                            modifier = Modifier.weight(1f)
                        )

                        if (isMultiBundle) {
                            FilledTonalIconButton(
                                onClick = { showFilterSheet.value = true },
                                modifier = Modifier.padding(bottom = 4.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (selectedBundle.value != null)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (selectedBundle.value != null)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = stringResource(R.string.filter),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Active bundle filter badge + empty state
            item(key = "filter_badges_and_empty") {
                Column {
                    AnimatedVisibility(
                        visible = selectedBundle.value != null,
                        enter = MorpheAnimations.expandFadeEnter,
                        exit = MorpheAnimations.shrinkFadeExit
                    ) {
                        selectedBundle.value?.let { uid ->
                            FlowRow(
                                modifier = Modifier.padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InputChip(
                                    selected = true,
                                    onClick = { selectedBundle.value = null },
                                    label = { Text(bundleNames[uid] ?: uid.toString()) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.remove),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = filteredPatches.isEmpty(),
                        enter = MorpheAnimations.fadeScaleIn,
                        exit = MorpheAnimations.fadeScaleOut
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.expert_mode_no_results),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Patch cards
            items(
                filteredPatches,
                key = { (uid, patch) ->
                    "$uid:${patch.name}:${patch.compatiblePackages?.joinToString { it.packageName.orEmpty() }.orEmpty()}"
                }
            ) { entry ->
                val uid: Int = entry.first
                val patch: PatchInfo = entry.second
                val isUniversal = patch.compatiblePackages == null
                Column(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                        fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION_SHORT),
                        placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
                    )
                ) {
                    // Bundle section label - only for multi-bundle, at first patch of each bundle
                    if (isMultiBundle) {
                        val isFirstOfBundle = firstPatchPerBundle[uid] == patch
                        if (isFirstOfBundle) {
                            InfoBadge(
                                text = bundleNames[uid] ?: uid.toString(),
                                style = InfoBadgeStyle.Primary,
                                icon = Icons.Outlined.Layers,
                                isExpanded = true,
                                modifier = Modifier.padding(bottom = 6.dp, top = 8.dp)
                            )
                        }
                    }

                    // Universal patches divider - shown before the first universal patch of each bundle
                    val isFirstUniversalOfBundle = isUniversal && firstUniversalPerBundle[uid] == patch
                    if (isFirstUniversalOfBundle) {
                        val hasSpecificAbove = uid in bundlesWithSpecificPatches
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (hasSpecificAbove) 8.dp else 0.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.expert_mode_universal_patches),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                    }

                    PatchItemCard(
                        patch = patch,
                        saveStateKey = "app_patches_${item.packageName}_$uid",
                        accentColor = bundleAccentColors[uid],
                    )
                }
            }
        }
    }

    // Bundle filter bottom sheet (multi-bundle only)
    if (showFilterSheet.value && isMultiBundle) {
        MorpheBottomSheet(
            onDismissRequest = { showFilterSheet.value = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.filter),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All" chip
                    FilterChip(
                        selected = selectedBundle.value == null,
                        onClick = { selectedBundle.value = null },
                        label = { Text(stringResource(R.string.all)) },
                        leadingIcon = if (selectedBundle.value == null) {
                            { Icon(Icons.Outlined.DoneAll, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    // Per-bundle chips
                    bundleNames.entries
                        .sortedBy { it.value }
                        .forEach { (uid, name) ->
                            val isSelected = uid == selectedBundle.value
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedBundle.value = if (isSelected) null else uid
                                    showFilterSheet.value = false
                                },
                                label = { Text(name) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Outlined.Done, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                }
            }
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
                )
            }

            // Explanation text
            Text(
                text = stringResource(R.string.home_app_hide_message),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Dialog listing all hidden apps.
 *
 * Swipe gestures (disabled in multi-select mode):
 * - Swipe LEFT  → Patches dialog
 * - Swipe RIGHT → Unhide
 *
 * Long-press enters multi-select; bulk unhide via footer button.
 */
@Composable
internal fun HiddenAppsDialog(
    hiddenAppItems: List<HomeAppItem>,
    onUnhide: (String) -> Unit,
    onUnhideMultiple: (Set<String>) -> Unit = {},
    onShowPatches: (HomeAppItem) -> Unit,
    onDismiss: () -> Unit
) {
    val itemSpacing = rememberWindowSize().itemSpacing
    val isMultiSelectMode = remember { mutableStateOf(false) }
    val selectedPackages = remember { mutableStateOf(emptySet<String>()) }

    // Sync selection with current item list; exit mode if no items remain
    LaunchedEffect(hiddenAppItems) {
        val filtered = selectedPackages.value.filter { pkg ->
            hiddenAppItems.any { it.packageName == pkg }
        }.toSet()
        selectedPackages.value = filtered
        if (filtered.isEmpty()) isMultiSelectMode.value = false
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 90.dp.toPx() }

    val patchesLabel = stringResource(R.string.patches)
    val unhideLabel = stringResource(R.string.unhide)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val leftConfig = remember(unhideLabel, tertiaryContainer, onTertiaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Visibility,
            label = unhideLabel,
            containerColor = tertiaryContainer,
            contentColor = onTertiaryContainer
        )
    }
    val rightConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    MorpheDialog(
        onDismissRequest = {
            if (isMultiSelectMode.value) {
                isMultiSelectMode.value = false
                selectedPackages.value = emptySet()
            } else {
                onDismiss()
            }
        },
        dismissOnClickOutside = !isMultiSelectMode.value,
        title = stringResource(R.string.home_app_hidden_apps_title),
        footer = {
            if (isMultiSelectMode.value) {
                MultiSelectBar(
                    selectedCount = selectedPackages.value.size,
                    totalCount = hiddenAppItems.size,
                    visible = true,
                    showReorderButton = false,
                    onSelectAll = {
                        selectedPackages.value = hiddenAppItems.map { it.packageName }.toSet()
                    },
                    onDeselectAll = { selectedPackages.value = emptySet() },
                    onAction = {
                        onUnhideMultiple(selectedPackages.value)
                        isMultiSelectMode.value = false
                        selectedPackages.value = emptySet()
                    },
                    actionIcon = Icons.Outlined.Visibility,
                    actionContentDescription = stringResource(R.string.unhide),
                    actionDoneMessage = stringResource(R.string.unhide_done),
                    actionColors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    onCancel = {
                        isMultiSelectMode.value = false
                        selectedPackages.value = emptySet()
                    },
                    onEnterReorder = {},
                    onSaveOrder = {},
                    onResetOrder = {},
                    onCancelReorder = {}
                )
            } else {
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        compactPadding = true,
        scrollable = false
    ) {
        if (hiddenAppItems.isEmpty()) {
            MorpheEmptyState(
                icon = Icons.Outlined.Visibility,
                title = stringResource(R.string.home_app_no_hidden)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                items(
                    items = hiddenAppItems,
                    key = { it.packageName }
                ) { item ->
                    val isSelected = item.packageName in selectedPackages.value
                    val offsetX = remember(item.packageName) { Animatable(0f) }

                    // Snap card back when entering multi-select
                    LaunchedEffect(isMultiSelectMode.value) {
                        if (isMultiSelectMode.value) offsetX.animateTo(0f, tween(200))
                    }

                    SelectableAppCard(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                            fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION_SHORT),
                            placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
                        ),
                        isSelected = isSelected,
                        isMultiSelectMode = isMultiSelectMode.value
                    ) {
                        SwipeableCardContainer(
                            offsetX = offsetX,
                            actionThresholdPx = actionThresholdPx,
                            onLeftSwipe = { onUnhide(item.packageName) },
                            onRightSwipe = { onShowPatches(item) },
                            leftHaptic = HapticFeedbackConstants.LONG_PRESS,
                            rightHaptic = HapticFeedbackConstants.VIRTUAL_KEY,
                            enabled = !isMultiSelectMode.value,
                            background = { leftProgress, rightProgress ->
                                SwipeBackground(
                                    leftProgress = leftProgress,
                                    rightProgress = rightProgress,
                                    leftConfig = leftConfig,
                                    rightConfig = rightConfig,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(24.dp))
                                )
                            }
                        ) {
                            AppCardLayout(
                                gradientColors = item.gradientColors,
                                enabled = true,
                                onClick = {
                                    if (isMultiSelectMode.value) {
                                        selectedPackages.value = if (isSelected)
                                            selectedPackages.value - item.packageName
                                        else
                                            selectedPackages.value + item.packageName
                                    } else {
                                        onUnhide(item.packageName)
                                    }
                                },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    isMultiSelectMode.value = true
                                    selectedPackages.value = if (isSelected)
                                        selectedPackages.value - item.packageName
                                    else
                                        selectedPackages.value + item.packageName
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppCardContent(
                                    packageName = item.packageName,
                                    packageInfo = item.packageInfo,
                                    displayName = item.displayName,
                                    subtitle = if (isMultiSelectMode.value) null
                                    else stringResource(R.string.home_app_hidden_apps_hint),
                                    gradientColors = item.gradientColors,
                                )
                            }
                        }
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
 */
@Composable
private fun RowScope.AppCardContent(
    packageName: String,
    packageInfo: PackageInfo?,
    displayName: String,
    subtitle: String?,
    gradientColors: List<Color>
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
        preferredSource = AppDataSource.PATCHED_APK,
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
 * Frosted-glass chip for use on gradient card backgrounds.
 * Uses white semi-transparent fill so it reads correctly regardless of
 * the card's accent color or the user's dynamic theme.
 */
@Composable
private fun GlassChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.20f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
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

        // App info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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

            // Version + deleted status + inline update chip
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = version,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    GlassChip(
                        text = stringResource(R.string.uninstalled),
                        icon = Icons.Outlined.DeleteOutline
                    )
                }

                AnimatedVisibility(
                    visible = hasUpdate && !isAppDeleted,
                    enter = MorpheAnimations.expandHorizFadeIn,
                    exit = MorpheAnimations.shrinkHorizFadeOut
                ) {
                    GlassChip(
                        text = stringResource(R.string.update),
                        icon = Icons.Outlined.ArrowUpward
                    )
                }
            }
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
    HomeGlassPillButton(
        onClick = onClick,
        modifier = modifier.padding(bottom = 12.dp),
        text = stringResource(R.string.home_other_apps)
    )
}

/**
 * Shared frosted-glass pill button used by [OtherAppsSection] and [ShowHiddenAppsButton].
 *
 * Renders a rounded pill with semi-transparent surface background, border, press-scale
 * animation, and haptic feedback. Content is either icon+text or text-only.
 */
@Composable
private fun HomeGlassPillButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
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
            stiffness = Spring.StiffnessMedium
        ),
        label = "pill_press_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; clip = true }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = backgroundAlpha))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)),
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
@Composable
private fun AppCardLayout(
    modifier: Modifier = Modifier,
    gradientColors: List<Color>,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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
                .drawBehind {
                    drawDiagonalShimmer(
                        progress = (shimmerOffset + 1f) / 3f,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                }
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

/**
 * Landscape sidebar panel: nav items (Search / Sources / Settings) centered vertically.
 */
@Composable
private fun HomeSidebarPanel(
    showSearchButton: Boolean,
    searchActive: Boolean,
    isExpertModeEnabled: Boolean,
    onSearchClick: () -> Unit,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSourcesPositioned: ((Rect) -> Unit)? = null,
    onSettingsPositioned: ((Rect) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        if (showSearchButton) {
            HomeSidebarNavItem(
                icon = if (searchActive) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                label = stringResource(R.string.home_search_apps),
                isSelected = searchActive,
                onClick = onSearchClick
            )
        }
        HomeSidebarNavItem(
            icon = Icons.Outlined.Source,
            label = stringResource(R.string.sources),
            isSelected = false,
            onClick = onBundlesClick,
            modifier = Modifier.then(
                if (onSourcesPositioned != null) Modifier.onGloballyPositioned { coords ->
                    onSourcesPositioned(coords.boundsInWindow())
                } else Modifier
            )
        )
        HomeSidebarNavItem(
            icon = if (isExpertModeEnabled) Icons.Outlined.Engineering else Icons.Outlined.Settings,
            label = stringResource(R.string.settings),
            isSelected = false,
            onClick = onSettingsClick,
            modifier = Modifier.then(
                if (onSettingsPositioned != null) Modifier.onGloballyPositioned { coords ->
                    onSettingsPositioned(coords.boundsInWindow())
                } else Modifier
            )
        )
    }
}

/**
 * Single sidebar nav item: 52dp tall, 16dp rounded corners, animated colors.
 */
@Composable
private fun HomeSidebarNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "sidebarNavItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sidebarNavItemFg"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { role = Role.Button; selected = isSelected },
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
