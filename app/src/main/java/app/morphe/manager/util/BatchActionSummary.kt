/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import androidx.annotation.PluralsRes
import app.morphe.manager.R

fun Context.batchActionSummary(
    @PluralsRes completedActionPluralRes: Int,
    completed: Int,
    skipped: Int
): String? {
    val parts = buildList {
        if (completed > 0) {
            add(resources.getQuantityString(completedActionPluralRes, completed, completed))
        }
        if (skipped > 0) {
            add(resources.getQuantityString(R.plurals.batch_skipped_summary, skipped, skipped))
        }
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
