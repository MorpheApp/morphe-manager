/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.core.net.toUri
import app.morphe.manager.R
import app.morphe.manager.domain.backup.AutoBackup
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.SettingsSection
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.JSON_MIMETYPE
import app.morphe.manager.util.TEXT_MIMETYPE
import app.morphe.manager.util.getRelativeTimeString
import app.morphe.manager.util.pickedFolder
import app.morphe.manager.util.rememberAdaptiveFilePicker
import app.morphe.manager.util.rememberDocumentFolderPicker
import app.morphe.manager.util.rememberDocumentSaver
import app.morphe.manager.util.toFilePath
import app.morphe.manager.worker.ScheduleInterval
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/**
 * Backups of Morphe's own settings on a page of their own, see [SettingsSubpage.SETTINGS_BACKUP]:
 * the files the user moves by hand, then the copies made on their own.
 */
@Composable
fun SettingsBackupSettings(
    settingsViewModel: SettingsViewModel = koinViewModel(),
    importExportViewModel: ImportExportViewModel = koinViewModel()
) {
    SettingsColumn {
        SettingsSection(
            title = stringResource(R.string.settings_section_import_export),
            icon = MaterialSymbols.Rounded.Swap_vert
        ) {
            SettingsGroup {
                TransferItems(importExportViewModel)
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_system_auto_backup),
            icon = MaterialSymbols.Rounded.History
        ) {
            AutoBackupItems(settingsViewModel.prefs, importExportViewModel)
        }
    }
}

/**
 * What the row opening this page says: when the last automatic copy was made, or that it could
 * not be, and what the page is for while there are none.
 */
@Composable
internal fun settingsBackupSummary(prefs: PreferencesManager): String {
    val folder by prefs.autoBackupFolder.getAsState()
    val lastBackupAt by prefs.lastAutoBackupAt.getAsState()
    val failed by prefs.autoBackupFailed.getAsState()
    return when {
        folder.isEmpty() -> stringResource(R.string.settings_system_import_manager_settings_description)
        failed -> stringResource(R.string.settings_system_auto_backup_failed)
        else -> lastBackupSummary(lastBackupAt)
    }
}

/** When the last automatic copy was made, or what the page is for before the first. */
@Composable
private fun lastBackupSummary(lastBackupAt: Long): String =
    if (lastBackupAt > 0) {
        stringResource(R.string.settings_system_auto_backup_last, getRelativeTimeString(lastBackupAt))
    } else {
        stringResource(R.string.settings_system_auto_backup_description)
    }

/**
 * Export and import as rows of a settings group, each behind a dialog that picks the sections it
 * moves. One choice serves both, see [ImportExportViewModel.settingsSections].
 */
@Composable
private fun TransferItems(importExportViewModel: ImportExportViewModel) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    // Replace and Merge only differ for sources and patch selections, so the mode is asked only
    // when one of them is taken
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val importLauncher = rememberAdaptiveFilePicker(
        mimeTypes = arrayOf(JSON_MIMETYPE, TEXT_MIMETYPE),
        customPickerMimeTypes = arrayOf(JSON_MIMETYPE),
        onResult = { uri ->
            uri ?: return@rememberAdaptiveFilePicker
            val sections = importExportViewModel.settingsSections
            if (SettingsSection.SOURCES in sections || SettingsSection.PATCH_SELECTIONS in sections) {
                pendingImportUri = uri
            } else {
                importExportViewModel.importManagerSettings(uri)
            }
        }
    )
    val exportSaver = rememberDocumentSaver(JSON_MIMETYPE, importExportViewModel::exportManagerSettings)

    DialogHost(visible = showExportDialog) {
        SettingsTransferDialog(
            title = stringResource(R.string.settings_system_export_manager_settings_title),
            importExportViewModel = importExportViewModel,
            onExport = { exportSaver("morphe_manager_settings.json") },
            onDismiss = { showExportDialog = false }
        )
    }

    DialogHost(visible = showImportDialog) {
        SettingsTransferDialog(
            title = stringResource(R.string.settings_system_import_manager_settings_mode_title),
            importExportViewModel = importExportViewModel,
            onImport = importLauncher,
            onDismiss = { showImportDialog = false }
        )
    }

    DialogHost(target = pendingImportUri) { uri ->
        ImportModeDialog(
            titleRes = R.string.settings_system_import_manager_settings_mode_title,
            descriptionRes = R.string.settings_system_import_manager_settings_mode_description,
            onDismiss = { pendingImportUri = null },
            onSelect = { mode ->
                importExportViewModel.importManagerSettings(uri, mode)
                pendingImportUri = null
            }
        )
    }

    SettingsItem(
        onClick = { showExportDialog = true },
        leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Upload) },
        title = stringResource(R.string.export),
        subtitle = stringResource(R.string.settings_system_export_manager_settings_description)
    )

    SettingsItem(
        onClick = { showImportDialog = true },
        leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Download) },
        title = stringResource(R.string.import_),
        subtitle = stringResource(R.string.settings_system_import_manager_settings_file_description)
    )
}

