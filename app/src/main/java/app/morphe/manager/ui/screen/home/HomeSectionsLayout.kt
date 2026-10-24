/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategoryState
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeAppSourceGroup
import app.morphe.manager.util.KnownApps
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds

internal fun HomeCategoryGroup.selectionKey(): String =
    sourceUid?.let { "source_$it" } ?: id?.let { "category_$it" } ?: "uncategorized"

/** Visible and hidden app lists with their loading state. */
@Immutable
data class HomeAppListUi(
    val visible: List<HomeAppItem>,
    val hidden: List<HomeAppItem>,
    val installedAppsLoading: Boolean,
    val showGestureHint: Boolean,
    val sortMode: HomeAppSortMode,
    val filterMode: HomeAppFilterMode,
    val categoryState: HomeAppCategoryState,
    val categoryViewMode: HomeAppCategoryViewMode,
    val sourceGroups: List<HomeAppSourceGroup>,
    /** Whether a batch queue is waiting to be started, which patching a selection adds to. */
    val batchQueuePending: Boolean = false,
    /** Whether the list ends on the card leading to the community patches. */
    val showMoreAppsCard: Boolean = false
)

/** Callbacks fired from an app card. */
@Stable
class HomeAppActions(
    val onAppClick: (HomeAppItem) -> Unit,
    val onHideApp: (String) -> Unit,
    val onHideMultiple: (Set<String>) -> Unit,
    val onUninstallMultiple: (List<HomeAppItem>) -> Unit,
    val onReinstallMultiple: (List<HomeAppItem>) -> Unit,
    val onPatchMultiple: (List<HomeAppItem>) -> Unit,
    val onUnhideApp: (String) -> Unit,
    val onShowPatches: (HomeAppItem) -> Unit,
    val onGestureHintShown: () -> Unit,
    val onSaveOrder: (List<String>) -> Unit,
    val onSaveSourceOrder: (Int, List<String>) -> Unit,
    val onResetOrder: () -> Unit,
    val onResetSourceOrder: (Int) -> Unit,
    val onSaveSourceGroupOrder: (List<Int>) -> Unit,
    val onSortModeChange: (HomeAppSortMode) -> Unit,
    val onFilterModeChange: (HomeAppFilterMode) -> Unit,
    val onCategoryViewModeChange: (HomeAppCategoryViewMode) -> Unit,
    val onCreateCategory: (String) -> String,
    val onRenameCategory: (String, String) -> Unit,
    val onDeleteCategory: (String) -> Unit,
    val onSaveCategoryOrder: (List<String>) -> Unit,
    val onToggleCategoryCollapsed: (String?) -> Unit,
    val onToggleSourceGroupCollapsed: (Int) -> Unit,
    val onAssignAppsToCategory: (Set<String>, String?) -> Unit
)

/** Callbacks of what stands around the apps: the header, the greeting, the empty state and the card closing the list. */
@Stable
class HomeChromeActions(
    val onOtherAppsClick: () -> Unit,
    val onOpenSources: () -> Unit,
    val onRefreshGreeting: (() -> Unit)?,
    val onHideMoreAppsCard: () -> Unit
)

/**
 * The apps page of the main screen: the list of apps under the page's header, opened by the
 * greeting, with the bars of a selection or a reorder docked over its lower edge.
 *
 * @param state The list's selection and reorder, held by the screen, whose navigation gives way to
 *   the docked bars.
 * @param bottomBarHeight Height of the navigation floating over the page, which the list ends clear of.
 * @param alerts The alerts of the main screen, those swiped aside brought back from the header.
 * @param onHeaderHeightChange Told the header's height, for the alerts floating over the page below it.
 */
