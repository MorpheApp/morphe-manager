/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.UpdateViewModel
import app.morphe.manager.util.formatMegabytes
import app.morphe.manager.util.isolateLtr

private val ProgressBarHeight = 8.dp
private val SuccessIconContainerSize = 80.dp
private val SuccessIconSize = 40.dp

/**
 * The distinct bodies the update dialog can show. States that share a body map to the same
 * entry so switching between them does not restart the crossfade.
 */
private enum class UpdateDialogContent {
    DetailsLoading,
    DetailsUnavailable,
    Details,
    Downloading,
    Installing,
    Failed,
    Success
}

/** Resolves the body to show, including which variant of the details view applies. */
private fun updateDialogContentOf(updateViewModel: UpdateViewModel): UpdateDialogContent =
    when (updateViewModel.state) {
        UpdateViewModel.State.CAN_DOWNLOAD, UpdateViewModel.State.CAN_INSTALL -> when {
            // A banner can outlive the release it points at, and a check can fail outright,
            // so name the situation rather than wait on data that is not coming
            updateViewModel.releaseInfo == null && !updateViewModel.isCheckingForUpdate ->
                UpdateDialogContent.DetailsUnavailable

            updateViewModel.missedChangelogEntries == null -> UpdateDialogContent.DetailsLoading
            else -> UpdateDialogContent.Details
        }

        UpdateViewModel.State.DOWNLOADING -> UpdateDialogContent.Downloading
        UpdateViewModel.State.INSTALLING -> UpdateDialogContent.Installing
        UpdateViewModel.State.FAILED -> UpdateDialogContent.Failed
        UpdateViewModel.State.SUCCESS -> UpdateDialogContent.Success
    }

/**
 * Update details dialog with download and install functionality.
 */
@Composable
fun ManagerUpdateDetailsDialog(
    onDismiss: () -> Unit,
    updateViewModel: UpdateViewModel
) {
    val state = updateViewModel.state

    // Reset state when dialog is opened if installation was canceled
    // This handles the case when user canceled the system install dialog
    DisposableEffect(state) {
        // When dialog opens, if we're in INSTALLING state but no actual installation is running,
        // reset to CAN_INSTALL (the file is already downloaded)
        if (state == UpdateViewModel.State.INSTALLING) {
            updateViewModel.resetIfInstallCancelled()
        }

        onDispose {
            // When dialog closes during download, notify dismiss
            if (state == UpdateViewModel.State.DOWNLOADING) {
                onDismiss()
            }
        }
    }

    // Collapse "Show older releases" when the dialog closes so it reopens fresh next time
    DisposableEffect(Unit) {
        onDispose { updateViewModel.resetOlderManagerEntries() }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(state.title),
        scrollable = false,
        footer = {
            AnimatedContent(
                targetState = state,
                transitionSpec = Animations.fadeCrossfade(),
                modifier = Modifier.fillMaxWidth(),
                label = "updateFooter"
            ) { footerState ->
                UpdateDialogFooter(
                    state = footerState,
                    updateViewModel = updateViewModel,
                    onDismiss = onDismiss
                )
            }
        }
    ) {
        AnimatedContent(
            targetState = updateDialogContentOf(updateViewModel),
            transitionSpec = Animations.fadeCrossfade(),
            modifier = Modifier.fillMaxWidth(),
            label = "updateContent"
        ) { content ->
            when (content) {
                UpdateDialogContent.DetailsLoading -> ChangelogSectionLoading()

                UpdateDialogContent.DetailsUnavailable -> Notice(
                    icon = Icons.Outlined.HourglassEmpty,
                    text = stringResource(R.string.manager_update_not_ready),
                    tone = SemanticTone.Warning
                )

                UpdateDialogContent.Details -> UpdateDetailsContent(updateViewModel)

                UpdateDialogContent.Downloading -> DownloadProgressCard(
                    version = updateViewModel.releaseInfo?.version,
                    downloadedSize = updateViewModel.downloadedSize,
                    totalSize = updateViewModel.totalSize,
                    progress = updateViewModel.downloadProgress
                )

                // The dialog title already reads "Installing update", so the logo carries it
                // as a description instead of repeating it on screen
                UpdateDialogContent.Installing -> PulsingLogoIndicator(
                    contentDescription = stringResource(R.string.installing_manager_update)
                )

                UpdateDialogContent.Failed -> InstallFailureContent(updateViewModel.installError)

                UpdateDialogContent.Success -> UpdateCompletedContent(
                    version = updateViewModel.releaseInfo?.version
                )
            }
        }
    }

    Overlay(visible = updateViewModel.isLoadingOlderEntries) {
        PulsingLogoWithCaption(caption = stringResource(R.string.loading_older_releases))
    }

    // Internet check dialog
    if (updateViewModel.showInternetCheckDialog) {
        AppDialog(
            onDismissRequest = { updateViewModel.showInternetCheckDialog = false },
            title = stringResource(R.string.download_update_confirmation),
            footer = {
                AppDialogButtonRow(
                    primaryText = stringResource(R.string.download),
                    onPrimaryClick = {
                        updateViewModel.showInternetCheckDialog = false
                        updateViewModel.downloadUpdate(ignoreInternetCheck = true)
                    },
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = { updateViewModel.showInternetCheckDialog = false }
                )
            }
        ) {
            Notice(
                icon = Icons.Outlined.Warning,
                text = stringResource(R.string.download_confirmation_metered),
                tone = SemanticTone.Warning
            )
        }
    }
}

