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

    private fun entriesOf(file: File) = ZipFile(file).use { zip -> zip.entries().asSequence().map { it.name }.toList() }

    /**
     * Puts an APK signing block holding a single [BLOCK_ID] pair before the central directory of
     * [file], as a signer lays it out, and points the end record at the moved directory.
     */
    private fun addSigningBlock(file: File) {
        val value = "certificates".toByteArray()
        val zip = file.readBytes()
        val endRecord = (zip.size - 22 downTo 0).first {
            ByteBuffer.wrap(zip, it, 4).order(ByteOrder.LITTLE_ENDIAN).int == 0x06054b50
        }
        val directoryOffset = ByteBuffer.wrap(zip, endRecord + 16, 4).order(ByteOrder.LITTLE_ENDIAN).int

        val pairLength = 4 + value.size
        val blockSize = 8L + pairLength + 8 + 16
        val block = ByteBuffer.allocate((8 + blockSize).toInt()).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(blockSize)
            .putLong(pairLength.toLong()).putInt(BLOCK_ID).put(value)
            .putLong(blockSize)
            .put("APK Sig Block 42".toByteArray())
            .array()

        val signed = zip.copyOfRange(0, directoryOffset) + block + zip.copyOfRange(directoryOffset, zip.size)
        ByteBuffer.wrap(signed, endRecord + block.size + 16, 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(directoryOffset + block.size)
        file.writeBytes(signed)
    }

    private fun signingBlockIds(file: File) = ArchiveFile(file).use { archive ->
        archive.apkSignatureBlock?.map { it.idValue }.orEmpty()
    }

    @Test
    fun `the signing block survives, for the certificates patches read from it`() {
        val input = apk("classes.dex", "lib/arm64-v8a/libapp.so", "lib/x86_64/libapp.so")
        addSigningBlock(input)

        assertTrue(NativeLibs.writeStripped(input, output, listOf("arm64-v8a")))
        assertTrue(BLOCK_ID in signingBlockIds(output))
    }

    @Test
    fun `an unsigned APK stays unsigned`() {
        val input = apk("classes.dex", "lib/arm64-v8a/libapp.so", "lib/x86_64/libapp.so")

        assertTrue(NativeLibs.writeStripped(input, output, listOf("arm64-v8a")))
        assertTrue(signingBlockIds(output).isEmpty())
        assertFalse(NativeLibs.hasSigningBlock(output))
    }

    @Test
    fun `a signing block is told apart from none`() {
        val input = apk("classes.dex")
        assertFalse(NativeLibs.hasSigningBlock(input))

        addSigningBlock(input)
        assertTrue(NativeLibs.hasSigningBlock(input))
    }

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

    private companion object {
        // An ID no scheme uses, so the block is carried over as it is rather than parsed
        const val BLOCK_ID = 0x5EED0001
    }
}
