/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.links

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLinksStatusTest {

    private val youTubeDomains = listOf("youtu.be", "youtube.com", "m.youtube.com", "www.youtube.com")

    @Test
    fun `no supported domains reports no links and no attention needed`() {
        val status = AppLinksStatus.None

        assertFalse(status.hasSupportedLinks)
        assertFalse(status.isFullyConfigured)
        assertFalse(status.needsAttention)
        assertFalse(status.opensInBrowser)
    }

    @Test
    fun `no selected domain opens every link in the browser`() {
        val status = AppLinksStatus(domains = youTubeDomains, unhandledDomains = youTubeDomains)

        assertTrue(status.hasSupportedLinks)
        assertFalse(status.isFullyConfigured)
        assertTrue(status.needsAttention)
        assertTrue(status.opensInBrowser)
    }

    @Test
    fun `partially selected domains need attention without opening every link in the browser`() {
        val status = AppLinksStatus(
            domains = youTubeDomains + "myaccount.google.com",
            unhandledDomains = listOf("myaccount.google.com")
        )

        assertFalse(status.isFullyConfigured)
        assertTrue(status.needsAttention)
        assertFalse(status.opensInBrowser)
    }

    @Test
    fun `all domains selected reports fully configured`() {
        val status = AppLinksStatus(domains = youTubeDomains, unhandledDomains = emptyList())

        assertTrue(status.isFullyConfigured)
        assertFalse(status.needsAttention)
        assertFalse(status.opensInBrowser)
    }
}
