package app.revanced.manager.ui.component.morphe.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.UpdateViewModel
import app.revanced.manager.util.relativeTime

/**
 * Initial update available dialog
 * Shows when app launches with available update
 */
@Composable
fun ManagerUpdateAvailableDialog(
    onDismiss: () -> Unit,
    onShowDetails: () -> Unit,
    setShowManagerUpdateDialogOnLaunch: (Boolean) -> Unit,
    newVersion: String
) {
    MorpheDialog(
        onDismissRequest = {
            setShowManagerUpdateDialogOnLaunch(true)
            onDismiss()
        },
        title = stringResource(R.string.update_available),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.show),
                onPrimaryClick = {
                    setShowManagerUpdateDialogOnLaunch(true)
                    onShowDetails()
                },
                secondaryText = stringResource(R.string.never_show_again),
                onSecondaryClick = {
                    setShowManagerUpdateDialogOnLaunch(false)
                    onDismiss()
                }
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Text(
            text = stringResource(R.string.update_available_dialog_description, newVersion),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Full update details dialog with download and install functionality
 * Replaces the UpdateScreen
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
                        icon = Icons.Outlined.InstallMobile,
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (updateViewModel.canResumeDownload) {
                            // Download failed/cancelled - offer to resume
                            MorpheDialogButton(
                                text = stringResource(R.string.resume_download),
                                onClick = { updateViewModel.downloadUpdate() },
                                icon = Icons.Outlined.InstallMobile,
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
                        MorpheDialogButton(
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
            // Download progress
            if (state == UpdateViewModel.State.DOWNLOADING) {
                DownloadProgressSection(
                    downloadedSize = updateViewModel.downloadedSize,
                    totalSize = updateViewModel.totalSize,
                    progress = updateViewModel.downloadProgress,
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
            }

            // Installing indicator
            if (state == UpdateViewModel.State.INSTALLING) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.installing_manager_update),
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondaryColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Error message
            if (state == UpdateViewModel.State.FAILED && updateViewModel.installError.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.install_update_manager_failed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = updateViewModel.installError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Success message
            if (state == UpdateViewModel.State.SUCCESS) {
                Text(
                    text = stringResource(R.string.update_completed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Green.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Loading changelog indicator
            if (releaseInfo == null && state == UpdateViewModel.State.CAN_DOWNLOAD
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(R.string.changelog_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Release info - show only when not actively installing
            if (releaseInfo != null && state != UpdateViewModel.State.INSTALLING) {
                ReleaseInfoSection(
                    releaseInfo = releaseInfo,
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
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
            val secondaryColor = LocalDialogSecondaryTextColor.current
            Text(
                text = stringResource(R.string.download_confirmation_metered),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
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
    textColor: Color,
    secondaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.downloading_manager_update),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(
                R.string.manager_update_progress_detail,
                formatMegabytes(downloadedSize),
                formatMegabytes(totalSize),
                (progress * 100).toInt()
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * Release information section with version, date, and changelog
 */
@Composable
private fun ReleaseInfoSection(
    releaseInfo: ReVancedAsset,
    textColor: Color,
    secondaryColor: Color
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val published = remember(releaseInfo.createdAt) {
        releaseInfo.createdAt.relativeTime(context)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Version and published date in a card-like section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Version
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor
                )
                Text(
                    text = releaseInfo.version,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            // Published date
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = stringResource(R.string.published),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor
                )
                Text(
                    text = published,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }

        // Changelog section
        if (releaseInfo.description.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Markdown(releaseInfo.description.replace("`", ""))
            }
        }

        // Full changelog button
        releaseInfo.pageUrl?.let { url ->
            MorpheDialogButton(
                text = stringResource(R.string.changelog),
                onClick = { uriHandler.openUri(url) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatMegabytes(bytes: Long): Float =
    if (bytes <= 0) 0f else bytes / 1_000_000f
