/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.app.Application
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.apk.InstalledPatchState
import app.morphe.manager.domain.apk.LocalApkSources
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.BATCH_UNINSTALL_TIMEOUT
import app.morphe.manager.util.batchActionSummary
import app.morphe.manager.util.simpleMessage
import kotlinx.coroutines.withTimeoutOrNull

/** Removes apps from the device one after another, reporting the whole batch in one summary. */
class BatchUninstaller(
    private val app: Application,
    private val installerManager: InstallerManager,
    private val installedAppRepository: InstalledAppRepository,
    private val localApkSources: LocalApkSources,
    private val messages: UserMessages
) {
    /**
     * One app to remove.
     *
     * @param tracked The record of the patched app, if it is one. Its package is only removed once
     *   the device still holds the build the record describes, so an app the user replaced since
     *   is left alone. A mount is the exception: it only lifts a bind mount and its module.
     * @param verify Whether a package needs that proof at all, which an unpatched app has no way to give.
     */
    class Target(
        val packageName: String,
        val installType: InstallType?,
        val tracked: InstalledApp? = null,
        val verify: Boolean = true
    )

    /** Removes [targets] in turn, calling [onRemoved] for each one that is gone. */
    suspend fun uninstall(
        targets: List<Target>,
        onRemoved: (Target) -> Unit = {}
    ) {
        var completed = 0
        var skipped = 0
        var unverified = 0
        for (target in targets) {
            runCatching {
                val mount = target.installType == InstallType.MOUNT
                if (target.verify && !mount &&
                    (target.tracked == null ||
                            localApkSources.trackedPatchState(target.tracked) != InstalledPatchState.Patched)
                ) {
                    unverified++
                    return@runCatching false
                }
                val removed = withTimeoutOrNull(BATCH_UNINSTALL_TIMEOUT) {
                    installerManager.uninstallPackage(target.packageName, target.installType)
                    true
                } == true
                if (!removed) error(app.getString(R.string.uninstall_timeout))
                // A mount leaves the stock app behind, which the record no longer describes
                if (mount && target.tracked != null) installedAppRepository.delete(target.tracked)
                true
            }.onSuccess { removed ->
                if (removed) {
                    completed++
                    onRemoved(target)
                } else {
                    skipped++
                }
            }.onFailure { error ->
                skipped++
                if (error !is UninstallCancelledException) {
                    messages.show(R.string.uninstall_app_fail, error.simpleMessage())
                }
            }
        }

        if (unverified > 0) messages.show(R.string.uninstall_app_unverified)
        app.batchActionSummary(R.plurals.batch_uninstall_summary, completed, skipped)
            ?.let { messages.show(it) }
    }
}
