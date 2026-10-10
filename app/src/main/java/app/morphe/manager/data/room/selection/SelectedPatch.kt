/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/selection/SelectedPatch.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.room.selection

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "selected_patches",
    primaryKeys = ["selection", "patch_name"],
    foreignKeys = [ForeignKey(
        PatchSelection::class,
        parentColumns = ["uid"],
        childColumns = ["selection"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SelectedPatch(
    @ColumnInfo(name = "selection") val selection: Int,
    @ColumnInfo(name = "patch_name") val patchName: String
)