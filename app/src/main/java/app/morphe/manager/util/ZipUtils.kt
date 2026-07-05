package app.morphe.manager.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility for writing plain files into a zip archive at a content [Uri].
 */
object ZipUtils {
    /**
     * Writes the existing files in [files] into a new zip archive at [uri].
     * Non-existent files are silently skipped. Returns false if nothing was
     * written or write failed.
     */
    fun zip(context: Context, uri: Uri, files: List<File>): Boolean {
        val existingFiles = files.filter { it.exists() }
        if (existingFiles.isEmpty()) return false

        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                val usedNames = mutableSetOf<String>()
                ZipOutputStream(output).use { zip ->
                    existingFiles.forEach { file ->
                        zip.putNextEntry(ZipEntry(uniqueEntryName(file, usedNames)))
                        file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: error("Failed to open export stream")
        }.isSuccess
    }

    private fun uniqueEntryName(file: File, usedNames: MutableSet<String>): String {
        val originalName = file.name.ifBlank { "file-${usedNames.size + 1}" }
        var candidate = originalName
        val dotIndex = originalName.lastIndexOf('.')
        val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        var suffix = 2

        while (!usedNames.add(candidate)) {
            candidate = "$base-$suffix$extension"
            suffix++
        }

        return candidate
    }
}
