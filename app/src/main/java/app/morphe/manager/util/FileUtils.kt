/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Purges the contents of this directory without blocking the calling thread.
 *
 * If the directory exists:
 * 1. Atomically renames the directory to a temporary trash name in the same parent
 *    directory (an O(1) filesystem metadata operation on the calling thread).
 * 2. Immediately recreates the clean, empty directory at the original path.
 * 3. Launches a background coroutine on [dispatcher] to recursively delete the renamed
 *    trash directory (and any stale trash directories left behind by previous crashes or kills).
 *
 * If the directory does not exist, simply ensures it exists via [File.mkdirs].
 */
fun File.purgeDirectoryAsync(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): Job {
    val trashDir = if (exists()) {
        val parent = parentFile
        val candidate = if (parent != null) {
            File(parent, "${name}_trash_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}")
        } else {
            File("${name}_trash_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}")
        }
        if (renameTo(candidate)) {
            candidate
        } else {
            null
        }
    } else {
        null
    }

    // Ensure the original directory exists and is immediately usable by downstream components
    mkdirs()

    return scope.launch(dispatcher) {
        if (trashDir != null) {
            runCatching { trashDir.deleteRecursively() }
        }
        // Also sweep any stale trash directories matching the pattern in the parent directory
        val parent = parentFile
        if (parent != null) {
            val prefix = "${name}_trash_"
            runCatching {
                parent.listFiles { f -> f.isDirectory && f.name.startsWith(prefix) }
                    ?.forEach { it.deleteRecursively() }
            }
        }
    }
}
