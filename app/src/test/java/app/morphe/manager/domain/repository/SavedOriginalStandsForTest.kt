/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedOriginalStandsForTest {
    private val dir = Files.createTempDirectory("morphe-saved-original").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun unsignedApk(name: String): File = dir.resolve(name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write("dex".toByteArray())
            zip.closeEntry()
        }
    }

    private fun signedApk(name: String): File {
        val file = unsignedApk(name)
        val bytes = file.readBytes()
        val archive = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val endOfCentralDirectory = (bytes.size - Int.SIZE_BYTES downTo 0)
            .first { archive.getInt(it) == END_OF_CENTRAL_DIRECTORY_SIGNATURE }
        val offsetField = endOfCentralDirectory + CENTRAL_DIRECTORY_OFFSET_FIELD
        val centralDirectory = archive.getInt(offsetField)

        val value = "certificate chain".toByteArray()
        val blockSize = Long.SIZE_BYTES + Int.SIZE_BYTES + value.size + Long.SIZE_BYTES + SIGNING_BLOCK_MAGIC.size
        val block = ByteBuffer.allocate(Long.SIZE_BYTES + blockSize).order(ByteOrder.LITTLE_ENDIAN)
        block.putLong(blockSize.toLong())
        block.putLong((Int.SIZE_BYTES + value.size).toLong())
        block.putInt(UNKNOWN_SCHEME_ID)
        block.put(value)
        block.putLong(blockSize.toLong())
        block.put(SIGNING_BLOCK_MAGIC)

        archive.putInt(offsetField, centralDirectory + block.capacity())
        file.writeBytes(
            bytes.copyOfRange(0, centralDirectory) + block.array() + bytes.copyOfRange(centralDirectory, bytes.size)
        )
        return file
    }

    @Test
    fun `a signed copy stands for a signed APK`() {
        assertTrue(savedOriginalStandsFor(signedApk("saved.apk"), signedApk("picked.apk")))
    }

    @Test
    fun `a copy saved without its signing block is replaced by a signed APK`() {
        assertFalse(savedOriginalStandsFor(unsignedApk("saved.apk"), signedApk("picked.apk")))
    }

    @Test
    fun `an unsigned copy stands for an unsigned APK`() {
        assertTrue(savedOriginalStandsFor(unsignedApk("saved.apk"), unsignedApk("merged.apk")))
    }

    @Test
    fun `a split archive stands for any APK`() {
        assertTrue(savedOriginalStandsFor(unsignedApk("saved.apks"), signedApk("picked.apk")))
    }

    @Test
    fun `a split archive replaces a single APK`() {
        assertFalse(savedOriginalStandsFor(signedApk("saved.apk"), unsignedApk("picked.apks")))
    }

    private companion object {
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
        const val CENTRAL_DIRECTORY_OFFSET_FIELD = 16
        const val UNKNOWN_SCHEME_ID = 0x0dec0ded
        val SIGNING_BLOCK_MAGIC = "APK Sig Block 42".toByteArray()
    }
}
