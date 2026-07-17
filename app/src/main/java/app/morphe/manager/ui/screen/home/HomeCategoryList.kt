/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategoryState
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.GlassButtonDefaults
import app.morphe.manager.ui.viewmodel.HomeAppSourceGroup
import app.morphe.manager.util.RemoteAvatar
import com.google.accompanist.drawablepainter.rememberDrawablePainter

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
 * [ignoreCollapsed] is used by the search flow to force every group open so matches are
 * visible; when true, empty categories are also dropped from the result.
 */
internal fun buildHomeCategoryGroups(
    items: List<HomeAppItem>,
    categoryState: HomeAppCategoryState,
    uncategorizedTitle: String,
    ignoreCollapsed: Boolean
): List<HomeCategoryGroup> {
    val assigned = items.groupBy { item -> categoryState.assignments[item.packageName] }
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
        categoryState.assignments[item.packageName] !in knownCategoryIds
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
    val claimedPackages = HashSet<String>(items.size)
    items.forEach { item ->
        val owners = sourcesByPackage[item.packageName] ?: return@forEach
        claimedPackages.add(item.packageName)
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

    val uncategorizedItems = items.filter { item -> item.packageName !in claimedPackages }
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
    return sortedBy { orderIndex[it.packageName] ?: Int.MAX_VALUE }
}

/**
 * Frosted-glass row primitive shared by [HomeCategoryHeader] and the hidden-apps button.
 *
 * When both [onClick] and [onLongClick] are non-null a `combinedClickable` is wired; when
 * only [onLongClick] is present, [onClick] falls back to a no-op so long-press can still fire
 * on a non-tappable row.
 */
@Composable
internal fun HomeGlassCategoryRow(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    count: String? = null,
    cornerRadius: Dp = 20.dp,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(cornerRadius)
    val containerColor = GlassButtonDefaults.containerColor()
    val borderColor = GlassButtonDefaults.borderColor()
    val contentColor = MaterialTheme.colorScheme.onSurface
    val mutedContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    val interactionModifier = when {
        onLongClick != null -> Modifier.combinedClickable(
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(interactionModifier),
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leading?.invoke()
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
    val leadingIcon = if (group.collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen

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
                    tint = mutedContentColor
                )
            }
        },
        trailing = {
            dragHandle?.invoke()
            if (group.collapsible) {
                Icon(
                    imageVector = if (group.collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = if (group.collapsed)
                        stringResource(R.string.expand)
                    else
                        stringResource(R.string.collapse),
                    modifier = Modifier.size(24.dp),
                    tint = mutedContentColor
                )
            }
        },
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
            group.sourceIsDefault -> {
                val context = LocalContext.current
                Image(
                    painter = rememberDrawablePainter(
                        drawable = AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_launcher_foreground
                        )
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 1.5f
                        }
                )
            }

            group.sourceAvatarUrl != null -> {
                RemoteAvatar(
                    url = group.sourceAvatarUrl,
                    fallbackUrl = group.sourceFallbackAvatarUrl,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Outlined.Source,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

/**
 * Drag-handle affordance for [HomeCategoryHeader]. Passed as the slot content so the caller
 * can attach a `draggableHandle` from the reorderable library without leaking that state
 * into the header.
 */
@Composable
internal fun CategoryHeaderDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = stringResource(R.string.reorder_list),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
