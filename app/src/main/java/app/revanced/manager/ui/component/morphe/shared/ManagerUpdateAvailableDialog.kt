package app.revanced.manager.ui.component.morphe.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.ui.component.Markdown
import app.revanced.manager.ui.component.morphe.utils.formatMegabytes
import app.revanced.manager.ui.viewmodel.UpdateViewModel
import app.revanced.manager.util.relativeTime

/**
 * Update details dialog with download and install functionality
 */
@Composable
fun ManagerUpdateDetailsDialog(
    onDismiss: () -> Unit,
    updateViewModel: UpdateViewModel
) {
    val state = updateViewModel.state
    val releaseInfo = updateViewModel.releaseInfo

    // Reset state when dialog is opened if installation was cancelled
    // This handles the case when user cancelled the system install dialog
    DisposableEffect(Unit) {
        // When dialog opens, if we're in INSTALLING state but no actual install is running,
        // reset to CAN_INSTALL (the file is already downloaded)
        if (state == UpdateViewModel.State.INSTALLING) {
            updateViewModel.resetIfInstallCancelled()
        }

        onDispose {
            // When dialog closes during download, cancel it
            if (state == UpdateViewModel.State.DOWNLOADING) {
                onDismiss()
            }
        }
    }

    MorpheDialog(
        onDismissRequest = { onDismiss() },
        title = stringResource(
            when (state) {
                UpdateViewModel.State.CAN_DOWNLOAD -> R.string.update_available
                UpdateViewModel.State.DOWNLOADING -> R.string.downloading_manager_update
                UpdateViewModel.State.CAN_INSTALL -> R.string.ready_to_install_update
                UpdateViewModel.State.INSTALLING -> R.string.installing_manager_update
                UpdateViewModel.State.FAILED -> R.string.install_update_manager_failed
                UpdateViewModel.State.SUCCESS -> R.string.update_completed
            }
        ),
        footer = {
            when (state) {
                UpdateViewModel.State.CAN_DOWNLOAD -> {
                    MorpheDialogButton(
                        text = stringResource(
                            if (updateViewModel.canResumeDownload) R.string.resume_download
                            else R.string.download
                        ),
                        onClick = { updateViewModel.downloadUpdate() },
                        icon = Icons.Outlined.Download,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                UpdateViewModel.State.DOWNLOADING -> {
                    MorpheDialogButton(
                        text = stringResource(R.string.close),
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                UpdateViewModel.State.CAN_INSTALL -> {
                    MorpheDialogButton(
                        text = stringResource(R.string.install_app),
                        onClick = { updateViewModel.installUpdate() },
                        icon = Icons.Outlined.InstallMobile,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                UpdateViewModel.State.INSTALLING -> {
                    // No cancel button during installation - can't cancel system dialog
                    // User can close our dialog, but install will continue
                }
                UpdateViewModel.State.FAILED -> {
                    MorpheDialogButtonColumn {
                        if (updateViewModel.canResumeDownload) {
                            // Download failed/cancelled - offer to resume
                            MorpheDialogButton(
                                text = stringResource(R.string.resume_download),
                                onClick = { updateViewModel.downloadUpdate() },
                                icon = Icons.Outlined.Download,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Download completed but install failed - offer to retry install
                            MorpheDialogButton(
                                text = stringResource(R.string.install_app),
                                onClick = { updateViewModel.installUpdate() },
                                icon = Icons.Outlined.InstallMobile,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        MorpheDialogOutlinedButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                UpdateViewModel.State.SUCCESS -> {
                    MorpheDialogButton(
                        text = stringResource(android.R.string.ok),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (state) {
                UpdateViewModel.State.DOWNLOADING -> {
                    DownloadProgressSection(
                        downloadedSize = updateViewModel.downloadedSize,
                        totalSize = updateViewModel.totalSize,
                        progress = updateViewModel.downloadProgress,
                        textColor = textColor
                    )
                }
                UpdateViewModel.State.INSTALLING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.installing_manager_update),
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                UpdateViewModel.State.FAILED -> {
                    if (updateViewModel.installError.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.install_update_manager_failed),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = updateViewModel.installError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                UpdateViewModel.State.SUCCESS -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.update_completed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                UpdateViewModel.State.CAN_DOWNLOAD, UpdateViewModel.State.CAN_INSTALL -> {
                    if (releaseInfo == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = stringResource(R.string.changelog_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        ReleaseInfoSection(
                            releaseInfo = releaseInfo,
                            textColor = textColor
                        )
                    }
                }
            }
        }
    }

    // Internet check dialog
    if (updateViewModel.showInternetCheckDialog) {
        MorpheDialog(
            onDismissRequest = { updateViewModel.showInternetCheckDialog = false },
            title = stringResource(R.string.download_update_confirmation),
            footer = {
                MorpheDialogButtonRow(
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
            InfoBadge(
                icon = Icons.Outlined.Warning,
                text = stringResource(R.string.download_confirmation_metered),
                style = InfoBadgeStyle.Warning,
                isExpanded = true
            )
        }
    }
}

/**
 * Download progress section with styled progress bar
 */
@Composable
private fun DownloadProgressSection(
    downloadedSize: Long,
    totalSize: Long,
    progress: Float,
    textColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            imageVector = Icons.Outlined.Download,
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
                        text = stringResource(R.string.downloading_manager_update),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Text(
                        text = stringResource(
                            R.string.manager_update_progress_detail,
                            formatMegabytes(downloadedSize),
                            formatMegabytes(totalSize),
                            (progress * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * Release information section with version, date, and changelog
 */
@Composable
private fun ReleaseInfoSection(
    releaseInfo: ReVancedAsset,
    textColor: Color
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val published = remember(releaseInfo.createdAt) {
        releaseInfo.createdAt.relativeTime(context)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
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
                            imageVector = Icons.Outlined.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = releaseInfo.version,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = published,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Changelog section
        if (releaseInfo.description.isNotBlank()) {
            Markdown(releaseInfo.description.replace("`", ""))
        }

        // Full changelog button
        releaseInfo.pageUrl?.let { url ->
            MorpheDialogButton(
                text = stringResource(R.string.changelog),
                onClick = { uriHandler.openUri(url) },
                icon = Icons.AutoMirrored.Outlined.Article,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
