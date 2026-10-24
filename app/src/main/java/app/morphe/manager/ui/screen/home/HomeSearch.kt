/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.CardAction
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.EmptyState
import app.morphe.manager.ui.screen.shared.autoMirrored
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Category-style row that opens the hidden-apps dialog.
 */
@Composable
private fun ShowHiddenAppsButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mutedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    HomeGlassCategoryRow(
        title = stringResource(R.string.hidden),
        count = pluralStringResource(R.plurals.home_category_app_count, count, count.toString()),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = MaterialSymbols.Rounded.Visibility,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = mutedContentColor
            )
        },
        trailing = {
            Icon(
                imageVector = MaterialSymbols.Rounded.Keyboard_arrow_right.autoMirrored,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = mutedContentColor
            )
        },
        modifier = modifier
    )
}

/**
 * What ends the list: the hidden apps a search finds, the way on when it finds nothing, and the row
 * opening the hidden apps.
 *
 * @param onOpenCommunityPatches Offered when a search finds nothing, since the app looked for may
 *   come from a source not added yet.
 */
internal fun LazyListScope.hiddenSearchAndShowHiddenItems(
    hiddenAppItems: List<HomeAppItem>,
    filteredHiddenItems: List<HomeAppItem>,
    searchQuery: String,
    isSearchEmpty: Boolean,
    appActions: HomeAppActions,
    onShowHiddenApps: () -> Unit,
    onOpenCommunityPatches: () -> Unit,
    keyPrefix: String = ""
) {
    if (filteredHiddenItems.isNotEmpty()) {
        item(key = "${keyPrefix}search_hidden_header") {
            Text(
                text = stringResource(R.string.hidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Defaults.SpaceSmall, bottom = Defaults.SpaceExtraSmall)
                    .animateItem()
            )
        }
        itemsIndexed(
            items = filteredHiddenItems,
            key = { _, item -> "${keyPrefix}hidden_${item.id}" }
        ) { _, item ->
            HiddenSearchAppCard(
                item = item,
                onUnhide = unhideReporting(item, appActions.onUnhideApp),
                onAppClick = { appActions.onAppClick(item) },
                onShowPatches = { appActions.onShowPatches(item) },
                modifier = Modifier.animateItem()
            )
        }
    }

    if (isSearchEmpty) {
        item(key = "${keyPrefix}search_empty") {
            EmptyState(
                message = stringResource(R.string.search_no_results),
                icon = MaterialSymbols.Rounded.Search_off,
                subtitle = stringResource(R.string.home_no_apps_search_subtitle, searchQuery),
                action = CardAction(
                    icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored,
                    label = stringResource(R.string.sources_dialog_community),
                    onClick = onOpenCommunityPatches
                ),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem()
            )
        }
    }

    if (hiddenAppItems.isNotEmpty() && searchQuery.isBlank()) {
        item(key = "${keyPrefix}show_hidden") {
            ShowHiddenAppsButton(
                count = hiddenAppItems.size,
                onClick = onShowHiddenApps,
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(Defaults.ANIMATION_DURATION),
                    fadeOutSpec = tween(Defaults.ANIMATION_DURATION),
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            )
        }
    }
}
