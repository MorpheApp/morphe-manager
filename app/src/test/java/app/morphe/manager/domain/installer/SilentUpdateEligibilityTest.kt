/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MANAGER = "app.morphe.manager"
private const val PLAY_STORE = "com.android.vending"

/** Android 12, the first release that waives the install dialog. */
private const val S = 31

class SilentUpdateEligibilityTest {
    private fun allows(
        sdkInt: Int = 34,
        installedTargetSdk: Int? = 35,
        installerPackageName: String? = MANAGER,
        updateOwnerPackageName: String? = MANAGER
    ) = allowsSilentUpdate(
        sdkInt = sdkInt,
        installedTargetSdk = installedTargetSdk,
        installerPackageName = installerPackageName,
        updateOwnerPackageName = updateOwnerPackageName,
        selfPackageName = MANAGER
    )

    @Test
    fun `owning the update of an installed app allows it`() {
        assertTrue(allows())
    }

    @Test
    fun `android 11 never allows it`() {
        assertFalse(allows(sdkInt = S - 1))
        assertTrue(allows(sdkInt = S, installedTargetSdk = S))
    }

    @Test
    fun `an app that is not installed has nothing to update`() {
        assertFalse(allows(installedTargetSdk = null))
    }

    @Test
    fun `an app built against android 11 is left to the dialog`() {
        assertFalse(allows(installedTargetSdk = S - 1))
    }

    @Test
    fun `an update owner outranks the installer of record`() {
        assertFalse(allows(installerPackageName = MANAGER, updateOwnerPackageName = PLAY_STORE))
        assertTrue(allows(installerPackageName = PLAY_STORE, updateOwnerPackageName = MANAGER))
    }

    @Test
    fun `the installer of record decides when nobody owns the update`() {
        assertTrue(allows(installerPackageName = MANAGER, updateOwnerPackageName = null))
        assertFalse(allows(installerPackageName = PLAY_STORE, updateOwnerPackageName = null))
    }

    @Test
    fun `an app nothing claims is left to the dialog`() {
        assertFalse(allows(installerPackageName = null, updateOwnerPackageName = null))
    }
}
