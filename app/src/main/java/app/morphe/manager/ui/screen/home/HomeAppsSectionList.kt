/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.util.toast
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

/**
 * The three shapes the apps list takes: dragging, grouped under headers, or a flat list.
 *
 * Kept out of the section composable so each stays small enough to read on its own - and so the
 * list builder captures one state object instead of the two dozen values these used to close over.
 */

/** Cards while reordering: only drag handles are live, taps just adjust the selection. */
internal fun LazyListScope.reorderableAppCards(
    state: HomeAppsSectionState,
    items: List<HomeAppItem>,
    reorderableState: ReorderableLazyListState,
    haptic: HapticFeedback,
) {
    val selectedPackages = state.selectedPackages
    itemsIndexed(
        items = items,
        key = { _, item -> item.id }
    ) { _, item ->
        ReorderableItem(reorderableState, key = item.id) { itemIsDragging ->
            DynamicAppCard(
                item = item,
                isLoading = false,
                onAppClick = {
                    if (selectedPackages.isNotEmpty) {
                        selectedPackages.toggle(item.id)
                    }
                },
                onHide = {},
                onShowPatches = {},
                showGestureHint = false,
                onGestureHintShown = {},
                isSelected = selectedPackages.contains(item.id),
                isMultiSelectMode = selectedPackages.isNotEmpty,
                onLongPress = { selectedPackages.toggle(item.id) },
                swipeActionsEnabled = false,
                dragHandleModifier = Modifier.draggableHandle(
                    onDragStarted = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val currentPkg = item.id
                        val selected = selectedPackages.keys
                        state.reorderGroupFollowers = if (selected.size > 1 && currentPkg in selected) {
                            (state.scopedSourceOrder ?: state.localOrder)
                                .filter { it in selected && it != currentPkg }
                        } else null
                    },
                    onDragStopped = {
                        val followers = state.reorderGroupFollowers
                        state.reorderGroupFollowers = null
                        if (followers.isNullOrEmpty()) return@draggableHandle
                        val currentOrder = state.scopedSourceOrder ?: state.localOrder
                        val withoutFollowers = currentOrder.filter { it !in followers }
                        val dropIdx = withoutFollowers.indexOf(item.id)
                        if (dropIdx < 0) return@draggableHandle
                        val nextOrder = buildList {
                            addAll(withoutFollowers.take(dropIdx + 1))
                            addAll(followers)
                            addAll(withoutFollowers.drop(dropIdx + 1))
                        }
                        if (state.scopedSourceOrder != null) {
                            state.scopedSourceOrder = nextOrder
                        } else {
                            state.localOrder = nextOrder
                        }
                    }
                ),
                modifier = Modifier.zIndex(if (itemIsDragging) 1f else 0f)
            )
        }
    }
}

