/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppSortMode

/**
 * Landscape sidebar panel: nav items (Search / Sources / Settings) centered vertically.
 */
@Composable
internal fun HomeSidebarPanel(
    showSearchButton: Boolean,
    searchActive: Boolean,
    isExpertModeEnabled: Boolean,
    showSortButton: Boolean,
    sortMode: HomeAppSortMode,
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    onBundlesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSourcesPositioned: ((Rect) -> Unit)? = null,
    onSettingsPositioned: ((Rect) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        if (showSearchButton) {
            HomeSidebarNavItem(
                icon = if (searchActive) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                label = stringResource(R.string.home_search_apps),
                isSelected = searchActive,
                onClick = onSearchClick
            )
        }
        if (showSortButton) {
            HomeSidebarNavItem(
                icon = Icons.AutoMirrored.Outlined.Sort,
                label = stringResource(R.string.sort),
                isSelected = sortMode != HomeAppSortMode.MANUAL,
                stateDescription = stringResource(sortMode.labelRes),
                onClick = onSortClick
            )
        }
        HomeSidebarNavItem(
            icon = Icons.Outlined.Source,
            label = stringResource(R.string.sources),
            isSelected = false,
            onClick = onBundlesClick,
            modifier = Modifier.then(
                if (onSourcesPositioned != null) Modifier.onGloballyPositioned { coords ->
                    onSourcesPositioned(coords.boundsInWindow())
                } else Modifier
            )
        )
        HomeSidebarNavItem(
            icon = if (isExpertModeEnabled) Icons.Outlined.Engineering else Icons.Outlined.Settings,
            label = stringResource(R.string.settings),
            isSelected = false,
            onClick = onSettingsClick,
            modifier = Modifier.then(
                if (onSettingsPositioned != null) Modifier.onGloballyPositioned { coords ->
                    onSettingsPositioned(coords.boundsInWindow())
                } else Modifier
            )
        )
    }
}

/**
 * Single sidebar nav item: 52dp tall, 16dp rounded corners, animated colors.
 */
@Composable
private fun HomeSidebarNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "sidebarNavItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sidebarNavItemFg"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics {
                role = Role.Button
                selected = isSelected
                if (stateDescription != null) this.stateDescription = stateDescription
            },
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
