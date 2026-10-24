/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.ConfirmDialog
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.rememberOpenUrl
import app.morphe.manager.util.COMMUNITY_PATCHES_URL
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
    DialogHost(visible = state.showHiddenAppsDialog) {
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

    // Nothing the community patches website lists is reviewed by Morphe, so that is said first
    val openUrl = rememberOpenUrl()
    DialogHost(visible = state.showCommunityNotice) {
        ConfirmDialog(
            title = stringResource(R.string.sources_dialog_community_notice_title),
            message = stringResource(R.string.sources_dialog_community_notice_message),
            primaryText = stringResource(R.string.open),
            isPrimaryDestructive = false,
            onDismiss = { state.showCommunityNotice = false },
            onConfirm = {
                state.showCommunityNotice = false
                openUrl(COMMUNITY_PATCHES_URL)
            }
        )
    }

    DialogHost(target = state.categoryNameRequest) { request ->
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
    val pendingDeleteCategory = state.pendingDeleteCategoryId?.let { pendingId ->
        apps.categoryState.categories.firstOrNull { it.id == pendingId }
    }
    // A category gone from under the prompt takes the prompt with it
    if (state.pendingDeleteCategoryId != null && pendingDeleteCategory == null) {
        SideEffect { state.pendingDeleteCategoryId = null }
    }
    DialogHost(target = pendingDeleteCategory) { category ->
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
                appActions.onDeleteCategory(category.id)
                state.pendingDeleteCategoryId = null
            }
        )
    }

    DialogHost(visible = state.showPatchSourcesDialog) {
        AppPatchSourcesDialog(
            packages = state.selectedPackages.keys.toSet(),
            // Every tap inside has already been applied, so closing is the end of the task rather
            // than a step back into picking apps for it
            onDismiss = {
                state.showPatchSourcesDialog = false
                state.exitMultiSelect()
            }
        )
    }

    DialogHost(visible = state.showMoveCategoryDialog) {
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

    DialogHost(visible = state.showBatchUninstallConfirm) {
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
