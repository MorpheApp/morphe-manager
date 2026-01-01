package app.revanced.manager.data.room.profile

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patch_profiles")
data class PatchProfileEntity(
    @PrimaryKey @ColumnInfo(name = "uid") val uid: Int,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_version") val appVersion: String?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "payload") val payload: PatchProfilePayload,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
