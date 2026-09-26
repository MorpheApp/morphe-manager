/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

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
import androidx.compose.runtime.Immutable
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
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.toHsv
import org.koin.compose.koinInject

/**
 * Matches patches against [query] by name, description and, while translation is on, translated
 * description. The descriptions of [patches] translate in the background, so the search finds them
 * without waiting for a scroll.
 */
@Composable
internal fun rememberPatchMatcher(query: String, patches: List<PatchInfo>): (PatchInfo) -> Boolean {
    val translation: ContentTranslation = koinInject()
    val descriptions = remember(patches) { patches.mapNotNull { it.description }.distinct() }
    PrefetchTranslations(descriptions)

    // Read only while searching, so translations landing in the background leave the list alone
    val revision = if (query.isBlank()) 0 else translation.revision
    return remember(query, translation.isEnabled, revision) {
        { patch ->
            patch.matchesQuery(query) ||
                    patch.description?.let(translation::cached)?.contains(query, ignoreCase = true) == true
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
 * One collapsible block of a patch list.
 *
 * A null [title] is the ungrouped remainder: it carries no header and is always drawn, which is
 * how a bundle that declares no categories keeps the plain list it has today. [key] is what the
 * fold state is stored under, so a block holds its fold while a search reorders the surrounding list.
 */
@Immutable
internal data class PatchGroup<T>(
    val key: String,
    val title: String?,
    val items: List<T>,
    val icon: ImageVector = Icons.Outlined.Category,
    /** Enabled patches, the ones a folded block would otherwise hide. */
    val selectedCount: Int = 0,
    val defaultExpanded: Boolean = true
)

/** Fold key of the universal block, for the callers that have to open it from the outside. */
internal const val UNIVERSAL_GROUP_KEY = "universal"

private const val UNGROUPED_GROUP_KEY = "ungrouped"

/**
 * Splits [patches] into the blocks a list is drawn as: whatever the bundle left uncategorized,
 * then one block per declared category, then the universal patches it did not categorize.
 *
 * A category is what the bundle asked for, so it wins over the universal split: a universal patch
 * that declares one joins that block rather than the tail. Bundles written entirely against
 * universal targets are the ones with the most patches to sort through, and folding all of them
 * into a single tail would leave them exactly as unsorted as before.
 *
 * The tail keeps the universal patches with no category of their own. Those apply to every app and
 * would otherwise bury the handful written for this one, so they stay last and folded. Categories
 * start out open instead, since folding a block that hides an enabled patch is only worth it for
 * the one the user is least likely to have picked from. A bundle that declares no categories at
 * all comes out as the plain list with only that tail split off.
 *
 * Order within a block is the order [patches] came in, so callers keep the sorting they want.
 */
internal fun <T> buildPatchGroups(
    patches: List<T>,
    options: PatchGroupingOptions,
    infoOf: (T) -> PatchInfo,
    isEnabled: (T) -> Boolean = { false }
): List<PatchGroup<T>> {
    val byCategory = patches.groupBy { infoOf(it).category }
    val (universal, ungrouped) = byCategory[null].orEmpty().partition { infoOf(it).isUniversal }

    return buildList {
        if (ungrouped.isNotEmpty()) {
            add(PatchGroup(key = UNGROUPED_GROUP_KEY, title = null, items = ungrouped))
        }

        byCategory.keys.filterNotNull().sortedBy { it.lowercase() }.forEach { category ->
            val items = byCategory.getValue(category)
            add(
                PatchGroup(
                    key = "category:$category",
                    title = category,
                    items = items,
                    selectedCount = items.count(isEnabled)
                )
            )
        }

        if (universal.isNotEmpty()) {
            add(
                PatchGroup(
                    key = UNIVERSAL_GROUP_KEY,
                    title = options.universalTitle,
                    items = universal,
                    icon = Icons.Outlined.Public,
                    selectedCount = universal.count(isEnabled),
                    defaultExpanded = false
                )
            )
        }
    }
}

/**
 * Everything [buildPatchGroups] needs beyond the patches themselves, read once per list rather
 * than per bundle, so a screen that groups several lists does not call into composition per item.
 */
@Immutable
internal data class PatchGroupingOptions(
    val universalTitle: String
)

@Composable
internal fun rememberPatchGroupingOptions(): PatchGroupingOptions {
    val universalTitle = stringResource(R.string.expert_mode_universal_patches)

    return remember(universalTitle) { PatchGroupingOptions(universalTitle) }
}

/**
 * The blocks [patches] is drawn as.
 *
 * Categories are whatever the bundle declares, so a bundle that declares none ends up with the
 * plain list plus its universal tail.
 */
@Composable
internal fun <T> rememberPatchGroups(
    patches: List<T>,
    infoOf: (T) -> PatchInfo,
    isEnabled: (T) -> Boolean = { false }
): List<PatchGroup<T>> {
    val options = rememberPatchGroupingOptions()

    return remember(patches, options) {
        buildPatchGroups(patches, options, infoOf, isEnabled)
    }
}

/**
 * Collapsible header of one block of a patch list.
 *
 * A null [onToggle] drops the chevron and the click, for the cases where the block has nothing
 * left to fold away.
 *
 * [accentColor] is the color the bundle marks its own patches with, so the header stays part of
 * the block it opens when several bundles each contribute one.
 *
 * [selectedCount] is badged on the header itself, since a folded block is the one place a patch
 * can be enabled without being visible.
 *
 * @param leading Drawn in place of [icon], for a block that stands for an app or a source.
 */
@Composable
internal fun PatchGroupHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Category,
    leading: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    selectedCount: Int = 0
) {
    // One chevron that turns, so the fold reads as the same control in both states
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "patch_group_chevron"
    )

    // Held while the badge fades out, so the count does not blink to zero on its way off
    val lastSelectedCount = remember { mutableIntStateOf(selectedCount) }
    if (selectedCount > 0) lastSelectedCount.intValue = selectedCount
    val shownSelectedCount = lastSelectedCount.intValue

    HomeGlassCategoryRow(
        title = title,
        count = pluralStringResource(R.plurals.patch_count, count, count.toString()),
        onClick = onToggle,
        leading = leading ?: {
            Icon(
                imageVector = icon,
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
        borderColor = accentColor?.let { appAccentBorder(it) },
        modifier = modifier
    )
}

/**
 * Rows of one patch list, block by block, each behind a collapsible header of its own.
 *
 * There is nothing worth folding away when the list is a single block, and a filter already
 * narrows it far enough that a fold would only hide results, so both keep every block open.
 *
 * [row] draws one patch and stays with the caller, since the lists differ in what a row carries
 * and in what it can be toggled into.
 *
 * [folds] is a plain snapshot rather than the state holder itself: this builder runs while the
 * lazy list assembles its items, so a fold has to reach it as a value the screen already read.
 */
internal fun <T> LazyListScope.patchGroupRows(
    sectionKey: Any,
    groups: List<PatchGroup<T>>,
    key: (T) -> Any,
    isFiltering: Boolean,
    folds: PatchFolds,
    onToggle: (PatchGroup<T>) -> Unit,
    accentColor: Color? = null,
    row: @Composable LazyItemScope.(T) -> Unit
) {
    val alwaysOpen = isFiltering || groups.size == 1

    groups.forEach { group ->
        if (group.title == null) {
            items(group.items, key = key, itemContent = row)
            return@forEach
        }

        val isExpanded = alwaysOpen || folds.isExpanded(sectionKey, group)

        item(key = "group_${sectionKey}_${group.key}") {
            PatchGroupHeader(
                title = group.title,
                count = group.items.size,
                isExpanded = isExpanded,
                onToggle = if (alwaysOpen) null else ({ onToggle(group) }),
                icon = group.icon,
                // Universal patches are for no app in particular, so they do not wear its color
                accentColor = accentColor.takeUnless { group.key == UNIVERSAL_GROUP_KEY },
                selectedCount = group.selectedCount,
                modifier = Modifier.animatedListItem(this)
            )
        }

        if (isExpanded) items(group.items, key = key, itemContent = row)
    }
}

/**
 * Name and description of a patch, the part every patch card shares, so a patch reads the same in
 * each list it shows up in. [badges] follow the name on its line.
 *
 * @param dimmed For a patch that is off, which fades back behind the ones that are on.
 */
@Composable
internal fun PatchCardText(
    name: String,
    description: String?,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {}
) {
    val secondaryColor = LocalDialogSecondaryTextColor.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (dimmed) secondaryColor.copy(alpha = 0.5f) else LocalDialogTextColor.current,
                modifier = Modifier.weight(1f, fill = false)
            )
            badges()
        }

        if (!description.isNullOrBlank()) {
            Text(
                text = rememberTranslated(description),
                style = MaterialTheme.typography.bodySmall,
                color = if (dimmed) secondaryColor.copy(alpha = 0.4f) else secondaryColor
            )
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
 * The folds of a patch list, as a value a screen can read and hand to the list builder.
 *
 * Only explicit toggles are stored, so a block the user never touched follows its own default.
 */
@Immutable
internal data class PatchFolds(private val overrides: Map<String, Boolean>) {
    fun isExpanded(sectionKey: Any, group: PatchGroup<*>) =
        overrides[foldKey(sectionKey, group.key)] ?: group.defaultExpanded

    internal fun with(sectionKey: Any, groupKey: String, expanded: Boolean) =
        PatchFolds(overrides + (foldKey(sectionKey, groupKey) to expanded))
}

private fun foldKey(sectionKey: Any, groupKey: String) = "$sectionKey:$groupKey"

/**
 * Which blocks of a patch list the user has folded open or shut.
 *
 * The state belongs to the screen rather than to the block, since "enable all" has to open a
 * block on the tap that finally reaches its universal patches. Screens read [folds] in
 * composition, so a toggle rebuilds the list the ordinary way instead of relying on the lazy
 * list to observe a read made while it was assembling its items.
 */
@Stable
internal class PatchSectionState {
    var folds by mutableStateOf(PatchFolds(emptyMap()))
        private set

    fun toggle(sectionKey: Any, group: PatchGroup<*>) {
        setExpanded(sectionKey, group.key, !folds.isExpanded(sectionKey, group))
    }

    fun setExpanded(sectionKey: Any, groupKey: String, expanded: Boolean) {
        folds = folds.with(sectionKey, groupKey, expanded)
    }
}

@Composable
internal fun rememberPatchSectionState() = remember { PatchSectionState() }
