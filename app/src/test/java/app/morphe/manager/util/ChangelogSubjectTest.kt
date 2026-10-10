/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which changelog bullets count as changes for one app. Third-party changelogs scope their
 * bullets loosely or not at all, so the cases below come from real ones.
 */
class ChangelogSubjectTest {
    private fun changesFor(
        bullet: String,
        vararg appNames: String,
        patchNames: Set<String> = emptySet(),
        packageName: String? = null,
        otherAppNames: Set<String> = emptySet()
    ) = ChangelogParser.hasChangesFor(
        entries = ChangelogParser.parse("# [1.1.0](https://example.com) (2026-10-01)\n\n$bullet\n"),
        installedVersion = "1.0.0",
        subject = ChangelogSubject(appNames.toSet(), patchNames, packageName, otherAppNames)
    )

    @Test
    fun `a scope matches the app name whatever its spelling`() {
        assertTrue(changesFor("* **google-photos:** fix crash", "Google Photos"))
        assertTrue(changesFor("* **unifiedremote:** unlock full version", "Unified Remote"))
    }

    @Test
    fun `a sub-scope matches its app`() {
        assertTrue(changesFor("* **YouTube - Hide ads:** hide new banner", "YouTube"))
    }

    @Test
    fun `a scope matches a distinctive package name segment`() {
        assertTrue(changesFor("* **brave:** unlock origin", "Brave Browser", packageName = "com.brave.browser"))
        assertFalse(changesFor("* **messenger:** hide ads", "Telegram", packageName = "org.telegram.messenger"))
    }

    @Test
    fun `a scope naming another app of the bundle is not taken from a package segment`() {
        val bullet = "* **Facebook:** hide reels"
        assertTrue(changesFor(bullet, "Messenger", packageName = "com.facebook.orca"))
        assertFalse(
            changesFor(bullet, "Messenger", packageName = "com.facebook.orca", otherAppNames = setOf("Facebook"))
        )
    }

    @Test
    fun `a longer name of another app is not a mention of this one`() {
        val others = setOf("Telegram Web")
        assertFalse(changesFor("* telegram web, closes #729", "Telegram", otherAppNames = others))
        assertTrue(changesFor("* fix Telegram Web and Telegram login", "Telegram", otherAppNames = others))
    }

    @Test
    fun `the text of a scoped bullet is not read`() {
        assertFalse(changesFor("* **Reddit:** open YouTube links externally", "YouTube"))
    }

    @Test
    fun `an unscoped bullet naming the app is a change for it`() {
        val bullet = "* bump pillo, ornament, lyfta ([96b80b9](https://github.com/kondratjev/morphe-patches/commit/96b80b9))"
        assertTrue(changesFor(bullet, "Lyfta"))
        assertFalse(changesFor(bullet, "Medisafe"))
    }

    @Test
    fun `an app name only counts as a whole word`() {
        assertFalse(changesFor("* fix Stravaganza login", "Strava"))
    }

    @Test
    fun `an app name inside a package name is not a mention`() {
        assertFalse(changesFor("* add Plus Messenger patches (org.telegram.plus v12.7.3.0)", "Telegram"))
        assertTrue(changesFor("* update recommended versions for Plus Messenger and Telegram.", "Telegram"))
    }

    @Test
    fun `a very short app name is not looked for in text`() {
        assertFalse(changesFor("* fix x86 crash in x mode", "X"))
    }

    @Test
    fun `a link target does not mention the app`() {
        assertFalse(changesFor("* fix login (closes [#1](https://github.com/someone/strava-patches/issues/1))", "Strava"))
    }

    @Test
    fun `an applied patch counts only when quoted`() {
        val patches = setOf("Signature spoof")
        assertTrue(changesFor("* Change `Signature spoof` to patch call sites", "FotMob", patchNames = patches))
        assertFalse(changesFor("* Change signature spoof to patch call sites", "FotMob", patchNames = patches))
    }

    @Test
    fun `adding experimental support is not a change in an unscoped bullet either`() {
        assertFalse(changesFor("* Add experimental support for Lyfta 4.2", "Lyfta"))
    }

    @Test
    fun `narrowing keeps the bullets naming the app and gathers the rest`() {
        val entries = ChangelogParser.parse(
            """
            # [1.1.0](https://example.com) (2026-10-01)

            ### Bug Fixes

            * **Lyfta:** fix login
            * restore Lyfta sync
            * update dependencies
            """.trimIndent()
        )
        val narrowed = ChangelogParser.entriesFor(entries, ChangelogSubject(setOf("Lyfta")), "General")

        assertEquals(
            "### Bug Fixes\n\n* **Lyfta:** fix login\n* restore Lyfta sync\n\n### General\n\n* update dependencies",
            narrowed.single().content
        )
    }
}
