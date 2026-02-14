package app.morphe.manager.data.room.options

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OptionDao {
    /**
     * Get options for a package across all bundles
     */
    @Transaction
    @Query(
        "SELECT patch_bundle, `group`, patch_name, `key`, value FROM option_groups" +
                " LEFT JOIN options ON uid = options.`group`" +
                " WHERE package_name = :packageName"
    )
    abstract suspend fun getOptions(packageName: String): Map<@MapColumn("patch_bundle") Int, List<Option>>

    /**
     * Get options for a specific bundle and package
     */
    @Query(
        "SELECT o.`group`, o.patch_name, o.`key`, o.value FROM option_groups og" +
                " INNER JOIN options o ON og.uid = o.`group`" +
                " WHERE og.package_name = :packageName AND og.patch_bundle = :bundleUid"
    )
    abstract suspend fun getOptionsForBundle(packageName: String, bundleUid: Int): List<Option>

    /**
     * Export raw option values for a specific package and bundle
     * Returns: Map<PatchName, Map<OptionKey, SerializedValue>>
     */
    @Transaction
    @Query(
        "SELECT o.patch_name, o.`key`, o.value FROM option_groups og" +
                " INNER JOIN options o ON og.uid = o.`group`" +
                " WHERE og.package_name = :packageName AND og.patch_bundle = :bundleUid"
    )
    abstract suspend fun exportRawOptionsForBundle(packageName: String, bundleUid: Int): List<RawOption>

    /**
     * Convert list of RawOption to nested map structure
     */
    suspend fun exportOptionsForBundle(packageName: String, bundleUid: Int): Map<String, Map<String, String>> {
        val rawOptions = exportRawOptionsForBundle(packageName, bundleUid)
        return rawOptions.groupBy { it.patchName }
            .mapValues { (_, options) ->
                options.associate { it.key to it.value.toJsonString() }
            }
    }

    /**
     * Import raw option values for a specific package and bundle
     * Accepts serialized JSON strings and stores them directly
     */
    @Transaction
    open suspend fun importOptionsForBundle(
        packageName: String,
        bundleUid: Int,
        options: Map<String, Map<String, String>>
    ) {
        // Get or create option group for this package+bundle
        val groupId = getGroupId(bundleUid, packageName) ?: run {
            val newGroup = OptionGroup(
                uid = app.morphe.manager.data.room.AppDatabase.generateUid(),
                patchBundle = bundleUid,
                packageName = packageName
            )
            createOptionGroup(newGroup)
            newGroup.uid
        }

        // Clear existing options for this group
        clearGroup(groupId)

        // Convert JSON strings back to SerializedValue and create Option objects
        val optionsList = options.flatMap { (patchName, patchOptions) ->
            patchOptions.map { (key, jsonString) ->
                Option(
                    group = groupId,
                    patchName = patchName,
                    key = key,
                    value = Option.SerializedValue.fromJsonString(jsonString)
                )
            }
        }

        if (optionsList.isNotEmpty()) {
            insertOptions(optionsList)
        }
    }

    /**
     * Get summary of options per package and bundle
     * Returns raw data that will be processed in repository
     */
    @Query(
        "SELECT og.package_name, og.patch_bundle, COUNT(o.`key`) as option_count " +
                "FROM option_groups og " +
                "LEFT JOIN options o ON og.uid = o.`group` " +
                "GROUP BY og.package_name, og.patch_bundle " +
                "HAVING option_count > 0"
    )
    abstract suspend fun getOptionsSummaryRaw(): List<OptionSummaryItem>

    /**
     * Get summary of options per package and bundle
     * Returns: Map<PackageName, Map<BundleUid, OptionCount>>
     */
    suspend fun getOptionsSummary(): Map<String, Map<Int, Int>> {
        val raw = getOptionsSummaryRaw()
        return raw.groupBy { it.packageName }
            .mapValues { (_, items) ->
                items.associate { it.patchBundle to it.optionCount }
            }
    }

    @Query("SELECT uid FROM option_groups WHERE patch_bundle = :bundleUid AND package_name = :packageName")
    abstract suspend fun getGroupId(bundleUid: Int, packageName: String): Int?

    @Query("SELECT DISTINCT package_name FROM option_groups")
    abstract fun getPackagesWithOptions(): Flow<List<String>>

    /**
     * Get all packages that have saved options for a specific bundle
     */
    @Query("SELECT DISTINCT package_name FROM option_groups WHERE patch_bundle = :bundleUid")
    abstract fun getPackagesWithOptionsForBundle(bundleUid: Int): Flow<List<String>>

    @Insert
    abstract suspend fun createOptionGroup(group: OptionGroup)

    @Transaction
    @Query("DELETE FROM option_groups WHERE patch_bundle = :uid")
    abstract suspend fun resetOptionsForPatchBundle(uid: Int)

    @Transaction
    @Query("DELETE FROM option_groups WHERE package_name = :packageName")
    abstract suspend fun resetOptionsForPackage(packageName: String)

    /**
     * Reset options for a specific package and bundle combination
     */
    @Transaction
    @Query("DELETE FROM option_groups WHERE package_name = :packageName AND patch_bundle = :bundleUid")
    abstract suspend fun resetOptionsForPackageAndBundle(packageName: String, bundleUid: Int)

    @Transaction
    @Query("DELETE FROM option_groups")
    abstract suspend fun reset()

    @Insert
    protected abstract suspend fun insertOptions(patches: List<Option>)

    @Query("DELETE FROM options WHERE `group` = :groupId")
    protected abstract suspend fun clearGroup(groupId: Int)

    @Transaction
    open suspend fun updateOptions(options: Map<Int, List<Option>>) =
        options.forEach { (groupId, options) ->
            clearGroup(groupId)
            if (options.isNotEmpty()) {
                insertOptions(options)
            }
        }

    /**
     * Update options for a specific group
     */
    @Transaction
    open suspend fun updateOptionsForGroup(groupId: Int, options: List<Option>) {
        clearGroup(groupId)
        if (options.isNotEmpty()) {
            insertOptions(options)
        }
    }
}

/**
 * Data class for options summary query result
 */
data class OptionSummaryItem(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "patch_bundle") val patchBundle: Int,
    @ColumnInfo(name = "option_count") val optionCount: Int
)

/**
 * Data class for raw option export (keeps serialized value)
 */
data class RawOption(
    @ColumnInfo(name = "patch_name") val patchName: String,
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: Option.SerializedValue
)
