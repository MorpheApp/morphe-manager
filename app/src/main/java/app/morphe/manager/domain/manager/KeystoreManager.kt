package app.morphe.manager.domain.manager

import android.app.Application
import android.content.Context
import android.util.Log
import app.morphe.manager.domain.apk.apkFileStampOrNull
import app.morphe.patcher.apk.ApkSigner
import app.morphe.patcher.apk.ApkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.security.UnrecoverableKeyException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class KeystoreManager(app: Application, private val prefs: PreferencesManager) {
    companion object Constants {
        /** Default alias and password for the keystore. */
        const val DEFAULT = "Morphe"

        private const val TAG = "Morphe Keystore"

        // apksig reaches the manager only through the patcher, so the format failures it raises
        // for a malformed archive are recognised by name rather than by type
        private val ARCHIVE_FORMAT_EXCEPTIONS = setOf("ApkFormatException", "ZipFormatException")
    }

    private val keystorePath =
        app.getDir("signing", Context.MODE_PRIVATE).resolve("morphe.keystore")

    // Reading the keystore goes through BouncyCastle, so the fingerprints are kept until the
    // file itself changes rather than re-read for every app a home refresh inspects
    @Volatile
    private var cachedCertificateHashes: Pair<String, Set<String>>? = null

    private suspend fun updatePrefs(alias: String, pass: String, keystorePw: String) = prefs.edit {
        prefs.keystoreAlias.value = alias
        prefs.keystorePass.value = pass
        prefs.keystorePassword.value = keystorePw
    }

    private suspend fun signingDetails(path: File = keystorePath) = ApkUtils.KeyStoreDetails(
        keyStore = path,
        keyStorePassword = prefs.keystorePassword.get().ifEmpty { null },
        alias = prefs.keystoreAlias.get(),
        password = prefs.keystorePass.get()
    )

    /**
     * Signs [input] into [output].
     *
     * Repackaging the archive first fixes the malformed headers some third-party APKs carry, but it
     * means inflating and re-deflating every entry of the archive, which is wasted whenever the
     * archive was already well-formed - as it is for anything the patcher itself just wrote. So sign
     * directly and fall back to [sanitizeZipIfNeeded] only if the signer rejects the archive itself.
     */
    suspend fun sign(input: File, output: File) = withContext(Dispatchers.Default) {
        val alias = prefs.keystoreAlias.get()
        try {
            ApkUtils.signApk(input, output, alias, signingDetails())
        } catch (e: Exception) {
            if (!e.isMalformedArchive()) throw e

            Log.w(TAG, "Signing failed, retrying with a repackaged archive", e)

            // Repackaging fell through, so a second attempt would hand the signer the same bytes
            val sanitized = sanitizeZipIfNeeded(input).takeIf { it != input } ?: throw e

            try {
                ApkUtils.signApk(sanitized, output, alias, signingDetails())
            } catch (retry: Exception) {
                // The rejection that sent us down this path is what names the archive as the
                // problem, so it travels with the failure the user ends up seeing
                throw retry.apply { addSuppressed(e) }
            } finally {
                sanitized.delete()
            }
        }
    }

    /**
     * Whether repackaging stands a chance, meaning the signer rejected the archive rather than the
     * keystore or the write.
     */
    private fun Throwable.isMalformedArchive() = generateSequence(this, Throwable::cause).any {
        it is ZipException || it.javaClass.simpleName in ARCHIVE_FORMAT_EXCEPTIONS
    }

    /**
     * Some APKs (often from third-party downloads) contain malformed ZIP headers that trigger
     * ApkSigner errors like "Data Descriptor presence mismatch". Repackage the archive to fix
     * header inconsistencies. Called from [sign] only after a signing attempt has failed, since
     * repackaging is expensive and almost never needed.
     */
    private suspend fun sanitizeZipIfNeeded(input: File): File = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File.createTempFile("apk-sanitized-", ".apk", input.parentFile)
            ZipFile(input).use { zip ->
                ZipOutputStream(tempFile.outputStream()).use { zos ->
                    zip.entries().asSequence().forEach { entry ->
                        val cleanEntry = ZipEntry(entry.name).apply {
                            method = entry.method
                            time = entry.time
                            comment = entry.comment
                            size = entry.size
                            compressedSize = -1 // let ZipOutputStream compute
                            crc = entry.crc
                            extra = entry.extra
                        }
                        zos.putNextEntry(cleanEntry)
                        if (!entry.isDirectory) {
                            zip.getInputStream(entry).use { inputStream ->
                                BufferedInputStream(inputStream).copyTo(zos)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
            tempFile
        }.getOrElse { input }
    }

    suspend fun import(alias: String, pass: String, keystorePw: String = "", keystore: InputStream): Boolean {
        val keystoreData = withContext(Dispatchers.IO) { keystore.readBytes() }

        try {
            val ks = ApkSigner.readKeyStore(ByteArrayInputStream(keystoreData), null)

            ApkSigner.readPrivateKeyCertificatePair(ks, alias, pass)
        } catch (_: UnrecoverableKeyException) {
            return false
        } catch (_: IllegalArgumentException) {
            return false
        }

        withContext(Dispatchers.IO) {
            Files.write(keystorePath.toPath(), keystoreData)
        }

        updatePrefs(alias, pass, keystorePw)
        return true
    }

    fun hasKeystore() = keystorePath.exists()

    /**
     * SHA-256 fingerprints of every certificate the signing keystore holds, in the same form the
     * package manager reports for an installed package.
     *
     * Everything Morphe signs carries one of them, which is the only thing still identifying a
     * patched build once both the patched APK and the original it was built from are gone.
     */
    suspend fun signingCertificateHashes(): Set<String> = withContext(Dispatchers.IO) {
        val stamp = keystorePath.apkFileStampOrNull() ?: return@withContext emptySet()
        cachedCertificateHashes?.takeIf { it.first == stamp.cacheKey }?.let {
            return@withContext it.second
        }

        val hashes = try {
            val keyStorePassword = prefs.keystorePassword.get().ifEmpty { null }
            val keyStore = keystorePath.inputStream().use {
                ApkSigner.readKeyStore(it, keyStorePassword)
            }
            val digest = MessageDigest.getInstance("SHA-256")

            // Every alias is read, because an imported keystore can hold the key an earlier
            // patched build was signed with next to the one patching uses now
            keyStore.aliases().asSequence().mapNotNullTo(mutableSetOf()) { alias ->
                keyStore.getCertificate(alias)?.encoded?.let { encoded ->
                    digest.reset()
                    digest.digest(encoded).joinToString("") { byte -> "%02x".format(byte) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read signing certificates", e)
            emptySet()
        }

        cachedCertificateHashes = stamp.cacheKey to hashes
        hashes
    }

    suspend fun export(target: OutputStream) {
        withContext(Dispatchers.IO) {
            Files.copy(keystorePath.toPath(), target)
        }
    }
}
