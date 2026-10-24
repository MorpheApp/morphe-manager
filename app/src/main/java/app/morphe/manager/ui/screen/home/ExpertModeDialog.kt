/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.patcher.patch.appColorFor
import app.morphe.manager.ui.model.renamesByDefault
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.PatchSelectionUtils.hasCustomizedOptions
import app.morphe.manager.util.PatchSelectionUtils.hasMissingRequiredOptions
import app.morphe.manager.util.PatchSelectionUtils.hasUniversalToEnable
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import org.koin.compose.koinInject

/**
 * The patches the expert-mode dialog chooses from: every source's patches with whether each is
 * selected, their option values, and what the selection started from.
 */
data class ExpertPatchSelection(
    val allPatchesInfo: List<Pair<PatchBundleInfo.Scoped, List<Pair<PatchInfo, Boolean>>>>,
    val options: Options,
    val totalSelectedCount: Int,
    val newPatches: Map<Int, Set<String>> = emptyMap(),
    val savedPatches: PatchSelection = emptyMap(),
    val lockStateOf: (PatchInfo) -> PatchLockState = { PatchLockState.NONE },
    /** True while "Enable all" still holds the universal patches of the given list back. */
    val holdsUniversalPatches: (bundleUid: Int, patches: List<Pair<PatchInfo, Boolean>>) -> Boolean = { _, _ -> false }
)

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
 *
 * @param packageName App being patched, which the header names and takes its color from.
 * @param appName Name to head the dialog with, as the rest of the run names the app.
 * @param appIcon Icon for the header where no source has one, as for a file picked from storage.
 */
