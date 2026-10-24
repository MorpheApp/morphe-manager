/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategoryState
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeAppSourceGroup
import app.morphe.manager.util.RemoteAvatar
import app.morphe.manager.util.rememberSourceAccent
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

internal const val SOURCE_CATEGORY_ID_PREFIX = "source_"

/**
 * UI-side group backing one collapsible section in the home list. Represents either a
 * user-defined category (has [id], [editable] true, no source fields), a source group
 * (has [sourceUid] and avatar fields, [editable] false), or the uncategorized bucket
 * ([id] null, [collapsible] false).
 */
internal data class HomeCategoryGroup(
    val id: String?,
    val sourceUid: Int? = null,
    val sourceAvatarUrl: String? = null,
    val sourceFallbackAvatarUrl: String? = null,
    val sourceIsDefault: Boolean = false,
    val title: String,
    val items: List<HomeAppItem>,
    val collapsed: Boolean,
    val collapsible: Boolean,
    val editable: Boolean
)

/**
 * Bucket [items] into the user's custom categories plus an uncategorized tail.
 * [ignoreCollapsed] is used by the search and filter flows to force every group open so matches
 * are visible; when true, empty categories are also dropped from the result.
 */
internal fun buildHomeCategoryGroups(
    items: List<HomeAppItem>,
    categoryState: HomeAppCategoryState,
    uncategorizedTitle: String,
    ignoreCollapsed: Boolean
): List<HomeCategoryGroup> {
    val assigned = items.groupBy { item -> categoryState.assignments[item.id] }
    val groups = categoryState.categories.mapNotNull { category ->
        val categoryItems = assigned[category.id].orEmpty()
        if (categoryItems.isEmpty() && ignoreCollapsed) return@mapNotNull null
        HomeCategoryGroup(
            id = category.id,
            title = category.name,
            items = categoryItems,
            collapsed = !ignoreCollapsed && category.collapsed,
            collapsible = true,
            editable = true
        )
    }

    val knownCategoryIds = categoryState.categories.mapTo(mutableSetOf()) { it.id }
    val uncategorizedItems = items.filter { item ->
        categoryState.assignments[item.id] !in knownCategoryIds
    }
    val uncategorizedGroup = uncategorizedItems.takeIf { it.isNotEmpty() }?.let {
        HomeCategoryGroup(
            id = null,
            title = uncategorizedTitle,
            items = it,
            collapsed = !ignoreCollapsed && categoryState.uncategorizedCollapsed,
            collapsible = true,
            editable = false
        )
    }

    return groups + listOfNotNull(uncategorizedGroup)
}

/**
 * Bucket [items] into their owning source groups plus an uncategorized tail for apps
 * no source claimed. If the same package is declared by multiple sources, it appears in
 * each matching source group. [ignoreCollapsed] behaves as in [buildHomeCategoryGroups].
 */
internal fun buildHomeSourceGroups(
    items: List<HomeAppItem>,
    sourceGroups: List<HomeAppSourceGroup>,
    uncategorizedTitle: String,
    uncategorizedCollapsed: Boolean,
    ignoreCollapsed: Boolean
): List<HomeCategoryGroup> {
    // Build a package → owning source(s) index once, then iterate items once
    val sourcesByPackage = HashMap<String, MutableList<HomeAppSourceGroup>>()
    sourceGroups.forEach { sourceGroup ->
        sourceGroup.packageNames.forEach { pkg ->
            sourcesByPackage.getOrPut(pkg) { ArrayList(2) }.add(sourceGroup)
        }
    }
    val itemsPerSource = HashMap<Int, MutableList<HomeAppItem>>(sourceGroups.size)
    // Cards, not packages: every clone of an app belongs to the source that declares the app
    val claimedCards = HashSet<String>(items.size)
    items.forEach { item ->
        val owners = sourcesByPackage[item.packageName] ?: return@forEach
        claimedCards.add(item.id)
        owners.forEach { sourceGroup ->
            itemsPerSource.getOrPut(sourceGroup.uid) { ArrayList() }.add(item)
        }
    }

    val groups = sourceGroups.mapNotNull { sourceGroup ->
        val sourceItems = itemsPerSource[sourceGroup.uid]?.orderedByPackageOrder(sourceGroup.packageOrder)
        if (sourceItems.isNullOrEmpty()) {
            null
        } else {
            HomeCategoryGroup(
                id = "$SOURCE_CATEGORY_ID_PREFIX${sourceGroup.uid}",
                sourceUid = sourceGroup.uid,
                sourceAvatarUrl = sourceGroup.avatarUrl,
                sourceFallbackAvatarUrl = sourceGroup.fallbackAvatarUrl,
                sourceIsDefault = sourceGroup.isDefault,
                title = sourceGroup.name,
                items = sourceItems,
                collapsed = !ignoreCollapsed && sourceGroup.collapsed,
                collapsible = sourceGroup.collapsible,
                editable = false
            )
        }
    }

    val uncategorizedItems = items.filter { item -> item.id !in claimedCards }
    val uncategorizedGroup = uncategorizedItems.takeIf { it.isNotEmpty() }?.let {
        HomeCategoryGroup(
            id = null,
            title = uncategorizedTitle,
            items = it,
            collapsed = !ignoreCollapsed && uncategorizedCollapsed,
            collapsible = true,
            editable = false
        )
    }

    return groups + listOfNotNull(uncategorizedGroup)
}

