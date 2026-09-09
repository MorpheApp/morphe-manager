/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import app.morphe.manager.patcher.patch.CompatiblePackage
import app.morphe.manager.patcher.patch.PatchInfo
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a patch list is cut into blocks. A bundle that declares no categories has to come out as the
 * plain list it was before categories existed, since that is what every already published bundle is.
 */
class PatchGroupingTest {
    private val options = PatchGroupingOptions(universalTitle = "Universal", groupByCategory = true)

    private fun patch(name: String, category: String? = null, universal: Boolean = false) = PatchInfo(
        name = name,
        description = null,
        include = false,
        compatiblePackages = if (universal) {
            null
        } else {
            persistentListOf(CompatiblePackage(packageName = "com.example", versions = null))
        },
        options = null,
        category = category
    )

    private fun group(patches: List<PatchInfo>, options: PatchGroupingOptions = this.options) =
        buildPatchGroups(patches, options, infoOf = { it }, isEnabled = { it.include })

    @Test
    fun `a bundle without categories keeps the plain list it had before`() {
        val groups = group(listOf(patch("A"), patch("B"), patch("U", universal = true)))

        assertEquals(2, groups.size)
        assertNull(groups[0].title, "the ungrouped block carries no header")
        assertEquals(listOf("A", "B"), groups[0].items.map { it.name })
        assertEquals("Universal", groups[1].title)
        assertFalse(groups[1].defaultExpanded, "the universal block stays folded")
    }

    @Test
    fun `categories become blocks of their own, ordered after the ungrouped ones`() {
        val groups = group(
            listOf(
                patch("Ungrouped"),
                patch("Zoom", category = "Video"),
                patch("Hide ads", category = "Ads"),
                patch("U", universal = true)
            )
        )

        assertEquals(listOf(null, "Ads", "Video", "Universal"), groups.map { it.title })
        assertTrue(groups[1].defaultExpanded, "categories start out open")
    }

    @Test
    fun `a list that is all one category is still a block, so it can be folded`() {
        val groups = group(listOf(patch("A", category = "Ads"), patch("B", category = "Ads")))

        assertEquals(listOf("Ads"), groups.map { it.title })
        assertEquals(2, groups[0].items.size)
    }

    @Test
    fun `grouping switched off leaves the same blocks as a bundle without categories`() {
        val patches = listOf(
            patch("A", category = "Ads"),
            patch("B", category = "Video"),
            patch("U", universal = true)
        )

        val groups = group(patches, options.copy(groupByCategory = false))

        assertEquals(listOf(null, "Universal"), groups.map { it.title })
        assertEquals(listOf("A", "B"), groups[0].items.map { it.name })
    }

    @Test
    fun `the selected count is per block, since a folded block hides what it holds`() {
        val enabled = patch("On", category = "Ads").copy(include = true)
        val groups = group(listOf(enabled, patch("Off", category = "Ads"), patch("Other", category = "Video")))

        assertEquals(1, groups.first { it.title == "Ads" }.selectedCount)
        assertEquals(0, groups.first { it.title == "Video" }.selectedCount)
    }

    @Test
    fun `a universal patch that declares a category joins it instead of the tail`() {
        val groups = group(
            listOf(
                patch("Specific", category = "Ads"),
                patch("Universal ad block", category = "Ads", universal = true),
                patch("Plain universal", universal = true)
            )
        )

        assertEquals(listOf("Ads", "Universal"), groups.map { it.title })
        assertEquals(listOf("Specific", "Universal ad block"), groups[0].items.map { it.name })
        assertEquals(listOf("Plain universal"), groups[1].items.map { it.name })
    }

    @Test
    fun `a bundle that categorizes every universal patch is left with no tail at all`() {
        val groups = group(
            listOf(
                patch("Block ads", category = "Ads", universal = true),
                patch("Spoof model", category = "Spoofs", universal = true)
            )
        )

        assertEquals(listOf("Ads", "Spoofs"), groups.map { it.title })
    }

    @Test
    fun `grouping switched off puts categorized universal patches back in the tail`() {
        val groups = group(
            listOf(patch("Block ads", category = "Ads", universal = true)),
            options.copy(groupByCategory = false)
        )

        assertEquals(listOf("Universal"), groups.map { it.title })
    }

    @Test
    fun `blocks are keyed by category, so a fold survives the list being filtered around it`() {
        val first = group(listOf(patch("A", category = "Ads"), patch("B", category = "Video")))
        val filtered = group(listOf(patch("B", category = "Video")))

        assertEquals(first.first { it.title == "Video" }.key, filtered.single().key)
    }
}
