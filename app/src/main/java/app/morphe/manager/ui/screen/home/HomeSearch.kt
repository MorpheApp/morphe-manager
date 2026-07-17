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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.LocalDialogTextColor
import app.morphe.manager.ui.screen.shared.MorpheDefaults
import app.morphe.manager.ui.screen.shared.MorpheDialogTextField

/**
 * Wraps [MorpheDialogTextField] with [LocalDialogTextColor] set to onSurface
 * so it renders correctly outside a dialog context.
 */
@Composable
internal fun HomeSearchTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    requestFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    CompositionLocalProvider(LocalDialogTextColor provides MaterialTheme.colorScheme.onSurface) {
        MorpheDialogTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.home_search_apps)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.home_search_apps)
                )
            },
            showClearButton = true,
            modifier = modifier.focusRequester(focusRequester)
        )
    }
}

/**
 * Generic empty state with icon, title, optional subtitle and optional action button.
 */
@Composable
internal fun MorpheEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
        if (onAction != null && actionLabel != null) {
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(onClick = onAction) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(actionLabel)
            }
        }
    }
}

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
                    .padding(top = 8.dp, bottom = 4.dp)
                    .animateItem()
            )
        }
        itemsIndexed(
            items = filteredHiddenItems,
            key = { _, item -> "${keyPrefix}hidden_${item.packageName}" }
        ) { _, item ->
            HiddenSearchAppCard(
                item = item,
                onUnhide = { appActions.onUnhideApp(item.packageName) },
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
                    .padding(vertical = 32.dp)
                    .animateItem(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    fadeInSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                    fadeOutSpec = tween(MorpheDefaults.ANIMATION_DURATION),
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            )
        }
    }
}
