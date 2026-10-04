/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLinksStatusTest {

    @Test
    fun `no supported domains reports no links and no attention needed`() {
        val status = AppLinksStatus(
            packageName = "app.test",
            totalDomains = 0,
            selectedDomains = 0,
            domains = emptyList(),
            unhandledDomains = emptyList(),
            isLinkHandlingAllowed = true
        )

        assertFalse(status.hasSupportedLinks)
        assertFalse(status.isFullyConfigured)
        assertFalse(status.needsAttention)
    }

    @Test
    fun `unhandled domains report attention needed`() {
        val status = AppLinksStatus(
            packageName = "com.google.android.youtube",
            totalDomains = 4,
            selectedDomains = 0,
            domains = listOf("youtu.be", "youtube.com", "m.youtube.com", "www.youtube.com"),
            unhandledDomains = listOf("youtu.be", "youtube.com", "m.youtube.com", "www.youtube.com"),
            isLinkHandlingAllowed = true
        )

        assertTrue(status.hasSupportedLinks)
        assertFalse(status.isFullyConfigured)
        assertTrue(status.needsAttention)
        assertEquals(4, status.unhandledDomains.size)
    }

    @Test
    fun `partially selected domains report attention needed`() {
        val status = AppLinksStatus(
            packageName = "com.google.android.youtube",
            totalDomains = 4,
            selectedDomains = 2,
            domains = listOf("youtu.be", "youtube.com", "m.youtube.com", "www.youtube.com"),
            unhandledDomains = listOf("m.youtube.com", "www.youtube.com"),
            isLinkHandlingAllowed = true
        )

        assertTrue(status.hasSupportedLinks)
        assertFalse(status.isFullyConfigured)
        assertTrue(status.needsAttention)
    }

    @Test
    fun `all domains selected with link handling enabled reports fully configured`() {
        val status = AppLinksStatus(
            packageName = "com.google.android.youtube",
            totalDomains = 4,
            selectedDomains = 4,
            domains = listOf("youtu.be", "youtube.com", "m.youtube.com", "www.youtube.com"),
            unhandledDomains = emptyList(),
            isLinkHandlingAllowed = true
        )

        assertTrue(status.hasSupportedLinks)
        assertTrue(status.isFullyConfigured)
        assertFalse(status.needsAttention)
    }

    @Test
    fun `all domains selected but link handling disabled reports attention needed`() {
        val status = AppLinksStatus(
            packageName = "com.google.android.youtube",
            totalDomains = 4,
            selectedDomains = 4,
            domains = listOf("youtu.be", "youtube.com", "m.youtube.com", "www.youtube.com"),
            unhandledDomains = emptyList(),
            isLinkHandlingAllowed = false
        )

        assertTrue(status.hasSupportedLinks)
        assertFalse(status.isFullyConfigured)
        assertTrue(status.needsAttention)
    }
}
