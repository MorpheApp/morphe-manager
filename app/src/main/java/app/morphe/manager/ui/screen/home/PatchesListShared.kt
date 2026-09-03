/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.screen.shared.AppDialogTextField
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.EmptyState
import app.morphe.manager.ui.screen.shared.HeroInfoCard
import app.morphe.manager.ui.screen.shared.SemanticTone
import app.morphe.manager.ui.screen.shared.StatusBadge
import app.morphe.manager.ui.screen.shared.animatedListItem
import app.morphe.manager.util.toHsv

/**
 * Header card shown at the top of patches-list dialogs.
 */
@Composable
internal fun PatchesListHeaderCard(
    title: String,
    totalCount: Int,
    filteredCount: Int,
    isFiltering: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Extension
) {
    HeroInfoCard(
        icon = icon,
        title = title,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Outlined.Widgets,
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = Modifier.size(16.dp)
        )
        val patchCountLabel = pluralStringResource(
            R.plurals.patch_count,
            totalCount,
            totalCount.toString()
        )
        val countText = if (isFiltering) "$filteredCount/$patchCountLabel"
        else patchCountLabel
        AnimatedContent(
            targetState = countText,
            transitionSpec = Animations.counterTransitionSpec,
            label = "patches_count"
        ) { count ->
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Fill that an accent color takes on a patch card.
 *
 * The accents themselves are picked for contrast against each other, not for sitting behind
 * text, so only their hue survives: the rest is a fixed wash the card content stays readable on.
 */
@Composable
internal fun rememberAccentCardColor(accentColor: Color?): Color? =
    // The hue conversion is a native call that allocates, so it must not run per frame
    remember(accentColor) {
        if (accentColor == null) return@remember null
        Color.hsl(
            hue = accentColor.toHsv().first,
            saturation = 0.35f,
            lightness = 0.55f,
            alpha = 0.2f
        )
    }

/**
 * Collapsible section header for the universal patches of one bundle.
 *
 * A null [onToggle] drops the chevron and the click, for the cases where the section has
 * nothing left to fold away.
 *
 * [accentColor] is the color the bundle marks its own patches with, so the header stays part of
 * the block it opens when several bundles each contribute a universal section.
 *
 * [selectedCount] is badged on the header itself, since a folded section is the one place a
 * patch can be enabled without being visible.
 */
@Composable
internal fun UniversalPatchesHeader(
    count: Int,
    isExpanded: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    selectedCount: Int = 0
) {
    // One chevron that turns, so the fold reads as the same control in both states
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "universal_patches_chevron"
    )

    // Held while the badge fades out, so the count does not blink to zero on its way off
    val lastSelectedCount = remember { mutableIntStateOf(selectedCount) }
    if (selectedCount > 0) lastSelectedCount.intValue = selectedCount
    val shownSelectedCount = lastSelectedCount.intValue

    HomeGlassCategoryRow(
        title = stringResource(R.string.expert_mode_universal_patches),
        count = pluralStringResource(R.plurals.patch_count, count, count.toString()),
        onClick = onToggle,
        leading = {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailing = {
            // One slot for both, so the chevron holds its place as the badge comes and goes
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = selectedCount > 0,
                    enter = Animations.expandHorizFadeIn,
                    exit = Animations.shrinkHorizFadeOut
                ) {
                    val selectedLabel = pluralStringResource(
                        R.plurals.expert_mode_selected_count,
                        shownSelectedCount,
                        shownSelectedCount.toString()
                    )
                    StatusBadge(
                        text = shownSelectedCount.toString(),
                        icon = Icons.Outlined.Check,
                        tone = SemanticTone.Primary,
                        // The bare number is meaningless read out, and the row merges its children
                        modifier = Modifier
                            .padding(end = Defaults.ContentPaddingSmall)
                            .clearAndSetSemantics { contentDescription = selectedLabel }
                    )
                }
                AnimatedVisibility(
                    visible = onToggle != null,
                    enter = Animations.expandHorizFadeIn,
                    exit = Animations.shrinkHorizFadeOut
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.collapse else R.string.expand
                        ),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        cornerRadius = Defaults.SettingsCornerRadius,
        color = rememberAccentCardColor(accentColor),
        modifier = modifier
    )
}

/**
 * Rows of one bundle: the patches written for the app at hand first, then the universal ones
 * behind a collapsible header.
 *
 * Universal patches apply to every app and would otherwise bury the handful written for this one.
 * There is nothing worth folding away when they are the whole list, or when a filter already
 * narrows it, so [isFiltering] and an empty [specific] both keep the section open.
 *
 * [row] draws one patch and stays with the caller, since the lists differ in what a row carries
 * and in what it can be toggled into.
 */
internal fun <T> LazyListScope.patchSectionRows(
    sectionKey: Any,
    specific: List<T>,
    universal: List<T>,
    key: (T) -> Any,
    isFiltering: Boolean,
    isUniversalExpanded: Boolean,
    onUniversalExpandedChange: (Boolean) -> Unit,
    accentColor: Color? = null,
    universalSelectedCount: Int = 0,
    row: @Composable LazyItemScope.(T) -> Unit
) {
    items(specific, key = key, itemContent = row)

    if (universal.isEmpty()) return

    val alwaysOpen = isFiltering || specific.isEmpty()
    val isExpanded = alwaysOpen || isUniversalExpanded

    item(key = "universal_header_$sectionKey") {
        UniversalPatchesHeader(
            count = universal.size,
            isExpanded = isExpanded,
            onToggle = if (alwaysOpen) null else { { onUniversalExpandedChange(!isUniversalExpanded) } },
            accentColor = accentColor,
            selectedCount = universalSelectedCount,
            modifier = Modifier.animatedListItem(this)
        )
    }

    if (!isExpanded) return

    items(universal, key = key, itemContent = row)
}

/**
 * Search field + optional filter button row.
 */
@Composable
internal fun PatchesListSearchRow(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showFilterButton: Boolean,
    isFilterActive: Boolean,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AppDialogTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
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

            if (showFilterButton) {
                FilledTonalIconButton(
                    onClick = onFilterClick,
                    modifier = Modifier.padding(bottom = 4.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isFilterActive)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isFilterActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.filter),
                        modifier = Modifier.size(Defaults.IconSizeSmall)
                    )
                }
            }
        }
    }
}

/**
 * "No results" empty state used when search or filter yields no patches.
 */
@Composable
internal fun PatchesListEmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        message = stringResource(R.string.expert_mode_no_results),
        icon = Icons.Outlined.SearchOff,
        modifier = modifier
    )
}

/**
 * Which bundles have their universal section unfolded.
 *
 * The state belongs to the screen rather than to the section, since "enable all" has to open a
 * section on the tap that finally reaches its universal patches.
 */
@Stable
internal class UniversalSectionState {
    private var expandedUids by mutableStateOf(emptySet<Int>())

    operator fun contains(bundleUid: Int) = bundleUid in expandedUids

    fun setExpanded(bundleUid: Int, expanded: Boolean) {
        expandedUids = if (expanded) expandedUids + bundleUid else expandedUids - bundleUid
    }
}

@Composable
internal fun rememberUniversalSectionState() = remember { UniversalSectionState() }
