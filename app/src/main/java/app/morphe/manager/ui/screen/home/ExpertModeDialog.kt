/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.ui.model.renamesByDefault
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.PatchSelectionUtils.hasCustomizedOptions
import app.morphe.manager.util.PatchSelectionUtils.hasEnablableUniversal
import app.morphe.manager.util.PatchSelectionUtils.hasMissingRequiredOptions
import app.morphe.manager.util.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Callbacks the expert-mode dialog invokes on the underlying patch selection. */
@Stable
class ExpertPatchActions(
    val onPatchToggle: (bundleUid: Int, patchName: String) -> Unit,
    val onSelectAll: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Unit,
    val onDeselectAll: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Unit,
    val onResetToDefault: (bundleUid: Int) -> Unit,
    val onRestoreSaved: (bundleUid: Int) -> Unit,
    val onCopyFromBundle: ((bundleUid: Int) -> Unit)? = null,
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
    /** True while "Enable all" still holds the universal patches of the given list back. */
    holdsUniversalPatches: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Boolean = { _, _ -> false },
    proceedText: String = stringResource(R.string.expert_mode_proceed),
    /** Off where mixing sources is the norm rather than something the user just did. */
    warnOnMultipleBundles: Boolean = true,
    /** Bundle uids currently receiving pre-release patch versions, shown as a warning header. */
    prereleaseBundleUids: Set<Int> = emptySet(),
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    val selectedPatchForOptions = remember { mutableStateOf<Pair<Int, PatchInfo>?>(null) }
    val search = rememberSearchFieldState()
    val showMultipleSourcesWarning = remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Both markers are keyed by bundle, since the same patch name can come from several sources
    // with values of its own. Recomputed whenever the selected patches or options change
    val patchesWithMissingRequired: Map<Int, Set<String>> = remember(allPatchesInfo, options) {
        allPatchesInfo.patchNamesWhere { bundle, patch, isEnabled ->
            isEnabled && patch.hasMissingRequiredOptions(options[bundle.uid]?.get(patch.name))
        }
    }

    val patchesWithCustomOptions: Map<Int, Set<String>> = remember(allPatchesInfo, options) {
        allPatchesInfo.patchNamesWhere { bundle, patch, _ ->
            patch.hasCustomizedOptions(options[bundle.uid]?.get(patch.name))
        }
    }

    val expandedUniversal = rememberUniversalSectionState()

    // The pre-release warning is worth a glance, not a permanent strip on top of the list. It
    // retires per source, so a source the user has not opened yet still gets its turn, and the
    // state lives for this dialog only: the next open warns again
    val retiredNotices = remember { mutableStateOf(emptySet<Int>()) }
    fun retireNotice(bundleUid: Int) {
        retiredNotices.value += bundleUid
    }

    // Names the filter holds on to, or null while it is off. A snapshot rather than a live read,
    // so a patch unticked under the filter keeps its place instead of vanishing mid-edit
    val selectedOnly = remember { mutableStateOf<Map<Int, Set<String>>?>(null) }
    val isSelectedOnly = selectedOnly.value != null
    fun toggleSelectedOnly() {
        selectedOnly.value = if (isSelectedOnly) {
            null
        } else {
            allPatchesInfo.patchNamesWhere { _, _, isEnabled -> isEnabled }
        }
    }

    // The two filters stack: either can narrow what the other left
    val filteredPatchesInfo = remember(allPatchesInfo, search.query, selectedOnly.value) {
        val query = search.query.takeIf { it.isNotBlank() }
        val onlySelected = selectedOnly.value
        if (query == null && onlySelected == null) return@remember allPatchesInfo

        allPatchesInfo.mapNotNull { (bundle, patches) ->
            val kept = onlySelected?.get(bundle.uid).orEmpty()
            val filtered = patches.filter { (patch, _) ->
                val matchesQuery = query == null ||
                        patch.displayName.contains(query, ignoreCase = true) ||
                        patch.description?.contains(query, ignoreCase = true) == true
                matchesQuery && (onlySelected == null || patch.name in kept)
            }
            if (filtered.isEmpty()) null else bundle to filtered
        }
    }

    // Both narrow the list far enough that a folded universal section would only hide results
    val isFiltering = search.isFiltering || isSelectedOnly

    val markers = remember(
        newPatches,
        patchesWithMissingRequired,
        patchesWithCustomOptions,
        prereleaseBundleUids,
        retiredNotices.value
    ) {
        PatchMarkers(
            newPatches = newPatches,
            missingRequiredOptions = patchesWithMissingRequired,
            customOptions = patchesWithCustomOptions,
            prereleaseNotices = prereleaseBundleUids - retiredNotices.value
        )
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.expert_mode_title),
        titleTrailingContent = {
            // The counter already stands for the selection, so it doubles as the way to filter
            // the list down to it
            val badgeTone = if (totalSelectedCount > 0) SemanticTone.Primary else SemanticTone.Neutral
            // Still switchable off after the last patch is unticked under the filter
            val canFilter = totalSelectedCount > 0 || isSelectedOnly
            val filterState = stringResource(
                if (isSelectedOnly) {
                    R.string.expert_mode_selected_only_on
                } else {
                    R.string.expert_mode_selected_only_off
                }
            )
            StatusBadge(
                text = "$totalSelectedCount/$totalPatchesCount",
                // Carried while the filter is merely available, so the counter reads as the
                // control it is instead of only announcing itself once tapped
                icon = Icons.Outlined.FilterAlt.takeIf { canFilter },
                tone = badgeTone,
                // Filled rather than tonal while filtering, so the narrowed list has a visible cause
                containerColor = if (isSelectedOnly) MaterialTheme.colorScheme.primary else badgeTone.container,
                contentColor = if (isSelectedOnly) MaterialTheme.colorScheme.onPrimary else badgeTone.content,
                onClick = if (canFilter) {
                    { toggleSelectedOnly() }
                } else {
                    null
                },
                modifier = Modifier.semantics { stateDescription = filterState }
            )

            TitleAction(
                icon = if (search.visible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                contentDescription = stringResource(R.string.expert_mode_search),
                onClick = { search.toggle() },
                style = TitleActionStyle.Toggle,
                active = search.visible
            )
        },
        dismissOnClickOutside = false,
        footer = null,
        padding = DialogPadding.Compact,
        scrollable = false
    ) {
        SearchFieldBackHandler(search)
        // Back unwinds the filter before the dialog itself, the way the search field does
        BackHandler(enabled = isSelectedOnly) { selectedOnly.value = null }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            // Search bar
            AnimatedVisibility(
                visible = search.visible,
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
                    value = search.query,
                    onValueChange = { search.query = it },
                    label = {
                        Text(stringResource(R.string.expert_mode_search))
                    },
                    leadingIcon = {
                        // The label already announces the field, so the icon stays decorative
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null
                        )
                    },
                    showClearButton = true,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }

            // Layout mode is determined by total bundle count
            val hasMultipleBundleLayout = allPatchesInfo.size > 1

            if (!hasMultipleBundleLayout) {
                val (bundle, _) = allPatchesInfo.firstOrNull() ?: return@Column
                val filteredPatches = filteredPatchesInfo.firstOrNull { it.first.uid == bundle.uid }?.second
                val displayPatches = filteredPatches ?: emptyList()

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

                BundleControls(
                    bundle = bundle,
                    patches = displayPatches,
                    patchActions = patchActions,
                    savedPatches = savedPatches,
                    lockStateOf = lockStateOf,
                    holdsUniversalPatches = holdsUniversalPatches,
                    onExpandUniversal = { expandedUniversal.setExpanded(it, true) }
                )

                RetirePrereleaseNotice(
                    bundleUid = bundle.uid.takeIf { it in prereleaseBundleUids },
                    onRetire = { retireNotice(it) }
                )

                val singleBundleList = rememberLazyListState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // The list keeps its place while empty, so its rows fade out under the
                    // empty state rather than the two swapping in a single frame
                    BundlePatchList(
                        bundle = bundle,
                        patches = displayPatches,
                        listState = singleBundleList,
                        markers = markers,
                        isFiltering = isFiltering,
                        isUniversalExpanded = bundle.uid in expandedUniversal,
                        onUniversalExpandedChange = { expandedUniversal.setExpanded(bundle.uid, it) },
                        lockStateOf = lockStateOf,
                        patchActions = patchActions,
                        onConfigureOptions = { selectedPatchForOptions.value = bundle.uid to it }
                    )

                    if (filteredPatches != null) {
                        ListScrollbar(
                            listState = singleBundleList,
                            modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
                        )
                    }

                    PatchesListEmptyOverlay(visible = filteredPatches == null)
                }
            } else {
                // Multiple bundles tab layout
                val pagerState = rememberPagerState { allPatchesInfo.size }
                val coroutineScope = rememberCoroutineScope()
                // Created up front, outside the pager, so the scrollbar overlay below can track
                // whichever page is current. HorizontalPager clips each page to its own bounds, so
                // a scrollbar drawn inside a page can never bleed out to the true dialog edge.
                // Keyed on the bundle count so pages never inherit a stale sibling's position
                val pageListStates = rememberSaveable(
                    allPatchesInfo.size,
                    saver = listSaver(
                        save = { states ->
                            states.flatMap { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) }
                        },
                        restore = { saved ->
                            saved.chunked(2).map { (index, offset) -> LazyListState(index, offset) }
                        }
                    )
                ) {
                    List(allPatchesInfo.size) { LazyListState() }
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
                    val (currentBundle, _) = allPatchesInfo.getOrNull(currentIndex) ?: return@Column
                    val currentFiltered = filteredPatchesInfo.firstOrNull { it.first.uid == currentBundle.uid }?.second

                    RetirePrereleaseNotice(
                        bundleUid = currentBundle.uid.takeIf { it in prereleaseBundleUids },
                        onRetire = { retireNotice(it) }
                    )

                    // Kept in place on a tab the filters emptied, so the pager height holds and
                    // the bulk actions grey out instead of the whole row snapping away
                    BundleControls(
                        bundle = currentBundle,
                        patches = currentFiltered.orEmpty(),
                        patchActions = patchActions,
                        savedPatches = savedPatches,
                        lockStateOf = lockStateOf,
                        holdsUniversalPatches = holdsUniversalPatches,
                        onExpandUniversal = { expandedUniversal.setExpanded(it, true) },
                        modifier = Modifier.padding(vertical = Defaults.ContentPaddingSmall)
                    )

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

                            Box(modifier = Modifier.fillMaxSize()) {
                                BundlePatchList(
                                    bundle = bundle,
                                    patches = patches.orEmpty(),
                                    listState = pageListStates[pageIndex],
                                    markers = markers,
                                    isFiltering = isFiltering,
                                    isUniversalExpanded = bundle.uid in expandedUniversal,
                                    onUniversalExpandedChange = { expandedUniversal.setExpanded(bundle.uid, it) },
                                    lockStateOf = lockStateOf,
                                    patchActions = patchActions,
                                    onConfigureOptions = { selectedPatchForOptions.value = bundle.uid to it }
                                )

                                PatchesListEmptyOverlay(visible = patches == null)
                            }
                        }

                        // Single overlay for the whole pager, tracking whichever page is current,
                        // instead of one per page - a page-local scrollbar would be clipped by the
                        // pager before it could reach the true dialog edge. Pages filtered down to
                        // an empty state have nothing to scroll, so they get no overlay
                        val currentPageList = allPatchesInfo.getOrNull(pagerState.currentPage)
                            ?.takeIf { (bundle, _) -> filteredPatchesInfo.any { it.first.uid == bundle.uid } }
                            ?.let { pageListStates.getOrNull(pagerState.currentPage) }
                        if (currentPageList != null) {
                            ListScrollbar(
                                listState = currentPageList,
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
                if (patch.name in patchesWithMissingRequired[bundleUid].orEmpty()) {
                    context.toast(missingOptionsMessage)
                }
                selectedPatchForOptions.value = null
            }
        )
    }
}

