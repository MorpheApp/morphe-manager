/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.toast
import kotlinx.coroutines.launch

/** Callbacks the expert-mode dialog invokes on the underlying patch selection. */
@Stable
class ExpertPatchActions(
    val onPatchToggle: (bundleUid: Int, patchName: String) -> Unit,
    val onSelectAll: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Unit,
    val onDeselectAll: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Unit,
    val onResetToDefault: (bundleUid: Int, allPatches: List<Pair<PatchInfo, Boolean>>) -> Unit,
    val onRestoreSaved: (bundleUid: Int) -> Unit,
    val onCopyFromBundle: (bundleUid: Int) -> Unit,
    val onOptionChange: (bundleUid: Int, patchName: String, optionKey: String, value: Any?) -> Unit,
    val onResetOptions: (bundleUid: Int, patchName: String) -> Unit
)

/**
 * Advanced patch selection and configuration dialog.
 * Shown before patching when expert mode is enabled.
 */
@Composable
fun ExpertModeDialog(
    newPatches: Map<Int, Set<String>> = emptyMap(),
    options: Options,
    allPatchesInfo: List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>,
    totalSelectedCount: Int,
    totalPatchesCount: Int,
    hasMultipleBundles: Boolean,
    patchActions: ExpertPatchActions,
    savedPatches: PatchSelection = emptyMap(),
    lockStateOf: (PatchInfo) -> PatchLockState = { PatchLockState.NONE },
    proceedText: String = stringResource(R.string.expert_mode_proceed),
    /** Off where mixing sources is the norm rather than something the user just did. */
    warnOnMultipleBundles: Boolean = true,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    val selectedPatchForOptions = remember { mutableStateOf<Pair<Int, PatchInfo>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    val showMultipleSourcesWarning = remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Compute set of enabled patch names that have at least one required option
    // with no default (default == null) and no user-provided non-blank value.
    // Recomputed whenever the selected patches or options change.
    val patchesWithMissingRequired: Set<String> = remember(allPatchesInfo, options) {
        buildSet {
            allPatchesInfo.forEach { (bundle, patches) ->
                patches.forEach { (patch, isEnabled) ->
                    if (!isEnabled) return@forEach
                    val patchValues = options[bundle.uid]?.get(patch.name)
                    val hasMissing = patch.options?.any { option ->
                        if (!option.required) return@any false
                        val savedValue = patchValues?.get(option.key)
                        val effectiveValue = savedValue ?: option.default
                        // Treat blank as missing only when the developer's own default is non-blank
                        effectiveValue == null || (
                            effectiveValue is String && effectiveValue.isBlank() &&
                            !(option.default is String && option.default.isBlank())
                        )
                    } == true
                    if (hasMissing) add(patch.name)
                }
            }
        }
    }

    // Filter patches based on search query
    val filteredPatchesInfo = remember(allPatchesInfo, searchQuery) {
        if (searchQuery.isBlank()) {
            allPatchesInfo
        } else {
            allPatchesInfo.mapNotNull { (bundle, patches) ->
                val filtered = patches.filter { (patch, _) ->
                    patch.displayName.contains(searchQuery, ignoreCase = true) ||
                            patch.description?.contains(searchQuery, ignoreCase = true) == true
                }
                if (filtered.isEmpty()) null else bundle to filtered
            }
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.expert_mode_title),
        titleTrailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Count badge
                StatusBadge(
                    text = "$totalSelectedCount/$totalPatchesCount",
                    tone = if (totalSelectedCount > 0) SemanticTone.Primary else SemanticTone.Neutral
                )

                // Search toggle button
                FilledTonalIconButton(
                    onClick = {
                        if (searchVisible) searchQuery = ""
                        searchVisible = !searchVisible
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (searchVisible)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (searchVisible)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = if (searchVisible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.expert_mode_search),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        dismissOnClickOutside = false,
        footer = null,
        padding = DialogPadding.Compact,
        scrollable = false
    ) {
        BackHandler(enabled = searchVisible) {
            searchQuery = ""
            searchVisible = false
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            // Search bar
            AnimatedVisibility(
                visible = searchVisible,
                enter = Animations.expandFadeEnter,
                exit = Animations.shrinkFadeExit
            ) {
                val focusRequester = remember { FocusRequester() }
                val keyboardController = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                AppDialogTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = {
                        Text(stringResource(R.string.expert_mode_search))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.expert_mode_search)
                        )
                    },
                    showClearButton = true,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }

            // Layout mode is determined by total bundle count
            val hasMultipleBundleLayout = allPatchesInfo.size > 1

            if (!hasMultipleBundleLayout) {
                val (bundle, allPatches) = allPatchesInfo.firstOrNull() ?: return@Column
                val filteredPatches = filteredPatchesInfo.firstOrNull { it.first.uid == bundle.uid }?.second
                val displayPatches = filteredPatches ?: emptyList()
                val enabledCount = displayPatches.count { it.second }
                val totalCount = displayPatches.size

                // Bundle name header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusCircleIcon(
                        icon = Icons.Outlined.Source,
                        size = 32.dp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = bundle.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalDialogTextColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BundlePatchControls(
                    enabledCount = enabledCount,
                    totalCount = totalCount,
                    onSelectAll = { patchActions.onSelectAll(bundle.uid, displayPatches) },
                    onDeselectAll = { patchActions.onDeselectAll(bundle.uid, displayPatches) },
                    onResetToDefault = { patchActions.onResetToDefault(bundle.uid, allPatches) },
                    onRestoreSaved = { patchActions.onRestoreSaved(bundle.uid) },
                    onCopyFromBundle = { patchActions.onCopyFromBundle(bundle.uid) },
                    hasSavedSelection = savedPatches[bundle.uid]?.isNotEmpty() == true
                )

                if (filteredPatches == null) {
                    // No search results for this bundle
                    EmptyState(
                        message = stringResource(R.string.expert_mode_no_results),
                        icon = Icons.Outlined.SearchOff,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val singleBundleScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(singleBundleScroll),
                            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                        ) {
                            PatchListWithUniversalSection(
                                patches = filteredPatches,
                                newPatchNames = newPatches[bundle.uid] ?: emptySet(),
                                missingRequiredOptions = patchesWithMissingRequired,
                                lockStateOf = lockStateOf,
                                onToggle = { patchActions.onPatchToggle(bundle.uid, it) },
                                onConfigureOptions = {
                                    if (!it.options.isNullOrEmpty()) selectedPatchForOptions.value = bundle.uid to it
                                }
                            )
                        }

                        ListScrollbar(
                            scrollState = singleBundleScroll,
                            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                        )

                        ScrollToTopButton(
                            scrollState = singleBundleScroll,
                            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                        )
                    }
                }
            } else {
                // Multiple bundles tab layout
                val pagerState = rememberPagerState { allPatchesInfo.size }
                val coroutineScope = rememberCoroutineScope()
                // Created up front, outside the pager, so the scrollbar overlay below can track
                // whichever page is current. HorizontalPager clips each page to its own bounds, so
                // a scrollbar drawn inside a page can never bleed out to the true dialog edge.
                // Keyed on the bundle count so pages never inherit a stale sibling's position
                val pageScrollStates = rememberSaveable(
                    allPatchesInfo.size,
                    saver = listSaver(
                        save = { states -> states.map { it.value } },
                        restore = { offsets -> offsets.map { ScrollState(it) } }
                    )
                ) {
                    List(allPatchesInfo.size) { ScrollState(0) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Tab row
                    SecondaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 0.dp,
                        divider = {},
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        allPatchesInfo.forEachIndexed { index, (bundle, patches) ->
                            val hasResults = filteredPatchesInfo.any { it.first.uid == bundle.uid }
                            val enabledCount = patches.count { it.second }
                            val totalCount = patches.size
                            val isSelected = pagerState.currentPage == index

                            Tab(
                                selected = isSelected,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = if (hasResults)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = Defaults.ItemSpacing, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = bundle.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Patch count badge
                                    StatusBadge(
                                        text = "$enabledCount/$totalCount",
                                        tone = if (isSelected && hasResults) SemanticTone.Primary else SemanticTone.Neutral
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )

                    // Controls fixed below the tab row
                    val currentIndex = pagerState.currentPage
                    val (currentBundle, currentAllPatches) = allPatchesInfo.getOrNull(currentIndex) ?: return@Column
                    val currentFiltered = filteredPatchesInfo.firstOrNull { it.first.uid == currentBundle.uid }?.second

                    if (currentFiltered != null) {
                        BundlePatchControls(
                            enabledCount = currentFiltered.count { it.second },
                            totalCount = currentFiltered.size,
                            onSelectAll = { patchActions.onSelectAll(currentBundle.uid, currentFiltered) },
                            onDeselectAll = { patchActions.onDeselectAll(currentBundle.uid, currentFiltered) },
                            onResetToDefault = { patchActions.onResetToDefault(currentBundle.uid, currentAllPatches) },
                            onRestoreSaved = { patchActions.onRestoreSaved(currentBundle.uid) },
                            onCopyFromBundle = { patchActions.onCopyFromBundle(currentBundle.uid) },
                            hasSavedSelection = savedPatches[currentBundle.uid]?.isNotEmpty() == true,
                            modifier = Modifier.padding(vertical = Defaults.ContentPaddingSmall)
                        )
                    } else {
                        // Reserve space so pager height stays stable when a tab has no results
                        Spacer(modifier = Modifier.height(52.dp))
                    }

                    // Pager
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            val (bundle, _) = allPatchesInfo.getOrNull(pageIndex) ?: return@HorizontalPager
                            val patches = filteredPatchesInfo.firstOrNull { it.first.uid == bundle.uid }?.second

                            if (patches == null) {
                                // No search results for this bundle
                                EmptyState(
                                    message = stringResource(R.string.expert_mode_no_results),
                                    icon = Icons.Outlined.SearchOff,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            } else {
                                val pageScroll = pageScrollStates[pageIndex]
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(pageScroll),
                                    verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                                ) {
                                    PatchListWithUniversalSection(
                                        patches = patches,
                                        newPatchNames = newPatches[bundle.uid] ?: emptySet(),
                                        missingRequiredOptions = patchesWithMissingRequired,
                                        lockStateOf = lockStateOf,
                                        onToggle = { patchActions.onPatchToggle(bundle.uid, it) },
                                        onConfigureOptions = {
                                            if (!it.options.isNullOrEmpty()) selectedPatchForOptions.value = bundle.uid to it
                                        }
                                    )
                                }
                            }
                        }

                        // Single overlay for the whole pager, tracking whichever page is current,
                        // instead of one per page - a page-local scrollbar would be clipped by the
                        // pager before it could reach the true dialog edge. Pages filtered down to
                        // an empty state have nothing to scroll, so they get no overlay
                        val currentPageScroll = allPatchesInfo.getOrNull(pagerState.currentPage)
                            ?.takeIf { (bundle, _) -> filteredPatchesInfo.any { it.first.uid == bundle.uid } }
                            ?.let { pageScrollStates.getOrNull(pagerState.currentPage) }
                        if (currentPageScroll != null) {
                            ListScrollbar(
                                scrollState = currentPageScroll,
                                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                            )

                            ScrollToTopButton(
                                scrollState = currentPageScroll,
                                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                            )
                        }
                    }
                }
            }

            // Proceed to Patching button
            AppDialogButton(
                text = proceedText,
                onClick = {
                    // Check if multiple bundles are selected
                    if (hasMultipleBundles && warnOnMultipleBundles) {
                        showMultipleSourcesWarning.value = true
                    } else {
                        onProceed()
                    }
                },
                enabled = totalSelectedCount > 0,
                icon = Icons.Outlined.AutoFixHigh,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Multiple bundles warning dialog
    if (showMultipleSourcesWarning.value) {
        ConfirmDialog(
            title = stringResource(R.string.expert_mode_multiple_sources_warning_title),
            message = stringResource(R.string.expert_mode_multiple_sources_warning_message),
            primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
            isPrimaryDestructive = false,
            onConfirm = {
                showMultipleSourcesWarning.value = false
                onProceed()
            },
            onDismiss = { showMultipleSourcesWarning.value = false }
        )
    }

    // Options dialog
    val patchForOptions = selectedPatchForOptions.value
    if (patchForOptions != null) {
        val (bundleUid, patch) = patchForOptions
        val missingOptionsMessage = stringResource(R.string.patch_option_required_missing, patch.displayName)
        PatchOptionsDialog(
            patch = patch,
            isDefaultBundle = bundleUid == 0,
            values = options[bundleUid]?.get(patch.name),
            onValueChange = { key, value ->
                patchActions.onOptionChange(bundleUid, patch.name, key, value)
            },
            onReset = {
                patchActions.onResetOptions(bundleUid, patch.name)
            },
            onDismiss = {
                // Show a toast if the patch still has unfilled required options
                if (patch.name in patchesWithMissingRequired) {
                    context.toast(missingOptionsMessage)
                }
                selectedPatchForOptions.value = null
            }
        )
    }
}

/**
 * Renders a patch list split into regular patches and a "Universal patches" section at the bottom.
 * Universal patches are those with no compatible packages defined.
 */
@Composable
private fun PatchListWithUniversalSection(
    patches: List<Pair<PatchInfo, Boolean>>,
    newPatchNames: Set<String> = emptySet(),
    missingRequiredOptions: Set<String> = emptySet(),
    lockStateOf: (PatchInfo) -> PatchLockState = { PatchLockState.NONE },
    onToggle: (String) -> Unit,
    onConfigureOptions: (PatchInfo) -> Unit,
) {
    val (regular, universal) = remember(patches) {
        patches.partition { (patch, _) -> !patch.compatiblePackages.isNullOrEmpty() }
    }

    // New patches float to the top; within each group order is alphabetical
    val sortedRegular = remember(regular, newPatchNames) {
        regular.sortedWith(
            compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) -> patch.name in newPatchNames }
                .thenBy { (patch, _) -> patch.name }
        )
    }
    val sortedUniversal = remember(universal, newPatchNames) {
        universal.sortedWith(
            compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) -> patch.name in newPatchNames }
                .thenBy { (patch, _) -> patch.name }
        )
    }

    sortedRegular.forEach { (patch, isEnabled) ->
        PatchCard(
            patch = patch,
            isEnabled = isEnabled,
            isNew = patch.name in newPatchNames,
            hasRequiredOptionsMissing = patch.name in missingRequiredOptions,
            lockState = lockStateOf(patch),
            onToggle = { onToggle(patch.name) },
            onConfigureOptions = { onConfigureOptions(patch) },
            hasOptions = !patch.options.isNullOrEmpty()
        )
    }

    if (sortedUniversal.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (sortedRegular.isNotEmpty()) 8.dp else 0.dp, bottom = 4.dp),
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

        sortedUniversal.forEach { (patch, isEnabled) ->
            PatchCard(
                patch = patch,
                isEnabled = isEnabled,
                isNew = patch.name in newPatchNames,
                hasRequiredOptionsMissing = patch.name in missingRequiredOptions,
                onToggle = { onToggle(patch.name) },
                onConfigureOptions = { onConfigureOptions(patch) },
                hasOptions = !patch.options.isNullOrEmpty()
            )
        }
    }
}
