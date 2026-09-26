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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.UpdateViewModel
import app.morphe.manager.util.MANAGER_REPO_URL
import app.morphe.manager.util.formatMegabytes
import app.morphe.manager.util.isolateLtr
import app.morphe.manager.util.releasePageUrl
import app.morphe.manager.util.rememberSourceAccent
import app.morphe.manager.util.withVersionPrefix
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val ProgressBarHeight = 8.dp
private val SuccessIconContainerSize = 80.dp
private val SuccessIconSize = 40.dp

/**
 * How long the app must hold the foreground before an install left in
 * [UpdateViewModel.State.INSTALLING] counts as abandoned rather than merely still opening.
 */
private val AbandonedInstallGrace = 1500.milliseconds

/**
 * The distinct bodies the update dialog can show. States that share a body map to the same
 * entry so switching between them does not restart the crossfade.
 */
private enum class UpdateDialogContent {
    DetailsLoading,
    Details,
    Downloading,
    Installing,
    Failed,
    Success
}

/** Resolves the body to show, the changelog or a stage of the update. */
private fun updateDialogContentOf(updateViewModel: UpdateViewModel): UpdateDialogContent =
    when (updateViewModel.state) {
        UpdateViewModel.State.CAN_DOWNLOAD, UpdateViewModel.State.CAN_INSTALL ->
            if (updateViewModel.changelogEntries == null) UpdateDialogContent.DetailsLoading
            else UpdateDialogContent.Details

        UpdateViewModel.State.DOWNLOADING -> UpdateDialogContent.Downloading
        UpdateViewModel.State.INSTALLING -> UpdateDialogContent.Installing
        UpdateViewModel.State.FAILED -> UpdateDialogContent.Failed
        UpdateViewModel.State.SUCCESS -> UpdateDialogContent.Success
    }

/**
 * Changelog of the manager, which doubles as its update dialog. The timeline runs from the
 * releases an available update brings, summed up above them, through the installed version to
 * the history below, and the footer downloads and installs the update while there is one.
 *
 * @param expectsUpdate Whether the dialog was opened for an update, from the banner or its
 *   notification. It then keeps to the update even when the check comes back empty, saying the
 *   release is not ready yet and offering to check again.
 */
