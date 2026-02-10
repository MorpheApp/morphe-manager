package app.morphe.manager.data.room.apps.original

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OriginalApkDao {
    @Query("SELECT * FROM original_apks")
    fun getAll(): Flow<List<OriginalApk>>

    @Query("SELECT * FROM original_apks WHERE package_name = :packageName")
    suspend fun get(packageName: String): OriginalApk?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(originalApk: OriginalApk)

    @Delete
    suspend fun delete(originalApk: OriginalApk)

    @Query("DELETE FROM original_apks WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("UPDATE original_apks SET last_used = :timestamp WHERE package_name = :packageName")
    suspend fun updateLastUsed(packageName: String, timestamp: Long = System.currentTimeMillis())
	
	@Query("SELECT COUNT(*) FROM original_apks")
    suspend fun getCount(): Int
}
