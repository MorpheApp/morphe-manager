/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppIconCatalogTest {
    /**
     * Every alias the catalog has offered. The launcher remembers a picked icon by its alias, so one
     * that goes missing leaves its users without an icon on update. A new icon is added here, an
     * existing one never leaves.
     */
    private val offeredAliases = listOf(
        "Default", "Default_Black",
        "Light_2", "Light_2_Black",
        "Light_3", "Light_3_Black",
        "Light_4", "Light_4_Black",
        "Light_5", "Light_5_Black",
        "Light_6", "Light_6_Black",
        "Light_7", "Light_7_Black",
        "Dark_1", "Dark_1_Black",
        "Dark_2", "Dark_2_White",
        "Dark_3", "Dark_3_Black",
        "Dark_4",
        "Dark_5", "Dark_5_Black",
        "Accent_1", "Accent_1_Black",
        "Accent_2", "Accent_2_Color",
        "Accent_3", "Accent_3_Color",
        "Accent_4", "Accent_4_Black"
    ).map { "app.morphe.manager.MainActivity_$it" }

    private val aliases = AppIcon.entries.map { it.aliasName }

    @Test
    fun `every alias once offered is still there`() {
        val missing = offeredAliases - aliases.toSet()
        assertTrue(missing.isEmpty(), "Aliases gone from the catalog: $missing")
    }

    @Test
    fun `every alias in the catalog is listed as offered`() {
        val unlisted = aliases - offeredAliases.toSet()
        assertTrue(unlisted.isEmpty(), "Add these to offeredAliases: $unlisted")
    }

    @Test
    fun `no two icons share an alias`() {
        assertEquals(aliases.size, aliases.toSet().size)
    }

    @Test
    fun `no two icons share a background and mark`() {
        val pairs = AppIcon.entries.map { it.background to it.mark }
        assertEquals(pairs.size, pairs.toSet().size)
    }

    @Test
    fun `every background offers its base mark`() {
        IconBackground.entries.forEach { background ->
            assertNotNull(AppIcon.of(background, background.baseMark), "$background has no base icon")
        }
    }

    @Test
    fun `the default icon comes first, as the one the manifest enables`() {
        assertEquals(AppIcon.DEFAULT, AppIcon.entries.first())
        assertEquals("app.morphe.manager.MainActivity_Default", AppIcon.DEFAULT.aliasName)
    }
}
