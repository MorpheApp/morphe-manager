/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.apk.InstalledPatchState

/** User-facing state derived from Morphe's evidence about a tracked install. */
internal data class TrackedInstallPresentation(
    val isPatched: Boolean = false,
    val isDeleted: Boolean = false,
    val isNotPatched: Boolean = false,
    val isUnknown: Boolean = false
)

/**
 * Keeps a present, confirmed non-patched package distinct from a package that is absent.
 *
 * [isDeleted] still accounts for the tracked patched build being gone so existing recovery and
 * filtering behavior stays unchanged. [isNotPatched] lets the UI describe the package that is
 * actually present instead of claiming that no app is installed.
 */
internal fun trackedInstallPresentation(
    installType: InstallType,
    patchState: InstalledPatchState?
): TrackedInstallPresentation = when (patchState) {
    InstalledPatchState.Patched -> TrackedInstallPresentation(isPatched = true)
    InstalledPatchState.NotPatched -> TrackedInstallPresentation(
        isDeleted = installType != InstallType.SAVED,
        isNotPatched = true
    )
    InstalledPatchState.Unknown -> TrackedInstallPresentation(isUnknown = true)
    null -> TrackedInstallPresentation(isDeleted = installType != InstallType.SAVED)
}
