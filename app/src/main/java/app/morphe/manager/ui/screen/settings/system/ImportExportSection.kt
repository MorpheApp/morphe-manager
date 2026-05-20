/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.util.toast

/**
 * Import and export section.
 */
@Composable
fun ImportExportSection(
    importExportViewModel: ImportExportViewModel,
    onImportKeystore: () -> Unit,
    onExportKeystore: () -> Unit,
    onImportSettings: () -> Unit,
    onExportSettings: () -> Unit,
    onExportDebugLogs: () -> Unit
) {
    val context = LocalContext.current
    // Extract strings to avoid LocalContext issues
    val keystoreUnavailable = stringResource(R.string.settings_system_export_keystore_unavailable)

    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)) {
        SectionTitle(
            text = stringResource(R.string.settings_system_import_export),
            icon = Icons.Outlined.SwapHoriz
        )

        SectionCard {
            Column {
                // Keystore
                ImportExportRow(
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.Key) },
                    title = stringResource(R.string.settings_system_keystore),
                    description = stringResource(R.string.settings_system_import_keystore_description),
                    onImport = onImportKeystore,
                    onExport = {
                        if (!importExportViewModel.canExport()) {
                            context.toast(keystoreUnavailable)
                        } else {
                            onExportKeystore()
                        }
                    }
                )

                MorpheSettingsDivider()

                // Manager Settings
                ImportExportRow(
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.Settings) },
                    title = stringResource(R.string.settings_system_morphe_settings),
                    description = stringResource(R.string.settings_system_import_manager_settings_description),
                    onImport = onImportSettings,
                    onExport = onExportSettings
                )

                MorpheSettingsDivider()

                // Debug Logs
                ImportExportRow(
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.BugReport) },
                    title = stringResource(R.string.settings_system_debug),
                    description = stringResource(R.string.settings_system_export_debug_logs_description),
                    onImport = null,
                    onExport = onExportDebugLogs
                )
            }
        }
    }
}