@Composable
fun DialogHostScope.ExpertModeDialog(
    packageName: String,
    appName: String,
    appIcon: Drawable? = null,
    selection: ExpertPatchSelection,
    hasMultipleBundles: Boolean,
    patchActions: ExpertPatchActions,
    proceedText: String = stringResource(R.string.patch),
    /** Off where mixing sources is the norm rather than something the user just did. */
    warnOnMultipleBundles: Boolean = true,
    /** Whether a source receives pre-release patch versions, which its tab warns about. */
    usesPrerelease: (bundleUid: Int) -> Boolean = { false },
    /** Sources this app is being kept from, which the notice above the list offers back. */
    hiddenSourceCount: Int = 0,
    onShowHiddenSources: () -> Unit = {},
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    val (allPatchesInfo, options, totalSelectedCount, newPatches, savedPatches, lockStateOf, holdsUniversalPatches) =
        selection
    val totalPatchesCount = allPatchesInfo.sumOf { (_, patches) -> patches.size }
    val prereleaseBundleUids = allPatchesInfo.mapNotNullTo(mutableSetOf()) { (bundle, _) ->
        bundle.uid.takeIf(usesPrerelease)
    }
    // Sources marking the APK's version experimental. A run leaves out the ones whose experimental
    // versions are off, so a source added back by hand says what its patches are applied to
    val experimentalBundleUids = allPatchesInfo.mapNotNullTo(mutableSetOf()) { (bundle, _) ->
        bundle.uid.takeIf { bundle.isVersionExperimental }
    }
    val selectedPatchForOptions = remember { mutableStateOf<Pair<Int, PatchInfo>?>(null) }
    val search = rememberSearchFieldState()
    val showMultipleSourcesWarning = remember { mutableStateOf(false) }
    val messages = koinInject<UserMessages>()

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

    val patchSections = rememberPatchSectionState()

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

    val allPatches = remember(allPatchesInfo) { allPatchesInfo.flatMap { (_, patches) -> patches.map { it.first } } }
    val matchesPatch = rememberPatchMatcher(search.query, allPatches)
    val appColor = remember(allPatches, packageName) { allPatches.asSequence().appColorFor(packageName) }
    val sourcesByUid = rememberSourcesByUid()

    // The two filters stack: either can narrow what the other left. Keyed by bundle and in bundle
    // order, since every reader below already holds a bundle and asks only what it kept
    val filteredPatchesByUid: Map<Int, List<Pair<PatchInfo, Boolean>>> =
        remember(allPatchesInfo, search.query, matchesPatch, selectedOnly.value) {
            val query = search.query.takeIf { it.isNotBlank() }
            val onlySelected = selectedOnly.value
            if (query == null && onlySelected == null) {
                return@remember allPatchesInfo.associate { (bundle, patches) -> bundle.uid to patches }
            }

            allPatchesInfo.mapNotNull { (bundle, patches) ->
                val kept = onlySelected?.get(bundle.uid).orEmpty()
                val filtered = patches.filter { (patch, _) ->
                    val matchesQuery = query == null || matchesPatch(patch)
                    matchesQuery && (onlySelected == null || patch.name in kept)
                }
                if (filtered.isEmpty()) null else bundle.uid to filtered
            }.toMap()
        }

    // Both narrow the list far enough that a folded universal section would only hide results
    val isFiltering = search.isFiltering || isSelectedOnly

    // A source the filter left without rows is only an empty page to swipe past. Order is kept,
    // since a tab that moved mid-keystroke carries off the one thing the user navigates by
    val displayedBundles = remember(filteredPatchesByUid) {
        allPatchesInfo.filter { (bundle, _) -> bundle.uid in filteredPatchesByUid }
    }

    val markers = remember(
        newPatches,
        patchesWithMissingRequired,
        patchesWithCustomOptions,
        prereleaseBundleUids,
        experimentalBundleUids,
        retiredNotices.value
    ) {
        PatchMarkers(
            newPatches = newPatches,
            missingRequiredOptions = patchesWithMissingRequired,
            customOptions = patchesWithCustomOptions,
            prereleaseNotices = prereleaseBundleUids - retiredNotices.value,
            experimentalNotices = experimentalBundleUids
        )
    }

    // Layout mode is determined by total bundle count
    val hasMultipleBundleLayout = allPatchesInfo.size > 1
    val pagerState = rememberPagerState { displayedBundles.size }
    val coroutineScope = rememberCoroutineScope()

    // Held to a source rather than to a page index, which a dropped source reassigns.
    // Only the settled page counts, so pages a swipe passes over are not a choice
    val currentBundles = rememberUpdatedState(displayedBundles)
    val openBundleUid = remember { mutableStateOf<Int?>(null) }
    // One collector outlives every change: an effect keyed on the filter would be torn
    // down by the next keystroke, leaving the pager halfway between two bundles
    LaunchedEffect(pagerState) {
        launch {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                currentBundles.value.getOrNull(page)?.let { (bundle, _) ->
                    openBundleUid.value = bundle.uid
                }
            }
        }
        snapshotFlow { currentBundles.value }.collect { bundles ->
            val openUid = openBundleUid.value ?: return@collect
            val target = bundles.indexOfFirst { (bundle, _) -> bundle.uid == openUid }
            if (target == pagerState.currentPage) return@collect
            if (target >= 0) {
                // The same source at a new index, so it must not appear to move
                pagerState.scrollToPage(target)
            } else if (bundles.isNotEmpty()) {
                // The open source lost every row: follow to one that kept some
                pagerState.animateScrollToPage(0)
            }
        }
    }

    // Created up front, outside the pager, so the scrollbar overlay below can track
    // whichever page is current. HorizontalPager clips each page to its own bounds, so
    // a scrollbar drawn inside a page can never bleed out to the true dialog edge.
    // Keyed by source, not by the page index a filter reassigns, so no page inherits
    // the position of whichever sibling held that slot
    val bundleUids = remember(allPatchesInfo) { allPatchesInfo.map { (bundle, _) -> bundle.uid } }
    val pageListStates = rememberSaveable(
        bundleUids,
        saver = listSaver(
            save = { states ->
                states.entries.flatMap { (uid, state) ->
                    listOf(uid, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
                }
            },
            restore = { saved ->
                saved.chunked(3).associate { (uid, index, offset) ->
                    uid to LazyListState(index, offset)
                }
            }
        )
    ) {
        bundleUids.associateWith { LazyListState() }
    }

    // The pager settles its own page as the count changes, so a frame can arrive
    // holding an index past the list the filter just narrowed
    val currentIndex = pagerState.currentPage.coerceAtMost(displayedBundles.lastIndex)
    // The source whose controls head the list: the lone one, or the one whose tab is open
    val headBundle = if (hasMultipleBundleLayout) {
        displayedBundles.getOrNull(currentIndex)?.first
    } else {
        allPatchesInfo.firstOrNull()?.first
    }

    ListDialog(
        onDismissRequest = onDismiss,
        accentColor = appColor,
        header = {
            // Headed by the app, as its other dialogs are. A lone source takes the subtitle's
            // second line rather than a row of its own, while several get their tabs below
            ListDialogHeader(
                icon = { modifier ->
                    AppIcon(packageName = packageName, icon = appIcon, contentDescription = null, modifier = modifier)
                },
                title = appName,
                subtitle = listOfNotNull(
                    stringResource(R.string.settings_advanced_expert_mode),
                    allPatchesInfo.singleOrNull()?.first?.name
                ).joinToString("\n"),
                search = search,
                searchLabel = stringResource(R.string.expert_mode_search),
                // The tabs between the sources stay put, the controls of the open one fold away
                // while the patches scroll
                content = if (hasMultipleBundleLayout && displayedBundles.isNotEmpty()) {
                    {
                        // The open tab is marked by a fill of its own rather than a line under it, and
                        // the row fades out at an end it runs past, so there is plainly more to scroll to
                        val tabScroll = rememberScrollState()
                        SecondaryScrollableTabRow(
                            selectedTabIndex = currentIndex,
                            modifier = Modifier
                                .padding(top = Defaults.SpaceSmall)
                                .horizontalScrollFade(tabScroll),
                            scrollState = tabScroll,
                            edgePadding = 0.dp,
                            indicator = {},
                            divider = {},
                            containerColor = Color.Transparent
                        ) {
                            displayedBundles.forEachIndexed { index, (bundle, patches) ->
                                SourceTab(
                                    source = sourcesByUid[bundle.uid],
                                    name = bundle.name,
                                    count = "${patches.count { it.second }}/${patches.size}",
                                    selected = currentIndex == index,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                                )
                            }
                        }
                    }
                } else null,
                // Kept in place on a tab the filters emptied, so the head keeps its height and the
                // bulk actions gray out instead of the whole row snapping away
                collapsible = headBundle?.let { bundle ->
                    {
                        BundleControls(
                            bundle = bundle,
                            patches = filteredPatchesByUid[bundle.uid].orEmpty(),
                            patchActions = patchActions,
                            savedPatches = savedPatches,
                            lockStateOf = lockStateOf,
                            holdsUniversalPatches = holdsUniversalPatches,
                            onExpandUniversal = { patchSections.setExpanded(it, UNIVERSAL_GROUP_KEY, true) }
                        )
                    }
                }
            ) {
                // The counter already stands for the selection, so it doubles as the way to filter
                // the list down to it
                val badgeTone = if (totalSelectedCount > 0) SemanticTone.Primary else SemanticTone.Neutral
                // In the header's color like the search beside it: tinted while it counts,
                // filled while it filters
                val accent = LocalAccent.current
                val badgeContainer = when {
                    accent == null -> if (isSelectedOnly) MaterialTheme.colorScheme.primary else badgeTone.container
                    isSelectedOnly -> accent
                    totalSelectedCount > 0 -> accent.copy(alpha = AccentAlpha.LEAD)
                    else -> accent.copy(alpha = AccentAlpha.STEP)
                }
                val badgeContent = when {
                    accent != null -> appAccentContent(badgeContainer)
                    isSelectedOnly -> MaterialTheme.colorScheme.onPrimary
                    else -> badgeTone.content
                }
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
                    icon = MaterialSymbols.Rounded.Filter_alt.takeIf { canFilter },
                    tone = badgeTone,
                    // Filled rather than tonal while filtering, so the narrowed list has a visible cause
                    containerColor = badgeContainer,
                    contentColor = badgeContent,
                    onClick = if (canFilter) {
                        { toggleSelectedOnly() }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { stateDescription = filterState }
                )
            }
        },
        footer = {
            AppDialogActions(
                // A row lays its actions out from the right, so patching ends up there
                actions = listOfNotNull(
                    DialogAction(
                        text = proceedText,
                        onClick = {
                            // Check if multiple bundles are selected
                            if (hasMultipleBundles && warnOnMultipleBundles) {
                                showMultipleSourcesWarning.value = true
                            } else {
                                onProceed()
                            }
                        },
                        icon = MaterialSymbols.Rounded.Auto_fix_high,
                        enabled = totalSelectedCount > 0
                    ),
                    translateAction()
                )
            )
        },
        hideFooterWhileTyping = true
    ) {
        SearchFieldBackHandler(search)
        // Back unwinds the filter before the dialog itself, the way the search field does
        BackHandler(enabled = isSelectedOnly) { selectedOnly.value = null }

        RetirePrereleaseNotice(
            bundleUid = headBundle?.uid?.takeIf { it in prereleaseBundleUids },
            onRetire = { retireNotice(it) }
        )

        // Each source's page keeps its own scroll, so the controls of the one turned to come back
        // into view rather than staying folded from the page left
        val headerCollapse = LocalDialogHeaderCollapse.current
        LaunchedEffect(pagerState.settledPage) { headerCollapse?.unfold() }

        // Above every source's patches rather than inside one: what it counts is the sources that
        // have no page here at all
        val leadingRows: LazyListScope.() -> Unit = {
            if (hiddenSourceCount > 0) {
                item(key = "hidden-sources-notice") {
                    Notice(
                        text = pluralStringResource(
                            R.plurals.expert_mode_hidden_sources_notice,
                            hiddenSourceCount,
                            hiddenSourceCount.toString()
                        ),
                        icon = MaterialSymbols.Rounded.Visibility_off,
                        tone = SemanticTone.Neutral,
                        density = NoticeDensity.Compact,
                        onClick = onShowHiddenSources,
                        modifier = Modifier
                            .animatedListItem(this)
                            .endsBlock()
                    )
                }
            }
        }

        val barInsets = LocalDialogBarInsets.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!hasMultipleBundleLayout) {
                val bundle = headBundle ?: return@Box
                val filteredPatches = filteredPatchesByUid[bundle.uid]
                val singleBundleList = rememberLazyListState()

                // The list keeps its place while empty, so its rows fade out under the
                // empty state rather than the two swapping in a single frame
                BundlePatchList(
                    bundle = bundle,
                    patches = filteredPatches.orEmpty(),
                    listState = singleBundleList,
                    markers = markers,
                    isFiltering = isFiltering,
                    sectionState = patchSections,
                    lockStateOf = lockStateOf,
                    patchActions = patchActions,
                    accentColor = appColor,
                    leadingRows = leadingRows,
                    onConfigureOptions = { selectedPatchForOptions.value = bundle.uid to it }
                )

                if (filteredPatches != null) DialogScrollIndicators(listState = singleBundleList)
            } else if (displayedBundles.isEmpty()) {
                // Nothing matched anywhere, so there is no tab to draw. The pager state
                // outlives this, so clearing the filter hands the open source back
                PatchesListEmptyState(modifier = Modifier.padding(barInsets))
            } else {
                // A remote stays on its source's page, the tabs being the way to the others
                val pageFocus = rememberPagerPageFocus()
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val (bundle, _) = displayedBundles.getOrNull(pageIndex) ?: return@HorizontalPager
                    val patches = filteredPatchesByUid[bundle.uid]
                    val listState = pageListStates[bundle.uid] ?: return@HorizontalPager

                    BundlePatchList(
                        bundle = bundle,
                        patches = patches.orEmpty(),
                        listState = listState,
                        markers = markers,
                        isFiltering = isFiltering,
                        sectionState = patchSections,
                        lockStateOf = lockStateOf,
                        patchActions = patchActions,
                        accentColor = appColor,
                        leadingRows = leadingRows,
                        modifier = pageFocus,
                        onConfigureOptions = { selectedPatchForOptions.value = bundle.uid to it }
                    )
                }

                // Single overlay for the whole pager, tracking whichever page is current,
                // instead of one per page - a page-local scrollbar would be clipped by the
                // pager before it could reach the true dialog edge
                headBundle?.let { pageListStates[it.uid] }?.let { DialogScrollIndicators(listState = it) }
            }
        }
    }

    // Multiple bundles warning dialog
    DialogHost(visible = showMultipleSourcesWarning.value) {
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
    DialogHost(target = selectedPatchForOptions.value) { (bundleUid, patch) ->
        val missingOptionsMessage = stringResource(R.string.patch_option_required_missing, patch.displayName)
        PatchOptionsDialog(
            patch = patch,
            packageName = packageName,
            appName = appName,
            appIcon = appIcon,
            accentColor = appColor,
            isDefaultBundle = bundleUid == 0,
            values = options[bundleUid]?.get(patch.name),
            onValueChange = { key, value ->
                patchActions.onOptionChange(bundleUid, patch.name, key, value)
            },
            onReset = {
                patchActions.onResetOptions(bundleUid, patch.name)
            },
            onDismiss = {
                // Tell the user if the patch still has unfilled required options
                if (patch.name in patchesWithMissingRequired[bundleUid].orEmpty()) {
                    messages.show(missingOptionsMessage)
                }
                selectedPatchForOptions.value = null
            }
        )
    }
}

