/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

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
 */
internal fun writeXzArchive(archive: File, entries: Sequence<Pair<String, () -> ByteArray>>) {
    archive.parentFile.mkdirs()
    val options = LZMA2Options(LZMA2Options.PRESET_MAX).apply { dictSize = DICTIONARY_SIZE }

    XZOutputStream(archive.outputStream().buffered(), options).use { xz ->
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
