package app.morphe.manager.patcher.util

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Makes an unsigned patcher output carry the original signature material again:
 * - v1 (JAR) signature files, when the stock APK has them, are merged back from META-INF;
 * - the APK Signing Block (v2/v3), which the repack drops, is grafted back verbatim.
 * With patched contents the restored signature verifies as FAILED (original certificate
 * visible), which is the state signature-kill tools expect as their input.
 */
object SignatureRestorer {
    private const val BLOCK_MAGIC = "APK Sig Block 42"

    fun restore(original: File, patched: File, output: File) {
        val merged = File.createTempFile("metainf-restored-", ".apk", patched.parentFile)
        try {
            mergeOriginalMetaInf(original, patched, merged)
            val block = readSigningBlock(original)
            if (block == null) {
                merged.copyTo(output, overwrite = true)
            } else {
                graftSigningBlock(block, merged, output)
            }
        } finally {
            merged.delete()
        }
    }

    private fun mergeOriginalMetaInf(original: File, patched: File, output: File) {
        val metaInf = LinkedHashMap<String, ByteArray>()
        ZipFile(original).use { src ->
            src.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("META-INF/") }
                .forEach { metaInf[it.name] = src.getInputStream(it).readBytes() }
        }
        ZipFile(patched).use { src ->
            ZipOutputStream(BufferedOutputStream(output.outputStream())).use { zos ->
                src.entries().asSequence()
                    .filter { !it.isDirectory && it.name !in metaInf }
                    .forEach { entry ->
                        val outEntry = ZipEntry(entry.name)
                        if (entry.method == ZipEntry.STORED) {
                            outEntry.method = ZipEntry.STORED
                            outEntry.size = entry.size
                            outEntry.crc = entry.crc
                        }
                        zos.putNextEntry(outEntry)
                        src.getInputStream(entry).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                metaInf.forEach { (name, bytes) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
    }

    /** Reads the whole APK Signing Block (it ends exactly at the central directory start). */
    private fun readSigningBlock(apk: File): ByteArray? {
        RandomAccessFile(apk, "r").use { f ->
            val eocd = findEocd(f) ?: return null
            val cdOffset = readU32(f, eocd + 16)
            if (cdOffset < 32 || cdOffset > f.length()) return null
            f.seek(cdOffset - 16)
            val magic = ByteArray(16)
            f.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != BLOCK_MAGIC) return null
            val size = readU64(f, cdOffset - 24)
            val blockStart = cdOffset - 8 - size
            if (blockStart < 0) return null
            if (readU64(f, blockStart) != size) return null
            f.seek(blockStart)
            val block = ByteArray((size + 8).toInt())
            f.readFully(block)
            return block
        }
    }

    /** Inserts [block] before the central directory of [zip] and fixes the EOCD offset. */
    private fun graftSigningBlock(block: ByteArray, zip: File, out: File) {
        RandomAccessFile(zip, "r").use { f ->
            val eocd = findEocd(f) ?: run {
                zip.copyTo(out, overwrite = true)
                return
            }
            val cdOffset = readU32(f, eocd + 16)
            val total = f.length()
            out.outputStream().buffered().use { o ->
                copyRange(f, o, 0, cdOffset)
                o.write(block)
                copyRange(f, o, cdOffset, eocd)
                val tail = ByteArray((total - eocd).toInt())
                f.seek(eocd)
                f.readFully(tail)
                ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(16, (cdOffset + block.size).toInt())
                o.write(tail)
            }
        }
    }

    private fun findEocd(f: RandomAccessFile): Long? {
        val len = f.length()
        val start = maxOf(0L, len - 22 - 65535)
        val buf = ByteArray((len - start).toInt())
        f.seek(start)
        f.readFully(buf)
        for (i in buf.size - 22 downTo 0) {
            if (buf[i].toInt() == 0x50 &&
                buf[i + 1].toInt() == 0x4b &&
                buf[i + 2].toInt() == 0x05 &&
                buf[i + 3].toInt() == 0x06
            ) {
                return start + i
            }
        }
        return null
    }

    private fun readU32(f: RandomAccessFile, pos: Long): Long {
        val b = ByteArray(4)
        f.seek(pos)
        f.readFully(b)
        var v = 0L
        for (i in 3 downTo 0) {
            v = (v shl 8) or (b[i].toInt() and 0xff).toLong()
        }
        return v
    }

    private fun readU64(f: RandomAccessFile, pos: Long): Long {
        val b = ByteArray(8)
        f.seek(pos)
        f.readFully(b)
        var v = 0L
        for (i in 7 downTo 0) {
            v = (v shl 8) or (b[i].toInt() and 0xff).toLong()
        }
        return v
    }

    private fun copyRange(f: RandomAccessFile, out: OutputStream, from: Long, to: Long) {
        f.seek(from)
        var remaining = to - from
        val buf = ByteArray(64 * 1024)
        while (remaining > 0) {
            val read = f.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (read < 0) break
            out.write(buf, 0, read)
            remaining -= read
        }
    }
}