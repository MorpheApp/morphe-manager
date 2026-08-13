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

    private fun outlives(
        installType: InstallType,
        packageIsInstalled: Boolean,
        installedSignatureHashes: Set<String> = setOf("patched"),
        retainedSignatureHashes: Set<String> = setOf("patched")
    ) = outlivesRetainedPatchedApk(
        installType,
        installedSavedApkIdentity(
            packageIsInstalled,
            installedSignatureHashes,
            retainedSignatureHashes
        )
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
    fun `saved record outlives its archive when the installed package matches`() {
        assertTrue(outlives(InstallType.SAVED, packageIsInstalled = true))
    }

    @Test
    fun `saved record survives when installed identity cannot be read`() {
        assertTrue(
            outlives(
                InstallType.SAVED,
                packageIsInstalled = true,
                installedSignatureHashes = emptySet()
            )
        )
        assertTrue(
            outlives(
                InstallType.SAVED,
                packageIsInstalled = true,
                retainedSignatureHashes = emptySet()
            )
        )
    }

    @Test
    fun `saved record does not outlive archive when a different package occupies its name`() {
        assertFalse(
            outlives(
                InstallType.SAVED,
                packageIsInstalled = true,
                installedSignatureHashes = setOf("stock"),
                retainedSignatureHashes = setOf("patched")
            )
        )
    }

    @Test
    fun `saved-only record does not outlive its archive`() {
        assertFalse(outlives(InstallType.SAVED, packageIsInstalled = false))
    }

    @Test
    fun `installed record types outlive their archives even while absent`() {
        assertTrue(
            outlives(InstallType.DEFAULT, packageIsInstalled = false)
        )
        assertTrue(
            outlives(InstallType.MOUNT, packageIsInstalled = false)
        )
    }
}
