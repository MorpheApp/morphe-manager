/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import app.morphe.manager.data.room.apps.installed.InstallType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackedRecordCleanupTest {
    private fun canRemove(
        installType: InstallType = InstallType.DEFAULT,
        patchState: InstalledPatchState? = InstalledPatchState.Patched,
        hasSavedApk: Boolean = false
    ) = canRemoveTrackedRecord(
        installType = installType,
        patchState = patchState,
        hasSavedApk = hasSavedApk
    )

    @Test
    fun `unverified install with nothing retained can still be cleaned up`() {
        assertTrue(canRemove(patchState = InstalledPatchState.Unknown))
    }

    @Test
    fun `unverified install with a retained APK can be cleaned up`() {
        assertTrue(canRemove(patchState = InstalledPatchState.Unknown, hasSavedApk = true))
    }

    @Test
    fun `replaced install can be cleaned up`() {
        assertTrue(canRemove(patchState = InstalledPatchState.NotPatched))
    }

    @Test
    fun `record without an installed package can be cleaned up`() {
        assertTrue(canRemove(patchState = null))
    }

    @Test
    fun `confirmed patched install with nothing retained has nothing to clean up`() {
        assertFalse(canRemove(patchState = InstalledPatchState.Patched))
    }

    @Test
    fun `confirmed patched install offers cleanup once an APK is retained`() {
        assertTrue(canRemove(patchState = InstalledPatchState.Patched, hasSavedApk = true))
    }

    @Test
    fun `saved records are cleanable regardless of the resolved state`() {
        assertTrue(
            canRemove(
                installType = InstallType.SAVED,
                patchState = InstalledPatchState.Patched
            )
        )
    }
}
