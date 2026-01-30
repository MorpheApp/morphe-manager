package app.revanced.manager.patcher.split

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SplitApkInspector {
    suspend fun extractRepresentativeApk(source: File, workspace: File): ExtractedApk? {
        if (!SplitApkPreparer.isSplitArchive(source)) return null

        val temp = File(
            workspace,
            "inspect-${UUID.randomUUID()}.apk"
        )

        return try {
            withContext(Dispatchers.IO) {
                ZipFile(source).use { zip ->
                    val entry = selectBestEntry(zip)
                        ?: throw IOException("Split archive does not contain any APK entries.")
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(temp.toPath()).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            ExtractedApk(temp) { temp.delete() }
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun selectBestEntry(zip: ZipFile): ZipEntry? {
        val entries = zip.entries().asSequence()
            .filterNot { it.isDirectory }
            .filter { it.name.lowercase(Locale.ROOT).endsWith(".apk") }
            .toList()
        if (entries.isEmpty()) return null

        val lowered = entries.associateWith { it.name.lowercase(Locale.ROOT) }
        val baseEntry = lowered.entries.firstOrNull { (_, name) ->
            name.endsWith("/base.apk") || name.endsWith("base.apk") || "base-master" in name || "base-main" in name
        }?.key
        if (baseEntry != null) return baseEntry

        val primaryEntry = lowered.entries.firstOrNull { (_, name) ->
            "main" in name || "master" in name
        }?.key
        if (primaryEntry != null) return primaryEntry

        val nonConfig = lowered.entries.filter { (_, name) ->
            !name.startsWith("config") && !name.contains("split_config") && !name.contains("config.")
        }.map { it.key }
        val largestNonConfig = nonConfig
            .filter { it.size >= 0 }
            .maxByOrNull { it.size }
        if (largestNonConfig != null) return largestNonConfig

        return entries.minWithOrNull(
            compareBy<ZipEntry> { entry ->
                val lower = entry.name.lowercase(Locale.ROOT)
                when {
                    "base" in lower -> 0
                    "main" in lower || "master" in lower -> 1
                    lower.startsWith("config") -> 99
                    else -> 2
                }
            }.thenBy { it.name.length }
        )
    }

    data class ExtractedApk(
        val file: File,
        val cleanup: () -> Unit = {}
    )
}
