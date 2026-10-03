package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.apps.original.OriginalApk
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.util.FilenameUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "Morphe OriginalApkRepository"

// Marks a copy that is still being written, so an interrupted save is never mistaken for an archive
internal const val STAGING_SUFFIX = ".part"

/**
 * Copies [source] over [target] through a staging file, so an archive is only ever replaced by a
 * copy that is already written in full. A failure drops the staged copy and leaves whatever sits
 * at [target] as it was.
 */
internal fun copyThroughStaging(source: File, target: File) {
    val staging = File(target.path + STAGING_SUFFIX)
    try {
        source.copyTo(staging, overwrite = true)
        // Files.move replaces the archive in one step and reports why it could not, which
        // File.renameTo neither guarantees nor tells
        Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        staging.delete()
        throw e
    }
}

/**
 * Moves [source] over [target] in one step where both sit on the same filesystem, which spares
 * rewriting a file of hundreds of megabytes. Elsewhere, it is copied through staging instead, and
 * [source] is left for its owner to delete.
 */
internal fun moveIntoPlace(source: File, target: File) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: AtomicMoveNotSupportedException) {
        copyThroughStaging(source, target)
    }
}

/**
 * Retains [source] as the original APK at [target]. Ephemeral inputs are moved directly into
 * place to avoid rewriting the file, while non-temporary inputs are copied through staging.
 */
internal fun retainOriginalApk(source: File, target: File, moveSource: Boolean) {
    // Copy file if source is different, and move it into place only once written in full
    if (source != target) {
        if (moveSource) moveIntoPlace(source, target)
        else copyThroughStaging(source, target)
    }
}

class OriginalApkRepository(
    db: AppDatabase,
    fs: Filesystem,
    private val prefs: PreferencesManager
) {
    private val dao = db.originalApkDao()
    // Use permanent directory from Filesystem instead of temporary directory
    private val originalApksDir: File = fs.originalApksDir

    fun getAll() = dao.getAll().distinctUntilChanged()

    suspend fun get(packageName: String) = dao.get(packageName)

    /**
     * Save original APK file for later repatching.
     * Automatically deletes old version if exists.
     * Returns null and skips persistence when the user has disabled original APK retention.
     *
     * @param moveSource whether [sourceFile] is a temporary file the caller has no further use
     *                   for, so it can be moved into place rather than copied.
     */
    suspend fun saveOriginalApk(
        packageName: String,
        version: String,
        sourceFile: File,
        moveSource: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        if (!prefs.saveOriginalApks.get()) {
            Log.d(TAG, "Original APK retention disabled, skipping save for $packageName")
            return@withContext null
        }
        // Create new file path
        val safePackage = FilenameUtils.sanitize(packageName)
        val safeVersion = FilenameUtils.sanitize(version.ifBlank { "unspecified" })
        val targetFile = originalApksDir.resolve("${safePackage}_${safeVersion}_original.apk")

        try {
            val existing = dao.get(packageName)

            retainOriginalApk(sourceFile, targetFile, moveSource)

            // Save to database
            val originalApk = OriginalApk(
                packageName = packageName,
                version = version,
                filePath = targetFile.absolutePath,
                lastUsed = System.currentTimeMillis(),
                fileSize = targetFile.length()
            )
            dao.upsert(originalApk)

            // The archive this one replaces goes last, so the package is never left without one.
            // The file just written and the file being copied from are not candidates: the first
            // is what the record now points at, the second is the caller's.
            existing?.let {
                val oldFile = File(it.filePath)
                if (oldFile.exists() && oldFile != sourceFile && oldFile != targetFile) {
                    oldFile.delete()
                    Log.d(TAG, "Deleted old original APK for $packageName")
                }
            }

            Log.d(TAG, "Saved original APK for $packageName v$version")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save original APK for $packageName", e)
            // Nothing to undo: whatever sits at the target is either the archive that was already
            // there or the one this call finished writing, and the staged copy cleans up after itself
            null
        }
    }

    /**
     * Update last used timestamp for tracking
     */
    suspend fun markUsed(packageName: String) {
        dao.updateLastUsed(packageName)
    }

    /**
     * Drops records whose retained file is gone, along with copies a save never finished writing.
     * A record here is only a pointer to its archive, so without the file it has nothing left to
     * describe and would otherwise keep reporting its old size and offering actions that no-op.
     * A staged copy has no record at all, so nothing but this pass would ever reclaim its space.
     */
    suspend fun pruneMissingApks() = withContext(Dispatchers.IO) {
        dao.getAll().first().forEach { originalApk ->
            if (File(originalApk.filePath).exists()) return@forEach
            dao.delete(originalApk)
            Log.d(TAG, "Dropped original APK record without a file for ${originalApk.packageName}")
        }
        originalApksDir.listFiles { file -> file.name.endsWith(STAGING_SUFFIX) }.orEmpty().forEach { staged ->
            staged.delete()
            Log.d(TAG, "Dropped staged original APK left by an interrupted save: ${staged.name}")
        }
    }

    /**
     * Delete original APK for package
     */
    suspend fun delete(packageName: String) = withContext(Dispatchers.IO) {
        val existing = dao.get(packageName) ?: return@withContext
        val file = File(existing.filePath)
        if (file.exists()) {
            file.delete()
        }
        dao.deleteByPackage(packageName)
        Log.d(TAG, "Deleted original APK for $packageName")
    }

    /**
     * Delete original APK entry
     */
    suspend fun delete(originalApk: OriginalApk) = withContext(Dispatchers.IO) {
        val file = File(originalApk.filePath)
        if (file.exists()) {
            file.delete()
        }
        dao.delete(originalApk)
    }
}
