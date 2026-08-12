package app.morphe.manager.data.room.apk

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ApkSignatureDao {
    /** Blocking on purpose: the callers are archive reads, which never run on the main thread. */
    @Query("SELECT * FROM apk_signatures")
    fun getAllBlocking(): List<ApkSignature>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(signature: ApkSignature)

    @Query(
        """
        DELETE FROM apk_signatures
        WHERE file_path = :filePath
          AND file_size = :fileSize
          AND last_modified = :lastModified
        """
    )
    suspend fun deleteRevision(filePath: String, fileSize: Long, lastModified: Long)
}
