/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.AutoUpdates
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Rows of a settings group for updates of the manager and its sources: pre-releases, how far
 * they update on their own and, in expert mode, the GitHub token the update checks are made with.
 */
@Composable
fun UpdatesItems(
    settingsViewModel: SettingsViewModel,
    onManagerPrereleasesToggle: () -> Unit
) {
    val prefs = settingsViewModel.prefs
    val backgroundUpdateNotifications by prefs.backgroundUpdateNotifications.getAsState()
    val useManagerPrereleases by prefs.useManagerPrereleases.getAsState()
    val autoUpdates by prefs.autoUpdates.getAsState()
    val showAutoUpdates = remember { mutableStateOf(false) }
    val usePatchesPrereleases by prefs.bundlePrereleasesEnabled.getAsState()
    val useExpertMode by prefs.useExpertMode.getAsState()
    val showPrereleaseWarning = remember { mutableStateOf(false) }

    fun applyManagerPrereleases() {
        settingsViewModel.toggleManagerPrereleases(
            currentValue = useManagerPrereleases,
            backgroundNotificationsEnabled = backgroundUpdateNotifications,
            patchesPrereleaseIds = usePatchesPrereleases,
            onCheckUpdate = onManagerPrereleasesToggle
        )
    }

    DialogHost(visible = showPrereleaseWarning.value) {
        ConfirmDialog(
            title = stringResource(R.string.settings_advanced_updates_prerelease_warning_title),
            message = stringResource(R.string.settings_advanced_updates_prerelease_warning_message),
            primaryText = stringResource(R.string.enable),
            isPrimaryDestructive = false,
            onDismiss = { showPrereleaseWarning.value = false },
            onConfirm = {
                showPrereleaseWarning.value = false
                applyManagerPrereleases()
            }
        )
    }

    // Use manager prereleases toggle
    SettingsSwitchItem(
        checked = useManagerPrereleases,
        onToggle = {
            if (useManagerPrereleases) {
                applyManagerPrereleases()
            } else {
                // Explain what pre-release means, and that patches are separate, before flipping it on
                showPrereleaseWarning.value = true
            }
        },
        icon = MaterialSymbols.Rounded.Science,
        title = stringResource(R.string.settings_advanced_updates_manager_prereleases),
        subtitle = stringResource(R.string.settings_advanced_updates_manager_prereleases_description)
    )

    DialogHost(visible = showAutoUpdates.value) {
        AutoUpdatesDialog(
            selected = autoUpdates,
            onSelect = settingsViewModel::setAutoUpdates,
            onDismiss = { showAutoUpdates.value = false }
        )
    }

    SettingsItem(
        onClick = { showAutoUpdates.value = true },
        icon = MaterialSymbols.Rounded.Sync,
        title = stringResource(R.string.settings_auto_updates),
        subtitle = stringResource(autoUpdates.titleRes)
    )

    if (useExpertMode) {
        GitHubPatSettingsItem(settingsViewModel)
    }
}

/** Picks how far the manager and its sources update on their own, see [AutoUpdates]. */
@Composable
private fun DialogHostScope.AutoUpdatesDialog(
    selected: AutoUpdates,
    onSelect: (AutoUpdates) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_auto_updates),
        description = stringResource(R.string.settings_auto_updates_description),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        ModeChoice(
            modes = AutoUpdates.entries,
            selected = selected,
            onSelect = { mode ->
                onSelect(mode)
                onDismiss()
            },
            titleRes = AutoUpdates::titleRes,
            descriptionRes = AutoUpdates::descriptionRes
        )
    }
}
