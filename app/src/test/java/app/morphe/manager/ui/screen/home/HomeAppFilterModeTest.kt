/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import app.morphe.manager.ui.model.patchedAppItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeAppFilterModeTest {
    @Test
    fun `confirmed replacement is historically patched and currently installed`() {
        val item = patchedAppItem().copy(isInstallStateNotPatched = true)

        assertTrue(HomeAppFilterMode.PATCHED.matches(item))
        assertTrue(HomeAppFilterMode.INSTALLED.matches(item))
        assertFalse(HomeAppFilterMode.NOT_PATCHED.matches(item))
        assertFalse(HomeAppFilterMode.UNINSTALLED.matches(item))
    }

    @Test
    fun `pending verification stays physically installed without showing an update verdict`() {
        val item = patchedAppItem().copy(isInstallStatePending = true, hasUpdate = true)

        assertTrue(HomeAppFilterMode.INSTALLED.matches(item))
        assertFalse(HomeAppFilterMode.UNINSTALLED.matches(item))
        assertFalse(item.status.offersRebuild)
    }

    @Test
    fun `a clone is told apart from the app it was built from`() {
        val app = patchedAppItem(id = "app.example")
        val clone = patchedAppItem(id = "app.example.morphe", isClone = true)

        assertTrue(HomeAppFilterMode.CLONES.matches(clone))
        assertFalse(HomeAppFilterMode.CLONES.matches(app))
        // The two share a package name, so the filter has to read the card rather than the app
        assertTrue(HomeAppFilterMode.PATCHED.matches(clone))
        assertTrue(HomeAppFilterMode.PATCHED.matches(app))
    }
}
