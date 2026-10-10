/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.annotation.StringRes
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.util.enumByNameOrNull

enum class HomeAppFilterMode(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int
) {
    ALL(R.string.home_category_all_apps, R.string.home_app_filter_all_description),
    PATCHED(R.string.patched, R.string.home_app_filter_patched_description),
    NOT_PATCHED(R.string.home_not_patched_yet, R.string.home_app_filter_not_patched_description),
    INSTALLED(R.string.installed, R.string.home_app_filter_installed_description),
    UNINSTALLED(R.string.uninstalled, R.string.home_app_filter_uninstalled_description),
    CLONES(R.string.home_app_filter_clones, R.string.home_app_filter_clones_description);

    val isActive: Boolean get() = this != ALL

    fun matches(item: HomeAppItem): Boolean = when (this) {
        ALL -> true
        PATCHED -> item.installedApp != null
        NOT_PATCHED -> item.installedApp == null
        INSTALLED -> item.isInstalledOnDevice && !item.isDeleted
        UNINSTALLED -> item.installedApp != null && (!item.isInstalledOnDevice || item.isDeleted)
        CLONES -> item.isClone
    }

    companion object {
        /** Parse a persisted enum name; unknown or missing values fall back to [ALL]. */
        fun fromPreference(value: String?): HomeAppFilterMode =
            enumByNameOrNull(value) ?: ALL
    }
}
