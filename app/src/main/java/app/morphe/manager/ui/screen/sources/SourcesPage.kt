/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.sources

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.isHeldBack
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.sourceType
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.SourceBundleSortMode
import app.morphe.manager.domain.repository.BlocklistRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.domain.repository.appsBrought
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.patcher.IncompatiblePatcherVersionDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.util.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

/** Enough placeholder rows to fill the page on open without implying a count. */
private val SourceShimmerRows = (0 until 4).toList()

/**
 * The sources page of the main screen: every patch source as a card folding out its details and
 * actions, searched from the page's header, sorted and filtered from a panel docked under the list,
 * and long-pressed into a selection acted on at once or dragged into order.
 *
 * @param bottomBarHeight Height of the navigation floating over the page, which the list ends clear of.
 * @param state The panels docked under the list, held by the screen, whose navigation gives way to them.
 * @param onboardingState Set while the tour runs, which points into the first source.
 * @param alertsHeight How far down the alerts reach under the header, which the list starts below.
 * @param onHeaderHeightChange Told the header's height, for the alerts floating over the page below it.
 */
@Composable
fun SourcesPage(
    homeViewModel: HomeViewModel,
    bottomBarHeight: Dp,
    state: SourcesPageState,
    onboardingState: OnboardingState? = null,
    alertsHeight: () -> Dp = { 0.dp },
    onHeaderHeightChange: (Dp) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val repository = homeViewModel.patchBundleRepository

    // Replacing the file of a local source keeps its uid, so the patch selection and options
    // stay attached instead of being stranded on a freshly added second source
    val openLocalBundleUpdatePicker = rememberAdaptiveFilePicker(
        mimeTypes = MPP_FILE_MIME_TYPES,
        onResult = { uri ->
            val uid = homeViewModel.localBundleUpdateUid
            homeViewModel.localBundleUpdateUid = null
            if (uri != null && uid != null) homeViewModel.updateLocalSource(uid, uri)
        }
    )

    SourcesPageContent(
        bottomBarHeight = bottomBarHeight,
        state = state,
        onboardingState = onboardingState,
        alertsHeight = alertsHeight,
        onHeaderHeightChange = onHeaderHeightChange,
        onAddSource = { homeViewModel.showAddSourceDialog = true },
        onDelete = { bundles -> scope.launch { repository.remove(*bundles.toTypedArray()) } },
        onToggleEnabled = { bundles -> scope.launch { repository.disable(*bundles.toTypedArray()) } },
        onUpdate = { bundles ->
            // A local source has nothing to fetch from, so it asks for a replacement file instead,
            // which can only be picked for one at a time
            val local = bundles.singleOrNull()?.takeUnless { it is RemotePatchBundle }
            if (local != null) {
                homeViewModel.localBundleUpdateUid = local.uid
                openLocalBundleUpdatePicker()
            } else {
                // A source that failed to load may hold a broken jar of the current version,
                // which a plain version check would keep
                val (failed, loaded) = bundles.filterIsInstance<RemotePatchBundle>()
                    .partition { it.state is PatchBundleSource.State.Failed }
                scope.launch {
                    if (failed.isNotEmpty()) repository.update(*failed.toTypedArray(), force = true, showMessage = true)
                    if (loaded.isNotEmpty()) repository.update(*loaded.toTypedArray(), showMessage = true)
                }
            }
        },
        onRename = { bundle ->
            homeViewModel.bundleToRename = bundle
            homeViewModel.showRenameBundleDialog = true
        },
        onReorder = { orderedUids -> scope.launch { repository.reorderBundles(orderedUids) } }
    )
}

