/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.batch.BatchRunState
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BundleUpdateStatus
import app.morphe.manager.util.formatMegabytes

/** Visibility flag paired with the tap callback for a single [AlertSnackbar] slot. */
@Immutable
data class AlertState(val visible: Boolean, val onShow: () -> Unit)

/**
 * The apps whose patches have moved on, and the tap that queues them. Shown from one app up,
 * which is also when the cards themselves start showing their update badge.
 */
@Immutable
data class RepatchAlertState(val count: Int, val visible: Boolean, val onShow: () -> Unit)

/** The batch queue while it patches or holds results nobody closed yet, and the tap back to it. */
@Immutable
data class BatchQueueAlertState(val run: BatchRunState?, val onShow: () -> Unit)

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
    val outdatedManager: AlertState,
    val heldBackSources: AlertState,
    val blockedSources: AlertState,
    val metadataErrors: AlertState,
    val meteredSkipped: AlertState,
    val repatchAvailable: RepatchAlertState,
    val batchQueue: BatchQueueAlertState,
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
                tone = SemanticTone.Error,
                icon = Icons.Outlined.Block,
                title = stringResource(R.string.home_blocked_source_title),
                subtitle = stringResource(R.string.home_blocked_source_subtitle),
                onShowDetails = notifications.blockedSources.onShow,
                swipeEnabled = false,
                modifier = Modifier.fillMaxWidth()
            )

            // The one alert raised by something that already went wrong rather than something
            // that might, so it sits above the rest
            AlertSnackbar(
                visible = notifications.heldBackSources.visible,
                tone = SemanticTone.Error,
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.home_held_back_title),
                subtitle = stringResource(R.string.home_held_back_subtitle),
                onShowDetails = notifications.heldBackSources.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            // A source built for a newer patcher stays unusable until the app itself is updated
            AlertSnackbar(
                visible = notifications.outdatedManager.visible,
                tone = SemanticTone.Error,
                icon = Icons.Outlined.SystemUpdate,
                title = stringResource(R.string.home_outdated_manager_title),
                subtitle = stringResource(R.string.home_outdated_manager_subtitle),
                onShowDetails = notifications.outdatedManager.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            AlertSnackbar(
                visible = notifications.meteredSkipped.visible,
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.SignalCellularAlt,
                title = stringResource(R.string.home_metered_skipped_title),
                subtitle = stringResource(R.string.home_metered_skipped_subtitle),
                onShowDetails = notifications.meteredSkipped.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            AlertSnackbar(
                visible = notifications.metadataErrors.visible,
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.home_metadata_errors_title),
                subtitle = stringResource(R.string.home_metadata_errors_subtitle),
                onShowDetails = notifications.metadataErrors.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            AlertSnackbar(
                visible = notifications.managerUpdate.visible,
                tone = SemanticTone.Primary,
                icon = Icons.Outlined.Update,
                title = stringResource(R.string.home_update_available),
                subtitle = stringResource(R.string.home_update_available_subtitle),
                onShowDetails = notifications.managerUpdate.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            val batchRun = notifications.batchQueue.run
            val batchRunning = batchRun?.isActive == true
            val batchFinished = batchRun?.hasOutcome == true

            // Once its screen is gone this is the only way back to a running queue, which holds
            // single-app patching back meanwhile, so it cannot be swiped away
            AlertSnackbar(
                visible = batchRunning,
                tone = SemanticTone.Primary,
                icon = Icons.Outlined.AutoFixHigh,
                progress = batchRun?.let { run -> if (run.total > 0) run.processed.toFloat() / run.total else 0f },
                title = stringResource(R.string.batch_patch_title),
                subtitle = stringResource(
                    R.string.batch_patch_progress_counter,
                    (batchRun?.processed ?: 0).toString(),
                    (batchRun?.total ?: 0).toString()
                ),
                onShowDetails = notifications.batchQueue.onShow,
                swipeEnabled = false,
                modifier = Modifier.fillMaxWidth()
            )

            // The patched APKs of a finished run are only installable from its summary
            val batchSucceeded = (batchRun?.succeeded ?: 0) > 0
            AlertSnackbar(
                visible = batchFinished,
                tone = if (batchSucceeded) SemanticTone.Success else SemanticTone.Error,
                icon = if (batchSucceeded) Icons.Outlined.Check else Icons.Outlined.ErrorOutline,
                title = stringResource(
                    if (batchSucceeded) R.string.patcher_complete_title else R.string.patcher_failed_title
                ),
                subtitle = stringResource(
                    R.string.batch_patch_summary,
                    (batchRun?.succeeded ?: 0).toString(),
                    (batchRun?.failed ?: 0).toString(),
                    (batchRun?.skipped ?: 0).toString()
                ),
                onShowDetails = notifications.batchQueue.onShow,
                modifier = Modifier.fillMaxWidth()
            )

            // The badges on the cards say the same thing one app at a time. This says it once
            // and queues every one of them, which is the part that is otherwise several taps.
            // While a queue is patching or awaiting review, its own alert stands in for this one
            AlertSnackbar(
                visible = notifications.repatchAvailable.visible &&
                        notifications.repatchAvailable.count > 0 &&
                        !batchRunning && !batchFinished,
                tone = SemanticTone.Primary,
                icon = Icons.Outlined.AutoFixHigh,
                title = pluralStringResource(
                    R.plurals.repatch_available_count,
                    notifications.repatchAvailable.count,
                    notifications.repatchAvailable.count.toString()
                ),
                subtitle = stringResource(R.string.home_repatch_available_subtitle),
                onShowDetails = notifications.repatchAvailable.onShow,
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

/**
 * Dismissible alert of the home screen's notification strip. Swipe-dismiss clears the alert for
 * the current session; it reappears next launch while [visible] stays true.
 *
 * @param progress Share of the work done, which puts [icon] inside a ring filling up with it, for
 *   work still in progress.
 */
@Composable
fun AlertSnackbar(
    visible: Boolean,
    tone: SemanticTone,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    progress: Float? = null
) {
    DismissibleAlert(visible = visible, swipeEnabled = swipeEnabled, modifier = modifier) {
        StatusCard(
            tone = tone,
            title = title,
            subtitle = subtitle,
            onClick = onShowDetails,
            leading = {
                if (progress != null) {
                    ProgressRing(progress = progress) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    StatusDisc(icon = icon, tone = tone)
                }
            }
        )
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
    // Swipe only for terminal states, so an update in progress cannot be dismissed. A new status
    // brings the alert back, which is how a new update cycle shows up after the last was swiped
    DismissibleAlert(
        visible = visible,
        swipeEnabled = status != BundleUpdateStatus.Updating,
        resetKey = status,
        modifier = modifier
    ) {
        BundleUpdateCard(status = status, progress = progress)
    }
}

/** The card of the bundle update, from its progress while it runs to how it ended. */
@Composable
private fun BundleUpdateCard(
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?
) {
    val sourcesByUid = rememberSourcesByUid()

    val downloadFraction = progress?.bytesTotal
        ?.takeIf { it > 0L }
        ?.let { progress.bytesRead.toFloat() / it }
    val isDownloading = progress?.phase == PatchBundleRepository.BundleUpdatePhase.Downloading &&
            downloadFraction != null && downloadFraction > 0f
    val countFraction = progress
        ?.takeIf { it.total > 1 && it.completed > 0 }
        ?.let { it.completed.toFloat() / it.total }
    // Null until there is a share to show, which spins the ring rather than parking it at zero
    val ringProgress = if (isDownloading) downloadFraction else countFraction

    val tone = when (status) {
        BundleUpdateStatus.Updating -> SemanticTone.Neutral
        BundleUpdateStatus.Success -> SemanticTone.Success
        BundleUpdateStatus.Warning -> SemanticTone.Warning
        BundleUpdateStatus.Error -> SemanticTone.Error
    }

    val title = stringResource(
        when (status) {
            BundleUpdateStatus.Updating -> R.string.home_updating_sources
            BundleUpdateStatus.Success -> R.string.home_update_success
            BundleUpdateStatus.Warning -> R.string.home_update_skipped_metered
            BundleUpdateStatus.Error -> R.string.home_update_error
        }
    )

    // One line names the source at work and how much of it has arrived, while the badge carries
    // how far along the whole update is: its share, or how many sources are done when several run
    val activeName = progress?.activeNames?.firstOrNull() ?: progress?.currentBundleName
    val arrived = progress?.takeIf { isDownloading }?.let {
        stringResource(
            R.string.manager_update_progress_size,
            formatMegabytes(it.bytesRead),
            formatMegabytes(it.bytesTotal ?: 0L)
        )
    }
    val subtitle = when (status) {
        BundleUpdateStatus.Updating -> listOfNotNull(activeName, arrived).joinToString(DETAIL_SEPARATOR)
        BundleUpdateStatus.Success -> stringResource(R.string.home_update_success_subtitle)
        BundleUpdateStatus.Warning -> stringResource(R.string.home_update_skipped_metered_subtitle)
        BundleUpdateStatus.Error -> stringResource(R.string.home_update_error_subtitle)
    }
    val badge = when {
        status != BundleUpdateStatus.Updating || progress == null -> null
        progress.total > 1 -> stringResource(R.string.home_update_bundle_count, progress.completed, progress.total)
        isDownloading -> stringResource(R.string.patcher_percentage, (downloadFraction * 100).toInt().toString())
        else -> null
    }
    val activeSource = progress?.activeUids?.firstOrNull()?.let(sourcesByUid::get)

    StatusCard(
        tone = tone,
        title = title,
        subtitle = subtitle,
        badge = badge,
        leading = {
            Crossfade(targetState = status, label = "bundleUpdateLeading") { shown ->
                when (shown) {
                    BundleUpdateStatus.Updating -> ProgressRing(progress = ringProgress) {
                        if (activeSource != null) {
                            BundleIcon(bundle = activeSource, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    BundleUpdateStatus.Success -> StatusDisc(Icons.Outlined.Check, SemanticTone.Success)
                    BundleUpdateStatus.Warning -> StatusDisc(Icons.Outlined.SignalCellularAlt, SemanticTone.Warning)
                    BundleUpdateStatus.Error -> StatusDisc(Icons.Outlined.Warning, SemanticTone.Error)
                }
            }
        }
    )
}

/**
 * Shows [content] while [visible], sliding it in and out, and lets a swipe put it away for the rest
 * of the session while [swipeEnabled]. A change of [resetKey] while visible brings a swiped alert
 * back.
 */
@Composable
private fun DismissibleAlert(
    visible: Boolean,
    swipeEnabled: Boolean,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    content: @Composable () -> Unit
) {
    val dismissed = remember { mutableStateOf(false) }
    LaunchedEffect(visible, resetKey) { if (visible) dismissed.value = false }

    AnimatedVisibility(
        visible = visible && !dismissed.value,
        enter = Animations.slideUpFadeEnter,
        exit = Animations.slideUpFadeExit,
        modifier = modifier
    ) {
        // Made anew each time the alert shows, so one swiped away earlier does not come back
        // still pushed off to the side
        val swipeState = rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.4f }
        )
        LaunchedEffect(swipeState.currentValue) {
            if (swipeEnabled && swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
                dismissed.value = true
            }
        }

        SwipeToDismissBox(
            state = swipeState,
            backgroundContent = {},
            enableDismissFromStartToEnd = swipeEnabled,
            enableDismissFromEndToStart = swipeEnabled
        ) {
            content()
        }
    }
}

/**
 * Card of one alert in the notification strip: [leading] beside its [title] over a [subtitle],
 * with an optional [badge], and a chevron when a tap opens more. Laid on the app's own card
 * surface, lightly tinted with [tone], and lifted just enough to stand apart from what it floats over.
 */
@Composable
private fun StatusCard(
    tone: SemanticTone,
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    badge: String? = null,
    onClick: (() -> Unit)? = null
) {
    val surface = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    val neutral = tone == SemanticTone.Neutral
    val containerColor by animateColorAsState(
        targetValue = if (neutral) surface else tone.container.copy(alpha = 0.35f).compositeOver(surface),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusCardContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (neutral) MaterialTheme.colorScheme.outlineVariant else tone.accent.copy(alpha = 0.35f),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusCardBorder"
    )

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding),
        shape = RoundedCornerShape(Defaults.CardCornerRadius),
        color = containerColor,
        border = CardBorder.of(borderColor),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .animateContentSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CrossfadeText(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    // Eased from one text to the next, but keyed by what comes before its details,
                    // so the figures of a running update tick over in place
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = Animations.fadeCrossfade(200),
                        contentKey = { it.substringBefore(DETAIL_SEPARATOR) },
                        label = "statusCardSubtitle"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (badge != null) {
                StatusBadge(text = badge, tone = if (neutral) SemanticTone.Primary else tone)
            }
            if (onClick != null) {
                ForwardChevronIcon(size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Leading icon of a [StatusCard] whose work is done, on a disc in [tone]. */
@Composable
private fun StatusDisc(icon: ImageVector, tone: SemanticTone) {
    StatusCircleIcon(
        icon = icon,
        containerColor = tone.container,
        contentColor = tone.content,
        size = StatusLeadingSize
    )
}

/**
 * Leading of a [StatusCard] whose work is still running: [content] inside a ring filling with
 * [progress], or spinning while there is no share to show yet.
 */
@Composable
private fun ProgressRing(progress: Float?, content: @Composable () -> Unit) {
    // Eased, since the share arrives in steps as the download reports back
    val animated by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusRingProgress"
    )

    Box(
        modifier = Modifier.size(StatusLeadingSize),
        contentAlignment = Alignment.Center
    ) {
        WavyProgressRing(
            progress = progress?.let { { animated } },
            wavelength = 10.dp,
            accentColor = null,
            strokeWidth = 3.5.dp,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}

private val StatusLeadingSize = 44.dp

/** Joins the parts of a [StatusCard]'s subtitle, a name and the details about it. */
private const val DETAIL_SEPARATOR = " · "
