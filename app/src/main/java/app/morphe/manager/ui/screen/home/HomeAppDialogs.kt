/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
    val collapsedBundles = remember { mutableStateOf(emptySet<Int>()) }

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

    MorpheDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = true,
        title = null,
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
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
                modifier = Modifier.fillMaxWidth()
            ) {
                // App header
                item {
                    PatchesListHeaderCard(
                        title = item.displayName,
                        totalCount = totalCount,
                        filteredCount = filteredPatches.size,
                        isFiltering = isFiltering,
                        modifier = Modifier.padding(bottom = MorpheDefaults.ContentPaddingSmall)
                    )
                }

                // Search + filter row (filter button visible only for multi-bundle)
                stickyHeader {
                    PatchesListSearchRow(
                        searchQuery = searchQuery.value,
                        onSearchQueryChange = { searchQuery.value = it },
                        showFilterButton = isMultiBundle,
                        isFilterActive = selectedBundle.value != null,
                        onFilterClick = { showFilterSheet.value = true }
                    )
                }

                // Active bundle filter badge (only emitted when a bundle is selected
                if (selectedBundle.value != null) {
                    item(key = "filter_badges") {
                        selectedBundle.value?.let { uid ->
                            FlowRow(
                                modifier = Modifier
                                    .animateItem()
                                    .padding(top = MorpheDefaults.ContentPaddingSmall),
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
                }

                if (filteredPatches.isEmpty()) {
                    item(key = "empty_state") {
                        PatchesListEmptyState(
                            modifier = Modifier
                                .animateItem()
                                .padding(top = MorpheDefaults.ContentPaddingSmall)
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
                                cornerRadius = MorpheDefaults.SettingsCornerRadius,
                                modifier = Modifier
                                    .padding(top = MorpheDefaults.ContentPaddingSmall)
                                    .animateItem(
                                        fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                                        fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION_SHORT),
                                        placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
                                    )
                            )
                        }
                    }

                    if (uid !in collapsedBundles.value) {
                        val firstUniversal = bundlePatches.firstOrNull { it.compatiblePackages == null }
                        val hasSpecificInBundle = bundlePatches.any { it.compatiblePackages != null }
                        items(
                            bundlePatches,
                            key = { patch ->
                                "$uid:${patch.name}:${patch.compatiblePackages?.joinToString { it.packageName.orEmpty() }.orEmpty()}"
                            }
                        ) { patch ->
                            val isUniversal = patch.compatiblePackages == null
                            val isFirstUniversalOfBundle = isUniversal && patch === firstUniversal
                            Column(
                                modifier = Modifier
                                    .padding(top = MorpheDefaults.ContentPaddingSmall)
                                    .animateItem(
                                        fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                                        fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION_SHORT),
                                        placementSpec = spring(stiffness = 400f, dampingRatio = 0.8f)
                                    )
                            ) {
                                // Universal patches divider - before first universal patch of each bundle
                                if (isFirstUniversalOfBundle) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                top = if (hasSpecificInBundle) MorpheDefaults.ContentPaddingSmall else 0.dp,
                                                bottom = 4.dp
                                            ),
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
        padding = DialogPadding.Compact
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
        padding = DialogPadding.Compact,
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
