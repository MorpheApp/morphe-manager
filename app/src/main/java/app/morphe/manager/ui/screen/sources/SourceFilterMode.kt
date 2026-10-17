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
    @param:StringRes override val labelRes: Int,
    @param:StringRes override val descriptionRes: Int
) : SortModeSpec {
    ALL(R.string.all, R.string.sources_filter_all_description),
    ENABLED(R.string.enabled, R.string.sources_filter_enabled_description),
    DISABLED(R.string.disabled, R.string.sources_filter_disabled_description),
    UPDATES(R.string.sources_filter_updates, R.string.sources_filter_updates_description),
    ISSUES(R.string.sources_filter_issues, R.string.sources_filter_issues_description);

    val isActive: Boolean get() = this != ALL

    /**
     * @param blocked Whether the source is blocked, which leaves it unusable however it is set.
     * @param unreachable Whether its details could not be fetched.
     * @param updatable Whether a newer version of it waits to be fetched.
     */
    fun matches(source: PatchBundleSource, blocked: Boolean, unreachable: Boolean, updatable: Boolean): Boolean =
        when (this) {
            ALL -> true
            ENABLED -> source.enabled && !blocked
            DISABLED -> !source.enabled
            UPDATES -> updatable
            ISSUES -> blocked || unreachable || source.isHeldBack || source.requiresManagerUpdate ||
                    source.state is PatchBundleSource.State.Failed ||
                    source.state is PatchBundleSource.State.Missing
        }
}
