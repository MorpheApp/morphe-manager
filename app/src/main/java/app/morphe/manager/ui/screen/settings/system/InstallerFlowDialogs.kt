/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.viewmodel.InstallViewModel

@Composable
fun InstallerFlowDialogs(
    installViewModel: InstallViewModel
) {
    installViewModel.installerUnavailableDialog?.let { dialogState ->
        InstallerUnavailableDialog(
            state = dialogState,
            onOpenApp = installViewModel::openInstallerApp,
            onRetry = installViewModel::retryWithPreferredInstaller,
            onUseFallback = installViewModel::proceedWithFallbackInstaller,
            onDismiss = installViewModel::dismissInstallerUnavailableDialog
        )
    }

    if (installViewModel.showInstallerSelectionDialog) {
        InstallerSelectionDialog(
            title = stringResource(R.string.installer_title),
            options = installViewModel.getInstallerOptions(),
            selected = installViewModel.getPrimaryInstallerToken(),
            onDismiss = installViewModel::dismissInstallerSelectionDialog,
            onConfirm = installViewModel::proceedWithSelectedInstaller,
            onOpenShizuku = installViewModel::openShizukuApp,
            shizukuStatusProvider = installViewModel::getShizukuStatus,
            onRequestShizukuPermission = installViewModel::requestShizukuPermission
        )
    }
}
