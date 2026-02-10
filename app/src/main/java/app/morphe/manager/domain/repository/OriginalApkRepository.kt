package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.apps.original.OriginalApk
import app.morphe.manager.util.FilenameUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File

class OriginalApkRepository(
    db: AppDatabase,
    fs: Filesystem
) {
    private val dao = db.originalApkDao()
    // Use permanent directory from Filesystem instead of temporary directory
    private val originalApksDir: File = fs.originalApksDir

    fun getAll() = dao.getAll().distinctUntilChanged()

    suspend fun get(packageName: String) = dao.get(packageName)

    /**
     * Save original APK file for later repatching.
     * Automatically deletes old version if exists.
     */
    suspend fun saveOriginalApk(
        packageName: String,
        version: String,
        sourceFile: File
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Delete old version if exists
            val existing = dao.get(packageName)
            existing?.let {
                val oldFile = File(it.filePath)
                if (oldFile.exists() && oldFile != sourceFile) {
                    oldFile.delete()
                    Log.d(TAG, "Deleted old original APK for $packageName")
                }
            }

            // Create new file path
            val safePackage = FilenameUtils.sanitize(packageName)
            val safeVersion = FilenameUtils.sanitize(version.ifBlank { "unspecified" })
            val targetFile = originalApksDir.resolve("${safePackage}_${safeVersion}_original.apk")

            // Copy file if source is different
            if (sourceFile != targetFile) {
                sourceFile.copyTo(targetFile, overwrite = true)
            }

            // Save to database
            val originalApk = OriginalApk(
                packageName = packageName,
                version = version,
                filePath = targetFile.absolutePath,
                lastUsed = System.currentTimeMillis(),
                fileSize = targetFile.length()
            )
            dao.upsert(originalApk)

            Log.d(TAG, "Saved original APK for $packageName v$version")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save original APK for $packageName", e)
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

    companion object {
        private const val TAG = "OriginalApkRepository"
    }
}