@Composable
internal fun SectionsLayout(
    apps: HomeAppListUi,
    appActions: HomeAppActions,
    chromeActions: HomeChromeActions,
    greetingMessage: String?,
    state: HomeAppsSectionState,
    bottomBarHeight: Dp,
    alerts: HomeAlerts,
    onboardingState: OnboardingState? = null,
    onHeaderHeightChange: (Dp) -> Unit = {}
) {
    val windowSize = rememberWindowSize()
    val search = rememberSearchFieldState()
    // Registered before the footer bars' own back handling, so an open bar takes priority
    SearchFieldBackHandler(search)
    val footerBar = remember { HomeFooterBarHost() }
    // The options share the footer slot, so they make way for a selection or a reorder started
    // under them
    LaunchedEffect(state.isEditing) {
        if (state.isEditing) state.isListOptionsOpen = false
    }

    // Opens the list rather than sitting over it, so it scrolls away with the cards
    val greeting: (@Composable () -> Unit)? = greetingMessage?.takeIf { it.isNotEmpty() }?.let { message ->
        { GreetingSection(message = message, onRefresh = chromeActions.onRefreshGreeting) }
    }

    PinnedHeaderPage(
        header = {
            HomeHeader(
                apps = apps,
                appActions = appActions,
                search = search,
                alerts = alerts,
                sortEnabled = !state.isEditing,
                sortOpen = state.isListOptionsOpen,
                onSortClick = { state.isListOptionsOpen = !state.isListOptionsOpen },
                onPatchFileClick = chromeActions.onOtherAppsClick,
                tour = onboardingState
            )
        },
        scrollState = state.listState,
        bottomBar = {
            HomeFooterBarDock(
                host = footerBar,
                modifier = Modifier.fillMaxWidth()
            )
        },
        onHeaderHeightChange = onHeaderHeightChange
    ) { insets ->
        MainAppsSection(
            apps = apps,
            appActions = appActions,
            state = state,
            search = search,
            onOpenSources = chromeActions.onOpenSources,
            onHideMoreAppsCard = chromeActions.onHideMoreAppsCard,
            footerBar = footerBar,
            footerBarHeight = insets.bottom,
            bottomBarHeight = bottomBarHeight,
            itemSpacing = windowSize.itemSpacing,
            // The gutters keep the scrollbar at the list's edge clear of the cards
            horizontalPadding = insets.horizontal,
            onboardingState = onboardingState,
            showFadeOverlay = !isLandscape(),
            header = greeting,
            topPadding = insets.top + Defaults.SpaceMedium
        )
    }
}

/**
 * Section 2: Greeting message.
 */
@Composable
private fun GreetingSection(
    message: String,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null
) {
    val refreshLabel = stringResource(R.string.refresh)
    Box(
        modifier = modifier.then(
            if (onRefresh != null) Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(refreshLabel) { onRefresh(); true }
                )
            } else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        CrossfadeText(
            text = message,
            transitionSpec = Animations.slideUpContentTransitionSpec,
            // A step below a headline, so a long phrase still fits in a line or two
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            label = "greeting_transition"
        )
    }
}

/**
 * Section 3: Dynamic scrollable app buttons list.
 */
