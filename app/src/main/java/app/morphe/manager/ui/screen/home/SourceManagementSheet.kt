/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.avatarUrls
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
import app.morphe.manager.ui.screen.patcher.IncompatiblePatcherVersionDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

/** Enough placeholder rows to fill the sheet on open without implying a count. */
private val SourceShimmerRows = (0 until 4).toList()

/** Share of a source's disc its fallback glyph takes, the 24dp a 44dp disc has always given it. */
private const val BUNDLE_GLYPH_FRACTION = 0.55f

/** How far a switched off source's icon and the lines under its name fade back. */
private const val DISABLED_SOURCE_ALPHA = 0.7f

/**
 * Bottom sheet for managing patch bundles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleManagementSheet(
    onDismissRequest: () -> Unit,
    onAddSource: () -> Unit,
    onDelete: (PatchBundleSource) -> Unit,
    onDisable: (PatchBundleSource) -> Unit,
    onUpdate: (PatchBundleSource) -> Unit,
    onRename: (PatchBundleSource) -> Unit,
    onReorder: (List<Int>) -> Unit,
    globalOnboardingState: GlobalOnboardingState? = null
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val prefs: PreferencesManager = koinInject()
    val sourceMuteRepository: SourceMuteRepository = koinInject()
    val scope = rememberCoroutineScope()

    val sources by patchBundleRepository.sources.collectAsStateWithLifecycle()
    val bundleState by patchBundleRepository.bundleState.collectAsStateWithLifecycle()
    val isLoadingSources = bundleState is PatchBundleRepository.BundleState.Loading
    val patchCounts by patchBundleRepository.patchCountsFlow.collectAsStateWithLifecycle(emptyMap())
    val manualUpdateInfo by patchBundleRepository.manualUpdateInfo.collectAsStateWithLifecycle(emptyMap())
    val activeUpdateUids by patchBundleRepository.activeUpdateUidsFlow.collectAsStateWithLifecycle(emptySet())
    val metadataFetchErrors by patchBundleRepository.metadataFetchErrors.collectAsStateWithLifecycle(emptyMap())
    val experimentalVersionsEnabled by prefs.bundleExperimentalVersionsEnabled.getAsState()
    // Every source, not only the enabled ones: a disabled source still answers a patch search
    // and still declares whether it carries experimental targets
    val bundleInfo by patchBundleRepository.allBundlesInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val blockedSources by patchBundleRepository.blockedSources.collectAsStateWithLifecycle(emptyMap())
    val keptFrom by sourceMuteRepository.mutedSources.collectAsStateWithLifecycle(emptyMap())
    // How many of its apps each source still brings, out of all it names
    val appCounts = remember(bundleInfo, keptFrom) {
        bundleInfo.mapValues { (_, info) -> info.appsBrought(keptFrom).size to info.listedApps().size }
    }

    val showSheetOnboarding = globalOnboardingState?.sheetOnboardingActive == true

    val bundleToDelete = remember { mutableStateOf<PatchBundleSource?>(null) }
    // Set when the user flips pre-releases on: the toggle waits for confirmation first,
    // so the meaning of unstable testing builds is explained before anything changes
    val bundleToConfirmPrerelease = remember { mutableStateOf<PatchBundleSource?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    // Search is offered from two sources up
    val isSearchable = sources.size >= 2
    val search = rememberSearchFieldState(searchable = isSearchable)
    // Expanded state lifted out of LazyColumn so it survives scroll-off-screen recomposition
    var expandedBundleUids by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Drag-and-drop state
    val listState = rememberLazyListState()
    var listWindowY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(globalOnboardingState) {
        globalOnboardingState?.onScrollToFirstSource = {
            scope.launch { listState.animateScrollToItem(0) }
        }
        globalOnboardingState?.onScrollToPrerelease = {
            scope.launch {
                val bounds = globalOnboardingState.sourcesPrereleaseBounds ?: return@launch
                val offset = (bounds.top - listWindowY).coerceAtLeast(0f).toInt()
                listState.animateScrollToItem(0, offset)
            }
        }
    }
    var localOrder by remember { mutableStateOf(sources.map { it.uid }) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(sources) {
        if (isDragging) return@LaunchedEffect
        val sourceUids = sources.map { it.uid }
        val existing = localOrder.filter { uid -> uid in sourceUids }
        val added = sourceUids.filter { it !in existing }
        val merged = existing + added
        if (merged != localOrder) localOrder = merged
    }
    val sortModePreference by prefs.sourceBundleSortMode.getAsState()
    val sourceSortMode = SourceBundleSortMode.fromPreference(sortModePreference)
    val isManualSort = sourceSortMode == SourceBundleSortMode.MANUAL
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
    val visibleSources = remember(orderedSources, patchMatchCounts, search.query) {
        val query = search.query
        if (query.isBlank()) return@remember orderedSources
        orderedSources
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

    val bundleToShowPatches = remember { mutableStateOf<PatchBundleSource?>(null) }
    val bundleToShowApps = remember { mutableStateOf<PatchBundleSource?>(null) }
    var bundleRequiringManagerUpdate by remember { mutableStateOf<PatchBundleSource?>(null) }
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

    // Auto-enable the default bundle if it's the only one and disabled
    LaunchedEffect(sources) {
        if (sources.size == 1) {
            val singleBundle = sources.first()
            if (singleBundle.isDefault && !singleBundle.enabled) {
                onDisable(singleBundle) // This will toggle it to enabled
            }
        }
    }

    AppBottomSheet(onDismissRequest = onDismissRequest) {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val failedToOpenUrlText = stringResource(R.string.sources_management_failed_to_open_url)
        fun openUrl(url: String) {
            try {
                uriHandler.openUri(url)
            } catch (_: Exception) {
                context.toast(failedToOpenUrlText)
            }
        }

        fun shareText(text: String) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }

        // Registered inside the sheet content so it outranks the sheet's own dismiss handler
        SearchFieldBackHandler(search)

        Box {
            Column(Modifier.fillMaxWidth()) {
                // Header - outside scrollable area
                PanelHeader(
                    title = {
                        Column {
                            PanelTitle(text = stringResource(R.string.sources_management_title))
                            PanelSubtitle(
                                text = pluralStringResource(
                                    R.plurals.sources_management_subtitle,
                                    sources.size,
                                    sources.size.toString()
                                )
                            )
                        }
                    }
                ) {
                    AnimatedVisibility(visible = isSearchable) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TitleAction(
                                icon = if (search.visible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search),
                                onClick = { search.toggle() },
                                style = TitleActionStyle.Toggle,
                                active = search.visible
                            )

                            val activeSortLabel = stringResource(sourceSortMode.labelRes)
                            TitleAction(
                                icon = Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = stringResource(R.string.sort),
                                onClick = { showSortDialog = true },
                                modifier = Modifier.semantics {
                                    role = Role.Button
                                    stateDescription = activeSortLabel
                                },
                                style = TitleActionStyle.Neutral
                            )
                        }
                    }
                    TitleAction(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                        onClick = onAddSource,
                        style = TitleActionStyle.Neutral
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AnimatedVisibility(
                        visible = search.visible,
                        enter = Animations.expandFadeEnter,
                        exit = Animations.shrinkFadeExit
                    ) {
                        HomeSearchTextField(
                            value = search.query,
                            onValueChange = { search.query = it },
                            label = stringResource(R.string.sources_search),
                            requestFocus = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // Bundle cards. The navigation bar inset goes on the box, so the scrollbar and the
                // scroll-to-top button above the list stop short of it along with the list
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .navigationBarsPadding()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords -> listWindowY = coords.boundsInWindow().top }
                            .verticalScrollFade(listState),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp
                        )
                    ) {
                        if (isLoadingSources) {
                            items(SourceShimmerRows, key = { index -> "shimmer_$index" }) {
                                ShimmerBundleRow()
                            }
                        }

                        if (search.isFiltering && visibleSources.isEmpty()) {
                            item(key = "search_empty") {
                                EmptyState(
                                    message = stringResource(R.string.search_no_results),
                                    icon = Icons.Outlined.SearchOff
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
                            ReorderableItem(
                                reorderableState,
                                key = bundle.uid,
                                // Only wanted while dragging: elsewhere it lags behind a card growing
                                // on expand, letting it overlap the one below
                                animateItemModifier = if (isDragging) {
                                    Modifier.animateItem()
                                } else {
                                    Modifier.animateItem(placementSpec = null)
                                }
                            ) { itemIsDragging ->
                                BundleManagementCard(
                                    bundle = bundle,
                                    info = SourceCardInfo(
                                        patchCount = patchCounts[bundle.uid] ?: 0,
                                        patchMatchCount = patchMatchCounts[bundle.uid],
                                        appCount = appCounts[bundle.uid] ?: (0 to 0),
                                        updateInfo = manualUpdateInfo[bundle.uid],
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
                                        onOpenInBrowser = {
                                            openUrl(
                                                manualUpdateInfo[bundle.uid]?.pageUrl
                                                    ?: (bundle as? RemotePatchBundle)?.browsePageUrl
                                                    ?: SOURCE_REPO_URL
                                            )
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
                                        onDisable = { onDisable(bundle) },
                                        onUpdate = { onUpdate(bundle) },
                                        onRename = { onRename(bundle) },
                                        onDelete = { bundleToDelete.value = bundle }
                                    ),
                                    expanded = isSingleDefaultBundle || bundle.uid in expandedBundleUids ||
                                        (showSheetOnboarding && isFirstCard),
                                    onToggleExpanded = {
                                        expandedBundleUids = if (bundle.uid in expandedBundleUids) {
                                            expandedBundleUids - bundle.uid
                                        } else {
                                            expandedBundleUids + bundle.uid
                                        }
                                    },
                                    forceExpanded = isSingleDefaultBundle,
                                    isDragging = itemIsDragging,
                                    // Reorder maps list positions onto the full order, so a
                                    // filtered list would move the wrong sources
                                    longPressModifier = if (isManualSort && !search.isFiltering) {
                                        Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                isDragging = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                isDragging = false
                                                onReorder(localOrder)
                                            }
                                        )
                                    } else {
                                        Modifier
                                    },
                                    tourTargets = if (isFirstCard) {
                                        SourceCardTourTargets(
                                            onPatchesPositioned = { globalOnboardingState?.sourcesPatchesBounds = it },
                                            onVersionPositioned = { globalOnboardingState?.sourcesVersionBounds = it },
                                            onPrereleasePositioned = { globalOnboardingState?.sourcesPrereleaseBounds = it }
                                        )
                                    } else null,
                                    modifier = Modifier.zIndex(if (itemIsDragging) 1f else 0f)
                                )
                            }
                        }
                    }

                    ListScrollbar(
                        listState = listState,
                        alphabetTargets = sourceScrollTargets,
                        alphabetMode = alphabetScrollMode
                    )

                    ScrollToTopButton(listState = listState)
                }
            }
        }
    }

    if (showSortDialog) {
        SortModeSelectionDialog(
            title = stringResource(R.string.sources_sort_title),
            current = sourceSortMode,
            options = sortModeOptions<SourceBundleSortMode>(),
            onSelect = { mode ->
                scope.launch { prefs.sourceBundleSortMode.update(mode.name) }
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    // Delete confirmation dialog
    bundleToDelete.value?.let { bundle ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.sources_dialog_delete_confirm_body),
            primaryText = stringResource(R.string.delete),
            subject = {
                ConfirmSubject(name = bundle.displayTitle) { modifier ->
                    BundleIcon(bundle = bundle, modifier = modifier)
                }
            },
            accentColor = rememberSourceHeaderColor(bundle),
            onDismiss = { bundleToDelete.value = null },
            onConfirm = {
                onDelete(bundle)
                bundleToDelete.value = null
            }
        )
    }

    // Pre-release enable confirmation dialog
    bundleToConfirmPrerelease.value?.let { bundle ->
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

    // Patches dialog
    if (bundleToShowPatches.value != null) {
        BundlePatchesDialog(
            onDismissRequest = { bundleToShowPatches.value = null },
            src = bundleToShowPatches.value!!,
            initialQuery = search.query
        )
    }

    bundleToShowApps.value?.let { src ->
        SourceAppsDialog(
            onDismissRequest = { bundleToShowApps.value = null },
            src = src
        )
    }

    // Outdated manager dialog, shared with the pre-flight check done when patching starts
    bundleRequiringManagerUpdate?.let { bundle ->
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

/** What a source card reports about its source, beyond the source itself. */
private data class SourceCardInfo(
    val patchCount: Int,
    /** Patches matching the sheet's search, or null while nothing is searched. */
    val patchMatchCount: Int?,
    /** Apps the source still brings, out of all it names. None named drops the row. */
    val appCount: Pair<Int, Int>,
    val updateInfo: PatchBundleRepository.ManualBundleUpdateInfo?,
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

/** Where the onboarding tour points on the first card, reported as each target is laid out. */
private class SourceCardTourTargets(
    val onPatchesPositioned: (Rect) -> Unit,
    val onVersionPositioned: (Rect) -> Unit,
    val onPrereleasePositioned: (Rect) -> Unit
)

/**
 * Card for individual bundle management.
 */
@Composable
private fun BundleManagementCard(
    bundle: PatchBundleSource,
    info: SourceCardInfo,
    actions: SourceCardActions,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    forceExpanded: Boolean = false,
    isDragging: Boolean = false,
    longPressModifier: Modifier = Modifier,
    tourTargets: SourceCardTourTargets? = null
) {
    // Localized strings for accessibility
    val expandedState = stringResource(R.string.expanded)
    val collapsedState = stringResource(R.string.collapsed)
    val enabledState = stringResource(R.string.enabled)
    val disabledState = stringResource(R.string.disabled)
    val openInBrowser = stringResource(R.string.sources_management_open_in_browser)
    val reportIssue = stringResource(R.string.sources_management_report_issue)
    val share = stringResource(R.string.share)
    val patchesLabel = stringResource(R.string.patches)

    val isBlocked = info.blockedInfo != null
    val isEnabled = bundle.enabled && !isBlocked
    val isUnavailable = info.metadataFetchError != null || bundle.state is PatchBundleSource.State.Missing

    // Neutral fill for all, so the sheet reads as one list; a working source keeps its color on the edge
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

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bundle_card_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(Defaults.CardCornerRadius),
        tonalElevation = if (isDragging) 8.dp else 3.dp,
        color = animatedColor,
        border = CardBorder.of(animatedBorderColor)
    ) {
        ProvideCardAccent(accentColor, cardColor) {
            // Build content description
            val updateLabel = stringResource(R.string.update)
            val availableLabel = stringResource(R.string.available)
            val contentDesc = remember(bundle.displayTitle, isEnabled, expanded, forceExpanded, info.updateInfo) {
                buildString {
                    append(bundle.displayTitle)
                    append(", ")
                    if (isEnabled) {
                        append(enabledState)
                    } else {
                        append(disabledState)
                    }
                    if (!forceExpanded) {
                        append(", ")
                        append(if (expanded) expandedState else collapsedState)
                    }
                    info.updateInfo?.let {
                        append(", ")
                        append(updateLabel)
                        append(" ")
                        append(availableLabel)
                    }
                }
            }

            Column(modifier = Modifier.padding(Defaults.ContentPadding)) {
                // Click target only on the header so expanded children stay independently focusable for screen readers
                BundleCardHeader(
                    bundle = bundle,
                    updateInfo = info.updateInfo,
                    expanded = expanded,
                    showChevron = !forceExpanded,
                    enabled = isEnabled,
                    metadataFetchError = info.metadataFetchError,
                    unavailable = isUnavailable,
                    blockedInfo = info.blockedInfo,
                    patchMatchCount = info.patchMatchCount,
                    onShowPatchMatches = actions.onPatchesClick,
                    modifier = longPressModifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (!forceExpanded) onToggleExpanded()
                        }
                        .semantics(mergeDescendants = true) {
                            if (!forceExpanded) {
                                role = Role.Button
                                stateDescription = if (expanded) expandedState else collapsedState
                            }
                            this.contentDescription = contentDesc
                            // The match badge is a tap target the merged node swallows otherwise
                            if (info.patchMatchCount != null) {
                                customActions = listOf(
                                    CustomAccessibilityAction(patchesLabel) { actions.onPatchesClick(); true }
                                )
                            }
                        }
                )

                // Expanded content
                AnimatedVisibility(
                    visible = expanded,
                    enter = Animations.expandVertEnter,
                    exit = Animations.shrinkVertExit
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                    ) {
                        Column {
                            // Blocked source banner (shown when the source appears on the remote blocklist)
                            val blockedLabel = stringResource(R.string.sources_management_source_blocked_badge)
                            val blockedReason = info.blockedInfo?.reason?.trim()?.takeIf { it.isNotEmpty() }
                                ?.replaceFirstChar { it.uppercaseChar() }
                            CardNotice(
                                visible = info.blockedInfo != null,
                                text = if (blockedReason != null) "$blockedLabel: $blockedReason" else blockedLabel,
                                icon = Icons.Outlined.Block
                            )

                            // Metadata unavailable hint (shown when patches-bundle.json / remote fetch failed)
                            CardNotice(
                                visible = isUnavailable,
                                text = if (bundle.state is PatchBundleSource.State.Missing) {
                                    stringResource(R.string.sources_management_metadata_unavailable_hint_missing)
                                } else {
                                    stringResource(R.string.sources_management_metadata_unavailable_hint)
                                },
                                icon = Icons.Outlined.CloudOff
                            )

                            // Held back hint (shown when reading the source killed the process)
                            CardNotice(
                                visible = bundle.isHeldBack,
                                text = stringResource(R.string.sources_management_held_back_hint),
                                icon = Icons.Outlined.ErrorOutline
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
                                icon = Icons.Outlined.SystemUpdate,
                                modifier = Modifier.clickable(onClick = actions.onOutdatedManagerClick)
                            )
                        }

                        // What the source holds, in one panel the way the app details list theirs
                        InfoPanel {
                            InfoRow(
                                modifier = Modifier.reportBounds(tourTargets?.onPatchesPositioned),
                                icon = Icons.Outlined.Info,
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
                                modifier = Modifier.reportBounds(tourTargets?.onVersionPositioned),
                                icon = Icons.Outlined.Update,
                                label = stringResource(R.string.version),
                                value = bundle.version?.removePrefix("v")?.isolateLtr() ?: "N/A",
                                onClick = actions.onVersionClick,
                                enabled = !info.isUpdating
                            )

                            // Apps, for any source naming some. It is also the one way back from an
                            // app kept from the source that does not depend on which mode the user
                            // patches in
                            val (offeredApps, listedApps) = info.appCount
                            if (listedApps > 0) {
                                SettingsDivider()

                                InfoRow(
                                    icon = Icons.Outlined.Apps,
                                    label = stringResource(R.string.sources_apps),
                                    value = if (offeredApps < listedApps) {
                                        "$offeredApps/$listedApps"
                                    } else {
                                        listedApps.toString()
                                    },
                                    onClick = actions.onAppsClick,
                                    enabled = !info.isUpdating
                                )
                            }
                        }

                        // Repository actions, as wide as the four pill action bar below
                        if (bundle is RemotePatchBundle) {
                            ActionPillRow(
                                modifier = Modifier.padding(vertical = 4.dp),
                                stretchLabelsTo = 4
                            ) {
                                ActionPillButton(
                                    onClick = actions.onOpenInBrowser,
                                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = openInBrowser + " " + bundle.displayTitle,
                                    label = openInBrowser,
                                    tooltip = openInBrowser
                                )

                                if (actions.onShare != null) {
                                    ActionPillButton(
                                        onClick = actions.onShare,
                                        icon = Icons.Outlined.Share,
                                        contentDescription = share + " " + bundle.displayTitle,
                                        tooltip = share
                                    )
                                }

                                ActionPillButton(
                                    onClick = actions.onReportIssue,
                                    icon = Icons.Outlined.BugReport,
                                    contentDescription = reportIssue + " " + bundle.displayTitle,
                                    tooltip = reportIssue
                                )
                            }
                        }

                        SettingsDivider(fullWidth = true)

                        // Resolve prerelease state once
                        val currentUsePrerelease = bundle.usesPrerelease

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
                                rowModifier = Modifier.reportBounds(tourTargets?.onPrereleasePositioned)
                            )
                        }

                        // Experimental versions toggle - shown for any bundle type that has experimental app version targets.
                        // For remote bundles (prerelease supported) it additionally requires prereleases to be ON.
                        AnimatedVisibility(
                            visible = actions.onExperimentalVersionsToggle != null &&
                                    (actions.onPrereleasesToggle == null || currentUsePrerelease),
                            enter = Animations.expandFadeEnter,
                            exit = Animations.shrinkFadeExit
                        ) {
                            ToggleRow(
                                title = stringResource(R.string.sources_management_experimental_versions_toggle),
                                description = stringResource(R.string.sources_management_experimental_versions_toggle_description),
                                checked = info.useExperimentalVersions,
                                onCheckedChange = { actions.onExperimentalVersionsToggle?.invoke(it) },
                                showDivider = false
                            )
                        }

                        if (actions.onPrereleasesToggle != null || actions.onExperimentalVersionsToggle != null) {
                            SettingsDivider(fullWidth = true)
                        }

                        // Action bar
                        ActionPillRow(modifier = Modifier.padding(top = 4.dp)) {
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
                                    icon = if (bundle.enabled) Icons.Outlined.Block else Icons.Outlined.CheckCircle,
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
                                    icon = Icons.Outlined.Refresh,
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
                                    icon = Icons.Outlined.Edit,
                                    contentDescription = renameDesc,
                                    tooltip = renameVerb
                                )

                                // Delete button
                                ActionPillButton(
                                    onClick = actions.onDelete,
                                    icon = Icons.Outlined.Delete,
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

@Composable
private fun BundleCardHeader(
    bundle: PatchBundleSource,
    updateInfo: PatchBundleRepository.ManualBundleUpdateInfo?,
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            val versionText = if (showChevron) bundle.version?.removePrefix("v") else null
            val dateText = remember(timestamp) { timestamp?.let { getRelativeTimeString(it) } }

            if (versionText != null || dateText != null) {
                Row(
                    modifier = Modifier.graphicsLayer { alpha = detailsAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            imageVector = if (bundle.updatedAt != null) Icons.Outlined.Schedule else Icons.Outlined.CalendarToday,
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                        icon = Icons.Outlined.Search,
                        tone = SemanticTone.Primary,
                        onClick = onShowPatchMatches
                    )
                }

                // Bundle type badge, faded with the version line since it describes the source as well
                BundleTypeBadge(
                    type = bundle.sourceType,
                    modifier = Modifier.graphicsLayer { alpha = detailsAlpha }
                )

                SourceStateBadge(
                    visible = unavailable,
                    text = stringResource(R.string.sources_management_metadata_unavailable)
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
                    icon = Icons.Outlined.PowerSettingsNew
                )

                // Update badge
                if (updateInfo != null) {
                    StatusBadge(
                        text = stringResource(R.string.update),
                        tone = SemanticTone.Warning
                    )
                }
            }
        }

        // Chevron
        if (showChevron) {
            ExpandChevron(expanded = expanded)
        }
    }
}

/**
 * Header badge for a state a source can enter and leave while the sheet is open, easing in and out
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
private fun ColumnScope.CardNotice(
    visible: Boolean,
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.expandFadeEnter,
        exit = Animations.shrinkFadeExit
    ) {
        Notice(
            modifier = modifier,
            text = text,
            icon = icon,
            tone = SemanticTone.Error,
            density = NoticeDensity.Compact
        )
    }
}

/** Reports where this lands in the window to [onPositioned], for the tour to point at. */
private fun Modifier.reportBounds(onPositioned: ((Rect) -> Unit)?): Modifier =
    if (onPositioned == null) this else onGloballyPositioned { onPositioned(it.boundsInWindow()) }

/**
 * @param accentColor Color of the card the badge sits on, the surrounding one by default, see
 *   [LocalAccent]. Null for the neutral badge.
 */
@Composable
fun BundleTypeBadge(
    type: BundleSourceType,
    modifier: Modifier = Modifier,
    accentColor: Color? = LocalAccent.current
) {
    val text = when (type) {
        BundleSourceType.PreInstalled -> stringResource(R.string.sources_dialog_preinstalled)
        BundleSourceType.Remote -> stringResource(R.string.sources_dialog_remote)
        BundleSourceType.Local -> stringResource(R.string.sources_dialog_local)
    }
    AppAccentBadge(text = text, modifier = modifier, accentColor = accentColor)
}

/**
 * Color a source's own dialogs head themselves with: its icon's, or the theme's accent where the
 * icon has none. A source that is off keeps it, since its dialogs say so in words of their own.
 */
@Composable
internal fun rememberSourceHeaderColor(bundle: PatchBundleSource): Color =
    rememberBundleAccent(bundle) ?: MaterialTheme.colorScheme.primary

@Composable
fun BundleIcon(
    bundle: PatchBundleSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    metadataFetchError: Throwable? = null
) {
    val avatarUrls = bundle.avatarUrls
    val hasMetadataError = metadataFetchError != null
    val hasBundleError = bundle.state is PatchBundleSource.State.Failed
    val isMissing = bundle.state is PatchBundleSource.State.Missing

    val animatedColor by animateColorAsState(
        targetValue = when {
            bundle.isDefault -> Color.White
            hasBundleError -> MaterialTheme.colorScheme.errorContainer
            // Matches the icon below, which a missing source wears as well
            hasMetadataError || isMissing -> SemanticTone.Warning.container
            enabled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant.distinctFromCard()
        },
        label = "bundle_icon_color"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_SOURCE_ALPHA,
        label = "bundle_icon_alpha"
    )
    // Drained of color as well as faded, since a faded photo alone reads as murky rather than off
    val animatedSaturation by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        label = "bundle_icon_saturation"
    )

    Surface(
        modifier = modifier
            .graphicsLayer { alpha = animatedAlpha }
            .saturation(animatedSaturation),
        shape = CircleShape,
        color = animatedColor
    ) {
        when {
            bundle.isDefault -> MorpheLauncherLogo(modifier = Modifier.fillMaxSize())

            hasBundleError -> {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.bundleGlyph()
                )
            }

            hasMetadataError || isMissing -> {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = SemanticTone.Warning.content,
                    modifier = Modifier.bundleGlyph()
                )
            }

            avatarUrls.primary != null -> {
                RemoteAvatar(
                    url = avatarUrls.primary,
                    fallbackUrl = avatarUrls.fallback,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Outlined.Source,
                    contentDescription = null,
                    tint = if (enabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.bundleGlyph()
                )
            }
        }
    }
}

/**
 * Sizes a source's fallback glyph as a share of its disc, so the glyph keeps its proportions on a
 * small disc such as a tab's instead of a fixed inset leaving it a speck.
 */
private fun Modifier.bundleGlyph(): Modifier = wrapContentSize().fillMaxSize(BUNDLE_GLYPH_FRACTION)

/** Draws the content at [saturation], from 0 for grayscale to 1 for its own colors. */
private fun Modifier.saturation(saturation: Float): Modifier = drawWithCache {
    // Full color skips the offscreen layer, which every source that is on would otherwise pay for
    if (saturation >= 1f) return@drawWithCache onDrawWithContent { drawContent() }
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }
    onDrawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(size.toRect(), paint)
            drawContent()
            canvas.restore()
        }
    }
}
