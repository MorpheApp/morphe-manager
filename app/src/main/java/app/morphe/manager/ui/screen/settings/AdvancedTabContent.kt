/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.advanced.GitHubPatSettingsItem
import app.morphe.manager.ui.screen.settings.advanced.PatchOptionsSection
import app.morphe.manager.ui.screen.settings.advanced.UpdatesSettingsItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel

/**
 * Advanced tab content.
 */
@Composable
fun AdvancedTabContent(
    patchOptionsViewModel: PatchOptionsViewModel,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel
) {
    val prefs = settingsViewModel.prefs
    val useExpertMode by prefs.useExpertMode.getAsState()
    val stripUnusedNativeLibs by prefs.stripUnusedNativeLibs.getAsState()

    // Notify VM on expert mode changes so it can derive showExpertModeNotice
    LaunchedEffect(useExpertMode) {
        settingsViewModel.onExpertModeChanged(useExpertMode)
    }

    val showExpertModeNotice = settingsViewModel.showExpertModeNotice
    val showExpertModeDialog = remember { mutableStateOf(false) }
    val gitHubPat by prefs.gitHubPat.getAsState()
    val includeGitHubPatInExports by prefs.includeGitHubPatInExports.getAsState()

    // Localized strings for accessibility
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    // Expert mode confirmation dialog
    if (showExpertModeDialog.value) {
        ExpertModeConfirmationDialog(
            onDismiss = { showExpertModeDialog.value = false },
            onConfirm = {
                settingsViewModel.setExpertMode(true)
                showExpertModeDialog.value = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Updates section
        SectionTitle(
            text = stringResource(R.string.settings_advanced_updates),
            icon = Icons.Outlined.Update
        )

        UpdatesSettingsItem(
            settingsViewModel = settingsViewModel,
            onManagerPrereleasesToggle = { homeViewModel.triggerUpdateCheck() }
        )

        // Expert settings section
        SectionTitle(
            text = stringResource(R.string.settings_advanced_expert),
            icon = Icons.Outlined.Engineering
        )

        RichSettingsItem(
            onClick = {
                if (!useExpertMode) showExpertModeDialog.value = true
                else settingsViewModel.setExpertMode(false)
            },
            showBorder = true,
            leadingContent = {
                MorpheIcon(icon = Icons.Outlined.Psychology)
            },
            title = stringResource(R.string.settings_advanced_expert_mode),
            subtitle = stringResource(R.string.settings_advanced_expert_mode_description),
            trailingContent = {
                Switch(
                    checked = useExpertMode,
                    onCheckedChange = null,
                    modifier = Modifier.semantics {
                        stateDescription = if (useExpertMode) enabledState else disabledState
                    }
                )
            }
        )

        Crossfade(
            targetState = useExpertMode,
            label = "expert_mode_crossfade"
        ) { expertMode ->
            if (expertMode) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // GitHub PAT
                    GitHubPatSettingsItem(
                        currentPat = gitHubPat,
                        currentIncludeInExport = includeGitHubPatInExports,
                        onSave = { pat, include ->
                            settingsViewModel.setGitHubPat(pat, include)
                        }
                    )

                    // Strip unused native libraries + filter split APKs for device
                    RichSettingsItem(
                        onClick = {
                            settingsViewModel.setStripUnusedNativeLibs(!stripUnusedNativeLibs)
                        },
                        showBorder = true,
                        leadingContent = {
                            MorpheIcon(icon = Icons.Outlined.LayersClear)
                        },
                        title = stringResource(R.string.settings_advanced_strip_unused_libs),
                        subtitle = stringResource(R.string.settings_advanced_strip_unused_libs_description),
                        trailingContent = {
                            Switch(
                                checked = stripUnusedNativeLibs,
                                onCheckedChange = null,
                                modifier = Modifier.semantics {
                                    stateDescription =
                                        if (stripUnusedNativeLibs) enabledState else disabledState
                                }
                            )
                        }
                    )

                    // Expert mode notice shown once after enabling
                    if (showExpertModeNotice) {
                        InfoBadge(
                            icon = Icons.Outlined.Info,
                            text = stringResource(R.string.settings_advanced_patch_options_expert_mode_notice),
                            style = InfoBadgeStyle.Warning,
                            isExpanded = true
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Patch Options (Simple mode only)
                    SectionTitle(
                        text = stringResource(R.string.settings_advanced_patch_options),
                        icon = Icons.Outlined.Tune
                    )

                    PatchOptionsSection(
                        patchOptionsPrefs = patchOptionsViewModel.patchOptionsPrefs,
                        patchOptionsViewModel = patchOptionsViewModel,
                        homeViewModel = homeViewModel
                    )
                }
            }
        }
    }
}

/**
 * Dialog to confirm enabling Expert mode.
 */
@Composable
private fun ExpertModeConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_advanced_expert_mode_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.enable),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = stringResource(R.string.settings_advanced_expert_mode_dialog_message),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
