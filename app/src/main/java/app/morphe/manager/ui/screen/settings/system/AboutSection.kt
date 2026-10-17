/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.ManagerChangelogDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.util.TEXT_MIMETYPE
import app.morphe.manager.util.isAndroidTv
import app.morphe.manager.util.isolateLtr
import org.koin.androidx.compose.koinViewModel

/**
 * The manager itself: its version, changelog, the logs a bug report asks for and the tour.
 *
 * @param onStartTour Restarts the onboarding tour, when no tour is running.
 */
@Composable
fun AboutSection(
    onStartTour: (() -> Unit)?,
    importExportViewModel: ImportExportViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val isTV = remember { context.isAndroidTv() }
    val logo = rememberMorpheLogoBitmap()
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    val exportDebugLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(TEXT_MIMETYPE)
    ) { uri -> uri?.let { importExportViewModel.exportDebugLogs(it) } }

    DialogHost(visible = showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    DialogHost(visible = showChangelogDialog) {
        ManagerChangelogDialog(onDismiss = { showChangelogDialog = false })
    }

    SettingsSection(
        title = stringResource(R.string.settings_system_about),
        icon = Icons.Outlined.Info
    ) {
        SettingsGroup {
            SettingsItem(
                onClick = { showAboutDialog = true },
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.version) + " " + BuildConfig.VERSION_NAME.isolateLtr(),
                leadingContent = {
                    logo?.let {
                        Icon(
                            bitmap = it,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Defaults.IconSize)
                        )
                    }
                }
            )

            SettingsItem(
                icon = Icons.AutoMirrored.Outlined.Article,
                title = stringResource(R.string.changelog),
                subtitle = stringResource(R.string.changelog_description),
                onClick = { showChangelogDialog = true }
            )

            // Next to the changelog rather than with the backups: logs are what a bug report asks for
            SettingsItem(
                icon = Icons.Outlined.BugReport,
                title = stringResource(R.string.settings_system_export_debug_logs),
                subtitle = stringResource(R.string.settings_system_export_debug_logs_description),
                onClick = {
                    if (isTV) importExportViewModel.exportDebugLogsToDownloads()
                    else exportDebugLogsLauncher.launch(importExportViewModel.debugLogFileName)
                }
            )

            if (onStartTour != null) {
                SettingsItem(
                    icon = Icons.Outlined.Lightbulb,
                    title = stringResource(R.string.onboarding_restart_title),
                    subtitle = stringResource(R.string.onboarding_restart_desc),
                    onClick = onStartTour
                )
            }
        }
    }
}
