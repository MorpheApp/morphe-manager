/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.util.ApkDownloadHelperContract
import app.morphe.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wires up the optional APK download helper flow and returns the action that starts it,
 * or null when no helper can serve the request.
 *
 * @param enabled Whether a helper may be used at all. Resolving helpers queries PackageManager,
 * so callers pass true only while the download instructions dialog is on screen.
 */
@Composable
fun rememberApkDownloadHelperAction(
    homeViewModel: HomeViewModel,
    enabled: Boolean
): (() -> Unit)? {
    val context = LocalContext.current
    val noResultMessage = stringResource(R.string.home_apk_helper_no_result)
    val noAccessMessage = stringResource(R.string.home_apk_helper_no_access)
    val noPackageMessage = stringResource(R.string.home_apk_helper_no_package)
    var helpers by remember { mutableStateOf(emptyList<ApkDownloadHelperContract.Helper>()) }
    var showPicker by remember { mutableStateOf(false) }

    // Re-resolved every time the dialog opens, so helpers installed mid-session are picked up
    LaunchedEffect(enabled) {
        helpers = if (enabled) {
            withContext(Dispatchers.IO) { ApkDownloadHelperContract.findHelpers(context) }
        } else {
            emptyList()
        }
    }

    val helperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        if (ApkDownloadHelperContract.isInstalledAppResult(result.data)) {
            val packageName = ApkDownloadHelperContract.resultInstalledPackageName(result.data)
            if (packageName == null) {
                context.toast(noPackageMessage)
                return@rememberLauncherForActivityResult
            }

            homeViewModel.showDownloadInstructionsDialog = false
            homeViewModel.showFilePickerPromptDialog = false
            homeViewModel.handleHelperInstalledAppSelection(packageName)
            return@rememberLauncherForActivityResult
        }

        val uri = ApkDownloadHelperContract.resultUri(result.data)
        if (uri == null) {
            context.toast(noResultMessage)
            return@rememberLauncherForActivityResult
        }

        // Reading would fail with a SecurityException anyway, but that surfaces as a generic
        // "try again" error, which is misleading when the helper is the one at fault
        if (!ApkDownloadHelperContract.grantsReadAccess(result.data)) {
            context.toast(noAccessMessage)
            return@rememberLauncherForActivityResult
        }

        homeViewModel.showDownloadInstructionsDialog = false
        homeViewModel.showFilePickerPromptDialog = false
        homeViewModel.handleApkSelection(uri)
    }

    if (showPicker && helpers.isNotEmpty()) {
        ApkDownloadHelperDialog(
            helpers = helpers,
            signatureCheckAvailable = homeViewModel.pendingApkSignatureCheckAvailable,
            onDismiss = { showPicker = false },
            onConfirm = { helper ->
                showPicker = false
                homeViewModel.createApkDownloadHelperIntent(helper.componentName)?.let { intent ->
                    try {
                        helperLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        // The helper was uninstalled between resolving and launching it
                        helpers = helpers - helper
                    }
                }
            }
        )
    }

    val openPicker = remember { { showPicker = true } }
    return if (helpers.isEmpty()) null else openPicker
}

/**
 * Names the app the APK will come from and lets the user confirm the hand-off,
 * so a helper can never download on Morphe's behalf unnoticed.
 */
@Composable
private fun ApkDownloadHelperDialog(
    helpers: List<ApkDownloadHelperContract.Helper>,
    signatureCheckAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ApkDownloadHelperContract.Helper) -> Unit
) {
    var selected by remember(helpers) { mutableStateOf(helpers.firstOrNull()) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_apk_helper_title),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.home_apk_helper_continue),
                onPrimaryClick = { selected?.let(onConfirm) },
                primaryIcon = Icons.Outlined.Download,
                primaryEnabled = selected != null,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss,
                layout = DialogButtonLayout.Vertical
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
        ) {
            Text(
                text = stringResource(R.string.home_apk_helper_description),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current
            )

            helpers.forEach { helper ->
                RadioSelectionCard(
                    selected = selected == helper,
                    onSelect = { selected = helper },
                    title = helper.label,
                    description = helper.componentName.packageName
                )
            }

            Notice(
                text = stringResource(
                    if (signatureCheckAvailable) {
                        R.string.home_apk_helper_warning
                    } else {
                        R.string.home_apk_helper_warning_unverified
                    }
                ),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.Warning
            )
        }
    }
}
