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
    val isPinned: Boolean,
    val isDeleted: Boolean,
    val hasUpdate: Boolean,
    val patchCount: Int
)
