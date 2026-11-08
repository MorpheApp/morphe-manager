/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.split

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a split archive has OBB files, which decides if the archive rather than the APK merged
 * from it is kept for repatching.
 */
class HasObbFilesTest {
    private val workspace = createTempDirectory("split-obb").toFile()

    @AfterTest
    fun cleanup() {
        workspace.deleteRecursively()
    }

    private var archives = 0

    private fun archive(vararg entries: String): File {
        val file = File(workspace, "bundle-${archives++}.xapk")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) zip.write(name.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `an OBB under Android obb is found`() {
        assertTrue(
            SplitApkPreparer.hasObbFiles(
                archive("base.apk", "Android/obb/com.example/main.1.com.example.obb")
            )
        )
    }

    @Test
    fun `an OBB outside Android obb is found`() {
        assertTrue(SplitApkPreparer.hasObbFiles(archive("base.apk", "game.OBB")))
    }

    @Test
    fun `a file under Android obb without the extension is found`() {
        assertTrue(SplitApkPreparer.hasObbFiles(archive("base.apk", "Android/obb/com.example/data.bin")))
    }

    @Test
    fun `an archive of only modules has none`() {
        assertFalse(
            SplitApkPreparer.hasObbFiles(
                archive("base.apk", "split_config.arm64_v8a.apk", "manifest.json", "Android/obb/")
            )
        )
    }
}
