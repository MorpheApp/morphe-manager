/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/apps/installed/AppliedPatch.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.room.apps.installed

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import app.morphe.manager.data.room.bundles.PatchBundleEntity
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "applied_patch",
    primaryKeys = ["package_name", "bundle", "patch_name"],
    foreignKeys = [
        ForeignKey(
            InstalledApp::class,
            parentColumns = ["current_package_name"],
            childColumns = ["package_name"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            PatchBundleEntity::class,
            parentColumns = ["uid"],
            childColumns = ["bundle"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bundle"], unique = false)]
)
data class AppliedPatch(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "bundle") val bundle: Int,
    @ColumnInfo(name = "patch_name") val patchName: String,
    @ColumnInfo(name = "bundle_version") val bundleVersion: String? = null
) : Parcelable
