/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.bundles

import app.morphe.patcher.patch.AppTarget
import kotlin.test.*

/**
 * The home card and the app dialog both describe an install by where it stands against the newest
 * version the sources cover, so the two ends and the silent middle have to be told apart here.
 */
class AppVersionStatusTest {

    @Test
    fun `an install older than the newest supported version is behind it`() {
        val status = assertNotNull(versionStatus("9.12.51", target("9.13.50")))

        assertTrue(status.isBehind)
        assertEquals("9.12.51", status.installedVersion)
        assertEquals("9.13.50", status.supportedVersion)
    }

    @Test
    fun `an install past everything the sources cover is ahead of them`() {
        val status = assertNotNull(versionStatus("9.14.10", target("9.13.50")))

        assertFalse(status.isBehind)
    }

    @Test
    fun `an install at the newest supported version has nothing to report`() {
        assertNull(versionStatus("9.13.50", target("9.13.50")))
    }

    @Test
    fun `versions carrying a build stamp are still placed by their numbers`() {
        val status = assertNotNull(
            versionStatus(
                "15.2.05.789012345-release-arm64-v8a",
                target("15.3.01.812345678-release-arm64-v8a")
            )
        )

        assertTrue(status.isBehind)
    }

    @Test
    fun `a version that was turned down stops being offered`() {
        assertNull(versionStatus("9.12.51", target("9.13.50"), ignoredVersion = "9.13.50"))
    }

    @Test
    fun `turning a version down does not carry over to the next one`() {
        val status = assertNotNull(
            versionStatus("9.12.51", target("9.14.10"), ignoredVersion = "9.13.50")
        )

        assertTrue(status.isBehind)
    }

    @Test
    fun `an install past the sources is described whatever was turned down`() {
        // Nothing was offered to turn down: the status says where the install ended up, and the
        // unpatched banner is the one that reads it
        val status = assertNotNull(
            versionStatus("9.14.10", target("9.13.50"), ignoredVersion = "9.13.50")
        )

        assertFalse(status.isBehind)
    }

    @Test
    fun `nothing is reported when either side names no version`() {
        // Which is what universal patches leave behind, and what a record without one reads as
        assertNull(versionStatus("9.12.51", target(null)))
        assertNull(versionStatus("9.12.51", null))
        assertNull(versionStatus(null, target("9.13.50")))
        assertNull(versionStatus("", target("9.13.50")))
    }

    @Test
    fun `only a version past every target is past the patches`() {
        val targets = listOf(bundled("9.13.50"), bundled("9.12.51"))

        assertTrue(targets.isPastEveryTarget("9.14.10"))
        assertFalse(targets.isPastEveryTarget("9.13.50"))
        assertFalse(targets.isPastEveryTarget("9.12.00"))
    }

    @Test
    fun `nothing is past patches that take any version`() {
        assertFalse(emptyList<BundledAppTarget>().isPastEveryTarget("9.14.10"))
        assertFalse(listOf(bundled("9.13.50"), bundled(null)).isPastEveryTarget("9.14.10"))
    }

    private fun target(version: String?) = AppTarget(version = version)

    private fun bundled(version: String?) =
        BundledAppTarget(target = target(version), bundleUid = 0, bundleName = "Source")
}
