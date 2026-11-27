/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import android.os.Environment
import android.util.Log
import android.util.LruCache
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.util.APK_EXTENSIONS
import app.morphe.manager.util.LOG_TAG
import app.morphe.manager.util.PM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// A browser saves one file per download, so a handful of covers several downloads in a row
private const val MAX_CANDIDATES = 5

/** An APK archive in the Download folder, read as [packageName]. */
data class DownloadedApk(
    val file: File,
    val packageName: String
)

/**
 * Finds an APK the user just downloaded in a browser, so it can be taken without a trip
 * through the file picker.
 *
 * Only the top of the public Download folder is looked at, where browsers save to, and only with
 * access to all files: other apps' downloads are not visible to the manager otherwise.
 */
class DownloadedApkFinder(
    private val filesystem: Filesystem,
    private val pm: PM
) {
    // Keyed by path, size and modification time, so a file still being written is read again.
    // Files that are no APK are kept too, as the folder is looked at again every few seconds
    private val readCache = LruCache<String, Read>(16)

    private class Read(val apk: DownloadedApk?)

    /** The newest archive of [packageName] saved at [since] or later, null when there is none. */
    suspend fun find(packageName: String, since: Long): DownloadedApk? = withContext(Dispatchers.IO) {
        if (!filesystem.hasStoragePermission()) return@withContext null
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        downloads.listFiles()
            .orEmpty()
            .filter { it.isCandidate(since) }
            .sortedByDescending(File::lastModified)
            .take(MAX_CANDIDATES)
            .firstNotNullOfOrNull { file -> read(file)?.takeIf { it.packageName == packageName } }
    }

    // Unfinished downloads are left out by name: browsers keep them hidden (".pending-" under
    // MediaStore) or under their own extension (.crdownload, .part) until they are complete
    private fun File.isCandidate(since: Long) =
        isFile && !name.startsWith('.') && extension.lowercase() in APK_EXTENSIONS && lastModified() >= since

    private suspend fun read(file: File): DownloadedApk? {
        val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        readCache.get(key)?.let { return it.apk }

        val apk = try {
            SplitApkInspector.withRepresentativeApk(file, filesystem.uiTempDir) { apk ->
                pm.getPackageInfo(apk)?.let { DownloadedApk(file, it.packageName) }
            }
        } catch (e: Exception) {
            // A download written straight to its final name fails here until it is complete
            Log.d(LOG_TAG, "Downloaded file ${file.name} is not a readable APK yet", e)
            null
        }
        readCache.put(key, Read(apk))
        return apk
    }
}
