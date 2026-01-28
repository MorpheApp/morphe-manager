package app.revanced.manager.data.room.apps.installed

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.morphe.manager.R
import kotlinx.serialization.Serializable

enum class InstallType(val stringResource: Int) {
    DEFAULT(R.string.install_type_system_installer),
    CUSTOM(R.string.install_type_custom_installer),
    MOUNT(R.string.mount_install),
    SAVED(R.string.saved_install),
    SHIZUKU(R.string.install_type_shizuku_label)
}

/**
 * Simplified payload for storing patch selection configuration.
 */
@Serializable
data class SelectionPayload(
    val bundles: List<BundleSelection>
) {
    @Serializable
    data class BundleSelection(
        val bundleUid: Int,
        val patches: List<String>,
        val options: Map<String, Map<String, String>> = emptyMap()
    )
}

@Entity(tableName = "installed_app")
data class InstalledApp(
    @PrimaryKey
    @ColumnInfo(name = "current_package_name") val currentPackageName: String,
    @ColumnInfo(name = "original_package_name") val originalPackageName: String,
    @ColumnInfo(name = "version") val version: String,
    @ColumnInfo(name = "install_type") val installType: InstallType,
    @ColumnInfo(name = "selection_payload") val selectionPayload: SelectionPayload? = null
)
