/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A bundle whose changelog is fetched must be one an installed app is behind on. */
class OutdatedBundleUidsTest {
    private val current = mapOf(1 to "1.2.0", 2 to "3.0.0", 3 to null)

    @Test
    fun `nothing is outdated when every app is on the current version`() {
        val stored = listOf(mapOf(1 to "1.2.0"), mapOf(2 to "3.0.0"))
        assertTrue(outdatedBundleUids(stored, current).isEmpty())
    }

    @Test
    fun `only bundles with an app behind them are returned`() {
        val stored = listOf(mapOf(1 to "1.1.0"), mapOf(2 to "3.0.0"))
        assertEquals(setOf(1), outdatedBundleUids(stored, current))
    }

    @Test
    fun `an app can be behind on several bundles`() {
        val stored = listOf(mapOf(1 to "1.0.0", 2 to "2.9.0"))
        assertEquals(setOf(1, 2), outdatedBundleUids(stored, current))
    }

    @Test
    fun `a bundle with no known current version is not outdated`() {
        val stored = listOf(mapOf(3 to "1.0.0", 4 to "1.0.0"))
        assertTrue(outdatedBundleUids(stored, current).isEmpty())
    }

    @Test
    fun `no installed apps means nothing is outdated`() {
        assertTrue(outdatedBundleUids(emptyList(), current).isEmpty())
    }
}
