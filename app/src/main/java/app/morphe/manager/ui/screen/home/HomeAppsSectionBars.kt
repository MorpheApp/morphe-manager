/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The bar that occupies the footer slot: app multi-select and reorder, or the category context
 * actions. Only one is ever visible - [HomeAppsSectionState] keeps the modes exclusive - so they
 * live together here, out of the section body.
 */
@Composable
internal fun HomeAppsFooterBars(
    state: HomeAppsSectionState,
    apps: HomeAppListUi,
    appActions: HomeAppActions,
    searchState: HomeSearchState,
    reorderItems: List<HomeAppItem>,
    orderedItems: List<HomeAppItem>,
    itemsByPackage: Map<String, HomeAppItem>,
    groupedSelectionGroup: HomeCategoryGroup?,
    groupedSelectionPackages: Set<String>?,
    sourceGroups: List<HomeCategoryGroup>,
    isCustomCategoryView: Boolean,
    isSourceCategoryView: Boolean,
    listState: LazyListState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val homeAppItems = apps.visible
    val selectedPackages = state.selectedPackages

    // What "select all" covers: the scope being reordered, the group being selected in, or
    // everything when neither narrows it
    val activeAppScopePackages = state.reorderScopePackages ?: groupedSelectionPackages
    // Memoized - otherwise selection toggles refilter homeAppItems each pass
    val activeAppScopeItems = remember(
        activeAppScopePackages,
        groupedSelectionGroup,
        homeAppItems,
        reorderItems,
        state.isReorderMode
    ) {
        when {
            state.isReorderMode -> reorderItems
            groupedSelectionGroup != null -> groupedSelectionGroup.items
            activeAppScopePackages != null -> homeAppItems.filter { it.packageName in activeAppScopePackages }
            else -> homeAppItems
        }
    }
    val selectedAppItems = remember(selectedPackages.keys.toList(), homeAppItems) {
        val selected = selectedPackages.keys.toSet()
        homeAppItems.filter { it.packageName in selected }
    }
    val selectedInstalledItems = remember(selectedAppItems) {
        selectedAppItems.filter { it.isInstalledOnDevice }
    }
    val selectedReinstallItems = remember(selectedAppItems) {
        selectedAppItems.filter {
            !it.isInstalledOnDevice && it.hasSavedCopy && it.installedApp != null
        }
    }
    // The context slot offers a single verb, so it appears only when it applies to the whole
    // selection rather than to some of it
    val contextActionIsReinstall = selectedAppItems.isNotEmpty() &&
            selectedReinstallItems.size == selectedAppItems.size
    val contextActionIsUninstall = selectedAppItems.isNotEmpty() &&
            selectedInstalledItems.size == selectedAppItems.size
    val reinstallLabel = stringResource(R.string.reinstall)
    val uninstallLabel = stringResource(R.string.uninstall)

    MultiSelectBar(
        selectedCount = selectedPackages.size,
        totalCount = activeAppScopeItems.size,
        visible = state.isFooterBarVisible,
        isReorderMode = state.isReorderMode,
        onSelectAll = {
            selectedPackages.setAll(activeAppScopeItems.map { it.packageName })
        },
        onDeselectAll = {
            selectedPackages.clear()
            state.selectedGroupKey = null
        },
        onAction = {
            appActions.onHideMultiple(selectedPackages.keys.toSet())
            state.exitMultiSelect()
        },
        actionIcon = Icons.Outlined.VisibilityOff,
        actionContentDescription = stringResource(R.string.hide),
        actionDoneMessage = stringResource(R.string.hidden),
        onContextAction = when {
            contextActionIsReinstall -> {
                {
                    appActions.onReinstallMultiple(selectedReinstallItems)
                    state.exitMultiSelect()
                }
            }
            contextActionIsUninstall -> {
                {
                    state.pendingUninstallItems = selectedInstalledItems.toList()
                    state.showBatchUninstallConfirm = true
                }
            }
            else -> null
        },
        contextActionIcon = when {
            contextActionIsReinstall -> Icons.Outlined.InstallMobile
            contextActionIsUninstall -> Icons.Outlined.DeleteForever
            else -> null
        },
        contextActionContentDescription = when {
            contextActionIsReinstall -> reinstallLabel
            contextActionIsUninstall -> uninstallLabel
            else -> null
        },
        contextActionColors = if (contextActionIsUninstall) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        },
        onMoveToCategory = if (isCustomCategoryView) {
            { state.showMoveCategoryDialog = true }
        } else null,
        onPatchSelected = {
            appActions.onPatchMultiple(selectedAppItems)
            state.exitMultiSelect()
        },
        onCancel = { state.exitMultiSelect() },
        onEnterReorder = {
            groupedSelectionPackages?.let { pkgs ->
                selectedPackages.retain { it in pkgs }
            }
            state.reorderScopePackages = groupedSelectionPackages
            state.reorderScopeSourceUid = groupedSelectionGroup?.sourceUid
            val sourceOrder = groupedSelectionGroup
                ?.takeIf { it.sourceUid != null }
                ?.items
                ?.map { it.packageName }
            state.scopedSourceOrder = sourceOrder
            val focusTargets = selectedPackages.keys.toSet()
            // Grouped pre-scrolls below (before flipping mode) so the LazyColumn doesn't hold a
            // stale offset when items swap to the scoped list; flat defers to the LaunchedEffect
            // after flipping
            state.reorderFocusPackages = if (groupedSelectionPackages == null) focusTargets else emptySet()
            state.isMultiSelectMode = false
            searchState.onClose()
            groupedSelectionPackages?.let { scopePackages ->
                val scopedItems = sourceOrder
                    ?.mapNotNull { itemsByPackage[it] }
                    ?: orderedItems.filter { it.packageName in scopePackages }
                val focusIndex = scopedItems.indexOfFirst { it.packageName in focusTargets }
                scope.launch {
                    listState.scrollToItem(focusIndex.coerceAtLeast(0))
                    state.isReorderMode = true
                }
            } ?: run {
                state.isReorderMode = true
            }
        },
        onSaveOrder = {
            val sourceUid = state.reorderScopeSourceUid
            if (sourceUid != null) {
                appActions.onSaveSourceOrder(
                    sourceUid,
                    state.scopedSourceOrder ?: reorderItems.map { it.packageName }
                )
            } else {
                appActions.onSaveOrder(state.localOrder)
            }
            state.exitReorder()
        },
        onResetOrder = {
            val sourceUid = state.reorderScopeSourceUid
            if (sourceUid != null) {
                appActions.onResetSourceOrder(sourceUid)
            } else {
                appActions.onResetOrder()
            }
            state.exitReorder(homeAppItems.map { it.packageName })
        },
        onCancelReorder = { state.exitReorder(homeAppItems.map { it.packageName }) },
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
            searchState.onClose()
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
        modifier = modifier,
        showEditActions = state.activeSourceUid == null && !isSourceCategoryView
    )
}
