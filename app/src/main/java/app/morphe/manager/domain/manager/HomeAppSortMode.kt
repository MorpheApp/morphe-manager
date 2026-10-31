/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.annotation.StringRes
import app.morphe.manager.R
import app.morphe.manager.util.enumByNameOrNull

enum class HomeAppSortMode(
    @param:StringRes override val labelRes: Int
) : SortModeSpec {
    MANUAL(R.string.sources_sort_manual),
    RECOMMENDED(R.string.home_app_sort_recommended),
    NAME_ASC(R.string.file_picker_sort_name_asc),
    NAME_DESC(R.string.file_picker_sort_name_desc),
    UPDATES_FIRST(R.string.home_app_sort_updates_first),
    RECENTLY_PATCHED(R.string.home_app_sort_recently_patched);

    companion object {
        fun fromPreference(value: String?): HomeAppSortMode =
            enumByNameOrNull(value) ?: RECOMMENDED
    }
}
