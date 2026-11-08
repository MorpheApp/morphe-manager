/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.batch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What an automatic helper pass is allowed to download for. Asking for the wrong app wastes a
 * download the user never requested, while skipping one that is verifiable leaves the pass
 * stopping for a tap it did not have to.
 */
class AutoDownloadCandidateTest {
    @Test
    fun `only apps without an APK are taken, in queue order`() {
        val items = listOf(
            item("ready", BatchItemState.READY),
            item("missing-one", BatchItemState.NEEDS_APK),
            item("excluded", BatchItemState.EXCLUDED),
            item("missing-two", BatchItemState.NEEDS_APK)
        )

        assertEquals(
            listOf("missing-one", "missing-two"),
            items.autoDownloadCandidates(emptySet()) { true }.map { it.id }
        )
    }

    @Test
    fun `an app the pass already asked for is not asked for again`() {
        val items = listOf(
            item("first", BatchItemState.NEEDS_APK),
            item("second", BatchItemState.NEEDS_APK)
        )

        assertEquals(listOf("second"), items.autoDownloadCandidates(setOf("first")) { true }.map { it.id })
    }

    @Test
    fun `an APK whose signature cannot be checked is left to the user`() {
        val items = listOf(
            item("unverifiable", BatchItemState.NEEDS_APK),
            item("verifiable", BatchItemState.NEEDS_APK)
        )

        val candidates = items.autoDownloadCandidates(emptySet()) { it.id == "verifiable" }

        assertEquals(listOf("verifiable"), candidates.map { it.id })
        assertTrue(items.autoDownloadCandidates(setOf("unverifiable", "verifiable")) { true }.isEmpty())
    }

    private fun item(id: String, state: BatchItemState) = BatchPatchItem(
        target = BatchTarget(packageName = id),
        appName = id,
        source = null,
        selection = emptyMap(),
        options = emptyMap(),
        bundles = emptyList(),
        state = state
    )
}
