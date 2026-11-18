/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import java.io.File

/** Selects the first verified stock input for the patched release, skipping unusable copies. */
internal suspend fun <T : Any> selectMountStockCandidate(
    files: List<File>,
    readVerified: suspend (File) -> T?,
    matches: (T) -> Boolean
): Pair<File, T>? {
    for (file in files) {
        val info = readVerified(file) ?: continue
        if (matches(info)) return file to info
    }
    return null
}