/** The sections a settings file moves, picked before the one transfer it offers. */
@Composable
private fun DialogHostScope.SettingsTransferDialog(
    title: String,
    importExportViewModel: ImportExportViewModel,
    onDismiss: () -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null
) {
    ImportExportDialog(
        title = title,
        // The sections below say what the file holds, so this says the one thing no settings
        // backup carries: moving to a new phone with only this file would otherwise look like
        // a complete backup. Held above the list, a list taller than the screen cannot push
        // it out of sight
        description = stringResource(R.string.settings_system_backup_signing_key_note),
        onImport = onImport,
        onExport = onExport,
        onDismiss = onDismiss,
        enabled = importExportViewModel.settingsSections.isNotEmpty()
    ) {
        SettingsBackupContents(
            selected = importExportViewModel.settingsSections,
            onToggle = importExportViewModel::toggleSettingsSection
        )
    }
}

/** Sections of the settings a backup moves, picked the same way for both directions. */
@Composable
private fun SettingsBackupContents(
    selected: Set<SettingsSection>,
    onToggle: (SettingsSection) -> Unit
) {
    SettingsGroup {
        SettingsSection.entries.forEach { section ->
            val isSelected = section in selected
            RadioSelectionCard(
                selected = isSelected,
                onSelect = { onToggle(section) },
                title = stringResource(section.titleRes),
                description = stringResource(section.descriptionRes),
                role = Role.Checkbox,
                leadingContent = {
                    SelectionCheckIndicator(ToggleableState(isSelected))
                }
            )
        }
    }
}

// Named after the places these settings live, so a section reads the same here as where it is set
private val SettingsSection.titleRes: Int
    get() = when (this) {
        SettingsSection.APPEARANCE -> R.string.appearance
        SettingsSection.HOME -> R.string.settings_appearance_home_screen
        SettingsSection.PATCHING -> R.string.settings_system_backup_patching
        SettingsSection.UPDATES -> R.string.settings_section_updates
        SettingsSection.SOURCES -> R.string.sources
        SettingsSection.PATCH_SELECTIONS -> R.string.settings_system_patch_selections_title
    }

private val SettingsSection.descriptionRes: Int
    get() = when (this) {
        SettingsSection.APPEARANCE -> R.string.settings_system_backup_appearance_description
        SettingsSection.HOME -> R.string.settings_system_backup_home_description
        SettingsSection.PATCHING -> R.string.settings_system_backup_patching_description
        SettingsSection.UPDATES -> R.string.settings_system_backup_updates_description
        SettingsSection.SOURCES -> R.string.settings_system_backup_sources_description
        SettingsSection.PATCH_SELECTIONS -> R.string.settings_system_backup_patch_selections_description
    }

/**
 * Automatic backups: the switch, which asks for a folder when turned on, then the folder, how
 * often a copy is made, whether the signing key goes along and a copy made on the spot.
 */
