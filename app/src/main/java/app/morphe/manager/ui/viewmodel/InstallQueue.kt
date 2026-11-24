/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import androidx.annotation.PluralsRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.installed.reinstallsByMount
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.batchActionSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * A single install request queued on an [InstallQueue].
 *
 * @param appName the name the install overlay shows while this request runs.
 * @param installedApp the tracked app a saved patched APK belongs to, which decides with the
 *        preferred installer whether it is mounted. Null for an APK that belongs to no record.
 * @param builtForMount whether [file] was patched for a root mount, which only works mounted
 *        over [originalPackageName] whatever the record or the preferred installer say.
 * @param onPersistApp forwarded to [InstallViewModel.install] or [InstallViewModel.installSavedMount];
 *        runs after a successful install to persist app metadata in the caller's repository.
 * @param onInstalled invoked with the installed package name after a successful install,
 *        before the next queue item starts.
 * @param onFailed invoked with the failure reason when an item is skipped or rejected, for
 *        callers that show per-item state instead of relying on the summary message alone.
 */
data class InstallQueueRequest(
    val file: File,
    val appName: String,
    val originalPackageName: String,
    val installedApp: InstalledApp? = null,
    val builtForMount: Boolean = false,
    val onPersistApp: suspend (String, InstallType) -> Boolean,
    val onInstalled: (installedPackageName: String) -> Unit = {},
    val onFailed: (message: String?) -> Unit = {}
)

/**
 * Sequential installs through the [installer] it belongs to. Skips missing files and dismissed
 * installer dialogs, and reports a summary once the queue drains.
 *
 * Kept by the view model rather than the screen, so a queue goes on to its end whatever the user
 * opens over it meanwhile.
 */
class InstallQueue internal constructor(
    private val installer: InstallViewModel,
    scope: CoroutineScope,
    private val app: Application,
    private val messages: UserMessages
) {
    private var queue = emptyList<InstallQueueRequest>()
    @PluralsRes private var completedPluralRes = R.plurals.batch_install_summary
    private var onDrained: (installed: Int) -> Unit = {}

    /** The request installing now, null while the queue is idle. */
    var active by mutableStateOf<InstallQueueRequest?>(null)
        private set

    /** Size of the queue as it started, so the overlay can say which item of how many is running. */
    var total by mutableIntStateOf(0)
        private set

    /** Position of [active] in the queue, counted from one. */
    var position by mutableIntStateOf(0)
        private set

    /** Whether [active] is mounted rather than installed, which the overlay says. */
    var activeMounts by mutableStateOf(false)
        private set

    // Whether the current item put an installer dialog on screen. Ready on its own does not
    // mean the user backed out of one, because install() only reaches Installing from a
    // coroutine, leaving a just-started item in the same state as a dismissed one
    private var awaitedInstallerDialog = false
    private var completed = 0
    // Counted apart from the installs, so the summary does not call a mount one
    private var mounted = 0
    private var skipped = 0

    /** Whether the installer has a dialog of its own up, which the overlay makes way for. */
    val installerDialogShowing: Boolean
        get() = installer.installerUnavailableDialog != null || installer.showInstallerSelectionDialog

    init {
        scope.launch {
            snapshotFlow {
                Triple(installer.installState, installer.installerUnavailableDialog, installer.showInstallerSelectionDialog)
            }.collect { onInstallerChanged() }
        }
    }

    /**
     * Replaces the queue with [requests] and starts the first install.
     *
     * @param completedPluralRes How the summary counts what the queue got through.
     * @param onDrained Called with the number of apps installed or mounted once the queue runs out.
     */
    fun start(
        requests: List<InstallQueueRequest>,
        @PluralsRes completedPluralRes: Int,
        onDrained: (installed: Int) -> Unit = {}
    ) {
        if (requests.isEmpty()) return
        queue = requests
        total = requests.size
        position = 0
        this.completedPluralRes = completedPluralRes
        this.onDrained = onDrained
        active = null
        awaitedInstallerDialog = false
        completed = 0
        mounted = 0
        skipped = 0
        installer.resetInstallState()
        startNext()
    }

    private fun startNext() {
        val next = queue.firstOrNull()
        if (next == null) {
            active = null
            awaitedInstallerDialog = false
            app.batchActionSummary(completedPluralRes, completed, skipped, mounted)
                ?.let { messages.show(it) }
            onDrained(completed + mounted)
            return
        }

        queue = queue.drop(1)
        position++
        val file = next.file.takeIf { it.exists() }
        if (file == null) {
            // The patched APK went away, which its card reports like any other failure
            next.onFailed(null)
            skipped++
            startNext()
            return
        }

        active = next
        awaitedInstallerDialog = false
        val installedApp = next.installedApp
        val mountPackageName = when {
            next.builtForMount -> next.originalPackageName
            installedApp != null && installedApp.reinstallsByMount(installer.mountsByDefault()) ->
                installedApp.currentPackageName
            else -> null
        }
        activeMounts = mountPackageName != null
        if (mountPackageName != null) {
            installer.installSavedMount(
                outputFile = file,
                packageName = mountPackageName,
                onPersistApp = next.onPersistApp
            )
        } else {
            installer.install(
                outputFile = file,
                originalPackageName = next.originalPackageName,
                onPersistApp = next.onPersistApp
            )
        }
    }

    private fun onInstallerChanged() {
        val current = active ?: return

        if (installerDialogShowing) awaitedInstallerDialog = true

        when (val state = installer.installState) {
            is InstallViewModel.InstallState.Ready -> {
                // The dialog this item raised is gone and nothing began, so it was dismissed
                if (awaitedInstallerDialog && !installerDialogShowing) {
                    skipped++
                    current.onFailed(null)
                    finishActive(reset = false)
                }
            }
            is InstallViewModel.InstallState.Installed -> {
                if (activeMounts) mounted++ else completed++
                current.onInstalled(state.packageName)
                finishActive()
            }
            is InstallViewModel.InstallState.Error -> {
                skipped++
                current.onFailed(state.message)
                messages.show(state.message)
                finishActive()
            }
            is InstallViewModel.InstallState.Conflict -> {
                val conflictText = app.getString(R.string.installer_hint_conflict)
                skipped++
                current.onFailed(conflictText)
                messages.show(conflictText)
                finishActive()
            }
            else -> Unit
        }
    }

    private fun finishActive(reset: Boolean = true) {
        active = null
        if (reset) installer.resetInstallState()
        startNext()
    }
}
