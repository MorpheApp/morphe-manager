/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.data.room.selection

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SourceMuteDao {
    /** Every exclusion there is, for the one flow the whole app reads them through. */
    @Query("SELECT * FROM app_source_mutes")
    abstract fun getAll(): Flow<List<AppSourceMute>>

    @Query("SELECT patch_bundle FROM app_source_mutes WHERE package_name = :packageName")
    abstract suspend fun getForPackage(packageName: String): List<Int>

    @Query("SELECT package_name FROM app_source_mutes WHERE patch_bundle = :bundleUid")
    abstract suspend fun getForBundle(bundleUid: Int): List<String>

    @Query("SELECT DISTINCT patch_bundle FROM app_source_mutes")
    abstract suspend fun getAllBundleUids(): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(mutes: List<AppSourceMute>)

    @Query("DELETE FROM app_source_mutes WHERE package_name = :packageName")
    protected abstract suspend fun clearForPackage(packageName: String)

    @Query("DELETE FROM app_source_mutes WHERE package_name = :packageName AND patch_bundle = :bundleUid")
    abstract suspend fun clearForPackageAndBundle(packageName: String, bundleUid: Int)

    @Query("DELETE FROM app_source_mutes WHERE patch_bundle = :bundleUid")
    abstract suspend fun clearForBundle(bundleUid: Int)

    @Query("DELETE FROM app_source_mutes WHERE patch_bundle = :bundleUid AND package_name IN (:packageNames)")
    abstract suspend fun clearForBundle(bundleUid: Int, packageNames: List<String>)

    @Query("DELETE FROM app_source_mutes")
    abstract suspend fun clear()

    /** Replaces what [packageName] rules out, so the rows always describe one whole decision. */
    @Transaction
    open suspend fun replaceForPackage(packageName: String, bundleUids: Set<Int>) {
        clearForPackage(packageName)
        insert(bundleUids.map { AppSourceMute(it, packageName) })
    }

    /** Adds the apps a restored backup keeps from [bundleUid], leaving the other sources alone. */
    @Transaction
    open suspend fun addForBundle(bundleUid: Int, packageNames: Collection<String>) =
        insert(packageNames.map { AppSourceMute(bundleUid, it) })
}
