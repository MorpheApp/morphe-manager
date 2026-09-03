/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.InstalledAppPickerItem
import app.morphe.manager.util.toast

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
    val context = LocalContext.current
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
        title = stringResource(R.string.home_installed_app_picker_title),
        padding = DialogPadding.Compact,
        scrollable = false,
        titleTrailingContent = {
            TitleAction(
                icon = if (search.visible) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                contentDescription = stringResource(R.string.search),
                onClick = { search.toggle() },
                style = TitleActionStyle.Toggle,
                active = search.visible
            )

            val labelAll = stringResource(R.string.home_installed_app_picker_filter_all)
            val labelUser = stringResource(R.string.home_installed_app_picker_filter_user)
            val labelSystem = stringResource(R.string.home_installed_app_picker_filter_system)
            val (icon, description) = when (appFilter) {
                AppFilter.All -> Icons.Outlined.FilterList to labelAll
                AppFilter.UserOnly -> Icons.Outlined.Person to labelUser
                AppFilter.SystemOnly -> Icons.Outlined.Android to labelSystem
            }
            TitleAction(
                icon = icon,
                contentDescription = description,
                onClick = {
                    appFilter = when (appFilter) {
                        AppFilter.All -> AppFilter.UserOnly
                        AppFilter.UserOnly -> AppFilter.SystemOnly
                        AppFilter.SystemOnly -> AppFilter.All
                    }
                    context.toast(
                        when (appFilter) {
                            AppFilter.All -> labelAll
                            AppFilter.UserOnly -> labelUser
                            AppFilter.SystemOnly -> labelSystem
                        }
                    )
                },
                style = TitleActionStyle.Toggle,
                active = appFilter != AppFilter.All
            )
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

        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = !isLoading
            ) {
                stickyHeader(key = "search") {
                    AppDialogSearchHeader(
                        visible = search.visible,
                        value = search.query,
                        onValueChange = { search.query = it },
                        label = stringResource(R.string.home_search_apps)
                    )
                }

                if (isLoading) {
                    items(10) { index ->
                        ShimmerInstalledAppRow()
                        if (index < 9) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        }
                    }
                } else {
                    if (filtered.isEmpty()) {
                        item(key = "empty_state") {
                            Box(
                                modifier = Modifier
                                    .animateItem()
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
                                        text = stringResource(R.string.home_installed_app_picker_empty),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(filtered, key = { _, item -> item.packageName }) { index, item ->
                        Column(modifier = Modifier.animateItem()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(item) }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sized to the three text lines beside it so the row reads as one block
                                AppIcon(
                                    packageInfo = item.packageInfo,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        // Universal patches name no version, so nothing here
                                        // checks the build code and nothing would act on it
                                        text = "v${item.info.version}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryColor.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (index < filtered.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }

            ListScrollbar(
                listState = listState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )

            ScrollToTopButton(
                listState = listState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )
        }
    }
}
