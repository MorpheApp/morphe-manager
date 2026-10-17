/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.ActionTone
import app.morphe.manager.ui.screen.shared.ListOptionSection
import app.morphe.manager.ui.screen.shared.ListOptionsPanel
import app.morphe.manager.ui.screen.shared.SelectionAction
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.ui.screen.shared.listOptionSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Hands the apps section's footer bar to the screen that draws it. The bar acts on what the
 * section holds, but docks to the bottom edge of the screen over controls a selection has no use
 * for, which the section cannot reach from inside its own bounds.
 */
@Stable
internal class HomeFooterBarHost {
    /** The bar the section wants drawn, or null while no section is on screen. */
    var bar by mutableStateOf<(@Composable (Modifier) -> Unit)?>(null)

    /** Height of the bar as last measured. One that has slid out keeps the last. */
    var barHeightPx by mutableIntStateOf(0)

    // Window y of the bottom of the area the list lays out in, and of the edge the bar docks to.
    // The bar covers the span between them anyway, so the list only has to clear the rest of it
    var contentBottom by mutableFloatStateOf(0f)
    var dockBottom by mutableFloatStateOf(0f)

    /** How far the bar reaches into the area the list lays out in. */
    val listOverlapPx: Float
        get() = (barHeightPx - (dockBottom - contentBottom).coerceAtLeast(0f)).coerceAtLeast(0f)
}

/** Draws the bar [host] carries, docked to the bottom of whatever [modifier] aligns it to. */
@Composable
internal fun HomeFooterBarDock(host: HomeFooterBarHost, modifier: Modifier = Modifier) {
    val bar = host.bar ?: return
    bar(
        modifier
            .onGloballyPositioned { host.dockBottom = it.boundsInWindow().bottom }
            .onSizeChanged { size ->
                // A bar that has slid out reports no height, and the list keeps clearing the
                // last one until it lets go of the space
                if (size.height > 0) host.barHeightPx = size.height
            }
    )
}

/**
 * The bar that occupies the footer slot: app multi-select and reorder, the category context
 * actions, or the options the list is arranged by. Only one is ever visible - [HomeAppsSectionState]
 * keeps the modes exclusive - so they live together here, out of the section body.
 */
