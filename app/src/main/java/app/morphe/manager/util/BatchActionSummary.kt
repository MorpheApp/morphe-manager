package app.morphe.manager.util

import android.content.Context
import androidx.annotation.StringRes
import app.morphe.manager.R

fun Context.batchActionSummary(
    @StringRes completedActionRes: Int,
    completed: Int,
    skipped: Int
): String? {
    val parts = buildList {
        if (completed > 0) {
            add(getString(completedActionRes, completed))
        }
        if (skipped > 0) {
            add(getString(R.string.batch_skipped_summary, skipped))
        }
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
