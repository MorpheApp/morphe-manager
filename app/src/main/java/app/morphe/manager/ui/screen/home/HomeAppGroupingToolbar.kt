/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Source
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategoryViewMode
import app.morphe.manager.ui.screen.shared.SegmentedIconLabelButton

/**
 * Segmented pill row for switching between [HomeAppCategoryViewMode]s from the home footer.
 * Shown only when the user has enabled the on-screen selector in Appearance settings.
 */
@Composable
internal fun AppGroupingToolbar(
    mode: HomeAppCategoryViewMode,
    onModeChange: (HomeAppCategoryViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppGroupingModeButton(
            onClick = { onModeChange(HomeAppCategoryViewMode.ALL_APPS) },
            icon = Icons.Outlined.Apps,
            label = stringResource(R.string.home_category_all_apps),
            selected = mode == HomeAppCategoryViewMode.ALL_APPS
        )
        AppGroupingModeButton(
            onClick = { onModeChange(HomeAppCategoryViewMode.SOURCES) },
            icon = Icons.Outlined.Source,
            label = stringResource(R.string.sources),
            selected = mode == HomeAppCategoryViewMode.SOURCES
        )
        AppGroupingModeButton(
            onClick = { onModeChange(HomeAppCategoryViewMode.CUSTOM) },
            icon = Icons.Outlined.Category,
            label = stringResource(R.string.home_category_custom),
            selected = mode == HomeAppCategoryViewMode.CUSTOM
        )
    }
}

@Composable
private fun RowScope.AppGroupingModeButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    selected: Boolean
) {
    SegmentedIconLabelButton(
        onClick = onClick,
        icon = icon,
        label = label,
        selected = selected,
        modifier = if (selected) Modifier.weight(1f) else Modifier.width(48.dp),
        containerColor = HomeGlassButtonDefaults.containerColor(selected),
        contentColor = HomeGlassButtonDefaults.contentColor(selected),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeGlassButtonDefaults.borderColor(selected)),
        pressScale = true,
        hapticFeedback = true
    )
}