/**
 * Empty state fading in over the list it stands in for, so a filter that clears a page settles
 * instead of swapping the two in one frame.
 */
@Composable
private fun PatchesListEmptyOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.fadeIn,
        exit = Animations.fadeOut
    ) {
        PatchesListEmptyState()
    }
}

/** One bundle's patches, split into the ones written for this app and the universal ones. */
@Immutable
private data class PatchSections(
    val specific: List<Pair<PatchInfo, Boolean>>,
    val universal: List<Pair<PatchInfo, Boolean>>
) {
    /** Enabled universal patches, the ones a folded section would otherwise hide. */
    val selectedUniversalCount = universal.count { (_, isEnabled) -> isEnabled }
}

/** Everything the rows of a bundle badge or warn about, all keyed by bundle uid. */
@Immutable
private data class PatchMarkers(
    val newPatches: Map<Int, Set<String>>,
    val missingRequiredOptions: Map<Int, Set<String>>,
    val customOptions: Map<Int, Set<String>>,
    val prereleaseNotices: Set<Int>
)

/**
 * Splits and orders one bundle's patches for display. New patches float to the top of each
 * group; within a group the order is alphabetical.
 */
@Composable
private fun rememberPatchSections(
    patches: List<Pair<PatchInfo, Boolean>>,
    newPatchNames: Set<String>
): PatchSections = remember(patches, newPatchNames) {
    val displayOrder = compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) ->
        patch.name in newPatchNames
    }.thenBy { (patch, _) -> patch.name }

    val (universal, specific) = patches.partition { (patch, _) -> patch.isUniversal }
    PatchSections(
        specific = specific.sortedWith(displayOrder),
        universal = universal.sortedWith(displayOrder)
    )
}

