/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyStore

enum class KeystoreInputFormat(
    val displayName: String,
    val extensions: List<String>,
    val jcaType: String
) {
    KEYSTORE(".keystore (BKS)", listOf("keystore"), "BKS"),
    BKS(".bks (BKS)", listOf("bks"), "BKS"),
    PKCS12(".p12 / .pfx (PKCS12)", listOf("p12", "pfx"), "PKCS12"),
    // JKS is available in the manual dialog only - Android has no JKS security provider,
    // so it cannot be used in the automatic import loop
    JKS(".jks (JKS)", listOf("jks"), "JKS");

    companion object {
        fun fromExtension(ext: String): KeystoreInputFormat? =
            entries.firstOrNull { ext.lowercase() in it.extensions }

        /**
         * Sniff the keystore format from the first 4 bytes of the file.
         * Returns null if the header is not recognized.
         *
         * PKCS12 - ASN.1 SEQUENCE: 0x30 + any BER length byte (0x80–0x84)
         * JKS    - magic: 0xFEEDFEED
         * BKS    - 4-byte big-endian version int, value 1 or 2
         */
        fun detectFromBytes(header: ByteArray): KeystoreInputFormat? {
            if (header.size < 4) return null
            return when {
                header[0] == 0x30.toByte() && header[1] in byteArrayOf(
                    0x80.toByte(), 0x81.toByte(), 0x82.toByte(), 0x83.toByte(), 0x84.toByte()
                ) -> PKCS12
                header[0] == 0xFE.toByte() && header[1] == 0xED.toByte() &&
                        header[2] == 0xFE.toByte() && header[3] == 0xED.toByte() -> JKS
                header[0] == 0x00.toByte() && header[1] == 0x00.toByte() &&
                        header[2] == 0x00.toByte() &&
                        (header[3] == 0x01.toByte() || header[3] == 0x02.toByte()) -> BKS
                else -> null
            }
        }
    }
}

sealed interface KeystoreConversionResult {
    /** Conversion succeeded - [data] is a BKS keystore ready for [app.morphe.manager.domain.manager.KeystoreManager.import]. */
    data class Success(val data: List<Byte>) : KeystoreConversionResult
    /** Wrong password/alias, corrupt file, or unsupported format variant. */
    data class Error(val cause: Exception) : KeystoreConversionResult
}

object KeystoreConversionUtils {

    /**
     * Loads a keystore of [format] from [inputStream] and re-encodes all entries into a
     * new BKS keystore, returning the raw bytes. The stream is read but not closed.
     *
     * When [alias] is blank all entries are transferred. Otherwise, the matching entry is
     * looked up case-insensitively, falling back to transferring everything if not found.
     *
     * [password] is used for both the keystore and the individual key entries.
     */
    fun convert(
        inputStream: InputStream,
        format: KeystoreInputFormat,
        alias: String,
        password: String
    ): KeystoreConversionResult = runCatching {
        val pass = password.toCharArray()

        val source = KeyStore.getInstance(format.jcaType).apply { load(inputStream, pass) }

        val entriesToMigrate = if (alias.isBlank()) {
            source.aliases().toList()
        } else {
            source.aliases().toList()
                .filter { it.equals(alias, ignoreCase = true) }
                .ifEmpty { source.aliases().toList() }
        }

        check(entriesToMigrate.isNotEmpty()) { "No entries found in keystore" }

        val target = KeyStore.getInstance("BKS").apply { load(null, pass) }

        for (entryAlias in entriesToMigrate) {
            val protection = KeyStore.PasswordProtection(pass)
            when {
                source.isKeyEntry(entryAlias) -> {
                    val entry = source.getEntry(entryAlias, protection) ?: continue
                    target.setEntry(entryAlias, entry, protection)
                }
                source.isCertificateEntry(entryAlias) ->
                    target.setCertificateEntry(entryAlias, source.getCertificate(entryAlias))
            }
        }

        val out = ByteArrayOutputStream()
        target.store(out, pass)
        KeystoreConversionResult.Success(out.toByteArray().toList())
    }.getOrElse { e ->
        KeystoreConversionResult.Error(e as? Exception ?: RuntimeException(e))
    }
}
