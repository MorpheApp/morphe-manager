/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstalledAppPickerItem

private enum class AppFilter { All, UserOnly, SystemOnly }

/**
 * Dialog that shows all installed apps for the universal-patch flow.
 * User picks an app; its APK is extracted and sent through the patch pipeline.
 */
@Composable
fun DialogHostScope.InstalledAppPickerDialog(
    items: List<InstalledAppPickerItem>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (InstalledAppPickerItem) -> Unit
) {
    val search = rememberSearchFieldState()
    var appFilter by remember { mutableStateOf(AppFilter.UserOnly) }
    val filtered = remember(items, search.query, appFilter) {
        items
            .let { list ->
                when (appFilter) {
                    AppFilter.UserOnly -> list.filter { !it.isSystemApp }
                    AppFilter.SystemOnly -> list.filter { it.isSystemApp }
                    AppFilter.All -> list
                }
            }
            .let { list ->
                if (search.query.isBlank()) list
                else list.filter {
                    it.label.contains(search.query, ignoreCase = true) ||
                            it.packageName.contains(search.query, ignoreCase = true)
                }
            }
    }
    // Picked from a menu as every list's filter is, in the order the filters narrow the list
    val filterOptions = listOf(
        SortModeOption(AppFilter.All, stringResource(R.string.home_category_all_apps)),
        SortModeOption(AppFilter.UserOnly, stringResource(R.string.home_installed_app_picker_filter_user)),
        SortModeOption(AppFilter.SystemOnly, stringResource(R.string.home_installed_app_picker_filter_system))
    )
    val filterLabel = filterOptions.first { it.value == appFilter }.title
    val filterMenuOpen = remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    ListDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = true,
        hideFooterWhileTyping = true,
        header = {
            ListDialogHeader(
                icon = { modifier ->
                    ListDialogHeaderIcon(icon = Icons.Outlined.Apps, color = accent, modifier = modifier)
                },
                title = stringResource(R.string.home_installed_app_picker_title),
                // The filter names itself here, so switching it says what the list now holds. A line
                // each, as the filter's name is a phrase that would otherwise break partway
                subtitle = listOf(
                    pluralStringResource(R.plurals.home_category_app_count, filtered.size, filtered.size.toString()),
                    filterLabel
                ).joinToString("\n"),
                subtitleLoading = isLoading,
                search = search,
                searchLabel = stringResource(R.string.home_search_apps),
                searchEnabled = !isLoading,
                accentColor = accent
            ) {
                // The menu hangs off the button, so it opens under the header's end
                Box {
                    TitleAction(
                        icon = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.filter),
                        onClick = { filterMenuOpen.value = true },
                        style = TitleActionStyle.Toggle,
                        // Engaged while its menu is open, and marked once the list is off the user
                        // apps it starts on
                        active = filterMenuOpen.value,
                        marked = appFilter != AppFilter.UserOnly,
                        modifier = Modifier.semantics { stateDescription = filterLabel }
                    )
                    SortModeMenu(
                        expanded = filterMenuOpen.value,
                        onDismissRequest = { filterMenuOpen.value = false },
                        current = appFilter,
                        options = filterOptions,
                        onSelect = { appFilter = it }
                    )
                }
            }
        },
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        SearchFieldBackHandler(search)

        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CompactCardSpacing),
            userScrollEnabled = !isLoading
        ) {
            if (isLoading) {
                items(10) { ShimmerCompactListCard(descriptionLines = 2) }
            } else {
                if (filtered.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyState(
                            message = stringResource(R.string.home_installed_app_picker_empty),
                            icon = Icons.Outlined.SearchOff,
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                // Carded like the file picker's entries, the other long list this is picked from
                items(filtered, key = { it.packageName }) { item ->
                    CompactListCard(
                        onClick = { onSelect(item) },
                        modifier = Modifier.animateItem()
                    ) {
                        AppIcon(
                            packageInfo = item.packageInfo,
                            contentDescription = null,
                            modifier = Modifier.size(CompactCardIconSize)
                        )
                        CardHeadingText(
                            name = item.label,
                            // Universal patches name no version, so nothing here checks the build
                            // code and nothing would act on it
                            description = "${item.packageName}\nv${item.info.version}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