@Composable
private fun SourcesPageContent(
    bottomBarHeight: Dp,
    state: SourcesPageState,
    onboardingState: OnboardingState?,
    alertsHeight: () -> Dp,
    onHeaderHeightChange: (Dp) -> Unit,
    onAddSource: () -> Unit,
    onDelete: (List<PatchBundleSource>) -> Unit,
    // Flips each source, on or off
    onToggleEnabled: (List<PatchBundleSource>) -> Unit,
    onUpdate: (List<PatchBundleSource>) -> Unit,
    onRename: (PatchBundleSource) -> Unit,
    onReorder: (List<Int>) -> Unit
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val prefs: PreferencesManager = koinInject()
    val sourceMuteRepository: SourceMuteRepository = koinInject()
    val scope = rememberCoroutineScope()

    val sources by patchBundleRepository.sources.collectAsStateWithLifecycle()
    val bundleState by patchBundleRepository.bundleState.collectAsStateWithLifecycle()
    val isLoadingSources = bundleState is PatchBundleRepository.BundleState.Loading
    val patchCounts by patchBundleRepository.patchCountsFlow.collectAsStateWithLifecycle(emptyMap())
    val activeUpdateUids by patchBundleRepository.activeUpdateUidsFlow.collectAsStateWithLifecycle(emptySet())
    val metadataFetchErrors by patchBundleRepository.metadataFetchErrors.collectAsStateWithLifecycle(emptyMap())
    val experimentalVersionsEnabled by prefs.bundleExperimentalVersionsEnabled.getAsState()
    // Every source, not only the enabled ones: a disabled source still answers a patch search
    // and still declares whether it carries experimental targets
    val bundleInfo by patchBundleRepository.allBundlesInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val blockedSources by patchBundleRepository.blockedSources.collectAsStateWithLifecycle(emptyMap())
    val keptFrom by sourceMuteRepository.mutedSources.collectAsStateWithLifecycle(emptyMap())
    val unseenNewApps by sourceMuteRepository.unseenNewApps.collectAsStateWithLifecycle(emptyMap())
    val appCounts = remember(bundleInfo, keptFrom, unseenNewApps) {
        bundleInfo.mapValues { (uid, info) ->
            val listed = info.listedApps()
            SourceAppCount(
                brought = info.appsBrought(keptFrom).size,
                listed = listed.size,
                // An app the source has since dropped is nothing left to look at
                unseen = unseenNewApps[uid].orEmpty().count { it in listed }
            )
        }
    }

    // The tour points into the first source, so it stays open while the tour runs
    val isTourRunning = onboardingState != null

    // The sources a delete waits on confirmation for, one from its card or several from a selection
    val bundlesToDelete = remember { mutableStateOf<List<PatchBundleSource>>(emptyList()) }
    // Set when the user flips pre-releases on: the toggle waits for confirmation first,
    // so the meaning of unstable testing builds is explained before anything changes
    val bundleToConfirmPrerelease = remember { mutableStateOf<PatchBundleSource?>(null) }
    // Search, sorting and filtering are offered from two sources up
    val isSearchable = sources.size >= 2
    val search = rememberSearchFieldState(searchable = isSearchable)
    var sourceFilter by rememberSaveable { mutableStateOf(SourceFilterMode.ALL) }
    LaunchedEffect(isSearchable) {
        if (!isSearchable) {
            state.isListOptionsOpen = false
            sourceFilter = SourceFilterMode.ALL
        }
    }
    // Expanded state lifted out of LazyColumn so it survives scroll-off-screen recomposition
    var expandedBundleUids by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Drag-and-drop state
    val listState = rememberLazyListState()
    // The rows the tour points at are on the first card, which has to be in the list for them to
    // bring themselves into view
    val tourTarget = onboardingState?.target
    LaunchedEffect(tourTarget) {
        if (tourTarget in SourceTourTargets && listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
    }
    var localOrder by remember { mutableStateOf(sources.map { it.uid }) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(sources) {
        val sourceUids = sources.map { it.uid }
        // A source deleted or gone from under the selection is nothing left to act on
        state.selection.retain { it in sourceUids }
        if (isDragging) return@LaunchedEffect
        val existing = localOrder.filter { uid -> uid in sourceUids }
        val added = sourceUids.filter { it !in existing }
        val merged = existing + added
        if (merged != localOrder) localOrder = merged
    }
    val sortModePreference by prefs.sourceBundleSortMode.getAsState()
    val sourceSortMode = SourceBundleSortMode.fromPreference(sortModePreference)
    val isManualSort = sourceSortMode == SourceBundleSortMode.MANUAL
    // The list otherwise holds on to whichever source led it, which a new order leaves somewhere
    // in the middle, so a new order starts the list over from its top. Requested in the pass that
    // brings the order, so the list is laid out from the top at once rather than a frame later
    val listedSortMode = remember { mutableListOf(sourceSortMode) }
    if (listedSortMode[0] != sourceSortMode) {
        listedSortMode[0] = sourceSortMode
        listState.requestScrollToItem(0)
    }
    val orderedSources = remember(localOrder, sources, sourceSortMode) {
        sources.sortedForSourceSort(sourceSortMode, localOrder)
    }
    // Patches are searched alongside source names, so one query answers which source carries a
    // patch instead of the user opening every source to find out. Blocked sources stay out of it,
    // since nothing they hold is reachable anyway
    val patchMatchCounts: Map<Int, Int> = remember(bundleInfo, blockedSources, search.query) {
        val query = search.query.takeIf { it.isNotBlank() } ?: return@remember emptyMap()
        buildMap {
            bundleInfo.forEach { (uid, info) ->
                if (uid in blockedSources) return@forEach
                val matches = info.patches.count { it.matchesQuery(query) }
                if (matches > 0) put(uid, matches)
            }
        }
    }
    val filteredSources = remember(orderedSources, sourceFilter, blockedSources, metadataFetchErrors) {
        orderedSources.filter { source ->
            sourceFilter.matches(
                source = source,
                blocked = source.uid in blockedSources,
                unreachable = source.uid in metadataFetchErrors
            )
        }
    }
    val visibleSources = remember(filteredSources, patchMatchCounts, search.query) {
        val query = search.query
        if (query.isBlank()) return@remember filteredSources
        filteredSources
            .filter { source -> source.matchesQuery(query) || source.uid in patchMatchCounts }
            // A source the query names is the one that was asked for; the rest rank by how much
            // of the query they carry. The sort is stable, so ties keep the order the sort mode
            // gave them, and clearing the query drops back to that order untouched
            .sortedWith(
                compareByDescending<PatchBundleSource> { it.matchesQuery(query) }
                    .thenByDescending { patchMatchCounts[it.uid] ?: 0 }
            )
    }
    // The alphabet rail reads positions off a list that is in name order, which a search ranking
    // results by relevance no longer is
    val alphabetScrollMode = !search.isFiltering &&
            (sourceSortMode == SourceBundleSortMode.NAME_ASC ||
                    sourceSortMode == SourceBundleSortMode.NAME_DESC)
    val sourceScrollTargets = remember(alphabetScrollMode, visibleSources) {
        if (!alphabetScrollMode) {
            emptyList()
        } else {
            buildIndexedScrollTargets(visibleSources) { source -> source.displayTitle }
        }
    }
    val haptic = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val newOrder = localOrder.toMutableList()
        val moved = newOrder.removeAt(from.index)
        newOrder.add(to.index, moved)
        localOrder = newOrder
    }

    // A step of the reorder without the drag, which a screen reader moves a source by instead
    fun moveSource(uid: Int, by: Int) {
        val from = localOrder.indexOf(uid)
        val to = from + by
        if (from < 0 || to !in localOrder.indices) return
        localOrder = localOrder.toMutableList().apply { add(to, removeAt(from)) }
    }

    val bundleToShowPatches = remember { mutableStateOf<PatchBundleSource?>(null) }
    val bundleToShowApps = remember { mutableStateOf<PatchBundleSource?>(null) }
    var bundleRequiringManagerUpdate by remember { mutableStateOf<PatchBundleSource?>(null) }
    var bundleToTrustKey by remember { mutableStateOf<RemotePatchBundle?>(null) }
    var bundleToShowChangelogUid by remember { mutableStateOf<Int?>(null) }

    // Switching branches invalidates whatever the changelog dialog is holding, so the cache goes
    // and an open dialog closes rather than keeping entries from the branch that was just left
    fun applyPrerelease(bundle: PatchBundleSource, usePrerelease: Boolean) {
        if (bundle.uid == bundleToShowChangelogUid) {
            bundleToShowChangelogUid = null
        }
        (bundle as? RemotePatchBundle)?.clearChangelogCache()
        scope.launch {
            patchBundleRepository.setUsePrerelease(bundle.uid, usePrerelease)
        }
    }

    // Check if only default bundle exists
    val isSingleDefaultBundle = sources.size == 1

    // Auto-enable the default bundle if it's the only one and disabled. Keyed by that condition
    // rather than the list: the call toggles, so a second emission before it lands would undo it
    val lonelyDisabledDefault = sources.singleOrNull()?.takeIf { it.isDefault && !it.enabled }
    LaunchedEffect(lonelyDisabledDefault != null) {
        lonelyDisabledDefault?.let { onToggleEnabled(listOf(it)) }
    }

    val context = LocalContext.current
    val openUrl = rememberOpenUrl()

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    // Registered inside the page so it outranks the main screen's own back handling
    SearchFieldBackHandler(search)
    val searchLabel = stringResource(R.string.sources_search)

    // What the options panel offers, and what marks the sort button once any is off its default
    val listOptions = listOf(
        listOptionSection(
            R.string.sort, MaterialSymbols.Rounded.Sort.autoMirrored, sourceSortMode, SourceBundleSortMode.MANUAL
        ) { mode -> scope.launch { prefs.sourceBundleSortMode.update(mode.name) } },
        listOptionSection(
            R.string.filter, MaterialSymbols.Rounded.Filter_list, sourceFilter, SourceFilterMode.ALL
        ) { sourceFilter = it }
    )

    PinnedHeaderPage(
        header = {
            PageHeader(
                title = stringResource(R.string.sources),
                subtitle = pluralStringResource(
                    R.plurals.sources_management_subtitle,
                    sources.size,
                    sources.size.toString()
                ),
                search = search,
                searchLabel = searchLabel,
                // A query would narrow the list the order is being dragged on
                searchEnabled = isSearchable && !state.isReordering
            ) {
                AnimatedVisibility(visible = isSearchable) {
                    val optionsState = listOptionsStateDescription(
                        sourceSortMode.labelRes,
                        sourceFilter.labelRes.takeIf { sourceFilter.isActive }
                    )
                    // Engaged while the options are open, and marked while any is off its default
                    TitleAction(
                        icon = MaterialSymbols.Rounded.Sort.autoMirrored,
                        contentDescription = stringResource(R.string.sort),
                        onClick = state::toggleListOptions,
                        modifier = Modifier.semantics {
                            role = Role.Button
                            stateDescription = optionsState
                        },
                        style = TitleActionStyle.Toggle,
                        active = state.isListOptionsOpen,
                        marked = listOptions.any { it.isChanged },
                        // The order being dragged is the one the list is sorted by
                        enabled = !state.isReordering
                    )
                }
                TitleAction(
                    icon = MaterialSymbols.RoundedFilled.Add,
                    contentDescription = stringResource(R.string.add),
                    onClick = onAddSource,
                    style = TitleActionStyle.Accent,
                    modifier = Modifier.tourTarget(TourTarget.ADD_SOURCE, onboardingState)
                )
            }
        },
        scrollState = listState,
        bottomBar = {
            ListOptionsPanel(
                visible = state.isListOptionsOpen,
                sections = listOptions,
                onClose = { state.isListOptionsOpen = false },
                modifier = Modifier.fillMaxWidth()
            )
            SourcesSelectionBar(
                state = state,
                sources = sources,
                listed = visibleSources,
                blocked = blockedSources.keys,
                onUpdate = onUpdate,
                onToggleEnabled = onToggleEnabled,
                onDelete = { bundlesToDelete.value = it },
                // Reorder maps list positions onto the full order, so a filtered list would move
                // the wrong sources. A search is only put away, being typed again in a moment
                onStartReorder = if (isManualSort && !sourceFilter.isActive) {
                    {
                        search.collapse()
                        state.startReorder()
                    }
                } else null,
                onSaveOrder = { onReorder(localOrder) },
                onCancelReorder = { localOrder = sources.map { it.uid } },
                modifier = Modifier.fillMaxWidth()
            )
        },
        onHeaderHeightChange = onHeaderHeightChange
    ) { insets ->
        // Clear of whichever floats over the list's lower edge, the navigation or the options
        val listBottom = maxOf(bottomBarHeight, insets.bottom)
        // Bundle cards, running on under the header and the navigation floating over the page
        val listRowResizing = remember { ListRowResizing() }
        CompositionLocalProvider(LocalListRowResizing provides listRowResizing) {
            // A row the tour turns to comes out from under the header, the alerts and the navigation
            ProvideBringIntoViewInsets(top = insets.top, bottom = listBottom, extraTop = alertsHeight) {
                AppPullToRefreshBox(
                    // The cards show which sources are being updated, and the update says how it went
                    isRefreshing = false,
                    // Every source an update reaches, the same as selecting them all and updating
                    onRefresh = {
                        onUpdate(sources.filter { it is RemotePatchBundle && it.uid !in blockedSources })
                    },
                    // Selecting, reordering and the tour each hold the list still
                    enabled = !isLoadingSources && !state.isSelecting && !state.isReordering && !isTourRunning,
                    modifier = Modifier.fillMaxSize(),
                    // Drawn out from under the header and the alerts rather than the status bar, as the list is
                    indicatorTop = { insets.top + alertsHeight() }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScrollFade(listState),
                        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
                        contentPadding = remember(insets, listBottom, alertsHeight) {
                            ListContentPadding(
                                horizontal = insets.horizontal,
                                top = insets.top + Defaults.SpaceMedium,
                                bottom = listBottom + Defaults.SpaceLarge,
                                extraTop = alertsHeight
                            )
                        }
                    ) {
                        if (isLoadingSources) {
                            items(SourceShimmerRows, key = { index -> "shimmer_$index" }) {
                                ShimmerBundleRow()
                            }
                        }

                        if ((search.isFiltering || sourceFilter.isActive) && visibleSources.isEmpty()) {
                            item(key = "search_empty") {
                                EmptyState(
                                    message = stringResource(R.string.search_no_results),
                                    icon = MaterialSymbols.Rounded.Search_off
                                )
                            }
                        }

                        items(visibleSources, key = { bundle -> bundle.uid }) { bundle ->
                            val hasExperimentalVersions = remember(bundle.uid, bundleInfo) {
                                bundleInfo[bundle.uid]?.patches?.any { patch ->
                                    patch.compatiblePackages?.any { pkg ->
                                        pkg.experimentalVersions?.isNotEmpty() == true
                                    } == true
                                } == true
                            }
                            val useExperimentalVersions = bundle.uid.toString() in experimentalVersionsEnabled

                            val isFirstCard = bundle.uid == visibleSources.firstOrNull()?.uid
                            val toggleSelected = { state.selection.toggle(bundle.uid) }
                            val toggleExpanded = {
                                expandedBundleUids = if (bundle.uid in expandedBundleUids) {
                                    expandedBundleUids - bundle.uid
                                } else {
                                    expandedBundleUids + bundle.uid
                                }
                            }
                            val selectOnLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (state.isSelecting) toggleSelected() else state.startSelection(bundle.uid)
                            }
                            ReorderableItem(
                                reorderableState,
                                key = bundle.uid,
                                animateItemModifier = Modifier.animatedListItem(this)
                            ) { itemIsDragging ->
                                BundleManagementCard(
                                    bundle = bundle,
                                    info = SourceCardInfo(
                                        patchCount = patchCounts[bundle.uid] ?: 0,
                                        patchMatchCount = patchMatchCounts[bundle.uid],
                                        appCount = appCounts[bundle.uid] ?: SourceAppCount.NONE,
                                        isUpdating = bundle.uid in activeUpdateUids,
                                        metadataFetchError = metadataFetchErrors[bundle.uid],
                                        blockedInfo = blockedSources[bundle.uid],
                                        useExperimentalVersions = useExperimentalVersions
                                    ),
                                    actions = SourceCardActions(
                                        onPatchesClick = { bundleToShowPatches.value = bundle },
                                        onAppsClick = { bundleToShowApps.value = bundle },
                                        onVersionClick = {
                                            if (bundle is RemotePatchBundle) {
                                                bundleToShowChangelogUid = bundle.uid
                                            }
                                        },
                                        onOutdatedManagerClick = { bundleRequiringManagerUpdate = bundle },
                                        onReviewSigningKey = { (bundle as? RemotePatchBundle)?.let { bundleToTrustKey = it } },
                                        onOpenInBrowser = {
                                            openUrl((bundle as? RemotePatchBundle)?.browsePageUrl ?: SOURCE_REPO_URL)
                                        },
                                        onReportIssue = {
                                            openUrl((bundle as? RemotePatchBundle)?.issuesPageUrl ?: SOURCE_REPO_URL)
                                        },
                                        onShare = (bundle as? RemotePatchBundle)?.addSourceLink?.let { link ->
                                            { shareText(link) }
                                        },
                                        onPrereleasesToggle = when {
                                            bundle is JsonPatchBundle && bundle.supportsPrerelease ||
                                                    bundle is APIPatchBundle -> { usePrerelease ->
                                                if (usePrerelease) {
                                                    // Explain what pre-release means before flipping it on
                                                    bundleToConfirmPrerelease.value = bundle
                                                } else {
                                                    applyPrerelease(bundle, false)
                                                }
                                            }

                                            else -> null
                                        },
                                        onExperimentalVersionsToggle = if (hasExperimentalVersions) {
                                            { useExperimental ->
                                                scope.launch {
                                                    patchBundleRepository.setUseExperimentalVersions(
                                                        bundle.uid,
                                                        useExperimental
                                                    )
                                                }
                                            }
                                        } else null,
                                        onDisable = { onToggleEnabled(listOf(bundle)) },
                                        onUpdate = { onUpdate(listOf(bundle)) },
                                        onRename = { onRename(bundle) },
                                        onDelete = { bundlesToDelete.value = listOf(bundle) }
                                    ),
                                    // Folded while reordering, so a card is dragged by its header alone
                                    expanded = !state.isReordering && (isSingleDefaultBundle ||
                                        bundle.uid in expandedBundleUids || (isTourRunning && isFirstCard)),
                                    onHeaderClick = when {
                                        state.isReordering -> null
                                        state.isSelecting -> toggleSelected
                                        isSingleDefaultBundle -> null
                                        else -> toggleExpanded
                                    },
                                    // The tour holds the first card open for its own steps, and a lone
                                    // source has its own card for everything a selection would offer
                                    onLongClick = selectOnLongPress.takeUnless {
                                        isTourRunning || state.isReordering || isSingleDefaultBundle
                                    },
                                    isSelectionMode = state.isSelecting,
                                    isSelected = state.selection.contains(bundle.uid),
                                    forceExpanded = isSingleDefaultBundle,
                                    isDragging = itemIsDragging,
                                    dragHandleModifier = if (state.isReordering) {
                                        Modifier.draggableHandle(
                                            onDragStarted = {
                                                isDragging = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = { isDragging = false }
                                        )
                                    } else null,
                                    onMove = if (state.isReordering) {
                                        { by -> moveSource(bundle.uid, by) }
                                    } else null,
                                    tour = onboardingState?.takeIf { isFirstCard },
                                    modifier = Modifier.zIndex(if (itemIsDragging) 1f else 0f)
                                )
                            }
                        }
                    }
                }
            }
        }

        ListScrollbar(
            listState = listState,
            // The same step at both ends as on the screens, so the end shows past rounded corners
            modifier = Modifier.padding(
                top = insets.top + Defaults.SpaceMedium,
                bottom = listBottom + Defaults.SpaceMedium
            ),
            alphabetTargets = sourceScrollTargets,
            alphabetMode = alphabetScrollMode
        )

        ScrollToTopButton(listState = listState, extraBottomPadding = listBottom)
    }

    // Delete confirmation dialog: one source is shown as itself, several are listed by name
    DialogHost(target = bundlesToDelete.value.takeIf { it.isNotEmpty() }) { bundles ->
        val single = bundles.singleOrNull()
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.sources_dialog_delete_confirm_body),
            primaryText = stringResource(R.string.delete),
            subject = single?.let { bundle ->
                {
                    ConfirmSubject(name = bundle.displayTitle) { modifier ->
                        BundleIcon(bundle = bundle, modifier = modifier)
                    }
                }
            },
            accentColor = single?.let { rememberSourceHeaderColor(it) },
            items = if (single == null) {
                bundles.map { ConfirmItem(MaterialSymbols.Rounded.Folder_code, it.displayTitle) }
            } else emptyList(),
            onDismiss = { bundlesToDelete.value = emptyList() },
            onConfirm = {
                onDelete(bundles)
                bundlesToDelete.value = emptyList()
                state.exitSelection()
            }
        )
    }

    // Pre-release enable confirmation dialog
    DialogHost(target = bundleToConfirmPrerelease.value) { bundle ->
        ConfirmDialog(
            title = stringResource(R.string.sources_prerelease_warning_title),
            message = stringResource(R.string.sources_prerelease_warning_message),
            primaryText = stringResource(R.string.enable),
            isPrimaryDestructive = false,
            onDismiss = { bundleToConfirmPrerelease.value = null },
            onConfirm = {
                bundleToConfirmPrerelease.value = null
                applyPrerelease(bundle, true)
            }
        )
    }

    // New signing key confirmation dialog
    DialogHost(target = bundleToTrustKey) { bundle ->
        SigningKeyTrustDialog(
            bundle = bundle,
            trustedKey = bundle.signingKeyFingerprint,
            newKey = (metadataFetchErrors[bundle.uid] as? PatchBundleSignerChangedException)?.signerFingerprint,
            onTrust = {
                bundleToTrustKey = null
                scope.launch { patchBundleRepository.trustNewSigningKey(bundle) }
            },
            onDismiss = { bundleToTrustKey = null }
        )
    }

    // Patches dialog
    DialogHost(target = bundleToShowPatches.value) { src ->
        BundlePatchesDialog(
            onDismissRequest = { bundleToShowPatches.value = null },
            src = src,
            initialQuery = search.query
        )
    }

    DialogHost(target = bundleToShowApps.value) { src ->
        SourceAppsDialog(
            onDismissRequest = { bundleToShowApps.value = null },
            src = src
        )
    }

    // Outdated manager dialog, shared with the pre-flight check done when patching starts
    DialogHost(target = bundleRequiringManagerUpdate) { bundle ->
        IncompatiblePatcherVersionDialog(
            bundleName = bundle.displayTitle,
            requiredVersion = bundle.requiredPatcherVersion.orEmpty(),
            onDismiss = { bundleRequiringManagerUpdate = null }
        )
    }

    // Changelog dialog
    BundleChangelogHost(
        request = bundleToShowChangelogUid?.let { BundleChangelogRequest(it) },
        sources = sources,
        onDismissRequest = { bundleToShowChangelogUid = null }
    )
}

