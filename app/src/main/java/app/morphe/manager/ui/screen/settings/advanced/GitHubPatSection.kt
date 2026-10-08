/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import kotlinx.coroutines.launch

/**
 * Kept in English because GitHub has no localized UI and users look for this exact name there.
 */
private const val GITHUB_PAT_NAME = "GitHub Personal Access Token"

/**
 * GitHub PAT settings item for Advanced tab.
 */
@Composable
fun GitHubPatSettingsItem(
    currentPat: String,
    currentIncludeInExport: Boolean,
    onSave: (String, Boolean) -> Unit
) {
    val showDialog = rememberSaveable { mutableStateOf(false) }
    val hasPat = currentPat.isNotBlank()

    SettingsItem(
        onClick = { showDialog.value = true },
        leadingContent = {
            ThemedIcon(icon = Icons.Outlined.VpnKey)
        },
        title = stringResource(R.string.settings_advanced_github_pat),
        subtitle = if (hasPat) {
            stringResource(R.string.settings_advanced_github_pat_configured)
        } else {
            stringResource(R.string.settings_advanced_github_pat_description)
        },
        statusContent = {
            StatusCircleIcon(
                icon = Icons.Outlined.Check,
                containerColor = if (hasPat) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (hasPat) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )

    if (showDialog.value) {
        GitHubPatDialog(
            currentPat = currentPat,
            currentIncludeInExport = currentIncludeInExport,
            onSubmit = { pat, include ->
                onSave(pat, include)
                showDialog.value = false
            },
            onDismiss = { showDialog.value = false }
        )
    }
}

/**
 * GitHub PAT configuration dialog.
 */
@Composable
private fun GitHubPatDialog(
    currentPat: String,
    currentIncludeInExport: Boolean,
    onSubmit: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val pat = rememberSaveable(currentPat) { mutableStateOf(currentPat) }
    val includePatInExport = rememberSaveable(currentIncludeInExport) { mutableStateOf(currentIncludeInExport) }
    val showIncludeWarning = rememberSaveable { mutableStateOf(false) }
    val showInfoDialog = rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val generatePatLink = "https://github.com/settings/tokens/new?scopes=public_repo&description=morphe-manager-github-integration"

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_github_pat_dialog_title, GITHUB_PAT_NAME),
        description = stringResource(R.string.settings_advanced_github_pat_description),
        titleTrailingContent = {
            TitleAction(
                icon = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.settings_advanced_github_pat_how_to_get),
                onClick = { showInfoDialog.value = true }
            )
        },
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = {
                    scope.launch {
                        onSubmit(pat.value, includePatInExport.value)
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
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            // PAT input
            AppDialogTextField(
                value = pat.value,
                onValueChange = { pat.value = it },
                label = { Text(stringResource(R.string.settings_advanced_github_pat)) },
                placeholder = { Text("ghp_xxxxxxxxxxxxxxx") },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Key, contentDescription = null) },
                isPassword = true,
                showClearButton = true
            )

            // Export include toggle + warning
            SettingsSwitchItem(
                checked = includePatInExport.value,
                onToggle = {
                    if (!includePatInExport.value) showIncludeWarning.value = true
                    else includePatInExport.value = false
                },
                showBorder = true,
                leadingContent = {
                    ThemedIcon(
                        icon = Icons.Outlined.Upload,
                        tint = dialogTextColor()
                    )
                },
                title = stringResource(R.string.settings_advanced_github_pat_export_include_label),
                subtitle = stringResource(R.string.settings_advanced_github_pat_export_include_supporting)
            )

            // Warning badge if PAT will be included
            if (includePatInExport.value) {
                Notice(
                    text = stringResource(R.string.settings_advanced_github_pat_export_warning),
                    tone = SemanticTone.Warning,
                    icon = Icons.Outlined.Warning
                )
            }
        }
    }

    // Info dialog with link to GitHub token creation
    if (showInfoDialog.value) {
        AppDialogWithLinks(
            title = stringResource(R.string.settings_advanced_github_pat_how_to_get),
            message = stringResource(R.string.settings_advanced_github_pat_dialog_description, "github.com", "public_repo", GITHUB_PAT_NAME),
            urlLink = generatePatLink,
            onDismiss = { showInfoDialog.value = false }
        )
    }

    // Include-in-export warning confirmation
    if (showIncludeWarning.value) {
        AppDialog(
            onDismissRequest = { showIncludeWarning.value = false },
            title = stringResource(R.string.warning),
            description = stringResource(R.string.settings_advanced_github_pat_export_warning),
            footer = {
                AppDialogButtonRow(
                    primaryText = stringResource(R.string.confirm),
                    onPrimaryClick = {
                        includePatInExport.value = true
                        showIncludeWarning.value = false
                    },
                    primaryIcon = Icons.Outlined.Warning,
                    isPrimaryDestructive = true,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = { showIncludeWarning.value = false }
                )
            }
        )
    }
}
