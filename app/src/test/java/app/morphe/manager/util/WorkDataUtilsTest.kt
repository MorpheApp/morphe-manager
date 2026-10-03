/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.*

/** WorkManager rejects an output past 10240 bytes, which would replace the failure with a crash. */
class WorkDataUtilsTest {
    @Test
    fun `short text is returned unchanged`() {
        val text = "java.lang.IllegalStateException: boom\n\tat a.b.C.d(C.kt:1)"
        assertSame(text, text.truncateForWorkData())
    }

    @Test
    fun `long text fits the budget and keeps both ends`() {
        val text = "FIRST" + "x".repeat(50_000) + "LAST"
        val result = text.truncateForWorkData()

        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 8192)
        assertTrue(result.startsWith("FIRST"))
        assertTrue(result.endsWith("LAST"))
        assertTrue("truncated" in result)
    }

    @Test
    fun `multi byte text is cut on character boundaries within the budget`() {
        val text = "é".repeat(30_000) + "😀".repeat(10_000)
        val result = text.truncateForWorkData()

        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 8192)
        assertFalse('�' in result)
    }

    @Test
    fun `text at the limit is not touched`() {
        val text = "y".repeat(8192)
        assertSame(text, text.truncateForWorkData())
    }
}
