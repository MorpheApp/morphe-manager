/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.*

class BitmapUtilsTest {
    @Test
    fun `image within the limit is not sampled`() {
        assertEquals(1, calculateSampleSize(2048, 1536, 2048))
    }

    @Test
    fun `sample size halves the longer side until it fits`() {
        assertEquals(8, calculateSampleSize(12_000, 9_000, 2048))
        assertEquals(2, calculateSampleSize(4000, 3000, 2048))
    }

    @Test
    fun `decoded size of a 12000 by 9000 image stays small`() {
        val sample = calculateSampleSize(12_000, 9_000, 2048)
        val bytes = (12_000 / sample).toLong() * (9_000 / sample) * 4
        assertTrue(bytes < 8L * 1024 * 1024, "decoded $bytes bytes")
    }

    @Test
    fun `unreadable bounds are left alone`() {
        assertEquals(1, calculateSampleSize(-1, -1, 2048))
    }
}