private fun List<PatchBundleSource>.sortedForSourceSort(
    sortMode: SourceBundleSortMode,
    manualOrder: List<Int>
): List<PatchBundleSource> = when (sortMode) {
    SourceBundleSortMode.MANUAL -> {
        val byUid = associateBy { it.uid }
        val ordered = manualOrder.mapNotNull { uid -> byUid[uid] }
        val orderedUids = ordered.map { it.uid }.toSet()
        ordered + filter { it.uid !in orderedUids }
    }

    SourceBundleSortMode.LAST_UPDATED -> sortedWith(
        compareByDescending<PatchBundleSource> { it.updatedAt ?: it.createdAt ?: 0L }
            .thenBy { it.sourceSortTitle() }
            .thenBy { it.uid }
    )

    SourceBundleSortMode.NAME_ASC -> sortedWith(
        compareBy<PatchBundleSource> { it.sourceSortTitle() }
            .thenBy { it.uid }
    )

    SourceBundleSortMode.NAME_DESC -> sortedWith(
        compareByDescending<PatchBundleSource> { it.sourceSortTitle() }
            .thenBy { it.uid }
    )

    SourceBundleSortMode.ENABLED_FIRST -> sortedWith(
        compareByDescending<PatchBundleSource> { it.enabled }
            .thenBy { it.sourceSortTitle() }
            .thenBy { it.uid }
    )
}

