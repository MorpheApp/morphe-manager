package app.revanced.manager.ui.component.morphe.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.ui.component.morphe.shared.*

/**
 * Dialog for adding patch bundles
 */
@Composable
fun MorpheAddBundleDialog(
    onDismiss: () -> Unit,
    onLocalSubmit: () -> Unit,
    onRemoteSubmit: (url: String) -> Unit,
    onLocalPick: () -> Unit,
    selectedLocalPath: String?
) {
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Remote, 1 = Local

    val isRemoteValid = remoteUrl.isNotBlank() &&
            (remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://"))
    val isLocalValid = selectedLocalPath != null

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_add_patch_bundle),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.add),
                onPrimaryClick = {
                    when (selectedTab) {
                        0 -> if (isRemoteValid) onRemoteSubmit(remoteUrl)
                        1 -> if (isLocalValid) onLocalSubmit()
                    }
                },
                primaryEnabled = if (selectedTab == 0) isRemoteValid else isLocalValid,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tabs
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        stringResource(R.string.morphe_remote),
                        stringResource(R.string.morphe_local)
                    ).forEachIndexed { index, title ->
                        val isSelected = selectedTab == index

                        Surface(
                            onClick = { selectedTab = index },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        LocalDialogTextColor.current
                                )
                            }
                        }
                    }
                }
            }

            // Content based on selected tab
            when (selectedTab) {
                0 -> RemoteTabContent(
                    remoteUrl = remoteUrl,
                    onUrlChange = { remoteUrl = it },
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
                1 -> LocalTabContent(
                    selectedPath = selectedLocalPath,
                    onPickFile = onLocalPick,
                    secondaryColor = secondaryColor
                )
            }
        }
    }
}

@Composable
private fun RemoteTabContent(
    remoteUrl: String,
    onUrlChange: (String) -> Unit,
    textColor: Color,
    secondaryColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // URL input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MorpheDialogTextField(
                value = remoteUrl,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(R.string.morphe_remote_source_url),
                        color = LocalDialogSecondaryTextColor.current
                    )
                },
                placeholder = {
                    Text(
                        text = "https://example.com/patches.json",
                        color = secondaryColor.copy(alpha = 0.5f)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }

        // Description
        Text(
            text = stringResource(R.string.morphe_remote_bundle_description),
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor
        )
    }
}

@Composable
private fun LocalTabContent(
    selectedPath: String?,
    onPickFile: () -> Unit,
    secondaryColor: androidx.compose.ui.graphics.Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // File picker button
        MorpheDialogButton(
            text = if (selectedPath == null) {
                stringResource(R.string.morphe_select_patch_bundle_file)
            } else {
                stringResource(R.string.morphe_change_file)
            },
            onClick = onPickFile,
            icon = Icons.Outlined.FolderOpen,
            modifier = Modifier.fillMaxWidth()
        )

        // Selected file path
        if (selectedPath != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Text(
                    text = selectedPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }

        // Description
        Text(
            text = stringResource(R.string.morphe_local_bundle_description),
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor
        )
    }
}

@Composable
fun BundleDeleteConfirmDialog(
    bundle: PatchBundleSource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    app.revanced.manager.ui.component.morphe.shared.MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        footer = {
            app.revanced.manager.ui.component.morphe.shared.MorpheDialogButtonRow(
                primaryText = stringResource(R.string.delete),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val secondaryColor = app.revanced.manager.ui.component.morphe.shared.LocalDialogSecondaryTextColor.current

        Text(
            text = stringResource(
                R.string.morphe_bundle_delete_confirm_message,
                bundle.displayTitle
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor
        )
    }
}

@Composable
fun BundleRenameDialog(
    bundle: PatchBundleSource,
    currentName: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    app.revanced.manager.ui.component.morphe.shared.MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.morphe_rename),
        footer = {
            app.revanced.manager.ui.component.morphe.shared.MorpheDialogButtonRow(
                primaryText = stringResource(R.string.morphe_rename),
                onPrimaryClick = { onConfirm(currentName) },
                primaryEnabled = currentName.isNotBlank() && currentName != bundle.name,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val textColor = app.revanced.manager.ui.component.morphe.shared.LocalDialogTextColor.current
        val secondaryColor = app.revanced.manager.ui.component.morphe.shared.LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MorpheDialogTextField(
                value = currentName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(R.string.morphe_bundle_rename_description),
                        color = LocalDialogSecondaryTextColor.current
                    )
                },
                placeholder = {
                    Text(bundle.name)
                },
                singleLine = true
            )
        }
    }
}
