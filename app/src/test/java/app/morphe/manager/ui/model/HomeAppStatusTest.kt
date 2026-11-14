/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.domain.bundles.AppVersionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeAppStatusTest {
    @Test
    fun `an app without a record is not patched yet`() {
        assertEquals(HomeAppStatus.NotPatchedYet, homeAppItem().status)
    }

    @Test
    fun `a settled build reads as patched at its version`() {
        assertEquals(HomeAppStatus.Patched("1.0"), patchedAppItem().copy(version = "1.0").status)
    }

    @Test
    fun `the state of the install wins over an update waiting for it`() {
        val update = patchedAppItem().copy(hasUpdate = true)

        assertEquals(HomeAppStatus.Pending, update.copy(isInstallStatePending = true, isDeleted = true).status)
        assertEquals(
            HomeAppStatus.UnpatchedInstall,
            update.copy(isInstallStateNotPatched = true, isDeleted = true).status
        )
        assertEquals(HomeAppStatus.Uninstalled, update.copy(isDeleted = true, isInstallStateUnknown = true).status)
        assertEquals(HomeAppStatus.Unverified, update.copy(isInstallStateUnknown = true).status)
        assertEquals(HomeAppStatus.NewerPatches, update.status)
    }

    @Test
    fun `a newer supported version leads newer patches`() {
        val item = patchedAppItem().copy(hasUpdate = true, versionStatus = behind())

        assertEquals(HomeAppStatus.NewerVersion("9.13.50"), item.status)
    }

    @Test
    fun `a newer supported version alone offers a rebuild`() {
        val item = patchedAppItem().copy(versionStatus = behind())

        assertEquals(HomeAppStatus.NewerVersion("9.13.50"), item.status)
        assertTrue(item.status.offersRebuild)
    }

    @Test
    fun `an install past what the sources cover is left to the state it is in`() {
        val item = patchedAppItem().copy(versionStatus = AppVersionStatus("9.14.10", "9.13.50", isBehind = false))

        assertEquals(HomeAppStatus.Patched(""), item.status)
        assertFalse(item.status.offersRebuild)
    }

    private fun behind() = AppVersionStatus("9.12.51", "9.13.50", isBehind = true)
}
