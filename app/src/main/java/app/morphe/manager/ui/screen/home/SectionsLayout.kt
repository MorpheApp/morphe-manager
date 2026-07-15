/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.manager.HomeAppCategory
import app.morphe.manager.domain.manager.HomeAppCategoryState
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeAppSourceGroup
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.KnownApps
import app.morphe.manager.util.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds

/** Data describing one side of a swipe action - icon, label, and colors. */
private data class SwipeActionConfig(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

private data class CategoryNameRequest(
    val category: HomeAppCategory?
)

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
    val uncategorizedCollapsed: Boolean,
    val sourceGroups: List<HomeAppSourceGroup>
)

/** Callbacks fired from an app card. */
@Stable
class HomeAppActions(
    val onAppClick: (HomeAppItem) -> Unit,
    val onHideApp: (String) -> Unit,
    val onHideMultiple: (Set<String>) -> Unit,
    val onUnhideApp: (String) -> Unit,
    val onShowPatches: (HomeAppItem) -> Unit,
    val onGestureHintShown: () -> Unit,
    val onSaveOrder: (List<String>) -> Unit,
    val onResetOrder: () -> Unit,
    val onSortModeChange: (HomeAppSortMode) -> Unit,
    val onCategoryViewModeChange: (HomeAppCategoryViewMode) -> Unit,
    val onCreateCategory: (String) -> String,
    val onRenameCategory: (String, String) -> Unit,
    val onDeleteCategory: (String) -> Unit,
    val onSaveCategoryOrder: (List<String>) -> Unit,
    val onToggleCategoryCollapsed: (String) -> Unit,
    val onToggleUncategorizedCollapsed: () -> Unit,
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

            // Section 5: Bottom action bar - тільки для одноколонкового (portrait) режиму
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
            enter = MorpheAnimations.expandFadeEnter,
            exit = MorpheAnimations.shrinkFadeExit
        ) {
            Column {
                Spacer(modifier = Modifier.height(itemSpacing))
                OtherAppsSection(
                    onClick = onOtherAppsClick,
                    modifier = Modifier.fillMaxWidth()
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

/** Thin wrapper that fades [AppGroupingToolbar] in and out with the standard home animations. */
@Composable
private fun AppGroupingFooter(
    visible: Boolean,
    mode: HomeAppCategoryViewMode,
    onModeChange: (HomeAppCategoryViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = MorpheAnimations.expandFadeEnter,
        exit = MorpheAnimations.shrinkFadeExit
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
            transitionSpec = MorpheAnimations.slideUpContentTransitionSpec,
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

    // Multi-select state - set of packageNames chosen for bulk hide
    val isMultiSelectMode = remember { mutableStateOf(false) }
    val selectedPackages = rememberSelectionState<String>()

    // Reorder state
    val isReorderMode = remember { mutableStateOf(false) }
    var localOrder by remember { mutableStateOf(homeAppItems.map { it.packageName }) }
    var reorderScopePackages by remember { mutableStateOf<Set<String>?>(null) }
    // Packages that were selected when entering reorder mode; used to scroll
    // the reordered list back to the card the user long-pressed (e.g. from search)
    val reorderFocusPackages = remember { mutableStateOf<Set<String>>(emptySet()) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Split into two flags so the app multi-select and category context bars stay
    // mutually exclusive at the footer slot
    var activeCategoryId by remember { mutableStateOf<String?>(null) }
    val isCategoryReorderMode = remember { mutableStateOf(false) }
    val isCategoryBarVisible = activeCategoryId != null || isCategoryReorderMode.value

    val isMultibarVisible = isMultiSelectMode.value || isReorderMode.value || isCategoryBarVisible

    // Back gesture/button cancels multi-select instead of navigating back
    BackHandler(enabled = isMultiSelectMode.value) {
        isMultiSelectMode.value = false
        selectedPackages.clear()
    }

    // Back gesture/button exits reorder mode without saving
    BackHandler(enabled = isReorderMode.value) {
        isReorderMode.value = false
        selectedPackages.clear()
        reorderScopePackages = null
        localOrder = homeAppItems.map { it.packageName }
    }

    BackHandler(enabled = isCategoryBarVisible) {
        activeCategoryId = null
        isCategoryReorderMode.value = false
    }

    // Retire stale bar state when leaving CUSTOM view or when the active id vanishes
    // from the category list (e.g. deleted from elsewhere)
    LaunchedEffect(isCustomCategoryView) {
        if (!isCustomCategoryView) {
            activeCategoryId = null
            isCategoryReorderMode.value = false
        }
    }
    LaunchedEffect(apps.categoryState.categories) {
        val currentIds = apps.categoryState.categories.mapTo(mutableSetOf()) { it.id }
        if (activeCategoryId != null && activeCategoryId !in currentIds) {
            activeCategoryId = null
        }
    }

    // Sync selection and local order with current item list
    LaunchedEffect(homeAppItems) {
        val currentPackages = homeAppItems.mapTo(mutableSetOf()) { it.packageName }
        selectedPackages.retain { it in currentPackages }
        if (selectedPackages.isEmpty) isMultiSelectMode.value = false

        if (!isReorderMode.value) {
            localOrder = homeAppItems.map { it.packageName }
        } else {
            val pkgSet = homeAppItems.map { it.packageName }.toSet()
            val kept = localOrder.filter { it in pkgSet }
            val keptSet = kept.toSet()
            val added = pkgSet.filter { it !in keptSet }
            localOrder = kept + added
        }
    }

    // Track if real content has ever arrived so we never re-show the shimmer on resume
    val hasEverLoaded = remember {
        mutableStateOf(homeAppItems.isNotEmpty() || hiddenAppItems.isNotEmpty())
    }

    // Stable loading state - drives shimmer visibility.
    // Starts as true when there is nothing to show yet; once content arrives it latches to false
    // and never goes back to true (we don't want shimmer on every recomposition).
    val stableLoadingState = remember { mutableStateOf(!hasEverLoaded.value) }

    LaunchedEffect(apps.installedAppsLoading, homeAppItems.size, hiddenAppItems.size) {
        val hasItems = homeAppItems.isNotEmpty() || hiddenAppItems.isNotEmpty()
        if (hasItems) hasEverLoaded.value = true

        val shouldShowShimmer = !hasEverLoaded.value && apps.installedAppsLoading
        if (shouldShowShimmer) {
            stableLoadingState.value = true
        } else {
            // Small delay so Compose has one frame to lay out the real cards before the
            // shimmer fades out - prevents a single-frame empty gap.
            if (stableLoadingState.value) delay(50.milliseconds)
            stableLoadingState.value = false
        }
    }

    // Placeholder gradients for cold-start shimmer
    val placeholderGradients = remember { KnownApps.DEFAULT_SHIMMER_GRADIENTS }

    // Hidden apps dialog state
    val showHiddenAppsDialog = remember { mutableStateOf(false) }
    val showMoveCategoryDialog = remember { mutableStateOf(false) }
    var categoryNameRequest by remember { mutableStateOf<CategoryNameRequest?>(null) }
    var pendingDeleteCategoryId by remember { mutableStateOf<String?>(null) }

    // Resolved outside the LazyColumn DSL scope since @Composable calls aren't allowed there
    val context = LocalContext.current
    val categoryActionsUnavailableToast = stringResource(R.string.home_category_actions_unavailable)

    if (showHiddenAppsDialog.value) {
        HiddenAppsDialog(
            hiddenAppItems = hiddenAppItems,
            onUnhide = appActions.onUnhideApp,
            onUnhideMultiple = { packages ->
                packages.forEach { appActions.onUnhideApp(it) }
            },
            onShowPatches = appActions.onShowPatches,
            onDismiss = { showHiddenAppsDialog.value = false }
        )
    }

    categoryNameRequest?.let { request ->
        CategoryNameDialog(
            category = request.category,
            onDismiss = { categoryNameRequest = null },
            onConfirm = { name ->
                val category = request.category
                if (category == null) appActions.onCreateCategory(name)
                else appActions.onRenameCategory(category.id, name)
                categoryNameRequest = null
            }
        )
    }

    // Held in local state so the dialog outlives the bar closing on Delete tap
    pendingDeleteCategoryId?.let { pendingId ->
        val category = apps.categoryState.categories.firstOrNull { it.id == pendingId }
        if (category == null) {
            pendingDeleteCategoryId = null
        } else {
            CategoryDeleteConfirmDialog(
                category = category,
                onDismiss = { pendingDeleteCategoryId = null },
                onConfirm = {
                    appActions.onDeleteCategory(pendingId)
                    pendingDeleteCategoryId = null
                }
            )
        }
    }

    if (showMoveCategoryDialog.value) {
        MoveToCategoryDialog(
            categories = apps.categoryState.categories,
            onDismiss = { showMoveCategoryDialog.value = false },
            onSelect = { categoryId ->
                appActions.onAssignAppsToCategory(selectedPackages.keys.toSet(), categoryId)
                selectedPackages.clear()
                isMultiSelectMode.value = false
                showMoveCategoryDialog.value = false
            },
            onCreateAndSelect = { name ->
                val categoryId = appActions.onCreateCategory(name)
                if (categoryId.isNotBlank()) {
                    appActions.onAssignAppsToCategory(selectedPackages.keys.toSet(), categoryId)
                    selectedPackages.clear()
                    isMultiSelectMode.value = false
                    showMoveCategoryDialog.value = false
                }
            }
        )
    }

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
        apps.uncategorizedCollapsed,
        searchQuery,
        uncategorizedTitle
    ) {
        buildHomeCategoryGroups(
            items = filteredItems,
            categoryState = apps.categoryState,
            uncategorizedTitle = uncategorizedTitle,
            uncategorizedCollapsed = apps.uncategorizedCollapsed,
            ignoreCollapsed = searchQuery.isNotBlank()
        )
    }
    val sourceCategoryGroups = remember(
        filteredItems,
        apps.sourceGroups,
        apps.uncategorizedCollapsed,
        searchQuery,
        uncategorizedTitle
    ) {
        buildHomeSourceGroups(
            items = filteredItems,
            sourceGroups = apps.sourceGroups,
            uncategorizedTitle = uncategorizedTitle,
            uncategorizedCollapsed = apps.uncategorizedCollapsed,
            ignoreCollapsed = searchQuery.isNotBlank()
        )
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
                categoryState = apps.categoryState,
                uncategorizedTitle = uncategorizedTitle,
                uncategorizedCollapsed = false,
                ignoreCollapsed = true
            )

            HomeAppCategoryViewMode.ALL_APPS -> emptyList()
        }
    }
    // Cheap key - the block only reads firstSelectedPackage, so passing the full keys
    // list would allocate on every recomp
    val firstSelectedPackage = selectedPackages.keys.firstOrNull()
    val groupedSelectionPackages = remember(
        appGrouping,
        firstSelectedPackage,
        groupedReorderGroups
    ) {
        if (appGrouping == HomeAppCategoryViewMode.ALL_APPS || firstSelectedPackage == null) {
            null
        } else {
            groupedReorderGroups
                .firstOrNull { group ->
                    group.items.any { item -> item.packageName == firstSelectedPackage }
                }
                ?.items
                ?.mapTo(linkedSetOf()) { it.packageName }
        }
    }

    val listState = rememberLazyListState()

    fun movePackagesInOrder(
        order: List<String>,
        fromIndex: Int,
        toIndex: Int,
        selectedSet: Set<String>
    ): List<String> {
        if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) {
            return order
        }

        val draggedPackage = order[fromIndex]
        if (draggedPackage !in selectedSet || selectedSet.size <= 1) {
            return order.toMutableList().apply {
                val moved = removeAt(fromIndex)
                add(toIndex.coerceIn(0, size), moved)
            }
        }

        val targetPackage = order[toIndex]
        if (targetPackage in selectedSet) return order

        val moving = order.filter { it in selectedSet }
        val remaining = order.filter { it !in selectedSet }
        val targetIndex = remaining.indexOf(targetPackage)
        if (targetIndex == -1) return order

        val insertionIndex = if (toIndex > fromIndex) targetIndex + 1 else targetIndex
        return buildList {
            addAll(remaining.take(insertionIndex.coerceIn(0, remaining.size)))
            addAll(moving)
            addAll(remaining.drop(insertionIndex.coerceIn(0, remaining.size)))
        }
    }

    fun moveAppOrder(fromIndex: Int, toIndex: Int): List<String> {
        val scopePackages = reorderScopePackages ?: return movePackagesInOrder(
            order = localOrder,
            fromIndex = fromIndex,
            toIndex = toIndex,
            selectedSet = selectedPackages.keys.toSet()
        )

        val scopedOrder = localOrder.filter { it in scopePackages }
        val movedScopedOrder = movePackagesInOrder(
            order = scopedOrder,
            fromIndex = fromIndex,
            toIndex = toIndex,
            selectedSet = selectedPackages.keys.filterTo(mutableSetOf()) { it in scopePackages }
        )
        if (movedScopedOrder == scopedOrder) return localOrder

        val movedIterator = movedScopedOrder.iterator()
        return localOrder.map { packageName ->
            if (packageName in scopePackages) {
                movedIterator.next()
            } else {
                packageName
            }
        }
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        localOrder = moveAppOrder(from.index, to.index)
    }
    fun categoryIdAtListIndex(index: Int): String? {
        var currentIndex = 0
        categoryGroups.forEach { group ->
            if (currentIndex == index) return group.id.takeIf { group.editable }
            currentIndex += 1
            if (!group.collapsed) currentIndex += group.items.size
        }
        return null
    }
    // Two reorder states share `listState`: app cards drag only in isReorderMode,
    // category headers drag only in CUSTOM view via editable headers. They are never
    // active at the same time, so the shared list state does not double-handle drags.
    val categoryReorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = categoryIdAtListIndex(from.index) ?: return@rememberReorderableLazyListState
        val toId = categoryIdAtListIndex(to.index) ?: return@rememberReorderableLazyListState
        if (fromId == toId) return@rememberReorderableLazyListState

        val orderedIds = apps.categoryState.categories.map { it.id }.toMutableList()
        val fromPosition = orderedIds.indexOf(fromId)
        val toPosition = orderedIds.indexOf(toId)
        if (fromPosition == -1 || toPosition == -1) return@rememberReorderableLazyListState

        val moved = orderedIds.removeAt(fromPosition)
        orderedIds.add(toPosition.coerceIn(0, orderedIds.size), moved)
        appActions.onSaveCategoryOrder(orderedIds)
    }
    val orderedItems = remember(localOrder, homeAppItems) {
        val byPackage = homeAppItems.associateBy { it.packageName }
        localOrder.mapNotNull { byPackage[it] }
    }
    val reorderItems = remember(orderedItems, reorderScopePackages) {
        reorderScopePackages?.let { scopePackages ->
            orderedItems.filter { it.packageName in scopePackages }
        } ?: orderedItems
    }

    // Flat-only: grouped reorder pre-scrolls to the top from onEnterReorder before the
    // items list flips, so it doesn't need a post-mode-change scroll here
    LaunchedEffect(isReorderMode.value) {
        if (isReorderMode.value && reorderScopePackages == null) {
            val targets = reorderFocusPackages.value
            if (targets.isNotEmpty()) {
                val topIndex = reorderItems.indexOfFirst { it.packageName in targets }
                if (topIndex >= 0) listState.scrollToItem(topIndex)
            }
            reorderFocusPackages.value = emptySet()
        }
    }

    // Polite TalkBack announcement after a screen-reader-triggered Move action.
    // Empty until the first move; cleared by the next compose if needed
    var moveAnnouncement by remember { mutableStateOf("") }
    val moveAnnouncementFormat = stringResource(R.string.accessibility_app_moved_announcement)

    // True empty state: loaded, no apps from any bundle (no sources / all disabled)
    val isNoSourcesState = !stableLoadingState.value && homeAppItems.isEmpty() && hiddenAppItems.isEmpty()
    // All-hidden state: apps exist but all are hidden
    val isAllHiddenState = !stableLoadingState.value && homeAppItems.isEmpty() && hiddenAppItems.isNotEmpty()
    val isEmptyState = isNoSourcesState || isAllHiddenState
    // Search empty state: items exist but nothing matches query (including hidden)
    val isSearchEmpty = !stableLoadingState.value && homeAppItems.isNotEmpty() &&
            searchQuery.isNotBlank() && filteredItems.isEmpty() && filteredHiddenItems.isEmpty()

    // Horizontal swipe on the background cycles through the visible grouping modes
    val modes = HomeAppCategoryViewMode.entries
    val currentModeIndex = modes.indexOf(appGrouping)
    val canSwipeMode = apps.showCategoryViewSwitcher &&
            !isMultiSelectMode.value &&
            !isReorderMode.value &&
            !isCategoryBarVisible &&
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
            transitionSpec = MorpheAnimations.fadeCrossfade(300),
            label = "home_empty_state"
        ) { empty ->
            if (empty) {
                if (isAllHiddenState) {
                    MorpheEmptyState(
                        icon = Icons.Outlined.VisibilityOff,
                        title = stringResource(R.string.home_all_apps_hidden_title),
                        subtitle = stringResource(R.string.home_all_apps_hidden_subtitle),
                        actionIcon = Icons.Outlined.Visibility,
                        actionLabel = pluralStringResource(R.plurals.home_app_show_hidden_count, hiddenAppItems.size, hiddenAppItems.size.toString()),
                        onAction = { showHiddenAppsDialog.value = true }
                    )
                } else {
                    MorpheEmptyState(
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
                            enter = MorpheAnimations.expandFadeEnter,
                            exit = MorpheAnimations.shrinkFadeExit
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
                            LazyColumn(
                                state = listState,
                                modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                // Center items vertically in the flat All-apps view when the list
                                // is shorter than the viewport; grouped views stay top-aligned so
                                // headers keep a stable position when groups expand/collapse
                                verticalArrangement = if (isGroupedAppView) {
                                    Arrangement.spacedBy(itemSpacing)
                                } else {
                                    Arrangement.spacedBy(itemSpacing, Alignment.CenterVertically)
                                },
                                contentPadding = PaddingValues(
                                    start = horizontalPadding,
                                    end = horizontalPadding,
                                    // Extra bottom padding so cards aren't hidden behind the action bar
                                    // MultiSelectBar surface height (100dp) minus bar's own
                                    // 8dp top padding, plus itemSpacing for consistent card gap
                                    bottom = if (isMultibarVisible) 92.dp + itemSpacing else 0.dp
                                )
                            ) {
                                // Cold start: homeAppItems still empty - show placeholder shimmer cards
                                if (stableLoadingState.value && homeAppItems.isEmpty()) {
                                    items(3, key = { "placeholder_$it" }) { index ->
                                        AppLoadingCard(
                                            gradientColors = placeholderGradients[index % placeholderGradients.size],
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                } else if (isReorderMode.value) {
                                    itemsIndexed(
                                        items = reorderItems,
                                        key = { _, item -> item.packageName }
                                    ) { _, item ->
                                        ReorderableItem(reorderableState, key = item.packageName) { itemIsDragging ->
                                            DynamicAppCard(
                                                item = item,
                                                isLoading = false,
                                                hasUpdate = item.hasUpdate,
                                                onAppClick = {
                                                    if (selectedPackages.isNotEmpty) {
                                                        selectedPackages.toggle(item.packageName)
                                                    }
                                                },
                                                onHide = {},
                                                onShowPatches = {},
                                                showGestureHint = false,
                                                onGestureHintShown = {},
                                                isSelected = selectedPackages.contains(item.packageName),
                                                isMultiSelectMode = selectedPackages.isNotEmpty,
                                                onLongPress = { selectedPackages.toggle(item.packageName) },
                                                swipeActionsEnabled = false,
                                                dragHandleModifier = Modifier.draggableHandle(
                                                    onDragStarted = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                ),
                                                modifier = Modifier.zIndex(if (itemIsDragging) 1f else 0f)
                                            )
                                        }
                                    }
                                } else if (isGroupedAppView) {
                                    // isGroupedAppView already excludes ALL_APPS, so the switch
                                    // only needs to distinguish between SOURCES and CUSTOM
                                    val groups = when (appGrouping) {
                                        HomeAppCategoryViewMode.SOURCES -> sourceCategoryGroups
                                        else -> categoryGroups
                                    }

                                    groups.forEach { group ->
                                        val headerKey = "category_${group.id ?: "uncategorized"}"
                                        item(key = headerKey) {
                                            // Long-press is gated while any other footer mode is
                                            // already using the slot
                                            val isFooterBusy = isMultiSelectMode.value ||
                                                    isReorderMode.value ||
                                                    isCategoryReorderMode.value
                                            val headerLongPress: (() -> Unit)? = when {
                                                isFooterBusy -> null
                                                group.editable -> { -> activeCategoryId = group.id }
                                                else -> { -> context.toast(categoryActionsUnavailableToast) }
                                            }
                                            val headerContent: @Composable ((@Composable () -> Unit)?) -> Unit = { dragHandle ->
                                                HomeCategoryHeader(
                                                    group = group,
                                                    onToggle = {
                                                        val sourceUid = group.sourceUid
                                                        when {
                                                            sourceUid != null -> appActions.onToggleSourceGroupCollapsed(sourceUid)
                                                            group.id != null -> appActions.onToggleCategoryCollapsed(group.id)
                                                            else -> appActions.onToggleUncategorizedCollapsed()
                                                        }
                                                    },
                                                    onLongPress = headerLongPress,
                                                    modifier = Modifier.animateItem(),
                                                    dragHandle = dragHandle
                                                )
                                            }

                                            if (group.editable && isCategoryReorderMode.value) {
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
                                                key = { item -> "category_${group.id ?: "uncategorized"}_${item.packageName}" }
                                            ) { item ->
                                                val isSelected = selectedPackages.contains(item.packageName)
                                                DynamicAppCard(
                                                    item = item,
                                                    isLoading = stableLoadingState.value,
                                                    hasUpdate = item.hasUpdate,
                                                    onAppClick = {
                                                        if (isMultiSelectMode.value) {
                                                            selectedPackages.toggle(item.packageName)
                                                        } else {
                                                            appActions.onAppClick(item)
                                                        }
                                                    },
                                                    onHide = { appActions.onHideApp(item.packageName) },
                                                    onShowPatches = { appActions.onShowPatches(item) },
                                                    showGestureHint = item.packageName == filteredItems.firstOrNull()?.packageName &&
                                                            apps.showGestureHint,
                                                    onGestureHintShown = appActions.onGestureHintShown,
                                                    isSelected = isSelected,
                                                    isMultiSelectMode = isMultiSelectMode.value,
                                                    onLongPress = {
                                                        // Skip so the category bar doesn't overlap with app multi-select
                                                        if (!isCategoryBarVisible) {
                                                            isMultiSelectMode.value = true
                                                            selectedPackages.toggle(item.packageName)
                                                        }
                                                    },
                                                    modifier = Modifier.animateItem()
                                                )
                                            }
                                        }
                                    }

                                    hiddenSearchAndShowHiddenItems(
                                        hiddenAppItems = hiddenAppItems,
                                        filteredHiddenItems = filteredHiddenItems,
                                        searchQuery = searchQuery,
                                        isSearchEmpty = isSearchEmpty,
                                        appActions = appActions,
                                        onShowHiddenApps = { showHiddenAppsDialog.value = true },
                                        keyPrefix = "category_"
                                    )
                                } else {
                                    // Direct reorder a11y actions are exposed only when there's no search
                                    // filter and no multi-select active so the indices match localOrder
                                    val directReorderAllowed = searchQuery.isBlank() && !isMultiSelectMode.value
                                    itemsIndexed(
                                        items = filteredItems,
                                        key = { _, item -> item.packageName }
                                    ) { index, item ->
                                        val isSelected = selectedPackages.contains(item.packageName)
                                        DynamicAppCard(
                                            item = item,
                                            isLoading = stableLoadingState.value,
                                            hasUpdate = item.hasUpdate,
                                            onAppClick = {
                                                if (isMultiSelectMode.value) {
                                                    // In multi-select mode taps toggle selection
                                                    selectedPackages.toggle(item.packageName)
                                                } else {
                                                    appActions.onAppClick(item)
                                                }
                                            },
                                            onHide = { appActions.onHideApp(item.packageName) },
                                            onShowPatches = { appActions.onShowPatches(item) },
                                            // Hint plays only on the first card
                                            showGestureHint = index == 0 && apps.showGestureHint,
                                            onGestureHintShown = appActions.onGestureHintShown,
                                            isSelected = isSelected,
                                            isMultiSelectMode = isMultiSelectMode.value,
                                            onLongPress = {
                                                // Long-press enters multi-select and toggles this card
                                                isMultiSelectMode.value = true
                                                selectedPackages.toggle(item.packageName)
                                            },
                                            onMoveUp = if (directReorderAllowed && index > 0) {
                                                {
                                                    val current = localOrder.toMutableList()
                                                    val from = current.indexOf(item.packageName)
                                                    if (from > 0) {
                                                        val moved = current.removeAt(from)
                                                        current.add(from - 1, moved)
                                                        localOrder = current
                                                        appActions.onSaveOrder(current)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        moveAnnouncement = moveAnnouncementFormat
                                                            .format(item.displayName, from, current.size)
                                                    }
                                                }
                                            } else null,
                                            onMoveDown = if (directReorderAllowed && index < filteredItems.size - 1) {
                                                {
                                                    val current = localOrder.toMutableList()
                                                    val from = current.indexOf(item.packageName)
                                                    if (from in 0 until current.size - 1) {
                                                        val moved = current.removeAt(from)
                                                        current.add(from + 1, moved)
                                                        localOrder = current
                                                        appActions.onSaveOrder(current)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        moveAnnouncement = moveAnnouncementFormat
                                                            .format(item.displayName, from + 2, current.size)
                                                    }
                                                }
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

                                    hiddenSearchAndShowHiddenItems(
                                        hiddenAppItems = hiddenAppItems,
                                        filteredHiddenItems = filteredHiddenItems,
                                        searchQuery = searchQuery,
                                        isSearchEmpty = isSearchEmpty,
                                        appActions = appActions,
                                        onShowHiddenApps = { showHiddenAppsDialog.value = true }
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

                            // Lift extra space for the MultiSelectBar when it's visible
                            ScrollToTopButton(
                                listState = listState,
                                extraBottomPadding = if (isMultibarVisible) 96.dp else 0.dp
                            )
                        }

                    }

                    // Multi-select / reorder bar - slides up from bottom
                    val activeAppScopePackages = reorderScopePackages ?: groupedSelectionPackages
                    // Memoized - otherwise selection toggles refilter homeAppItems each pass
                    val activeAppScopeItems = remember(activeAppScopePackages, homeAppItems) {
                        activeAppScopePackages?.let { scopePackages ->
                            homeAppItems.filter { it.packageName in scopePackages }
                        } ?: homeAppItems
                    }
                    MultiSelectBar(
                        selectedCount = selectedPackages.size,
                        totalCount = activeAppScopeItems.size,
                        visible = isMultibarVisible,
                        isReorderMode = isReorderMode.value,
                        onSelectAll = {
                            selectedPackages.setAll(activeAppScopeItems.map { it.packageName })
                        },
                        onDeselectAll = { selectedPackages.clear() },
                        onAction = {
                            appActions.onHideMultiple(selectedPackages.keys.toSet())
                            isMultiSelectMode.value = false
                            selectedPackages.clear()
                        },
                        actionIcon = Icons.Outlined.VisibilityOff,
                        actionContentDescription = stringResource(R.string.hide),
                        actionDoneMessage = stringResource(R.string.hidden),
                        onMoveToCategory = if (isCustomCategoryView) {
                            { showMoveCategoryDialog.value = true }
                        } else null,
                        onCancel = {
                            isMultiSelectMode.value = false
                            selectedPackages.clear()
                        },
                        onEnterReorder = {
                            groupedSelectionPackages?.let { packages ->
                                selectedPackages.retain { it in packages }
                            }
                            reorderScopePackages = groupedSelectionPackages
                            reorderFocusPackages.value = if (groupedSelectionPackages == null) {
                                selectedPackages.keys.toSet()
                            } else {
                                emptySet()
                            }
                            isMultiSelectMode.value = false
                            searchState.onClose()
                            if (groupedSelectionPackages == null) {
                                isReorderMode.value = true
                            } else {
                                scope.launch {
                                    listState.scrollToItem(0)
                                    isReorderMode.value = true
                                }
                            }
                        },
                        onSaveOrder = {
                            appActions.onSaveOrder(localOrder)
                            isReorderMode.value = false
                            reorderScopePackages = null
                            selectedPackages.clear()
                        },
                        onResetOrder = {
                            appActions.onResetOrder()
                            localOrder = homeAppItems.map { it.packageName }
                            isReorderMode.value = false
                            reorderScopePackages = null
                            selectedPackages.clear()
                        },
                        onCancelReorder = {
                            isReorderMode.value = false
                            reorderScopePackages = null
                            selectedPackages.clear()
                            localOrder = homeAppItems.map { it.packageName }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = horizontalPadding)
                    )

                    val activeCategoryTitle = activeCategoryId?.let { id ->
                        apps.categoryState.categories.firstOrNull { it.id == id }?.name
                    }
                    CategoryActionBar(
                        activeCategoryTitle = activeCategoryTitle,
                        visible = isCategoryBarVisible,
                        isReorderMode = isCategoryReorderMode.value,
                        onRename = {
                            val category = apps.categoryState.categories
                                .firstOrNull { it.id == activeCategoryId }
                            if (category != null) {
                                categoryNameRequest = CategoryNameRequest(category)
                            }
                            activeCategoryId = null
                        },
                        onDelete = {
                            // Hand off to the confirmation dialog; actual deletion runs only if
                            // the user confirms. Close the bar so the dialog isn't shadowed.
                            pendingDeleteCategoryId = activeCategoryId
                            activeCategoryId = null
                        },
                        onEnterReorder = {
                            activeCategoryId = null
                            searchState.onClose()
                            isCategoryReorderMode.value = true
                        },
                        onExitReorder = {
                            isCategoryReorderMode.value = false
                        },
                        onCancel = {
                            activeCategoryId = null
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = horizontalPadding)
                    )
                }
            }
        }
    }
}

/**
 * Category-style row that opens the hidden-apps dialog.
 */
@Composable
private fun ShowHiddenAppsButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mutedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    HomeGlassCategoryRow(
        title = stringResource(R.string.hidden),
        count = pluralStringResource(R.plurals.home_category_app_count, count, count.toString()),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = mutedContentColor
            )
        },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = mutedContentColor
            )
        },
        modifier = modifier
    )
}

