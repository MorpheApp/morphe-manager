/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/manager/KeystoreManager.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.manager

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import app.morphe.manager.domain.apk.apkFileStampOrNull
import app.morphe.manager.util.JksKeyStoreParser
import app.morphe.manager.util.sha256Fingerprint
import app.morphe.patcher.apk.ApkSigner
import app.morphe.patcher.apk.ApkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * What tells one signing key from another at a glance.
 *
 * @param sha256 Fingerprint of the key's certificate in lowercase hex, the same form the package
 *        manager reports for an installed package.
 * @param createdAt When the certificate became valid, which for a key Morphe generated is when
 *        it was made. Null when the certificate does not say.
 */
data class SigningKeyInfo(
    val alias: String,
    val sha256: String,
    val createdAt: Long?
)

class KeystoreManager(app: Application, private val prefs: PreferencesManager) {
    companion object Constants {
        /** Default alias and password for the keystore. */
        const val DEFAULT = "Morphe"

        private const val TAG = "Morphe Keystore"
    }

    private val keystorePath =
        app.getDir("signing", Context.MODE_PRIVATE).resolve("morphe.keystore")

    // Reading the keystore goes through BouncyCastle, so the fingerprints are kept until the
    // file itself changes rather than re-read for every app a home refresh inspects
    @Volatile
    private var cachedCertificateHashes: Pair<String, Set<String>>? = null

    // The signer is kept for the same reason, until the keystore or its settings change
    private var cachedSigner: Pair<List<Any?>, ApkSigner.Signer>? = null
    private val signerLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun signingDetails() = ApkUtils.KeyStoreDetails(
        keyStore = keystorePath,
        keyStorePassword = prefs.keystorePassword.get().ifEmpty { null },
        alias = prefs.keystoreAlias.get(),
        password = prefs.keystorePass.get()
    )

    /**
     * Signs [apk] in place for this device, which never needs a v1 signature. Only ever handed
     * what the patcher just wrote, whose headers it already rewrites where the signer would reject them.
     */
    suspend fun sign(apk: File) = withContext(Dispatchers.Default) {
        signer()?.signApk(apk, apk, Build.VERSION.SDK_INT)
            // Creates the keystore on the first patch
            ?: ApkUtils.signApk(apk, apk, prefs.keystoreAlias.get(), signingDetails(), Build.VERSION.SDK_INT)
    }

    /** Starts loading the signing key, which takes BouncyCastle a while, so signing need not wait for it. */
    fun preloadSigner() {
        scope.launch { runCatching { signer() } }
    }

    private suspend fun signer(): ApkSigner.Signer? = signerLock.withLock {
        withContext(Dispatchers.IO) {
            val stamp = keystorePath.apkFileStampOrNull() ?: return@withContext null
            val details = signingDetails()
            val key = listOf(stamp.cacheKey, details.alias, details.password, details.keyStorePassword)
            cachedSigner?.takeIf { it.first == key }?.second
                ?: ApkSigner.newApkSigner(
                    details.alias,
                    ApkSigner.readPrivateKeyCertificatePair(readKeyStore(), details.alias, details.password)
                ).also { cachedSigner = key to it }
        }
    }

    /**
     * Makes the key [alias] in the keystore [source] the one patching signs with, keeping the
     * keystore as it is unless Android cannot read it. Returns false if [pass] does not open it.
     */
    suspend fun import(alias: String, pass: String, source: File): Boolean {
        val stagingPath = keystorePath.resolveSibling("${keystorePath.name}.tmp")
        val storedAlias = withContext(Dispatchers.IO) {
            try {
                // Android has no JKS support, so only that format is converted
                val data = source.readBytes()
                val isJks = ApkSigner.KeyStoreType.of(data) == ApkSigner.KeyStoreType.JKS
                stagingPath.writeBytes(if (isJks) JksKeyStoreParser.toPkcs12(data, pass) else data)

                // The store opens with the key's password, as the export writes it and keytool
                // does for PKCS12, while a BKS keystore is read without one
                val details = ApkUtils.KeyStoreDetails(stagingPath, alias = alias, password = pass)
                val keyStore = ApkUtils.readKeyStore(details)

                // BKS looks aliases up by exact case, while keytool stores a JKS alias in lowercase, so
                // "MyKey" typed for a key saved as "mykey" is matched to the alias the file holds
                val resolved = if (keyStore.containsAlias(alias)) alias
                else keyStore.aliases().asSequence().firstOrNull { it.equals(alias, ignoreCase = true) } ?: alias

                ApkSigner.readPrivateKeyCertificatePair(keyStore, resolved, pass)
                Files.move(stagingPath.toPath(), keystorePath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                resolved
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: SecurityException) {
                // A JKS key the password does not decrypt
                null
            } catch (_: IOException) {
                // BouncyCastle reports a wrong PKCS12 password as a failed integrity check
                null
            } finally {
                stagingPath.delete()
            }
        } ?: return false

        prefs.edit {
            prefs.keystoreAlias.value = storedAlias
            prefs.keystorePass.value = pass
            prefs.keystorePassword.value = ""
        }
        return true
    }

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
            val keyStore = readKeyStore()

            // Every alias is read, because an imported keystore can hold the key an earlier
            // patched build was signed with next to the one patching uses now
            keyStore.aliases().asSequence().mapNotNullTo(mutableSetOf()) { alias ->
                keyStore.getCertificate(alias)?.sha256()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read signing certificates", e)
            emptySet()
        }

        cachedCertificateHashes = stamp.cacheKey to hashes
        hashes
    }

    /** The key patching signs with. Null until the first patch creates one, and when it cannot be read. */
    suspend fun signingKeyInfo(): SigningKeyInfo? = withContext(Dispatchers.IO) {
        if (!keystorePath.exists()) return@withContext null
        try {
            val alias = prefs.keystoreAlias.get()
            val certificate = readKeyStore().getCertificate(alias) ?: return@withContext null
            SigningKeyInfo(
                alias = alias,
                sha256 = certificate.sha256(),
                createdAt = (certificate as? X509Certificate)?.notBefore?.time
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read the signing key", e)
            null
        }
    }

    private suspend fun readKeyStore(): KeyStore = ApkUtils.readKeyStore(signingDetails())

    private fun Certificate.sha256(): String = encoded.sha256Fingerprint()

    /** Writes the signing key to [target] as a PKCS12 keystore that opens with the key's password. */
    suspend fun export(target: OutputStream) {
        val details = signingDetails()
        withContext(Dispatchers.IO) { ApkUtils.exportKeyStore(details, target) }
    }
}
