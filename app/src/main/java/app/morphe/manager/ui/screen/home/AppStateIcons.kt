/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icons an app's states go by, shared by its home card and its details so a state is told by the
 * same picture in both.
 */
internal object AppStateIcons {
    val Clone: ImageVector get() = Icons.Outlined.ContentCopy
    val Uninstalled: ImageVector get() = Icons.Outlined.DeleteOutline
    val NotPatched: ImageVector get() = Icons.Outlined.AutoFixHigh
    val Unverified: ImageVector get() = Icons.AutoMirrored.Outlined.HelpOutline
    val Pending: ImageVector get() = Icons.Outlined.HourglassEmpty

    /** A newer app version the sources support, which takes a newer APK. */
    val NewerVersion: ImageVector get() = Icons.Outlined.ArrowUpward

    /** Newer patches for the version installed, which the APK already there can take. */
    val NewerPatches: ImageVector get() = Icons.Outlined.Extension
}
