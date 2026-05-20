/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel

/**
 * Storage management section.
 */
@Composable
fun StorageManagementSection(
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel
) {
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()

    // Storage counts
    val originalApkCount by settingsViewModel.originalApkCount.collectAsStateWithLifecycle()
    val patchedApkCount by settingsViewModel.patchedApkCount.collectAsStateWithLifecycle()
    val patchedPackagesCount by settingsViewModel.patchedPackagesCount.collectAsStateWithLifecycle()

    val showApkManagementDialog = remember { mutableStateOf<ApkManagementType?>(null) }
    val showPatchSelectionDialog = remember { mutableStateOf(false) }

    // APK management dialog
    showApkManagementDialog.value?.let { type ->
        ApkManagementDialog(
            type = type,
            onDismissRequest = { showApkManagementDialog.value = null }
        )
    }

    // Patch selection management dialog
    if (showPatchSelectionDialog.value) {
        PatchSelectionManagementDialog(
            settingsViewModel = settingsViewModel,
            importExportViewModel = importExportViewModel,
            onDismiss = { showPatchSelectionDialog.value = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
        SectionTitle(
            text = stringResource(R.string.settings_system_storage_management),
            icon = Icons.Outlined.Storage
        )

        SectionCard {
            Column {
                // Original APKs management
                RichSettingsItem(
                    onClick = { showApkManagementDialog.value = ApkManagementType.ORIGINAL },
                    title = stringResource(R.string.settings_system_original_apks_title),
                    subtitle = stringResource(R.string.settings_system_original_apks_description),
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.Storage)
                    },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (originalApkCount > 0) {
                                InfoBadge(
                                    text = originalApkCount.toString(),
                                    style = InfoBadgeStyle.Default,
                                    isCompact = true
                                )
                            }
                            MorpheIcon(icon = Icons.Outlined.ChevronRight)
                        }
                    }
                )

                MorpheSettingsDivider()

                // Patched APKs management
                RichSettingsItem(
                    onClick = { showApkManagementDialog.value = ApkManagementType.PATCHED },
                    title = stringResource(R.string.settings_system_patched_apks_title),
                    subtitle = stringResource(R.string.settings_system_patched_apks_description),
                    leadingContent = {
                        MorpheIcon(icon = Icons.Outlined.Apps)
                    },
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (patchedApkCount > 0) {
                                InfoBadge(
                                    text = patchedApkCount.toString(),
                                    style = InfoBadgeStyle.Default,
                                    isCompact = true
                                )
                            }
                            MorpheIcon(icon = Icons.Outlined.ChevronRight)
                        }
                    }
                )

                // Patch Selections management (Expert mode only)
                if (useExpertMode) {
                    MorpheSettingsDivider()

                    RichSettingsItem(
                        onClick = { showPatchSelectionDialog.value = true },
                        title = stringResource(R.string.settings_system_patch_selections_title),
                        subtitle = stringResource(R.string.settings_system_patch_selections_description),
                        leadingContent = {
                            MorpheIcon(icon = Icons.Outlined.Tune)
                        },
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (patchedPackagesCount > 0) {
                                    InfoBadge(
                                        text = patchedPackagesCount.toString(),
                                        style = InfoBadgeStyle.Default,
                                        isCompact = true
                                    )
                                }
                                MorpheIcon(icon = Icons.Outlined.ChevronRight)
                            }
                        }
                    )
                }
            }
        }
    }
}
