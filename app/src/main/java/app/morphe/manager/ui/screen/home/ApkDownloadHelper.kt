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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.model.ApkDownloadHelperHost
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.ApkDownloadHelperContract
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * What the download instructions can offer of the APK download helpers installed.
 *
 * @param open Starts the helper flow with one of those trusted, null while none is.
 * @param hasUntrusted Whether one is installed that is not trusted yet, which only the settings
 *   let in, while none trusted is there to download with instead.
 * @param opensDirectly Whether the user asked for the helper flow to start as soon as an APK is
 *   needed, see [showInstructions].
 * @param opensAutomatically Starts the first trusted helper without asking, null while none is.
 * @param isResolved Whether the installed helpers were looked up already.
 */
@Immutable
class ApkDownloadHelperOffer(
    val open: (() -> Unit)?,
    val hasUntrusted: Boolean,
    private val opensDirectly: Boolean,
    private val opensAutomatically: (() -> Boolean)?,
    val isResolved: Boolean
) {
    /** Whether a helper is there for a flow that asks for one on its own. */
    val canOpenAutomatically get() = opensAutomatically != null

    /** Starts the first trusted helper for an automatic download, false when it could not. */
    fun openAutomatically(): Boolean = opensAutomatically?.invoke() == true

    /**
     * Shows the download instructions with [show] and starts the helper flow over them at once
     * where the user asked for that. The instructions stay behind it for when the helper finds nothing.
     */
    fun showInstructions(show: () -> Unit) {
        show()
        if (opensDirectly) open?.invoke()
    }
}

/**
 * Wires up the optional APK download helper flow and hands what it can offer, see
 * [ApkDownloadHelperOffer], to the dialogs in [content]. The picker is composed after them, so one
 * opening in the same frame as a dialog stands over it rather than under it.
 *
 * @param host The patching flow asking for the APK, which is what a result is handed back to.
 * @param enabled Whether a helper may be offered right now. Resolving helpers queries
 * PackageManager, so callers pass true only while a helper could start: one of the flow's dialogs
 * is on screen, or the flow is preparing to download APKs on its own.
 */
@Composable
fun WithApkDownloadHelpers(
    host: ApkDownloadHelperHost,
    enabled: Boolean,
    prefs: PreferencesManager = koinInject(),
    content: @Composable (ApkDownloadHelperOffer) -> Unit
) {
    val context = LocalContext.current
    val messages = koinInject<UserMessages>()
    val trustedPackages by prefs.trustedApkDownloadHelpers.getAsState()
    val openDirectly by prefs.openApkDownloadHelperDirectly.getAsState()
    val noResultMessage = stringResource(R.string.home_apk_helper_no_result)
    val noAccessMessage = stringResource(R.string.home_apk_helper_no_access)
    val noPackageMessage = stringResource(R.string.home_apk_helper_no_package)
    var installed by remember { mutableStateOf(emptyList<ApkDownloadHelperContract.Helper>()) }
    var resolved by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    // Re-resolved every time an APK is asked for, so helpers installed mid-session are picked up
    LaunchedEffect(enabled) {
        if (enabled) {
            installed = withContext(Dispatchers.IO) { ApkDownloadHelperContract.findHelpers(context) }
            resolved = true
        } else {
            installed = emptyList()
            resolved = false
        }
    }
    val helpers = installed.filter { it.componentName.packageName in trustedPackages }

    val helperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            host.onHelperDismissed()
            return@rememberLauncherForActivityResult
        }

        if (ApkDownloadHelperContract.isInstalledAppResult(result.data)) {
            val packageName = ApkDownloadHelperContract.resultInstalledPackageName(result.data)
            if (packageName == null) {
                messages.show(noPackageMessage)
                host.onHelperDismissed()
                return@rememberLauncherForActivityResult
            }

            host.onHelperInstalledAppChosen(packageName)
            return@rememberLauncherForActivityResult
        }

        val uri = ApkDownloadHelperContract.resultUri(result.data)
        if (uri == null) {
            messages.show(noResultMessage)
            host.onHelperDismissed()
            return@rememberLauncherForActivityResult
        }

        // Reading would fail with a SecurityException anyway, but that surfaces as a generic
        // "try again" error, which is misleading when the helper is the one at fault
        if (!ApkDownloadHelperContract.grantsReadAccess(result.data)) {
            messages.show(noAccessMessage)
            host.onHelperDismissed()
            return@rememberLauncherForActivityResult
        }

        host.onHelperApkReceived(uri)
    }

    /** @return false when the flow has nothing left to ask about, or the helper is gone. */
    fun launch(helper: ApkDownloadHelperContract.Helper): Boolean {
        val intent = host.createApkDownloadHelperIntent(helper.componentName) ?: return false
        return try {
            helperLauncher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            // The helper was uninstalled between resolving and launching it
            installed = installed - helper
            false
        }
    }

    // A user who wants the helper right away has nothing left to confirm with a single one trusted,
    // unless the APK it brings cannot be verified, which only the picker warns about
    val open: () -> Unit = {
        val helper = helpers.singleOrNull()?.takeIf { openDirectly && host.helperSignatureCheckAvailable }
        if (helper != null) launch(helper) else showPicker = true
    }

    // The first trusted helper answers for a flow that downloads on its own, since stopping for a
    // pick would undo the point of asking for that flow
    val openAutomatically: (() -> Boolean)? = helpers.firstOrNull()?.let { helper ->
        { launch(helper) }
    }

    content(
        ApkDownloadHelperOffer(
            open = open.takeIf { helpers.isNotEmpty() },
            hasUntrusted = helpers.isEmpty() && installed.any { it.componentName.packageName !in trustedPackages },
            opensDirectly = openDirectly,
            opensAutomatically = openAutomatically,
            isResolved = resolved
        )
    )

    DialogHost(visible = showPicker && helpers.isNotEmpty()) {
        ApkDownloadHelperDialog(
            helpers = helpers,
            signatureCheckAvailable = host.helperSignatureCheckAvailable,
            onDismiss = { showPicker = false },
            onConfirm = { helper ->
                showPicker = false
                launch(helper)
            }
        )
    }
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
                primaryIcon = MaterialSymbols.Rounded.Download,
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

            Footnote(
                text = stringResource(
                    if (signatureCheckAvailable) {
                        R.string.home_apk_helper_warning
                    } else {
                        R.string.home_apk_helper_warning_unverified
                    }
                ),
                tone = SemanticTone.Warning
            )
        }
    }
}
