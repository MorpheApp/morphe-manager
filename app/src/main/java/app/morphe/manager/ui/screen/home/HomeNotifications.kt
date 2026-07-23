/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.MorpheAnimations
import app.morphe.manager.ui.screen.shared.MorpheIcon
import app.morphe.manager.ui.viewmodel.BundleUpdateStatus

/** Visibility flag paired with the tap callback for a single [AlertSnackbar] slot. */
@Immutable
data class AlertState(val visible: Boolean, val onShow: () -> Unit)

/** Transient state driving the bundle-update progress snackbar. */
@Immutable
data class BundleUpdateState(
    val visible: Boolean,
    val status: BundleUpdateStatus,
    val progress: PatchBundleRepository.BundleUpdateProgress?
)

/** Aggregate of all notification-strip inputs, grouped by alert. */
@Immutable
data class HomeNotificationsUi(
    val managerUpdate: AlertState,
    val blockedSources: AlertState,
    val metadataErrors: AlertState,
    val meteredSkipped: AlertState,
    val bundleUpdate: BundleUpdateState
)

/**
 * Section 1: Unified notifications overlay component.
 * Handles both manager update and bundle update notifications.
 */
@Composable
fun NotificationsOverlay(
    notifications: HomeNotificationsUi,
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
            // Blocked source alert takes priority and cannot be dismissed while the block persists
            AlertSnackbar(
                visible = notifications.blockedSources.visible,
                level = AlertLevel.Error,
                icon = Icons.Outlined.Block,
                title = stringResource(R.string.home_blocked_source_title),
                subtitle = stringResource(R.string.home_blocked_source_subtitle),
                onShowDetails = notifications.blockedSources.onShow,
                swipeEnabled = false,
                modifier = Modifier.fillMaxWidth()
            )

            AlertSnackbar(
                visible = notifications.meteredSkipped.visible,
                level = AlertLevel.Warning,
                icon = Icons.Outlined.SignalCellularAlt,
                title = stringResource(R.string.home_metered_skipped_title),
                subtitle = stringResource(R.string.home_metered_skipped_subtitle),
                onShowDetails = notifications.meteredSkipped.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            AlertSnackbar(
                visible = notifications.metadataErrors.visible,
                level = AlertLevel.Warning,
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.home_metadata_errors_title),
                subtitle = stringResource(R.string.home_metadata_errors_subtitle),
                onShowDetails = notifications.metadataErrors.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            AlertSnackbar(
                visible = notifications.managerUpdate.visible,
                level = AlertLevel.Info,
                icon = Icons.Outlined.Update,
                title = stringResource(R.string.home_update_available),
                subtitle = stringResource(R.string.home_update_available_subtitle),
                onShowDetails = notifications.managerUpdate.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            BundleUpdateSnackbar(
                visible = notifications.bundleUpdate.visible,
                status = notifications.bundleUpdate.status,
                progress = notifications.bundleUpdate.progress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Semantic level of an [AlertSnackbar], resolved into a Material color pair at call time. */
enum class AlertLevel { Info, Warning, Error, Success }

private data class AlertColorPair(val container: Color, val content: Color)

@Composable
private fun alertColorsFor(level: AlertLevel): AlertColorPair = when (level) {
    AlertLevel.Info -> AlertColorPair(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )
    AlertLevel.Warning -> AlertColorPair(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer
    )
    AlertLevel.Error -> AlertColorPair(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer
    )
    AlertLevel.Success -> AlertColorPair(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
}

/**
 * Dismissible card-style alert used for the home-screen notification strip. Swipe-dismiss clears
 * the alert for the current session; it reappears next launch while [visible] stays true.
 */
@Composable
fun AlertSnackbar(
    visible: Boolean,
    level: AlertLevel,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true
) {
    val colors = alertColorsFor(level)
    val dismissed = remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) dismissed.value = false }

    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )
    LaunchedEffect(swipeState.currentValue) {
        if (swipeEnabled && swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
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
            enableDismissFromStartToEnd = swipeEnabled,
            enableDismissFromEndToStart = swipeEnabled
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = onShowDetails,
                colors = CardDefaults.cardColors(containerColor = colors.container),
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
                    MorpheIcon(icon = icon, tint = colors.content)

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.content
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.content.copy(alpha = 0.8f)
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