@Composable
fun ManagerChangelogDialog(
    onDismiss: () -> Unit,
    updateViewModel: UpdateViewModel,
    expectsUpdate: Boolean = false
) {
    val state = updateViewModel.state
    val content = updateDialogContentOf(updateViewModel)
    val hasUpdate = updateViewModel.releaseInfo != null
    // A banner can outlive the release it points at, and a check can fail outright, so name the
    // situation rather than wait on data that is not coming
    val isUpdateUnavailable = expectsUpdate && !hasUpdate && !updateViewModel.isCheckingForUpdate
    val older = OlderReleases(
        entries = updateViewModel.olderManagerEntries,
        isLoading = updateViewModel.isLoadingOlderEntries,
        isFailed = updateViewModel.olderEntriesFailed,
        onLoad = updateViewModel::loadOlderManagerEntries
    )

    // An installer activity reports nothing when it is dismissed, so an abandoned install shows
    // up only as the app holding the foreground while the state is still INSTALLING
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, updateViewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(AbandonedInstallGrace)
            updateViewModel.resetIfInstallCancelled()
        }
    }

    LaunchedEffect(Unit) {
        updateViewModel.loadChangelog()
    }
    // Drop the older releases when the dialog closes so it reopens on the releases it starts with
    DisposableEffect(Unit) {
        onDispose { updateViewModel.resetOlderManagerEntries() }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        scrollable = false,
        // The timeline gives up its leading edge to the rail, so the list takes the wider layout.
        // The header holds the top, and progress and results sit centered in the room below it
        padding = DialogPadding.Compact,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
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
                    expectsUpdate = expectsUpdate,
                    onDismiss = onDismiss,
                    // Only the changelog body has anything to translate
                    translatable = content == UpdateDialogContent.Details
                )
            }
        }
    ) {
        // Headed by Morphe itself, as a source's changelog is by the source. The version is the
        // one on its way where there is one, so a download names what it fetches
        ListDialogHeader(
            icon = { modifier ->
                Surface(modifier = modifier, shape = CircleShape, color = Color.White) {
                    MorpheLauncherLogo(modifier = Modifier.fillMaxSize())
                }
            },
            // With no update on the way, this is just the changelog
            title = if (state == UpdateViewModel.State.CAN_DOWNLOAD && !hasUpdate && !expectsUpdate) {
                stringResource(R.string.changelog)
            } else {
                stringResource(state.title)
            },
            subtitle = listOf(
                stringResource(R.string.app_name),
                (updateViewModel.releaseInfo?.version ?: BuildConfig.VERSION_NAME).withVersionPrefix().isolateLtr()
            ).joinToString(" · "),
            accentColor = rememberSourceAccent(isDefault = true, avatarUrl = null, fallbackAvatarUrl = null)
        )

        AnimatedContent(
            targetState = content,
            transitionSpec = Animations.fadeCrossfade(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "updateContent"
        ) { content ->
            when (content) {
                UpdateDialogContent.DetailsLoading -> ChangelogListLoading(
                    withSummary = hasUpdate || expectsUpdate,
                    modifier = Modifier.padding(top = Defaults.ItemSpacing)
                )

                UpdateDialogContent.Details -> {
                    val entries = updateViewModel.changelogEntries.orEmpty()
                    val newReleases = entries.take(updateViewModel.newReleaseCount)
                    ChangelogList(
                        entries = entries,
                        older = older,
                        currentVersion = BuildConfig.VERSION_NAME,
                        // The gap under the header is the list's own, so releases scroll up to its edge
                        contentPadding = PaddingValues(top = Defaults.ItemSpacing),
                        header = when {
                            // Everything the user is about to install, summed up above the releases themselves
                            newReleases.isNotEmpty() -> {
                                {
                                    ChangelogUpdateSummary(
                                        fromVersion = BuildConfig.VERSION_NAME,
                                        toVersion = updateViewModel.releaseInfo?.version
                                            ?: newReleases.first().version,
                                        entries = newReleases
                                    )
                                }
                            }

                            isUpdateUnavailable -> {
                                {
                                    Notice(
                                        icon = Icons.Outlined.HourglassEmpty,
                                        text = stringResource(R.string.manager_update_not_ready),
                                        tone = SemanticTone.Warning
                                    )
                                }
                            }

                            else -> null
                        }
                    )
                }

                UpdateDialogContent.Downloading -> CenteredStatus {
                    DownloadProgress(
                        downloadedSize = updateViewModel.downloadedSize,
                        totalSize = updateViewModel.totalSize,
                        progress = updateViewModel.downloadProgress
                    )
                }

                // The dialog title already reads "Installing update", so the logo carries it
                // as a description instead of repeating it on screen
                UpdateDialogContent.Installing -> CenteredStatus {
                    PulsingLogoIndicator(contentDescription = stringResource(R.string.installing_manager_update))
                }

                UpdateDialogContent.Failed -> CenteredStatus {
                    InstallFailureContent(updateViewModel.installError)
                }

                UpdateDialogContent.Success -> CenteredStatus {
                    UpdateCompletedContent(version = updateViewModel.releaseInfo?.version)
                }
            }
        }
    }

    TranslationOverlays()

    // Internet check dialog
    if (updateViewModel.showInternetCheckDialog) {
        MeteredDownloadDialog(
            title = stringResource(R.string.download_update_confirmation),
            onConfirm = {
                updateViewModel.showInternetCheckDialog = false
                updateViewModel.downloadUpdate(ignoreInternetCheck = true)
            },
            onDismiss = { updateViewModel.showInternetCheckDialog = false }
        )
    }
}

