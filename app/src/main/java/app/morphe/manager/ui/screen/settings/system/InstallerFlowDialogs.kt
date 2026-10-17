/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.viewmodel.InstallViewModel

/**
 * Renders the installer-selection and installer-unavailable dialogs driven by
 * [InstallViewModel] state. Called from any screen that triggers an install
 * ([app.morphe.manager.ui.screen.HomeScreen], [app.morphe.manager.ui.screen.PatcherScreen],
 * [ApkManagementPage], etc.).
 *
 * Lives in this package because the two dialogs it composes are defined here.
 */
@Composable
fun InstallerFlowDialogs(
    installViewModel: InstallViewModel
) {
    DialogHost(target = installViewModel.installerUnavailableDialog) { dialogState ->
        InstallerUnavailableDialog(
            state = dialogState,
            onOpenApp = installViewModel::openInstallerApp,
            onRetry = installViewModel::retryWithPreferredInstaller,
            onUseFallback = installViewModel::proceedWithFallbackInstaller,
            onDismiss = installViewModel::dismissInstallerUnavailableDialog
        )
    }

    DialogHost(visible = installViewModel.showInstallerSelectionDialog) {
        val selected = remember { installViewModel.getPromptInstallerToken() }
        val options = rememberPolledInstallerEntries(selected, installViewModel::getInstallerOptions)
        InstallerSelectionDialog(
            options = options,
            selected = selected,
            onDismiss = installViewModel::dismissInstallerSelectionDialog,
            onConfirm = installViewModel::proceedWithSelectedInstaller,
            onOpenShizuku = installViewModel::openShizukuApp,
            shizukuStatusProvider = installViewModel::getShizukuStatus,
            onRequestShizukuPermission = installViewModel::requestShizukuPermission
        )
    }
}
