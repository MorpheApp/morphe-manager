/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.util

import java.io.File
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
}
