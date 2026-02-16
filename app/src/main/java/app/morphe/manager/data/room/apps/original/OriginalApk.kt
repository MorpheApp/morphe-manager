package app.morphe.manager.data.room.apps.original

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "original_apks")
data class OriginalApk(
    @PrimaryKey
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "version") val version: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "last_used") val lastUsed: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "file_size") val fileSize: Long
) {
    fun getFile(): File = File(filePath)
}