/** How long a pre-release warning holds its place, long enough to read it once. */
private val PrereleaseNoticeDuration = 8.seconds

/**
 * Retires the pre-release warning of [bundleUid] once it has had its seconds on screen. Null while
 * the source in view carries no warning, and keyed on the bundle, so switching tabs hands the
 * countdown to whichever source the user just opened rather than expiring one never seen.
 */
@Composable
private fun RetirePrereleaseNotice(bundleUid: Int?, onRetire: (Int) -> Unit) {
    LaunchedEffect(bundleUid) {
        if (bundleUid == null) return@LaunchedEffect
        delay(PrereleaseNoticeDuration)
        onRetire(bundleUid)
    }
}

/**
 * Warning header for a source on the dev branch. It scrolls with the patches it belongs to
 * rather than holding a fixed strip, which also keeps the tabs from shifting as pages change.
 *
 * It animates its own placement like the rows below it, so the list closes the gap when the
 * warning retires instead of snapping shut.
 */
private fun LazyListScope.prereleaseNotice() = item(key = "prerelease-notice") {
    Notice(
        text = stringResource(R.string.expert_mode_prerelease_notice),
        modifier = Modifier.animatedListItem(this),
        icon = Icons.Outlined.WarningAmber,
        tone = SemanticTone.Warning,
        density = NoticeDensity.Compact
    )
}

