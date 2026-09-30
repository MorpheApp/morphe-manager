/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.SupportedAbi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A bundle names several accepted builds under one version string by declaring several targets
 * for it, one per build. The merged codes have to be the union of all of them, or the version-code
 * check downstream would turn away every build but the last one merged.
 */
class MergedVersionCodesTest {

    private fun target(version: String?, code: Int?) = AppTarget(
        version = version,
        versionCodes = code?.let { mapOf(SupportedAbi.ARM64_V8A to it) }
    )

    @Test
    fun `codes from several targets sharing a version are unioned`() {
        val targets = listOf(
            target("580.0.0.49.91", 346013387),
            target("580.0.0.49.91", 346013440),
            target("580.0.0.49.91", 346013442),
            target("580.0.0.49.91", 346013354),
            target("580.0.0.49.91", 346013370),
        )

        val merged = targets.mergedVersionCodes()

        assertEquals(
            setOf(346013387, 346013440, 346013442, 346013354, 346013370),
            merged["580.0.0.49.91"]
        )
    }

    @Test
    fun `a target with no codes leaves the version unconstrained even if others named codes`() {
        val targets = listOf(
            target("580.0.0.49.91", 346013387),
            target("580.0.0.49.91", null),
        )

        val merged = targets.mergedVersionCodes()

        assertNull(merged["580.0.0.49.91"])
    }

    @Test
    fun `versions are kept apart and a single target still resolves as before`() {
        val targets = listOf(
            target("580.0.0.49.91", 346013387),
            target("579.0.0.30.10", 111111111),
        )

        val merged = targets.mergedVersionCodes()

        assertEquals(setOf(346013387), merged["580.0.0.49.91"])
        assertEquals(setOf(111111111), merged["579.0.0.30.10"])
    }

    @Test
    fun `a target with a null version contributes nothing`() {
        val targets = listOf(AppTarget(version = null))

        assertEquals(emptyMap(), targets.mergedVersionCodes())
    }
}
