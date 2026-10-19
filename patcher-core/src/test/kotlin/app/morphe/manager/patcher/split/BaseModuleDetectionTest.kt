/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.split

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The base module of a split archive is told apart by its manifest, since an .xapk names it after
 * the package and such a name can read like a config split.
 */
class BaseModuleDetectionTest {
    private val workspace = createTempDirectory("base-module").toFile()

    @AfterTest
    fun cleanup() {
        workspace.deleteRecursively()
    }

    private fun manifest(split: String?): ByteArray = AndroidManifestBlock().apply {
        packageName = "com.example.app"
        if (split != null) setSplit(split, true)
        refresh()
    }.bytes

    private fun module(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun <T> withArchive(vararg modules: Pair<String, ByteArray>, block: (ZipFile) -> T): T {
        val file = File(workspace, "bundle.xapk")
        file.writeBytes(module(*modules))
        return ZipFile(file).use(block)
    }

    private fun isBase(name: String, content: ByteArray) = withArchive(name to content) { zip ->
        SplitApkPreparer.isBaseModule(zip, zip.getEntry(name))
    }

    @Test
    fun `a module whose manifest declares no split is the base, whatever its name`() {
        val base = module("AndroidManifest.xml" to manifest(split = null))

        assertTrue(isBase("com.example.config.tl.apk", base))
    }

    @Test
    fun `a module whose manifest declares a split is not the base`() {
        val split = module("AndroidManifest.xml" to manifest(split = "config.tl"))

        assertFalse(isBase("split_config.tl.apk", split))
    }

    @Test
    fun `the manifest is found past the entries written before it`() {
        val base = module(
            "classes.dex" to byteArrayOf(0),
            "AndroidManifest.xml" to manifest(split = null)
        )

        assertTrue(isBase("com.example.app.apk", base))
    }

    @Test
    fun `a module without a readable manifest is not taken for the base`() {
        assertFalse(isBase("broken.apk", module("classes.dex" to byteArrayOf(0))))
        assertFalse(isBase("garbage.apk", byteArrayOf(1, 2, 3)))
    }
}
