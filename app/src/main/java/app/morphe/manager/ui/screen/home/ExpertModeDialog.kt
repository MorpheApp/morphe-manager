/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.Option
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import kotlinx.coroutines.launch

/**
 * Advanced patch selection and configuration dialog.
 * Shown before patching when expert mode is enabled.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ExpertModeDialog(
    newPatches: Map<Int, Set<String>> = emptyMap(),
    options: Options,
    allPatchesInfo: List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>,
    totalSelectedCount: Int,
    totalPatchesCount: Int,
    hasMultipleBundles: Boolean,
    onPatchToggle: (bundleUid: Int, patchName: String) -> Unit,
    onSelectAll: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Unit,
    onDeselectAll: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Unit,
    onResetToDefault: (bundleUid: Int, allPatches: List<Pair<PatchInfo, Boolean>>) -> Unit,
    onRestoreSaved: (bundleUid: Int) -> Unit = {},
    savedPatches: PatchSelection = emptyMap(),
    onOptionChange: (bundleUid: Int, patchName: String, optionKey: String, value: Any?) -> Unit,
    onResetOptions: (bundleUid: Int, patchName: String) -> Unit,
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
                        effectiveValue == null || (effectiveValue is String && effectiveValue.isBlank())
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
                    patch.name.contains(searchQuery, ignoreCase = true) ||
                            patch.description?.contains(searchQuery, ignoreCase = true) == true
                }
                if (filtered.isEmpty()) null else bundle to filtered
            }
        }
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.expert_mode_title),
        titleTrailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Count badge
                InfoBadge(
                    text = "$totalSelectedCount/$totalPatchesCount",
                    style = if (totalSelectedCount > 0) InfoBadgeStyle.Primary else InfoBadgeStyle.Default,
                    isCompact = true
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
        compactPadding = true,
        scrollable = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search bar
            AnimatedVisibility(
                visible = searchVisible,
                enter = expandVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeIn(tween(MorpheDefaults.ANIMATION_DURATION)),
                exit = shrinkVertically(tween(MorpheDefaults.ANIMATION_DURATION)) + fadeOut(tween(MorpheDefaults.ANIMATION_DURATION))
            ) {
                val focusRequester = remember { FocusRequester() }
                val keyboardController = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                MorpheDialogTextField(
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.Source,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
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
                    onSelectAll = { onSelectAll(bundle.uid, displayPatches) },
                    onDeselectAll = { onDeselectAll(bundle.uid, displayPatches) },
                    onResetToDefault = { onResetToDefault(bundle.uid, allPatches) },
                    onRestoreSaved = { onRestoreSaved(bundle.uid) },
                    hasSavedSelection = savedPatches[bundle.uid]?.isNotEmpty() == true
                )

                if (filteredPatches == null) {
                    // No search results for this bundle
                    EmptyStateContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PatchListWithUniversalSection(
                            patches = filteredPatches,
                            newPatchNames = newPatches[bundle.uid] ?: emptySet(),
                            missingRequiredOptions = patchesWithMissingRequired,
                            onToggle = { onPatchToggle(bundle.uid, it) },
                            onConfigureOptions = {
                                if (!it.options.isNullOrEmpty()) selectedPatchForOptions.value = bundle.uid to it
                            }
                        )
                    }
                }
            } else {
                // Multiple bundles tab layout
                val pagerState = rememberPagerState { allPatchesInfo.size }
                val coroutineScope = rememberCoroutineScope()

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
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
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
                                    InfoBadge(
                                        text = "$enabledCount/$totalCount",
                                        style = if (isSelected && hasResults) InfoBadgeStyle.Primary else InfoBadgeStyle.Default,
                                        isCompact = true,
                                        isCentered = true
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
                            onSelectAll = { onSelectAll(currentBundle.uid, currentFiltered) },
                            onDeselectAll = { onDeselectAll(currentBundle.uid, currentFiltered) },
                            onResetToDefault = { onResetToDefault(currentBundle.uid, currentAllPatches) },
                            onRestoreSaved = { onRestoreSaved(currentBundle.uid) },
                            hasSavedSelection = savedPatches[currentBundle.uid]?.isNotEmpty() == true,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // Reserve space so pager height stays stable when a tab has no results
                        Spacer(modifier = Modifier.height(52.dp))
                    }

                    // Pager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { pageIndex ->
                        val (bundle, _) = allPatchesInfo.getOrNull(pageIndex) ?: return@HorizontalPager
                        val patches = filteredPatchesInfo.firstOrNull { it.first.uid == bundle.uid }?.second

                        if (patches == null) {
                            // No search results for this bundle
                            EmptyStateContent(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PatchListWithUniversalSection(
                                    patches = patches,
                                    newPatchNames = newPatches[bundle.uid] ?: emptySet(),
                                    missingRequiredOptions = patchesWithMissingRequired,
                                    onToggle = { onPatchToggle(bundle.uid, it) },
                                    onConfigureOptions = {
                                        if (!it.options.isNullOrEmpty()) selectedPatchForOptions.value = bundle.uid to it
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Proceed to Patching button
            MorpheDialogButton(
                text = stringResource(R.string.expert_mode_proceed),
                onClick = {
                    // Check if multiple bundles are selected
                    if (hasMultipleBundles) {
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
        MultipleSourcesWarningDialog(
            onDismiss = { showMultipleSourcesWarning.value = false },
            onProceed = {
                showMultipleSourcesWarning.value = false
                onProceed()
            }
        )
    }

    // Options dialog
    selectedPatchForOptions.value?.let { (bundleUid, patch) ->
        PatchOptionsDialog(
            patch = patch,
            isDefaultBundle = bundleUid == 0,
            values = options[bundleUid]?.get(patch.name),
            onValueChange = { key, value ->
                onOptionChange(bundleUid, patch.name, key, value)
            },
            onReset = {
                onResetOptions(bundleUid, patch.name)
            },
            onDismiss = {
                // Show a toast if the patch still has unfilled required options
                if (patch.name in patchesWithMissingRequired) {
                    context.toast(context.getString(R.string.patch_option_required_missing, patch.name))
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

/**
 * Bundle controls: three action buttons (Select All / Default / Deselect All).
 */
