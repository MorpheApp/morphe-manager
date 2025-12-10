package app.revanced.manager.patcher.util

import android.os.Build
import android.util.Log
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeLibStripper {
    private const val TAG = "NativeLibStripper"

    suspend fun strip(apkFile: File): Boolean =
        strip(apkFile, Build.SUPPORTED_ABIS.filter { it.isNotBlank() })

    suspend fun strip(apkFile: File, supportedAbis: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            if (supportedAbis.isEmpty()) return@withContext false

            val preferredAbi = determinePreferredAbi(apkFile, supportedAbis)
            val allowedAbis = preferredAbi?.let { setOf(it) } ?: supportedAbis.toSet()

            if (preferredAbi != null) {
                Log.i(TAG, "Preserving native libraries for ABI $preferredAbi")
            }

            val tempFile = File(apkFile.parentFile, "${apkFile.nameWithoutExtension}-abi-stripped.apk")
            var removedEntries = 0

            ZipInputStream(apkFile.inputStream().buffered()).use { zis ->
                ZipOutputStream(tempFile.outputStream().buffered()).use { zos ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val keepEntry = shouldKeepZipEntry(name, allowedAbis)

                        if (keepEntry) {
                            val newEntry = cloneEntry(entry)
                            zos.putNextEntry(newEntry)
                            if (!entry.isDirectory) {
                                while (true) {
                                    val read = zis.read(buffer)
                                    if (read == -1) break
                                    zos.write(buffer, 0, read)
                                }
                            }
                            zos.closeEntry()
                        } else if (!entry.isDirectory) {
                            removedEntries++
                        }

                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (removedEntries > 0) {
                if (!apkFile.delete()) {
                    Log.w(TAG, "Failed to delete original APK before stripping ABIs")
                }
                tempFile.copyTo(apkFile, overwrite = true)
                tempFile.delete()
                Log.i(TAG, "Removed $removedEntries native library entries for unsupported ABIs")
                true
            } else {
                tempFile.delete()
                false
            }
        }

    private fun shouldKeepZipEntry(name: String, allowedAbis: Set<String>): Boolean {
        val abi = extractAbiFromEntry(name) ?: return true
        return abi in allowedAbis
    }

    private fun cloneEntry(entry: ZipEntry): ZipEntry {
        val clone = ZipEntry(entry.name)
        clone.time = entry.time
        clone.comment = entry.comment
        entry.extra?.let { clone.extra = it.copyOf() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                entry.creationTime?.let { clone.creationTime = it }
                entry.lastAccessTime?.let { clone.lastAccessTime = it }
                entry.lastModifiedTime?.let { clone.lastModifiedTime = it }
            } catch (_: Exception) {
                // Ignore metadata failures
            }
        }

        when (entry.method) {
            ZipEntry.STORED -> {
                clone.method = ZipEntry.STORED
                if (entry.size >= 0) clone.size = entry.size
                if (entry.compressedSize >= 0) clone.compressedSize = entry.compressedSize
                clone.crc = entry.crc
            }

            ZipEntry.DEFLATED -> clone.method = ZipEntry.DEFLATED
            else -> if (entry.method != -1) clone.method = entry.method
        }

        return clone
    }

    private fun extractAbiFromEntry(name: String): String? {
        if (!name.startsWith("lib/")) return null
        val secondSlash = name.indexOf('/', startIndex = 4)
        if (secondSlash == -1) return null
        return name.substring(4, secondSlash)
    }

    private fun determinePreferredAbi(apkFile: File, supportedAbis: List<String>): String? =
        runCatching {
            ZipFile(apkFile).use { zip ->
                val abisInApk = zip.entries().asSequence()
                    .map { it.name }
                    .mapNotNull(::extractAbiFromEntry)
                    .toSet()

                supportedAbis.firstOrNull { it in abisInApk }
            }
        }.getOrNull()
}
