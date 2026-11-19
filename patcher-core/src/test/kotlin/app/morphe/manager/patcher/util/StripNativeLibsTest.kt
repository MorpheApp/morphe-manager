/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.util

import com.reandroid.archive.ArchiveFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An original kept for later runs loses the same native libraries the patcher strips from the
 * output, and nothing else.
 */
class StripNativeLibsTest {
    private val workspace = createTempDirectory("strip-native-libs").toFile()
    private val output = File(workspace, "stripped.apk")

    @AfterTest
    fun cleanup() {
        workspace.deleteRecursively()
    }

    private fun apk(vararg entries: String): File {
        val file = File(workspace, "input.apk")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(entry.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private fun withSigningBlock(apk: File, pairs: Map<Int, ByteArray>): File {
        val signed = File(workspace, "signed.apk")
        val bytes = apk.readBytes()
        val archive = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val endOfCentralDirectory = (bytes.size - Int.SIZE_BYTES downTo 0)
            .first { archive.getInt(it) == END_OF_CENTRAL_DIRECTORY_SIGNATURE }
        val offsetField = endOfCentralDirectory + CENTRAL_DIRECTORY_OFFSET_FIELD
        val centralDirectory = archive.getInt(offsetField)

        val pairsSize = pairs.values.sumOf { Long.SIZE_BYTES + Int.SIZE_BYTES + it.size }
        val blockSize = pairsSize + Long.SIZE_BYTES + SIGNING_BLOCK_MAGIC.size
        val block = ByteBuffer.allocate(Long.SIZE_BYTES + blockSize).order(ByteOrder.LITTLE_ENDIAN)
        block.putLong(blockSize.toLong())
        pairs.forEach { (id, value) ->
            block.putLong((Int.SIZE_BYTES + value.size).toLong())
            block.putInt(id)
            block.put(value)
        }
        block.putLong(blockSize.toLong())
        block.put(SIGNING_BLOCK_MAGIC)

        archive.putInt(offsetField, centralDirectory + block.capacity())
        signed.writeBytes(
            bytes.copyOfRange(0, centralDirectory) + block.array() + bytes.copyOfRange(centralDirectory, bytes.size)
        )
        return signed
    }

    private fun signingBlockOf(file: File): Map<Int, List<Byte>>? =
        ArchiveFile(file).use { archive ->
            archive.apkSignatureBlock?.filter { it.idValue != PADDING_ID }?.associate { pair ->
                val raw = File(workspace, "pair.bin").also(pair::writeRaw).readBytes()
                pair.idValue to raw.toList()
            }
        }

    private fun entriesOf(file: File) = ZipFile(file).use { zip -> zip.entries().asSequence().map { it.name }.toList() }

    @Test
    fun `only the libraries of the ABI the device prefers are kept`() {
        val input = apk(
            "AndroidManifest.xml",
            "classes.dex",
            "lib/arm64-v8a/libapp.so",
            "lib/armeabi-v7a/libapp.so",
            "lib/x86_64/libapp.so"
        )

        assertTrue(NativeLibs.writeStripped(input, output, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(
            listOf("AndroidManifest.xml", "classes.dex", "lib/arm64-v8a/libapp.so"),
            entriesOf(output).sorted()
        )
    }

    @Test
    fun `an APK with nothing to strip is not written`() {
        val input = apk("classes.dex", "lib/arm64-v8a/libapp.so")

        assertFalse(NativeLibs.writeStripped(input, output, listOf("arm64-v8a")))
        assertFalse(output.exists())
    }

    @Test
    fun `an APK with nothing the device runs keeps every library`() {
        // As the patcher does, rather than leaving the app without native code
        val input = apk("classes.dex", "lib/x86/libapp.so", "lib/x86_64/libapp.so")

        assertFalse(NativeLibs.writeStripped(input, output, listOf("arm64-v8a")))
        assertFalse(output.exists())
    }

    @Test
    fun `the signing block of the input is kept`() {
        val input = withSigningBlock(
            apk("classes.dex", "lib/arm64-v8a/libapp.so", "lib/x86_64/libapp.so"),
            mapOf(FIRST_UNKNOWN_ID to "certificate chain".toByteArray(), SECOND_UNKNOWN_ID to "other signer".toByteArray())
        )
        val signingBlock = signingBlockOf(input)

        assertTrue(NativeLibs.writeStripped(input, output, listOf("arm64-v8a")))
        assertEquals(listOf("classes.dex", "lib/arm64-v8a/libapp.so"), entriesOf(output).sorted())
        assertEquals(setOf(FIRST_UNKNOWN_ID, SECOND_UNKNOWN_ID), signingBlock?.keys)
        assertEquals(signingBlock, signingBlockOf(output))
    }

    @Test
    fun `an unsigned APK stays unsigned`() {
        val input = apk("classes.dex", "lib/arm64-v8a/libapp.so", "lib/x86_64/libapp.so")

        assertTrue(NativeLibs.writeStripped(input, output, listOf("arm64-v8a")))
        assertNull(signingBlockOf(output))
    }

    private companion object {
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
        const val CENTRAL_DIRECTORY_OFFSET_FIELD = 16
        const val FIRST_UNKNOWN_ID = 0x0dec0ded
        const val SECOND_UNKNOWN_ID = 0x0badf00d
        const val PADDING_ID = 0x42726577
        val SIGNING_BLOCK_MAGIC = "APK Sig Block 42".toByteArray()
    }
}
