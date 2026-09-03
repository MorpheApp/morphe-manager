/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*

/**
 * Dialog that shows available patches for a specific app.
 * Shown when the user swipes right on a home app card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPatchesDialog(
    item: HomeAppItem,
    patchesByBundle: Map<Int, List<PatchInfo>>,
    bundleNames: Map<Int, String>,
    onDismiss: () -> Unit,
    isLoading: Boolean = false
) {
    // Flatten to a list of (bundleUid, patch).
    // Bundle ordering: bundles with at least one specific patch come first (by name),
    // then bundles with only universal patches (by name).
    // Within each bundle: specific patches first (alphabetically), universal patches last (alphabetically).
    val allPatches = remember(patchesByBundle, bundleNames) {
        patchesByBundle.entries
            .sortedWith(
                compareBy(
                    { (_, patches) -> patches.all { it.isUniversal } },
                    { (uid, _) -> bundleNames[uid] ?: uid.toString() }
                )
            )
            .flatMap { (uid, patches) ->
                val (universal, specific) = patches.partition { it.isUniversal }
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
    val collapsedBundles = remember { mutableStateOf(emptySet<Int>()) }
    val expandedUniversal = rememberUniversalSectionState()

    val filteredPatches = remember(allPatches, searchQuery.value, selectedBundle.value) {
        allPatches.filter { (uid, patch) ->
            val bundleMatch = selectedBundle.value == null || uid == selectedBundle.value
            val queryMatch = searchQuery.value.isBlank() ||
                    patch.displayName.contains(searchQuery.value, ignoreCase = true) ||
                    patch.description?.contains(searchQuery.value, ignoreCase = true) == true
            bundleMatch && queryMatch
        }
    }

    val isFiltering = searchQuery.value.isNotBlank() || selectedBundle.value != null
    val totalCount = allPatches.size

    // Group filtered patches by bundle, preserving order. Consumed by the collapsible list below
    val groupedFilteredPatches: List<Pair<Int, List<PatchInfo>>> = remember(filteredPatches) {
        if (filteredPatches.isEmpty()) return@remember emptyList()
        val result = mutableListOf<Pair<Int, MutableList<PatchInfo>>>()
        filteredPatches.forEach { (uid, patch) ->
            val last = result.lastOrNull()
            if (last?.first == uid) {
                last.second.add(patch)
            } else {
                result.add(uid to mutableListOf(patch))
            }
        }
        result.map { it.first to it.second.toList() }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = true,
        title = null,
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        // Back unwinds the active filters before the dialog itself. Registered last so the
        // query clears first, and kept off onDismissRequest so an outside tap still dismisses.
        BackHandler(enabled = selectedBundle.value != null) { selectedBundle.value = null }
        BackHandler(enabled = searchQuery.value.isNotBlank()) { searchQuery.value = "" }

        val listState = rememberLazyListState()
        val activeBundleLabel = remember { mutableStateOf("") }
        LaunchedEffect(selectedBundle.value) {
            val uid = selectedBundle.value ?: return@LaunchedEffect
            activeBundleLabel.value = bundleNames[uid] ?: uid.toString()
        }

        // A list read from the database arrives a frame or two late, and the search row over an
        // empty list reads as "this app has no patches" until it does
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = Animations.fadeCrossfade(),
            label = "app_patches_loading"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PulsingLogoIndicator()
                }
                return@AnimatedContent
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
            ) {
                PatchesListSearchRow(
                    searchQuery = searchQuery.value,
                    onSearchQueryChange = { searchQuery.value = it },
                    showFilterButton = isMultiBundle,
                    isFilterActive = selectedBundle.value != null,
                    onFilterClick = { showFilterSheet.value = true }
                )

                AnimatedVisibility(
                    visible = selectedBundle.value != null,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InputChip(
                            selected = true,
                            onClick = { selectedBundle.value = null },
                            label = { Text(activeBundleLabel.value) },
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

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
                    ) {
                        // App header
                        item {
                            PatchesListHeaderCard(
                                title = item.displayName,
                                totalCount = totalCount,
                                filteredCount = filteredPatches.size,
                                isFiltering = isFiltering
                            )
                        }

                        if (filteredPatches.isEmpty()) {
                            item(key = "empty_state") {
                                PatchesListEmptyState(
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // Patch cards grouped by bundle
                        groupedFilteredPatches.forEach { (uid, bundlePatches) ->
                            // Bundle section header (collapsible) - only for multi-bundle
                            if (isMultiBundle) {
                                item(key = "header_$uid") {
                                    val isCollapsed = uid in collapsedBundles.value
                                    val expandLabel = stringResource(R.string.expand)
                                    val collapseLabel = stringResource(R.string.collapse)
                                    HomeGlassCategoryRow(
                                        title = bundleNames[uid] ?: uid.toString(),
                                        leading = {
                                            Icon(
                                                imageVector = Icons.Outlined.Layers,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        color = rememberAccentCardColor(bundleAccentColors[uid]),
                                        trailing = {
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                                contentDescription = if (isCollapsed) expandLabel else collapseLabel,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            collapsedBundles.value = if (isCollapsed) {
                                                collapsedBundles.value - uid
                                            } else {
                                                collapsedBundles.value + uid
                                            }
                                        },
                                        cornerRadius = Defaults.SettingsCornerRadius,
                                        modifier = Modifier.animatedListItem(this)
                                    )
                                }
                            }

                            if (uid !in collapsedBundles.value) {
                                val (specificPatches, universalPatches) = bundlePatches.partition { !it.isUniversal }

                                patchSectionRows(
                                    sectionKey = uid,
                                    specific = specificPatches,
                                    universal = universalPatches,
                                    key = { patch: PatchInfo ->
                                        "$uid:${patch.name}:${patch.compatiblePackages?.joinToString { it.packageName.orEmpty() }.orEmpty()}"
                                    },
                                    isFiltering = isFiltering,
                                    isUniversalExpanded = uid in expandedUniversal,
                                    onUniversalExpandedChange = { expandedUniversal.setExpanded(uid, it) },
                                    accentColor = bundleAccentColors[uid]
                                ) { patch ->
                                    PatchItemCard(
                                        patch = patch,
                                        saveStateKey = "app_patches_${item.id}_$uid",
                                        accentColor = bundleAccentColors[uid],
                                        modifier = Modifier.animatedListItem(this)
                                    )
                                }
                            }
                        }
                    }

                    ListScrollbar(
                        listState = listState,
                        modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                    )

                    ScrollToTopButton(
                        listState = listState,
                        modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                    )
                }
            }
        }
    }

    // Bundle filter bottom sheet (multi-bundle only)
    if (showFilterSheet.value && isMultiBundle) {
        AppBottomSheet(
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
                    AppFilterChip(
                        selected = selectedBundle.value == null,
                        onClick = { selectedBundle.value = null },
                        label = stringResource(R.string.all),
                        selectedIcon = Icons.Outlined.DoneAll
                    )
                    // Per-bundle chips
                    bundleNames.entries
                        .sortedBy { it.value }
                        .forEach { (uid, name) ->
                            val isSelected = uid == selectedBundle.value
                            AppFilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedBundle.value = if (isSelected) null else uid
                                    showFilterSheet.value = false
                                },
                                label = name
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
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_app_hide_title),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.hide),
                primaryIcon = Icons.Outlined.VisibilityOff,
                onPrimaryClick = onHide,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        },
        padding = DialogPadding.Compact
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Original app card preview
            AppCardLayout(
                gradientColors = item.gradientColors,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                AppCardContent(
                    packageName = item.id,
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
        val currentPackages = hiddenAppItems.mapTo(mutableSetOf()) { it.id }
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

    val startConfig = remember(unhideLabel, tertiaryContainer, onTertiaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Visibility,
            label = unhideLabel,
            containerColor = tertiaryContainer,
            contentColor = onTertiaryContainer
        )
    }
    val endConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    AppDialog(
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
                        selectedPackages.setAll(hiddenAppItems.map { it.id })
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
                AppDialogOutlinedButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        padding = DialogPadding.Compact,
        scrollable = false
    ) {
        if (hiddenAppItems.isEmpty()) {
            HomeEmptyState(
                icon = Icons.Outlined.Visibility,
                title = stringResource(R.string.home_app_no_hidden)
            )
        } else {
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    items(
                        items = hiddenAppItems,
                        key = { it.id }
                    ) { item ->
                        val isSelected = selectedPackages.contains(item.id)
                        val offsetX = remember(item.id) { Animatable(0f) }

                        // Snap card back when entering multi-select
                        LaunchedEffect(isMultiSelectMode.value) {
                            if (isMultiSelectMode.value) offsetX.animateTo(0f, tween(200))
                        }

                        SelectableCard(
                            modifier = Modifier.animatedListItem(this),
                            isSelected = isSelected,
                            isSelectionMode = isMultiSelectMode.value
                        ) {
                            SwipeableCardContainer(
                                offsetX = offsetX,
                                actionThresholdPx = actionThresholdPx,
                                onSwipeToStart = { onUnhide(item.id) },
                                onSwipeToEnd = { onShowPatches(item) },
                                startHaptic = HapticFeedbackConstants.LONG_PRESS,
                                endHaptic = HapticFeedbackConstants.VIRTUAL_KEY,
                                enabled = !isMultiSelectMode.value,
                                background = { startProgress, endProgress ->
                                    SwipeBackground(
                                        startProgress = startProgress,
                                        endProgress = endProgress,
                                        startConfig = startConfig,
                                        endConfig = endConfig,
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(24.dp))
                                    )
                                }
                            ) {
                                AppCardLayout(
                                    gradientColors = item.gradientColors,
                                    onClick = {
                                        if (isMultiSelectMode.value) {
                                            selectedPackages.toggle(item.id)
                                        } else {
                                            onUnhide(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        isMultiSelectMode.value = true
                                        selectedPackages.toggle(item.id)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AppCardContent(
                                        packageName = item.id,
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

                ListScrollbar(
                    listState = listState,
                    modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                )

                ScrollToTopButton(
                    listState = listState,
                    modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                )
            }
        }
    }
}