@Composable
internal fun HomeAppsFooterBars(
    state: HomeAppsSectionState,
    apps: HomeAppListUi,
    appActions: HomeAppActions,
    searchState: HomeSearchState,
    listedItems: List<HomeAppItem>,
    reorderItems: List<HomeAppItem>,
    orderedItems: List<HomeAppItem>,
    itemsByPackage: Map<String, HomeAppItem>,
    groupedSelectionGroup: HomeCategoryGroup?,
    groupedSelectionPackages: Set<String>?,
    sourceGroups: List<HomeCategoryGroup>,
    isCustomCategoryView: Boolean,
    isSourceCategoryView: Boolean,
    listState: LazyListState,
    leadingItemCount: Int,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val homeAppItems = apps.visible
    val selectedPackages = state.selectedPackages

    // What "select all" covers: the scope being reordered, the group being selected in, or
    // whatever the search and filter left on screen when neither narrows it
    val activeAppScopePackages = state.reorderScopePackages ?: groupedSelectionPackages
    // Memoized - otherwise selection toggles refilter listedItems each pass
    val activeAppScopeItems = remember(
        activeAppScopePackages,
        groupedSelectionGroup,
        listedItems,
        reorderItems,
        state.isReorderMode
    ) {
        when {
            state.isReorderMode -> reorderItems
            groupedSelectionGroup != null -> groupedSelectionGroup.items
            activeAppScopePackages != null -> listedItems.filter { it.id in activeAppScopePackages }
            else -> listedItems
        }
    }
    val selectedAppItems = remember(selectedPackages.keys.toList(), homeAppItems) {
        val selected = selectedPackages.keys.toSet()
        homeAppItems.filter { it.id in selected }
    }
    val selectedInstalledItems = remember(selectedAppItems) {
        // Apps that are on the device but were never patched here have nothing Morphe can
        // uninstall, so they must not put the selection into the uninstall verb
        selectedAppItems.filter { it.isInstalledOnDevice && it.installedApp != null }
    }
    val selectedReinstallItems = remember(selectedAppItems) {
        selectedAppItems.filter {
            !it.isInstalledOnDevice && it.hasSavedCopy && it.installedApp != null
        }
    }
    // Reinstall and uninstall are offered only when they apply to the whole selection rather
    // than to some of it
    val contextActionIsReinstall = selectedAppItems.isNotEmpty() &&
            selectedReinstallItems.size == selectedAppItems.size
    val contextActionIsUninstall = selectedAppItems.isNotEmpty() &&
            selectedInstalledItems.size == selectedAppItems.size
    val messages = koinInject<UserMessages>()
    val reinstallLabel = stringResource(R.string.reinstall)
    val uninstallLabel = stringResource(R.string.uninstall)
    val patchSelectedLabel = stringResource(
        if (apps.batchQueuePending) R.string.batch_queue_add_action else R.string.batch_patch_action
    )
    val patchSourcesLabel = stringResource(R.string.sources_management_title)
    val moveToCategoryLabel = stringResource(R.string.home_category_move_to)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val hideLabel = stringResource(R.string.hide)
    val hiddenMessage = stringResource(R.string.hidden)

    val enterReorder: () -> Unit = {
        groupedSelectionPackages?.let { pkgs ->
            selectedPackages.retain { it in pkgs }
        }
        state.reorderScopePackages = groupedSelectionPackages
        state.reorderScopeSourceUid = groupedSelectionGroup?.sourceUid
        val sourceOrder = groupedSelectionGroup
            ?.takeIf { it.sourceUid != null }
            ?.items
            ?.map { it.id }
        state.scopedSourceOrder = sourceOrder
        val focusTargets = selectedPackages.keys.toSet()
        // Grouped pre-scrolls below (before flipping mode) so the LazyColumn doesn't hold a
        // stale offset when items swap to the scoped list; flat defers to the LaunchedEffect
        // after flipping
        state.reorderFocusPackages = if (groupedSelectionPackages == null) focusTargets else emptySet()
        // A lone card is not a group, so keeping it highlighted only dims everything else for nothing
        if (selectedPackages.size == 1) selectedPackages.clear()
        state.isMultiSelectMode = false
        searchState.onClear()
        groupedSelectionPackages?.let { scopePackages ->
            val scopedItems = sourceOrder
                ?.mapNotNull { itemsByPackage[it] }
                ?: orderedItems.filter { it.id in scopePackages }
            val focusIndex = scopedItems.indexOfFirst { it.id in focusTargets }
            scope.launch {
                listState.scrollToItem(focusIndex.coerceAtLeast(0) + leadingItemCount)
                state.isReorderMode = true
            }
        } ?: run {
            state.isReorderMode = true
        }
    }

    val actions = buildList {
        add(
            SelectionAction(
                icon = if (apps.batchQueuePending) Icons.AutoMirrored.Outlined.PlaylistAdd else Icons.Outlined.AutoFixHigh,
                label = patchSelectedLabel,
                onClick = {
                    appActions.onPatchMultiple(selectedAppItems)
                    state.exitMultiSelect()
                },
                tone = ActionTone.Primary
            )
        )
        // Next to "Patch selected" rather than next to "Hide": both answer what patching these
        // apps does, while "Hide" is about this screen
        add(
            SelectionAction(
                icon = Icons.Outlined.Source,
                label = patchSourcesLabel,
                onClick = { state.showPatchSourcesDialog = true },
                tone = ActionTone.Secondary
            )
        )
        if (isCustomCategoryView) {
            add(
                SelectionAction(
                    icon = Icons.Outlined.FolderOpen,
                    label = moveToCategoryLabel,
                    onClick = { state.showMoveCategoryDialog = true }
                )
            )
        }
        add(
            SelectionAction(
                icon = Icons.Outlined.Reorder,
                label = reorderListLabel,
                onClick = enterReorder
            )
        )
        when {
            contextActionIsReinstall -> add(
                SelectionAction(
                    icon = Icons.Outlined.InstallMobile,
                    label = reinstallLabel,
                    onClick = {
                        appActions.onReinstallMultiple(selectedReinstallItems)
                        state.exitMultiSelect()
                    }
                )
            )
            contextActionIsUninstall -> add(
                SelectionAction(
                    icon = Icons.Outlined.DeleteForever,
                    label = uninstallLabel,
                    onClick = {
                        state.pendingUninstallItems = selectedInstalledItems.toList()
                        state.showBatchUninstallConfirm = true
                    },
                    tone = ActionTone.Destructive
                )
            )
        }
        add(
            SelectionAction(
                icon = Icons.Outlined.VisibilityOff,
                label = hideLabel,
                onClick = messages.confirming(hiddenMessage) {
                    appActions.onHideMultiple(selectedPackages.keys.toSet())
                    state.exitMultiSelect()
                },
                tone = ActionTone.Destructive
            )
        )
    }

    MultiSelectBar(
        selectedCount = selectedPackages.size,
        totalCount = activeAppScopeItems.size,
        visible = state.isMultiSelectMode || state.isReorderMode,
        onSelectAll = {
            selectedPackages.setAll(activeAppScopeItems.map { it.id })
        },
        onDeselectAll = {
            selectedPackages.clear()
            state.selectedGroupKey = null
        },
        actions = actions,
        onCancel = { state.exitMultiSelect() },
        reorder = MultiSelectReorder(
            isActive = state.isReorderMode,
            onSave = {
                val sourceUid = state.reorderScopeSourceUid
                if (sourceUid != null) {
                    appActions.onSaveSourceOrder(
                        sourceUid,
                        state.scopedSourceOrder ?: reorderItems.map { it.id }
                    )
                } else {
                    appActions.onSaveOrder(state.localOrder)
                }
                state.exitReorder()
            },
            onReset = {
                val sourceUid = state.reorderScopeSourceUid
                if (sourceUid != null) {
                    appActions.onResetSourceOrder(sourceUid)
                } else {
                    appActions.onResetOrder()
                }
                state.exitReorder(homeAppItems.map { it.id })
            },
            onCancel = { state.exitReorder(homeAppItems.map { it.id }) }
        ),
        modifier = modifier
    )

    val activeCategoryTitle = state.activeCategoryId?.let { id ->
        apps.categoryState.categories.firstOrNull { it.id == id }?.name
    }
    val activeSourceTitle = state.activeSourceUid?.let { uid ->
        sourceGroups.firstOrNull { it.sourceUid == uid }?.title
    }
    CategoryActionBar(
        activeCategoryTitle = activeCategoryTitle ?: activeSourceTitle,
        visible = state.isCategoryBarVisible,
        isReorderMode = state.isCategoryReorderMode,
        onRename = {
            val category = apps.categoryState.categories
                .firstOrNull { it.id == state.activeCategoryId }
            if (category != null) {
                state.categoryNameRequest = CategoryNameRequest(category)
            }
            state.activeCategoryId = null
            state.activeSourceUid = null
        },
        onDelete = {
            // Hand off to the confirmation dialog; actual deletion runs only if the user
            // confirms. Close the bar so the dialog isn't shadowed.
            state.pendingDeleteCategoryId = state.activeCategoryId
            state.activeCategoryId = null
            state.activeSourceUid = null
        },
        onEnterReorder = {
            // localSourceGroupOrder is kept in sync by the section's LaunchedEffect, so it
            // already reflects the current source list when reorder begins
            state.activeCategoryId = null
            state.activeSourceUid = null
            searchState.onClear()
            state.isCategoryReorderMode = true
        },
        onExitReorder = {
            if (isSourceCategoryView) {
                appActions.onSaveSourceGroupOrder(state.localSourceGroupOrder)
            } else {
                appActions.onSaveCategoryOrder(state.localCategoryOrder)
            }
            state.isCategoryReorderMode = false
        },
        onCancel = {
            state.activeCategoryId = null
            state.activeSourceUid = null
        },
        onBack = { state.closeCategoryBar() },
        modifier = modifier,
        showEditActions = state.activeSourceUid == null && !isSourceCategoryView
    )

    ListOptionsPanel(
        visible = state.isListOptionsOpen,
        sections = homeListOptions(apps, appActions),
        onClose = { state.isListOptionsOpen = false },
        modifier = modifier
    )
}

/**
 * How the home list is sorted, filtered and grouped: what its options panel offers, and what marks
 * the sort button once any of it is off its default.
 */
@Composable
internal fun homeListOptions(apps: HomeAppListUi, appActions: HomeAppActions): List<ListOptionSection<*>> = listOf(
    listOptionSection(
        R.string.sort, Icons.AutoMirrored.Outlined.Sort, apps.sortMode, HomeAppSortMode.RECOMMENDED,
        appActions.onSortModeChange
    ),
    listOptionSection(
        R.string.filter, Icons.Outlined.FilterList, apps.filterMode, HomeAppFilterMode.ALL,
        appActions.onFilterModeChange
    ),
    listOptionSection(
        R.string.group, Icons.Outlined.Category, apps.categoryViewMode, HomeAppCategoryViewMode.ALL_APPS,
        appActions.onCategoryViewModeChange
    )
)