@SuppressLint("FrequentlyChangingValue")
@Composable
internal fun MainAppsSection(
    apps: HomeAppListUi,
    appActions: HomeAppActions,
    state: HomeAppsSectionState,
    search: SearchFieldState,
    onOpenSources: () -> Unit,
    onHideMoreAppsCard: () -> Unit,
    footerBar: HomeFooterBarHost,
    modifier: Modifier = Modifier,
    // Height of the bar docked over the list's lower edge, such as a selection's
    footerBarHeight: Dp = 0.dp,
    // Height of the navigation floating over the list's lower edge
    bottomBarHeight: Dp = 0.dp,
    itemSpacing: Dp = 16.dp,
    horizontalPadding: Dp = 0.dp,
    onboardingState: OnboardingState? = null,
    showFadeOverlay: Boolean = true,
    // Opens the list and scrolls away with it, such as the greeting
    header: (@Composable () -> Unit)? = null,
    // Room above the first row, for the page's header the list scrolls on under
    topPadding: Dp = 0.dp
) {
    // Aliases for values used many times in the body
    val homeAppItems = apps.visible
    val hiddenAppItems = apps.hidden
    val searchQuery = search.query
    val filterMode = apps.filterMode
    val onClearFilter = { appActions.onFilterModeChange(HomeAppFilterMode.ALL) }
    val isFilterActive = filterMode.isActive
    val isFilteringList = searchQuery.isNotBlank() || isFilterActive
    val appGrouping = apps.categoryViewMode
    val isGroupedAppView = appGrouping != HomeAppCategoryViewMode.ALL_APPS
    val isCustomCategoryView = appGrouping == HomeAppCategoryViewMode.CUSTOM

    val selectedPackages = state.selectedPackages
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val isSourceCategoryView = appGrouping == HomeAppCategoryViewMode.SOURCES

    // Clears whichever bar reaches into the list: the floating navigation, or the docked bar that
    // takes its place
    val footerBarOverlap = maxOf(footerBarHeight, bottomBarHeight)

    // Retire stale header action state when switching grouping modes.
    LaunchedEffect(appGrouping) { state.closeCategoryBar() }
    LaunchedEffect(apps.categoryState.categories) {
        val currentIds = apps.categoryState.categories.mapTo(mutableSetOf()) { it.id }
        if (state.activeCategoryId != null && state.activeCategoryId !in currentIds) {
            state.activeCategoryId = null
        }
    }
    // Sync selection and local order with current item list
    LaunchedEffect(homeAppItems) {
        val currentPackages = homeAppItems.mapTo(mutableSetOf()) { it.id }
        selectedPackages.retain { it in currentPackages }
        if (selectedPackages.isEmpty) {
            state.isMultiSelectMode = false
            state.selectedGroupKey = null
        }

        if (!state.isReorderMode) {
            state.localOrder = homeAppItems.map { it.id }
        } else {
            val pkgSet = homeAppItems.mapTo(mutableSetOf()) { it.id }
            state.scopedSourceOrder = state.scopedSourceOrder?.filter { it in pkgSet }
            val kept = state.localOrder.filter { it in pkgSet }
            val keptSet = kept.toSet()
            val added = pkgSet.filter { it !in keptSet }
            state.localOrder = kept + added
        }
    }

    LaunchedEffect(apps.installedAppsLoading, homeAppItems.size, hiddenAppItems.size) {
        val hasItems = homeAppItems.isNotEmpty() || hiddenAppItems.isNotEmpty()
        if (hasItems) state.hasEverLoaded = true

        val shouldShowShimmer = !state.hasEverLoaded && apps.installedAppsLoading
        if (shouldShowShimmer) {
            state.isLoading = true
        } else {
            // Small delay so Compose has one frame to lay out the real cards before the
            // shimmer fades out - prevents a single-frame empty gap.
            if (state.isLoading) delay(50.milliseconds)
            state.isLoading = false
        }
    }

    // Placeholder gradients for cold-start shimmer
    val placeholderGradients = remember { KnownApps.DEFAULT_SHIMMER_GRADIENTS }

    // Resolved outside the LazyColumn DSL scope since @Composable calls aren't allowed there
    val messages = koinInject<UserMessages>()
    val categoryActionsUnavailableMessage = stringResource(R.string.home_category_actions_unavailable)

    HomeAppsSectionDialogs(
        state = state,
        apps = apps,
        appActions = appActions
    )

    fun HomeAppItem.matchesSearch(): Boolean =
        searchQuery.isBlank() ||
                displayName.contains(searchQuery, ignoreCase = true) ||
                packageName.contains(searchQuery, ignoreCase = true) ||
                id.contains(searchQuery, ignoreCase = true)

    // Filtered visible items based on selected status filter and search query
    val filteredItems = remember(homeAppItems, searchQuery, filterMode) {
        homeAppItems.filter { item ->
            filterMode.matches(item) && item.matchesSearch()
        }
    }

    // Hidden items surface only while searching, and still obey the status filter
    val filteredHiddenItems = remember(hiddenAppItems, searchQuery, filterMode) {
        if (searchQuery.isBlank()) emptyList()
        else hiddenAppItems.filter { item ->
            filterMode.matches(item) && item.matchesSearch()
        }
    }

    // Only the grouping on screen is built, since the query rebuilds it on every keystroke
    val uncategorizedTitle = stringResource(R.string.home_category_uncategorized)
    val categoryGroups = remember(
        isCustomCategoryView,
        filteredItems,
        apps.categoryState,
        isFilteringList,
        uncategorizedTitle
    ) {
        if (!isCustomCategoryView) return@remember emptyList()
        buildHomeCategoryGroups(
            items = filteredItems,
            categoryState = apps.categoryState,
            uncategorizedTitle = uncategorizedTitle,
            ignoreCollapsed = isFilteringList
        )
    }
    val sourceCategoryGroups = remember(
        isSourceCategoryView,
        filteredItems,
        apps.sourceGroups,
        apps.categoryState.uncategorizedCollapsed,
        isFilteringList,
        uncategorizedTitle
    ) {
        if (!isSourceCategoryView) return@remember emptyList()
        buildHomeSourceGroups(
            items = filteredItems,
            sourceGroups = apps.sourceGroups,
            uncategorizedTitle = uncategorizedTitle,
            uncategorizedCollapsed = apps.categoryState.uncategorizedCollapsed,
            ignoreCollapsed = isFilteringList
        )
    }
    LaunchedEffect(apps.sourceGroups, state.isCategoryReorderMode, isSourceCategoryView) {
        if (state.isCategoryReorderMode && isSourceCategoryView) return@LaunchedEffect
        val sourceUids = apps.sourceGroups.map { it.uid }
        val kept = state.localSourceGroupOrder.filter { it in sourceUids }
        val added = sourceUids.filter { it !in kept }
        state.localSourceGroupOrder = kept + added
    }
    val displayedSourceCategoryGroups = remember(
        sourceCategoryGroups,
        state.localSourceGroupOrder,
        state.isCategoryReorderMode,
        isSourceCategoryView
    ) {
        if (!state.isCategoryReorderMode || !isSourceCategoryView) {
            sourceCategoryGroups
        } else {
            val byUid = sourceCategoryGroups.mapNotNull { group ->
                group.sourceUid?.let { uid -> uid to group }
            }.toMap()
            val orderedGroups = state.localSourceGroupOrder.mapNotNull { byUid[it] }
            val orderedUids = orderedGroups.mapNotNullTo(mutableSetOf()) { it.sourceUid }
            orderedGroups + sourceCategoryGroups.filter { group ->
                val uid = group.sourceUid
                uid == null || uid !in orderedUids
            }
        }
    }
    LaunchedEffect(apps.categoryState.categories, state.isCategoryReorderMode, isSourceCategoryView) {
        if (state.isCategoryReorderMode && !isSourceCategoryView) return@LaunchedEffect
        val categoryIds = apps.categoryState.categories.map { it.id }
        val kept = state.localCategoryOrder.filter { it in categoryIds }
        val added = categoryIds.filter { it !in kept }
        state.localCategoryOrder = kept + added
    }
    val displayedCategoryGroups = remember(
        categoryGroups,
        state.localCategoryOrder,
        state.isCategoryReorderMode,
        isSourceCategoryView
    ) {
        if (!state.isCategoryReorderMode || isSourceCategoryView) {
            categoryGroups
        } else {
            val byId = categoryGroups.mapNotNull { group ->
                group.id?.let { id -> id to group }
            }.toMap()
            val orderedGroups = state.localCategoryOrder.mapNotNull { byId[it] }
            val orderedIds = orderedGroups.mapNotNullTo(mutableSetOf()) { it.id }
            orderedGroups + categoryGroups.filter { group ->
                val id = group.id
                id == null || id !in orderedIds
            }
        }
    }
    LaunchedEffect(sourceCategoryGroups) {
        val currentUids = sourceCategoryGroups.mapNotNullTo(mutableSetOf()) { it.sourceUid }
        if (state.activeSourceUid != null && state.activeSourceUid !in currentUids) {
            state.activeSourceUid = null
        }
    }
    // Cheap key - the block only reads firstSelectedPackage, so passing the full keys
    // list would allocate on every recomp
    val firstSelectedPackage = selectedPackages.keys.firstOrNull()
    val hasSelection = firstSelectedPackage != null
    // Unfiltered groups, read only to find the group a selection belongs to
    val groupedReorderGroups = remember(
        appGrouping,
        hasSelection,
        homeAppItems,
        apps.categoryState,
        apps.sourceGroups,
        uncategorizedTitle
    ) {
        if (!hasSelection) return@remember emptyList()
        when (appGrouping) {
            HomeAppCategoryViewMode.SOURCES -> buildHomeSourceGroups(
                items = homeAppItems,
                sourceGroups = apps.sourceGroups,
                uncategorizedTitle = uncategorizedTitle,
                uncategorizedCollapsed = false,
                ignoreCollapsed = true
            )

            HomeAppCategoryViewMode.CUSTOM -> buildHomeCategoryGroups(
                items = homeAppItems,
                categoryState = apps.categoryState.copy(uncategorizedCollapsed = false),
                uncategorizedTitle = uncategorizedTitle,
                ignoreCollapsed = true
            )

            HomeAppCategoryViewMode.ALL_APPS -> emptyList()
        }
    }
    val groupedSelectionGroup = remember(
        appGrouping,
        firstSelectedPackage,
        state.selectedGroupKey,
        groupedReorderGroups
    ) {
        if (appGrouping == HomeAppCategoryViewMode.ALL_APPS || firstSelectedPackage == null) {
            return@remember null
        }
        val hasSelected: (HomeCategoryGroup) -> Boolean = { group ->
            group.items.any { it.id == firstSelectedPackage }
        }
        val keyMatch = state.selectedGroupKey?.let { key ->
            groupedReorderGroups.firstOrNull { it.selectionKey() == key && hasSelected(it) }
        }
        keyMatch ?: groupedReorderGroups.firstOrNull(hasSelected)
    }
    val groupedSelectionPackages = remember(groupedSelectionGroup) {
        groupedSelectionGroup
            ?.items
            ?.mapTo(linkedSetOf()) { it.id }
    }

    val listState = state.listState

    fun movePackagesInOrder(
        order: List<String>,
        fromIndex: Int,
        toIndex: Int
    ): List<String> {
        if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) {
            return order
        }
        return order.toMutableList().apply {
            val moved = removeAt(fromIndex)
            add(toIndex.coerceIn(0, size), moved)
        }
    }

    fun moveAppOrder(fromIndex: Int, toIndex: Int): List<String> {
        val scopePackages = state.reorderScopePackages ?: return movePackagesInOrder(
            order = state.localOrder,
            fromIndex = fromIndex,
            toIndex = toIndex
        )

        val scopedOrder = state.localOrder.filter { it in scopePackages }
        val movedScopedOrder = movePackagesInOrder(
            order = scopedOrder,
            fromIndex = fromIndex,
            toIndex = toIndex
        )
        if (movedScopedOrder == scopedOrder) return state.localOrder

        val movedIterator = movedScopedOrder.iterator()
        return state.localOrder.map { packageName ->
            if (packageName in scopePackages) {
                movedIterator.next()
            } else {
                packageName
            }
        }
    }

    fun moveReorderOrder(fromIndex: Int, toIndex: Int) {
        val sourceOrder = state.scopedSourceOrder
        if (sourceOrder != null) {
            state.scopedSourceOrder = movePackagesInOrder(sourceOrder, fromIndex, toIndex)
        } else {
            state.localOrder = moveAppOrder(fromIndex, toIndex)
        }
    }

    // Rows above the cards, which the reorder indices count
    val leadingItemCount = if (header != null) 1 else 0
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        moveReorderOrder(from.index - leadingItemCount, to.index - leadingItemCount)
    }
    fun headerGroupAtListIndex(listIndex: Int): HomeCategoryGroup? {
        val index = listIndex - leadingItemCount
        val groups = when (appGrouping) {
            HomeAppCategoryViewMode.SOURCES -> displayedSourceCategoryGroups
            HomeAppCategoryViewMode.CUSTOM -> displayedCategoryGroups
            HomeAppCategoryViewMode.ALL_APPS -> emptyList()
        }
        var currentIndex = 0
        groups.forEach { group ->
            if (currentIndex == index) {
                return when (appGrouping) {
                    HomeAppCategoryViewMode.SOURCES -> group.takeIf { it.sourceUid != null }
                    HomeAppCategoryViewMode.CUSTOM -> group.takeIf { it.editable }
                    HomeAppCategoryViewMode.ALL_APPS -> null
                }
            }
            currentIndex += 1
            if (!group.collapsed) currentIndex += group.items.size
        }
        return null
    }
    // Two reorder states share `listState`: app cards drag only in isReorderMode,
    // category headers drag only in CUSTOM view via editable headers. They are never
    // active at the same time, so the shared list state does not double-handle drags.
    val categoryReorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromGroup = headerGroupAtListIndex(from.index) ?: return@rememberReorderableLazyListState
        val toGroup = headerGroupAtListIndex(to.index) ?: return@rememberReorderableLazyListState
        if (fromGroup.selectionKey() == toGroup.selectionKey()) return@rememberReorderableLazyListState

        when (appGrouping) {
            HomeAppCategoryViewMode.SOURCES -> {
                val fromUid = fromGroup.sourceUid ?: return@rememberReorderableLazyListState
                val toUid = toGroup.sourceUid ?: return@rememberReorderableLazyListState
                val orderedUids = state.localSourceGroupOrder.toMutableList()
                val fromPosition = orderedUids.indexOf(fromUid)
                val toPosition = orderedUids.indexOf(toUid)
                if (fromPosition == -1 || toPosition == -1) return@rememberReorderableLazyListState

                val moved = orderedUids.removeAt(fromPosition)
                orderedUids.add(toPosition.coerceIn(0, orderedUids.size), moved)
                state.localSourceGroupOrder = orderedUids
            }

            HomeAppCategoryViewMode.CUSTOM -> {
                val fromId = fromGroup.id ?: return@rememberReorderableLazyListState
                val toId = toGroup.id ?: return@rememberReorderableLazyListState
                val orderedIds = state.localCategoryOrder.toMutableList()
                val fromPosition = orderedIds.indexOf(fromId)
                val toPosition = orderedIds.indexOf(toId)
                if (fromPosition == -1 || toPosition == -1) return@rememberReorderableLazyListState

                val moved = orderedIds.removeAt(fromPosition)
                orderedIds.add(toPosition.coerceIn(0, orderedIds.size), moved)
                state.localCategoryOrder = orderedIds
            }

            HomeAppCategoryViewMode.ALL_APPS -> Unit
        }
    }
    // Overscroll left over by the list reaches the pull-to-refresh wrapping the screen, which would
    // otherwise fire a source update while the user is only picking or rearranging cards
    val pullToRefreshGuard = remember(state) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (state.isMultiSelectMode || state.isReorderMode || state.isCategoryReorderMode) {
                available
            } else {
                Offset.Zero
            }
        }
    }

    val homeItemsByPackage = remember(homeAppItems) {
        homeAppItems.associateBy { it.id }
    }
    val orderedItems = remember(state.localOrder, homeItemsByPackage) {
        state.localOrder.mapNotNull { homeItemsByPackage[it] }
    }
    val reorderItems = remember(orderedItems, state.reorderScopePackages, state.scopedSourceOrder, homeItemsByPackage) {
        state.scopedSourceOrder?.let { sourceOrder ->
            sourceOrder.mapNotNull { homeItemsByPackage[it] }
        } ?: state.reorderScopePackages?.let { scopePackages ->
            orderedItems.filter { it.id in scopePackages }
        } ?: orderedItems
    }
    val displayedAppGroups = remember(
        appGrouping,
        displayedSourceCategoryGroups,
        displayedCategoryGroups
    ) {
        when (appGrouping) {
            HomeAppCategoryViewMode.SOURCES -> displayedSourceCategoryGroups
            HomeAppCategoryViewMode.CUSTOM -> displayedCategoryGroups
            HomeAppCategoryViewMode.ALL_APPS -> emptyList()
        }
    }
    // Reordering and the Sources grouping have no alphabetical order to jump through
    val alphabetScrollMode = (apps.sortMode == HomeAppSortMode.NAME_ASC ||
            apps.sortMode == HomeAppSortMode.NAME_DESC) &&
            appGrouping != HomeAppCategoryViewMode.SOURCES &&
            !state.isReorderMode &&
            !state.isCategoryReorderMode
    val scrollTargets = remember(
        alphabetScrollMode,
        state.isLoading,
        homeAppItems.isEmpty(),
        isGroupedAppView,
        filteredItems,
        displayedAppGroups
    ) {
        when {
            !alphabetScrollMode -> emptyList()
            state.isLoading && homeAppItems.isEmpty() -> emptyList()
            isGroupedAppView -> buildGroupedHomeScrollTargets(displayedAppGroups)
            else -> buildFlatHomeScrollTargets(filteredItems)
        }
    }

    // Cards that arrive after the first frame can sort above the anchor, and LazyColumn keeps
    // the keyed first visible item in place, leaving the list scrolled past its top. Pin to the
    // top until a real drag, so programmatic scrolls and rotation keep the user's position.
    var userScrolledList by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) userScrolledList = true
        }
    }
    val listOrderKeys = remember(homeAppItems) { homeAppItems.map { it.id } }
    LaunchedEffect(listOrderKeys, userScrolledList) {
        if (userScrolledList || state.isReorderMode) return@LaunchedEffect
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    // Flat-only: grouped scrolls in enterReorder before the items list swaps
    LaunchedEffect(state.isReorderMode) {
        if (state.isReorderMode && state.reorderScopePackages == null) {
            val targets = state.reorderFocusPackages
            if (targets.isNotEmpty()) {
                val topIndex = reorderItems.indexOfFirst { it.id in targets }
                if (topIndex >= 0) listState.scrollToItem(topIndex + leadingItemCount)
            }
            state.reorderFocusPackages = emptySet()
        }
    }

    // Polite TalkBack announcement after a screen-reader-triggered Move action.
    // Empty until the first move; cleared by the next compose if needed
    var moveAnnouncement by remember { mutableStateOf("") }
    val moveAnnouncementFormat = stringResource(R.string.accessibility_app_moved_announcement)

    // True empty state: loaded, no apps from any bundle (no sources / all disabled)
    val isNoSourcesState = !state.isLoading && homeAppItems.isEmpty() && hiddenAppItems.isEmpty()
    // All-hidden state: apps exist but all are hidden
    val isAllHiddenState = !state.isLoading && homeAppItems.isEmpty() && hiddenAppItems.isNotEmpty()
    val isEmptyState = isNoSourcesState || isAllHiddenState
    // Nothing left to show: items exist but neither the filter nor the query matches any of them
    val isListEmpty = !state.isLoading && homeAppItems.isNotEmpty() &&
            filteredItems.isEmpty() && filteredHiddenItems.isEmpty()
    // An active filter takes the empty state, since clearing it is the way out the user needs
    val isFilterEmpty = isListEmpty && isFilterActive
    val isSearchEmpty = isListEmpty && !isFilterActive && searchQuery.isNotBlank()
    val showMoreAppsCard = apps.showMoreAppsCard && !isFilteringList && !state.isEditing
    val openCommunityPatches = { state.showCommunityNotice = true }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Hidden polite live region used to announce the result of TalkBack Move up/down actions
        Spacer(
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = moveAnnouncement
            }
        )

        AnimatedContent(
            targetState = isEmptyState,
            transitionSpec = Animations.fadeCrossfade(300),
            label = "home_empty_state"
        ) { empty ->
            if (empty) {
                Column(
                    modifier = Modifier.padding(top = topPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    header?.invoke()
                    if (isAllHiddenState) {
                        EmptyState(
                            message = stringResource(R.string.home_all_apps_hidden_title),
                            icon = MaterialSymbols.Rounded.Visibility_off,
                            subtitle = stringResource(R.string.home_all_apps_hidden_subtitle),
                            action = CardAction(
                                icon = MaterialSymbols.Rounded.Visibility,
                                label = pluralStringResource(R.plurals.home_app_show_hidden_count, hiddenAppItems.size, hiddenAppItems.size.toString()),
                                onClick = { state.showHiddenAppsDialog = true }
                            ),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        EmptyState(
                            message = stringResource(R.string.home_no_apps_title),
                            icon = MaterialSymbols.Rounded.Inbox,
                            subtitle = stringResource(R.string.home_no_apps_subtitle, stringResource(R.string.sources_management_title)),
                            action = CardAction(
                                icon = MaterialSymbols.Rounded.Folder_code,
                                label = stringResource(R.string.sources_management_title),
                                onClick = onOpenSources
                            ),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Vertical fade overlay drawn on top of LazyColumn.
                    // The overlay is pointer-transparent so swipe gestures pass through
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Clears the action bar, plus itemSpacing for a consistent card gap.
                        // Eased rather than switched, so the last cards settle with the bar
                        // rather than jumping
                        val footerClearance by animateDpAsState(
                            targetValue = if (footerBarOverlap > 0.dp) {
                                footerBarOverlap + itemSpacing
                            } else {
                                0.dp
                            },
                            animationSpec = tween(Defaults.ANIMATION_DURATION, easing = FastOutSlowInEasing),
                            label = "footerClearance"
                        )
                        // Cached so the LazyColumn doesn't allocate a new PaddingValues on
                        // every recomposition (which can be per-frame under scroll)
                        val listContentPadding = remember(horizontalPadding, topPadding, footerClearance) {
                            PaddingValues(
                                start = horizontalPadding,
                                top = topPadding,
                                end = horizontalPadding,
                                bottom = footerClearance
                            )
                        }
                        // A focused row comes out from under the header and the footer bar
                        ProvideBringIntoViewInsets(top = topPadding, bottom = footerClearance) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(pullToRefreshGuard),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                                contentPadding = listContentPadding
                            ) {
                                if (header != null) item(key = "header") { header() }

                                // Cold start: homeAppItems still empty - show placeholder shimmer cards
                                if (state.isLoading && homeAppItems.isEmpty()) {
                                    items(3, key = { "placeholder_$it" }) { index ->
                                        AppLoadingCard(
                                            gradientColors = placeholderGradients[index % placeholderGradients.size],
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                } else if (state.isReorderMode) {
                                    reorderableAppCards(
                                        state = state,
                                        items = reorderItems,
                                        reorderableState = reorderableState,
                                        haptic = haptic
                                    )
                                } else if (isGroupedAppView) {
                                    groupedAppCards(
                                        state = state,
                                        groups = displayedAppGroups,
                                        appGrouping = appGrouping,
                                        firstFilteredPackage = filteredItems.firstOrNull()?.id,
                                        showGestureHint = apps.showGestureHint,
                                        appActions = appActions,
                                        categoryReorderableState = categoryReorderableState,
                                        haptic = haptic,
                                        messages = messages,
                                        categoryActionsUnavailableMessage = categoryActionsUnavailableMessage
                                    )

                                    filterEmptyState(
                                        isFilterEmpty = isFilterEmpty,
                                        filterMode = filterMode,
                                        onClearFilter = onClearFilter,
                                        keyPrefix = "category_"
                                    )

                                    moreAppsCard(
                                        visible = showMoreAppsCard,
                                        onOpen = openCommunityPatches,
                                        onHide = onHideMoreAppsCard,
                                        keyPrefix = "category_"
                                    )

                                    hiddenSearchAndShowHiddenItems(
                                        hiddenAppItems = hiddenAppItems,
                                        filteredHiddenItems = filteredHiddenItems,
                                        searchQuery = searchQuery,
                                        isSearchEmpty = isSearchEmpty,
                                        appActions = appActions,
                                        onShowHiddenApps = { state.showHiddenAppsDialog = true },
                                        onOpenCommunityPatches = openCommunityPatches,
                                        keyPrefix = "category_"
                                    )
                                } else {
                                    flatAppCards(
                                        state = state,
                                        items = filteredItems,
                                        showGestureHint = apps.showGestureHint,
                                        // Direct reorder a11y actions are exposed only when the list is
                                        // unfiltered and no multi-select is active, so the indices
                                        // match state.localOrder
                                        directReorderAllowed = !isFilteringList && !state.isMultiSelectMode,
                                        appActions = appActions,
                                        haptic = haptic,
                                        onboardingState = onboardingState,
                                        moveAnnouncementFormat = moveAnnouncementFormat,
                                        onMoveAnnouncement = { moveAnnouncement = it }
                                    )

                                    filterEmptyState(
                                        isFilterEmpty = isFilterEmpty,
                                        filterMode = filterMode,
                                        onClearFilter = onClearFilter
                                    )

                                    moreAppsCard(
                                        visible = showMoreAppsCard,
                                        onOpen = openCommunityPatches,
                                        onHide = onHideMoreAppsCard
                                    )

                                    hiddenSearchAndShowHiddenItems(
                                        hiddenAppItems = hiddenAppItems,
                                        filteredHiddenItems = filteredHiddenItems,
                                        searchQuery = searchQuery,
                                        isSearchEmpty = isSearchEmpty,
                                        appActions = appActions,
                                        onShowHiddenApps = { state.showHiddenAppsDialog = true },
                                        onOpenCommunityPatches = openCommunityPatches
                                    )
                                }
                            }
                        }

                        // Vertical fade overlay drawn on top of LazyColumn.
                        // The overlay is pointer-transparent so swipe gestures pass through.
                        // Derived, so a scroll recomposes only when an edge starts or stops
                        // hiding rows rather than on every frame the offset moves
                        val canScrollDown by remember(listState) {
                            derivedStateOf { listState.canScrollForward }
                        }
                        val bottomAlpha by animateFloatAsState(
                            targetValue = if (canScrollDown) 1f else 0f,
                            animationSpec = tween(150),
                            label = "fade_bottom_alpha"
                        )
                        if (showFadeOverlay) {
                            val bgColor = MaterialTheme.colorScheme.background
                            val fadePx = with(LocalDensity.current) { 8.dp.toPx() } // Fade size
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .drawWithContent {
                                        drawContent()
                                        // Each rect is bound to its own band. Left unbounded it
                                        // covers the whole list, and the clamped tail of the
                                        // gradient still costs a blend pass over every pixel
                                        if (bottomAlpha > 0f) {
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, bgColor),
                                                    startY = size.height - fadePx,
                                                    endY = size.height
                                                ),
                                                topLeft = Offset(0f, size.height - fadePx),
                                                size = Size(size.width, fadePx),
                                                alpha = bottomAlpha
                                            )
                                        }
                                    }
                            )
                        }

                        ListScrollbar(
                            listState = listState,
                            modifier = Modifier.scrollbarSystemBarsPadding(),
                            alphabetTargets = scrollTargets,
                            alphabetMode = alphabetScrollMode
                        )

                        // Lifted clear of whichever bar covers the bottom of the list
                        ScrollToTopButton(
                            listState = listState,
                            extraBottomPadding = footerBarOverlap
                        )
                    }

                    val footerBarContent = HomeFooterBarContent(
                        state = state,
                        apps = apps,
                        appActions = appActions,
                        search = search,
                        listedItems = filteredItems,
                        reorderItems = reorderItems,
                        orderedItems = orderedItems,
                        itemsByPackage = homeItemsByPackage,
                        groupedSelectionGroup = groupedSelectionGroup,
                        groupedSelectionPackages = groupedSelectionPackages,
                        sourceGroups = displayedSourceCategoryGroups,
                        isCustomCategoryView = isCustomCategoryView,
                        isSourceCategoryView = isSourceCategoryView,
                        listState = listState,
                        leadingItemCount = leadingItemCount,
                        scope = scope
                    )
                    SideEffect { footerBar.content = footerBarContent }
                    DisposableEffect(footerBar) { onDispose { footerBar.content = null } }
                }
            }
        }
    }
}

private fun LazyListScope.filterEmptyState(
    isFilterEmpty: Boolean,
    filterMode: HomeAppFilterMode,
    onClearFilter: () -> Unit,
    keyPrefix: String = ""
) {
    if (!isFilterEmpty) return

    item(key = "${keyPrefix}filter_empty") {
        EmptyState(
            message = stringResource(R.string.home_no_apps_filter_title),
            icon = MaterialSymbols.Rounded.Filter_list_off,
            subtitle = stringResource(
                R.string.home_no_apps_filter_subtitle,
                stringResource(filterMode.labelRes)
            ),
            action = CardAction(
                icon = MaterialSymbols.Rounded.Filter_list,
                label = stringResource(R.string.clear),
                onClick = onClearFilter
            ),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.animateItem()
        )
    }
}
