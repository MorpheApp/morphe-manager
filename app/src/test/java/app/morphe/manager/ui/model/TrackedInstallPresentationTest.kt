/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.apk.InstalledPatchState
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackedInstallPresentationTest {
    @Test
    fun `confirmed patched install is presented as installed`() {
        assertEquals(
            TrackedInstallPresentation(isPatched = true, showsInstalledPackage = true),
            trackedInstallPresentation(InstallType.DEFAULT, InstalledPatchState.Patched)
        )
    }

    @Test
    fun `missing tracked install is presented as deleted`() {
        assertEquals(
            TrackedInstallPresentation(isDeleted = true),
            trackedInstallPresentation(InstallType.DEFAULT, null)
        )
    }

    @Test
    fun `confirmed replacement is present and distinct from a never-patched app`() {
        assertEquals(
            TrackedInstallPresentation(isNotPatched = true, showsInstalledPackage = true),
            trackedInstallPresentation(InstallType.DEFAULT, InstalledPatchState.NotPatched)
        )
    }

    @Test
    fun `confirmed replacement displays current package information`() {
        val presentation = trackedInstallPresentation(
            InstallType.DEFAULT,
            InstalledPatchState.NotPatched
        )

        assertEquals(
            "current",
            presentation.displayedPackageInfo(
                installedPackageInfo = "current",
                savedPackageInfo = "saved"
            )
        )
    }

    @Test
    fun `unknown package keeps displaying retained package information`() {
        val presentation = trackedInstallPresentation(
            InstallType.DEFAULT,
            InstalledPatchState.Unknown
        )

        assertEquals(
            "saved",
            presentation.displayedPackageInfo(
                installedPackageInfo = "current",
                savedPackageInfo = "saved"
            )
        )
    }

    @Test
    fun `unknown tracked package never falls back to untracked current information`() {
        val presentation = trackedInstallPresentation(
            InstallType.DEFAULT,
            InstalledPatchState.Unknown
        )

        assertEquals(
            null,
            displayedHomePackageInfo(
                trackedPresentation = presentation,
                installedPackageInfo = "current",
                savedPackageInfo = null,
                untrackedPackageInfo = "resolved-current"
            )
        )
    }

    @Test
    fun `untracked app uses normally resolved package information`() {
        assertEquals(
            "resolved-current",
            displayedHomePackageInfo(
                trackedPresentation = null,
                installedPackageInfo = "current",
                savedPackageInfo = null,
                untrackedPackageInfo = "resolved-current"
            )
        )
    }

    @Test
    fun `unverifiable install is presented as unknown`() {
        assertEquals(
            TrackedInstallPresentation(isUnknown = true),
            trackedInstallPresentation(InstallType.DEFAULT, InstalledPatchState.Unknown)
        )
    }

    @Test
    fun `saved record without an install is not presented as deleted`() {
        assertEquals(
            TrackedInstallPresentation(),
            trackedInstallPresentation(InstallType.SAVED, null)
        )
    }
}