/** Dialog actions, one set per [UpdateViewModel.State]. */
@Composable
private fun UpdateDialogFooter(
    state: UpdateViewModel.State,
    updateViewModel: UpdateViewModel,
    onDismiss: () -> Unit
) {
    val releaseInfo = updateViewModel.releaseInfo

    when (state) {
        UpdateViewModel.State.CAN_DOWNLOAD -> {
            AppDialogButtonColumn {
                AppDialogButton(
                    text = stringResource(
                        if (updateViewModel.canResumeDownload) R.string.resume_download
                        else R.string.download
                    ),
                    onClick = { updateViewModel.downloadUpdate() },
                    icon = Icons.Outlined.Download,
                    // Nothing to download until the check resolves an actual release
                    enabled = releaseInfo != null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Offered once the check has settled on nothing, which is recoverable
                // on its own a moment later
                if (releaseInfo == null && !updateViewModel.isCheckingForUpdate) {
                    AppDialogOutlinedButton(
                        text = stringResource(R.string.retry),
                        onClick = { updateViewModel.retryUpdateCheck() },
                        icon = Icons.Outlined.Refresh,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ChangelogButton(
                    pageUrl = releaseInfo?.pageUrl,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        UpdateViewModel.State.DOWNLOADING -> {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }

        UpdateViewModel.State.CAN_INSTALL -> {
            AppDialogButtonColumn {
                AppDialogButton(
                    text = stringResource(R.string.install),
                    onClick = { updateViewModel.installUpdate() },
                    icon = Icons.Outlined.InstallMobile,
                    modifier = Modifier.fillMaxWidth()
                )

                ChangelogButton(
                    pageUrl = releaseInfo?.pageUrl,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        UpdateViewModel.State.INSTALLING -> {
            // No cancel button during installation - can't cancel system dialog
            // User can close our dialog, but install will continue
        }

        UpdateViewModel.State.FAILED -> {
            AppDialogButtonColumn {
                if (updateViewModel.canResumeDownload) {
                    // Download failed/canceled - offer to resume
                    AppDialogButton(
                        text = stringResource(R.string.resume_download),
                        onClick = { updateViewModel.downloadUpdate() },
                        icon = Icons.Outlined.Download,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Download completed but install failed - offer to retry install
                    AppDialogButton(
                        text = stringResource(R.string.install),
                        onClick = { updateViewModel.installUpdate() },
                        icon = Icons.Outlined.InstallMobile,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ChangelogButton(
                    pageUrl = releaseInfo?.pageUrl,
                    modifier = Modifier.fillMaxWidth()
                )

                AppDialogOutlinedButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        UpdateViewModel.State.SUCCESS -> {
            AppDialogButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Changelog for everything the user is about to install, with its own scrollbar so the list
 * stays lazy while the surrounding dialog does not scroll.
 */
@Composable
private fun UpdateDetailsContent(updateViewModel: UpdateViewModel) {
    val listState = rememberLazyListState()
    val entries = updateViewModel.missedChangelogEntries.orEmpty()

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            changelogEntryItems(
                entries = entries,
                keyPrefix = "missed",
                headerIcon = Icons.Outlined.NewReleases
            )
            changelogOlderItems(
                entries = updateViewModel.olderManagerEntries,
                isLoading = updateViewModel.isLoadingOlderEntries,
                onExpand = { updateViewModel.loadOlderManagerEntries() }
            )
        }

        ListScrollbar(
            listState = listState,
            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
        )

        ScrollToTopButton(
            listState = listState,
            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
        )
    }
}

/**
 * Download progress with an animated bar.
 *
 * The total size is unknown until the first progress callback, so a resumed download shows an
 * indeterminate bar rather than one pinned at zero while bytes are already arriving.
 */
@Composable
private fun DownloadProgressCard(
    version: String?,
    downloadedSize: Long,
    totalSize: Long,
    progress: Float
) {
    val hasKnownSize = totalSize > 0L
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "downloadProgress"
    )

    HeroInfoCard(
        icon = Icons.Outlined.Download,
        // Names the release being fetched, since the dialog title already says it is downloading
        title = version?.isolateLtr() ?: stringResource(R.string.app_name),
        footer = {
            val progressModifier = Modifier
                .fillMaxWidth()
                .height(ProgressBarHeight)
            val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

            if (hasKnownSize) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = progressModifier,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = trackColor
                )
            } else {
                LinearProgressIndicator(
                    modifier = progressModifier,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = trackColor
                )
            }
        },
        subtitle = {
            Text(
                text = if (hasKnownSize) {
                    stringResource(
                        R.string.manager_update_progress_detail,
                        formatMegabytes(downloadedSize),
                        formatMegabytes(totalSize),
                        (animatedProgress * 100).toInt()
                    )
                } else {
                    stringResource(
                        R.string.manager_update_progress_downloaded,
                        formatMegabytes(downloadedSize)
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    )
}

/** Installer failure details. The dialog title already states that the install failed. */
@Composable
private fun InstallFailureContent(message: String) {
    if (message.isEmpty()) return

    Notice(
        icon = Icons.Outlined.ErrorOutline,
        text = message,
        tone = SemanticTone.Error
    )
}

/**
 * Success confirmation, with the check mark springing into place. The installed [version] takes
 * the place of a caption because the dialog title already announces the result.
 */
@Composable
private fun UpdateCompletedContent(version: String?) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "successScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Defaults.ContentPaddingExpanded),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingMedium)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .size(SuccessIconContainerSize)
                .scale(scale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                ThemedIcon(
                    icon = Icons.Outlined.CheckCircle,
                    tint = MaterialTheme.colorScheme.tertiary,
                    size = SuccessIconSize
                )
            }
        }

        if (version != null) {
            Text(
                text = version.isolateLtr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current,
                textAlign = TextAlign.Center
            )
        }
    }
}
