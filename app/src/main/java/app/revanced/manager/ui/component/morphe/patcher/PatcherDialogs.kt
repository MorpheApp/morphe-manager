package app.revanced.manager.ui.component.morphe.patcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.morphe.home.MorpheDialog

/**
 * Cancel patching confirmation dialog
 * Warns user about stopping patching process
 */
@Composable
fun CancelPatchingDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.patcher_stop_confirm_title),
        footer = {
            // Fixed footer - buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stop button
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.yes))
                }

                // Continue button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.no))
                }
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.patcher_stop_confirm_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Unified install dialog with state management
 * Handles initial install, conflicts, ready to install, and errors
 */
@Composable
fun InstallDialog(
    state: InstallDialogState,
    isWaitingForUninstall: Boolean,
    usingMountInstall: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onCancel: () -> Unit
) {
    val installButtonText = if (usingMountInstall) R.string.mount else R.string.install_app
    val installIcon = if (usingMountInstall) Icons.Outlined.FolderOpen else Icons.Outlined.FileDownload

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            when (state) {
                InstallDialogState.ERROR -> R.string.install_app_fail_title
                else -> installButtonText
            }
        ),
        footer = {
            // Fixed footer - buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancel button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(android.R.string.cancel))
                }

                // Action button (Install/Mount or Uninstall)
                when (state) {
                    InstallDialogState.INITIAL, InstallDialogState.READY_TO_INSTALL -> {
                        Button(
                            onClick = onInstall,
                            enabled = !isWaitingForUninstall,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                installIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(installButtonText))
                        }
                    }
                    InstallDialogState.CONFLICT, InstallDialogState.ERROR -> {
                        Button(
                            onClick = onUninstall,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.uninstall))
                        }
                    }
                }
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Description or error message
            if (state == InstallDialogState.ERROR && errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = stringResource(
                        when (state) {
                            InstallDialogState.INITIAL -> if (usingMountInstall)
                                R.string.morphe_patcher_mount_dialog_message
                            else
                                R.string.morphe_patcher_install_dialog_message
                            InstallDialogState.CONFLICT -> R.string.morphe_patcher_install_conflict_message
                            InstallDialogState.READY_TO_INSTALL -> if (usingMountInstall)
                                R.string.morphe_patcher_mount_ready_message
                            else
                                R.string.morphe_patcher_install_ready_message
                            InstallDialogState.ERROR -> R.string.morphe_patcher_install_dialog_message
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Root mode warning
                if (usingMountInstall && state == InstallDialogState.INITIAL) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.3f
                                )
                            )
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.morphe_root_gmscore_excluded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