private fun PatchBundleSource.sourceSortTitle(): String =
    displayTitle.lowercase(Locale.ROOT)

/** Whether the source's own name answers to a search query, before its patches are consulted. */
private fun PatchBundleSource.matchesQuery(query: String): Boolean =
    displayTitle.contains(query, ignoreCase = true) || name.contains(query, ignoreCase = true)

/**
 * Apps a source still [brought] to the home screen, out of all it [listed], and how many of them
 * were left out on arrival without the user having looked yet. None listed drops the card's row.
 */
private data class SourceAppCount(val brought: Int, val listed: Int, val unseen: Int) {
    companion object {
        val NONE = SourceAppCount(0, 0, 0)
    }
}

/** What a source card reports about its source, beyond the source itself. */
private data class SourceCardInfo(
    val patchCount: Int,
    /** Patches matching the page's search, or null while nothing is searched. */
    val patchMatchCount: Int?,
    val appCount: SourceAppCount,
    val isUpdating: Boolean,
    val metadataFetchError: Throwable?,
    val blockedInfo: BlocklistRepository.BlockedEntry?,
    val useExperimentalVersions: Boolean
)

/** What a source card's controls do. A null toggle or [onShare] leaves its control out. */
private class SourceCardActions(
    val onPatchesClick: () -> Unit,
    val onAppsClick: () -> Unit,
    val onVersionClick: () -> Unit,
    val onOutdatedManagerClick: () -> Unit,
    val onReviewSigningKey: () -> Unit,
    val onOpenInBrowser: () -> Unit,
    val onReportIssue: () -> Unit,
    val onShare: (() -> Unit)?,
    val onPrereleasesToggle: ((Boolean) -> Unit)?,
    val onExperimentalVersionsToggle: ((Boolean) -> Unit)?,
    val onDisable: () -> Unit,
    val onUpdate: () -> Unit,
    val onRename: () -> Unit,
    val onDelete: () -> Unit
)

