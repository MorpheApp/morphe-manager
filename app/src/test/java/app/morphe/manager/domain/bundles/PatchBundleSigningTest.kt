/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.bundles

import app.morphe.patcher.patch.PatchBundleSignature
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

/**
 * A source trusts the key of the first signed bundle it installs, and refuses bundles signed by
 * any other key, or not signed at all, until the user trusts the new one.
 */
class PatchBundleSigningTest {
    private val directory: File = Files.createTempDirectory("signing").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    // A new instance each time, the way the repository reloads its sources
    private fun source() = LocalPatchBundle("bundle", 1, null, null, null, null, directory.resolve("source"), true)

    private fun bundle(content: Int, privateKey: String? = null) = directory.resolve("bundle-$content.mpp").apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(ByteArray(1024) { content.toByte() })
            zip.closeEntry()
        }
        privateKey?.let { PatchBundleSignature.sign(this, it) }
    }

    private fun install(bundle: File) = runBlocking {
        // Windows cannot rename over an existing file the way Android does, so the installed one is
        // set aside first and put back if the install fails
        val installed = source().patchesJarFile
        val kept = installed.resolveSibling("kept.jar")
        if (installed.exists()) installed.renameTo(kept)
        try {
            bundle.inputStream().use { source().replace(it) }
        } catch (e: Exception) {
            kept.renameTo(installed)
            throw e
        }
        kept.setWritable(true)
        kept.delete()
    }

    @Test
    fun `unsigned bundles are taken while no key is trusted`() {
        install(bundle(1))
        install(bundle(2))

        assertNull(source().signingKey)
    }

    @Test
    fun `first signed bundle pins its key`() {
        install(bundle(1))
        install(bundle(2, KEY))

        assertEquals(PUBLIC_KEY, source().signingKey)
        assertEquals("5647 5aa7 5463 474c", source().signingKeyFingerprint)
    }

    @Test
    fun `bundle of another key is refused and the installed one kept`() {
        install(bundle(1, KEY))
        val installed = source().patchesJarFile.readBytes()

        val refused = assertFailsWith<PatchBundleSignerChangedException> { install(bundle(2, OTHER_KEY)) }

        assertEquals(OTHER_PUBLIC_KEY, refused.signer)
        assertContentEquals(installed, source().patchesJarFile.readBytes())
    }

    @Test
    fun `unsigned bundle is refused once a key is trusted`() {
        install(bundle(1, KEY))

        val refused = assertFailsWith<PatchBundleSignerChangedException> { install(bundle(2)) }

        assertNull(refused.signer)
    }

    @Test
    fun `trusted new key is taken`() {
        install(bundle(1, KEY))
        source().trustSigningKey(OTHER_PUBLIC_KEY)

        install(bundle(2, OTHER_KEY))

        assertEquals(OTHER_PUBLIC_KEY, source().signingKey)
    }

    @Test
    fun `changed bundle is refused`() {
        val bundle = bundle(1, KEY).apply {
            val bytes = readBytes()
            bytes[100] = (bytes[100].toInt() xor 1).toByte()
            writeBytes(bytes)
        }

        assertFailsWith<SecurityException> { install(bundle) }
        assertFalse(source().patchesJarFile.exists())
    }

    private companion object {
        // Keys made of the bytes 0 to 31 and of 32 sevens
        const val KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        const val PUBLIC_KEY = "A6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg="
        const val OTHER_KEY = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc="
        const val OTHER_PUBLIC_KEY = "6kpsY+KcUgq+9VB7Ey7F+ZVHdq6+vnuSQh7qaRRG0iw="
    }
}