@Composable
private fun AutoBackupItems(
    prefs: PreferencesManager,
    importExportViewModel: ImportExportViewModel
) {
    val folder by prefs.autoBackupFolder.getAsState()
    val interval by prefs.autoBackupInterval.getAsState()
    val includesSigningKey by prefs.autoBackupSigningKey.getAsState()
    val lastBackupAt by prefs.lastAutoBackupAt.getAsState()
    val failed by prefs.autoBackupFailed.getAsState()
    val enabled = folder.isNotEmpty()
    val pickFolder = rememberDocumentFolderPicker(
        suggestedFolderName = stringResource(R.string.settings_system_auto_backup_folder_name),
        onFolderPicked = importExportViewModel::enableAutoBackup
    )
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showSigningKeyWarning by remember { mutableStateOf(false) }

    DialogHost(visible = showIntervalDialog) {
        BackupIntervalDialog(
            selected = interval,
            onSelect = importExportViewModel::setAutoBackupInterval,
            onDismiss = { showIntervalDialog = false }
        )
    }

    // The key signs updates for every patched app, so a copy of it leaving the app is confirmed first
    DialogHost(visible = showSigningKeyWarning) {
        ConfirmDialog(
            title = stringResource(R.string.settings_system_auto_backup_signing_key_warning_title),
            message = stringResource(R.string.settings_system_auto_backup_signing_key_warning_message),
            primaryText = stringResource(R.string.enable),
            isPrimaryDestructive = false,
            onDismiss = { showSigningKeyWarning = false },
            onConfirm = {
                showSigningKeyWarning = false
                importExportViewModel.setAutoBackupSigningKey(true)
            }
        )
    }

    SettingsGroup {
        SettingsSwitchItem(
            checked = enabled,
            onToggle = { if (enabled) importExportViewModel.disableAutoBackup() else pickFolder() },
            icon = MaterialSymbols.Rounded.Backup,
            title = stringResource(R.string.settings_system_auto_backup_switch),
            subtitle = lastBackupSummary(lastBackupAt.takeIf { enabled } ?: 0L)
        )

        FoldingVisibility(visible = enabled) {
            Column {
                SettingsItem(
                    onClick = pickFolder,
                    leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Folder_open) },
                    title = stringResource(R.string.settings_system_auto_backup_folder),
                    subtitle = rememberFolderLabel(folder)
                )
                SettingsItem(
                    onClick = { showIntervalDialog = true },
                    leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Schedule) },
                    title = stringResource(R.string.settings_system_auto_backup_interval),
                    subtitle = stringResource(interval.labelResId)
                )
                SettingsSwitchItem(
                    checked = includesSigningKey,
                    onToggle = {
                        if (includesSigningKey) importExportViewModel.setAutoBackupSigningKey(false)
                        else showSigningKeyWarning = true
                    },
                    icon = MaterialSymbols.Rounded.Key,
                    title = stringResource(R.string.settings_system_auto_backup_signing_key),
                    subtitle = stringResource(R.string.settings_system_auto_backup_signing_key_description)
                )
                SettingsItem(
                    onClick = importExportViewModel::backUpNow,
                    leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Save) },
                    title = stringResource(R.string.settings_system_auto_backup_now)
                )
            }
        }
    }

    // When copies are made, which nothing above says, or that the last one could not be
    FoldingVisibility(visible = enabled) {
        SettingsFooter(
            text = if (failed) {
                stringResource(R.string.settings_system_auto_backup_failed)
            } else {
                stringResource(R.string.settings_system_auto_backup_footer, AutoBackup.KEPT_COPIES)
            },
            tone = if (failed) SemanticTone.Warning else SemanticTone.Neutral
        )
    }
}

/** Picks the least time between two automatic copies. */
@Composable
private fun DialogHostScope.BackupIntervalDialog(
    selected: ScheduleInterval,
    onSelect: (ScheduleInterval) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_auto_backup_interval),
        description = stringResource(R.string.settings_system_auto_backup_interval_description),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        ModeChoice(
            modes = ScheduleInterval.entries,
            selected = selected,
            onSelect = { interval ->
                onSelect(interval)
                onDismiss()
            },
            titleRes = ScheduleInterval::labelResId
        )
    }
}

/** Where [folder] is: its path where it maps to one, else the name its provider gives it. */
@Composable
private fun rememberFolderLabel(folder: String): String {
    val context = LocalContext.current
    // The provider is asked off the main thread, as it can be one that answers slowly
    return produceState(initialValue = "", folder) {
        if (folder.isEmpty()) return@produceState
        value = withContext(Dispatchers.IO) {
            val uri = folder.toUri()
            uri.toFilePath().takeIf { it.startsWith('/') } ?: context.pickedFolder(uri)?.name ?: folder
        }
    }.value
}
