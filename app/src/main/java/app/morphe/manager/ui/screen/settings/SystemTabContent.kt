/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.settings.system.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.ui.viewmodel.SettingsViewModel

/**
 * System tab content.
 */
@Composable
fun SystemTabContent(
    settingsViewModel: SettingsViewModel,
    onShowInstallerDialog: () -> Unit,
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit,
    onImportSettings: () -> Unit,
    onExportSettings: () -> Unit,
    onExportDebugLogs: () -> Unit,
    onAboutClick: () -> Unit,
    onChangelogClick: () -> Unit
) {
    val useExpertMode by settingsViewModel.prefs.useExpertMode.getAsState()
    val useCustomFilePicker by settingsViewModel.prefs.useCustomFilePicker.getAsState()

    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Installers
        SectionTitle(
            text = stringResource(R.string.installer),
            icon = Icons.Outlined.InstallMobile
        )

        SectionCard {
            InstallerSection(
                settingsViewModel = settingsViewModel,
                onShowInstallerDialog = onShowInstallerDialog
            )
        }

        // Performance
        PerformanceSection(settingsViewModel = settingsViewModel)

        // Import & Export (Expert mode only)
        if (useExpertMode) {
            ImportExportSection(
                importExportViewModel = importExportViewModel,
                onImportKeystore = onImportKeystore,
                onExportKeystore = onExportKeystore,
                onImportSettings = onImportSettings,
                onExportSettings = onExportSettings,
                onExportDebugLogs = onExportDebugLogs
            )
        }

        // Files
        SectionTitle(
            text = stringResource(R.string.settings_system_files),
            icon = Icons.Outlined.FolderOpen
        )

        SectionCard {
            RichSettingsItem(
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

        // Storage Management
        StorageManagementSection(
            settingsViewModel = settingsViewModel,
            importExportViewModel = importExportViewModel
        )

        // About
        SectionTitle(
            text = stringResource(R.string.settings_system_about),
            icon = Icons.Outlined.Info
        )

        SectionCard {
            AboutSection(
                onAboutClick = onAboutClick,
                onChangelogClick = onChangelogClick
            )
        }
    }
}
