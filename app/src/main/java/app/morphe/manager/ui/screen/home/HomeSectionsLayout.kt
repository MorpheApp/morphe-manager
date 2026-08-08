/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategoryState
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeAppSourceGroup
import app.morphe.manager.util.KnownApps
import kotlinx.coroutines.delay
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
    val categoryState: HomeAppCategoryState,
    val categoryViewMode: HomeAppCategoryViewMode,
    val showCategoryViewSwitcher: Boolean,
    val sourceGroups: List<HomeAppSourceGroup>
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
    val onCategoryViewModeChange: (HomeAppCategoryViewMode) -> Unit,
    val onCreateCategory: (String) -> String,
    val onRenameCategory: (String, String) -> Unit,
    val onDeleteCategory: (String) -> Unit,
    val onSaveCategoryOrder: (List<String>) -> Unit,
    val onToggleCategoryCollapsed: (String?) -> Unit,
    val onToggleSourceGroupCollapsed: (Int) -> Unit,
    val onAssignAppsToCategory: (Set<String>, String?) -> Unit
)

/** Callbacks for surrounding chrome elements. */
@Stable
class HomeChromeActions(
    val onOtherAppsClick: () -> Unit,
    val onBundlesClick: () -> Unit,
    val onSettingsClick: () -> Unit,
    val onRefreshGreeting: (() -> Unit)?
)

/** Flags that control which chrome elements are shown. */
@Immutable
data class HomeChromeFlags(
    val showSearchButton: Boolean,
    val showSortButton: Boolean,
    val showOtherAppsButton: Boolean,
    val isExpertModeEnabled: Boolean
)

/** Search bar visibility, query and mutation callbacks. */
@Stable
class HomeSearchState(
    val visible: Boolean,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onToggle: () -> Unit,
    val onClose: () -> Unit
)

/**
 * Home screen layout with dynamic app buttons:
 * 1. Notifications section
 * 2. Greeting message section
 * 3. Dynamic app buttons
 * 4. Other apps button
 * 5. Bottom action bar
 */
