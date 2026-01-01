package app.revanced.manager.data.room.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatchProfileDao {
    @Query("SELECT * FROM patch_profiles ORDER BY created_at DESC")
    fun observeAll(): Flow<List<PatchProfileEntity>>

    @Query("SELECT * FROM patch_profiles WHERE package_name = :packageName ORDER BY created_at DESC")
    fun observeForPackage(packageName: String): Flow<List<PatchProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PatchProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<PatchProfileEntity>)

    @Query("SELECT * FROM patch_profiles ORDER BY created_at DESC")
    suspend fun getAll(): List<PatchProfileEntity>

    @Query("SELECT * FROM patch_profiles WHERE package_name = :packageName AND name = :name LIMIT 1")
    suspend fun findByPackageAndName(packageName: String, name: String): PatchProfileEntity?

    @Query("DELETE FROM patch_profiles WHERE uid = :uid")
    suspend fun delete(uid: Int)

    @Query("DELETE FROM patch_profiles WHERE uid IN (:uids)")
    suspend fun delete(uids: List<Int>)

    @Query("SELECT * FROM patch_profiles WHERE uid = :uid")
    suspend fun get(uid: Int): PatchProfileEntity?
}
