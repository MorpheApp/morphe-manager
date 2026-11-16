/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.home.ManagerChangelogDialog
import app.morphe.manager.ui.screen.settings.SettingsSubpage
import app.morphe.manager.ui.screen.settings.SubpageItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.ImportExportViewModel
import app.morphe.manager.util.TEXT_MIMETYPE
import app.morphe.manager.util.isolateLtr
import app.morphe.manager.util.rememberDocumentSaver
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel

/**
 * The manager itself, closing the settings page as a section of its own: its version, which opens
 * [AboutDialog], over the row opening its changelog, licenses and help, see [AboutSettings].
 */
@Composable
fun AboutGroup(onOpenSubpage: (SettingsSubpage) -> Unit) {
    val logo = rememberMorpheLogoBitmap()
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    DialogHost(visible = showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    SettingsSection(
        title = stringResource(R.string.settings_system_about),
        icon = MaterialSymbols.Rounded.Info
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
            SubpageItem(SettingsSubpage.ABOUT, onOpenSubpage)
        }
    }
}

/**
 * What there is to know about the manager past its version, and help with it, on a page of its
 * own, see [SettingsSubpage.ABOUT]: its changelog and licenses, the logs a bug report
 * asks for and the tour.
 *
 * @param onOpenLicenses Opens the open-source licenses the manager is built on.
 * @param onStartTour Restarts the onboarding tour, when no tour is running.
 */
@Composable
fun AboutSettings(
    onOpenLicenses: () -> Unit,
    onStartTour: (() -> Unit)?,
    importExportViewModel: ImportExportViewModel = koinViewModel()
) {
    var showChangelogDialog by remember { mutableStateOf(false) }

    val exportDebugLogs = rememberDocumentSaver(TEXT_MIMETYPE, importExportViewModel::exportDebugLogs)

    DialogHost(visible = showChangelogDialog) {
        ManagerChangelogDialog(onDismiss = { showChangelogDialog = false })
    }

    SettingsColumn {
        SettingsSection(
            title = stringResource(R.string.settings_about_section_project),
            icon = MaterialSymbols.Rounded.Info
        ) {
            SettingsGroup {
                SettingsItem(
                    icon = MaterialSymbols.Rounded.Article.autoMirrored,
                    title = stringResource(R.string.changelog),
                    subtitle = stringResource(R.string.changelog_description),
                    onClick = { showChangelogDialog = true }
                )
                SettingsItem(
                    icon = MaterialSymbols.Rounded.Gavel,
                    title = stringResource(R.string.opensource_licenses),
                    subtitle = stringResource(R.string.opensource_licenses_description),
                    onClick = onOpenLicenses
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_about_section_help),
            icon = MaterialSymbols.Rounded.Help.autoMirrored
        ) {
            SettingsGroup {
                // Logs are what a bug report asks for
                SettingsItem(
                    icon = MaterialSymbols.Rounded.Bug_report,
                    title = stringResource(R.string.settings_system_export_debug_logs),
                    subtitle = stringResource(R.string.settings_system_export_debug_logs_description),
                    onClick = { exportDebugLogs(importExportViewModel.debugLogFileName) },
                    // Saves a file rather than opening a page, marked as the other exports are
                    trailingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Upload) }
                )
                if (onStartTour != null) {
                    SettingsItem(
                        icon = MaterialSymbols.Rounded.Lightbulb,
                        title = stringResource(R.string.onboarding_restart_title),
                        subtitle = stringResource(R.string.onboarding_restart_desc),
                        onClick = onStartTour
                    )
                }
            }
        }
    }
}