/** Category or source headers with their cards; headers themselves drag in reorder mode. */
internal fun LazyListScope.groupedAppCards(
    state: HomeAppsSectionState,
    groups: List<HomeCategoryGroup>,
    appGrouping: HomeAppCategoryViewMode,
    firstFilteredPackage: String?,
    showGestureHint: Boolean,
    appActions: HomeAppActions,
    categoryReorderableState: ReorderableLazyListState,
    haptic: HapticFeedback,
    context: Context,
    categoryActionsUnavailableToast: String,
) {
    val selectedPackages = state.selectedPackages
    groups.forEach { group ->
        val headerKey = "category_${group.id ?: "uncategorized"}"
        item(key = headerKey) {
            // Long-press is gated while any other footer mode is already using the slot
            val isFooterBusy = state.isMultiSelectMode ||
                    state.isReorderMode ||
                    state.isCategoryReorderMode
            val headerLongPress: (() -> Unit)? = when {
                isFooterBusy -> null
                group.editable -> { -> state.activeCategoryId = group.id }
                group.sourceUid != null -> { -> state.activeSourceUid = group.sourceUid }
                else -> { -> context.toast(categoryActionsUnavailableToast) }
            }
            val headerContent: @Composable ((@Composable () -> Unit)?) -> Unit = { dragHandle ->
                HomeCategoryHeader(
                    group = group,
                    onToggle = {
                        val sourceUid = group.sourceUid
                        if (sourceUid != null) {
                            appActions.onToggleSourceGroupCollapsed(sourceUid)
                        } else {
                            // null id = uncategorized bucket
                            appActions.onToggleCategoryCollapsed(group.id)
                        }
                    },
                    onLongPress = headerLongPress,
                    modifier = Modifier.animateItem(),
                    dragHandle = dragHandle
                )
            }

            val canReorderHeader = when (appGrouping) {
                HomeAppCategoryViewMode.SOURCES -> group.sourceUid != null
                HomeAppCategoryViewMode.CUSTOM -> group.editable
                HomeAppCategoryViewMode.ALL_APPS -> false
            }
            if (canReorderHeader && state.isCategoryReorderMode) {
                ReorderableItem(categoryReorderableState, key = headerKey) { _ ->
                    headerContent {
                        CategoryHeaderDragHandle(
                            modifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        )
                    }
                }
            } else {
                headerContent(null)
            }
        }

        if (!group.collapsed) {
            items(
                items = group.items,
                key = { item -> "category_${group.id ?: "uncategorized"}_${item.id}" }
            ) { item ->
                val groupKey = group.selectionKey()
                val isSelected = selectedPackages.contains(item.id) &&
                        (state.selectedGroupKey == null || state.selectedGroupKey == groupKey)
                DynamicAppCard(
                    item = item,
                    isLoading = state.isLoading,
                    onAppClick = {
                        if (state.isMultiSelectMode) {
                            state.toggleInGroup(item.id, groupKey)
                        } else {
                            appActions.onAppClick(item)
                        }
                    },
                    onHide = { appActions.onHideApp(item.id) },
                    onShowPatches = { appActions.onShowPatches(item) },
                    showGestureHint = item.id == firstFilteredPackage && showGestureHint,
                    onGestureHintShown = appActions.onGestureHintShown,
                    isSelected = isSelected,
                    isMultiSelectMode = state.isMultiSelectMode,
                    onLongPress = {
                        // Skip so the category bar doesn't overlap with app multi-select
                        if (!state.isCategoryBarVisible) {
                            state.isMultiSelectMode = true
                            state.toggleInGroup(item.id, groupKey)
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

/** The plain All-apps list, where TalkBack can also move a card one position at a time. */
internal fun LazyListScope.flatAppCards(
    state: HomeAppsSectionState,
    items: List<HomeAppItem>,
    showGestureHint: Boolean,
    directReorderAllowed: Boolean,
    appActions: HomeAppActions,
    haptic: HapticFeedback,
    onboardingState: OnboardingState?,
    moveAnnouncementFormat: String,
    onMoveAnnouncement: (String) -> Unit,
) {
    val selectedPackages = state.selectedPackages
    itemsIndexed(
        items = items,
        key = { _, item -> item.id }
    ) { index, item ->
        // Moves the card one step and reports where it landed, so a screen reader user hears
        // the result of an action they cannot see
        fun moveBy(offset: Int, announcePosition: Int) {
            val current = state.localOrder.toMutableList()
            val from = current.indexOf(item.id)
            val target = from + offset
            if (from < 0 || target !in current.indices) return
            val moved = current.removeAt(from)
            current.add(target, moved)
            state.localOrder = current
            appActions.onSaveOrder(current)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onMoveAnnouncement(
                moveAnnouncementFormat.format(item.displayName, from + announcePosition, current.size)
            )
        }

        DynamicAppCard(
            item = item,
            isLoading = state.isLoading,
            onAppClick = {
                if (state.isMultiSelectMode) {
                    // In multi-select mode taps toggle selection
                    selectedPackages.toggle(item.id)
                } else {
                    appActions.onAppClick(item)
                }
            },
            onHide = { appActions.onHideApp(item.id) },
            onShowPatches = { appActions.onShowPatches(item) },
            // Hint plays only on the first card
            showGestureHint = index == 0 && showGestureHint,
            onGestureHintShown = appActions.onGestureHintShown,
            isSelected = selectedPackages.contains(item.id),
            isMultiSelectMode = state.isMultiSelectMode,
            onLongPress = {
                // Long-press enters multi-select and toggles this card
                state.isMultiSelectMode = true
                selectedPackages.toggle(item.id)
            },
            onMoveUp = if (directReorderAllowed && index > 0) {
                { moveBy(offset = -1, announcePosition = 0) }
            } else null,
            onMoveDown = if (directReorderAllowed && index < items.size - 1) {
                { moveBy(offset = 1, announcePosition = 2) }
            } else null,
            modifier = Modifier
                .animateItem()
                .then(
                    if (index == 0 && onboardingState != null)
                        Modifier.onGloballyPositioned { coords ->
                            onboardingState.firstAppCardBounds = coords.boundsInWindow()
                        }
                    else Modifier
                )
        )
    }
}
