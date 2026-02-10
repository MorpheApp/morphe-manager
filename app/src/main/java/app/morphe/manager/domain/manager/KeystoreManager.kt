package app.morphe.manager.domain.manager

import android.app.Application
import android.content.Context
import app.morphe.library.ApkSigner
import app.morphe.library.ApkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.file.Files
import java.security.UnrecoverableKeyException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class KeystoreManager(app: Application, private val prefs: PreferencesManager) {
    companion object Constants {
        /**
         * Default alias and password for the keystore.
         */
        const val DEFAULT = "Morphe"
    }

    private val keystorePath =
        app.getDir("signing", Context.MODE_PRIVATE).resolve("morphe.keystore")

    private suspend fun updatePrefs(alias: String, pass: String) = prefs.edit {
        prefs.keystoreAlias.value = alias
        prefs.keystorePass.value = pass
    }

    private suspend fun signingDetails(path: File = keystorePath) = ApkUtils.KeyStoreDetails(
        keyStore = path,
        keyStorePassword = null,
        alias = prefs.keystoreAlias.get(),
        password = prefs.keystorePass.get()
    )

    suspend fun sign(input: File, output: File) = withContext(Dispatchers.Default) {
        val sanitized = sanitizeZipIfNeeded(input)
        ApkUtils.signApk(sanitized, output, prefs.keystoreAlias.get(), signingDetails())
        if (sanitized != input) sanitized.delete()
    }

    /**
     * Some APKs (often from third-party downloads) contain malformed ZIP headers that trigger
     * ApkSigner errors like "Data Descriptor presence mismatch". Repackage the archive to fix
     * header inconsistencies before signing.
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

    suspend fun import(alias: String, pass: String, keystore: InputStream): Boolean {
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

        updatePrefs(alias, pass)
        return true
    }

    fun hasKeystore() = keystorePath.exists()

    suspend fun export(target: OutputStream) {
        withContext(Dispatchers.IO) {
            Files.copy(keystorePath.toPath(), target)
        }
    }
}
