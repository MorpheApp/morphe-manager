/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.split

import app.morphe.manager.patcher.DeviceConfig
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The split modules a device has no use for, decided from the device the app describes rather
 * than from Android, as the JVM that may run the patcher cannot ask the system.
 */
class UnneededModulesTest {
    private val workspace = createTempDirectory("unneeded-modules").toFile()

    @AfterTest
    fun cleanup() {
        workspace.deleteRecursively()
    }

    private fun device(
        abi: String = "arm64-v8a",
        locales: List<String> = listOf("en-US"),
        densityDpi: Int = 420
    ) = DeviceConfig(listOf(abi), locales, densityDpi)

    private fun baseModule(): ByteArray {
        val manifest = AndroidManifestBlock().apply {
            packageName = "com.example.app"
            refresh()
        }.bytes
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifest)
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    /** The modules of an archive [device] would leave out, each module named and none a base unless given. */
    private fun unneeded(
        device: DeviceConfig,
        vararg names: String,
        base: String? = null
    ): Set<String> {
        val file = File(workspace, "bundle.apks")
        ZipOutputStream(file.outputStream()).use { zip ->
            (names.toList() + listOfNotNull(base)).forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(if (name == base) baseModule() else name.toByteArray())
                zip.closeEntry()
            }
        }
        return ZipFile(file).use { zip ->
            SplitApkPreparer.unneededModules(zip, SplitApkPreparer.splitModuleEntries(zip).toList(), device)
        }
    }

    @Test
    fun `only the native libraries of the preferred ABI are kept`() {
        assertEquals(
            setOf("split_config.armeabi_v7a.apk", "split_config.x86_64.apk"),
            unneeded(
                device(abi = "arm64-v8a"),
                "base.apk", "split_config.arm64_v8a.apk", "split_config.armeabi_v7a.apk", "split_config.x86_64.apk"
            )
        )
    }

    @Test
    fun `the density bucket of the device is kept`() {
        // 420 dpi falls into xxhdpi
        assertEquals(
            setOf("split_config.xhdpi.apk", "split_config.xxxhdpi.apk"),
            unneeded(device(densityDpi = 420), "split_config.xhdpi.apk", "split_config.xxhdpi.apk", "split_config.xxxhdpi.apk")
        )
        assertEquals(
            setOf("split_config.xxhdpi.apk"),
            unneeded(device(densityDpi = 160), "split_config.mdpi.apk", "split_config.xxhdpi.apk")
        )
    }

    @Test
    fun `a missing density falls back to the nearest lower one before a higher one`() {
        assertEquals(
            setOf("split_config.xxxhdpi.apk"),
            unneeded(device(densityDpi = 420), "split_config.xhdpi.apk", "split_config.xxxhdpi.apk")
        )
        assertEquals(
            emptySet(),
            unneeded(device(densityDpi = 120), "split_config.xxxhdpi.apk")
        )
    }

    @Test
    fun `languages of the system locales are kept, the rest left out`() {
        assertEquals(
            setOf("split_config.de.apk"),
            unneeded(device(locales = listOf("uk-UA", "en-US")), "split_config.uk.apk", "split_config.en.apk", "split_config.de.apk")
        )
    }

    @Test
    fun `a locale with a region or script keeps the matching regional module`() {
        assertEquals(
            setOf("split_config.zh_rCN.apk"),
            unneeded(device(locales = listOf("zh-Hant-TW")), "split_config.zh.apk", "split_config.zh_rTW.apk", "split_config.zh_rCN.apk")
        )
    }

    @Test
    fun `a base module is kept whatever its name says`() {
        assertEquals(
            setOf("split_config.fr.apk"),
            unneeded(device(locales = listOf("en-US")), "split_config.fr.apk", base = "split_config.de.apk")
        )
    }

    @Test
    fun `modules that are no config split are kept`() {
        assertEquals(emptySet(), unneeded(device(), "base.apk", "feature_camera.apk"))
    }
}
