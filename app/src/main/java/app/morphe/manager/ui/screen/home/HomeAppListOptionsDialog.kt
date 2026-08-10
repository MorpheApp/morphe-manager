/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.screen.shared.*

/**
 * The button carries both modes, so the sort mode alone only describes it while nothing is filtered.
 */
@Composable
internal fun homeAppListOptionsStateDescription(
    sortMode: HomeAppSortMode,
    filterMode: HomeAppFilterMode
): String = if (filterMode.isActive) {
    stringResource(
        R.string.home_app_list_options_state_description,
        stringResource(sortMode.labelRes),
        stringResource(filterMode.labelRes)
    )
} else {
    stringResource(sortMode.labelRes)
}

@Composable
internal fun HomeAppListOptionsDialog(
    sortMode: HomeAppSortMode,
    filterMode: HomeAppFilterMode,
    onSortModeChange: (HomeAppSortMode) -> Unit,
    onFilterModeChange: (HomeAppFilterMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(if (filterMode.isActive) 1 else 0) }
    val tabs = listOf(
        CardSelectorOption(
            label = stringResource(R.string.sort),
            icon = Icons.AutoMirrored.Outlined.Sort
        ),
        CardSelectorOption(
            label = stringResource(R.string.filter),
            icon = Icons.Outlined.FilterList
        )
    )

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_app_list_options_title),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Defaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
        ) {
            CardSelectorRow(
                options = tabs,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = Animations.fadeCrossfade()
            ) { tab ->
                Column(verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)) {
                    when (tab) {
                        0 -> sortModeOptions<HomeAppSortMode>().forEach { option ->
                            RadioSelectionCard(
                                selected = sortMode == option.value,
                                onSelect = { onSortModeChange(option.value) },
                                title = option.title,
                                description = option.description
                            )
                        }

                        else -> HomeAppFilterMode.entries.forEach { mode ->
                            RadioSelectionCard(
                                selected = filterMode == mode,
                                onSelect = { onFilterModeChange(mode) },
                                title = stringResource(mode.labelRes),
                                description = stringResource(mode.descriptionRes)
                            )
                        }
                    }
                }
            }
        }
    }
}
