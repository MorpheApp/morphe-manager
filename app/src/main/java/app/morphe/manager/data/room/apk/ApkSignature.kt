package app.morphe.manager.data.room.apk

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Certificate fingerprints extracted from an APK on disk.
 *
 * [fileSize] and [lastModified] identify the archive they were taken from, so a file rewritten
 * at the same path no longer matches.
 */
@Entity(tableName = "apk_signatures")
data class ApkSignature(
    @PrimaryKey
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    /** SHA-256 fingerprints joined by [SEPARATOR]. Empty when the archive carries no signature. */
    @ColumnInfo(name = "hashes") val hashes: String
) {
    companion object {
        const val SEPARATOR = ","
    }
}
