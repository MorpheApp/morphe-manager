package app.morphe.manager.ui.model

import android.content.pm.PackageInfo
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.morphe.manager.data.room.apps.installed.InstalledApp

/**
 * Represents a single app button on the home screen.
 * Built dynamically from patch bundle info and installed app data.
 */
@Immutable
data class HomeAppItem(
    val packageName: String,
    val displayName: String,
    val gradientColors: List<Color>,
    val installedApp: InstalledApp?,
    val packageInfo: PackageInfo?,
    val isPinnedByDefault: Boolean,
    val isInstalledOnDevice: Boolean,
    val isDeleted: Boolean,
    val hasSavedCopy: Boolean,
    val hasUpdate: Boolean,
    val patchCount: Int
) {
    /**
     * Whether the pending update is worth surfacing in the UI.
     * Uninstalled apps keep their update flag but show the uninstalled state instead.
     */
    val showsUpdateBadge: Boolean get() = hasUpdate && !isDeleted
}