/**
 * Per-bundle patch names matching [predicate]. Bundles that match nothing are left out, so an
 * absent uid and an empty set mean the same thing to the caller.
 */
private inline fun List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>.patchNamesWhere(
    predicate: (bundle: PatchBundleInfo.Scoped, patch: PatchInfo, isEnabled: Boolean) -> Boolean
): Map<Int, Set<String>> = mapNotNull { (bundle, patches) ->
    val names = patches.mapNotNullTo(mutableSetOf()) { (patch, isEnabled) ->
        patch.name.takeIf { predicate(bundle, patch, isEnabled) }
    }
    if (names.isEmpty()) null else bundle.uid to names
}.toMap()

/**
 * Bulk-action row for one bundle, wired to the patches currently in view: what "enable all" and
 * "disable all" reach is what the search and the filters left on screen.
 */
@Composable
private fun BundleControls(
    bundle: PatchBundleInfo.Scoped,
    patches: List<Pair<PatchInfo, Boolean>>,
    patchActions: ExpertPatchActions,
    savedPatches: PatchSelection,
    lockStateOf: (PatchInfo) -> PatchLockState,
    holdsUniversalPatches: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Boolean,
    onExpandUniversal: (bundleUid: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val holdsUniversal = holdsUniversalPatches(bundle.uid, patches)
    val copySelection: (() -> Unit)? = patchActions.onCopyFromBundle?.let { copy ->
        { copy(bundle.uid) }
    }

    BundlePatchControls(
        enabledCount = patches.count { (_, isEnabled) -> isEnabled },
        totalCount = patches.size,
        holdsUniversalPatches = holdsUniversal,
        // The second "Enable all" tap applies every universal patch at once; warn first
        warnOnUniversalAll = !holdsUniversal && patches.hasEnablableUniversal(lockStateOf),
        onSelectAll = {
            // This tap enables the universal patches, so it has to show what it turned on
            if (!holdsUniversal) onExpandUniversal(bundle.uid)
            patchActions.onSelectAll(bundle.uid, patches)
        },
        onDeselectAll = { patchActions.onDeselectAll(bundle.uid, patches) },
        onResetToDefault = { patchActions.onResetToDefault(bundle.uid) },
        onRestoreSaved = { patchActions.onRestoreSaved(bundle.uid) },
        onCopyFromBundle = copySelection,
        hasSavedSelection = savedPatches[bundle.uid]?.isNotEmpty() == true,
        modifier = modifier
    )
}

/**
 * One bundle's scrolling list: its pre-release warning, the patches written for this app, then
 * the universal ones behind a collapsible header. Every row animates its own placement, so
 * search results and the fold settle instead of jumping.
 *
 * The scrollbar stays with the caller, since the tabbed layout draws a single overlay for the
 * whole pager rather than one per page.
 */
@Composable
private fun BundlePatchList(
    bundle: PatchBundleInfo.Scoped,
    patches: List<Pair<PatchInfo, Boolean>>,
    listState: LazyListState,
    markers: PatchMarkers,
    isFiltering: Boolean,
    isUniversalExpanded: Boolean,
    onUniversalExpandedChange: (Boolean) -> Unit,
    lockStateOf: (PatchInfo) -> PatchLockState,
    patchActions: ExpertPatchActions,
    onConfigureOptions: (PatchInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val newPatchNames = markers.newPatches[bundle.uid].orEmpty()
    val missingRequiredOptions = markers.missingRequiredOptions[bundle.uid].orEmpty()
    val customOptions = markers.customOptions[bundle.uid].orEmpty()
    val sections = rememberPatchSections(patches = patches, newPatchNames = newPatchNames)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        if (bundle.uid in markers.prereleaseNotices) prereleaseNotice()

        // Availability is resolved per patch, so a universal patch the installer requires or
        // rules out carries the same lock as an app-specific one
        patchSectionRows(
            sectionKey = bundle.uid,
            specific = sections.specific,
            universal = sections.universal,
            key = { (patch, _): Pair<PatchInfo, Boolean> -> "${bundle.uid}:${patch.name}" },
            isFiltering = isFiltering,
            isUniversalExpanded = isUniversalExpanded,
            onUniversalExpandedChange = onUniversalExpandedChange,
            universalSelectedCount = sections.selectedUniversalCount
        ) { (patch, isEnabled) ->
            PatchCard(
                patch = patch,
                isEnabled = isEnabled,
                isNew = patch.name in newPatchNames,
                buildsClone = patch.renamesByDefault,
                hasRequiredOptionsMissing = patch.name in missingRequiredOptions,
                hasCustomOptions = patch.name in customOptions,
                lockState = lockStateOf(patch),
                onToggle = { patchActions.onPatchToggle(bundle.uid, patch.name) },
                onConfigureOptions = { onConfigureOptions(patch) },
                hasOptions = !patch.options.isNullOrEmpty(),
                modifier = Modifier.animatedListItem(this)
            )
        }
    }
}
