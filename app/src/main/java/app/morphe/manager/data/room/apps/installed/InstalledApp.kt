/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/apps/installed/InstalledApp.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.room.apps.installed

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.morphe.manager.R
import kotlinx.serialization.Serializable

enum class InstallType(val stringResource: Int) {
    DEFAULT(R.string.home_app_info_install_type_system_installer),
    PLAY_STORE(R.string.home_app_info_install_type_play_store),
    ROOT_PLAY_STORE(R.string.home_app_info_install_type_root_play_store),
    CUSTOM(R.string.home_app_info_install_type_custom_installer),
    MOUNT(R.string.mount),
    SAVED(R.string.saved),
    SHIZUKU(R.string.home_app_info_install_type_shizuku),
    SHIZUKU_PLAY_STORE(R.string.home_app_info_install_type_shizuku_play_store)
}

/**
 * Simplified payload for storing patch selection configuration.
 */
@Serializable
data class SelectionPayload(
    val bundles: List<BundleSelection>
) {
    /**
     * [bundleName] and [bundleVersion] are the source as it was when the app was patched. They
     * outlive its deletion, which cascades the applied patches away along with their attribution.
     */
    @Serializable
    data class BundleSelection(
        val bundleUid: Int,
        val patches: List<String>,
        val options: Map<String, Map<String, String>> = emptyMap(),
        val bundleName: String? = null,
        val bundleVersion: String? = null
    )
}

@Entity(tableName = "installed_app")
data class InstalledApp(
    @PrimaryKey
    @ColumnInfo(name = "current_package_name") val currentPackageName: String,
    @ColumnInfo(name = "original_package_name") val originalPackageName: String,
    @ColumnInfo(name = "version") val version: String,
    @ColumnInfo(name = "install_type") val installType: InstallType,
    @ColumnInfo(name = "selection_payload") val selectionPayload: SelectionPayload? = null,
    @ColumnInfo(name = "patched_at") val patchedAt: Long? = null,
    /**
     * The app's own name as it read at patch time. Records outlive the APKs and the bundle that
     * could describe them, and nothing else is left to show in their place but the package name.
     */
    @ColumnInfo(name = "app_label") val appLabel: String? = null,
    /**
     * Whether this install is a copy the user asked for rather than the app's own install.
     *
     * A package name of its own is what makes a copy installable, but it is not what makes it a
     * copy: patches rename the app for reasons of their own, and those builds are still the one
     * install the app has. Only the run knows which it produced, so it records the answer here.
     */
    @ColumnInfo(name = "is_clone") val isClone: Boolean = false
)

/**
 * Root mount binds the patched APK over the stock one, so it only applies while patching kept
 * the original package name. Apps renamed by patches (GmsCore builds) must use a regular install.
 */
val InstalledApp.supportsMount: Boolean
    get() = currentPackageName == originalPackageName

/**
 * Whether the saved build of this app is put back by mounting it. A mount build only works
 * mounted, so its record decides, while any other build follows the primary installer.
 */
fun InstalledApp.reinstallsByMount(primaryInstallerIsMount: Boolean): Boolean =
    installType == InstallType.MOUNT || (primaryInstallerIsMount && supportsMount)

/**
 * How the manager refers to this install everywhere the package name is not enough: home cards,
 * batch targets and the saved configuration a rebuild reads back.
 *
 * The app answers for its own install whatever name the patches built it under, so only a copy
 * is referred to by a name of its own, which is the one thing it cannot share with the app.
 */
val InstalledApp.trackingKey: String
    get() = if (isClone) currentPackageName else originalPackageName
