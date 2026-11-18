/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.morphe.patcher.apk.ApkUtils
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Small signed synthetic APKs exercise the real verifier, not archive certificate metadata. */
class StockApkIntegrityTest {
    private fun fixture(name: String, directory: File): File = File(directory, name).also { file ->
        checkNotNull(javaClass.getResourceAsStream("/stock-apk-integrity/$name")).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun <T> withDirectory(block: (File) -> T): T {
        val directory = Files.createTempDirectory("stock-integrity-test-").toFile()
        return try { block(directory) } finally { directory.deleteRecursively() }
    }

    private fun changeSignedContents(file: File) {
        val time = file.lastModified()
        val bytes = file.readBytes()
        val marker = "morphe-stock-integrity-payload".toByteArray()
        val index = (0..bytes.size - marker.size).first { offset ->
            marker.indices.all { bytes[offset + it] == marker[it] }
        }
        bytes[index] = (bytes[index].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        check(file.setLastModified(time))
    }

    private fun bundle(directory: File, base: File, split: File): File = File(directory, "original.apkm").also {
        ZipOutputStream(it.outputStream()).use { output ->
            listOf("base.apk" to base, "split_config.en.apk" to split).forEach { (name, apk) ->
                output.putNextEntry(ZipEntry(name))
                apk.inputStream().use { input -> input.copyTo(output) }
                output.closeEntry()
            }
        }
    }

    @Test
    fun `original APK verifies on supported device signature schemes`() = withDirectory { directory ->
        val apk = fixture("stock.apk", directory)
        listOf(26, 28, 37).forEach { sdk ->
            val snapshot = assertNotNull(verifiedStockSnapshot(apk, sdk))
            assertTrue(snapshot.signers.isNotEmpty())
            assertEquals(stockApkSha256(apk), snapshot.sha256)
        }
    }

    @Test
    fun `a rotated APK matches its current device signer rather than every historical certificate`() = withDirectory { directory ->
        val apk = fixture("rotated.apk", directory)
        val certificates = checkNotNull(javaClass.getResourceAsStream("/stock-apk-integrity/rotation-certificates.json"))
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        val old = certificates.getValue("old").jsonPrimitive.content
        val current = certificates.getValue("new").jsonPrimitive.content
        val history = ApkUtils.verifiedSigningCertificates(apk, 37).orEmpty().mapTo(mutableSetOf()) {
            MessageDigest.getInstance("SHA-256").digest(it.encoded).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
        assertEquals(setOf(old, current), history)
        assertEquals(setOf(old), verifiedStockApkSigners(apk, 32))
        assertEquals(setOf(current), verifiedStockApkSigners(apk, 37))

        var restores = 0
        val verified = runBlocking {
            ensureVerifiedInstalledStock(
                canRestore = true,
                capture = { verifiedStockSnapshot(apk, 37)?.takeIf { it.signers == setOf(current) } },
                restore = { restores++ }
            )
        }
        assertEquals(0, restores)
        assertEquals(setOf(current), verified.signers)
    }

    @Test
    fun `an APK inside resources is not treated as an original split module`() = withDirectory { directory ->
        val apk = fixture("nested-resource.apk", directory)
        assertTrue(isVerifiedStockArchive(apk, 37, File(directory, "verification")))
    }

    @Test
    fun `changed signed contents are rejected even with identical size and timestamp`() = withDirectory { directory ->
        val apk = fixture("stock.apk", directory)
        val size = apk.length()
        val time = apk.lastModified()
        assertNotNull(verifiedStockSnapshot(apk, 37))
        changeSignedContents(apk)
        assertEquals(size, apk.length())
        assertEquals(time, apk.lastModified())
        assertTrue(verifiedStockApkSigners(apk, 37).isEmpty())
        assertNull(verifiedStockSnapshot(apk, 37))
    }

    @Test
    fun `all original split modules must verify and have the same signer`() = withDirectory { directory ->
        val base = fixture("stock.apk", directory)
        val split = fixture("split.apk", directory)
        val temporary = File(directory, "verification")
        assertTrue(isVerifiedStockArchive(bundle(directory, base, split), 37, temporary))
        assertTrue(temporary.listFiles().orEmpty().isEmpty())

        changeSignedContents(split)
        assertFalse(isVerifiedStockArchive(bundle(directory, base, split), 37, temporary))
        assertTrue(temporary.listFiles().orEmpty().isEmpty())

        val other = fixture("other-signer-split.apk", directory)
        assertTrue(verifiedStockApkSigners(other, 37).isNotEmpty())
        assertFalse(isVerifiedStockArchive(bundle(directory, base, other), 37, temporary))
        assertTrue(temporary.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a signed split for another version or package is not a restorable bundle`() = withDirectory { directory ->
        val base = fixture("stock.apk", directory)
        val temporary = File(directory, "verification")
        listOf("other-version-split.apk", "other-package-split.apk").forEach { name ->
            val split = fixture(name, directory)
            assertTrue(verifiedStockApkSigners(split, 37).isNotEmpty())
            assertFalse(isVerifiedStockArchive(bundle(directory, base, split), 37, temporary))
        }
        assertTrue(temporary.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `hex version codes follow the device major version semantics`() = withDirectory { directory ->
        val apk = fixture("hex-resource-version.apk", directory)
        assertEquals(42L, assertNotNull(verifiedStockSnapshot(apk, 26)).identity.versionCode)
        assertEquals(42L, assertNotNull(verifiedStockSnapshot(apk, 27)).identity.versionCode)
        assertEquals((1L shl 32) or 42L, assertNotNull(verifiedStockSnapshot(apk, 37)).identity.versionCode)
    }

    @Test
    fun `a resource version name on the base does not invalidate a literal split version name`() = withDirectory { directory ->
        val base = fixture("hex-resource-version.apk", directory)
        val split = fixture("hex-version-split.apk", directory)
        assertNull(assertNotNull(verifiedStockSnapshot(base, 37)).identity.versionName)
        assertEquals("compatibility 1.0", assertNotNull(verifiedStockSnapshot(split, 37)).identity.versionName)
        assertTrue(isVerifiedStockArchive(bundle(directory, base, split), 37, File(directory, "verification")))
    }

    @Test
    fun `installed split names stay paired with their source paths`() {
        val base = "/data/app/app/base.apk"
        assertEquals(mapOf(base to null), installedStockSourceSplits(base, emptyList(), emptyList()))
        assertEquals(
            linkedMapOf(base to null, "/data/app/app/en.apk" to "config.en", "/data/app/app/de.apk" to "config.de"),
            installedStockSourceSplits(base, listOf("config.en", "config.de"), listOf("/data/app/app/en.apk", "/data/app/app/de.apk"))
        )
    }

    @Test
    fun `inconsistent installed split metadata cannot collapse the captured backup set`() {
        val base = "/data/app/app/base.apk"
        val cases = listOf(
            emptyList<String>() to listOf("/data/app/app/en.apk"),
            listOf("config.en", "config.en") to listOf("/data/app/app/en.apk", "/data/app/app/de.apk"),
            listOf("config.en", "config.de") to listOf("/data/app/app/en.apk", "/data/app/app/en.apk"),
            listOf("config.en") to listOf(base),
            listOf("config.en", "config.de") to listOf("/data/app/one/split.apk", "/data/app/two/split.apk"),
            listOf("") to listOf("/data/app/app/en.apk"),
            listOf("config.en") to listOf("")
        )
        cases.forEach { (names, paths) -> assertNull(installedStockSourceSplits(base, names, paths)) }
        assertNull(installedStockSourceSplits("", emptyList(), emptyList()))
    }

    @Test
    fun `a validly signed stale split cannot substitute for another recorded split`() = withDirectory { directory ->
        val base = assertNotNull(verifiedStockSnapshot(fixture("stock.apk", directory), 37)).identity
        val split = assertNotNull(verifiedStockSnapshot(fixture("split.apk", directory), 37)).identity
        val splitName = assertNotNull(split.splitName)
        val sources = assertNotNull(installedStockSourceSplits(
            "/data/app/app/base.apk", listOf(splitName, "$splitName.other"),
            listOf("/data/app/app/first.apk", "/data/app/app/second.apk")
        ))
        assertTrue(matchesInstalledStockIdentity(
            split, base.packageName, base.versionCode, assertNotNull(base.versionName), sources.getValue("/data/app/app/first.apk")
        ))
        // A root redirect exposes the first signed split through the second path too.
        assertFalse(matchesInstalledStockIdentity(
            split, base.packageName, base.versionCode, assertNotNull(base.versionName), sources.getValue("/data/app/app/second.apk")
        ))
    }

    @Test
    fun `metadata matching but invalid installed contents are repaired from the genuine original`() = withDirectory { directory ->
        val original = fixture("stock.apk", directory)
        val installed = original.copyTo(File(directory, "installed.apk"))
        changeSignedContents(installed)
        val appData = File(directory, "app-data").apply { writeText("retained login") }
        var repairs = 0

        val verified = runBlocking {
            ensureVerifiedInstalledStock(
                canRestore = true,
                capture = { verifiedStockSnapshot(installed, 37) },
                restore = {
                    repairs++
                    original.copyTo(installed, overwrite = true)
                }
            )
        }
        assertEquals(1, repairs)
        assertEquals(stockApkSha256(original), verified.sha256)
        assertEquals("retained login", appData.readText())
    }

    @Test
    fun `healthy installed stock does not need a replacement install`() = withDirectory { directory ->
        val installed = fixture("stock.apk", directory)
        var repairs = 0
        val verified = runBlocking {
            ensureVerifiedInstalledStock(
                canRestore = true,
                capture = { verifiedStockSnapshot(installed, 37) },
                restore = { repairs++ }
            )
        }
        assertEquals(0, repairs)
        assertEquals(stockApkSha256(installed), verified.sha256)
    }

    @Test
    fun `a lower code recovery variant does not downgrade healthy signed stock of the same release`() = withDirectory { directory ->
        val installedFile = fixture("stock-variant-high.apk", directory)
        val installed = assertNotNull(verifiedStockSnapshot(installedFile, 37))
        val original = assertNotNull(verifiedStockSnapshot(fixture("stock-variant-low.apk", directory), 37))
        assertEquals(installed.signers, original.signers)
        assertEquals(installed.identity.versionName, original.identity.versionName)
        assertNotEquals(installed.identity.versionCode, original.identity.versionCode)
        var restores = 0

        val captured = runBlocking {
            ensureVerifiedInstalledStock(
                canRestore = true,
                capture = {
                    // This is the eligibility gate used by RootInstaller's real capture lambda.
                    if (!canCaptureInstalledStockRelease(installed.identity, original.identity)) null
                    else verifiedStockSnapshot(installedFile, 37)?.takeIf {
                        it.signers == installed.signers && it.identity.versionCode == installed.identity.versionCode
                    }
                },
                restore = { restores++; error("Healthy same-release stock must not be replaced") }
            )
        }
        assertEquals(0, restores)
        assertEquals(installed.identity.versionCode, captured.identity.versionCode)
        assertFalse(canCaptureInstalledStockRelease(null, original.identity))
        assertFalse(canCaptureInstalledStockRelease(installed.identity, original.identity.copy(packageName = "another.package")))
        assertFalse(canCaptureInstalledStockRelease(installed.identity, original.identity.copy(versionName = "another release")))
    }

    @Test
    fun `a stale differently signed view that survives repair is never promoted`() = withDirectory { directory ->
        val original = fixture("stock.apk", directory)
        val staleView = fixture("other-signer.apk", directory)
        val expected = verifiedStockApkSigners(original, 37)
        val previousModule = File(directory, "previous-module.apk").apply { writeText("unchanged module") }
        var repairs = 0
        assertFailsWith<StockApkIntegrityException> {
            runBlocking {
                val copy = ensureVerifiedInstalledStock(
                    canRestore = true,
                    capture = { verifiedStockSnapshot(staleView, 37)?.takeIf { it.signers == expected } },
                    restore = { repairs++ }
                )
                previousModule.writeText(copy.sha256)
            }
        }
        assertEquals(1, repairs)
        assertEquals("unchanged module", previousModule.readText())
    }

    @Test
    fun `invalid installed contents without a genuine original leave the previous module alone`() = withDirectory { directory ->
        val installed = fixture("stock.apk", directory)
        changeSignedContents(installed)
        val previousModule = File(directory, "previous-module.apk").apply { writeText("unchanged module") }
        assertFailsWith<StockApkIntegrityException> {
            runBlocking {
                val copy = ensureVerifiedInstalledStock(
                    canRestore = false,
                    capture = { verifiedStockSnapshot(installed, 37) },
                    restore = { error("No restore may be attempted") }
                )
                previousModule.writeText(copy.sha256)
            }
        }
        assertEquals("unchanged module", previousModule.readText())
    }

    @Test
    fun `a changed stage fails before any module file is replaced`() = withDirectory { directory ->
        val original = fixture("stock.apk", directory)
        val other = fixture("other-signer.apk", directory)
        val stockTarget = File(directory, "package-stock.apk").apply { writeText("previous stock") }
        val scriptTarget = File(directory, "service.sh").apply { writeText("previous service") }
        val stockStage = original.copyTo(File(directory, "stock.tmp"))
        val scriptStage = File(directory, "service.tmp").apply { writeText("new service") }
        val verified = assertNotNull(verifiedStockSnapshot(stockStage, 37))
        // A persistence callback or another writer changed the stage after full verification.
        other.copyTo(stockStage, overwrite = true)
        val command = verifiedStockCommitCommand(
            linkedMapOf(scriptTarget.path to scriptStage.path, stockTarget.path to stockStage.path),
            mapOf(stockTarget.path to verified.sha256)
        )
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
        assertTrue(process.waitFor() != 0)
        assertEquals("previous stock", stockTarget.readText())
        assertEquals("previous service", scriptTarget.readText())
    }

    @Test
    fun `verified stock base splits and marker are committed together`() = withDirectory { directory ->
        val baseStage = fixture("stock.apk", directory)
        val splitStage = fixture("split.apk", directory)
        val baseTarget = File(directory, "package-stock.apk")
        val splitTarget = File(directory, "package-stock-split_config.en.apk")
        val markerStage = File(directory, "marker.tmp")
        val markerTarget = File(directory, "stock-apk-sha256.txt")
        val hashes = linkedMapOf(
            baseTarget.path to assertNotNull(verifiedStockSnapshot(baseStage, 37)).sha256,
            splitTarget.path to assertNotNull(verifiedStockSnapshot(splitStage, 37)).sha256
        )
        val manifest = stockApkHashManifest(hashes.mapKeys { File(it.key).name })
        markerStage.writeText(manifest)
        val command = verifiedStockCommitCommand(
            linkedMapOf(baseTarget.path to baseStage.path, splitTarget.path to splitStage.path, markerTarget.path to markerStage.path),
            hashes
        )
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor())
        assertEquals(hashes.getValue(baseTarget.path), stockApkSha256(baseTarget))
        assertEquals(hashes.getValue(splitTarget.path), stockApkSha256(splitTarget))
        assertEquals(manifest, markerTarget.readText())
    }

    @Test
    fun `untrusted manifest file names and malformed digests are refused`() {
        val digest = "a".repeat(64)
        assertEquals("$digest  com.example-stock.apk\n", stockApkHashManifest(mapOf("com.example-stock.apk" to digest)))
        listOf("../stock.apk", "stock.apk\nother.apk", "stock.apk'", "/stock.apk").forEach { name ->
            assertFailsWith<IllegalArgumentException> { stockApkHashManifest(mapOf(name to digest)) }
        }
        assertFailsWith<IllegalArgumentException> { stockApkHashManifest(mapOf("stock.apk" to "unverified")) }
    }
}
