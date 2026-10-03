/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.*

class AvatarUtilsTest {
    private var now = 0L
    private val failed = FailedUrlCache(retryAfterMillis = 1_000L) { now }

    @Test
    fun `an unknown address is not a recent failure`() {
        assertFalse(failed.isRecent("https://example.com/a.png"))
    }

    @Test
    fun `a failed address is skipped until the retry delay passes`() {
        failed.add("https://example.com/a.png")

        now = 999L
        assertTrue(failed.isRecent("https://example.com/a.png"))
        assertFalse(failed.isRecent("https://example.com/b.png"))

        now = 1_000L
        assertFalse(failed.isRecent("https://example.com/a.png"))
    }

    @Test
    fun `a failure that expired can fail again`() {
        failed.add("https://example.com/a.png")
        now = 5_000L
        assertFalse(failed.isRecent("https://example.com/a.png"))

        failed.add("https://example.com/a.png")
        assertTrue(failed.isRecent("https://example.com/a.png"))
    }
}
