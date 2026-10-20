/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.tukaani.xz.FilterOptions
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Big enough to see across the whole runtime, small enough for the app to decompress it in memory
private const val DICTIONARY_SIZE = 32 shl 20

/**
 * Packs [entries] (path in the archive to content) as a zip of stored entries, compressed as a
 * whole with xz. Compressing it whole finds what the entries share, which deflating each one on
 * its own misses, and the zip inside it still reads as a stream.
 *
 * Entries are sorted and carry no timestamps, so the same content always packs the same way.
 *
 * @param bcj The branch converter for the machine code of the entries, which xz then finds more
 *            of alike in. The app's xz reads it back on its own.
 */
internal fun writeXzArchive(archive: File, entries: Sequence<Pair<String, () -> ByteArray>>, bcj: FilterOptions? = null) {
    xzOutput(archive, bcj).use { xz ->
        ZipOutputStream(xz).use { zip ->
            entries.sortedBy { it.first }.forEach { (path, read) ->
                val content = read()
                // A stored entry has to state its size and checksum before its content
                zip.putNextEntry(ZipEntry(path).apply {
                    method = ZipEntry.STORED
                    size = content.size.toLong()
                    compressedSize = content.size.toLong()
                    crc = CRC32().apply { update(content) }.value
                    time = 0
                })
                zip.write(content)
                zip.closeEntry()
            }
        }
    }
}

/** Compresses [content] with xz on its own, for a file the app reads as one stream. */
internal fun writeXzFile(file: File, content: ByteArray) {
    xzOutput(file, null).use { it.write(content) }
}

private fun xzOutput(file: File, bcj: FilterOptions?): XZOutputStream {
    file.parentFile.mkdirs()
    val lzma2 = LZMA2Options(LZMA2Options.PRESET_MAX).apply { dictSize = DICTIONARY_SIZE }
    return XZOutputStream(file.outputStream().buffered(), listOfNotNull(bcj, lzma2).toTypedArray())
}