private fun List<HomeAppItem>.orderedByPackageOrder(packageOrder: List<String>): List<HomeAppItem> {
    if (packageOrder.isEmpty()) return this

    val orderIndex = packageOrder.mapIndexed { index, pkg -> pkg to index }.toMap()
    return sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
}

/**
 * Frosted-glass row primitive shared by [HomeCategoryHeader] and the hidden-apps button.
 *
 * When both [onClick] and [onLongClick] are non-null a `combinedClickable` is wired; when
 * only [onLongClick] is present, [onClick] falls back to a no-op so long-press can still fire
 * on a non-tappable row.
 *
 * A non-null [color] replaces the frosted fill, for rows that belong to something already
 * carrying a color of its own. [below] goes under the title, in its column, as a list item's
 * supporting line does. A [shape] given outright, as a segment of a block takes from its place,
 * is taken over [cornerRadius] and eased into as it changes.
 */
@Composable
internal fun HomeGlassCategoryRow(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    count: String? = null,
    cornerRadius: Dp = Defaults.CornerLargeIncreased,
    shape: CornerBasedShape? = null,
    color: Color? = null,
    borderColor: Color? = null,
    below: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val shape = shape?.let { animateCornersAsState(it) } ?: RoundedCornerShape(cornerRadius)
    val containerColor = color ?: GlassButtonDefaults.containerColor()
    val borderColor = borderColor ?: GlassButtonDefaults.borderColor()
    val contentColor = MaterialTheme.colorScheme.onSurface
    val mutedContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    val interactionModifier = when {
        onLongClick != null -> Modifier.shapedCombinedClickable(
            shape = shape,
            onLongClick = onLongClick,
            onClick = onClick ?: {}
        )
        onClick != null -> Modifier.shapedClickable(shape, onClick = onClick)
        else -> Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(interactionModifier),
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = CardBorder.of(borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                // A supporting line makes it a two-line item, which keeps more room around it
                .padding(
                    // Inset as the cards it heads, so a block keeps one edge
                    horizontal = Defaults.SpaceLarge,
                    vertical = if (below != null) Defaults.SpaceMedium else Defaults.SpaceSmall
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
        ) {
            leading?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (count != null) {
                        Text(
                            text = count,
                            style = MaterialTheme.typography.labelMedium,
                            color = mutedContentColor,
                            maxLines = 1
                        )
                    }
                }
                below?.invoke()
            }
            trailing()
        }
    }
}

/**
 * Row that titles one collapsible section in the home list.
 */
@Composable
internal fun HomeCategoryHeader(
    group: HomeCategoryGroup,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null
) {
    val countText = pluralStringResource(
        R.plurals.home_category_app_count,
        group.items.size,
        group.items.size.toString()
    )
    val mutedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isSourceGroup = group.sourceUid != null
    val leadingIcon = if (group.collapsed) MaterialSymbols.Rounded.Folder else MaterialSymbols.Rounded.Folder_open
    // Surface pending updates on the header so collapsed groups still hint at work to do
    val hasPendingUpdate = group.items.any { it.showsRebuildBadge }
    val folderTint = if (hasPendingUpdate) MaterialTheme.colorScheme.primary else mutedContentColor
    // A source's group takes on the color of its icon, as the source's own dialogs do
    val sourceAccent = rememberSourceAccent(group.sourceIsDefault, group.sourceAvatarUrl, group.sourceFallbackAvatarUrl)

    HomeGlassCategoryRow(
        title = group.title,
        count = countText,
        onClick = if (group.collapsible) onToggle else null,
        onLongClick = onLongPress,
        leading = {
            if (isSourceGroup) {
                SourceCategoryIcon(group = group, modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = folderTint
                )
            }
        },
        trailing = {
            dragHandle?.invoke()
            if (group.collapsible) {
                Icon(
                    imageVector = if (group.collapsed) MaterialSymbols.Rounded.Expand_more else MaterialSymbols.Rounded.Expand_less,
                    contentDescription = if (group.collapsed)
                        stringResource(R.string.expand)
                    else
                        stringResource(R.string.collapse),
                    modifier = Modifier.size(24.dp),
                    tint = mutedContentColor
                )
            }
        },
        color = appAccentCardFill(sourceAccent),
        borderColor = sourceAccent?.let { appAccentBorder(it) },
        modifier = modifier
    )
}

/**
 * Circular avatar for a source-group header. The default (Morphe) source gets the app
 * launcher icon; other sources use the remote avatar from the bundle metadata, with a
 * neutral placeholder when no URL is available.
 */
@Composable
private fun SourceCategoryIcon(
    group: HomeCategoryGroup,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (group.sourceIsDefault)
            Color.White
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        when {
            group.sourceIsDefault -> MorpheLauncherLogo(modifier = Modifier.fillMaxSize())

            group.sourceAvatarUrl != null -> {
                RemoteAvatar(
                    url = group.sourceAvatarUrl,
                    fallbackUrl = group.sourceFallbackAvatarUrl,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Folder_code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Defaults.SpaceExtraSmall)
                )
            }
        }
    }
}
