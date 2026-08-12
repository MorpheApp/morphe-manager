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
    val isUnknown: Boolean = false,
    /** Whether the package currently installed on the device is safe to identify in the UI. */
    val showsInstalledPackage: Boolean = false
)

/**
 * Chooses which APK should supply the visible icon, label, and version for a tracked record.
 * Unknown packages deliberately keep showing Morphe's retained artifact rather than assigning
 * that record to a package whose identity could not be established.
 */
internal fun <T> TrackedInstallPresentation.displayedPackageInfo(
    installedPackageInfo: T?,
    savedPackageInfo: T?
): T? = if (showsInstalledPackage) {
    installedPackageInfo ?: savedPackageInfo
} else {
    savedPackageInfo
}

/**
 * Keeps the untracked-app resolver fallback out of tracked states. In particular, an Unknown
 * tracked package with no retained APK must stay metadata-free instead of silently revealing the
 * package currently occupying its name.
 */
internal fun <T> displayedHomePackageInfo(
    trackedPresentation: TrackedInstallPresentation?,
    installedPackageInfo: T?,
    savedPackageInfo: T?,
    untrackedPackageInfo: T?
): T? = if (trackedPresentation == null) {
    untrackedPackageInfo
} else {
    trackedPresentation.displayedPackageInfo(installedPackageInfo, savedPackageInfo)
}

/**
 * Keeps a present, confirmed non-patched package distinct from a package that is absent.
 *
 * A confirmed replacement is present, not deleted, but it remains distinct from both the patched
 * build in Morphe's record and an app that has never been patched. [showsInstalledPackage] lets
 * the UI use the replacement's current icon and version only when its identity is confirmed.
 */
internal fun trackedInstallPresentation(
    installType: InstallType,
    patchState: InstalledPatchState?
): TrackedInstallPresentation = when (patchState) {
    InstalledPatchState.Patched -> TrackedInstallPresentation(
        isPatched = true,
        showsInstalledPackage = true
    )
    InstalledPatchState.NotPatched -> TrackedInstallPresentation(
        isNotPatched = true,
        showsInstalledPackage = true
    )
    InstalledPatchState.Unknown -> TrackedInstallPresentation(isUnknown = true)
    null -> TrackedInstallPresentation(isDeleted = installType != InstallType.SAVED)
}
