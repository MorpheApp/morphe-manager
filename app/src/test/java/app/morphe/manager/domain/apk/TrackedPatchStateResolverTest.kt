/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackedPatchStateResolverTest {
    private fun resolve(
        installedHashes: Set<String> = emptySet(),
        savedPatchedHashes: Set<String> = emptySet(),
        originalHashes: Set<String> = emptySet(),
        installedByPatchManager: Boolean = false,
        installerAttributionMatches: Boolean = true,
        installedAfterPatching: Boolean = false
    ) = resolveTrackedPatchState(
        installedHashes = installedHashes,
        savedPatchedHashes = savedPatchedHashes,
        originalHashes = originalHashes,
        installedByPatchManager = installedByPatchManager,
        installerAttributionMatches = installerAttributionMatches,
        installedAfterPatching = installedAfterPatching
    )

    @Test
    fun `saved patched certificate identifies the tracked install`() {
        assertEquals(
            InstalledPatchState.Patched,
            resolve(
                installedHashes = setOf("patched"),
                savedPatchedHashes = setOf("patched"),
                originalHashes = setOf("stock")
            )
        )
    }

    @Test
    fun `different certificate from saved patched APK remains unknown`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolve(
                installedHashes = setOf("unrecognized"),
                savedPatchedHashes = setOf("patched")
            )
        )
    }

    @Test
    fun `original certificate identifies a stock reinstall`() {
        assertEquals(
            InstalledPatchState.NotPatched,
            resolve(
                installedHashes = setOf("stock"),
                originalHashes = setOf("stock")
            )
        )
    }

    @Test
    fun `saved original certificate identifies stock when patched certificate differs`() {
        assertEquals(
            InstalledPatchState.NotPatched,
            resolve(
                installedHashes = setOf("stock"),
                savedPatchedHashes = setOf("patched"),
                originalHashes = setOf("stock")
            )
        )
    }

    @Test
    fun `certificate different from original remains unknown`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolve(
                installedHashes = setOf("unrecognized"),
                originalHashes = setOf("stock")
            )
        )
    }

    @Test
    fun `patch manager installer is a fallback when certificates are unavailable`() {
        assertEquals(
            InstalledPatchState.Patched,
            resolve(installedByPatchManager = true)
        )
    }

    @Test
    fun `patch manager installer identifies patched install when certificates differ`() {
        assertEquals(
            InstalledPatchState.Patched,
            resolve(
                installedHashes = setOf("patched"),
                savedPatchedHashes = setOf("old-patched"),
                originalHashes = setOf("stock"),
                installedByPatchManager = true
            )
        )
    }

    @Test
    fun `unverified same-name package is not assumed patched`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolve(originalHashes = setOf("stock"))
        )
    }

    @Test
    fun `unrecognized certificate without references remains unknown`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolve(installedHashes = setOf("unrecognized"))
        )
    }

    @Test
    fun `foreign installer on an installation newer than the patch identifies a replacement`() {
        assertEquals(
            InstalledPatchState.NotPatched,
            resolve(
                installedHashes = setOf("unrecognized"),
                installerAttributionMatches = false,
                installedAfterPatching = true
            )
        )
    }

    @Test
    fun `foreign installer alone does not identify a replacement`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolve(
                installedHashes = setOf("unrecognized"),
                installerAttributionMatches = false,
                installedAfterPatching = false
            )
        )
    }

    @Test
    fun `expected attribution keeps a newer installation unknown`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolve(
                installedHashes = setOf("unrecognized"),
                installerAttributionMatches = true,
                installedAfterPatching = true
            )
        )
    }

    @Test
    fun `patch manager attribution outweighs a newer installation`() {
        assertEquals(
            InstalledPatchState.Patched,
            resolve(
                installedHashes = setOf("patched"),
                installedByPatchManager = true,
                installerAttributionMatches = false,
                installedAfterPatching = true
            )
        )
    }

    @Test
    fun `saved patched certificate outweighs a foreign newer installation`() {
        assertEquals(
            InstalledPatchState.Patched,
            resolve(
                installedHashes = setOf("patched"),
                savedPatchedHashes = setOf("patched"),
                installerAttributionMatches = false,
                installedAfterPatching = true
            )
        )
    }
}
