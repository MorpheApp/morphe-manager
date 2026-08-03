/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.installer.InstallerManager
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.util.batchActionSummary
import app.morphe.manager.util.toast
import java.io.File

/**
 * A single install request queued by [rememberInstallQueue].
 *
 * @param mountPackageName package to mount when this request targets a saved patched APK and
 *        Mount is the primary installer. Null for apps that patching renamed, since mount
 *        replaces the stock APK in place and cannot serve a different package.
 * @param onPersistApp forwarded to [InstallViewModel.install] or [InstallViewModel.installSavedMount];
 *        runs after a successful install to persist app metadata in the caller's repository.
 * @param onInstalled invoked with the installed package name after a successful install,
 *        before the next queue item starts.
 * @param onFailed invoked with the failure reason when an item is skipped or rejected, for
 *        callers that show per-item state instead of relying on the summary toast alone.
 */
data class InstallQueueRequest(
    val file: File,
    val originalPackageName: String,
    val mountPackageName: String? = null,
    val onPersistApp: suspend (String, InstallType) -> Boolean,
    val onInstalled: (installedPackageName: String) -> Unit = {},
    val onFailed: (message: String?) -> Unit = {}
)

/**
 * Drives sequential installs through a shared [InstallViewModel]. Handles state
 * transitions, skips missing files and dismissed installer dialogs, and toasts a
 * summary once the queue drains.
 *
 * Callers are responsible for placing [app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs]
 * somewhere in their tree so the installer selection / unavailable dialogs can appear.
 *
 * @return a callback that replaces the current queue with new requests and starts the
 *         first install immediately.
 */
@Composable
fun rememberInstallQueue(
    installViewModel: InstallViewModel,
    @PluralsRes completedPluralRes: Int,
    showOverlay: Boolean = true
): (requests: List<InstallQueueRequest>) -> Unit {
    val context = LocalContext.current
    val conflictText = stringResource(R.string.installer_hint_conflict)
    var queue by remember { mutableStateOf<List<InstallQueueRequest>>(emptyList()) }
    var active by remember { mutableStateOf<InstallQueueRequest?>(null) }

    // Whether the current item put an installer dialog on screen. Ready on its own does not
    // mean the user backed out of one, because install() only reaches Installing from a
    // coroutine, leaving a just-started item in the same state as a dismissed one
    var awaitedInstallerDialog by remember { mutableStateOf(false) }
    var completed by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableIntStateOf(0) }

    fun showSummary() {
        context.batchActionSummary(completedPluralRes, completed, skipped)
            ?.let { context.toast(it) }
        completed = 0
        skipped = 0
    }

    fun startNext() {
        val next = queue.firstOrNull()
        if (next == null) {
            active = null
            awaitedInstallerDialog = false
            showSummary()
            return
        }

        queue = queue.drop(1)
        val file = next.file.takeIf { it.exists() }
        if (file == null) {
            skipped++
            startNext()
            return
        }

        active = next
        awaitedInstallerDialog = false
        val mountPackageName = next.mountPackageName
        if (
            mountPackageName != null &&
            installViewModel.getPrimaryInstallerToken() == InstallerManager.Token.AutoSaved
        ) {
            installViewModel.installSavedMount(
                outputFile = file,
                packageName = mountPackageName,
                onPersistApp = next.onPersistApp
            )
        } else {
            installViewModel.install(
                outputFile = file,
                originalPackageName = next.originalPackageName,
                onPersistApp = next.onPersistApp
            )
        }
    }

    LaunchedEffect(
        installViewModel.installState,
        installViewModel.installerUnavailableDialog,
        installViewModel.showInstallerSelectionDialog
    ) {
        val current = active ?: return@LaunchedEffect

        val dialogShowing = installViewModel.installerUnavailableDialog != null ||
                installViewModel.showInstallerSelectionDialog
        if (dialogShowing) awaitedInstallerDialog = true

        when (val state = installViewModel.installState) {
            is InstallViewModel.InstallState.Ready -> {
                // The dialog this item raised is gone and nothing began, so it was dismissed
                if (awaitedInstallerDialog && !dialogShowing) {
                    skipped++
                    current.onFailed(null)
                    active = null
                    startNext()
                }
            }
            is InstallViewModel.InstallState.Installed -> {
                completed++
                current.onInstalled(state.packageName)
                active = null
                installViewModel.resetInstallState()
                startNext()
            }
            is InstallViewModel.InstallState.Error -> {
                skipped++
                current.onFailed(state.message)
                context.toast(state.message)
                active = null
                installViewModel.resetInstallState()
                startNext()
            }
            is InstallViewModel.InstallState.Conflict -> {
                skipped++
                current.onFailed(conflictText)
                context.toast(conflictText)
                active = null
                installViewModel.resetInstallState()
                startNext()
            }
            else -> Unit
        }
    }

    if (showOverlay) {
        Overlay(
            visible = active != null &&
                    installViewModel.installState is InstallViewModel.InstallState.Installing
        ) {
            PulsingLogoWithCaption(caption = stringResource(R.string.installing_ellipsis))
        }
    }

    return { requests ->
        if (requests.isNotEmpty()) {
            queue = requests
            active = null
            awaitedInstallerDialog = false
            completed = 0
            skipped = 0
            installViewModel.resetInstallState()
            startNext()
        }
    }
}
