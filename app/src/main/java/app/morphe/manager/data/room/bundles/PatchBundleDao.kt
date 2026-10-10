/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/bundles/PatchBundleDao.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.room.bundles

import androidx.room.*

@Dao
interface PatchBundleDao {
    @Query("SELECT * FROM patch_bundles ORDER BY sort_order ASC, uid ASC")
    suspend fun all(): List<PatchBundleEntity>

    @Query("SELECT uid FROM patch_bundles")
    suspend fun allUids(): List<Int>

    @Query("UPDATE patch_bundles SET version = :patches WHERE uid = :uid")
    suspend fun updateVersionHash(uid: Int, patches: String?)

    @Query("DELETE FROM patch_bundles WHERE uid != 0")
    suspend fun purgeCustomBundles()

    @Transaction
    suspend fun reset() {
        purgeCustomBundles()
        updateVersionHash(0, null) // Reset the main source
    }

    @Query("DELETE FROM patch_bundles WHERE uid = :uid")
    suspend fun remove(uid: Int)

    @Query("SELECT name, display_name, version, auto_update, source, enabled, sort_order, created_at, updated_at FROM patch_bundles WHERE uid = :uid")
    suspend fun getProps(uid: Int): PatchBundleProperties?

    @Upsert
    suspend fun upsert(source: PatchBundleEntity)

    @Query("SELECT MAX(sort_order) FROM patch_bundles")
    suspend fun maxSortOrder(): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM patch_bundles WHERE uid != :uid AND display_name IS NOT NULL AND LOWER(display_name) = LOWER(:displayName))")
    suspend fun hasDisplayNameConflict(uid: Int, displayName: String): Boolean
}
