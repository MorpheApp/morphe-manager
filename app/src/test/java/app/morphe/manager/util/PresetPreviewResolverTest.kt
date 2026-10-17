package app.morphe.manager.util

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PresetPreviewResolverTest {

    private fun bundleDirWith(vararg names: String): File {
        val dir = Files.createTempDirectory("bundle").toFile()
        names.forEach { name ->
            dir.resolve(name).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        }
        return dir
    }

    private fun bundleZipWith(vararg names: String): File {
        val dir = Files.createTempDirectory("bundlezip").toFile()
        val zip = dir.resolve("patches.mpp")
        ZipOutputStream(zip.outputStream()).use { out ->
            names.forEach { name ->
                out.putNextEntry(ZipEntry(name))
                out.write(byteArrayOf(4, 5, 6))
                out.closeEntry()
            }
        }
        return zip
    }

    @Test
    fun `sibling file wins without touching the archive`() {
        val dir = bundleDirWith("previews/com.google.android.youtube/appIcon/mmt.png")
        val found = PresetPreviewResolver.resolve(dir, "com.google.android.youtube", "appIcon", "mmt", Files.createTempDirectory("c").toFile())
        assertEquals(dir.resolve("previews/com.google.android.youtube/appIcon/mmt.png").absolutePath, found?.absolutePath)
    }

    @Test
    fun `archive entry extracts to cache`() {
        val zip = bundleZipWith("previews/com.google.android.youtube/appIcon/mmt.webp")
        val cache = Files.createTempDirectory("c").toFile()
        val found = PresetPreviewResolver.resolve(zip, "com.google.android.youtube", "appIcon", "mmt", cache)
        assertEquals(byteArrayOf(4, 5, 6).toList(), found?.readBytes()?.toList())
    }

    @Test
    fun `video entries resolve like images`() {
        val zip = bundleZipWith("previews/com.google.android.youtube/appIcon/mmt.mp4")
        val cache = Files.createTempDirectory("c").toFile()
        val found = PresetPreviewResolver.resolve(zip, "com.google.android.youtube", "appIcon", "mmt", cache)
        assertEquals(true, found?.let { PresetPreviewResolver.isVideoPreview(it) })
    }

    @Test
    fun `missing preview resolves to null`() {
        val dir = bundleDirWith("previews/com.google.android.youtube/appIcon/other.png")
        assertNull(PresetPreviewResolver.resolve(dir, "com.google.android.youtube", "appIcon", "nope", Files.createTempDirectory("c").toFile()))
    }

    @Test
    fun `path separators in values cannot escape`() {
        val dir = bundleDirWith("previews/com.google.android.youtube/appIcon/ok.png")
        assertNull(PresetPreviewResolver.resolve(dir, "com.google.android.youtube", "appIcon", "../ok", Files.createTempDirectory("c").toFile()))
        assertNull(PresetPreviewResolver.resolve(dir, "com.google.android.youtube", "appIcon", "", Files.createTempDirectory("c").toFile()))
    }
}