/**
 * One source's tab: the icon the source wears everywhere else, beside its name over how much of it
 * is picked. Stacked rather than in a line, so the row fits more sources before it scrolls. Each
 * sits on a pill, the open one filled with the accent.
 *
 * @param accentColor Color of the app being patched, which the open tab fills with as the header's
 *   actions do. The dialog's own by default, or null for the theme's palette.
 */
@Composable
private fun SourceTab(
    source: PatchBundleSource?,
    name: String,
    count: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color? = LocalAccent.current
) {
    val accent = usableAppAccent(accentColor)
    val selectedFill = accent?.copy(alpha = AccentAlpha.LEAD) ?: MaterialTheme.colorScheme.primaryContainer
    val fill by animateColorAsState(
        // A faint pill of its own while closed, so every tab reads as the button it is
        targetValue = if (selected) {
            selectedFill
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "source_tab_fill"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            !selected -> MaterialTheme.colorScheme.onSurfaceVariant
            accent != null -> appAccentContent(selectedFill)
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "source_tab_content"
    )

    // The row lays tabs edge to edge, so the gap between the pills comes from each one's end,
    // which leaves the first flush with the list below. Nothing above or below, so the row
    // keeps the same spacing to the header and the controls as a lone source's layout does
    Row(
        modifier = Modifier
            .padding(end = Defaults.SpaceSmall)
            .background(fill, Defaults.PillShape)
            .shapedSelectable(Defaults.PillShape, selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (source != null) {
                BundleIcon(bundle = source, modifier = Modifier.size(24.dp), enabled = source.enabled)
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** Everything the rows of a bundle badge or warn about, all keyed by bundle uid. */
@Immutable
private data class PatchMarkers(
    val newPatches: Map<Int, Set<String>>,
    val missingRequiredOptions: Map<Int, Set<String>>,
    val customOptions: Map<Int, Set<String>>,
    val prereleaseNotices: Set<Int>,
    val experimentalNotices: Set<Int>
)

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
        modifier = Modifier
            .animatedListItem(this)
            .endsBlock(),
        icon = MaterialSymbols.Rounded.Warning,
        tone = SemanticTone.Warning,
        density = NoticeDensity.Compact
    )
}

/**
 * Header for a source marking the APK's version experimental. Unlike the pre-release warning it
 * stays, since it matters for as long as the source's patches can be switched on.
 */
private fun LazyListScope.experimentalVersionNotice() = item(key = "experimental-version-notice") {
    Notice(
        text = stringResource(R.string.expert_mode_experimental_version_notice),
        modifier = Modifier
            .animatedListItem(this)
            .endsBlock(),
        icon = VersionTag.Experimental.icon,
        tone = VersionTag.Experimental.tone,
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
    onExpandUniversal: (bundleUid: Int) -> Unit
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
        warnOnUniversalAll = !holdsUniversal && patches.hasUniversalToEnable(lockStateOf),
        onSelectAll = {
            // This tap enables the universal patches, so it has to show what it turned on
            if (!holdsUniversal) onExpandUniversal(bundle.uid)
            patchActions.onSelectAll(bundle.uid, patches)
        },
        onDeselectAll = { patchActions.onDeselectAll(bundle.uid, patches) },
        onResetToDefault = { patchActions.onResetToDefault(bundle.uid) },
        onRestoreSaved = { patchActions.onRestoreSaved(bundle.uid) },
        onCopyFromBundle = copySelection,
        hasSavedSelection = savedPatches[bundle.uid]?.isNotEmpty() == true
    )
}

/**
 * One bundle's scrolling list: its pre-release warning, then the patches block by block, each
 * behind a collapsible header of its own. Every row animates its own placement, so search
 * results and the fold settle instead of jumping.
 *
 * The scrollbar stays with the caller, since the tabbed layout draws a single overlay for the
 * whole pager rather than one per page.
 *
 * @param accentColor Color of the app being patched, which the block headers take.
 */
@Composable
private fun BundlePatchList(
    bundle: PatchBundleInfo.Scoped,
    patches: List<Pair<PatchInfo, Boolean>>,
    listState: LazyListState,
    markers: PatchMarkers,
    isFiltering: Boolean,
    sectionState: PatchSectionState,
    lockStateOf: (PatchInfo) -> PatchLockState,
    patchActions: ExpertPatchActions,
    accentColor: Color?,
    leadingRows: LazyListScope.() -> Unit,
    onConfigureOptions: (PatchInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val newPatchNames = markers.newPatches[bundle.uid].orEmpty()
    val missingRequiredOptions = markers.missingRequiredOptions[bundle.uid].orEmpty()
    val customOptions = markers.customOptions[bundle.uid].orEmpty()
    // New patches float to the top of each block; within a block the order is alphabetical
    val ordered = remember(patches, newPatchNames) {
        val displayOrder = compareByDescending<Pair<PatchInfo, Boolean>> { (patch, _) ->
            patch.name in newPatchNames
        }.thenBy { (patch, _) -> patch.name }
        patches.sortedWith(displayOrder)
    }
    val groups = rememberPatchGroups(
        patches = ordered,
        infoOf = { (patch, _) -> patch },
        isEnabled = { (_, isEnabled) -> isEnabled }
    )
    val folds = sectionState.folds

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .verticalScrollFade(listState),
        // Runs on under the head and footer floating over the list, and comes out from under them
        contentPadding = LocalDialogBarInsets.current,
        verticalArrangement = Arrangement.spacedBy(SegmentGap)
    ) {
        leadingRows()
        if (bundle.uid in markers.prereleaseNotices) prereleaseNotice()
        if (bundle.uid in markers.experimentalNotices) experimentalVersionNotice()

        // Kept in the list rather than laid over it, so the message holds its place under a notice
        // and an opening keyboard shortens the surrounding page instead of moving it
        if (ordered.isEmpty()) {
            item(key = "empty_state") {
                PatchesListEmptyState(modifier = Modifier.animateItem())
            }
        }

        // Availability is resolved per patch, so a universal patch the installer requires or
        // rules out carries the same lock as an app-specific one
        patchGroupRows(
            sectionKey = bundle.uid,
            groups = groups,
            key = { (patch, _): Pair<PatchInfo, Boolean> -> "${bundle.uid}:${patch.name}" },
            isFiltering = isFiltering,
            folds = folds,
            onToggle = { group -> sectionState.toggle(bundle.uid, group) },
            accentColor = accentColor
        ) { (patch, isEnabled), segment ->
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
                shape = segment.shape,
                modifier = Modifier
                    .animatedListItem(this)
                    .endsBlock(segment.endsBlock)
            )
        }
    }
}
