/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstalledAppPickerItem

private enum class AppFilter { All, UserOnly, SystemOnly }

/**
 * Dialog that shows all installed apps for the universal-patch flow.
 * User picks an app; its APK is extracted and sent through the patch pipeline.
 */
@Composable
fun InstalledAppPickerDialog(
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
    AppDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = true,
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
        hideFooterWhileTyping = true,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        SearchFieldBackHandler(search)

        val (filterIcon, filterLabel) = when (appFilter) {
            AppFilter.All -> Icons.Outlined.FilterList to stringResource(R.string.home_category_all_apps)
            AppFilter.UserOnly -> Icons.Outlined.Person to stringResource(R.string.home_installed_app_picker_filter_user)
            AppFilter.SystemOnly -> Icons.Outlined.Android to stringResource(R.string.home_installed_app_picker_filter_system)
        }
        val accent = MaterialTheme.colorScheme.primary
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
            TitleAction(
                icon = filterIcon,
                contentDescription = filterLabel,
                onClick = {
                    appFilter = when (appFilter) {
                        AppFilter.All -> AppFilter.UserOnly
                        AppFilter.UserOnly -> AppFilter.SystemOnly
                        AppFilter.SystemOnly -> AppFilter.All
                    }
                },
                style = TitleActionStyle.Toggle,
                active = appFilter != AppFilter.All
            )
        }

        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
            pinnedFirstRow = true,
            userScrollEnabled = !isLoading
        ) {
            // Kept while the field is closed, so its share of the spacing makes the gap under
            // the header
            stickyHeader(key = "search") {
                AppDialogSearchHeader(
                    visible = search.visible,
                    value = search.query,
                    onValueChange = { search.query = it },
                    label = stringResource(R.string.home_search_apps),
                    // Opaque, so rows scrolled under the gap stay hidden
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = Defaults.ItemSpacing)
                )
            }

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