/** Dialog actions, one set per [UpdateViewModel.State]. */
@Composable
private fun UpdateDialogFooter(
    state: UpdateViewModel.State,
    updateViewModel: UpdateViewModel,
    expectsUpdate: Boolean,
    onDismiss: () -> Unit,
    translatable: Boolean
) {
    val releaseInfo = updateViewModel.releaseInfo

    val actions: List<DialogAction> = when (state) {
        UpdateViewModel.State.CAN_DOWNLOAD -> buildList {
            // Opened for the changelog alone, the dialog offers the download only once an update turns up
            if (releaseInfo == null && !expectsUpdate) {
                add(
                    DialogAction(
                        text = stringResource(R.string.close),
                        onClick = onDismiss,
                        emphasis = DialogActionEmphasis.Outlined
                    )
                )
                return@buildList
            }

            add(
                DialogAction(
                    text = stringResource(R.string.download),
                    onClick = { updateViewModel.downloadUpdate() },
                    icon = Icons.Outlined.Download,
                    // Nothing to download until the check resolves an actual release
                    enabled = releaseInfo != null
                )
            )

            // Offered once the check has settled on nothing, which is recoverable
            // on its own a moment later
            if (releaseInfo == null && !updateViewModel.isCheckingForUpdate) {
                add(
                    DialogAction(
                        text = stringResource(R.string.retry),
                        onClick = { updateViewModel.retryUpdateCheck() },
                        icon = Icons.Outlined.Refresh
                    )
                )
            }
        }

        UpdateViewModel.State.DOWNLOADING -> listOf(
            DialogAction(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                emphasis = DialogActionEmphasis.Outlined
            )
        )

        UpdateViewModel.State.CAN_INSTALL -> listOf(
            DialogAction(
                text = stringResource(R.string.install),
                onClick = { updateViewModel.installUpdate() },
                icon = Icons.Outlined.InstallMobile
            )
        )

        UpdateViewModel.State.INSTALLING -> {
            // No cancel button during installation - can't cancel system dialog
            // User can close our dialog, but install will continue
            emptyList()
        }

        UpdateViewModel.State.FAILED -> listOf(
            // Only an install can end here, so the retry is always an install; a download that
            // fails drops what it wrote and returns to CAN_DOWNLOAD
            DialogAction(
                text = stringResource(R.string.install),
                onClick = { updateViewModel.installUpdate() },
                icon = Icons.Outlined.InstallMobile
            ),
            DialogAction(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss
            )
        )

        UpdateViewModel.State.SUCCESS -> listOf(
            DialogAction(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                emphasis = DialogActionEmphasis.Outlined
            )
        )
    }

    // The release page stays at hand while the changelog is on screen or an install has failed
    val offersReleasePage = state == UpdateViewModel.State.CAN_DOWNLOAD ||
            state == UpdateViewModel.State.CAN_INSTALL ||
            state == UpdateViewModel.State.FAILED

    // The page of the update while there is one, else that of the newest release listed
    val pageUrl = releaseInfo?.pageUrl
        ?: updateViewModel.changelogEntries?.firstOrNull()?.version?.let { releasePageUrl(MANAGER_REPO_URL, it) }

    ChangelogFooter(
        actions = actions,
        translatable = translatable,
        pageUrl = pageUrl.takeIf { offersReleasePage }
    )
}

/** Stands a progress or result in the middle of the room the header leaves, where the list would be. */
@Composable
private fun CenteredStatus(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

/**
 * Download progress with an animated bar. The header names the release being fetched.
 *
 * The total size is unknown until the first progress callback, and stays unknown when the server
 * streams the release without a content length, so both cases fall back to an indeterminate bar.
 */
@Composable
private fun DownloadProgress(
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        Text(
            text = if (hasKnownSize) {
                stringResource(
                    R.string.download_progress,
                    formatMegabytes(downloadedSize),
                    formatMegabytes(totalSize),
                    (progress * 100).toInt().toString()
                )
            } else {
                stringResource(
                    R.string.manager_update_progress_downloaded,
                    formatMegabytes(downloadedSize)
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

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
    }
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
