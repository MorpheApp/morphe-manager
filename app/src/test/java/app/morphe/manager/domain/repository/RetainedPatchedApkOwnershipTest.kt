/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import app.morphe.manager.data.room.apps.installed.InstallType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedPatchedApkOwnershipTest {
    private fun owners(
        currentPackageName: String = "app.morphe.youtube",
        originalPackageName: String = "com.google.android.youtube",
        originalPackageIsTracked: Boolean = false
    ) = retainedPatchedApkOwners(
        currentPackageName = currentPackageName,
        originalPackageName = originalPackageName,
        originalPackageIsTracked = originalPackageIsTracked
    )

    @Test
    fun `renamed record owns its legacy copy while no other record claims that name`() {
        assertEquals(listOf("app.morphe.youtube", "com.google.android.youtube"), owners())
    }

    @Test
    fun `renamed record leaves the original name to the record that occupies it`() {
        assertEquals(
            listOf("app.morphe.youtube"),
            owners(originalPackageIsTracked = true)
        )
    }

    @Test
    fun `unrenamed record owns a single copy`() {
        assertEquals(
            listOf("com.google.android.youtube"),
            owners(
                currentPackageName = "com.google.android.youtube",
                originalPackageIsTracked = true
            )
        )
    }

    @Test
    fun `saved-only record does not outlive its archive`() {
        assertFalse(outlivesRetainedPatchedApk(InstallType.SAVED))
    }

    @Test
    fun `installed record outlives its archive`() {
        assertTrue(outlivesRetainedPatchedApk(InstallType.DEFAULT))
        assertTrue(outlivesRetainedPatchedApk(InstallType.MOUNT))
    }
}
