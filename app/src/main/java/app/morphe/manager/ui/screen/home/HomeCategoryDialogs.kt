/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategory
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.htmlAnnotatedString

/**
 * Prompt for a category name. Reused for both create and rename flows: pass a non-null
 * [category] to prefill the field and switch the title/primary button to "Rename".
 * [onConfirm] receives the trimmed name and is only enabled once it is non-blank.
 */
@Composable
internal fun CategoryNameDialog(
    category: HomeAppCategory?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    val trimmed = name.trim()

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            if (category == null) R.string.home_category_new_title
            else R.string.home_category_rename_title
        ),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(if (category == null) R.string.add else R.string.rename),
                onPrimaryClick = { onConfirm(trimmed) },
                primaryEnabled = trimmed.isNotEmpty(),
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        MorpheDialogTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.home_category_name)) },
            leadingIcon = {
                Icon(Icons.Outlined.Category, contentDescription = null)
            },
            showClearButton = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Confirmation prompt shown when the user taps Delete in the category action bar.
 * Category deletion drops assignments back to Uncategorized rather than removing apps,
 * so the message reflects that instead of implying data loss.
 */
@Composable
internal fun CategoryDeleteConfirmDialog(
    category: HomeAppCategory,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_category_delete_confirm_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.delete),
                onPrimaryClick = onConfirm,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current
        Text(
            text = htmlAnnotatedString(
                stringResource(
                    R.string.home_category_delete_confirm_message,
                    category.name,
                    stringResource(R.string.home_category_uncategorized)
                )
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Bulk destination picker for the multi-select "move to category" action. Offers the
 * uncategorized bucket, every existing [categories] entry, and an inline "Add category"
 * shortcut that opens [CategoryNameDialog] and forwards the new id via [onCreateAndSelect].
 * [onSelect] is called with null to clear the assignment.
 */
@Composable
internal fun MoveToCategoryDialog(
    categories: List<HomeAppCategory>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreateAndSelect: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CategoryNameDialog(
            category = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreateAndSelect(name)
            }
        )
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_category_move_selected_title),
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MorpheDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            SettingsItemCard(onClick = { showCreateDialog = true }, borderWidth = 1.dp) {
                IconTextRow(
                    modifier = Modifier.padding(MorpheDefaults.ContentPadding),
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.Add) },
                    title = stringResource(R.string.home_category_add),
                    trailingContent = null
                )
            }

            SettingsItemCard(onClick = { onSelect(null) }, borderWidth = 1.dp) {
                IconTextRow(
                    modifier = Modifier.padding(MorpheDefaults.ContentPadding),
                    leadingContent = { MorpheIcon(icon = Icons.Outlined.FolderOff) },
                    title = stringResource(R.string.home_category_uncategorized),
                    trailingContent = null
                )
            }

            categories.forEach { category ->
                SettingsItemCard(onClick = { onSelect(category.id) }, borderWidth = 1.dp) {
                    IconTextRow(
                        modifier = Modifier.padding(MorpheDefaults.ContentPadding),
                        leadingContent = { MorpheIcon(icon = Icons.Outlined.Folder) },
                        title = category.name,
                        trailingContent = null
                    )
                }
            }
        }
    }
}
