/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The accepted archive becomes the certificate that decides whether an installed package is the
 * tracked patched build. Anything reaching this predicate has to be the artifact the record was
 * written for, not merely a file sitting at the expected path.
 */
class SavedApkRecordTest {
    private fun matches(
        archivePackageName: String? = "app.morphe.target",
        archiveVersionName: String? = "1.2.3",
        isSigned: Boolean = true,
        trackedPackageNames: Collection<String> = listOf("app.morphe.target"),
        trackedVersion: String = "1.2.3"
    ) = matchesSavedApkRecord(
        archivePackageName = archivePackageName,
        archiveVersionName = archiveVersionName,
        trackedPackageNames = trackedPackageNames,
        trackedVersion = trackedVersion,
        isSigned = { isSigned }
    )

    @Test
    fun `the recorded package and version are accepted`() {
        assertTrue(matches())
    }

    @Test
    fun `a renamed app accepts only its current package`() {
        assertTrue(
            matches(
                archivePackageName = "app.morphe.target.renamed",
                trackedPackageNames = listOf("app.morphe.target.renamed")
            )
        )
        assertFalse(
            matches(
                archivePackageName = "app.morphe.target",
                trackedPackageNames = listOf("app.morphe.target.renamed")
            )
        )
    }

    @Test
    fun `an archive for another version is rejected`() {
        assertFalse(matches(archiveVersionName = "1.2.4"))
        assertFalse(matches(archiveVersionName = "1.2"))
    }

    @Test
    fun `a version-rewriting patch still matches its own record`() {
        // The patcher persists the version it produced, so a spoofed one is what the record holds
        assertTrue(matches(archiveVersionName = "18.0.0", trackedVersion = "18.0.0"))
    }

    @Test
    fun `an unsigned or unreadable archive is rejected`() {
        assertFalse(matches(isSigned = false))
        assertFalse(matches(archivePackageName = null))
        assertFalse(matches(archiveVersionName = null))
    }

    @Test
    fun `an archive that is not the recorded artifact is rejected without reading its certificate`() {
        var certificateRead = false
        val matched = matchesSavedApkRecord(
            archivePackageName = "app.morphe.other",
            archiveVersionName = "1.2.3",
            trackedPackageNames = listOf("app.morphe.target"),
            trackedVersion = "1.2.3",
            isSigned = {
                certificateRead = true
                true
            }
        )

        assertFalse(matched)
        assertFalse(certificateRead)
    }
}
