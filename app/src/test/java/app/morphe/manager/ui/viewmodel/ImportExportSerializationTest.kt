/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import kotlin.test.*

/**
 * Backup files outlive the manager version that wrote them: users restore a year-old export on a
 * new device. These cases pin the two export formats against each other, because settings exports
 * drop unset fields while selection exports must keep writing them.
 */
class ImportExportSerializationTest {
    private val json = ImportExportViewModel.json
    private val settingsJson = ImportExportViewModel.settingsJson

    private fun bundleSnapshot(
        prerelease: Boolean? = null,
        experimentalVersions: Boolean? = null,
    ) = BundleSnapshot(
        name = "Custom patches",
        source = "https://example.com/patches/dev",
        autoUpdate = true,
        sortOrder = 1,
        prerelease = prerelease,
        experimentalVersions = experimentalVersions,
    )

    @Test
    fun `per-source toggles survive a settings round trip`() {
        val original = bundleSnapshot(prerelease = true, experimentalVersions = false)

        val decoded = settingsJson.decodeFromString<BundleSnapshot>(
            settingsJson.encodeToString(original)
        )

        assertEquals(original, decoded)
        assertEquals(true, decoded.prerelease)
        assertEquals(false, decoded.experimentalVersions)
    }

    @Test
    fun `settings exports omit unset fields`() {
        val encoded = settingsJson.encodeToString(bundleSnapshot())

        assertFalse(encoded.contains("displayName"), "unset fields must not reach the file: $encoded")
        assertFalse(encoded.contains("prerelease"), "unset toggles must not reach the file: $encoded")
        assertTrue(encoded.contains("sortOrder"), "required fields are always written: $encoded")
    }

    @Test
    fun `backups written before per-source toggles decode as unset`() {
        val legacy = """
            {
              "name": "Custom patches",
              "source": "https://example.com/patches",
              "autoUpdate": false,
              "sortOrder": 0
            }
        """.trimIndent()

        val decoded = settingsJson.decodeFromString<BundleSnapshot>(legacy)

        // Null is what keeps Merge from touching toggles the backup never spoke about
        assertNull(decoded.prerelease)
        assertNull(decoded.experimentalVersions)
    }

    @Test
    fun `unknown fields from newer backups are ignored`() {
        val forwardLooking = """
            {
              "name": "Custom patches",
              "source": "https://example.com/patches",
              "autoUpdate": false,
              "sortOrder": 0,
              "somethingAddedLater": true
            }
        """.trimIndent()

        assertEquals("Custom patches", settingsJson.decodeFromString<BundleSnapshot>(forwardLooking).name)
    }

    @Test
    fun `selection exports keep writing null options`() {
        val encoded = json.encodeToString(
            PatchBundleDataExportFile(
                bundleUid = 0,
                exportDate = "2026-08-11T12:00:00",
                selections = mapOf("com.example.app" to listOf("Some patch")),
                options = null,
            )
        )

        // Older manager versions require the field, so this format must not follow the settings one
        assertTrue(encoded.contains("\"options\": null"), "options must stay explicit: $encoded")
    }

    @Test
    fun `selection exports round trip through the null options they write`() {
        val original = PatchBundleDataExportFile(
            bundleUid = 0,
            exportDate = "2026-08-11T12:00:00",
            selections = mapOf("com.example.app" to listOf("Some patch")),
            options = null,
        )

        val decoded = json.decodeFromString<PatchBundleDataExportFile>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertNull(decoded.options)
    }
}
