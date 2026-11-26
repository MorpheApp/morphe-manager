/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

/**
 * This check is the last thing between a download that merely transferred cleanly and a file that
 * gets installed. It earns its place from real cases: an API answered a download with its own
 * JSON metadata, which the manager then stored as a patch bundle without noticing, and a bundle
 * cut short read as an unsigned one.
 */
class ArchiveUtilsTest {
    private val temporaryFiles = mutableListOf<File>()

    private fun tempFile(): File = File.createTempFile("archive-test", null).also { temporaryFiles += it }

    private fun fileOf(vararg bytes: Int): File =
        tempFile().apply { writeBytes(bytes.map(Int::toByte).toByteArray()) }

    private fun fileOf(content: String): File = tempFile().apply { writeText(content) }

    private fun zipFile(): File = tempFile().apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(ByteArray(1024))
            zip.closeEntry()
        }
    }

    @AfterTest
    fun cleanUp() {
        temporaryFiles.forEach { it.delete() }
    }

    @Test
    fun `whole archive is accepted`() {
        assertTrue(zipFile().isCompleteZip())
    }

    @Test
    fun `api metadata is rejected`() {
        val metadata = """{"url":"https://api.github.com/repos/o/r/releases/assets/1","name":"patches.mpp"}"""
        assertFalse(fileOf(metadata).isCompleteZip())
    }

    @Test
    fun `captive portal html is rejected`() {
        assertFalse(fileOf("<!DOCTYPE html><html><body>Sign in to continue</body></html>").isCompleteZip())
    }

    @Test
    fun `empty archive is rejected`() {
        // A valid archive, but never a real bundle or APK
        val empty = tempFile().apply { ZipOutputStream(outputStream()).close() }
        assertFalse(empty.isCompleteZip())
    }

    @Test
    fun `archive cut short is rejected though its header is intact`() {
        // The signature sits in the comment at the very end, so a cut short bundle reads as unsigned
        val cut = zipFile().apply { writeBytes(readBytes().copyOf(length().toInt() / 2)) }
        assertFalse(cut.isCompleteZip())
    }

    @Test
    fun `header alone, truncated and empty files are rejected`() {
        assertFalse(fileOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00).isCompleteZip())
        assertFalse(fileOf(0x50, 0x4B).isCompleteZip())
        assertFalse(fileOf("").isCompleteZip())
    }

    @Test
    fun `missing file is rejected rather than thrown`() {
        val missing = tempFile().also { it.delete() }
        assertFalse(missing.isCompleteZip())
    }

    @Test
    fun `rejected file is deleted`() {
        val page = fileOf("<html></html>")
        assertFailsWith<IOException> { page.requireCompleteZip("Downloading") }
        assertFalse(page.exists())
    }

    @Test
    fun `whole archive is kept`() {
        val archive = zipFile()
        archive.requireCompleteZip("Downloading")
        assertTrue(archive.exists())
    }
}
