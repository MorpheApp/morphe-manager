/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginalApkStagingTest {
    private val dir = Files.createTempDirectory("morphe-original-apk").toFile()
    private val target = dir.resolve("com.example_1.0_original.apk")

    private fun stagedCopies() = dir.listFiles { file -> file.name.endsWith(STAGING_SUFFIX) }.orEmpty()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a completed copy replaces the archive that was there`() {
        target.writeText("old archive")
        val source = dir.resolve("merged.apk").apply { writeText("new archive") }

        copyThroughStaging(source, target)

        assertEquals("new archive", target.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a copy that fails leaves the previous archive in place`() {
        target.writeText("old archive")
        val unreadableSource = dir.resolve("missing.apk")

        assertFails { copyThroughStaging(unreadableSource, target) }

        assertEquals("old archive", target.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a moved file replaces the archive and leaves nothing at its old path`() {
        target.writeText("old archive")
        val source = dir.resolve("merged.apk").apply { writeText("new archive") }

        moveIntoPlace(source, target)

        assertEquals("new archive", target.readText())
        assertFalse(source.exists())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a move that fails leaves the previous archive in place`() {
        target.writeText("old archive")
        val missingSource = dir.resolve("missing.apk")

        assertFails { moveIntoPlace(missingSource, target) }

        assertEquals("old archive", target.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a copy that fails without an archive to replace leaves nothing behind`() {
        val unreadableSource = dir.resolve("missing.apk")

        assertFails { copyThroughStaging(unreadableSource, target) }

        assertTrue(!target.exists())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `temporary input moves the file into place and leaves no file at its old path`() {
        val temporarySource = dir.resolve("temporary.apk").apply { writeText("temporary archive") }

        retainOriginalApk(temporarySource, target, moveSource = true)

        assertEquals("temporary archive", target.readText())
        assertFalse(temporarySource.exists())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `non-temporary input copies through staging and preserves the source file intact`() {
        val persistentSource = dir.resolve("user_picked.apk").apply { writeText("persistent archive") }

        retainOriginalApk(persistentSource, target, moveSource = false)

        assertEquals("persistent archive", target.readText())
        assertTrue(persistentSource.exists())
        assertEquals("persistent archive", persistentSource.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `retention does nothing when source is already at target path`() {
        target.writeText("existing archive")

        retainOriginalApk(target, target, moveSource = true)

        assertEquals("existing archive", target.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a stripped APK replaces the archive, and one with nothing to strip leaves it alone`() {
        target.writeText("old archive")

        val abis = listOf("arm64-v8a")

        assertFalse(stripThroughStaging(apk("classes.dex", "lib/arm64-v8a/a.so"), target, abis))
        assertEquals("old archive", target.readText())

        assertTrue(stripThroughStaging(apk("classes.dex", "lib/arm64-v8a/a.so", "lib/x86/a.so"), target, abis))
        val entries = ZipFile(target).use { zip -> zip.entries().asSequence().map { it.name }.sorted().toList() }
        assertEquals(listOf("classes.dex", "lib/arm64-v8a/a.so"), entries)
        assertTrue(stagedCopies().isEmpty())
    }

    private fun apk(vararg entries: String) = dir.resolve("input.apk").apply {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(entry.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
