/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.viewmodel.InstallQueue
import app.morphe.manager.ui.viewmodel.InstallQueueRequest
import app.morphe.manager.ui.viewmodel.InstallViewModel
import app.morphe.manager.ui.viewmodel.labelRes

/**
 * Shows the [InstallQueue] of [installViewModel] while it runs, and returns what starts it.
 *
 * Callers are responsible for placing [app.morphe.manager.ui.screen.settings.system.InstallerFlowDialogs]
 * somewhere in their tree so the installer selection / unavailable dialogs can appear.
 *
 * @param completedPluralRes How the summary counts what the queue got through.
 * @param onDrained invoked with the number of apps installed or mounted once the queue runs out.
 * @return a callback that replaces the current queue with new requests and starts the
 *         first install immediately.
 */
@Composable
fun rememberInstallQueue(
    installViewModel: InstallViewModel,
    @PluralsRes completedPluralRes: Int,
    onDrained: (installed: Int) -> Unit = {}
): (requests: List<InstallQueueRequest>) -> Unit {
    val queue = installViewModel.installQueue
    val installing = installViewModel.installState as? InstallViewModel.InstallState.Installing
    // Up for the whole queue, also while the next item starts and is not Installing yet, so it
    // does not dip between apps. Only the installer's own dialogs take its place
    Overlay(visible = queue.active != null && (installing != null || !queue.installerDialogShowing)) {
        // A mount names the step it is on, as the patcher's install button does
        val caption = installing?.stage?.labelRes
            ?: if (queue.activeMounts) R.string.mounting_ellipsis else R.string.installing_ellipsis
        PulsingLogoWithCaption(
            caption = stringResource(caption),
            // The last app stays named while the overlay fades out after the queue drains
            subtitle = rememberLatest(queue.active?.appName),
            counter = if (queue.total > 1) {
                stringResource(R.string.progress_count, queue.position.toString(), queue.total.toString())
            } else null
        )
    }

    return { requests -> queue.start(requests, completedPluralRes, onDrained) }
}
