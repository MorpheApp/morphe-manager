/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppSortMode
import app.morphe.manager.ui.screen.shared.AppDialog
import app.morphe.manager.ui.screen.shared.AppDialogOutlinedButton
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.RadioSelectionCard
import app.morphe.manager.ui.screen.shared.sortModeOptions

@Composable
internal fun HomeAppListOptionsDialog(
    sortMode: HomeAppSortMode,
    filterMode: HomeAppFilterMode,
    onSortModeChange: (HomeAppSortMode) -> Unit,
    onFilterModeChange: (HomeAppFilterMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (filterMode.isActive) 1 else 0) }
    val tabs = listOf(R.string.sort, R.string.filter)

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
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, titleRes ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(titleRes)) }
                    )
                }
            }

            when (selectedTab) {
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
