/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/split/SplitApkInspector.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.split

import android.util.Log
import app.morphe.manager.util.LOG_TAG
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
    /**
     * Runs [block] on the APK that describes [source]: the file itself, or the base module of a
     * split archive, extracted for the call and deleted after it.
     */
    suspend fun <T> withRepresentativeApk(
        source: File,
        workspace: File,
        block: suspend (File) -> T
    ): T {
        if (!SplitApkPreparer.isSplitArchive(source)) return block(source)

        val temp = File(
            workspace,
            "inspect-${UUID.randomUUID()}.apk"
        )

        try {
            withContext(Dispatchers.IO) {
                ZipFile(source).use { zip ->
                    val entry = selectBestEntry(zip)
                        ?: throw IOException("Split archive does not contain any APK entries.")
                    Log.d(LOG_TAG, "Reading ${source.name} through its module ${entry.name}")
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(temp.toPath()).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            return block(temp)
        } finally {
            temp.delete()
        }
    }

    private fun selectBestEntry(zip: ZipFile): ZipEntry? {
        val entries = SplitApkPreparer.splitModuleEntries(zip).toList()
        if (entries.isEmpty()) return null

        val lowered = entries.associateWith { it.name.lowercase(Locale.ROOT) }
        val baseEntry = lowered.entries.firstOrNull { (_, name) ->
            name.endsWith("base.apk") || "base-master" in name || "base-main" in name
        }?.key
        if (baseEntry != null) return baseEntry

        // Past the conventional names the base is told apart by its manifest, the same way the
        // merger finds it. An .xapk names it after the package, and an asset pack beside it can
        // outweigh it, so neither a name nor a size can be trusted there
        val manifestBase = entries.firstOrNull { SplitApkPreparer.isBaseModule(zip, it) }
        if (manifestBase != null) return manifestBase

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
}
