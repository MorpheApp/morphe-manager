/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.runtime.*
import app.morphe.manager.domain.manager.HomeAppCategory
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.SelectionState
import app.morphe.manager.ui.screen.shared.rememberSelectionState

internal data class CategoryNameRequest(
    val category: HomeAppCategory?
)

/**
 * Everything the apps section keeps for itself: what is selected, which reorder or category
 * context is open, and which dialog is showing.
 *
 * Held in one object rather than as two dozen loose `remember`s so the list builders and the
 * action bar can take a single parameter instead of threading each flag through their own. The
 * modes are mutually exclusive by construction - entering one leaves the others - which is what
 * the footer slot expects, since it renders only one bar at a time.
 */
@Stable
internal class HomeAppsSectionState(
    val selectedPackages: SelectionState<String>,
    initialOrder: List<String>,
    initialSourceGroupOrder: List<Int>,
    initialCategoryOrder: List<String>,
    hasContent: Boolean,
) {
    // Selection
    var isMultiSelectMode by mutableStateOf(false)
    /** Group the current selection belongs to, so a grouped view cannot select across groups. */
    var selectedGroupKey by mutableStateOf<String?>(null)

    // Reorder
    var isReorderMode by mutableStateOf(false)
    var localOrder by mutableStateOf(initialOrder)
    var reorderScopePackages by mutableStateOf<Set<String>?>(null)
    var reorderScopeSourceUid by mutableStateOf<Int?>(null)
    var scopedSourceOrder by mutableStateOf<List<String>?>(null)
    /**
     * Snapshot taken on drag start when the dragged card is part of a multi-selection. Only the
     * dragged card moves during the drag; on drop these followers teleport next to it so the
     * group lands consolidated.
     */
    var reorderGroupFollowers by mutableStateOf<List<String>?>(null)
    /**
     * Packages that were selected when entering reorder mode, used to scroll the reordered list
     * back to the card the user long-pressed.
     */
    var reorderFocusPackages by mutableStateOf<Set<String>>(emptySet())

    // Category context bar. Kept as two flags so the app multi-select and the category bar stay
    // mutually exclusive at the footer slot
    var activeCategoryId by mutableStateOf<String?>(null)
    var activeSourceUid by mutableStateOf<Int?>(null)
    var isCategoryReorderMode by mutableStateOf(false)

    // Group order being dragged. Updated in place and flushed to preferences on reorder exit, so
    // the reorderable list indices stay in sync with the rendered groups mid-drag
    var localSourceGroupOrder by mutableStateOf(initialSourceGroupOrder)
    var localCategoryOrder by mutableStateOf(initialCategoryOrder)

    // Loading. hasEverLoaded latches so the shimmer never comes back on resume
    var hasEverLoaded by mutableStateOf(hasContent)
    var isLoading by mutableStateOf(!hasContent)

    // Dialogs
    var showHiddenAppsDialog by mutableStateOf(false)
    var showMoveCategoryDialog by mutableStateOf(false)
    var showBatchUninstallConfirm by mutableStateOf(false)
    var categoryNameRequest by mutableStateOf<CategoryNameRequest?>(null)
    var pendingDeleteCategoryId by mutableStateOf<String?>(null)
    var pendingUninstallItems by mutableStateOf<List<HomeAppItem>>(emptyList())

    /** True while any bar owns the footer slot, which the list pads for. */
    val isFooterBarVisible: Boolean
        get() = isMultiSelectMode || isReorderMode || isCategoryBarVisible

    val isCategoryBarVisible: Boolean
        get() = activeCategoryId != null || activeSourceUid != null || isCategoryReorderMode

    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedPackages.clear()
        selectedGroupKey = null
    }

    /** Leaves reorder mode and drops the scope it was entered with. [order] restores the list. */
    fun exitReorder(order: List<String>? = null) {
        isReorderMode = false
        reorderScopePackages = null
        reorderScopeSourceUid = null
        scopedSourceOrder = null
        reorderGroupFollowers = null
        selectedPackages.clear()
        selectedGroupKey = null
        if (order != null) localOrder = order
    }

    fun closeCategoryBar() {
        activeCategoryId = null
        activeSourceUid = null
        isCategoryReorderMode = false
    }

    /** Toggles a card in a grouped view, keeping the selection inside a single group. */
    fun toggleInGroup(packageName: String, groupKey: String) {
        if (selectedGroupKey != null && selectedGroupKey != groupKey) selectedPackages.clear()
        selectedGroupKey = groupKey
        selectedPackages.toggle(packageName)
        if (selectedPackages.isEmpty) selectedGroupKey = null
    }
}

@Composable
internal fun rememberHomeAppsSectionState(
    initialOrder: List<String>,
    initialSourceGroupOrder: List<Int>,
    initialCategoryOrder: List<String>,
    hasContent: Boolean,
): HomeAppsSectionState {
    val selectedPackages = rememberSelectionState<String>()
    return remember(selectedPackages) {
        HomeAppsSectionState(
            selectedPackages = selectedPackages,
            initialOrder = initialOrder,
            initialSourceGroupOrder = initialSourceGroupOrder,
            initialCategoryOrder = initialCategoryOrder,
            hasContent = hasContent,
        )
    }
}
