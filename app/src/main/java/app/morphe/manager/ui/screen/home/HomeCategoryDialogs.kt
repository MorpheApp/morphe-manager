/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppCategory
import app.morphe.manager.ui.screen.shared.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Prompt for a category name. Reused for both create and rename flows: pass a non-null
 * [category] to prefill the field and switch the title/primary button to "Rename".
 * [onConfirm] receives the trimmed name and is only enabled once it is non-blank.
 */
@Composable
internal fun DialogHostScope.CategoryNameDialog(
    category: HomeAppCategory?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    val trimmed = name.trim()

    TextInputDialog(
        title = stringResource(
            if (category == null) R.string.home_category_new_title
            else R.string.home_category_rename_title
        ),
        description = stringResource(R.string.home_category_name_description),
        value = name,
        onValueChange = { name = it },
        label = stringResource(R.string.home_category_name),
        leadingIcon = MaterialSymbols.Rounded.Category,
        confirmText = stringResource(if (category == null) R.string.add else R.string.rename),
        confirmEnabled = trimmed.isNotEmpty(),
        onConfirm = { onConfirm(trimmed) },
        onDismiss = onDismiss
    )
}

/**
 * Bulk destination picker for the multi-select "move to category" action. Offers the
 * uncategorized bucket, every existing [categories] entry, and an inline "Add category"
 * shortcut that opens [CategoryNameDialog] and forwards the new id via [onCreateAndSelect].
 * [onSelect] is called with null to clear the assignment.
 */
@Composable
internal fun DialogHostScope.MoveToCategoryDialog(
    categories: List<HomeAppCategory>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreateAndSelect: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    DialogHost(visible = showCreateDialog) {
        CategoryNameDialog(
            category = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreateAndSelect(name)
            }
        )
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_category_move_selected_title),
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
                .padding(vertical = Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            SettingsItemCard(onClick = { showCreateDialog = true }, showBorder = true) {
                IconTextRow(
                    modifier = Modifier.padding(Defaults.SpaceLarge),
                    leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Add) },
                    title = stringResource(R.string.home_category_add),
                    trailingContent = null
                )
            }

            SettingsItemCard(onClick = { onSelect(null) }, showBorder = true) {
                IconTextRow(
                    modifier = Modifier.padding(Defaults.SpaceLarge),
                    leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Folder_off) },
                    title = stringResource(R.string.home_category_uncategorized),
                    trailingContent = null
                )
            }

            categories.forEach { category ->
                SettingsItemCard(onClick = { onSelect(category.id) }, showBorder = true) {
                    IconTextRow(
                        modifier = Modifier.padding(Defaults.SpaceLarge),
                        leadingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Folder) },
                        title = category.name,
                        trailingContent = null
                    )
                }
            }
        }
    }
}
