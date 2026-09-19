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
import app.morphe.manager.util.withToast

/**
 * Everything [MultiSelectBar] renders from state its own actions clear, kept together so it
 * can be frozen as a single value while the bar slides out. Anything that changes as a result
 * of using the bar belongs here rather than being read straight from a parameter.
 */
private data class MultiSelectDisplay(
    val count: Int,
    val total: Int,
    val inReorderMode: Boolean,
    val contextIcon: ImageVector?,
    val contextDescription: String?,
    val contextColors: IconButtonColors,
    val onContextAction: (() -> Unit)?
)

/** The same, for [CategoryActionBar]. */
private data class CategoryDisplay(
    val title: String?,
    val inReorderMode: Boolean,
    val showEditActions: Boolean
)

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
    onContextAction: (() -> Unit)? = null,
    contextActionIcon: ImageVector? = null,
    contextActionContentDescription: String? = null,
    contextActionColors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    onMoveToCategory: (() -> Unit)? = null,
    onPatchSelected: (() -> Unit)? = null,
    onPatchSources: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val cancelLabel = stringResource(android.R.string.cancel)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val reorderListHint = stringResource(R.string.reorder_list_hint)
    val reorderDone = stringResource(R.string.reorder_done)
    val resetOrderLabel = stringResource(R.string.reset_order)
    val resetOrderDone = stringResource(R.string.reset_order_done)
    val doneLabel = stringResource(R.string.done)
    val moveToCategoryLabel = stringResource(R.string.home_category_move_to)
    val patchSelectedLabel = stringResource(R.string.batch_patch_action)
    val patchSourcesLabel = stringResource(R.string.sources_management_title)

    val selection = rememberWhileVisible(
        visible,
        MultiSelectDisplay(
            count = selectedCount,
            total = totalCount,
            inReorderMode = isReorderMode && showReorderButton,
            contextIcon = contextActionIcon,
            contextDescription = contextActionContentDescription,
            contextColors = contextActionColors,
            onContextAction = onContextAction
        )
    )

    MultiSelectShell(visible = visible, modifier = modifier) {
        AnimatedContent(
            targetState = selection.inReorderMode,
            transitionSpec = Animations.fadeCrossfade(200),
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
                            onClick = onResetOrder,
                            icon = Icons.Outlined.Restore,
                            contentDescription = resetOrderLabel,
                            tooltip = resetOrderLabel,
                            confirmation = resetOrderDone
                        )
                        ActionPillButton(
                            onClick = onCancelReorder,
                            icon = Icons.Outlined.Close,
                            contentDescription = cancelLabel,
                            tooltip = cancelLabel
                        )
                        ActionPillButton(
                            onClick = context.withToast(reorderDone, onSaveOrder),
                            icon = Icons.Outlined.Check,
                            contentDescription = doneLabel,
                            tooltip = doneLabel
                        )
                    }
                }
            } else {
                SelectionActionBar(
                    selectedCount = selection.count,
                    totalCount = selection.total,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    onCancel = onCancel
                ) {
                    if (onPatchSelected != null) {
                        ActionPillButton(
                            onClick = onPatchSelected,
                            icon = Icons.Outlined.AutoFixHigh,
                            contentDescription = patchSelectedLabel,
                            tooltip = patchSelectedLabel,
                            enabled = selection.count > 0,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    if (onPatchSources != null) {
                        // Next to "Patch selected" rather than next to "Hide": both answer
                        // what patching these apps does, while "Hide" is about this screen
                        ActionPillButton(
                            onClick = onPatchSources,
                            icon = Icons.Outlined.Source,
                            contentDescription = patchSourcesLabel,
                            tooltip = patchSourcesLabel,
                            enabled = selection.count > 0,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                    if (onMoveToCategory != null) {
                        ActionPillButton(
                            onClick = onMoveToCategory,
                            icon = Icons.Outlined.FolderOpen,
                            contentDescription = moveToCategoryLabel,
                            tooltip = moveToCategoryLabel,
                            enabled = selection.count > 0
                        )
                    }
                    val contextIcon = selection.contextIcon
                    val contextDescription = selection.contextDescription
                    val contextAction = selection.onContextAction
                    if (contextIcon != null && contextDescription != null && contextAction != null) {
                        ActionPillButton(
                            onClick = contextAction,
                            icon = contextIcon,
                            contentDescription = contextDescription,
                            tooltip = contextDescription,
                            enabled = selection.count > 0,
                            colors = selection.contextColors
                        )
                    }
                    ActionPillButton(
                        onClick = context.withToast(actionDoneMessage, onAction),
                        icon = actionIcon,
                        contentDescription = actionContentDescription,
                        tooltip = actionContentDescription,
                        enabled = selection.count > 0,
                        colors = actionColors
                    )
                    if (showReorderButton) {
                        ActionPillButton(
                            onClick = onEnterReorder,
                            icon = Icons.Outlined.Reorder,
                            contentDescription = reorderListLabel,
                            tooltip = reorderListLabel,
                            enabled = selection.count > 0
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

    val category = rememberWhileVisible(
        visible,
        CategoryDisplay(
            title = activeCategoryTitle,
            inReorderMode = isReorderMode,
            showEditActions = showEditActions
        )
    )

    MultiSelectShell(visible = visible, modifier = modifier) {
        AnimatedContent(
            targetState = category.inReorderMode,
            transitionSpec = Animations.fadeCrossfade(200),
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
                    val title = category.title
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ActionPillRow {
                        if (category.showEditActions) {
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
                        if (category.showEditActions) {
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