/** Rows of the first card the tour points at. */
private val SourceTourTargets = setOf(
    TourTarget.SOURCE_PATCHES,
    TourTarget.SOURCE_VERSION,
    TourTarget.SOURCE_APPS,
    TourTarget.SOURCE_PRERELEASE
)

/**
 * Card for individual bundle management.
 *
 * @param onHeaderClick What a tap on the header does, folding the card or picking it, or null
 *   while it does nothing.
 * @param onLongClick Starts or adds to a selection, or null while there is none to make.
 * @param dragHandleModifier Set while the sources are being reordered, which a handle takes the
 *   header's end for.
 * @param onMove Moves the source by that many places, set while reordering, for a screen reader
 *   to do without the drag.
 */
@Composable
private fun BundleManagementCard(
    bundle: PatchBundleSource,
    info: SourceCardInfo,
    actions: SourceCardActions,
    expanded: Boolean,
    onHeaderClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    forceExpanded: Boolean = false,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier? = null,
    onMove: ((Int) -> Unit)? = null,
    // Set on the card the tour points into
    tour: OnboardingState? = null
) {
    // Localized strings for accessibility
    val expandedState = stringResource(R.string.expanded)
    val collapsedState = stringResource(R.string.collapsed)
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)
    val openInBrowser = stringResource(R.string.sources_management_open_in_browser)
    val reportIssue = stringResource(R.string.sources_management_report_issue)
    val share = stringResource(R.string.share)
    val copy = stringResource(R.string.copy)
    val copyToClipboard = rememberCopyToClipboard()
    val patchesLabel = stringResource(R.string.patches)
    val selectLabel = stringResource(R.string.accessibility_select_source)
    val moveUpLabel = stringResource(R.string.accessibility_move_up)
    val moveDownLabel = stringResource(R.string.accessibility_move_down)

    val isBlocked = info.blockedInfo != null
    val isEnabled = bundle.enabled && !isBlocked
    val isUnavailable = info.metadataFetchError != null || bundle.state is PatchBundleSource.State.Missing
    val signingKeyChanged = info.metadataFetchError is PatchBundleSignerChangedException

    // Neutral fill for all, so the page reads as one list; a working source keeps its color on the edge
    // and controls, a disabled one drops it rather than turn red, which stays for an unusable source
    val accentColor = usableAppAccent(rememberBundleAccent(bundle).takeIf { isEnabled && !isUnavailable })
    val cardColor = when {
        isBlocked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        isUnavailable -> SemanticTone.Warning.container.copy(alpha = 0.15f)
        else -> cardFill()
    }
    val animatedColor by animateColorAsState(cardColor, label = "bundle_card_color")

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            isUnavailable -> SemanticTone.Warning.accent.copy(alpha = 0.5f)
            accentColor != null -> appAccentBorder(accentColor)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        label = "bundle_card_border_color"
    )

    // Whether a tap on the header folds the card, rather than picks it or does nothing mid-reorder
    val folds = !forceExpanded && !isSelectionMode && dragHandleModifier == null
    val headerInteraction = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bundle_card_scale"
    )

    SelectableCard(
        modifier = modifier,
        isSelected = isSelected,
        isSelectionMode = isSelectionMode
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(Defaults.CornerLarge),
            tonalElevation = if (isDragging) 8.dp else 3.dp,
            color = animatedColor,
            // The fill is a blend of the card's own, which the theme has no content color paired with
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = CardBorder.of(animatedBorderColor)
        ) {
            ProvideCardAccent(accentColor, cardColor) {
                // Build content description
                val contentDesc = remember(bundle.displayTitle, isEnabled, expanded, folds) {
                    buildString {
                        append(bundle.displayTitle)
                        append(", ")
                        if (isEnabled) {
                            append(enabledState)
                        } else {
                            append(disabledState)
                        }
                        if (folds) {
                            append(", ")
                            append(if (expanded) expandedState else collapsedState)
                        }
                    }
                }

                Column(modifier = Modifier.padding(Defaults.SpaceLarge)) {
                    // Click target only on the header so expanded children stay independently focusable for screen readers
                    BundleCardHeader(
                        bundle = bundle,
                        expanded = expanded,
                        showChevron = !forceExpanded,
                        selected = isSelected.takeIf { isSelectionMode },
                        dragHandleModifier = dragHandleModifier,
                        enabled = isEnabled,
                        metadataFetchError = info.metadataFetchError,
                        unavailable = isUnavailable,
                        blockedInfo = info.blockedInfo,
                        patchMatchCount = info.patchMatchCount,
                        onShowPatchMatches = actions.onPatchesClick,
                        modifier = Modifier
                            .then(
                                if (onHeaderClick != null || onLongClick != null) {
                                    Modifier.combinedClickable(
                                        // The card answers a tap by folding
                                        indication = shapedIndication(
                                            RoundedCornerShape(Defaults.CornerMedium),
                                            press = false
                                        ),
                                        interactionSource = headerInteraction,
                                        onClick = { onHeaderClick?.invoke() },
                                        onLongClick = onLongClick
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .semantics(mergeDescendants = true) {
                                if (isSelectionMode) {
                                    selected = isSelected
                                } else if (folds) {
                                    role = Role.Button
                                    stateDescription = if (expanded) expandedState else collapsedState
                                }
                                this.contentDescription = contentDesc
                                customActions = buildList {
                                    // The match badge is a tap target the merged node swallows otherwise
                                    if (info.patchMatchCount != null) {
                                        add(CustomAccessibilityAction(patchesLabel) { actions.onPatchesClick(); true })
                                    }
                                    // A long press is the only way into a selection, which a
                                    // screen reader cannot announce on its own
                                    if (onLongClick != null && !isSelectionMode) {
                                        add(CustomAccessibilityAction(selectLabel) { onLongClick(); true })
                                    }
                                    if (onMove != null) {
                                        add(CustomAccessibilityAction(moveUpLabel) { onMove(-1); true })
                                        add(CustomAccessibilityAction(moveDownLabel) { onMove(1); true })
                                    }
                                }
                            }
                    )

                    // Expanded content
                    ListRowFold(visible = expanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
                        ) {
                            Column {
                                // Blocked source banner (shown when the source appears on the remote blocklist)
                                val blockedLabel = stringResource(R.string.sources_management_source_blocked_badge)
                                val blockedReason = info.blockedInfo?.reason?.trim()?.takeIf { it.isNotEmpty() }
                                    ?.replaceFirstChar { it.uppercaseChar() }
                                CardNotice(
                                    visible = info.blockedInfo != null,
                                    text = if (blockedReason != null) "$blockedLabel: $blockedReason" else blockedLabel,
                                    icon = MaterialSymbols.Rounded.Block
                                )

                                // Metadata unavailable hint (shown when patches-bundle.json / remote fetch failed)
                                CardNotice(
                                    visible = isUnavailable && !signingKeyChanged,
                                    text = if (bundle.state is PatchBundleSource.State.Missing) {
                                        stringResource(R.string.sources_management_metadata_unavailable_hint_missing)
                                    } else {
                                        stringResource(R.string.sources_management_metadata_unavailable_hint)
                                    },
                                    icon = MaterialSymbols.Rounded.Cloud_off
                                )

                                // Signing key hint (shown when an update was refused for its signer)
                                CardNotice(
                                    visible = signingKeyChanged,
                                    text = stringResource(R.string.sources_management_signing_key_changed_hint),
                                    icon = MaterialSymbols.Rounded.Gpp_bad,
                                    onClick = actions.onReviewSigningKey
                                )

                                // Held back hint (shown when reading the source killed the process)
                                CardNotice(
                                    visible = bundle.isHeldBack,
                                    text = stringResource(R.string.sources_management_held_back_hint),
                                    icon = MaterialSymbols.Rounded.Error
                                )

                                // Outdated manager hint
                                CardNotice(
                                    visible = bundle.requiresManagerUpdate,
                                    text = stringResource(
                                        R.string.sources_management_outdated_manager_hint,
                                        bundle.requiredPatcherVersion.orEmpty(),
                                        BuildConfig.VERSION_NAME,
                                        BuildConfig.PATCHER_VERSION
                                    ),
                                    icon = MaterialSymbols.Rounded.System_update,
                                    onClick = actions.onOutdatedManagerClick
                                )
                            }

                            // What the source holds, in one panel the way the app details list theirs
                            InfoPanel {
                                InfoRow(
                                    modifier = Modifier.tourTarget(TourTarget.SOURCE_PATCHES, tour),
                                    icon = MaterialSymbols.Rounded.Info,
                                    label = stringResource(R.string.patches),
                                    value = if (info.patchMatchCount != null) {
                                        "${info.patchMatchCount}/${info.patchCount}"
                                    } else {
                                        info.patchCount.toString()
                                    },
                                    onClick = actions.onPatchesClick,
                                    // A disabled source still lists what it holds, which is what the
                                    // decision to switch it back on is made on. A blocked one does not,
                                    // and neither does one whose patches never loaded
                                    enabled = !isBlocked && !info.isUpdating && info.patchCount > 0
                                )

                                SettingsDivider()

                                InfoRow(
                                    modifier = Modifier.tourTarget(TourTarget.SOURCE_VERSION, tour),
                                    icon = MaterialSymbols.Rounded.Update,
                                    label = stringResource(R.string.version),
                                    value = bundle.version?.normalizeVersion()?.isolateLtr() ?: "N/A",
                                    onClick = actions.onVersionClick,
                                    enabled = !info.isUpdating
                                )

                                bundle.signingKeyFingerprint?.let { fingerprint ->
                                    SettingsDivider()

                                    InfoRow(
                                        icon = MaterialSymbols.Rounded.Verified,
                                        label = stringResource(R.string.sources_management_signed_by),
                                        value = fingerprint,
                                        // The whole key, which is what a developer publishes to compare against
                                        onClick = { bundle.signingKey?.let(copyToClipboard) },
                                        trailing = {
                                            Icon(
                                                imageVector = MaterialSymbols.Rounded.Content_copy,
                                                contentDescription = copy,
                                                modifier = Modifier.size(Defaults.IconSizeSmall),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    )
                                }

                                // Apps, for any source naming some. It is also the one way back from an
                                // app kept from the source that does not depend on which mode the user
                                // patches in
                                val (broughtApps, listedApps, unseenApps) = info.appCount
                                if (listedApps > 0) {
                                    SettingsDivider()

                                    val count = if (broughtApps < listedApps) {
                                        "$broughtApps/$listedApps"
                                    } else {
                                        listedApps.toString()
                                    }
                                    InfoRow(
                                        modifier = Modifier.tourTarget(TourTarget.SOURCE_APPS, tour),
                                        icon = MaterialSymbols.Rounded.Apps,
                                        label = stringResource(R.string.sources_apps),
                                        value = if (unseenApps > 0) {
                                            pluralStringResource(R.plurals.sources_apps_count_with_new, unseenApps, count, unseenApps)
                                        } else {
                                            count
                                        },
                                        onClick = actions.onAppsClick,
                                        enabled = !info.isUpdating
                                    )
                                }
                            }

                            // Repository actions, as wide as the four pill action bar below. Connected,
                            // since every one of them leads out to the repository
                            if (bundle is RemotePatchBundle) {
                                ActionPillRow(
                                    modifier = Modifier.padding(vertical = Defaults.SpaceExtraSmall),
                                    spacing = ButtonGroupDefaults.ConnectedSpaceBetween,
                                    stretchLabelsTo = 4
                                ) {
                                    ActionPillButton(
                                        onClick = actions.onOpenInBrowser,
                                        icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored,
                                        contentDescription = openInBrowser + " " + bundle.displayTitle,
                                        label = openInBrowser,
                                        tooltip = openInBrowser,
                                        shape = ConnectedShapes.Pill.leading
                                    )

                                    if (actions.onShare != null) {
                                        ActionPillButton(
                                            onClick = actions.onShare,
                                            icon = MaterialSymbols.Rounded.Share,
                                            contentDescription = share + " " + bundle.displayTitle,
                                            tooltip = share,
                                            shape = ConnectedShapes.Pill.middle
                                        )
                                    }

                                    ActionPillButton(
                                        onClick = actions.onReportIssue,
                                        icon = MaterialSymbols.Rounded.Bug_report,
                                        contentDescription = reportIssue + " " + bundle.displayTitle,
                                        tooltip = reportIssue,
                                        shape = ConnectedShapes.Pill.trailing
                                    )
                                }
                            }

                            SettingsDivider(fullWidth = true)

                            // Resolve prerelease state once
                            val currentUsePrerelease = bundle.usesPrerelease

                            if (actions.onPrereleasesToggle != null || actions.onExperimentalVersionsToggle != null) {
                                // Grouped apart from the card's spaced column, so the experimental toggle
                                // brings its gap along as it folds in rather than the column opening it at once
                                Column {
                                    // Prerelease toggle (for JsonPatchBundle with GitHub endpoint or APIPatchBundle)
                                    if (actions.onPrereleasesToggle != null) {
                                        ToggleRow(
                                            title = stringResource(R.string.sources_management_prerelease_toggle),
                                            description = stringResource(R.string.sources_management_prerelease_toggle_description),
                                            checked = currentUsePrerelease,
                                            onCheckedChange = actions.onPrereleasesToggle,
                                            enabled = !info.isUpdating,
                                            isLoading = info.isUpdating,
                                            showDivider = false,
                                            rowModifier = Modifier.tourTarget(TourTarget.SOURCE_PRERELEASE, tour)
                                        )
                                    }

                                    // Experimental versions toggle - shown for any bundle type that has experimental app version targets.
                                    // For remote bundles (prerelease supported) it additionally requires prereleases to be ON.
                                    ListRowFold(
                                        visible = actions.onExperimentalVersionsToggle != null &&
                                                (actions.onPrereleasesToggle == null || currentUsePrerelease)
                                    ) {
                                        ToggleRow(
                                            title = stringResource(R.string.sources_management_experimental_versions_toggle),
                                            description = stringResource(R.string.sources_management_experimental_versions_toggle_description),
                                            checked = info.useExperimentalVersions,
                                            onCheckedChange = { actions.onExperimentalVersionsToggle?.invoke(it) },
                                            modifier = Modifier.padding(
                                                top = if (actions.onPrereleasesToggle != null) Defaults.SpaceSmall else 0.dp
                                            ),
                                            showDivider = false
                                        )
                                    }
                                }

                                SettingsDivider(fullWidth = true)
                            }

                            // Action bar
                            ActionPillRow(modifier = Modifier.padding(top = Defaults.SpaceExtraSmall)) {
                                if (!forceExpanded) {
                                    val disableEnableVerb = stringResource(
                                        if (bundle.enabled) R.string.disable else R.string.enable
                                    )
                                    val disableEnableDesc = disableEnableVerb + " " + bundle.displayTitle
                                    val disableDone = stringResource(
                                        if (bundle.enabled) R.string.sources_management_source_disabled
                                        else R.string.sources_management_source_enabled
                                    )

                                    // Disable button
                                    ActionPillButton(
                                        onClick = actions.onDisable,
                                        icon = if (bundle.enabled) MaterialSymbols.Rounded.Block else MaterialSymbols.Rounded.Check_circle,
                                        contentDescription = disableEnableDesc,
                                        tooltip = disableEnableVerb,
                                        confirmation = disableDone,
                                        enabled = !isBlocked
                                    )
                                }

                                val isLocal = bundle is LocalPatchBundle
                                if (bundle is RemotePatchBundle || isLocal) {
                                    val updateVerb = stringResource(R.string.update)
                                    val updateDesc = updateVerb + " " + bundle.displayTitle
                                    val updateStarted = stringResource(R.string.sources_management_source_updating)
                                    // Update button. A local source has nothing to fetch from, so it asks
                                    // for a replacement file instead and reports progress once one is picked
                                    ActionPillButton(
                                        onClick = actions.onUpdate,
                                        icon = MaterialSymbols.Rounded.Refresh,
                                        contentDescription = updateDesc,
                                        tooltip = updateVerb,
                                        confirmation = updateStarted.takeUnless { isLocal },
                                        enabled = !isBlocked
                                    )
                                }

                                if (!bundle.isDefault) {
                                    val renameVerb = stringResource(R.string.rename)
                                    val deleteVerb = stringResource(R.string.delete)
                                    val renameDesc = renameVerb + " " + bundle.displayTitle
                                    val deleteDesc = deleteVerb + " " + bundle.displayTitle
                                    // Rename button
                                    ActionPillButton(
                                        onClick = actions.onRename,
                                        icon = MaterialSymbols.Rounded.Edit,
                                        contentDescription = renameDesc,
                                        tooltip = renameVerb
                                    )

                                    // Delete button
                                    ActionPillButton(
                                        onClick = actions.onDelete,
                                        icon = MaterialSymbols.Rounded.Delete,
                                        contentDescription = deleteDesc,
                                        tooltip = deleteVerb,
                                        destructive = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleCardHeader(
    bundle: PatchBundleSource,
    expanded: Boolean,
    showChevron: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    metadataFetchError: Throwable? = null,
    /** Whether the source's metadata could not be read, or its file is gone. */
    unavailable: Boolean = false,
    blockedInfo: BlocklistRepository.BlockedEntry? = null,
    patchMatchCount: Int? = null,
    onShowPatchMatches: (() -> Unit)? = null,
    // Whether the card is picked, null outside a selection. Marked where the chevron sits
    selected: Boolean? = null,
    // Set while reordering, which puts the drag handle where the chevron sits
    dragHandleModifier: Modifier? = null,
) {
    // The name dims by color rather than alpha so it stays crisp and above the lines under it,
    // which fade along with the icon, so a source switched off reads as such before its badge is read
    val titleColor by animateColorAsState(
        targetValue = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "bundle_title_color"
    )
    val detailsAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_SOURCE_ALPHA,
        label = "bundle_details_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Defaults.SpaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
    ) {
        // Bundle icon with GitHub avatar support
        BundleIcon(
            bundle = bundle,
            enabled = enabled,
            metadataFetchError = metadataFetchError,
            modifier = Modifier.size(44.dp)
        )
        // Title + badges
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bundle.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Version • date
            // When showChevron=false (single bundle): show only date, no version.
            // When showChevron=true (multiple bundles): show version • date.
            val timestamp = bundle.updatedAt ?: bundle.createdAt
            val versionText = if (showChevron) bundle.version?.normalizeVersion() else null
            val dateText = remember(timestamp) { timestamp?.let { getRelativeTimeString(it) } }

            if (versionText != null || dateText != null) {
                Row(
                    modifier = Modifier.graphicsLayer { alpha = detailsAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
                ) {
                    if (versionText != null) {
                        Text(
                            text = versionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (versionText != null && dateText != null) {
                        Text(
                            text = "  •  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (dateText != null) {
                        Icon(
                            imageVector = if (bundle.updatedAt != null) MaterialSymbols.Rounded.Schedule else MaterialSymbols.Rounded.Calendar_today,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // The badges inside animate their own entry and exit, so animating the row on top of
            // that only buys a second measure pass per frame while the list is scrolling
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
            ) {
                // What the search found inside the source leads, being the reason the card is on
                // screen at all. It swaps in without an animation of its own, since the list
                // behind it is already re-ordering on every keystroke
                if (patchMatchCount != null) {
                    StatusBadge(
                        text = pluralStringResource(
                            R.plurals.sources_search_patch_matches,
                            patchMatchCount,
                            patchMatchCount.toString()
                        ),
                        icon = MaterialSymbols.Rounded.Search,
                        tone = SemanticTone.Primary,
                        onClick = onShowPatchMatches
                    )
                }

                // Bundle type badge, faded with the version line since it describes the source as well
                BundleTypeBadge(
                    type = bundle.sourceType,
                    modifier = Modifier.graphicsLayer { alpha = detailsAlpha }
                )

                val signingKeyChanged = metadataFetchError is PatchBundleSignerChangedException
                SourceStateBadge(
                    visible = unavailable && !signingKeyChanged,
                    text = stringResource(R.string.sources_management_metadata_unavailable)
                )
                SourceStateBadge(
                    visible = signingKeyChanged,
                    text = stringResource(R.string.sources_management_signing_key_changed_badge)
                )
                SourceStateBadge(
                    visible = bundle.requiresManagerUpdate,
                    text = stringResource(R.string.sources_management_outdated_manager_badge)
                )
                SourceStateBadge(
                    visible = bundle.isHeldBack,
                    text = stringResource(R.string.sources_management_held_back_badge)
                )
                SourceStateBadge(
                    visible = blockedInfo != null,
                    text = stringResource(R.string.sources_management_source_blocked_badge)
                )
                // Switching a source off is a choice rather than a fault, so it takes no alarm color
                SourceStateBadge(
                    visible = !enabled && blockedInfo == null,
                    text = stringResource(R.string.disabled),
                    tone = SemanticTone.Neutral,
                    icon = MaterialSymbols.Rounded.Power_settings_new
                )
            }
        }

        if (dragHandleModifier != null) {
            DragHandle(modifier = dragHandleModifier)
        } else {
            // The chevron gives way to the mark while the card is picked rather than folded
            SelectionMark(
                selected = selected,
                idle = if (showChevron) {
                    { ExpandChevron(expanded = expanded) }
                } else null
            )
        }
    }
}

/**
 * Header badge for a state a source can enter and leave while the page is open, easing in and out
 * beside the badges that stay.
 */
@Composable
private fun RowScope.SourceStateBadge(
    visible: Boolean,
    text: String,
    tone: SemanticTone = SemanticTone.Error,
    icon: ImageVector? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.expandHorizFadeIn,
        exit = Animations.shrinkHorizFadeOut
    ) {
        AppAccentBadge(text = text, accentColor = null, icon = icon, tone = tone)
    }
}

/** Card banner for a problem with a source, folding in and out as the problem comes and goes. */
@Composable
private fun CardNotice(
    visible: Boolean,
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    ListRowFold(visible = visible) {
        Notice(
            modifier = modifier,
            text = text,
            icon = icon,
            tone = SemanticTone.Error,
            density = NoticeDensity.Compact,
            onClick = onClick
        )
    }
}

/**
 * Color a source's own dialogs head themselves with: its icon's, or the theme's accent where the
 * icon has none. A source that is off keeps it, since its dialogs say so in words of their own.
 */
@Composable
internal fun rememberSourceHeaderColor(bundle: PatchBundleSource): Color =
    rememberBundleAccent(bundle) ?: MaterialTheme.colorScheme.primary
