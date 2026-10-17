/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import app.morphe.manager.R

/**
 * A part of the settings with too much to it for a row of the settings page, opened as a page of its own.
 * A search of the settings page finds what it holds too, see [SettingsSubpageContent].
 */
enum class SettingsSubpage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val icon: ImageVector
) {
    PATCH_OPTIONS(
        R.string.settings_advanced_patch_options,
        R.string.settings_section_patch_options_summary,
        Icons.Outlined.Tune
    ),
    APPEARANCE(
        R.string.appearance,
        R.string.settings_section_appearance_summary,
        Icons.Outlined.Palette
    ),
    NOTIFICATIONS(
        R.string.settings_system_notifications,
        R.string.settings_system_notifications_description,
        Icons.Outlined.NotificationsActive
    ),
    INSTALLER(
        R.string.settings_installation,
        R.string.onboarding_system_installer_desc,
        Icons.Outlined.InstallMobile
    )
}
