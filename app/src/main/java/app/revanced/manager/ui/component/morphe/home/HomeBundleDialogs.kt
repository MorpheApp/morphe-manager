package app.revanced.manager.ui.component.morphe.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.ui.component.morphe.shared.*

/**
 * Dialog for adding patch bundles
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MorpheAddBundleDialog(
    onDismiss: () -> Unit,
    onLocalSubmit: () -> Unit,
    onRemoteSubmit: (url: String) -> Unit,
    onLocalPick: () -> Unit,
    selectedLocalPath: String?
) {
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Remote, 1 = Local

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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tabs
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        stringResource(R.string.morphe_remote),
                        stringResource(R.string.morphe_local)
                    ).forEachIndexed { index, title ->
                        val isSelected = selectedTab == index

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { selectedTab = index }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            ) {}
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge,
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

            // Tabs content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(200)))
                }
            ) { tab ->
                when (tab) {
                    0 -> RemoteTabContent(
                        remoteUrl = remoteUrl,
                        onUrlChange = { remoteUrl = it }
                    )
                    1 -> LocalTabContent(
                        selectedPath = selectedLocalPath,
                        onPickFile = onLocalPick
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteTabContent(
    remoteUrl: String,
    onUrlChange: (String) -> Unit
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
                    Text(stringResource(R.string.morphe_remote_source_url))
                },
                placeholder = {
                    Text(text = "https://example.com/patches.json")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }

        // Description
        InfoBadge(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.morphe_remote_bundle_description),
            style = InfoBadgeStyle.Success
        )
    }
}

@Composable
private fun LocalTabContent(
    selectedPath: String?,
    onPickFile: () -> Unit
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
            InfoBadge(
                icon = null,
                text = selectedPath,
                style = InfoBadgeStyle.Default
            )
        }

        // Description
        InfoBadge(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.morphe_local_bundle_description),
            style = InfoBadgeStyle.Success
        )
    }
}

@Composable
fun MorpheBundleDeleteConfirmDialog(
    bundle: PatchBundleSource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.delete),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

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

/**
 * Dialog for renaming a bundle
 */
@Composable
fun MorpheRenameBundleDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val keyboardController = LocalSoftwareKeyboardController.current

    MorpheDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.patches_display_name),
        dismissOnClickOutside = false,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = {
                    keyboardController?.hide()
                    onConfirm(textValue)
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = {
                    keyboardController?.hide()
                    onDismissRequest()
                }
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.patch_bundle_rename),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryColor
            )

            MorpheDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.morphe_patch_option_enter_value),
                        color = secondaryColor.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = secondaryColor
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onConfirm(textValue)
                    }
                )
            )
        }
    }
}