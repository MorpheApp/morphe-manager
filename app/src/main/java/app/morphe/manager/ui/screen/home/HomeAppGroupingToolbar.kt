/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppGroupingModeButton(
            onClick = { onModeChange(HomeAppCategoryViewMode.ALL_APPS) },
            icon = Icons.Outlined.Apps,
            label = stringResource(R.string.home_category_all_apps),
            selected = mode == HomeAppCategoryViewMode.ALL_APPS,
            modifier = Modifier.weight(1f)
        )
        AppGroupingModeButton(
            onClick = { onModeChange(HomeAppCategoryViewMode.SOURCES) },
            icon = Icons.Outlined.Source,
            label = stringResource(R.string.sources),
            selected = mode == HomeAppCategoryViewMode.SOURCES,
            modifier = Modifier.weight(1f)
        )
        AppGroupingModeButton(
            onClick = { onModeChange(HomeAppCategoryViewMode.CUSTOM) },
            icon = Icons.Outlined.Category,
            label = stringResource(R.string.home_category_custom),
            selected = mode == HomeAppCategoryViewMode.CUSTOM,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AppGroupingModeButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    HomeGlassPillButton(
        onClick = onClick,
        text = label,
        icon = icon,
        modifier = modifier,
        selected = selected,
        compact = true
    )
}
