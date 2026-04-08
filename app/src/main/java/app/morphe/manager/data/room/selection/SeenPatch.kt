package app.morphe.manager.data.room.selection

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import app.morphe.manager.data.room.bundles.PatchBundleEntity

/**
 * Stores the complete set of patch names that were present in a bundle at the time of the
 * last patching session. Used to distinguish genuinely new patches (added in a bundle update)
 * from patches that were simply deselected by the user during a previous session.
 */
@Entity(
    tableName = "seen_patches",
    primaryKeys = ["patch_bundle", "package_name", "patch_name"],
    foreignKeys = [ForeignKey(
        PatchBundleEntity::class,
        parentColumns = ["uid"],
        childColumns = ["patch_bundle"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SeenPatch(
    @ColumnInfo(name = "patch_bundle") val patchBundle: Int,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "patch_name") val patchName: String
)