@Composable
fun SectionsLayout(
    notifications: HomeNotificationsUi,
    apps: HomeAppListUi,
    appActions: HomeAppActions,
    chromeActions: HomeChromeActions,
    chromeFlags: HomeChromeFlags,
    greetingMessage: String?,
    onboardingState: OnboardingState? = null
) {
    val windowSize = rememberWindowSize()

    // Search state hoisted here so both AdaptiveContent and HomeBottomActionBar share it
    val searchVisible = remember { mutableStateOf(false) }
    val searchQuery = remember { mutableStateOf("") }
    LaunchedEffect(searchVisible.value) { if (!searchVisible.value) searchQuery.value = "" }
    // Auto-close search if the button disappears
    LaunchedEffect(chromeFlags.showSearchButton) {
        if (!chromeFlags.showSearchButton) searchVisible.value = false
    }

    // Back gesture closes search (registered before multiselect BackHandler so multiselect takes priority)
    BackHandler(enabled = searchVisible.value) { searchVisible.value = false }

    val searchState = HomeSearchState(
        visible = searchVisible.value,
        query = searchQuery.value,
        onQueryChange = { searchQuery.value = it },
        onToggle = { searchVisible.value = !searchVisible.value },
        onClose = { searchVisible.value = false }
    )
    var showSortDialog by remember { mutableStateOf(false) }

    if (showSortDialog) {
        SortModeSelectionDialog(
            title = stringResource(R.string.home_app_sort_title),
            current = apps.sortMode,
            options = sortModeOptions<HomeAppSortMode>(),
            onSelect = { mode ->
                appActions.onSortModeChange(mode)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main layout structure
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AdaptiveContent(
                    windowSize = windowSize,
                    greetingMessage = greetingMessage,
                    apps = apps,
                    appActions = appActions,
                    searchState = searchState,
                    chromeActions = chromeActions,
                    chromeFlags = chromeFlags,
                    onSortClick = { showSortDialog = true },
                    onboardingState = onboardingState
                )
            }

            // Section 5: Bottom action bar
            if (!isLandscape()) {
                HomeBottomActionBar(
                    onBundlesClick = chromeActions.onBundlesClick,
                    onSettingsClick = chromeActions.onSettingsClick,
                    isExpertModeEnabled = chromeFlags.isExpertModeEnabled,
                    showSearchButton = chromeFlags.showSearchButton,
                    showSortButton = chromeFlags.showSortButton,
                    sortMode = apps.sortMode,
                    searchActive = searchState.visible,
                    onSearchClick = searchState.onToggle,
                    onSortClick = { showSortDialog = true },
                    onSourcesPositioned = onboardingState?.let { s -> { b -> s.sourcesButtonBounds = b } },
                    onSettingsPositioned = onboardingState?.let { s -> { b -> s.settingsButtonBounds = b } }
                )
            }
        }

        // Section 1: Notifications overlay - matches maxCardWidth in AdaptiveContent
        val maxCardWidth = if (isLandscape()) 700.dp else 560.dp
        NotificationsOverlay(
            notifications = notifications,
            modifier = Modifier
                .widthIn(max = maxCardWidth)
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
    }
}

/**
 * Adaptive content layout that switches between portrait and landscape modes.
 */
@Composable
private fun AdaptiveContent(
    windowSize: WindowSize,
    greetingMessage: String?,
    apps: HomeAppListUi,
    appActions: HomeAppActions,
    searchState: HomeSearchState,
    chromeActions: HomeChromeActions,
    chromeFlags: HomeChromeFlags,
    onSortClick: () -> Unit,
    onboardingState: OnboardingState? = null
) {
    val contentPadding = windowSize.contentPadding
    val itemSpacing = windowSize.itemSpacing
    val useTwoColumns = isLandscape()
    val maxCardWidth = if (useTwoColumns) 700.dp else 560.dp

    // True empty state: loaded and no items from any bundle: all disabled or no sources
    val isAppsEmpty by remember(apps.visible, apps.installedAppsLoading) {
        derivedStateOf { !apps.installedAppsLoading && apps.visible.isEmpty() }
    }
    val showGroupingFooter = !isAppsEmpty && apps.showCategoryViewSwitcher
    val showOtherAppsFooter = !isAppsEmpty && chromeFlags.showOtherAppsButton
    // Grouped views reserve the full list area so the footer keeps a stable position when
    // groups expand or collapse; the flat All-apps view lets the list wrap to its content
    // so the greeting and cards center together as one block
    val isGroupedAppView = apps.categoryViewMode != HomeAppCategoryViewMode.ALL_APPS

    Column(modifier = Modifier.fillMaxSize()) {
        if (useTwoColumns) {
            // Sidebar layout for landscape
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeSidebarPanel(
                    showSearchButton = chromeFlags.showSearchButton && !isAppsEmpty,
                    searchActive = searchState.visible,
                    isExpertModeEnabled = chromeFlags.isExpertModeEnabled,
                    showSortButton = chromeFlags.showSortButton,
                    sortMode = apps.sortMode,
                    onSearchClick = searchState.onToggle,
                    onSortClick = onSortClick,
                    onBundlesClick = chromeActions.onBundlesClick,
                    onSettingsClick = chromeActions.onSettingsClick,
                    onSourcesPositioned = onboardingState?.let { s -> { b -> s.sourcesButtonBounds = b } },
                    onSettingsPositioned = onboardingState?.let { s -> { b -> s.settingsButtonBounds = b } }
                )
                VerticalDivider(modifier = Modifier.padding(vertical = 20.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = if (isGroupedAppView) Arrangement.Top else Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!greetingMessage.isNullOrEmpty()) {
                            GreetingSection(
                                message = greetingMessage,
                                modifier = Modifier.widthIn(max = maxCardWidth).fillMaxWidth(),
                                onRefresh = chromeActions.onRefreshGreeting
                            )
                            Spacer(modifier = Modifier.height(itemSpacing))
                        }
                        Box(modifier = Modifier.weight(1f, fill = isGroupedAppView)) {
                            MainAppsSection(
                                apps = apps,
                                appActions = appActions,
                                searchState = searchState,
                                onBundlesClick = chromeActions.onBundlesClick,
                                itemSpacing = itemSpacing,
                                maxCardWidth = maxCardWidth,
                                onboardingState = onboardingState,
                                showFadeOverlay = false,
                                fillHeight = isGroupedAppView,
                                modifier = if (isGroupedAppView) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // Footer stays pinned to the bottom of the pane regardless of view mode
                    HomeFooterControls(
                        showOtherApps = showOtherAppsFooter,
                        showGroupingSelector = showGroupingFooter,
                        mode = apps.categoryViewMode,
                        onOtherAppsClick = chromeActions.onOtherAppsClick,
                        onModeChange = appActions.onCategoryViewModeChange,
                        itemSpacing = itemSpacing,
                        modifier = Modifier
                            .widthIn(max = maxCardWidth)
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            // Single-column layout for compact windows (portrait)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = if (isGroupedAppView) Arrangement.Top else Arrangement.Center
            ) {
                // Section 2: Greeting - when disabled, show a small top spacer so
                // the app cards don't sit flush against the top of the screen
                if (!greetingMessage.isNullOrEmpty()) {
                    GreetingSection(
                        message = greetingMessage,
                        modifier = Modifier.padding(horizontal = contentPadding),
                        onRefresh = chromeActions.onRefreshGreeting
                    )
                    Spacer(modifier = Modifier.height(itemSpacing))
                } else if (isGroupedAppView) {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Section 3: Scrollable app buttons
                Box(modifier = Modifier.weight(1f, fill = isGroupedAppView)) {
                    MainAppsSection(
                        apps = apps,
                        appActions = appActions,
                        searchState = searchState,
                        onBundlesClick = chromeActions.onBundlesClick,
                        itemSpacing = itemSpacing,
                        horizontalPadding = contentPadding,
                        maxCardWidth = maxCardWidth,
                        onboardingState = onboardingState,
                        fillHeight = isGroupedAppView,
                        modifier = if (isGroupedAppView) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                    )
                }
            }
            // Section 4: footer controls - pinned to the bottom of the screen,
            // hidden when no apps are available
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HomeFooterControls(
                    showOtherApps = showOtherAppsFooter,
                    showGroupingSelector = showGroupingFooter,
                    mode = apps.categoryViewMode,
                    onOtherAppsClick = chromeActions.onOtherAppsClick,
                    onModeChange = appActions.onCategoryViewModeChange,
                    itemSpacing = itemSpacing,
                    modifier = Modifier
                        .padding(horizontal = contentPadding)
                        .widthIn(max = maxCardWidth - contentPadding * 2)
                        .fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Fixed area below the app list that hosts the "Other apps" button and the optional
 * grouping-mode switcher. Kept as one composable so both children share a single Column
 * and animate in step.
 */
@Composable
private fun HomeFooterControls(
    showOtherApps: Boolean,
    showGroupingSelector: Boolean,
    mode: HomeAppCategoryViewMode,
    onOtherAppsClick: () -> Unit,
    onModeChange: (HomeAppCategoryViewMode) -> Unit,
    itemSpacing: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = showOtherApps,
            enter = Animations.expandFadeEnter,
            exit = Animations.shrinkFadeExit
        ) {
            Column {
                Spacer(modifier = Modifier.height(itemSpacing))
                GlassButton(
                    label = stringResource(R.string.home_other_apps),
                    selected = false,
                    onClick = onOtherAppsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    containerColor = GlassButtonDefaults.containerColor(),
                    contentColor = GlassButtonDefaults.contentColor(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, GlassButtonDefaults.borderColor()),
                    role = Role.Button,
                    pressScale = true,
                    hapticFeedback = true
                )
            }
        }
        AppGroupingFooter(
            visible = showGroupingSelector,
            mode = mode,
            onModeChange = onModeChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (showOtherApps) 0.dp else 8.dp,
                    bottom = 12.dp
                )
        )
    }
}

/**
 * Thin wrapper that fades [AppGroupingToolbar] in and out with the standard home animations.
 */
@Composable
private fun AppGroupingFooter(
    visible: Boolean,
    mode: HomeAppCategoryViewMode,
    onModeChange: (HomeAppCategoryViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.expandFadeEnter,
        exit = Animations.shrinkFadeExit
    ) {
        AppGroupingToolbar(
            mode = mode,
            onModeChange = onModeChange,
            modifier = modifier
        )
    }
}

/**
 * Section 2: Greeting message.
 */
@Composable
fun GreetingSection(
    message: String?,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null
) {
    if (message.isNullOrEmpty()) return
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
        AnimatedContent(
            targetState = message,
            transitionSpec = Animations.slideUpContentTransitionSpec,
            label = "greeting_transition"
        ) { targetMessage ->
            Text(
                text = targetMessage,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Section 3: Dynamic scrollable app buttons list.
 */
@SuppressLint("FrequentlyChangingValue")
@Composable
fun MainAppsSection(
    apps: HomeAppListUi,
    appActions: HomeAppActions,
    searchState: HomeSearchState,
    onBundlesClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 16.dp,
    horizontalPadding: Dp = 0.dp,
    maxCardWidth: Dp = 500.dp,
    onboardingState: OnboardingState? = null,
    showFadeOverlay: Boolean = true,
    // When false, the section wraps its content vertically so the parent can center it as a
    // single block together with the greeting; when true, it takes the full available height
    // so the footer keeps a stable position while groups expand or collapse.
    fillHeight: Boolean = true
) {
    // Aliases for values used many times in the body
    val homeAppItems = apps.visible
    val hiddenAppItems = apps.hidden
    val searchQuery = searchState.query
    val appGrouping = apps.categoryViewMode
    val isGroupedAppView = appGrouping != HomeAppCategoryViewMode.ALL_APPS
    val isCustomCategoryView = appGrouping == HomeAppCategoryViewMode.CUSTOM

    val state = rememberHomeAppsSectionState(
        initialOrder = homeAppItems.map { it.packageName },
        initialSourceGroupOrder = apps.sourceGroups.map { it.uid },
        initialCategoryOrder = apps.categoryState.categories.map { it.id },
        hasContent = homeAppItems.isNotEmpty() || hiddenAppItems.isNotEmpty(),
    )
    val selectedPackages = state.selectedPackages
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val isSourceCategoryView = appGrouping == HomeAppCategoryViewMode.SOURCES

    // Back gesture/button cancels multi-select instead of navigating back
    BackHandler(enabled = state.isMultiSelectMode) { state.exitMultiSelect() }

    // Back gesture/button exits reorder mode without saving
    BackHandler(enabled = state.isReorderMode) {
        state.exitReorder(homeAppItems.map { it.packageName })
    }

    BackHandler(enabled = state.isCategoryBarVisible) { state.closeCategoryBar() }

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
        val currentPackages = homeAppItems.mapTo(mutableSetOf()) { it.packageName }
        selectedPackages.retain { it in currentPackages }
        if (selectedPackages.isEmpty) {
            state.isMultiSelectMode = false
            state.selectedGroupKey = null
        }

        if (!state.isReorderMode) {
            state.localOrder = homeAppItems.map { it.packageName }
        } else {
            val pkgSet = homeAppItems.mapTo(mutableSetOf()) { it.packageName }
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
    val context = LocalContext.current
    val categoryActionsUnavailableToast = stringResource(R.string.home_category_actions_unavailable)

    HomeAppsSectionDialogs(
        state = state,
        apps = apps,
        appActions = appActions
    )

    // Filtered visible items based on search query
    val filteredItems = remember(homeAppItems, searchQuery) {
        if (searchQuery.isBlank()) homeAppItems
        else homeAppItems.filter { item ->
            item.displayName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    // Hidden items that match the search query
    val filteredHiddenItems = remember(hiddenAppItems, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else hiddenAppItems.filter { item ->
            item.displayName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val uncategorizedTitle = stringResource(R.string.home_category_uncategorized)
    val categoryGroups = remember(
        filteredItems,
        apps.categoryState,
        searchQuery,
        uncategorizedTitle
    ) {
        buildHomeCategoryGroups(
            items = filteredItems,
            categoryState = apps.categoryState,
            uncategorizedTitle = uncategorizedTitle,
            ignoreCollapsed = searchQuery.isNotBlank()
        )
    }
    val sourceCategoryGroups = remember(
        filteredItems,
        apps.sourceGroups,
        apps.categoryState.uncategorizedCollapsed,
        searchQuery,
        uncategorizedTitle
    ) {
        buildHomeSourceGroups(
            items = filteredItems,
            sourceGroups = apps.sourceGroups,
            uncategorizedTitle = uncategorizedTitle,
            uncategorizedCollapsed = apps.categoryState.uncategorizedCollapsed,
            ignoreCollapsed = searchQuery.isNotBlank()
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
    val groupedReorderGroups = remember(
        appGrouping,
        homeAppItems,
        apps.categoryState,
        apps.sourceGroups,
        uncategorizedTitle
    ) {
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
    // Cheap key - the block only reads firstSelectedPackage, so passing the full keys
    // list would allocate on every recomp
    val firstSelectedPackage = selectedPackages.keys.firstOrNull()
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
            group.items.any { it.packageName == firstSelectedPackage }
        }
        val keyMatch = state.selectedGroupKey?.let { key ->
            groupedReorderGroups.firstOrNull { it.selectionKey() == key && hasSelected(it) }
        }
        keyMatch ?: groupedReorderGroups.firstOrNull(hasSelected)
    }
    val groupedSelectionPackages = remember(groupedSelectionGroup) {
        groupedSelectionGroup
            ?.items
            ?.mapTo(linkedSetOf()) { it.packageName }
    }

    val listState = rememberLazyListState()

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

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        moveReorderOrder(from.index, to.index)
    }
    fun headerGroupAtListIndex(index: Int): HomeCategoryGroup? {
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
        homeAppItems.associateBy { it.packageName }
    }
    val orderedItems = remember(state.localOrder, homeItemsByPackage) {
        state.localOrder.mapNotNull { homeItemsByPackage[it] }
    }
    val reorderItems = remember(orderedItems, state.reorderScopePackages, state.scopedSourceOrder, homeItemsByPackage) {
        state.scopedSourceOrder?.let { sourceOrder ->
            sourceOrder.mapNotNull { homeItemsByPackage[it] }
        } ?: state.reorderScopePackages?.let { scopePackages ->
            orderedItems.filter { it.packageName in scopePackages }
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
    val listOrderKeys = remember(homeAppItems) { homeAppItems.map { it.packageName } }
    LaunchedEffect(listOrderKeys, userScrolledList) {
        if (userScrolledList || state.isReorderMode) return@LaunchedEffect
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    // Flat-only: grouped scrolls in onEnterReorder before the items list swaps
    LaunchedEffect(state.isReorderMode) {
        if (state.isReorderMode && state.reorderScopePackages == null) {
            val targets = state.reorderFocusPackages
            if (targets.isNotEmpty()) {
                val topIndex = reorderItems.indexOfFirst { it.packageName in targets }
                if (topIndex >= 0) listState.scrollToItem(topIndex)
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
    // Search empty state: items exist but nothing matches query (including hidden)
    val isSearchEmpty = !state.isLoading && homeAppItems.isNotEmpty() &&
            searchQuery.isNotBlank() && filteredItems.isEmpty() && filteredHiddenItems.isEmpty()

    // Horizontal swipe on the background cycles through the visible grouping modes
    val modes = HomeAppCategoryViewMode.entries
    val currentModeIndex = modes.indexOf(appGrouping)
    val canSwipeMode = apps.showCategoryViewSwitcher &&
            !state.isMultiSelectMode &&
            !state.isReorderMode &&
            !state.isCategoryBarVisible &&
            !searchState.visible
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val layoutDirection = LocalLayoutDirection.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canSwipeMode) {
                    Modifier.pointerInput(currentModeIndex, layoutDirection) {
                        var accumulator = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { accumulator = 0f },
                            onDragEnd = {
                                val direction = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
                                val delta = accumulator * direction
                                when {
                                    delta <= -swipeThresholdPx && currentModeIndex < modes.lastIndex ->
                                        appActions.onCategoryViewModeChange(modes[currentModeIndex + 1])
                                    delta >= swipeThresholdPx && currentModeIndex > 0 ->
                                        appActions.onCategoryViewModeChange(modes[currentModeIndex - 1])
                                }
                                accumulator = 0f
                            },
                            onDragCancel = { accumulator = 0f }
                        ) { _, dragAmount -> accumulator += dragAmount }
                    }
                } else Modifier
            ),
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
                if (isAllHiddenState) {
                    HomeEmptyState(
                        icon = Icons.Outlined.VisibilityOff,
                        title = stringResource(R.string.home_all_apps_hidden_title),
                        subtitle = stringResource(R.string.home_all_apps_hidden_subtitle),
                        actionIcon = Icons.Outlined.Visibility,
                        actionLabel = pluralStringResource(R.plurals.home_app_show_hidden_count, hiddenAppItems.size, hiddenAppItems.size.toString()),
                        onAction = { state.showHiddenAppsDialog = true }
                    )
                } else {
                    HomeEmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(R.string.home_no_apps_title),
                        subtitle = stringResource(R.string.home_no_apps_subtitle, stringResource(R.string.sources_management_title)),
                        actionIcon = Icons.Outlined.Source,
                        actionLabel = stringResource(R.string.sources_management_title),
                        onAction = onBundlesClick
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .widthIn(max = maxCardWidth)
                        .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                ) {
                    Column(
                        modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                    ) {
                        // Search bar
                        AnimatedVisibility(
                            visible = searchState.visible,
                            enter = Animations.expandFadeEnter,
                            exit = Animations.shrinkFadeExit
                        ) {
                            HomeSearchTextField(
                                value = searchQuery,
                                onValueChange = searchState.onQueryChange,
                                requestFocus = searchState.visible,
                                modifier = Modifier
                                    .padding(horizontal = horizontalPadding)
                                    .padding(bottom = 8.dp)
                            )
                        }

                        // Vertical fade overlay drawn on top of LazyColumn.
                        // The overlay is pointer-transparent so swipe gestures pass through
                        Box(
                            modifier = if (fillHeight) {
                                Modifier.weight(1f).fillMaxWidth()
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        ) {
                            // Cached so the LazyColumn doesn't allocate a new PaddingValues on
                            // every recomposition (which can be per-frame under scroll)
                            val listContentPadding = remember(horizontalPadding, itemSpacing, state.isFooterBarVisible) {
                                PaddingValues(
                                    start = horizontalPadding,
                                    end = horizontalPadding,
                                    // Extra bottom padding so cards aren't hidden behind the action bar
                                    // MultiSelectBar surface height (100dp) minus bar's own
                                    // 8dp top padding, plus itemSpacing for consistent card gap
                                    bottom = if (state.isFooterBarVisible) 92.dp + itemSpacing else 0.dp
                                )
                            }
                            val listArrangement = remember(itemSpacing, isGroupedAppView) {
                                if (isGroupedAppView) Arrangement.spacedBy(itemSpacing)
                                else Arrangement.spacedBy(itemSpacing, Alignment.CenterVertically)
                            }
                            LazyColumn(
                                state = listState,
                                modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                                    .nestedScroll(pullToRefreshGuard),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                // Center items vertically in the flat All-apps view when the list
                                // is shorter than the viewport; grouped views stay top-aligned so
                                // headers keep a stable position when groups expand/collapse
                                verticalArrangement = listArrangement,
                                contentPadding = listContentPadding
                            ) {
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
                                        firstFilteredPackage = filteredItems.firstOrNull()?.packageName,
                                        showGestureHint = apps.showGestureHint,
                                        appActions = appActions,
                                        categoryReorderableState = categoryReorderableState,
                                        haptic = haptic,
                                        context = context,
                                        categoryActionsUnavailableToast = categoryActionsUnavailableToast
                                    )

                                    hiddenSearchAndShowHiddenItems(
                                        hiddenAppItems = hiddenAppItems,
                                        filteredHiddenItems = filteredHiddenItems,
                                        searchQuery = searchQuery,
                                        isSearchEmpty = isSearchEmpty,
                                        appActions = appActions,
                                        onShowHiddenApps = { state.showHiddenAppsDialog = true },
                                        keyPrefix = "category_"
                                    )
                                } else {
                                    flatAppCards(
                                        state = state,
                                        items = filteredItems,
                                        showGestureHint = apps.showGestureHint,
                                        // Direct reorder a11y actions are exposed only when there's no
                                        // search filter and no multi-select active, so the indices
                                        // match state.localOrder
                                        directReorderAllowed = searchQuery.isBlank() && !state.isMultiSelectMode,
                                        appActions = appActions,
                                        haptic = haptic,
                                        onboardingState = onboardingState,
                                        moveAnnouncementFormat = moveAnnouncementFormat,
                                        onMoveAnnouncement = { moveAnnouncement = it }
                                    )

                                    hiddenSearchAndShowHiddenItems(
                                        hiddenAppItems = hiddenAppItems,
                                        filteredHiddenItems = filteredHiddenItems,
                                        searchQuery = searchQuery,
                                        isSearchEmpty = isSearchEmpty,
                                        appActions = appActions,
                                        onShowHiddenApps = { state.showHiddenAppsDialog = true }
                                    )
                                }
                            }

                            // Vertical fade overlay drawn on top of LazyColumn.
                            // The overlay is pointer-transparent so swipe gestures pass through
                            val canScrollUp = listState.firstVisibleItemIndex > 0 ||
                                    listState.firstVisibleItemScrollOffset > 0
                            val canScrollDown = listState.canScrollForward
                            val topAlpha by animateFloatAsState(
                                targetValue = if (canScrollUp) 1f else 0f,
                                animationSpec = tween(150),
                                label = "fade_top_alpha"
                            )
                            val bottomAlpha by animateFloatAsState(
                                targetValue = if (canScrollDown) 1f else 0f,
                                animationSpec = tween(150),
                                label = "fade_bottom_alpha"
                            )
                            if (showFadeOverlay && (topAlpha > 0f || bottomAlpha > 0f)) {
                                val bgColor = MaterialTheme.colorScheme.background
                                val fadePx = with(LocalDensity.current) { 8.dp.toPx() } // Fade size
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .drawWithContent {
                                            drawContent()
                                            if (topAlpha > 0f) {
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(bgColor, Color.Transparent),
                                                        startY = 0f,
                                                        endY = fadePx
                                                    ),
                                                    alpha = topAlpha
                                                )
                                            }
                                            if (bottomAlpha > 0f) {
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, bgColor),
                                                        startY = size.height - fadePx,
                                                        endY = size.height
                                                    ),
                                                    alpha = bottomAlpha
                                                )
                                            }
                                        }
                                )
                            }

                            ListScrollbar(
                                listState = listState,
                                alphabetTargets = scrollTargets,
                                alphabetMode = alphabetScrollMode,
                                extraBottomPadding = if (state.isFooterBarVisible) 96.dp else 0.dp
                            )

                            // Lift extra space for the MultiSelectBar when it's visible
                            ScrollToTopButton(
                                listState = listState,
                                extraBottomPadding = if (state.isFooterBarVisible) 96.dp else 0.dp
                            )
                        }

                    }

                    HomeAppsFooterBars(
                        state = state,
                        apps = apps,
                        appActions = appActions,
                        searchState = searchState,
                        reorderItems = reorderItems,
                        orderedItems = orderedItems,
                        itemsByPackage = homeItemsByPackage,
                        groupedSelectionGroup = groupedSelectionGroup,
                        groupedSelectionPackages = groupedSelectionPackages,
                        sourceGroups = displayedSourceCategoryGroups,
                        isCustomCategoryView = isCustomCategoryView,
                        isSourceCategoryView = isSourceCategoryView,
                        listState = listState,
                        scope = scope,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = horizontalPadding)
                    )
                }
            }
        }
    }
}
