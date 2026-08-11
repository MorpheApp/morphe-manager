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
            TrackedInstallPresentation(isPatched = true),
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
    fun `present non-patched package is distinguished from an absent package`() {
        assertEquals(
            TrackedInstallPresentation(isDeleted = true, isNotPatched = true),
            trackedInstallPresentation(InstallType.DEFAULT, InstalledPatchState.NotPatched)
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