@Composable
private fun BundlePatchControls(
    enabledCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onResetToDefault: () -> Unit,
    onRestoreSaved: () -> Unit,
    hasSavedSelection: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Returns a lambda that shows a toast with [label] and then executes [action].
    fun withToast(label: String, action: () -> Unit): () -> Unit = {
        context.toast(label)
        action()
    }

    val selectAllLabel = stringResource(R.string.expert_mode_enable_all)
    val defaultLabel = stringResource(R.string.expert_mode_reset_to_default)
    val restoreLabel = stringResource(R.string.expert_mode_restore_saved)
    val deselectAllLabel = stringResource(R.string.expert_mode_disable_all)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        ActionPillButton(
            onClick = withToast(selectAllLabel, onSelectAll),
            icon = Icons.Outlined.DoneAll,
            contentDescription = selectAllLabel,
            tooltip = selectAllLabel,
            enabled = enabledCount < totalCount,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
        ActionPillButton(
            onClick = withToast(defaultLabel, onResetToDefault),
            icon = Icons.Outlined.Recommend,
            contentDescription = defaultLabel,
            tooltip = defaultLabel,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
        ActionPillButton(
            onClick = withToast(restoreLabel, onRestoreSaved),
            icon = Icons.Outlined.History,
            contentDescription = restoreLabel,
            tooltip = restoreLabel,
            enabled = hasSavedSelection,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
        ActionPillButton(
            onClick = withToast(deselectAllLabel, onDeselectAll),
            icon = Icons.Outlined.ClearAll,
            contentDescription = deselectAllLabel,
            tooltip = deselectAllLabel,
            enabled = enabledCount > 0,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
    }
}


/**
 * Individual patch card with toggle and options button.
 */
@Composable
private fun PatchCard(
    patch: PatchInfo,
    isEnabled: Boolean,
    isNew: Boolean = false,
    hasRequiredOptionsMissing: Boolean = false,
    onToggle: () -> Unit,
    onConfigureOptions: () -> Unit,
    hasOptions: Boolean
) {
    // Localized strings for accessibility
    val settings = stringResource(R.string.settings)
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)
    val patchState = if (isEnabled) enabledState else disabledState

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasRequiredOptionsMissing && isEnabled)
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp)
                    )
                else Modifier
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .semantics {
                stateDescription = patchState
                contentDescription = "${patch.name}, $patchState"
            },
        shape = RoundedCornerShape(14.dp),
        color = when {
            isNew && isEnabled -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
            isNew -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            isEnabled -> MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            else -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)
        },
        contentColor = if (isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        tonalElevation = if (isEnabled) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Patch info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (hasOptions) 8.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Name row: patch name + "New" badge inline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = patch.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled)
                            LocalDialogTextColor.current
                        else
                            LocalDialogSecondaryTextColor.current.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNew) {
                        InfoBadge(
                            text = stringResource(R.string.expert_mode_new_patches),
                            style = InfoBadgeStyle.Primary,
                            isCompact = true
                        )
                    }
                }

                if (!patch.description.isNullOrBlank()) {
                    Text(
                        text = patch.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEnabled)
                            LocalDialogSecondaryTextColor.current
                        else
                            LocalDialogSecondaryTextColor.current.copy(alpha = 0.4f)
                    )
                }
            }

            // Options button (only enabled if patch is enabled)
            if (hasOptions) {
                FilledTonalIconButton(
                    onClick = {
                        // Prevent click propagation to card
                        onConfigureOptions()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .semantics {
                            contentDescription = "${patch.name}, $settings"
                        },
                    enabled = isEnabled,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (hasRequiredOptionsMissing && isEnabled)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        contentColor = if (hasRequiredOptionsMissing && isEnabled)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Empty state content when no patches match search or none selected.
 */
@Composable
private fun EmptyStateContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = LocalDialogSecondaryTextColor.current
            )
            Text(
                text = stringResource(R.string.expert_mode_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalDialogSecondaryTextColor.current,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Represents the resolved UI kind of patch option.
 * Used to drive an exhaustive when-expression in [PatchOptionsDialog].
 */
private sealed interface OptionKind {
    data object StringList      : OptionKind
    data object Color           : OptionKind
    data object PathWithPresets : OptionKind
    data object StringDropdown  : OptionKind
    data object Path            : OptionKind
    data object StringText      : OptionKind
    data object BooleanToggle   : OptionKind
    data object IntLong         : OptionKind
    data object FloatDouble     : OptionKind
    data object ArrayDropdown   : OptionKind
}

/**
 * Resolves the [OptionKind] for a given [option] and its current [value].
 * All type-detection heuristics live here, keeping the UI when-expression clean and exhaustive.
 */
private fun resolveOptionKind(option: Option<*>, value: Any?): OptionKind {
    val t        = option.type.toString()
    val isArray  = t.contains("Array")
    val isString = t.contains("String") && !isArray

    return when {
        // List<String> free-form comma-separated input
        t.contains("List") && t.contains("String") -> OptionKind.StringList

        // Color: string whose key/title hints "color" or value looks like a color literal
        isString && (
                option.title.contains("color", ignoreCase = true) ||
                        option.key.contains("color", ignoreCase = true) ||
                        (value is String && (value.startsWith("#") || value.startsWith("@android:color/")))
                ) -> OptionKind.Color

        // Path/folder string with presets: combined dropdown + path picker
        isString && option.presets?.isNotEmpty() == true && (
                option.description.contains("folder",   ignoreCase = true) ||
                        option.description.contains("mipmap",   ignoreCase = true) ||
                        option.description.contains("drawable", ignoreCase = true)
                ) -> OptionKind.PathWithPresets

        // String with presets: pure dropdown
        isString && option.presets?.isNotEmpty() == true -> OptionKind.StringDropdown

        // Path/folder string without presets: file picker + optional creator buttons
        isString && option.key != "customName" && (
                option.key.contains("icon",   ignoreCase = true) ||
                        option.key.contains("header", ignoreCase = true) ||
                        option.key.contains("custom", ignoreCase = true) ||
                        option.description.contains("folder",   ignoreCase = true) ||
                        option.description.contains("image",    ignoreCase = true) ||
                        option.description.contains("mipmap",   ignoreCase = true) ||
                        option.description.contains("drawable", ignoreCase = true)
                ) -> OptionKind.Path

        // Comma-separated string
        isString && option.presets == null &&
                (value is String && value.contains(",")) -> OptionKind.StringList

        // Plain string text field
        isString -> OptionKind.StringText

        // Boolean toggle
        t.contains("Boolean") -> OptionKind.BooleanToggle

        // Integer / Long numeric input
        (t.contains("Int") || t.contains("Long")) && !isArray -> OptionKind.IntLong

        // Float / Double decimal input
        (t.contains("Float") || t.contains("Double")) && !isArray -> OptionKind.FloatDouble

        // Array: dropdown driven by presets
        isArray -> OptionKind.ArrayDropdown

        // Safe fallback
        else -> OptionKind.StringText
    }
}

/**
 * Options dialog for configuring patch options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatchOptionsDialog(
    patch: PatchInfo,
    isDefaultBundle: Boolean,
    values: Map<String, Any?>?,
    onValueChange: (String, Any?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    // Derive the target package from the patch's compatible packages list
    val packageName = patch.compatiblePackages?.firstOrNull()?.packageName.orEmpty()

    val showColorPicker = remember { mutableStateOf<Pair<String, String>?>(null) }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = patch.name,
        titleTrailingContent = {
            IconButton(onClick = onReset) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = stringResource(R.string.reset),
                    tint = LocalDialogTextColor.current
                )
            }
        },
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!patch.description.isNullOrBlank()) {
                Text(
                    text = patch.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            if (patch.options == null) return@Column

            patch.options.forEach { option ->
                val key   = option.key
                val value = if (values == null || key !in values) option.default else values[key]

                when (resolveOptionKind(option, value)) {
                    OptionKind.StringList -> ListStringInputOption(
                        title = option.title,
                        description = option.description,
                        value = when (value) {
                            is List<*> -> value.filterIsInstance<String>()
                            is String  -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            else       -> emptyList()
                        },
                        onValueChange = { newList ->
                            // Check the KType classifier to determine how the patcher expects the value.
                            // List<String> options need a real List<String>, while plain String options expect a comma-separated String.
                            if (option.type.classifier == List::class) {
                                onValueChange(key, newList.ifEmpty { null })
                            } else {
                                onValueChange(key, newList.joinToString(", ").ifBlank { null })
                            }
                        }
                    )

                    OptionKind.Color -> ColorOptionWithPresets(
                        title = option.title,
                        description = option.description,
                        value = value as? String ?: "#000000",
                        presets = option.presets,
                        onPresetSelect = { onValueChange(key, it) },
                        onCustomColorClick = {
                            showColorPicker.value = key to (value as? String ?: "#000000")
                        }
                    )

                    OptionKind.PathWithPresets -> {
                        val presets = option.presets as Map<String, Any?>
                        PathWithPresetsOption(
                            title = option.title,
                            description = option.description,
                            value = value?.toString() ?: "",
                            presets = presets,
                            packageName = packageName,
                            isDefaultBundle = isDefaultBundle,
                            required = option.required,
                            onValueChange = { onValueChange(key, it) }
                        )
                    }

                    OptionKind.StringDropdown -> {
                        val presets = option.presets as Map<String, Any?>
                        DropdownOptionItem(
                            title = option.title,
                            description = option.description,
                            value = value?.toString() ?: "",
                            presets = presets,
                            onValueChange = { onValueChange(key, it) }
                        )
                    }

                    OptionKind.Path -> PathInputOption(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        packageName = packageName,
                        isDefaultBundle = isDefaultBundle,
                        required = option.required,
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.StringText -> TextInputOption(
                        title = option.title,
                        value = value?.toString() ?: "",
                        required = option.required,
                        keyboardType = KeyboardType.Text,
                        // Pass "" explicitly so the field stays visually cleared after
                        // the user taps ✕. updateOption stores "" as a valid value (key
                        // is kept in the map), which prevents the repository from re-injecting
                        // the bundled default on the next load.
                        // "" is stripped back to null (→ patcher default) in
                        // Options.sanitizeForPatcher() before being sent to the patcher.
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.BooleanToggle -> BooleanOptionItem(
                        title = option.title,
                        description = option.description,
                        value = value as? Boolean == true,
                        onValueChange = { onValueChange(key, it) }
                    )

                    OptionKind.IntLong -> TextInputOption(
                        title = option.title,
                        value = (value as? Number)?.toLong()?.toString() ?: "",
                        required = option.required,
                        keyboardType = KeyboardType.Number,
                        onValueChange = { it.toLongOrNull()?.let { num -> onValueChange(key, num) } }
                    )

                    OptionKind.FloatDouble -> TextInputOption(
                        title = option.title,
                        value = (value as? Number)?.toFloat()?.toString() ?: "",
                        required = option.required,
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = { it.toFloatOrNull()?.let { num -> onValueChange(key, num) } }
                    )

                    OptionKind.ArrayDropdown -> DropdownOptionItem(
                        title = option.title,
                        description = option.description,
                        value = value?.toString() ?: "",
                        presets = option.presets ?: emptyMap(),
                        onValueChange = { onValueChange(key, it) }
                    )
                }
            }
        }
    }

    // Color picker dialog
    showColorPicker.value?.let { (key, currentColor) ->
        ColorPickerDialog(
            title = patch.options?.find { it.key == key }?.title ?: key,
            currentColor = currentColor,
            onColorSelected = { newColor ->
                onValueChange(key, newColor)
                showColorPicker.value = null
            },
            onDismiss = { showColorPicker.value = null }
        )
    }
}

@Composable
private fun ColorOptionWithPresets(
    title: String,
    description: String,
    value: String,
    presets: Map<String, *>?,
    onPresetSelect: (String) -> Unit,
    onCustomColorClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title and description
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
        }

        // Presets
        if (!presets.isNullOrEmpty()) {
            presets.forEach { (label, presetValue) ->
                val colorValue = presetValue?.toString() ?: return@forEach
                ColorPresetItem(
                    label = label,
                    colorValue = colorValue,
                    isSelected = value == colorValue,
                    onClick = { onPresetSelect(colorValue) }
                )
            }
        }

        val isValueInPresets = presets?.values?.any { it.toString() == value } == true
        val isCustomSelected = !isValueInPresets

        // Custom color button
        ColorPresetItem(
            label = stringResource(R.string.custom_color),
            colorValue = value,
            isSelected = isCustomSelected,
            isCustom = true,
            onClick = onCustomColorClick
        )
    }
}

@Composable
fun ColorPresetItem(
    label: String,
    colorValue: String,
    isSelected: Boolean,
    isCustom: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)

    val isMaterialYou = colorValue.contains("system_neutral", ignoreCase = true) ||
            colorValue.contains("system_accent", ignoreCase = true) ||
            colorValue.contains("material_you", ignoreCase = true)

    val parsedColor = if (!isMaterialYou) {
        when (colorValue) {
            "@android:color/transparent" -> Color.Transparent
            "@android:color/black", "#000000", "#FF000000" -> Color.Black
            "@android:color/white", "#FFFFFF", "#ffffff", "#FFFFFFFF" -> Color.White
            else -> colorValue.toColorOrNull()
        }
    } else null

    val hasTransparency = parsedColor != null && parsedColor.alpha < 0.99f

    val contentColor = parsedColor?.let {
        val opaque = it.copy(alpha = 1f)
        // For very transparent colors the checkerboard dominates (light background)
        // For opaque/semi-opaque colors use effective luminance against dark background
        val effectiveLuminance = if (it.alpha < 0.4f) {
            // Blend against white checkerboard
            opaque.luminance() * it.alpha + (1f - it.alpha)
        } else {
            opaque.luminance() * it.alpha
        }
        if (effectiveLuminance > 0.18f) Color.Black.copy(alpha = 0.85f)
        else Color.White.copy(alpha = 0.9f)
    } ?: MaterialTheme.colorScheme.onSurface

    val borderColor = parsedColor?.let {
        if (it.luminance() > 0.35f) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)
    } ?: if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .then(
                when {
                    isCustom -> if (parsedColor != null) Modifier.background(Color.Transparent, shape)
                    else Modifier.background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape
                    )
                    isMaterialYou -> Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF6650A4).copy(alpha = 0.25f),
                                Color(0xFF4B86B4).copy(alpha = 0.25f),
                                Color(0xFF2D9596).copy(alpha = 0.25f),
                            )
                        )
                    )
                    parsedColor != null -> Modifier.background(Color.Transparent, shape)
                    else -> Modifier.background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape
                    )
                }
            )
            .border(1.dp, borderColor, shape)
    ) {
        // Checkerboard underlay for transparent/semi-transparent colors
        if (parsedColor != null && hasTransparency) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cellSize = 12.dp.toPx()
                val cols = (size.width / cellSize).toInt() + 1
                val rows = (size.height / cellSize).toInt() + 1
                for (row in 0..rows) {
                    for (col in 0..cols) {
                        val isLight = (row + col) % 2 == 0
                        drawRect(
                            color = if (isLight) Color.White else Color(0xFFCCCCCC),
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
        // Color overlay
        if (parsedColor != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(parsedColor)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCustom || isMaterialYou || parsedColor != null) {
                Icon(
                    imageVector = when {
                        isCustom -> Icons.Outlined.Palette
                        isMaterialYou -> Icons.Outlined.AutoAwesome
                        else -> Icons.Outlined.Palette
                    },
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = if (isCustom) stringResource(R.string.custom_color) else label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PathInputOption(
    title: String,
    description: String,
    value: String,
    packageName: String,
    isDefaultBundle: Boolean,
    required: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val showIconCreator = remember { mutableStateOf(false) }
    val showHeaderCreator = remember { mutableStateOf(false) }
    val isInvalid = required && value.isBlank()

    // Detect if this is icon-related or header-related field
    // Check header first, then icon (header takes priority)
    val isHeaderField = title.contains("header", ignoreCase = true) ||
            description.contains("header", ignoreCase = true)

    val isIconField = !isHeaderField && (
            title.contains("icon", ignoreCase = true) ||
                    description.contains("mipmap", ignoreCase = true)
            )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Folder picker button (needs permissions for icon/header creation)
        val folderPicker = rememberFolderPickerWithPermission { uri ->
            // Convert URI to path for patch options compatibility
            onValueChange(uri.toFilePath())
        }

        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(if (required) "$title *" else title)
            },
            placeholder = {
                Text("/storage/emulated/0/folder")
            },
            isError = isInvalid,
            showClearButton = true,
            onFolderPickerClick = { folderPicker() }
        )

        // Create Icon button (only for the default Morphe bundle)
        if (isIconField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.adaptive_icon_create),
                onClick = { showIconCreator.value = true },
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Create Header button (only for the default Morphe bundle)
        if (isHeaderField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.header_creator_create),
                onClick = { showHeaderCreator.value = true },
                icon = Icons.Outlined.Image,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Instructions
        if (description.isNotBlank()) {
            ExpandableSurface(
                title = stringResource(R.string.patch_option_instructions),
                content = {
                    ScrollableInstruction(
                        description = description,
                        maxHeight = 280.dp
                    )
                }
            )
        }
    }

    // Icon creator dialog
    if (showIconCreator.value) {
        AdaptiveIconCreatorDialog(
            packageName = packageName,
            onDismiss = { showIconCreator.value = false },
            onIconCreated = { path ->
                onValueChange(path)
                showIconCreator.value = false
            }
        )
    }

    // Header creator dialog
    if (showHeaderCreator.value) {
        HeaderCreatorDialog(
            packageName = packageName,
            onDismiss = { showHeaderCreator.value = false },
            onHeaderCreated = { path ->
                onValueChange(path)
                showHeaderCreator.value = false
            }
        )
    }
}

/**
 * Combined path input with dropdown presets.
 * Used for options that have predefined values but also allow custom folder paths.
 */
@Composable
private fun PathWithPresetsOption(
    title: String,
    description: String,
    value: String,
    presets: Map<String, *>,
    packageName: String,
    isDefaultBundle: Boolean,
    required: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val showIconCreator = remember { mutableStateOf(false) }
    val showHeaderCreator = remember { mutableStateOf(false) }

    // Detect if this is icon-related or header-related field
    // Check header first, then icon (header takes priority)
    val isHeaderField = title.contains("header", ignoreCase = true) ||
            description.contains("header", ignoreCase = true)

    val isIconField = !isHeaderField && (
            title.contains("icon", ignoreCase = true) ||
                    description.contains("mipmap", ignoreCase = true)
            )

    // Convert presets to Map<String, String> for dropdown
    val dropdownItems = presets.mapValues { it.value.toString() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Folder picker
        val folderPicker = rememberFolderPickerWithPermission { uri ->
            onValueChange(uri.toFilePath())
        }

        // Dropdown TextField with folder picker and clear button
        MorpheDialogDropdownTextField(
            value = value,
            onValueChange = onValueChange,
            dropdownItems = dropdownItems,
            label = if (required) ({ Text("$title *") }) else null,
            placeholder = {
                Text("/storage/emulated/0/folder")
            },
            showClearButton = true,
            onFolderPickerClick = { folderPicker() }
        )

        // Create Icon button (only for the default Morphe bundle)
        if (isIconField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.adaptive_icon_create),
                onClick = { showIconCreator.value = true },
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Create Header button (only for the default Morphe bundle)
        if (isHeaderField && isDefaultBundle) {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.header_creator_create),
                onClick = { showHeaderCreator.value = true },
                icon = Icons.Outlined.Image,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Instructions (collapsed by default)
        if (description.isNotBlank()) {
            ExpandableSurface(
                title = stringResource(R.string.patch_option_instructions),
                content = {
                    ScrollableInstruction(
                        description = description,
                        maxHeight = 200.dp
                    )
                },
                icon = Icons.Outlined.Info,
                initialExpanded = false
            )
        }
    }

    // Icon creator dialog
    if (showIconCreator.value) {
        AdaptiveIconCreatorDialog(
            packageName = packageName,
            onDismiss = { showIconCreator.value = false },
            onIconCreated = { path ->
                onValueChange(path)
                showIconCreator.value = false
            }
        )
    }

    // Header creator dialog
    if (showHeaderCreator.value) {
        HeaderCreatorDialog(
            packageName = packageName,
            onDismiss = { showHeaderCreator.value = false },
            onHeaderCreated = { path ->
                onValueChange(path)
                showHeaderCreator.value = false
            }
        )
    }
}

@Composable
private fun TextInputOption(
    title: String,
    value: String,
    required: Boolean = false,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    val isInvalid = required && value.isBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(if (required) "$title *" else title)
            },
            placeholder = {
                Text(
                    stringResource(
                        when (keyboardType) {
                            KeyboardType.Number -> R.string.patch_option_enter_number
                            KeyboardType.Decimal -> R.string.patch_option_enter_decimal
                            else -> R.string.patch_option_enter_value
                        }
                    )
                )
            },
            isError = isInvalid,
            showClearButton = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

@Composable
private fun BooleanOptionItem(
    title: String,
    description: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)

    RichSettingsItem(
        onClick = { onValueChange(!value) },
        title = title,
        subtitle = description.ifBlank { null },
        trailingContent = {
            Switch(
                checked = value,
                onCheckedChange = onValueChange,
                modifier = Modifier.semantics {
                    stateDescription = if (value) enabledState else disabledState
                }
            )
        }
    )
}

/**
 * Inline option row that shows current item count and opens [ListStringEditorDialog].
 */
@Composable
private fun ListStringInputOption(
    title: String,
    description: String,
    value: List<String>,
    onValueChange: (List<String>) -> Unit
) {
    val showEditor = remember { mutableStateOf(false) }
    val textColor = LocalDialogTextColor.current
    val secondaryColor = LocalDialogSecondaryTextColor.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showEditor.value = true },
        shape = RoundedCornerShape(12.dp),
        color = textColor.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (value.isNotEmpty()) {
                    InfoBadge(
                        text = "${value.size}",
                        style = InfoBadgeStyle.Primary,
                        isCompact = true
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = secondaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showEditor.value) {
        ListStringEditorDialog(
            title = title,
            description = description,
            initialItems = value,
            onDismiss = { showEditor.value = false },
            onConfirm = { newList ->
                onValueChange(newList)
                showEditor.value = false
            }
        )
    }
}

/**
 * Dialog for managing a list of string values.
 */
@SuppressLint("MutableCollectionMutableState")
@Composable
private fun ListStringEditorDialog(
    title: String,
    description: String,
    initialItems: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var items by remember { mutableStateOf(initialItems.toMutableStateList()) }
    var inputText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }

    fun addItem() {
        val trimmed = inputText.trim()
        if (trimmed.isBlank()) {
            inputError = true
            return
        }
        if (trimmed in items) {
            inputError = true
            return
        }
        items.add(trimmed)
        inputText = ""
        inputError = false
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissOnClickOutside = false,
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = { onConfirm(items.toList()) },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Description
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalDialogSecondaryTextColor.current
                )
            }

            // Input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorpheDialogTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        inputError = false
                    },
                    placeholder = { Text(stringResource(R.string.patch_option_enter_value)) },
                    isError = inputError,
                    showClearButton = inputText.isNotBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { addItem() }
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(onClick = { addItem() }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.add)
                    )
                }
            }

            if (inputError) {
                Text(
                    text = stringResource(
                        if (inputText.trim() in items)
                            R.string.patch_option_list_duplicate
                        else
                            R.string.patch_option_list_empty
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Items list
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.patch_option_list_empty_state),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        ListStringItemRow(
                            value = item,
                            onRemove = { items.removeAt(index) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single item row inside [ListStringEditorDialog].
 */
@Composable
private fun ListStringItemRow(
    value: String,
    onRemove: () -> Unit
) {
    val textColor = LocalDialogTextColor.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = textColor.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.remove),
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DropdownOptionItem(
    title: String,
    description: String,
    value: String,
    presets: Map<String, Any?>,
    onValueChange: (Any?) -> Unit
) {
    // Convert presets to String map for dropdown: display name -> value as string
    val dropdownItems = presets.mapValues { it.value?.toString() ?: "" }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = LocalDialogTextColor.current
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalDialogSecondaryTextColor.current
                )
            }
        }

        MorpheDialogDropdownTextField(
            value = value,
            onValueChange = { newValue ->
                // Try to find the actual value from presets by matching the string representation
                val actualValue = presets.entries.find { it.value?.toString() == newValue }?.value
                    ?: newValue
                onValueChange(actualValue)
            },
            dropdownItems = dropdownItems
        )
    }
}

@Composable
fun ExpandableSurface(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    initialExpanded: Boolean = false,
    headerTint: Color = LocalDialogTextColor.current
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MorpheDefaults.ANIMATION_DURATION),
        label = "rotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = headerTint.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = headerTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = headerTint
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded)
                        stringResource(R.string.collapse)
                    else
                        stringResource(R.string.expand),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle),
                    tint = LocalDialogTextColor.current.copy(alpha = 0.7f)
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                content()
            }
        }
    }
}

/**
 * Scrollable instructions box with fade at bottom.
 */
@Composable
fun ScrollableInstruction(
    description: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 300.dp
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MorpheSettingsDivider(fullWidth = true)

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f
            )
        }

        // Fade at bottom
        val showFade by remember {
            derivedStateOf { scrollState.value < scrollState.maxValue }
        }

        if (showFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Warning dialog shown when user selects patches from multiple sources.
 */
@Composable
private fun MultipleSourcesWarningDialog(
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.expert_mode_multiple_sources_warning_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onPrimaryClick = onProceed,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Text(
            text = stringResource(R.string.expert_mode_multiple_sources_warning_message),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalDialogSecondaryTextColor.current
        )
    }
}
