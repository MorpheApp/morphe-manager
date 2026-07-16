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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.toast

/**
 * Animated confirmation bar that slides up from the bottom of the card list
 * when the user is in multi-select mode.
 */
@Composable
internal fun MultiSelectBar(
    selectedCount: Int,
    totalCount: Int,
    visible: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onAction: () -> Unit,
    actionIcon: ImageVector,
    actionContentDescription: String,
    actionDoneMessage: String,
    onCancel: () -> Unit,
    onEnterReorder: () -> Unit,
    onSaveOrder: () -> Unit,
    onResetOrder: () -> Unit,
    onCancelReorder: () -> Unit,
    modifier: Modifier = Modifier,
    isReorderMode: Boolean = false,
    showReorderButton: Boolean = true,
    actionColors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ),
    onMoveToCategory: (() -> Unit)? = null
) {
    val effectiveReorderMode = isReorderMode && showReorderButton

    val context = LocalContext.current
    fun withToast(doneMessage: String, action: () -> Unit): () -> Unit = {
        context.toast(doneMessage)
        action()
    }

    val cancelLabel = stringResource(android.R.string.cancel)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val reorderListHint = stringResource(R.string.reorder_list_hint)
    val reorderDone = stringResource(R.string.reorder_done)
    val resetOrderLabel = stringResource(R.string.reset_order)
    val resetOrderDone = stringResource(R.string.reset_order_done)
    val doneLabel = stringResource(R.string.done)
    val moveToCategoryLabel = stringResource(R.string.home_category_move_to)

    MultiSelectShell(visible = visible, modifier = modifier) {
        AnimatedContent(
            targetState = effectiveReorderMode,
            transitionSpec = MorpheAnimations.fadeCrossfade(200),
            label = "multibar_mode"
        ) { inReorder ->
            if (inReorder) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = reorderListHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ActionPillRow {
                        ActionPillButton(
                            onClick = withToast(resetOrderDone, onResetOrder),
                            icon = Icons.Outlined.Restore,
                            contentDescription = resetOrderLabel,
                            tooltip = resetOrderLabel
                        )
                        ActionPillButton(
                            onClick = onCancelReorder,
                            icon = Icons.Outlined.Close,
                            contentDescription = cancelLabel,
                            tooltip = cancelLabel
                        )
                        ActionPillButton(
                            onClick = withToast(reorderDone, onSaveOrder),
                            icon = Icons.Outlined.Check,
                            contentDescription = doneLabel,
                            tooltip = doneLabel
                        )
                    }
                }
            } else {
                SelectionActionBar(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    selectedCount = selectedCount,
                    totalCount = totalCount,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    onCancel = onCancel
                ) {
                    if (onMoveToCategory != null) {
                        ActionPillButton(
                            onClick = onMoveToCategory,
                            icon = Icons.Outlined.FolderOpen,
                            contentDescription = moveToCategoryLabel,
                            tooltip = moveToCategoryLabel,
                            enabled = selectedCount > 0
                        )
                    }
                    ActionPillButton(
                        onClick = withToast(actionDoneMessage, onAction),
                        icon = actionIcon,
                        contentDescription = actionContentDescription,
                        tooltip = actionContentDescription,
                        enabled = selectedCount > 0,
                        colors = actionColors
                    )
                    if (showReorderButton) {
                        ActionPillButton(
                            onClick = onEnterReorder,
                            icon = Icons.Outlined.Reorder,
                            contentDescription = reorderListLabel,
                            tooltip = reorderListLabel,
                            enabled = selectedCount > 0
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slide-up bar for the currently long-pressed category header.
 */
@Composable
internal fun CategoryActionBar(
    activeCategoryTitle: String?,
    visible: Boolean,
    isReorderMode: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEnterReorder: () -> Unit,
    onExitReorder: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    showEditActions: Boolean = true
) {
    val cancelLabel = stringResource(android.R.string.cancel)
    val renameLabel = stringResource(R.string.rename)
    val deleteLabel = stringResource(R.string.delete)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val reorderListHint = stringResource(R.string.reorder_list_hint)
    val doneLabel = stringResource(R.string.done)

    val destructiveColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )

    MultiSelectShell(visible = visible, modifier = modifier) {
        AnimatedContent(
            targetState = isReorderMode,
            transitionSpec = MorpheAnimations.fadeCrossfade(200),
            label = "category_bar_mode"
        ) { inReorder ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (inReorder) {
                    Text(
                        text = reorderListHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ActionPillRow {
                        ActionPillButton(
                            onClick = onExitReorder,
                            icon = Icons.Outlined.Check,
                            contentDescription = doneLabel,
                            tooltip = doneLabel
                        )
                    }
                } else {
                    if (activeCategoryTitle != null) {
                        Text(
                            text = activeCategoryTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ActionPillRow {
                        if (showEditActions) {
                            ActionPillButton(
                                onClick = onRename,
                                icon = Icons.Outlined.Edit,
                                contentDescription = renameLabel,
                                tooltip = renameLabel
                            )
                        }
                        ActionPillButton(
                            onClick = onEnterReorder,
                            icon = Icons.Outlined.Reorder,
                            contentDescription = reorderListLabel,
                            tooltip = reorderListLabel
                        )
                        if (showEditActions) {
                            ActionPillButton(
                                onClick = onDelete,
                                icon = Icons.Outlined.Delete,
                                contentDescription = deleteLabel,
                                tooltip = deleteLabel,
                                colors = destructiveColors
                            )
                        }
                        ActionPillButton(
                            onClick = onCancel,
                            icon = Icons.Outlined.Close,
                            contentDescription = cancelLabel,
                            tooltip = cancelLabel
                        )
                    }
                }
            }
        }
    }
}