private fun LazyListScope.hiddenSearchAndShowHiddenItems(
    hiddenAppItems: List<HomeAppItem>,
    filteredHiddenItems: List<HomeAppItem>,
    searchQuery: String,
    isSearchEmpty: Boolean,
    appActions: HomeAppActions,
    onShowHiddenApps: () -> Unit,
    keyPrefix: String = ""
) {
    if (filteredHiddenItems.isNotEmpty()) {
        item(key = "${keyPrefix}search_hidden_header") {
            Text(
                text = stringResource(R.string.hidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
                    .animateItem()
            )
        }
        itemsIndexed(
            items = filteredHiddenItems,
            key = { _, item -> "${keyPrefix}hidden_${item.packageName}" }
        ) { _, item ->
            HiddenSearchAppCard(
                item = item,
                onUnhide = { appActions.onUnhideApp(item.packageName) },
                onAppClick = { appActions.onAppClick(item) },
                onShowPatches = { appActions.onShowPatches(item) },
                modifier = Modifier.animateItem()
            )
        }
    }

    if (isSearchEmpty) {
        item(key = "${keyPrefix}search_empty") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
                    .animateItem(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = stringResource(R.string.home_no_apps_search_subtitle, searchQuery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (hiddenAppItems.isNotEmpty() && searchQuery.isBlank()) {
        item(key = "${keyPrefix}show_hidden") {
            ShowHiddenAppsButton(
                count = hiddenAppItems.size,
                onClick = onShowHiddenApps,
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                    fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            )
        }
    }
}

/**
 * Generic empty state with icon, title, optional subtitle and optional action button.
 */
@Composable
internal fun MorpheEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
        if (onAction != null && actionLabel != null) {
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(onClick = onAction) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(actionLabel)
            }
        }
    }
}

/**
 * Wraps [MorpheDialogTextField] with [LocalDialogTextColor] set to onSurface
 * so it renders correctly outside a dialog context.
 */
@Composable
private fun HomeSearchTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    requestFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    CompositionLocalProvider(LocalDialogTextColor provides MaterialTheme.colorScheme.onSurface) {
        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.home_search_apps)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.home_search_apps)
                )
            },
            showClearButton = true,
            modifier = modifier.focusRequester(focusRequester)
        )
    }
}

