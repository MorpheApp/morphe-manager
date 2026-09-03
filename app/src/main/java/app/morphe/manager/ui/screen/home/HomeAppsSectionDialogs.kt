/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.ConfirmDialog
import app.morphe.manager.util.htmlAnnotatedString

/**
 * Dialogs the apps section can raise. Split out so the section itself reads as list and bar
 * only - each one is driven purely by a flag on [state] and closes by clearing it.
 */
@Composable
internal fun HomeAppsSectionDialogs(
    state: HomeAppsSectionState,
    apps: HomeAppListUi,
    appActions: HomeAppActions,
) {
    if (state.showHiddenAppsDialog) {
        HiddenAppsDialog(
            hiddenAppItems = apps.hidden,
            onUnhide = appActions.onUnhideApp,
            onUnhideMultiple = { packages ->
                packages.forEach { appActions.onUnhideApp(it) }
            },
            onShowPatches = appActions.onShowPatches,
            onDismiss = { state.showHiddenAppsDialog = false }
        )
    }

    state.categoryNameRequest?.let { request ->
        CategoryNameDialog(
            category = request.category,
            onDismiss = { state.categoryNameRequest = null },
            onConfirm = { name ->
                val category = request.category
                if (category == null) appActions.onCreateCategory(name)
                else appActions.onRenameCategory(category.id, name)
                state.categoryNameRequest = null
            }
        )
    }

    // Held in state so the dialog outlives the bar closing on Delete tap
    state.pendingDeleteCategoryId?.let { pendingId ->
        val category = apps.categoryState.categories.firstOrNull { it.id == pendingId }
        if (category == null) {
            state.pendingDeleteCategoryId = null
        } else {
            ConfirmDialog(
                title = stringResource(R.string.home_category_delete_confirm_title),
                message = htmlAnnotatedString(
                    stringResource(
                        R.string.home_category_delete_confirm_message,
                        category.name,
                        stringResource(R.string.home_category_uncategorized)
                    )
                ),
                primaryText = stringResource(R.string.delete),
                onDismiss = { state.pendingDeleteCategoryId = null },
                onConfirm = {
                    appActions.onDeleteCategory(pendingId)
                    state.pendingDeleteCategoryId = null
                }
            )
        }
    }

    if (state.showMoveCategoryDialog) {
        MoveToCategoryDialog(
            categories = apps.categoryState.categories,
            onDismiss = { state.showMoveCategoryDialog = false },
            onSelect = { categoryId ->
                appActions.onAssignAppsToCategory(state.selectedPackages.keys.toSet(), categoryId)
                state.exitMultiSelect()
                state.showMoveCategoryDialog = false
            },
            onCreateAndSelect = { name ->
                val categoryId = appActions.onCreateCategory(name)
                if (categoryId.isNotBlank()) {
                    appActions.onAssignAppsToCategory(state.selectedPackages.keys.toSet(), categoryId)
                    state.exitMultiSelect()
                    state.showMoveCategoryDialog = false
                }
            }
        )
    }

    if (state.showBatchUninstallConfirm) {
        val pendingItems = state.pendingUninstallItems
        ConfirmDialog(
            title = pluralStringResource(
                R.plurals.batch_uninstall_confirm_title,
                pendingItems.size,
                pendingItems.size.toString()
            ),
            message = stringResource(R.string.batch_uninstall_confirm_body),
            primaryText = stringResource(R.string.uninstall),
            onConfirm = {
                appActions.onUninstallMultiple(pendingItems)
                state.exitMultiSelect()
                state.pendingUninstallItems = emptyList()
                state.showBatchUninstallConfirm = false
            },
            onDismiss = { state.showBatchUninstallConfirm = false }
        )
    }
}
