/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.annotation.StringRes
import app.morphe.manager.R

enum class SourceBundleSortMode(
    @param:StringRes override val labelRes: Int,
    @param:StringRes override val descriptionRes: Int
) : SortModeSpec {
    MANUAL(R.string.sources_sort_manual, R.string.sources_sort_manual_hint),
    LAST_UPDATED(R.string.sources_sort_last_updated, R.string.sources_sort_last_updated_description),
    NAME_ASC(R.string.file_picker_sort_name_asc, R.string.home_app_sort_name_asc_description),
    NAME_DESC(R.string.file_picker_sort_name_desc, R.string.home_app_sort_name_desc_description),
    ENABLED_FIRST(R.string.sources_sort_enabled_first, R.string.sources_sort_enabled_first_description);

    companion object {
        fun fromPreference(value: String?): SourceBundleSortMode =
            entries.firstOrNull { it.name == value } ?: MANUAL
    }
}