/**
 * Single dynamic app card with horizontal swipe gestures:
 * - Swipe LEFT  → reveal hide action
 * - Swipe RIGHT → reveal patches dialog
 *
 * On first appearance plays a one-time nudge hint animation.
 */
@Composable
private fun DynamicAppCard(
    modifier: Modifier = Modifier,
    item: HomeAppItem,
    isLoading: Boolean,
    hasUpdate: Boolean,
    onAppClick: () -> Unit,
    onHide: () -> Unit,
    onShowPatches: () -> Unit,
    showGestureHint: Boolean,
    onGestureHintShown: () -> Unit,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onLongPress: () -> Unit = {},
    swipeActionsEnabled: Boolean = true,
    dragHandleModifier: Modifier? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val showHideDialog = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current

    val actionThresholdPx = with(density) { 90.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    // When entering multi-select mode snap card back to center (no swipe visible)
    LaunchedEffect(isMultiSelectMode) {
        if (isMultiSelectMode) offsetX.animateTo(0f, tween(200))
    }

    // Hint animation: nudge right then left, once (only first card)
    LaunchedEffect(showGestureHint, isLoading) {
        if (!showGestureHint || isLoading) {
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        delay(800.milliseconds)
        val nudge = with(density) { 72.dp.toPx() }
        offsetX.animateTo(nudge,  tween(500, easing = FastOutSlowInEasing))
        offsetX.animateTo(0f,     tween(400, easing = FastOutSlowInEasing))
        delay(250.milliseconds)
        offsetX.animateTo(-nudge, tween(500, easing = FastOutSlowInEasing))
        offsetX.animateTo(0f,     tween(400, easing = FastOutSlowInEasing))
        onGestureHintShown()
    }

    val hideLabel = stringResource(R.string.hide)
    val patchesLabel = stringResource(R.string.patches)
    val moveUpLabel = stringResource(R.string.accessibility_move_up)
    val moveDownLabel = stringResource(R.string.accessibility_move_down)
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val leftConfig = remember(hideLabel, errorContainer, onErrorContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.VisibilityOff,
            label = hideLabel,
            containerColor = errorContainer,
            contentColor = onErrorContainer
        )
    }
    val rightConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    Box(modifier = modifier.fillMaxWidth().semantics {
        customActions = buildList {
            if (swipeActionsEnabled) {
                add(CustomAccessibilityAction(hideLabel) { showHideDialog.value = true; true })
                add(CustomAccessibilityAction(patchesLabel) { onShowPatches(); true })
            }
            if (onMoveUp != null) {
                add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
            }
            if (onMoveDown != null) {
                add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
            }
        }
    }) {
        SwipeableCardContainer(
            offsetX = offsetX,
            actionThresholdPx = actionThresholdPx,
            onLeftSwipe = { showHideDialog.value = true },
            onRightSwipe = onShowPatches,
            enabled = swipeActionsEnabled && !isMultiSelectMode,
            background = { leftProgress, rightProgress ->
                SwipeBackground(
                    leftProgress = leftProgress,
                    rightProgress = rightProgress,
                    leftConfig = leftConfig,
                    rightConfig = rightConfig,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        ) {
            SelectableCard(
                isSelected = isSelected,
                isSelectionMode = isMultiSelectMode
            ) {
                Crossfade(
                    targetState = isLoading,
                    animationSpec = tween(300),
                    label = "app_card_crossfade_${item.packageName}"
                ) { loading ->
                    if (loading) {
                        AppLoadingCard(gradientColors = item.gradientColors)
                    } else {
                        if (item.installedApp != null) {
                            InstalledAppCard(
                                installedApp = item.installedApp,
                                packageInfo = item.packageInfo,
                                displayName = item.displayName,
                                gradientColors = item.gradientColors,
                                onClick = onAppClick,
                                hasUpdate = hasUpdate,
                                isAppDeleted = item.isDeleted,
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onLongPress()
                                }
                            )
                        } else {
                            AppButton(
                                packageName = item.packageName,
                                displayName = item.displayName,
                                packageInfo = item.packageInfo,
                                gradientColors = item.gradientColors,
                                onClick = onAppClick,
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onLongPress()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (dragHandleModifier != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        if (showHideDialog.value) {
            HideAppDialog(
                item = item,
                onDismiss = { showHideDialog.value = false },
                onHide = {
                    onHide()
                    showHideDialog.value = false
                }
            )
        }
    }
}

/**
 * Semi-transparent background that reveals contextual action icons as the user drags the card.
 */
@Composable
private fun SwipeBackground(
    leftProgress: Float,
    rightProgress: Float,
    leftConfig: SwipeActionConfig?,
    rightConfig: SwipeActionConfig?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Left edge
        if (leftConfig != null && leftProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterEnd)
                    .background(
                        Brush.horizontalGradient(
                            0f to leftConfig.containerColor.copy(alpha = 0f),
                            1f to leftConfig.containerColor.copy(alpha = 0.85f * leftProgress)
                        )
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .graphicsLayer { alpha = leftProgress },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = leftConfig.icon,
                        contentDescription = null,
                        tint = leftConfig.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = leftConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = leftConfig.contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Right edge
        if (rightConfig != null && rightProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            0f to rightConfig.containerColor.copy(alpha = 0.85f * rightProgress),
                            1f to rightConfig.containerColor.copy(alpha = 0f)
                        )
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .graphicsLayer { alpha = rightProgress },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = rightConfig.icon,
                        contentDescription = null,
                        tint = rightConfig.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = rightConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = rightConfig.contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Shared container that handles horizontal swipe gestures and drives the
 * [SwipeBackground] reveal animation.
 */
@Composable
private fun SwipeableCardContainer(
    modifier: Modifier = Modifier,
    offsetX: Animatable<Float, AnimationVector1D>,
    actionThresholdPx: Float,
    onLeftSwipe: () -> Unit,
    onRightSwipe: () -> Unit,
    leftHaptic: Int = HapticFeedbackConstants.LONG_PRESS,
    rightHaptic: Int = HapticFeedbackConstants.VIRTUAL_KEY,
    enabled: Boolean = true,
    background: @Composable BoxScope.(leftProgress: Float, rightProgress: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Progress values for background reveal [0..1]
    val leftProgress by remember { derivedStateOf { (-offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }
    val rightProgress by remember { derivedStateOf { (offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }

    Box(modifier = modifier.fillMaxWidth()) {
        background(leftProgress, rightProgress)

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value }
                .then(
                    if (enabled) Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offsetX.value < -actionThresholdPx -> {
                                            view.performHapticFeedback(leftHaptic)
                                            offsetX.animateTo(0f, tween(200))
                                            onLeftSwipe()
                                        }
                                        offsetX.value > actionThresholdPx -> {
                                            view.performHapticFeedback(rightHaptic)
                                            offsetX.animateTo(0f, tween(200))
                                            onRightSwipe()
                                        }
                                        else -> offsetX.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val clamped = (offsetX.value + dragAmount)
                                        .coerceIn(-actionThresholdPx * 1.5f, actionThresholdPx * 1.5f)
                                    offsetX.snapTo(clamped)
                                }
                            }
                        )
                    } else Modifier
                )
        ) {
            content()
        }
    }
}

/**
 * App card for hidden apps shown in search results.
 * - Swipe LEFT  → Patches dialog
 * - Swipe RIGHT → Unhide
 *
 * Rendered at reduced opacity to signal the hidden state.
 */
@Composable
private fun HiddenSearchAppCard(
    modifier: Modifier = Modifier,
    item: HomeAppItem,
    onUnhide: () -> Unit,
    onAppClick: () -> Unit,
    onShowPatches: () -> Unit
) {
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 90.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    val patchesLabel = stringResource(R.string.patches)
    val unhideLabel = stringResource(R.string.unhide)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val leftConfig = remember(unhideLabel, tertiaryContainer, onTertiaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Visibility,
            label = unhideLabel,
            containerColor = tertiaryContainer,
            contentColor = onTertiaryContainer
        )
    }
    val rightConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = 0.6f }
    ) {
        SwipeableCardContainer(
            offsetX = offsetX,
            actionThresholdPx = actionThresholdPx,
            onLeftSwipe = onUnhide,
            onRightSwipe = onShowPatches,
            leftHaptic = HapticFeedbackConstants.LONG_PRESS,
            rightHaptic = HapticFeedbackConstants.VIRTUAL_KEY,
            background = { leftProgress, rightProgress ->
                SwipeBackground(
                    leftProgress = leftProgress,
                    rightProgress = rightProgress,
                    leftConfig = leftConfig,
                    rightConfig = rightConfig,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        ) {
            if (item.installedApp != null) {
                InstalledAppCard(
                    installedApp = item.installedApp,
                    packageInfo = item.packageInfo,
                    displayName = item.displayName,
                    gradientColors = item.gradientColors,
                    onClick = onAppClick,
                    hasUpdate = item.hasUpdate,
                    isAppDeleted = item.isDeleted,
                    onLongClick = {}
                )
            } else {
                AppButton(
                    packageName = item.packageName,
                    displayName = item.displayName,
                    packageInfo = item.packageInfo,
                    gradientColors = item.gradientColors,
                    onClick = onAppClick,
                    onLongClick = {}
                )
            }
        }
    }
}

/**
 * Dialog that shows available patches for a specific app.
 * Shown when the user swipes right on a home app card.
 * Uses the shared [PatchItemCard] component from [BundlePatchesDialog]
 * to display rich patch info with search and (multi-bundle) filter support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPatchesDialog(
    item: HomeAppItem,
    patchesByBundle: Map<Int, List<PatchInfo>>,
    bundleNames: Map<Int, String>,
    onDismiss: () -> Unit
) {
    // Flatten to a list of (bundleUid, patch).
    // Bundle ordering: bundles with at least one specific patch come first (by name),
    // then bundles with only universal patches (by name).
    // Within each bundle: specific patches first (alphabetically), universal patches last (alphabetically).
    val allPatches = remember(patchesByBundle, bundleNames) {
        patchesByBundle.entries
            .sortedWith(
                compareBy(
                    { (_, patches) -> patches.all { it.compatiblePackages == null } },
                    { (uid, _) -> bundleNames[uid] ?: uid.toString() }
                )
            )
            .flatMap { (uid, patches) ->
                val (universal, specific) = patches.partition { it.compatiblePackages == null }
                (specific.sortedBy { it.name } + universal.sortedBy { it.name })
                    .map { patch -> uid to patch }
            }
    }

    val isMultiBundle = patchesByBundle.size > 1

    // Per-bundle accent color for multi-bundle mode only.
    // Generated deterministically from uid via multiplicative hash → HSL,
    // so the same uid always produces the same color.
    // Returns null for single-bundle (no coloring needed).
    val bundleAccentColors: Map<Int, Color> = remember(patchesByBundle, isMultiBundle) {
        if (!isMultiBundle) return@remember emptyMap()
        patchesByBundle.keys.associateWith { uid ->
            val hue = ((uid.hashCode() * 2654435761L) and 0xFFFFFFFFL).toFloat() % 360f
            Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.60f)
        }
    }
    val searchQuery = remember { mutableStateOf("") }
    val selectedBundle = remember { mutableStateOf<Int?>(null) }
    val showFilterSheet = remember { mutableStateOf(false) }

    val filteredPatches = remember(allPatches, searchQuery.value, selectedBundle.value) {
        allPatches.filter { (uid, patch) ->
            val bundleMatch = selectedBundle.value == null || uid == selectedBundle.value
            val queryMatch = searchQuery.value.isBlank() ||
                    patch.name.contains(searchQuery.value, ignoreCase = true) ||
                    patch.description?.contains(searchQuery.value, ignoreCase = true) == true
            bundleMatch && queryMatch
        }
    }

    val isFiltering = searchQuery.value.isNotBlank() || selectedBundle.value != null
    val totalCount = allPatches.size

    // Pre-compute per-bundle markers once so items{} can do O(1) lookups instead of O(n) scans
    val firstPatchPerBundle: Map<Int, PatchInfo> = remember(filteredPatches) {
        buildMap {
            filteredPatches.forEach { (uid, patch) -> putIfAbsent(uid, patch) }
        }
    }
    val firstUniversalPerBundle: Map<Int, PatchInfo> = remember(filteredPatches) {
        buildMap {
            filteredPatches.forEach { (uid, patch) ->
                if (patch.compatiblePackages == null) putIfAbsent(uid, patch)
            }
        }
    }
    val bundlesWithSpecificPatches: Set<Int> = remember(filteredPatches) {
        filteredPatches
            .filter { (_, patch) -> patch.compatiblePackages != null }
            .map { it.first }
            .toSet()
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = true,
        title = null,
        compactPadding = true,
        scrollable = false,
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // App header
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = item.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalDialogTextColor.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Widgets,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    val patchCountLabel = pluralStringResource(
                                        R.plurals.patch_count,
                                        totalCount,
                                        totalCount
                                    )
                                    val countText = if (isFiltering)
                                        "${filteredPatches.size}/$patchCountLabel"
                                    else
                                        patchCountLabel
                                    AnimatedContent(
                                        targetState = countText,
                                        transitionSpec = MorpheAnimations.counterTransitionSpec,
                                        label = "app_patch_count"
                                    ) { count ->
                                        Text(
                                            text = count,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Search + filter row (filter button visible only for multi-bundle)
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            MorpheDialogTextField(
                                value = searchQuery.value,
                                onValueChange = { searchQuery.value = it },
                                label = { Text(stringResource(R.string.expert_mode_search)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null
                                    )
                                },
                                showClearButton = true,
                                modifier = Modifier.weight(1f)
                            )

                            if (isMultiBundle) {
                                FilledTonalIconButton(
                                    onClick = { showFilterSheet.value = true },
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (selectedBundle.value != null)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (selectedBundle.value != null)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = stringResource(R.string.filter),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Active bundle filter badge + empty state
                item(key = "filter_badges_and_empty") {
                    Column {
                        AnimatedVisibility(
                            visible = selectedBundle.value != null,
                            enter = MorpheAnimations.expandFadeEnter,
                            exit = MorpheAnimations.shrinkFadeExit
                        ) {
                            selectedBundle.value?.let { uid ->
                                FlowRow(
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedBundle.value = null },
                                        label = { Text(bundleNames[uid] ?: uid.toString()) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = stringResource(R.string.remove),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = filteredPatches.isEmpty(),
                            enter = MorpheAnimations.fadeScaleIn,
                            exit = MorpheAnimations.fadeScaleOut
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.expert_mode_no_results),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Patch cards
                items(
                    filteredPatches,
                    key = { (uid, patch) ->
                        "$uid:${patch.name}:${patch.compatiblePackages?.joinToString { it.packageName.orEmpty() }.orEmpty()}"
                    }
                ) { entry ->
                    val uid: Int = entry.first
                    val patch: PatchInfo = entry.second
                    val isUniversal = patch.compatiblePackages == null
                    Column(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                            fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION_SHORT),
                            placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
                        )
                    ) {
                        // Bundle section label - only for multi-bundle, at first patch of each bundle
                        if (isMultiBundle) {
                            val isFirstOfBundle = firstPatchPerBundle[uid] == patch
                            if (isFirstOfBundle) {
                                InfoBadge(
                                    text = bundleNames[uid] ?: uid.toString(),
                                    style = InfoBadgeStyle.Primary,
                                    icon = Icons.Outlined.Layers,
                                    isExpanded = true,
                                    modifier = Modifier.padding(bottom = 6.dp, top = 8.dp)
                                )
                            }
                        }

                        // Universal patches divider - shown before the first universal patch of each bundle
                        val isFirstUniversalOfBundle = isUniversal && firstUniversalPerBundle[uid] == patch
                        if (isFirstUniversalOfBundle) {
                            val hasSpecificAbove = uid in bundlesWithSpecificPatches
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (hasSpecificAbove) 8.dp else 0.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.expert_mode_universal_patches),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    thickness = 0.5.dp
                                )
                            }
                        }

                        PatchItemCard(
                            patch = patch,
                            saveStateKey = "app_patches_${item.packageName}_$uid",
                            accentColor = bundleAccentColors[uid],
                        )
                    }
                }
            }

            ScrollToTopButton(listState = listState)
        }
    }

    // Bundle filter bottom sheet (multi-bundle only)
    if (showFilterSheet.value && isMultiBundle) {
        MorpheBottomSheet(
            onDismissRequest = { showFilterSheet.value = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.filter),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All" chip
                    FilterChip(
                        selected = selectedBundle.value == null,
                        onClick = { selectedBundle.value = null },
                        label = { Text(stringResource(R.string.all)) },
                        leadingIcon = if (selectedBundle.value == null) {
                            { Icon(Icons.Outlined.DoneAll, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    // Per-bundle chips
                    bundleNames.entries
                        .sortedBy { it.value }
                        .forEach { (uid, name) ->
                            val isSelected = uid == selectedBundle.value
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedBundle.value = if (isSelected) null else uid
                                    showFilterSheet.value = false
                                },
                                label = { Text(name) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Outlined.Done, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                }
            }
        }
    }
}

/**
 * Confirmation dialog asking user whether to hide the app.
 */
@Composable
internal fun HideAppDialog(
    item: HomeAppItem,
    onDismiss: () -> Unit,
    onHide: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_app_hide_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.hide),
                primaryIcon = Icons.Outlined.VisibilityOff,
                onPrimaryClick = onHide,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        },
        compactPadding = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Original app card preview
            AppCardLayout(
                gradientColors = item.gradientColors,
                enabled = true,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                AppCardContent(
                    packageName = item.packageName,
                    packageInfo = item.packageInfo,
                    displayName = item.displayName,
                    subtitle = stringResource(R.string.home_app_will_be_hidden),
                    gradientColors = item.gradientColors,
                )
            }

            // Explanation text
            Text(
                text = stringResource(R.string.home_app_hide_message),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Dialog listing all hidden apps.
 *
 * Swipe gestures (disabled in multi-select mode):
 * - Swipe LEFT  → Patches dialog
 * - Swipe RIGHT → Unhide
 *
 * Long-press enters multi-select; bulk unhide via footer button.
 */
@Composable
internal fun HiddenAppsDialog(
    hiddenAppItems: List<HomeAppItem>,
    onUnhide: (String) -> Unit,
    onUnhideMultiple: (Set<String>) -> Unit = {},
    onShowPatches: (HomeAppItem) -> Unit,
    onDismiss: () -> Unit
) {
    val itemSpacing = rememberWindowSize().itemSpacing
    val isMultiSelectMode = remember { mutableStateOf(false) }
    val selectedPackages = rememberSelectionState<String>()

    // Sync selection with current item list; exit mode if no items remain
    LaunchedEffect(hiddenAppItems) {
        val currentPackages = hiddenAppItems.mapTo(mutableSetOf()) { it.packageName }
        selectedPackages.retain { it in currentPackages }
        if (selectedPackages.isEmpty) isMultiSelectMode.value = false
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 90.dp.toPx() }

    val patchesLabel = stringResource(R.string.patches)
    val unhideLabel = stringResource(R.string.unhide)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val leftConfig = remember(unhideLabel, tertiaryContainer, onTertiaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Visibility,
            label = unhideLabel,
            containerColor = tertiaryContainer,
            contentColor = onTertiaryContainer
        )
    }
    val rightConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    MorpheDialog(
        onDismissRequest = {
            if (isMultiSelectMode.value) {
                isMultiSelectMode.value = false
                selectedPackages.clear()
            } else {
                onDismiss()
            }
        },
        dismissOnClickOutside = !isMultiSelectMode.value,
        title = stringResource(R.string.home_app_hidden_apps_title),
        footer = {
            if (isMultiSelectMode.value) {
                MultiSelectBar(
                    selectedCount = selectedPackages.size,
                    totalCount = hiddenAppItems.size,
                    visible = true,
                    showReorderButton = false,
                    onSelectAll = {
                        selectedPackages.setAll(hiddenAppItems.map { it.packageName })
                    },
                    onDeselectAll = { selectedPackages.clear() },
                    onAction = {
                        onUnhideMultiple(selectedPackages.keys.toSet())
                        isMultiSelectMode.value = false
                        selectedPackages.clear()
                    },
                    actionIcon = Icons.Outlined.Visibility,
                    actionContentDescription = stringResource(R.string.unhide),
                    actionDoneMessage = stringResource(R.string.unhide_done),
                    actionColors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    onCancel = {
                        isMultiSelectMode.value = false
                        selectedPackages.clear()
                    },
                    onEnterReorder = {},
                    onSaveOrder = {},
                    onResetOrder = {},
                    onCancelReorder = {}
                )
            } else {
                MorpheDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        compactPadding = true,
        scrollable = false
    ) {
        if (hiddenAppItems.isEmpty()) {
            MorpheEmptyState(
                icon = Icons.Outlined.Visibility,
                title = stringResource(R.string.home_app_no_hidden)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                items(
                    items = hiddenAppItems,
                    key = { it.packageName }
                ) { item ->
                    val isSelected = selectedPackages.contains(item.packageName)
                    val offsetX = remember(item.packageName) { Animatable(0f) }

                    // Snap card back when entering multi-select
                    LaunchedEffect(isMultiSelectMode.value) {
                        if (isMultiSelectMode.value) offsetX.animateTo(0f, tween(200))
                    }

                    SelectableCard(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                            fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION_SHORT),
                            placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
                        ),
                        isSelected = isSelected,
                        isSelectionMode = isMultiSelectMode.value
                    ) {
                        SwipeableCardContainer(
                            offsetX = offsetX,
                            actionThresholdPx = actionThresholdPx,
                            onLeftSwipe = { onUnhide(item.packageName) },
                            onRightSwipe = { onShowPatches(item) },
                            leftHaptic = HapticFeedbackConstants.LONG_PRESS,
                            rightHaptic = HapticFeedbackConstants.VIRTUAL_KEY,
                            enabled = !isMultiSelectMode.value,
                            background = { leftProgress, rightProgress ->
                                SwipeBackground(
                                    leftProgress = leftProgress,
                                    rightProgress = rightProgress,
                                    leftConfig = leftConfig,
                                    rightConfig = rightConfig,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(24.dp))
                                )
                            }
                        ) {
                            AppCardLayout(
                                gradientColors = item.gradientColors,
                                enabled = true,
                                onClick = {
                                    if (isMultiSelectMode.value) {
                                        selectedPackages.toggle(item.packageName)
                                    } else {
                                        onUnhide(item.packageName)
                                    }
                                },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    isMultiSelectMode.value = true
                                    selectedPackages.toggle(item.packageName)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AppCardContent(
                                    packageName = item.packageName,
                                    packageInfo = item.packageInfo,
                                    displayName = item.displayName,
                                    subtitle = if (isMultiSelectMode.value) null
                                    else stringResource(R.string.home_app_hidden_apps_hint),
                                    gradientColors = item.gradientColors,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared icon + text content for [AppCardLayout] rows.
 *
 * @param packageName   Package name used for icon lookup when [packageInfo] is null.
 * @param packageInfo   Resolved [PackageInfo]; when non-null [packageName] is ignored for the icon.
 * @param displayName   Primary label shown in bold.
 * @param subtitle      Secondary line shown below [displayName]; null → not rendered.
 * @param gradientColors Gradient palette forwarded to [AppIcon] placeholder.
 */
@Composable
private fun RowScope.AppCardContent(
    packageName: String,
    packageInfo: PackageInfo?,
    displayName: String,
    subtitle: String?,
    gradientColors: List<Color>
) {
    val textColor = Color.White
    val subtitleColor = Color.White.copy(alpha = 0.75f)
    val titleShadow = Shadow(
        color = Color.Black.copy(alpha = 0.4f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    )
    val subtitleShadow = Shadow(
        color = Color.Black.copy(alpha = 0.4f),
        offset = Offset(0f, 1f),
        blurRadius = 2f
    )

    AppIcon(
        packageInfo = packageInfo,
        packageName = if (packageInfo == null) packageName else null,
        contentDescription = null,
        modifier = Modifier.size(60.dp),
        preferredSource = AppDataSource.PATCHED_APK,
        placeholderGradientColors = gradientColors,
        placeholderInnerPadding = 6.dp
    )

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                shadow = titleShadow
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = subtitleShadow),
                color = subtitleColor
            )
        }
    }
}

/**
 * Frosted-glass chip for use on gradient card backgrounds.
 * Uses white semi-transparent fill so it reads correctly regardless of
 * the card's accent color or the user's dynamic theme.
 */
@Composable
private fun GlassChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.20f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

/**
 * Installed app card with gradient background.
 */
@Composable
fun InstalledAppCard(
    installedApp: InstalledApp,
    packageInfo: PackageInfo?,
    displayName: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasUpdate: Boolean = false,
    isAppDeleted: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val textColor = Color.White

    val versionLabel = stringResource(R.string.version)
    val installedLabel = stringResource(R.string.installed)
    val updateAvailableLabel = stringResource(R.string.update_available)
    val deletedLabel = stringResource(R.string.uninstalled)

    val version = remember(packageInfo, installedApp, isAppDeleted) {
        val raw = packageInfo?.versionName ?: installedApp.version
        if (raw.startsWith("v")) raw else "v$raw"
    }

    val contentDesc = remember(displayName, version, versionLabel, installedLabel, hasUpdate, updateAvailableLabel, isAppDeleted, deletedLabel) {
        buildString {
            append(displayName)
            if (version.isNotEmpty()) {
                append(", $versionLabel $version")
            }
            append(", ")
            append(if (isAppDeleted) deletedLabel else installedLabel)
            if (hasUpdate && !isAppDeleted) append(", $updateAvailableLabel")
        }
    }

    AppCardLayout(
        gradientColors = gradientColors,
        enabled = true,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.semantics {
            role = Role.Button
            this.contentDescription = contentDesc
        }
    ) {
        // App icon
        AppIcon(
            packageInfo = packageInfo,
            packageName = installedApp.originalPackageName,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            preferredSource = AppDataSource.INSTALLED
        )

        // App info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // App name
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.4f),
                        offset = Offset(0f, 2f),
                        blurRadius = 4f
                    )
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Version + deleted status + inline update chip
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = version,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            offset = Offset(0f, 1f),
                            blurRadius = 2f
                        )
                    ),
                    color = textColor.copy(alpha = 0.85f)
                )

                if (isAppDeleted) {
                    GlassChip(
                        text = stringResource(R.string.uninstalled),
                        icon = Icons.Outlined.DeleteOutline
                    )
                }

                AnimatedVisibility(
                    visible = hasUpdate && !isAppDeleted,
                    enter = MorpheAnimations.expandHorizFadeIn,
                    exit = MorpheAnimations.shrinkHorizFadeOut
                ) {
                    GlassChip(
                        text = stringResource(R.string.update),
                        icon = Icons.Outlined.ArrowUpward
                    )
                }
            }
        }
    }
}

/**
 * App button with gradient background.
 */
@Composable
fun AppButton(
    packageName: String,
    displayName: String,
    packageInfo: PackageInfo?,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null
) {
    val notPatchedText = stringResource(R.string.home_not_patched_yet)
    val disabledText = stringResource(R.string.disabled)

    // Build content description for accessibility
    val contentDesc = remember(displayName, notPatchedText, disabledText, enabled) {
        buildString {
            append(displayName)
            append(", ")
            append(notPatchedText)
            if (!enabled) {
                append(", ")
                append(disabledText)
            }
        }
    }

    AppCardLayout(
        gradientColors = gradientColors,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.semantics {
            role = Role.Button
            this.contentDescription = contentDesc
            if (!enabled) {
                stateDescription = disabledText
            }
        }
    ) {
        AppCardContent(
            packageName = packageName,
            packageInfo = packageInfo,
            displayName = displayName,
            subtitle = notPatchedText,
            gradientColors = gradientColors,
        )
    }
}

/**
 * Section 4: Other apps button.
 */
@Composable
fun OtherAppsSection(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeGlassPillButton(
        onClick = onClick,
        modifier = modifier.padding(bottom = 12.dp),
        text = stringResource(R.string.home_other_apps)
    )
}

/**
 * Shared frosted-glass pill button used by [OtherAppsSection] and [ShowHiddenAppsButton].
 *
 * Renders a rounded pill with semi-transparent surface background, border, press-scale
 * animation, and haptic feedback. Content is either icon+text or text-only.
 */
@Composable
internal fun HomeGlassPillButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(20.dp)
    val backgroundColor = GlassButtonDefaults.containerColor(selected)
    val borderColor = GlassButtonDefaults.borderColor(selected)
    val contentColor = GlassButtonDefaults.contentColor(selected)
    val height = if (compact) 44.dp else 48.dp
    val horizontalPadding = if (compact) 8.dp else 16.dp
    val spacing = if (compact) 6.dp else 8.dp
    val textStyle = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium

    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource, label = "pill_press_scale")
    val handleClick = rememberHapticClick(onClick)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale; clip = true }
            .clip(shape)
            .background(backgroundColor)
            .border(
                BorderStroke(1.dp, borderColor),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = handleClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Shared content layout for app cards and buttons.
 *
 * Uses a multi-layer frosted glass effect:
 * - radial gradient base tinted from card colors
 * - top-left specular shine
 * - bottom-right warm glow from card accent color
 * - diagonal sweep highlight
 * - subtle horizontal frost band
 * - gradient border
 */
@Composable
private fun AppCardLayout(
    modifier: Modifier = Modifier,
    gradientColors: List<Color>,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val view = LocalView.current

    val contentAlpha = if (enabled) 1f else 0.45f
    val baseColor = gradientColors.firstOrNull() ?: Color.White
    val midColor = gradientColors.getOrElse(1) { baseColor }
    val endColor = gradientColors.lastOrNull() ?: baseColor

    // Disabled state fades everything
    val glassAlpha  = if (enabled) 1f else 0.5f
    val borderAlpha = if (enabled) 1f else 0.4f

    // Press scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "card_press_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .drawWithContent {
                val w  = size.width
                val h  = size.height
                val cr = CornerRadius(24.dp.toPx())
                val rtl = layoutDirection == LayoutDirection.Rtl

                // Layer 1: radial base - color blooms from bottom-start
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.80f * glassAlpha),
                            midColor.copy(alpha = 0.60f * glassAlpha),
                            endColor.copy(alpha = 0.40f * glassAlpha)
                        ),
                        center = Offset(if (rtl) w * 0.85f else w * 0.15f, h * 0.85f),
                        radius = w * 1.1f
                    ),
                    cornerRadius = cr
                )

                // Layer 2: secondary radial bloom from top-end (accent)
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            endColor.copy(alpha = 0.55f * glassAlpha),
                            midColor.copy(alpha = 0.25f * glassAlpha),
                            Color.Transparent
                        ),
                        center = Offset(if (rtl) w * 0.12f else w * 0.88f, h * 0.12f),
                        radius = w * 0.75f
                    ),
                    cornerRadius = cr
                )

                // Layer 3: frosted white overlay - very subtle, just adds glass texture
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f * glassAlpha),
                            Color.White.copy(alpha = 0.01f * glassAlpha),
                            Color.White.copy(alpha = 0.02f * glassAlpha)
                        ),
                        startY = 0f,
                        endY = h
                    ),
                    cornerRadius = cr
                )

                // Layer 4: diagonal sweep highlight (top-start → mid) - thin specular only
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f * glassAlpha),
                            Color.White.copy(alpha = 0.02f * glassAlpha),
                            Color.Transparent
                        ),
                        start = Offset(if (rtl) w else 0f, 0f),
                        end   = Offset(w * 0.5f, h)
                    ),
                    cornerRadius = cr
                )

                // Layer 5: bottom edge warm reflection
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            endColor.copy(alpha = 0.22f * glassAlpha)
                        ),
                        center = Offset(w * 0.5f, h),
                        radius = w * 0.65f
                    ),
                    cornerRadius = cr
                )

                drawContent()

                // Border: bright top-start → faded bottom-end
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f * borderAlpha),
                            midColor.copy(alpha = 0.30f * borderAlpha),
                            endColor.copy(alpha = 0.15f * borderAlpha),
                            Color.White.copy(alpha = 0.20f * borderAlpha)
                        ),
                        start = Offset(if (rtl) w else 0f, 0f),
                        end   = Offset(if (rtl) 0f else w, h)
                    ),
                    cornerRadius = cr,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
                onLongClick = if (onLongClick != null) {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongClick()
                    }
                } else null
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .graphicsLayer { alpha = contentAlpha },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Shimmer loading animation for app cards.
 */
