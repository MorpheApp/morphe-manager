package app.revanced.manager.ui.screen.settings.advanced

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.screen.shared.*
import kotlinx.coroutines.launch

/**
 * GitHub PAT settings item for Advanced tab
 */
@Composable
fun GitHubPatSettingsItem(
    currentPat: String,
    currentIncludeInExport: Boolean,
    onSave: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val hasPat = currentPat.isNotBlank()

    RichSettingsItem(
        onClick = { showDialog = true },
        showBorder = true,
        leadingContent = {
            MorpheIcon(icon = Icons.Outlined.VpnKey)
        },
        title = stringResource(R.string.settings_advanced_github_pat),
        subtitle = if (hasPat) {
            stringResource(R.string.settings_advanced_github_pat_configured)
        } else {
            stringResource(R.string.settings_advanced_github_pat_description)
        },
        trailingContent = {
            if (hasPat) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        modifier = modifier
    )

    if (showDialog) {
        GitHubPatDialog(
            currentPat = currentPat,
            currentIncludeInExport = currentIncludeInExport,
            onSubmit = { pat, include ->
                onSave(pat, include)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * GitHub PAT configuration dialog
 */
@Composable
private fun GitHubPatDialog(
    currentPat: String,
    currentIncludeInExport: Boolean,
    onSubmit: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pat by rememberSaveable(currentPat) { mutableStateOf(currentPat) }
    var includePatInExport by rememberSaveable(currentIncludeInExport) { mutableStateOf(currentIncludeInExport) }
    var showIncludeWarning by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val generatePatLink = "https://github.com/settings/tokens/new?scopes=public_repo&description=morphe-manager-github-integration"

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_github_pat_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    scope.launch {
                        onSubmit(pat, includePatInExport)
                    }
                },
                primaryIcon = Icons.Outlined.Save,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Info button section
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.settings_advanced_github_pat_how_to_get),
                onClick = { showInfoDialog = true },
                icon = Icons.Outlined.Info,
                modifier = Modifier.fillMaxWidth()
            )

            // PAT input field section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_advanced_github_pat_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalDialogTextColor.current
                )

                MorpheDialogTextField(
                    value = pat,
                    onValueChange = { pat = it },
                    label = { Text(stringResource(R.string.settings_advanced_github_pat_label)) },
                    placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = LocalDialogTextColor.current.copy(alpha = 0.7f)
                        )
                    },
                    trailingIcon = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Toggle visibility button
                            IconButton(
                                onClick = { showPassword = !showPassword }
                            ) {
                                Icon(
                                    imageVector = if (showPassword) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (showPassword) {
                                        stringResource(R.string.settings_advanced_github_pat_hide)
                                    } else {
                                        stringResource(R.string.settings_advanced_github_pat_show)
                                    },
                                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f)
                                )
                            }

                            // Clear button
                            if (pat.isNotBlank()) {
                                IconButton(onClick = { pat = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.clear),
                                        tint = LocalDialogTextColor.current.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // Export settings section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_advanced_github_pat_export_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalDialogTextColor.current
                )

                RichSettingsItem(
                    onClick = {
                        if (!includePatInExport) {
                            showIncludeWarning = true
                        } else {
                            includePatInExport = false
                        }
                    },
                    showBorder = true,
                    leadingContent = {
                        MorpheIcon(
                            icon = Icons.Outlined.Upload,
                            tint = LocalDialogTextColor.current
                        )
                    },
                    title = stringResource(R.string.settings_advanced_github_pat_export_include_label),
                    subtitle = stringResource(R.string.settings_advanced_github_pat_export_include_supporting),
                    trailingContent = {
                        Switch(
                            checked = includePatInExport,
                            onCheckedChange = null
                        )
                    }
                )

                // Warning badge if PAT will be included
                if (includePatInExport) {
                    InfoBadge(
                        text = stringResource(R.string.settings_advanced_github_pat_export_warning),
                        style = InfoBadgeStyle.Warning,
                        icon = Icons.Outlined.Warning,
                        isExpanded = true
                    )
                }
            }
        }
    }

    // Info dialog with link to GitHub token creation
    if (showInfoDialog) {
        MorpheDialogWithLinks(
            title = stringResource(R.string.settings_advanced_github_pat_how_to_get),
            message = stringResource(R.string.settings_advanced_github_pat_dialog_description),
            urlLink = generatePatLink,
            onDismiss = { showInfoDialog = false }
        )
    }

    // Include warning confirmation dialog
    if (showIncludeWarning) {
        MorpheDialog(
            onDismissRequest = { showIncludeWarning = false },
            title = stringResource(R.string.warning),
            footer = {
                MorpheDialogButtonRow(
                    primaryText = stringResource(R.string.confirm),
                    onPrimaryClick = {
                        includePatInExport = true
                        showIncludeWarning = false
                    },
                    primaryIcon = Icons.Outlined.Warning,
                    isPrimaryDestructive = true,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = { showIncludeWarning = false }
                )
            }
        ) {
            Text(
                text = stringResource(R.string.settings_advanced_github_pat_export_warning),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
