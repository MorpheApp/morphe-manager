/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.annotation.StringRes
import app.morphe.manager.R
import app.morphe.manager.util.enumByNameOrNull

enum class SourceBundleSortMode(
    @param:StringRes override val labelRes: Int
) : SortModeSpec {
    MANUAL(R.string.sources_sort_manual),
    LAST_UPDATED(R.string.sources_sort_last_updated),
    NAME_ASC(R.string.file_picker_sort_name_asc),
    NAME_DESC(R.string.file_picker_sort_name_desc),
    ENABLED_FIRST(R.string.sources_sort_enabled_first);

    companion object {
        fun fromPreference(value: String?): SourceBundleSortMode =
            enumByNameOrNull(value) ?: MANUAL
    }
}
