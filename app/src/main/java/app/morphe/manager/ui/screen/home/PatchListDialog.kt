/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.CompatiblePackage
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.*

/**
 * One block of a patch list dialog: the patches of one app, of one source, or the universal ones.
 *
 * @param packageName App the block lists its patches for, so their cards leave out what the block
 *   already says. Null for universal patches, which target no app.
 * @param accentColor Tints the block's header and cards, so blocks side by side tell apart.
 */
@Stable
internal class PatchListSection(
    val key: String,
    val title: String,
    val patches: List<PatchInfo>,
    val packageName: String?,
    val accentColor: Color? = null,
    val icon: @Composable (Modifier) -> Unit
)

/**
 * A patch list dialog: a header naming the app or source the list belongs to, a search, a filter
 * over [sections] when there are several, and the patches block by block.
 *
 * @param subtitle What the header sums the list up as.
 * @param accentColor Brand color of the app the list belongs to, when its source declares one.
 * @param notice Shown above the list, for what the user should know before reading it.
 * @param onExpertBadgeClick Marks the patches left out by default, and explains the mark.
 */
@Composable
internal fun PatchListDialog(
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    sections: List<PatchListSection>,
    isLoading: Boolean,
    saveStateKey: String,
    onDismiss: () -> Unit,
    initialQuery: String = "",
    accentColor: Color? = null,
    notice: (@Composable () -> Unit)? = null,
    onExpertBadgeClick: (() -> Unit)? = null
) {
    val search = rememberSearchFieldState()
    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) {
            search.toggle()
            search.query = initialQuery
        }
    }

    var selectedKey by remember { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    // A block the list no longer holds cannot stay picked, or the filter would hide everything
    val selected = selectedKey?.takeIf { key -> sections.any { it.key == key } }
    var foldedKeys by remember { mutableStateOf(emptySet<String>()) }
    val patchSections = rememberPatchSectionState()
    val patchFolds = patchSections.folds

    val allPatches = remember(sections) { sections.flatMap { it.patches }.distinct() }
    val matchesPatch = rememberPatchMatcher(search.query, allPatches)
    val groupingOptions = rememberPatchGroupingOptions()

    // Each block with what the search and the filter leave of it, grouped the way the bundle asks.
    // A bundle may declare several patches under one name and compatibility, so rows are keyed by
    // their place in the block rather than by anything derived from the patch
    val shownSections = remember(sections, matchesPatch, selected, groupingOptions) {
        sections
            .filter { selected == null || it.key == selected }
            .mapNotNull { section ->
                val kept = section.patches.withIndex().filter { (_, patch) -> matchesPatch(patch) }
                when {
                    kept.isEmpty() -> null
                    // A block of universal patches already is the universal tail, which grouping would
                    // only fold away behind a second header of the same name
                    section.packageName == null ->
                        section to listOf(PatchGroup(key = section.key, title = null, items = kept))
                    else -> section to buildPatchGroups(kept, groupingOptions, infoOf = { it.value })
                }
            }
    }
    // Read from the whole block, so a search does not change the versions it names
    val commonTargets = remember(sections) {
        sections.associate { it.key to commonTargetOf(it.patches, it.packageName) }
    }
    // A list about one app whose blocks all target the same versions names them once, in its header
    val headerTarget = remember(sections, commonTargets) {
        if (sections.map { it.packageName }.distinct().singleOrNull() == null) return@remember null
        sections.mapNotNull { commonTargets[it.key] }.distinctBy { it.versions }.singleOrNull()
    }
    val isFiltering = search.isFiltering || selected != null
    val searchLabel = stringResource(R.string.expert_mode_search)
    val hasSections = sections.size > 1

    AppDialog(
        onDismissRequest = onDismiss,
        title = null,
        footer = { PatchListFooter(onClose = onDismiss) },
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
        hideFooterWhileTyping = true
    ) {
        // Back closes the search, then the filter, before the dialog itself. The last handler
        // registered is the first one asked, so they go in reverse
        BackHandler(enabled = selected != null) { selectedKey = null }
        SearchFieldBackHandler(search)

        Column(modifier = Modifier.fillMaxSize()) {
            ListDialogHeader(
                icon = icon,
                title = title,
                subtitle = subtitle,
                subtitleLoading = isLoading,
                search = search,
                searchLabel = searchLabel,
                accentColor = accentColor,
                badges = if (headerTarget == null || isLoading) null else {
                    {
                        var expanded by rememberSaveable(saveStateKey, "header_versions") {
                            mutableStateOf(false)
                        }
                        VersionBadges(
                            versions = headerTarget.versions.orEmpty().toList(),
                            experimental = headerTarget.experimentalVersions.orEmpty(),
                            expanded = expanded,
                            onToggle = { expanded = !expanded },
                            accentColor = accentColor
                        )
                    }
                }
            ) {
                // A source can patch dozens of apps, more than a row of chips could hold
                if (hasSections) {
                    TitleAction(
                        icon = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.filter),
                        onClick = { showFilterSheet = true },
                        style = TitleActionStyle.Toggle,
                        active = selected != null
                    )
                }
            }

            // A list read from the database arrives a frame or two late, and an empty list reads
            // as "nothing to patch" until it does
            AnimatedContent(
                targetState = isLoading,
                transitionSpec = Animations.fadeCrossfade(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "patchListLoading"
            ) { loading ->
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PulsingLogoIndicator()
                    }
                    return@AnimatedContent
                }

                val listState = rememberLazyListState()
                // The list otherwise holds on to whichever row led it, which another block leaves
                // somewhere in the middle, so picking one starts the list over from its top
                LaunchedEffect(selected) {
                    listState.scrollToItem(0)
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
                    ) {
                        // A row of the list rather than a field above it, so the rows below ease into
                        // place as it comes and goes. The row stays while the field is closed, and its
                        // share of the spacing makes the gap under the header
                        stickyHeader(key = "search") {
                            AppDialogSearchHeader(
                                visible = search.visible,
                                value = search.query,
                                onValueChange = { search.query = it },
                                label = searchLabel,
                                // Opaque, so rows scrolled under the gap stay hidden
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(top = Defaults.ItemSpacing)
                            )
                        }

                        if (notice != null) item(key = "notice") { notice() }

                        if (shownSections.isEmpty()) {
                            item(key = "empty_state") {
                                PatchesListEmptyState(modifier = Modifier.animatedListItem(this))
                            }
                        }

                        shownSections.forEach { (section, groups) ->
                            // A filter narrows the list far enough that a fold would only hide results
                            val isFolded = !isFiltering && section.key in foldedKeys

                            if (hasSections) {
                                item(key = "section_${section.key}") {
                                    PatchGroupHeader(
                                        title = section.title,
                                        count = groups.sumOf { it.items.size },
                                        isExpanded = !isFolded,
                                        onToggle = if (isFiltering) null else ({
                                            foldedKeys = if (isFolded) foldedKeys - section.key
                                            else foldedKeys + section.key
                                        }),
                                        leading = { section.icon(Modifier.size(24.dp)) },
                                        accentColor = section.accentColor,
                                        modifier = Modifier.animatedListItem(this)
                                    )
                                }
                            }

                            val target = commonTargets[section.key]
                            val targetVersions = target?.versions.orEmpty().toList()
                            // Named once for the block under its header, unless the list's header names them
                            if (!isFolded && headerTarget == null && targetVersions.isNotEmpty()) {
                                item(key = "versions_${section.key}") {
                                    var expanded by rememberSaveable(saveStateKey, section.key) {
                                        mutableStateOf(false)
                                    }
                                    FlowRow(
                                        modifier = Modifier.animatedListItem(this),
                                        horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                                        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                                    ) {
                                        VersionBadges(
                                            versions = targetVersions,
                                            experimental = target?.experimentalVersions.orEmpty(),
                                            expanded = expanded,
                                            onToggle = { expanded = !expanded },
                                            accentColor = section.accentColor
                                        )
                                    }
                                }
                            }

                            if (!isFolded) {
                                patchGroupRows(
                                    sectionKey = section.key,
                                    groups = groups,
                                    key = { (index, _): IndexedValue<PatchInfo> -> "${section.key}:$index" },
                                    isFiltering = isFiltering,
                                    folds = patchFolds,
                                    onToggle = { group -> patchSections.toggle(section.key, group) },
                                    accentColor = section.accentColor
                                ) { (_, patch) ->
                                    PatchItemCard(
                                        patch = patch,
                                        saveStateKey = "$saveStateKey:${section.key}",
                                        packageName = section.packageName,
                                        commonVersions = commonTargets[section.key]?.versions,
                                        onExpertBadgeClick = onExpertBadgeClick,
                                        accentColor = section.accentColor,
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

    TranslationOverlays()

    if (showFilterSheet) {
        SectionFilterSheet(
            sections = sections,
            selectedKey = selected,
            onSelect = { key ->
                selectedKey = key
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

/**
 * Footer of a patch list dialog: translating the descriptions beside the close button, where the
 * app language has a translation to offer.
 */
@Composable
private fun PatchListFooter(onClose: () -> Unit) {
    AppDialogActions(
        // A row lays its actions out from the right, so the close button ends up there
        actions = listOfNotNull(
            DialogAction(
                text = stringResource(R.string.close),
                onClick = onClose,
                emphasis = DialogActionEmphasis.Outlined
            ),
            translateAction()
        )
    )
}

/** Sheet picking one block of the list to show alone, in the order the list has them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionFilterSheet(
    sections: List<PatchListSection>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            PanelHeader(title = { PanelTitle(text = stringResource(R.string.filter)) })
            FlowRow(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = Defaults.ContentPadding, end = Defaults.ContentPadding, bottom = Defaults.ContentPadding),
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
            ) {
                AppFilterChip(
                    selected = selectedKey == null,
                    onClick = { onSelect(null) },
                    label = stringResource(R.string.all),
                    selectedIcon = Icons.Outlined.DoneAll
                )
                sections.forEach { section ->
                    val isSelected = section.key == selectedKey
                    AppFilterChip(
                        selected = isSelected,
                        onClick = { onSelect(if (isSelected) null else section.key) },
                        label = section.title
                    )
                }
            }
        }
    }
}

/**
 * One patch of a patch list: its name, description and what sets it apart from the rest of its
 * block. Versions show only where they differ from [commonVersions], which the block states once,
 * and the options fold out from a badge naming how many there are.
 *
 * @param packageName App the block lists the patch under, whose versions the card compares.
 */
@Composable
internal fun PatchItemCard(
    patch: PatchInfo,
    saveStateKey: String,
    packageName: String?,
    commonVersions: Set<String>?,
    modifier: Modifier = Modifier,
    onExpertBadgeClick: (() -> Unit)? = null,
    accentColor: Color? = null
) {
    val textColor = LocalDialogTextColor.current
    val secondaryColor = LocalDialogSecondaryTextColor.current

    var expandVersions by rememberSaveable(saveStateKey, patch.name, "versions") {
        mutableStateOf(false)
    }
    var expandOptions by rememberSaveable(saveStateKey, patch.name, "options") {
        mutableStateOf(false)
    }

    val options = patch.options.orEmpty()
    val compatible = patch.compatiblePackages?.firstOrNull { it.packageName == packageName }
    // A universal patch has none, and most others share the versions their block names above them
    val versions = compatible?.versions.orEmpty().takeIf { it != commonVersions }.orEmpty().toList()
    val isExpertOnly = !patch.include && onExpertBadgeClick != null

    val cardColor = rememberAccentCardColor(accentColor)
    val effectiveCardColor = cardColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    // Card colors come from the app's own icon, so no fixed badge fill can be counted on to show
    val cardBackground = effectiveCardColor.compositeOver(MaterialTheme.colorScheme.background)

    SettingsItemCard(
        onClick = if (options.isNotEmpty()) {
            { expandOptions = !expandOptions }
        } else null,
        modifier = modifier,
        borderWidth = 1.dp,
        borderColor = appAccentBorder(accentColor),
        color = effectiveCardColor
    ) {
        CompositionLocalProvider(LocalCardBackground provides cardBackground) {
            Column(
                modifier = Modifier.padding(Defaults.ContentPadding),
                verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
            ) {
                PatchCardText(name = patch.displayName, description = patch.description)

                if (versions.isNotEmpty() || options.isNotEmpty() || isExpertOnly) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                    ) {
                        VersionBadges(
                            versions = versions,
                            experimental = compatible?.experimentalVersions.orEmpty(),
                            expanded = expandVersions,
                            onToggle = { expandVersions = !expandVersions }
                        )

                        if (options.isNotEmpty()) {
                            StatusBadge(
                                text = pluralStringResource(R.plurals.option_count, options.size, options.size.toString()),
                                icon = if (expandOptions) Icons.Outlined.ExpandLess else Icons.Outlined.Tune,
                                tone = SemanticTone.Primary,
                                onClick = { expandOptions = !expandOptions }
                            )
                        }

                        // Shown only for patches that are disabled by default
                        if (isExpertOnly) {
                            StatusBadge(
                                text = stringResource(R.string.sources_patch_expert_badge),
                                icon = Icons.Outlined.Lock,
                                tone = SemanticTone.Warning,
                                onClick = onExpertBadgeClick
                            )
                        }
                    }
                }

                if (options.isNotEmpty()) {
                    AnimatedVisibility(
                        visible = expandOptions,
                        enter = Animations.expandFadeEnter,
                        exit = Animations.shrinkFadeExit
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)) {
                            options.forEach { option ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(Defaults.CompactCornerRadius),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Defaults.ItemSpacing),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = option.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textColor
                                        )
                                        Text(
                                            text = rememberTranslated(option.description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryColor
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
}

/**
 * The versions an app is patched at: the first, then the rest behind a badge that folds them out,
 * one after another. Emitted into the caller's row, so a card keeps its other badges beside them.
 *
 * @param accentColor Color of the header or block the badges belong to, see [PatchVersionBadge].
 */
@Composable
private fun VersionBadges(
    versions: List<String>,
    experimental: Set<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    accentColor: Color? = null
) {
    versions.forEachIndexed { index, version ->
        // The first version stands from the start, and only the ones folding out animate
        FoldingBadge(visible = index == 0 || expanded, order = index - 1) {
            PatchVersionBadge(version = version, isExperimental = version in experimental, accentColor = accentColor)
        }
    }
    if (versions.size > 1) {
        AppAccentBadge(
            text = if (expanded) stringResource(R.string.less) else "+${versions.size - 1}",
            accentColor = accentColor,
            onClick = onToggle
        )
    }
}

/**
 * A badge that folds out [order] steps after the first of a run. Shown as it stands on first
 * composition, so opening a list plays nothing.
 */
@Composable
private fun FoldingBadge(
    visible: Boolean,
    order: Int,
    content: @Composable () -> Unit
) {
    val state = remember { MutableTransitionState(visible) }
    state.targetState = visible

    val enter = remember(order) { Animations.chipEnterStaggered(order) }
    // Gone at once: a badge fading out would hold its place, and the toggle after the run would
    // sit at the end of the row until it let go, then jump back to the first one
    AnimatedVisibility(visibleState = state, enter = enter, exit = ExitTransition.None) {
        content()
    }
}

/**
 * One version a patch declares support for, tagged the way every version list tags it.
 *
 * @param accentColor Color of the header or block the badge belongs to, which a plain version takes
 *   on as the app details' chips do. An experimental one keeps its warning.
 */
@Composable
private fun PatchVersionBadge(
    version: String,
    isExperimental: Boolean,
    accentColor: Color? = null
) {
    AppAccentBadge(
        text = version,
        accentColor = accentColor.takeUnless { isExperimental },
        icon = if (isExperimental) VersionTag.Experimental.icon else Icons.Outlined.Code,
        tone = if (isExperimental) VersionTag.Experimental.tone else SemanticTone.Neutral
    )
}

/**
 * What most of [patches] target on [packageName]: the compatibility entry they share, or null
 * where they name no versions, as universal patches do.
 */
private fun commonTargetOf(patches: List<PatchInfo>, packageName: String?): CompatiblePackage? {
    if (packageName == null) return null
    return patches
        .mapNotNull { patch -> patch.compatiblePackages?.firstOrNull { it.packageName == packageName } }
        .filter { !it.versions.isNullOrEmpty() }
        .groupBy { it.versions }
        .maxByOrNull { (_, entries) -> entries.size }
        ?.value
        ?.first()
}
