/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.annotation.StringRes
import app.morphe.manager.R

enum class HomeAppSortMode(
    @param:StringRes override val labelRes: Int,
    @param:StringRes override val descriptionRes: Int
) : SortModeSpec {
    MANUAL(R.string.sources_sort_manual, R.string.home_app_sort_manual_description),
    RECOMMENDED(R.string.home_app_sort_recommended, R.string.home_app_sort_recommended_description),
    NAME_ASC(R.string.file_picker_sort_name_asc, R.string.home_app_sort_name_asc_description),
    NAME_DESC(R.string.file_picker_sort_name_desc, R.string.home_app_sort_name_desc_description),
    UPDATES_FIRST(R.string.home_app_sort_updates_first, R.string.home_app_sort_updates_first_description);

    companion object {
        fun fromPreference(value: String?): HomeAppSortMode =
            entries.firstOrNull { it.name == value } ?: RECOMMENDED
    }
}
