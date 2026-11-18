/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The verifier's verdict must decide root retention before any previous copy is replaced. */
class OriginalApkMountRetentionTest {
    private val dir = Files.createTempDirectory("morphe-mount-originals").toFile()
    private val saved = dir.resolve("com.example_1.0_original.apk")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun input(name: String, contents: String) = dir.resolve(name).apply { writeText(contents) }

    private fun bundle(name: String): File = dir.resolve(name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            for (entry in listOf("base.apk", "split_config.arm64_v8a.apk")) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(entry.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `a legacy merged copy does not suppress a fresh root original of the same version`() {
        saved.writeText("legacy merged repatching input")
        val fresh = input("original.apk", "fresh signed original")
        val verifies: (File) -> Boolean = { it.readText() == "fresh signed original" }

        assertFalse(canReuseSavedOriginal(saved, fresh, forMount = true, verifyStock = verifies))
        assertTrue(retainOriginalApk(fresh, saved, moveSource = false, verify = verifies))

        assertEquals(fresh.readText(), saved.readText())
        assertTrue(fresh.exists())
    }

    @Test
    fun `an invalid saved bundle can be replaced by the actual root bundle`() {
        val oldBundle = bundle("old-original.apk")
        val fresh = bundle("original.apkm")

        assertFalse(canReuseSavedOriginal(oldBundle, fresh, forMount = true) { false })
        assertTrue(retainOriginalApk(fresh, saved, moveSource = false) {
            it.readBytes().contentEquals(fresh.readBytes())
        })
        assertContentEquals(fresh.readBytes(), saved.readBytes())
        assertTrue(oldBundle.exists())
    }

    @Test
    fun `nonmount repatching keeps a transformed original without verifying it as stock`() {
        saved.writeText("merged or native-library-stripped input")
        val fresh = input("another.apk", "same-version patching input")

        assertTrue(canReuseSavedOriginal(saved, fresh, forMount = false) {
            error("A repatching input must not be treated as stock")
        })
        assertEquals("merged or native-library-stripped input", saved.readText())
    }

    @Test
    fun `a genuine root archive is reused rather than replaced with a mono input`() {
        val archive = bundle("original.apkm")
        val fresh = input("input.apk", "mono input")

        assertTrue(canReuseSavedOriginal(archive, fresh, forMount = true) { it == archive })
    }

    @Test
    fun `a genuine split bundle still replaces a merged nonmount copy`() {
        saved.writeText("merged input")

        assertFalse(canReuseSavedOriginal(saved, bundle("original.apkm"), forMount = false) {
            error("Nonmount retention does not need stock verification")
        })
    }

    @Test
    fun `rejecting a root original preserves both the previous copy and temporary selected input`() {
        saved.writeText("previous retained copy")
        val rejected = input("temporary.apk", "modified original")

        assertFalse(retainOriginalApk(rejected, saved, moveSource = true) { false })

        assertEquals("previous retained copy", saved.readText())
        assertEquals("modified original", rejected.readText())
        assertTrue(dir.listFiles { file -> file.name.endsWith(STAGING_SUFFIX) }.orEmpty().isEmpty())
    }

    @Test
    fun `a root copy is verified before replacing the previous original or removing its input`() {
        saved.writeText("previous retained copy")
        val fresh = input("temporary.apk", "fresh signed original")
        var verified = false

        assertTrue(retainOriginalApk(fresh, saved, moveSource = true) { staged ->
            assertEquals("previous retained copy", saved.readText())
            assertEquals("fresh signed original", fresh.readText())
            assertEquals("fresh signed original", staged.readText())
            assertFalse(staged == fresh || staged == saved)
            verified = true
            true
        })

        assertTrue(verified)
        assertEquals("fresh signed original", saved.readText())
        assertFalse(fresh.exists())
        assertTrue(dir.listFiles { file -> file.name.endsWith(STAGING_SUFFIX) }.orEmpty().isEmpty())
    }

    @Test
    fun `rejecting the selected retained path does not delete its repatching contents`() {
        saved.writeText("legacy merged input")

        assertFalse(retainOriginalApk(saved, saved, moveSource = true) { false })

        assertEquals("legacy merged input", saved.readText())
    }

    @Test
    fun `a missing saved original never blocks retaining a fresh input`() {
        val fresh = input("original.apk", "original")

        assertFalse(canReuseSavedOriginal(saved, fresh, forMount = true) {
            error("Missing files are rejected before verification")
        })
        assertTrue(retainOriginalApk(fresh, saved, moveSource = false) { true })
        assertEquals("original", saved.readText())
    }
}
