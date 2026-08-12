/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackedEvidenceInvalidationTest {
    @Test
    fun `added removed and replaced evidence keys are invalidated`() {
        assertEquals(
            setOf("added", "removed", "replaced"),
            changedMapKeys(
                previous = mapOf(
                    "unchanged" to "same",
                    "removed" to "old",
                    "replaced" to "old"
                ),
                current = mapOf(
                    "unchanged" to "same",
                    "added" to "new",
                    "replaced" to "new"
                )
            )
        )
    }
}
