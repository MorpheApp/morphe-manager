/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.api

import app.morphe.manager.network.dto.ReleaseAsset
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Release times reach the app from GitHub, from source manifests and from the Morphe API. Each is
 * kept in UTC, and its text is part of the key a refused release is remembered by on disk.
 */
class ReleaseTimestampTest {
    @Test
    fun `timestamps of every shape are read in UTC`() {
        assertEquals("2024-01-15T12:00", parseTimestamp("2024-01-15T12:00:00Z").toString())
        assertEquals("2024-01-15T10:00", parseTimestamp("2024-01-15T12:00:00+02:00").toString())
        assertEquals("2024-01-15T17:00", parseTimestamp("2024-01-15T12:00:00-05:00").toString())
        assertEquals("2024-01-15T12:00:30", parseTimestamp("2024-01-15T12:00:30").toString())
    }

    @Test
    fun `a manifest gives its release time without an offset`() {
        val asset = Json.decodeFromString<ReleaseAsset>(
            """{"download_url":"https://example.com/patches.mpp","created_at":"2026-11-03T12:13:45","description":"","version":"v1.0.0"}"""
        )

        assertEquals("2026-11-03T12:13:45", asset.createdAt.toString())
        assertEquals(1_793_708_025_000, asset.createdAtMillis)
    }
}
