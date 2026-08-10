/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.annotation.StringRes
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem

enum class HomeAppFilterMode(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int
) {
    ALL(R.string.home_category_all_apps, R.string.home_app_filter_all_description),
    PATCHED(R.string.patched, R.string.home_app_filter_patched_description),
    NOT_PATCHED(R.string.home_not_patched_yet, R.string.home_app_filter_not_patched_description),
    INSTALLED(R.string.installed, R.string.home_app_filter_installed_description),
    UNINSTALLED(R.string.uninstalled, R.string.home_app_filter_uninstalled_description);

    val isActive: Boolean get() = this != ALL

    fun matches(item: HomeAppItem): Boolean = when (this) {
        ALL -> true
        PATCHED -> item.installedApp != null
        NOT_PATCHED -> item.installedApp == null
        INSTALLED -> item.isInstalledOnDevice && !item.isDeleted
        UNINSTALLED -> item.installedApp != null && (!item.isInstalledOnDevice || item.isDeleted)
    }
}
