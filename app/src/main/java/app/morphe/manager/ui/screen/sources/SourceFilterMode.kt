/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.sources

import androidx.annotation.StringRes
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.isHeldBack
import app.morphe.manager.domain.manager.SortModeSpec

/** Which sources the sources page lists. */
enum class SourceFilterMode(
    @param:StringRes override val labelRes: Int
) : SortModeSpec {
    ALL(R.string.all),
    ENABLED(R.string.enabled),
    DISABLED(R.string.disabled),
    ISSUES(R.string.sources_filter_issues);

    val isActive: Boolean get() = this != ALL

    /**
     * @param blocked Whether the source is blocked, which leaves it unusable however it is set.
     * @param unreachable Whether its details could not be fetched.
     */
    fun matches(source: PatchBundleSource, blocked: Boolean, unreachable: Boolean): Boolean =
        when (this) {
            ALL -> true
            ENABLED -> source.enabled && !blocked
            DISABLED -> !source.enabled
            ISSUES -> blocked || unreachable || source.isHeldBack || source.requiresManagerUpdate ||
                    source.state is PatchBundleSource.State.Failed ||
                    source.state is PatchBundleSource.State.Missing
        }
}
