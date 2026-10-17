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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.Defaults

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
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = mutedContentColor
            )
        },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = mutedContentColor
            )
        },
        modifier = modifier
    )
}

internal fun LazyListScope.hiddenSearchAndShowHiddenItems(
    hiddenAppItems: List<HomeAppItem>,
    filteredHiddenItems: List<HomeAppItem>,
    searchQuery: String,
    isSearchEmpty: Boolean,
    appActions: HomeAppActions,
    onShowHiddenApps: () -> Unit,
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
                onUnhide = { appActions.onUnhideApp(item.id) },
                onAppClick = { appActions.onAppClick(item) },
                onShowPatches = { appActions.onShowPatches(item) },
                modifier = Modifier.animateItem()
            )
        }
    }

    if (isSearchEmpty) {
        item(key = "${keyPrefix}search_empty") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Defaults.SpaceExtraExtraLarge)
                    .animateItem(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = stringResource(R.string.home_no_apps_search_subtitle, searchQuery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
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
