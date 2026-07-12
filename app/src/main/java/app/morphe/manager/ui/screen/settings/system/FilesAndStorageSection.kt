/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.isAndroidTv

/**
 * Storage management section.
 */
@Composable
fun FilesAndStorageSection(
    settingsViewModel: SettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    onFilePickerPositioned: ((Rect) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()
    val useCustomFilePicker by settingsViewModel.prefs.useCustomFilePicker.getAsState()
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

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
            text = stringResource(R.string.settings_system_files),
            icon = Icons.Outlined.Storage
        )

        SettingsGroup {
            // Original APKs management
            SettingsItem(
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
            SettingsItem(
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

                SettingsItem(
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

        // TV always uses the custom picker regardless of this toggle, so hide it to avoid confusion
        if (!isTV) {
            SettingsGroup(
                modifier = if (onFilePickerPositioned != null)
                    Modifier.onGloballyPositioned { coords -> onFilePickerPositioned(coords.boundsInWindow()) }
                else Modifier
            ) {
                SettingsItem(
                    onClick = { settingsViewModel.setUseCustomFilePicker(!useCustomFilePicker) },
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.FolderOpen) },
                    title = stringResource(R.string.settings_system_custom_file_picker),
                    subtitle = stringResource(R.string.settings_system_custom_file_picker_description),
                    trailingContent = {
                        MorpheSwitch(
                            checked = useCustomFilePicker,
                            onCheckedChange = null,
                            modifier = Modifier.semantics {
                                stateDescription = if (useCustomFilePicker) enabledState else disabledState
                            }
                        )
                    }
                )
            }
        }
    }
}
