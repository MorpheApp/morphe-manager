/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/**
 * The file at [url], downloaded once into [cache] and checked against [sha256] every time it is
 * used. Builds of the runtime rarely change, so they are kept between builds.
 */
internal fun downloadPinned(url: String, sha256: String, cache: File, logger: Logger): File {
    val expected = sha256.lowercase()
    // Named after the hash, keeping the extension the archive is read by
    val cached = cache.resolve(expected + archiveExtension(url))
    if (cached.exists() && cached.sha256() == expected) return cached

    cached.parentFile.mkdirs()
    val partial = File(cached.path + ".part")
    logger.lifecycle("Downloading $url")
    URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }

    val actual = partial.sha256()
    if (actual != expected) {
        partial.delete()
        throw GradleException("$url has SHA-256 $actual instead of $expected")
    }
    if (!partial.renameTo(cached)) throw GradleException("Could not move the download into $cached")
    return cached
}

private fun archiveExtension(url: String): String {
    val name = url.substringAfterLast('/')
    return listOf(".tar.xz", ".tar.gz", ".deb").firstOrNull { name.endsWith(it) } ?: ""
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** The members of an ar archive, the format a Debian package comes in, by name. */
internal fun readArMembers(archive: File): Map<String, ByteArray> {
    val data = archive.readBytes()
    if (String(data, 0, AR_MAGIC.length, Charsets.US_ASCII) != AR_MAGIC) throw GradleException("$archive is not an ar archive")
    val members = mutableMapOf<String, ByteArray>()
    var offset = AR_MAGIC.length
    while (offset + AR_HEADER_SIZE <= data.size) {
        val name = String(data, offset, 16, Charsets.US_ASCII).trim().removeSuffix("/")
        val size = String(data, offset + 48, 10, Charsets.US_ASCII).trim().toInt()
        val start = offset + AR_HEADER_SIZE
        members[name] = data.copyOfRange(start, start + size)
        // Members are aligned to an even offset
        offset = start + size + size % 2
    }
    return members
}

/** A regular file or a symbolic link of a tar archive. */
internal class TarEntry(val path: String, val content: ByteArray?, val linkTarget: String?)

/** The regular files and symbolic links of a tar archive, by path without a leading ./ or /. */
internal fun readTarEntries(input: InputStream): Map<String, TarEntry> {
    val entries = mutableMapOf<String, TarEntry>()
    val header = ByteArray(TAR_BLOCK)
    while (input.readFully(header)) {
        if (header.all { it == 0.toByte() }) break
        val name = header.field(0, 100)
        val prefix = header.field(345, 155)
        val size = header.field(124, 12).trim().ifEmpty { "0" }.toLong(8).toInt()
        val type = header[156].toInt().toChar()
        val content = ByteArray(size).also { if (!input.readFully(it)) throw GradleException("Truncated tar entry $name") }
        // The content is padded to whole blocks
        input.skipNBytes(((TAR_BLOCK - size % TAR_BLOCK) % TAR_BLOCK).toLong())

        val path = (if (prefix.isEmpty()) name else "$prefix/$name").removePrefix("./").removePrefix("/")
        when (type) {
            '0', '\u0000' -> entries[path] = TarEntry(path, content, null)
            '2' -> entries[path] = TarEntry(path, null, header.field(157, 100))
        }
    }
    return entries
}

/**
 * The content of the file at [path], following symbolic links within the archive, which Debian
 * packages use to name a library by its soname.
 */
internal fun Map<String, TarEntry>.resolve(path: String): ByteArray {
    var current = path
    repeat(MAX_LINKS) {
        val entry = this[current] ?: throw GradleException("The archive has no $current")
        entry.content?.let { return it }
        val target = entry.linkTarget!!
        current = if (target.startsWith("/")) target.removePrefix("/")
        else File(current).parentFile?.resolve(target)?.normalize()?.invariantSeparatorsPath ?: target
    }
    throw GradleException("Too many links to follow from $path")
}

private fun ByteArray.field(offset: Int, length: Int): String {
    val end = (offset until offset + length).firstOrNull { this[it] == 0.toByte() } ?: (offset + length)
    return String(this, offset, end - offset, Charsets.US_ASCII)
}

private fun InputStream.readFully(buffer: ByteArray): Boolean {
    var read = 0
    while (read < buffer.size) {
        val count = read(buffer, read, buffer.size - read)
        if (count < 0) return false
        read += count
    }
    return true
}

private const val AR_MAGIC = "!<arch>\n"
private const val AR_HEADER_SIZE = 60
private const val TAR_BLOCK = 512
private const val MAX_LINKS = 8
