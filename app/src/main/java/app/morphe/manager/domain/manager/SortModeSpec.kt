/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.annotation.StringRes

/**
 * Enum values used as sort modes provide their own label/description string resources
 * so shared UI can render them without a per-enum mapping helper.
 */
interface SortModeSpec {
    @get:StringRes val labelRes: Int
    @get:StringRes val descriptionRes: Int
}
