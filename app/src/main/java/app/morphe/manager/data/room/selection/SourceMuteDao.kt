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
import androidx.room.Upsert
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

    @Query("SELECT * FROM source_app_lists")
    abstract fun getAppLists(): Flow<List<SourceAppList>>

    @Query("SELECT * FROM source_app_lists WHERE patch_bundle = :bundleUid")
    abstract suspend fun getAppList(bundleUid: Int): SourceAppList?

    @Query("SELECT patch_bundle FROM source_app_lists")
    abstract suspend fun getAppListBundleUids(): List<Int>

    @Upsert
    abstract suspend fun upsertAppList(list: SourceAppList)

    @Query("DELETE FROM source_app_lists WHERE patch_bundle = :bundleUid")
    abstract suspend fun clearAppList(bundleUid: Int)

    @Transaction
    open suspend fun clearUnseenApps(bundleUid: Int) {
        val list = getAppList(bundleUid)?.takeIf { it.unseenApps.isNotEmpty() } ?: return
        upsertAppList(list.copy(unseenApps = emptySet()))
    }

    @Query("DELETE FROM source_app_lists")
    abstract suspend fun clearAppLists()

    /**
     * Keeps the apps each source names for the first time from it, for the sources that leave
     * new apps out, and records them as known. [listedApps] holds what each source names now.
     *
     * Known apps are only ever added to. A release that briefly drops an app, or fails to name
     * any, would otherwise make every app it names again look new and take the user's own
     * choices off the home screen.
     */
    @Transaction
    open suspend fun leaveOutNewApps(listedApps: Map<Int, Set<String>>) {
        listedApps.forEach { (bundleUid, listed) ->
            val list = getAppList(bundleUid) ?: return@forEach
            val arrived = listed - list.knownApps
            if (arrived.isEmpty()) return@forEach

            insert(arrived.map { AppSourceMute(bundleUid, it) })
            upsertAppList(
                list.copy(knownApps = list.knownApps + arrived, unseenApps = list.unseenApps + arrived)
            )
        }
    }
}
