/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Entry content here takes the shapes conventional-changelog writes for Morphe repositories:
 * `### Features` and `### Bug Fixes` headings over `*` bullets, optionally scoped to a patch.
 */
class ChangelogSectionsTest {
    @Test
    fun `headings split the changes into kinds, features first`() {
        val sections = ChangelogParser.sections(
            """
            ### Bug Fixes

            * Keep the mini-game in place during batch patching

            ### Features

            * Add haptic feedback to mini-game moments
            * Animate the logo on the splash screen
            """.trimIndent()
        )

        assertEquals(
            listOf(ChangelogSection.Kind.FEATURES, ChangelogSection.Kind.FIXES),
            sections.map { it.kind }
        )
        assertEquals(1, sections.countOf(ChangelogSection.Kind.FIXES))
        assertEquals(2, sections.countOf(ChangelogSection.Kind.FEATURES))
    }

    @Test
    fun `emoji headings map to the same kinds and lose their decoration`() {
        val sections = ChangelogParser.sections(
            """
            ### 🚀 Updated App Support

            * **YouTube:** Add experimental support for `21.39.522`

            ### 🐛 Bug Fixes

            * Fixed Type 2 aspect ratio

            ### ✨ New Features

            * Add channel search result sorting

            ### 🔧 Improvements

            * Speed up the patch list
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ChangelogSection.Kind.FEATURES,
                ChangelogSection.Kind.FIXES,
                ChangelogSection.Kind.IMPROVEMENTS,
                ChangelogSection.Kind.APP_SUPPORT
            ),
            sections.map { it.kind }
        )
        assertEquals("Updated App Support", sections.last().title)
    }

    @Test
    fun `a bold scope is taken apart from the change`() {
        val item = ChangelogParser.sections("* **YouTube - Hide ads:** Hide the sponsored shelf")
            .single().items.single()

        assertEquals("YouTube - Hide ads", item.scope)
        assertEquals("Hide the sponsored shelf", item.text)
    }

    @Test
    fun `bullets ahead of any heading form an untitled section`() {
        val section = ChangelogParser.sections("- First change\n- Second change").single()

        assertEquals(ChangelogSection.Kind.OTHER, section.kind)
        assertNull(section.title)
        assertEquals(2, section.items.size)
    }

    @Test
    fun `an unknown heading keeps its own title`() {
        val section = ChangelogParser.sections("### General changes\n\n* Bump the patcher").single()

        assertEquals(ChangelogSection.Kind.OTHER, section.kind)
        assertEquals("General changes", section.title)
    }

    @Test
    fun `commit links left in release notes are dropped`() {
        val item = ChangelogParser.sections(
            "* Close the split modules ([1a0dbae](https://github.com/MorpheApp/morphe-manager/commit/1a0dbae))"
        ).single().items.single()

        assertEquals("Close the split modules", item.text)
    }

    @Test
    fun `nested and numbered items flatten into the list`() {
        val items = ChangelogParser.sections("* Parent\n  * Nested change\n1. Numbered change").single().items

        assertEquals(listOf("Parent", "Nested change", "Numbered change"), items.map { it.text })
        assertTrue(items.all { it.isBullet })
    }

    @Test
    fun `prose stays as a note that is not counted as a change`() {
        val section = ChangelogParser.sections(
            """
            ### Features

            Patches and extensions were added or expanded for:

            * Add the monochrome icon
            """.trimIndent()
        ).single()

        assertEquals(
            ChangelogItem(scope = null, text = "Patches and extensions were added or expanded for:", isBullet = false),
            section.items.first()
        )
        assertEquals(1, section.changeCount)
    }

    @Test
    fun `leftovers with nothing to read are dropped`() {
        val items = ChangelogParser.sections(
            """
            * Keep the change
            =======
            ---
            Signed-off-by: Someone <someone@example.com>
            ![screenshot](https://example.com/shot.png)
            """.trimIndent()
        ).single().items

        assertEquals(listOf("Keep the change"), items.map { it.text })
    }

    @Test
    fun `an empty entry has no sections`() {
        assertTrue(ChangelogParser.sections("").isEmpty())
    }

    @Test
    fun `translating texts keeps scopes and kinds`() {
        val sections = ChangelogParser.sections("### Features\n\n* **Reddit:** Add an icon")
        val translated = sections.mapItemTexts { it.uppercase() }

        assertEquals(ChangelogSection.Kind.FEATURES, translated.single().kind)
        assertEquals(ChangelogItem("Reddit", "ADD AN ICON"), translated.single().items.single())
    }
}
