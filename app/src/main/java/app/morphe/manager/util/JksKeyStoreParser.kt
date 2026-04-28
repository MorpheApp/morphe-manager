/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import java.io.DataInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Minimal JKS keystore parser that extracts private key + certificate chain entries.
 *
 * Android's security provider does not include a JKS KeyStoreSpi, so we parse
 * the format manually. The key protection algorithm is Sun's proprietary XOR scheme:
 * keystream = SHA1(pwd || salt) || SHA1(pwd || SHA1(pwd || salt)) || ...
 * plaintext = ciphertext XOR keystream
 * integrity  = SHA1(pwd || plaintext) (last 20 bytes of the protected-key blob)
 *
 * Reference: sun.security.provider.KeyProtector (OpenJDK source)
 */
object JksKeyStoreParser {

    private const val JKS_MAGIC = 0xFEEDFEED.toInt()
    private const val TAG_PRIVATE_KEY = 1
    private const val TAG_TRUSTED_CERT = 2

    data class JksEntry(
        val alias: String,
        val privateKeyDer: List<Byte>,
        val certificatesDer: List<List<Byte>>
    )

    /**
     * Parses a JKS [inputStream] and returns all private key entries.
     * [password] is used both to decrypt each key entry and to verify keystore integrity.
     *
     * @throws IllegalArgumentException if the stream is not a valid JKS keystore.
     * @throws SecurityException if key decryption fails (wrong password).
     */
    fun parse(inputStream: InputStream, password: String): List<JksEntry> {
        val dis = DataInputStream(inputStream.buffered())

        val magic = dis.readInt()
        require(magic == JKS_MAGIC) { "Not a JKS keystore (magic=0x${magic.toString(16)})" }

        val version = dis.readInt()
        require(version == 1 || version == 2) { "Unsupported JKS version: $version" }

        val count = dis.readInt()
        val entries = mutableListOf<JksEntry>()

        repeat(count) {
            when (dis.readInt()) {
                TAG_PRIVATE_KEY -> entries.add(readPrivateKeyEntry(dis, password))
                TAG_TRUSTED_CERT -> skipTrustedCertEntry(dis)
                else -> error("Unknown JKS entry tag")
            }
        }

        return entries
    }

    private fun readPrivateKeyEntry(dis: DataInputStream, password: String): JksEntry {
        val alias = dis.readUTF()
        dis.readLong() // timestamp - unused

        val protectedKeyLen = dis.readInt()
        val protectedKey = ByteArray(protectedKeyLen).also { dis.readFully(it) }
        val privateKeyDer = decryptKey(protectedKey, password).toList()

        val chainCount = dis.readInt()
        val certs = (0 until chainCount).map {
            dis.readUTF()
            val certLen = dis.readInt()
            ByteArray(certLen).also { dis.readFully(it) }.toList()
        }

        return JksEntry(alias, privateKeyDer, certs)
    }

    private fun skipTrustedCertEntry(dis: DataInputStream) {
        dis.readUTF() // alias
        dis.readLong() // timestamp
        dis.readUTF() // cert type
        val len = dis.readInt()
        dis.skipBytes(len)
    }

    /**
     * Decrypts a Sun JKS protected key blob.
     *
     * Blob layout: [20 salt][ciphertext][20 sha1_check]
     * Keystream:   SHA1(pwd_utf16be || salt) || SHA1(pwd_utf16be || prev_hash) || ...
     * Verify:      SHA1(pwd_utf16be || plaintext) == sha1_check
     */
    private fun decryptKey(protectedKey: ByteArray, password: String): ByteArray {
        // The EncryptedPrivateKeyInfo DER wraps the actual protected blob.
        // Structure: SEQUENCE { AlgorithmIdentifier(16 bytes from offset 4), OCTET STRING { blob } }
        // AlgorithmIdentifier is always 16 bytes for OID 1.3.6.1.4.1.42.2.17.1.1 with NULL params.
        val algoIdLen = 16
        val outerHeaderLen = 4  // 30 82 xx xx
        var pos = outerHeaderLen + algoIdLen

        // OCTET STRING tag + length (may be 2 or 4 bytes depending on size)
        pos++ // skip 0x04 tag
        val lenByte = protectedKey[pos++].toInt() and 0xff
        if (lenByte and 0x80 != 0) {
            val n = lenByte and 0x7f
            pos += n  // skip multi-byte length
        }

        val blob = protectedKey.copyOfRange(pos, protectedKey.size)
        val salt = blob.copyOfRange(0, 20)
        val ciphertext = blob.copyOfRange(20, blob.size - 20)
        val check = blob.copyOfRange(blob.size - 20, blob.size)

        val pwdUtf16 = password.toByteArray(Charsets.UTF_16BE)

        val keystream = generateKeystream(pwdUtf16, salt, ciphertext.size)
        val plaintext = ByteArray(ciphertext.size) { i -> (ciphertext[i].toInt() xor keystream[i].toInt()).toByte() }

        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(pwdUtf16)
        sha1.update(plaintext)
        val verify = sha1.digest()

        if (!verify.contentEquals(check)) {
            throw SecurityException("JKS key decryption failed - wrong password for key entry")
        }

        return plaintext
    }

    private fun generateKeystream(pwdUtf16: ByteArray, salt: ByteArray, length: Int): ByteArray {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val result = ByteArray(length)
        var pos = 0
        var digestInput = pwdUtf16 + salt

        while (pos < length) {
            val hash = sha1.digest(digestInput)
            val copy = minOf(hash.size, length - pos)
            hash.copyInto(result, pos, 0, copy)
            pos += copy
            digestInput = pwdUtf16 + hash
        }
        return result
    }
}
