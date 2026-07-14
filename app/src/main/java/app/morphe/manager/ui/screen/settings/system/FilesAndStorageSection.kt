/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.isAndroidTv

/**
 * Files & storage settings section. Owns the entry into the full storage-management screen,
 * the expert-mode patch selection dialog, and the custom file picker toggle.
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
    val deletePatchedApkAfterInstall by settingsViewModel.prefs.deletePatchedApkAfterInstall.getAsState()
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    val showStorageDialog = remember { mutableStateOf(false) }
    val showPatchSelectionDialog = remember { mutableStateOf(false) }

    if (showStorageDialog.value) {
        StorageManagementDialog(onDismissRequest = { showStorageDialog.value = false })
    }

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
            SettingsItem(
                onClick = { showStorageDialog.value = true },
                title = stringResource(R.string.settings_system_storage_management_title),
                subtitle = stringResource(R.string.settings_system_storage_management_description),
                leadingContent = { MorpheIcon(icon = Icons.Outlined.Storage) }
            )

            MorpheSettingsDivider()

            SettingsItem(
                onClick = {
                    settingsViewModel.setDeletePatchedApkAfterInstall(!deletePatchedApkAfterInstall)
                },
                title = stringResource(R.string.settings_system_delete_patched_apk_after_install),
                subtitle = stringResource(R.string.settings_system_delete_patched_apk_after_install_description),
                leadingContent = { MorpheIcon(icon = Icons.Outlined.DeleteSweep) },
                trailingContent = {
                    MorpheSwitch(
                        checked = deletePatchedApkAfterInstall,
                        onCheckedChange = null,
                        modifier = Modifier.semantics {
                            stateDescription = if (deletePatchedApkAfterInstall) enabledState else disabledState
                        }
                    )
                }
            )

            // Patch Selections (Expert mode only)
            if (useExpertMode) {
                MorpheSettingsDivider()

                SettingsItem(
                    onClick = { showPatchSelectionDialog.value = true },
                    title = stringResource(R.string.settings_system_patch_selections_title),
                    subtitle = stringResource(R.string.settings_system_patch_selections_description),
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.Tune) }
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
