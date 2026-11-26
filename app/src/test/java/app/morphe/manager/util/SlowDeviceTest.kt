/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlowDeviceTest {
    private val arm64 = arrayOf("arm64-v8a")

    @Test
    fun `a device without 64-bit code is slow whatever its cores`() {
        assertTrue(isSlowPatchingDevice(emptyArray(), 4))
        assertTrue(isSlowPatchingDevice(emptyArray(), 8))
    }

    @Test
    fun `a device with fewer than 8 cores is slow`() {
        assertTrue(isSlowPatchingDevice(arm64, 4))
        assertTrue(isSlowPatchingDevice(arm64, 7))
    }

    @Test
    fun `a 64-bit device with 8 cores or more is fast`() {
        assertFalse(isSlowPatchingDevice(arm64, 8))
        assertFalse(isSlowPatchingDevice(arm64, 10))
    }
}
