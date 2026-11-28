/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Local file header of a zip archive, the container both patch bundles and APKs use. */
private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/**
 * Whether the file is a whole zip archive with at least one entry.
 *
 * A download can succeed and still hand back the wrong thing: an API serving metadata instead of
 * the asset, or a captive portal answering with HTML, which the header at the start rules out.
 * The central directory at the end is read too, so a download cut short is caught as well.
 */
fun File.isCompleteZip(): Boolean = hasZipHeader() && runCatching { ZipFile(this).close() }.isSuccess

/**
 * Throws if the file is not [isCompleteZip], and deletes it so nothing broken is left for a later
 * install to pick up. [context] says in the message what the file came from.
 */
fun File.requireCompleteZip(context: String) {
    if (isCompleteZip()) return
    val size = runCatching { length() }.getOrDefault(0L)
    runCatching { delete() }
    throw IOException("$context: not a whole zip archive (size=$size)")
}

private fun File.hasZipHeader(): Boolean = runCatching {
    inputStream().use { input ->
        val header = ByteArray(ZIP_HEADER.size)
        input.read(header) == header.size && header.contentEquals(ZIP_HEADER)
    }
}.getOrDefault(false)
