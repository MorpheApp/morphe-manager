/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.bundles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A bundle can declare one package and version through several compatible package entries, one
 * per accepted build, and [AppVersionCatalog] folds every entry it sees for that version into this
 * map. Overwriting instead of unioning would leave only the last build's codes standing, and the
 * version-code check downstream would turn away every build the earlier entries accepted.
 */
class MergeVersionCodesTest {

    @Test
    fun `codes from several entries for the same version are unioned`() {
        val codesMap = mutableMapOf<String, Set<Int>?>()

        codesMap.mergeVersionCodes("580.0.0.49.91", setOf(346013387))
        codesMap.mergeVersionCodes("580.0.0.49.91", setOf(346013440))
        codesMap.mergeVersionCodes("580.0.0.49.91", setOf(346013442, 346013354, 346013370))

        assertEquals(
            setOf(346013387, 346013440, 346013442, 346013354, 346013370),
            codesMap["580.0.0.49.91"]
        )
    }

    @Test
    fun `an unconstrained entry wins over codes already recorded`() {
        val codesMap = mutableMapOf<String, Set<Int>?>()

        codesMap.mergeVersionCodes("580.0.0.49.91", setOf(346013387))
        codesMap.mergeVersionCodes("580.0.0.49.91", null)

        assertNull(codesMap["580.0.0.49.91"])
    }

    @Test
    fun `an unconstrained entry stays unconstrained however it is followed`() {
        val codesMap = mutableMapOf<String, Set<Int>?>()

        codesMap.mergeVersionCodes("580.0.0.49.91", null)
        codesMap.mergeVersionCodes("580.0.0.49.91", setOf(346013387))

        assertNull(codesMap["580.0.0.49.91"])
    }

    @Test
    fun `versions stay independent`() {
        val codesMap = mutableMapOf<String, Set<Int>?>()

        codesMap.mergeVersionCodes("580.0.0.49.91", setOf(346013387))
        codesMap.mergeVersionCodes("579.0.0.30.10", setOf(111111111))

        assertEquals(setOf(346013387), codesMap["580.0.0.49.91"])
        assertEquals(setOf(111111111), codesMap["579.0.0.30.10"])
    }
}
