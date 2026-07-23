/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.HeroInfoCard
import app.morphe.manager.ui.screen.shared.MorpheAnimations
import app.morphe.manager.ui.screen.shared.MorpheDialogTextField

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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        val patchCountLabel = pluralStringResource(
            R.plurals.patch_count,
            totalCount,
            totalCount
        )
        val countText = if (isFiltering) "$filteredCount/$patchCountLabel"
        else patchCountLabel
        AnimatedContent(
            targetState = countText,
            transitionSpec = MorpheAnimations.counterTransitionSpec,
            label = "patches_count"
        ) { count ->
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
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
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            MorpheDialogTextField(
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
                        modifier = Modifier.size(20.dp)
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.expert_mode_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
