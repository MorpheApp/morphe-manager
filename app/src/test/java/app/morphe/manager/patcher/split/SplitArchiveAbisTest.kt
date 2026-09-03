/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.split

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ABIs a split archive is read to carry. They stand in for the lib/ directories of the APK
 * the modules are merged into, which is what patches declare their architecture against.
 */
class SplitArchiveAbisTest {
    private val workspace = createTempDirectory("split-abis").toFile()

    @AfterTest
    fun cleanup() {
        workspace.deleteRecursively()
    }

    private var archives = 0

    private fun archive(vararg modules: String): File =
        archiveOf(*modules.map { it to it.toByteArray() }.toTypedArray())

    private fun archiveOf(vararg modules: Pair<String, ByteArray>): File {
        val file = File(workspace, "bundle-${archives++}.apks")
        ZipOutputStream(file.outputStream()).use { zip ->
            modules.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return file
    }

    // A module holding nothing but the entry names, which is all the ABIs are read out of
    private fun module(vararg entries: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(entry.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun `config splits are read by the ABI they are named after`() {
        val file = archive("base.apk", "split_config.arm64_v8a.apk", "split_config.xxhdpi.apk")

        assertEquals(listOf("arm64-v8a"), SplitApkPreparer.splitArchiveAbis(file))
    }

    @Test
    fun `an ABI is not claimed by the shorter name it contains`() {
        val file = archive("base.apk", "split_config.armeabi_v7a.apk", "split_config.x86_64.apk")

        assertEquals(listOf("armeabi-v7a", "x86_64"), SplitApkPreparer.splitArchiveAbis(file))
    }

    @Test
    fun `an archive without ABI splits carries no architecture`() {
        val file = archive("base.apk", "split_config.en.apk")

        assertEquals(emptyList(), SplitApkPreparer.splitArchiveAbis(file))
    }

    @Test
    fun `nested APKs are not split modules`() {
        val file = archive("base.apk", "res/raw/config.arm64-v8a.apk")

        assertEquals(emptyList(), SplitApkPreparer.splitArchiveAbis(file))
    }

    @Test
    fun `a base module keeping the native libraries is read from its lib directory`() {
        val file = archiveOf(
            "base.apk" to module(
                "AndroidManifest.xml",
                "lib/arm64-v8a/libapp.so",
                "lib/armeabi-v7a/libapp.so",
                "classes.dex"
            ),
            "split_config.xxhdpi.apk" to module("resources.arsc")
        )

        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), SplitApkPreparer.splitArchiveAbis(file))
    }

    @Test
    fun `an ABI split names the architecture even when the base module has libraries too`() {
        val file = archiveOf(
            "base.apk" to module("lib/armeabi-v7a/libapp.so"),
            "split_config.arm64_v8a.apk" to module("lib/arm64-v8a/libapp.so")
        )

        assertEquals(listOf("arm64-v8a"), SplitApkPreparer.splitArchiveAbis(file))
    }

    // Which of these names is an architecture patches can be asked about is ApkArchitectureResolver's
    // call, so a directory nothing runs is reported here rather than dropped
    @Test
    fun `lib directories are reported as they are named`() {
        val file = archiveOf("base.apk" to module("lib/mips/libapp.so", "classes.dex"))

        assertEquals(listOf("mips"), SplitApkPreparer.splitArchiveAbis(file))
    }

    @Test
    fun `a base module without native libraries carries no architecture`() {
        val file = archiveOf("base.apk" to module("AndroidManifest.xml", "classes.dex"))

        assertEquals(emptyList(), SplitApkPreparer.splitArchiveAbis(file))
    }
}
