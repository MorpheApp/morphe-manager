/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.data.room.selection

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import app.morphe.manager.data.room.bundles.PatchBundleEntity

/**
 * The apps a source has listed so far, kept for a source that leaves out the apps it adds later.
 *
 * Only such sources have a row. Every other source brings whatever its patches name, so an app
 * it starts supporting reaches the home screen on its own. Deleting a source takes its row along.
 */
@Entity(
    tableName = "source_app_lists",
    foreignKeys = [ForeignKey(
        PatchBundleEntity::class,
        parentColumns = ["uid"],
        childColumns = ["patch_bundle"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SourceAppList(
    @PrimaryKey @ColumnInfo(name = "patch_bundle") val patchBundle: Int,
    /** Every app the source has named since it stopped bringing new ones. */
    @ColumnInfo(name = "known_apps") val knownApps: Set<String>,
    /** Apps left out on arrival that the user has not looked at in the source's app list yet. */
    @ColumnInfo(name = "unseen_apps") val unseenApps: Set<String> = emptySet()
)
