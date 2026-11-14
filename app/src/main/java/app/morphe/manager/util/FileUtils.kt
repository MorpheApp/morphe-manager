/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.OutputStream
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
 * If the rename fails, the directory is deleted on the calling thread instead.
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
            // Without the rename the previous session's files would outlive the purge, so they
            // are wiped in place instead
            deleteRecursively()
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

/** Total size of the files under this one, or of this file itself. */
fun File.totalBytes(): Long =
    if (!exists()) 0L else walkBottomUp().filter { it.isFile }.sumOf { it.length() }

/** Deletes everything inside this directory, keeping the directory itself. */
fun File.wipeContents() {
    if (!exists()) return
    listFiles()?.forEach { it.deleteRecursively() }
}

/**
 * Writes this file through a sibling that is moved over it once complete, so a process killed
 * midway leaves the previous file whole rather than a half written one.
 */
fun File.writeAtomically(write: (OutputStream) -> Unit) {
    parentFile?.mkdirs()
    val partial = resolveSibling("$name.partial")
    try {
        partial.outputStream().use(write)
        if (!partial.renameTo(this)) throw IOException("Could not replace $this")
    } catch (e: Exception) {
        partial.delete()
        throw e
    }
}

fun File.writeTextAtomically(text: String) = writeAtomically { it.write(text.toByteArray()) }

/**
 * The folder at [uri], whether the system picker granted it or Morphe's own picker named it by
 * path, which needs no grant once storage access is given.
 */
fun Context.pickedFolder(uri: Uri): DocumentFile? =
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        uri.path?.let { DocumentFile.fromFile(File(it)) }
    } else {
        DocumentFile.fromTreeUri(this, uri)
    }

fun DocumentFile.getOrCreateFile(mimeType: String, name: String): DocumentFile? =
    findFile(name) ?: if (uri.scheme == ContentResolver.SCHEME_FILE) {
        // A plain file keeps its name as given, where createFile would add an extension for the type
        uri.path?.let { File(it, name) }?.takeIf { it.createNewFile() }?.let(DocumentFile::fromFile)
    } else {
        createFile(mimeType, name)
    }
