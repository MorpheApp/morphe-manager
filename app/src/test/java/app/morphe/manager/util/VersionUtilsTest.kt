/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Version strings reaching these functions come from GitHub tags, bundle manifests and the
 * database, so the cases below use the shapes this project actually ships: `1.25.0` releases,
 * `1.25.1-dev.1` pre-releases and `v`-prefixed tags.
 */
class VersionUtilsTest {
    private fun assertOlder(older: String?, newer: String?) {
        assertTrue(compareVersions(older, newer) < 0, "expected $older < $newer")
        assertTrue(compareVersions(newer, older) > 0, "expected $newer > $older")
    }

    @Test
    fun `missing versions sort below present ones`() {
        assertEquals(0, compareVersions(null, null))
        assertOlder(null, "1.25.0")
    }

    @Test
    fun `identical versions compare equal`() {
        assertEquals(0, compareVersions("1.25.0", "1.25.0"))
    }

    @Test
    fun `tag prefix and whitespace do not affect the comparison`() {
        assertEquals(0, compareVersions("v1.25.0", "1.25.0"))
        assertEquals(0, compareVersions("1.25.0 ", "v1.25.0"))
        assertEquals("1.25.0", "v1.25.0".normalizeVersion())
        assertEquals("1.25.0", " 1.25.0 ".normalizeVersion())
    }

    @Test
    fun `segments are compared numerically rather than as text`() {
        assertOlder("1.9.0", "1.10.0")
        assertOlder("1.25.9", "1.25.10")
    }

    @Test
    fun `a missing trailing segment counts as zero`() {
        assertEquals(0, compareVersions("1.25", "1.25.0"))
        assertOlder("1.25", "1.25.1")
    }

    @Test
    fun `a release outranks its own pre-releases`() {
        assertOlder("1.25.1-dev.1", "1.25.1")
    }

    @Test
    fun `pre-release counters are compared numerically`() {
        assertOlder("1.25.1-dev.2", "1.25.1-dev.10")
    }

    @Test
    fun `a newer base outranks an older release even as a pre-release`() {
        assertOlder("1.25.0", "1.26.0-dev.1")
    }

    @Test
    fun `isNewerVersion only reports a strict increase`() {
        assertTrue(isNewerVersion("1.25.0", "1.25.1"))
        assertTrue(isNewerVersion("1.25.1-dev.1", "1.25.1"))
        assertFalse(isNewerVersion("1.25.1", "1.25.0"))
        assertFalse(isNewerVersion("1.25.0", "1.25.0"))
        assertFalse(isNewerVersion("1.25.0", "v1.25.0"))
    }

    @Test
    fun `a bundle built for a newer patcher is reported as outdated`() {
        assertTrue(isPatcherOutdated(required = "2.0.0", current = "1.8.0"))
        assertFalse(isPatcherOutdated(required = "1.8.0", current = "1.8.0"))
        assertFalse(isPatcherOutdated(required = "1.7.0", current = "1.8.0"))
    }

    @Test
    fun `an unparseable requirement never blocks patching`() {
        assertFalse(isPatcherOutdated(required = "not-a-version", current = "1.8.0"))
        assertFalse(isPatcherOutdated(required = "", current = "1.8.0"))
        assertFalse(isPatcherOutdated(required = "2.0.0", current = "unknown"))
    }

    @Test
    fun `release links follow the host's tag layout`() {
        assertEquals(
            "https://github.com/MorpheApp/morphe-manager/releases/tag/v1.25.0",
            releasePageUrl("https://github.com/MorpheApp/morphe-manager", "1.25.0")
        )
        assertEquals(
            "https://gitlab.com/group/project/-/releases/v1.25.0",
            releasePageUrl("https://gitlab.com/group/project", "v1.25.0")
        )
    }
}
