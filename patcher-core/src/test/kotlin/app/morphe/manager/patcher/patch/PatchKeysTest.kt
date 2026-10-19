/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The keys selections and options name patches by. The patcher resolves a selection with them,
 * so they have to come out the same every time for the same bundle.
 */
class PatchKeysTest {
    private val app = "com.example.app"

    private fun patch(name: String, vararg packages: String) = bytecodePatch(name = name) {
        packages.forEach { compatibleWith(Compatibility(packageName = it, name = it)) }
    }

    private fun universal(name: String) = bytecodePatch(name = name) {
        compatibleWith(Compatibility(description = "Any app"))
    }

    @Test
    fun `a patch applies to the packages it names and to none else`() {
        assertTrue(patch("Ads", app).compatibleWith(app))
        assertFalse(patch("Ads", "com.other").compatibleWith(app))
    }

    @Test
    fun `a patch that names no package, or a target of any package, applies everywhere`() {
        assertTrue(bytecodePatch(name = "Theme") {}.compatibleWith(app))
        assertTrue(universal("Theme").compatibleWith(app))
    }

    @Test
    fun `a patch with an empty compatibility list applies nowhere`() {
        assertFalse(bytecodePatch(name = "Nothing") { compatibleWith() }.compatibleWith(app))
    }

    @Test
    fun `patches are keyed by name, leaving out those for other packages`() {
        val ads = patch("Ads", app)
        val keyed = listOf(ads, patch("Other", "com.other"), universal("Theme")).keyedFor(app)

        assertEquals(setOf("Ads", "Theme"), keyed.keys)
        assertSame(ads, keyed["Ads"])
    }

    @Test
    fun `the most specific of same-named patches keeps the plain name`() {
        val generic = universal("Ads")
        val specific = patch("Ads", app)

        val keyed = listOf(generic, specific).keyedFor(app)

        assertSame(specific, keyed["Ads"])
        assertSame(generic, keyed["Ads (2)"])
    }

    @Test
    fun `a suffix already taken by a patch of that name is skipped`() {
        val keys = uniqueKeys(
            listOf("Ads", "Ads", "Ads (2)"),
            name = { it },
            compatibilityKey = { "" }
        )

        assertEquals(listOf("Ads", "Ads (3)", "Ads (2)"), keys)
    }

    @Test
    fun `keys do not depend on the order the bundle lists the patches in`() {
        val patches: List<Patch<*>> = listOf(universal("Ads"), patch("Ads", app), patch("Ads", app, "com.other"))

        val forward = patches.keyedFor(app)
        val backward = patches.reversed().keyedFor(app)

        assertEquals(forward, backward)
    }
}
