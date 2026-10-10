/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/apps/installed/InstalledAppDao.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.room.apps.installed

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class BundleWithVersion(
    val bundleUid: Int,
    val version: String?
)

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_app")
    fun getAll(): Flow<List<InstalledApp>>

    @Query("SELECT * FROM installed_app WHERE current_package_name = :packageName")
    suspend fun get(packageName: String): InstalledApp?

    @Query("SELECT * FROM installed_app WHERE current_package_name = :packageName")
    fun getAsFlow(packageName: String): Flow<InstalledApp?>

    @Query(
        "SELECT bundle, patch_name FROM applied_patch" +
                " WHERE package_name = :packageName"
    )
    suspend fun getPatchesSelection(packageName: String): Map<@MapColumn("bundle") Int, List<@MapColumn(
        "patch_name"
    ) String>>

    @Query(
        "SELECT DISTINCT bundle as bundleUid, bundle_version as version FROM applied_patch" +
                " WHERE package_name = :packageName"
    )
    suspend fun getBundleVersions(packageName: String): List<BundleWithVersion>

    @Transaction
    suspend fun upsertApp(installedApp: InstalledApp, appliedPatches: List<AppliedPatch>) {
        upsertApp(installedApp)
        deleteAppliedPatches(installedApp.currentPackageName)
        insertAppliedPatches(appliedPatches)
    }

    @Upsert
    suspend fun upsertApp(installedApp: InstalledApp)

    @Insert
    suspend fun insertAppliedPatches(appliedPatches: List<AppliedPatch>)

    @Query("DELETE FROM applied_patch WHERE package_name = :packageName")
    suspend fun deleteAppliedPatches(packageName: String)

    @Delete
    suspend fun delete(installedApp: InstalledApp)

    /**
     * How many other records describe an install of the same app, which is what separates a
     * single install from an app the user keeps several patched copies of.
     */
    @Query(
        "SELECT COUNT(*) FROM installed_app" +
                " WHERE original_package_name = :originalPackageName AND current_package_name != :currentPackageName"
    )
    suspend fun countSiblings(originalPackageName: String, currentPackageName: String): Int
}
