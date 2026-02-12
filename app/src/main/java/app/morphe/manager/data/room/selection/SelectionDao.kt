package app.morphe.manager.data.room.selection

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SelectionDao {
    /**
     * Get selected patches for a package across all bundles
     * @deprecated Use getSelectedPatchesForBundle for specific bundle or getAllSelectionsForPackage for all bundles
     */
    @Transaction
    @Query(
        "SELECT patch_bundle, patch_name FROM patch_selections" +
                " LEFT JOIN selected_patches ON uid = selected_patches.selection" +
                " WHERE package_name = :packageName"
    )
    abstract suspend fun getSelectedPatches(packageName: String): Map<@MapColumn("patch_bundle") Int, List<@MapColumn(
        "patch_name"
    ) String>>

    /**
     * Get selected patches for a specific package and bundle
     * Returns List - convert to Set in repository if needed
     */
    @Query(
        "SELECT sp.patch_name FROM patch_selections ps" +
                " INNER JOIN selected_patches sp ON ps.uid = sp.selection" +
                " WHERE ps.package_name = :packageName AND ps.patch_bundle = :bundleUid"
    )
    abstract suspend fun getSelectedPatchesForBundle(packageName: String, bundleUid: Int): List<String>

    /**
     * Get all selections for a package grouped by bundle
     */
    @Transaction
    @Query(
        "SELECT patch_bundle, patch_name FROM patch_selections" +
                " LEFT JOIN selected_patches ON uid = selected_patches.selection" +
                " WHERE package_name = :packageName"
    )
    abstract suspend fun getAllSelectionsForPackage(packageName: String): Map<@MapColumn("patch_bundle") Int, List<@MapColumn(
        "patch_name"
    ) String>>

    /**
     * Get summary of selections per package and bundle
     * Returns: Map<PackageName, Map<BundleUid, PatchCount>>
     */
    @Query(
        "SELECT ps.package_name, ps.patch_bundle, COUNT(sp.patch_name) as patch_count " +
                "FROM patch_selections ps " +
                "LEFT JOIN selected_patches sp ON ps.uid = sp.selection " +
                "GROUP BY ps.package_name, ps.patch_bundle " +
                "HAVING patch_count > 0"
    )
    abstract suspend fun getSelectionsSummaryRaw(): List<SelectionSummaryItem>

    suspend fun getSelectionsSummary(): Map<String, Map<Int, Int>> {
        val raw = getSelectionsSummaryRaw()
        return raw.groupBy { it.packageName }
            .mapValues { (_, items) ->
                items.associate { it.patchBundle to it.patchCount }
            }
    }

    /**
     * Export selection for a specific bundle
     */
    @Transaction
    @Query(
        "SELECT package_name, patch_name FROM patch_selections" +
                " LEFT JOIN selected_patches ON uid = selected_patches.selection" +
                " WHERE patch_bundle = :bundleUid"
    )
    abstract suspend fun exportSelection(bundleUid: Int): Map<@MapColumn("package_name") String, List<@MapColumn(
        "patch_name"
    ) String>>

    /**
     * Export selection for a specific package and bundle
     */
    @Query(
        "SELECT sp.patch_name FROM patch_selections ps" +
                " INNER JOIN selected_patches sp ON ps.uid = sp.selection" +
                " WHERE ps.package_name = :packageName AND ps.patch_bundle = :bundleUid"
    )
    abstract suspend fun exportSelectionForPackageAndBundle(packageName: String, bundleUid: Int): List<String>

    @Query("SELECT uid FROM patch_selections WHERE patch_bundle = :bundleUid AND package_name = :packageName")
    abstract suspend fun getSelectionId(bundleUid: Int, packageName: String): Int?

    @Insert
    abstract suspend fun createSelection(selection: PatchSelection)

    @Query("SELECT DISTINCT package_name FROM patch_selections")
    abstract fun getPackagesWithSelection(): Flow<List<String>>

    @Query("SELECT DISTINCT package_name FROM patch_selections WHERE patch_bundle = :bundleUid")
    abstract fun getPackagesWithSelectionForBundle(bundleUid: Int): Flow<List<String>>

    @Query("DELETE FROM patch_selections WHERE patch_bundle = :uid")
    abstract suspend fun resetForPatchBundle(uid: Int)

    @Query("DELETE FROM patch_selections WHERE package_name = :packageName")
    abstract suspend fun resetForPackage(packageName: String)

    @Query("DELETE FROM patch_selections WHERE package_name = :packageName AND patch_bundle = :bundleUid")
    abstract suspend fun resetForPackageAndBundle(packageName: String, bundleUid: Int)

    @Query("DELETE FROM patch_selections")
    abstract suspend fun reset()

    @Insert
    protected abstract suspend fun selectPatches(patches: List<SelectedPatch>)

    @Query("DELETE FROM selected_patches WHERE selection = :selectionId")
    protected abstract suspend fun clearSelection(selectionId: Int)

    @Transaction
    open suspend fun updateSelections(selections: Map<Int, Set<String>>) =
        selections.forEach { (selectionUid, patches) ->
            clearSelection(selectionUid)
            selectPatches(patches.map { SelectedPatch(selectionUid, it) })
        }
}

/**
 * Data class for selection summary query result
 */
data class SelectionSummaryItem(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "patch_bundle") val patchBundle: Int,
    @ColumnInfo(name = "patch_count") val patchCount: Int
)