@Composable
fun AppLoadingCard(
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    // Pulse animation for gradient background
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Shimmer animation
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shape = RoundedCornerShape(24.dp)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        // Base gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = gradientColors.map { it.copy(alpha = pulseAlpha) },
                        start = Offset(if (rtl) 1000f else 0f, 0f),
                        end = Offset(if (rtl) 0f else 1000f, 0f)
                    )
                )
        )

        // Shimmer overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .drawBehind {
                    drawDiagonalShimmer(
                        progress = (shimmerOffset + 1f) / 3f,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                }
        )

        // Content skeleton
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon skeleton
            ShimmerBox(
                modifier = Modifier
                    .size(60.dp)
                    .padding(6.dp),
                shape = RoundedCornerShape(12.dp),
                baseColor = Color.White.copy(alpha = 0.2f)
            )

            // Text skeleton
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(20.dp),
                    shape = RoundedCornerShape(4.dp),
                    baseColor = Color.White.copy(alpha = 0.25f)
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp),
                    shape = RoundedCornerShape(4.dp),
                    baseColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }
    }
}

/**
 * Landscape sidebar panel: nav items (Search / Sources / Settings) centered vertically.
 */
@Composable
private fun HomeSidebarPanel(
    showSearchButton: Boolean,
    searchActive: Boolean,
    isExpertModeEnabled: Boolean,
    showSortButton: Boolean,
    sortMode: HomeAppSortMode,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSourcesPositioned: ((Rect) -> Unit)? = null,
    onSettingsPositioned: ((Rect) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        if (showSearchButton) {
            HomeSidebarNavItem(
                icon = if (searchActive) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                label = stringResource(R.string.home_search_apps),
                isSelected = searchActive,
                onClick = onSearchClick
            )
        }
        if (showSortButton) {
            HomeSidebarNavItem(
                icon = Icons.AutoMirrored.Outlined.Sort,
                label = stringResource(R.string.sort),
                isSelected = sortMode != HomeAppSortMode.MANUAL,
                stateDescription = stringResource(sortMode.labelRes),
                onClick = onSortClick
            )
        }
        HomeSidebarNavItem(
            icon = Icons.Outlined.Source,
            label = stringResource(R.string.sources),
            isSelected = false,
            onClick = onBundlesClick,
            modifier = Modifier.then(
                if (onSourcesPositioned != null) Modifier.onGloballyPositioned { coords ->
                    onSourcesPositioned(coords.boundsInWindow())
                } else Modifier
            )
        )
        HomeSidebarNavItem(
            icon = if (isExpertModeEnabled) Icons.Outlined.Engineering else Icons.Outlined.Settings,
            label = stringResource(R.string.settings),
            isSelected = false,
            onClick = onSettingsClick,
            modifier = Modifier.then(
                if (onSettingsPositioned != null) Modifier.onGloballyPositioned { coords ->
                    onSettingsPositioned(coords.boundsInWindow())
                } else Modifier
            )
        )
    }
}

/**
 * Single sidebar nav item: 52dp tall, 16dp rounded corners, animated colors.
 */
@Composable
private fun HomeSidebarNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "sidebarNavItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sidebarNavItemFg"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics {
                role = Role.Button
                selected = isSelected
                if (stateDescription != null) this.stateDescription = stateDescription
            },
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
