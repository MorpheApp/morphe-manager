/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackedPatchStateResolverTest {
    @Test
    fun `saved patched certificate identifies the tracked install`() {
        assertEquals(
            InstalledPatchState.Patched,
            resolveTrackedPatchState(
                installedHashes = setOf("patched"),
                savedPatchedHashes = setOf("patched"),
                originalHashes = setOf("stock"),
                installedByPatchManager = false
            )
        )
    }

    @Test
    fun `different certificate from saved patched APK remains unknown`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolveTrackedPatchState(
                installedHashes = setOf("unrecognized"),
                savedPatchedHashes = setOf("patched"),
                originalHashes = emptySet(),
                installedByPatchManager = false
            )
        )
    }

    @Test
    fun `original certificate identifies a stock reinstall`() {
        assertEquals(
            InstalledPatchState.NotPatched,
            resolveTrackedPatchState(
                installedHashes = setOf("stock"),
                savedPatchedHashes = emptySet(),
                originalHashes = setOf("stock"),
                installedByPatchManager = false
            )
        )
    }

    @Test
    fun `saved original certificate identifies stock when patched certificate differs`() {
        assertEquals(
            InstalledPatchState.NotPatched,
            resolveTrackedPatchState(
                installedHashes = setOf("stock"),
                savedPatchedHashes = setOf("patched"),
                originalHashes = setOf("stock"),
                installedByPatchManager = false
            )
        )
    }

    @Test
    fun `certificate different from original remains unknown`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolveTrackedPatchState(
                installedHashes = setOf("unrecognized"),
                savedPatchedHashes = emptySet(),
                originalHashes = setOf("stock"),
                installedByPatchManager = false
            )
        )
    }

    @Test
    fun `patch manager installer is a fallback when certificates are unavailable`() {
        assertEquals(
            InstalledPatchState.Patched,
            resolveTrackedPatchState(
                installedHashes = emptySet(),
                savedPatchedHashes = emptySet(),
                originalHashes = emptySet(),
                installedByPatchManager = true
            )
        )
    }

    @Test
    fun `patch manager installer identifies patched install when certificates differ`() {
        assertEquals(
            InstalledPatchState.Patched,
            resolveTrackedPatchState(
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
            resolveTrackedPatchState(
                installedHashes = emptySet(),
                savedPatchedHashes = emptySet(),
                originalHashes = setOf("stock"),
                installedByPatchManager = false
            )
        )
    }

    @Test
    fun `unrecognized certificate without references remains unknown`() {
        assertEquals(
            InstalledPatchState.Unknown,
            resolveTrackedPatchState(
                installedHashes = setOf("unrecognized"),
                savedPatchedHashes = emptySet(),
                originalHashes = emptySet(),
                installedByPatchManager = false
            )
        )
    }
}
