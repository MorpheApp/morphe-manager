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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.model.ApkDownloadHelperHost
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.ApkDownloadHelperContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Wires up the optional APK download helper flow and returns the action that starts it,
 * or null when no trusted helper can serve the request.
 *
 * @param host The patching flow asking for the APK, which is what a result is handed back to.
 * @param enabled Whether a helper may be offered right now. Resolving helpers queries
 * PackageManager, so callers pass true only while the download instructions dialog is on screen.
 */
@Composable
fun rememberApkDownloadHelperAction(
    host: ApkDownloadHelperHost,
    enabled: Boolean,
    prefs: PreferencesManager = koinInject()
): (() -> Unit)? {
    val context = LocalContext.current
    val messages = koinInject<UserMessages>()
    val trustedPackages by prefs.trustedApkDownloadHelpers.getAsState()
    val noResultMessage = stringResource(R.string.home_apk_helper_no_result)
    val noAccessMessage = stringResource(R.string.home_apk_helper_no_access)
    val noPackageMessage = stringResource(R.string.home_apk_helper_no_package)
    var helpers by remember { mutableStateOf(emptyList<ApkDownloadHelperContract.Helper>()) }
    var showPicker by remember { mutableStateOf(false) }

    // Re-resolved every time the dialog opens, so helpers installed mid-session are picked up
    LaunchedEffect(enabled, trustedPackages) {
        helpers = if (enabled && trustedPackages.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                ApkDownloadHelperContract.findHelpers(context)
                    .filter { it.componentName.packageName in trustedPackages }
            }
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
                messages.show(noPackageMessage)
                return@rememberLauncherForActivityResult
            }

            host.onHelperInstalledAppChosen(packageName)
            return@rememberLauncherForActivityResult
        }

        val uri = ApkDownloadHelperContract.resultUri(result.data)
        if (uri == null) {
            messages.show(noResultMessage)
            return@rememberLauncherForActivityResult
        }

        // Reading would fail with a SecurityException anyway, but that surfaces as a generic
        // "try again" error, which is misleading when the helper is the one at fault
        if (!ApkDownloadHelperContract.grantsReadAccess(result.data)) {
            messages.show(noAccessMessage)
            return@rememberLauncherForActivityResult
        }

        host.onHelperApkReceived(uri)
    }

    DialogHost(visible = showPicker && helpers.isNotEmpty()) {
        ApkDownloadHelperDialog(
            helpers = helpers,
            signatureCheckAvailable = host.helperSignatureCheckAvailable,
            onDismiss = { showPicker = false },
            onConfirm = { helper ->
                showPicker = false
                host.createApkDownloadHelperIntent(helper.componentName)?.let { intent ->
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
private fun DialogHostScope.ApkDownloadHelperDialog(
    helpers: List<ApkDownloadHelperContract.Helper>,
    signatureCheckAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ApkDownloadHelperContract.Helper) -> Unit
) {
    var selected by remember(helpers) { mutableStateOf(helpers.firstOrNull()) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_apk_helper_title),
        description = stringResource(R.string.home_apk_helper_description),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.home_apk_helper_continue),
                onPrimaryClick = { selected?.let(onConfirm) },
                primaryIcon = Icons.Outlined.Download,
                primaryEnabled = selected != null,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            SettingsGroup {
                helpers.forEach { helper ->
                    RadioSelectionCard(
                        selected = selected == helper,
                        onSelect = { selected = helper },
                        title = helper.label,
                        description = helper.componentName.packageName
                    )
                }
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
