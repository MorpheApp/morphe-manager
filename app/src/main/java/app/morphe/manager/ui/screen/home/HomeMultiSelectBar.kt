/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import org.koin.compose.koinInject

/**
 * Reorder mode of a [MultiSelectBar]: whether it is on, and what its pills do. The mode is
 * entered through one of the bar's actions.
 */
internal class MultiSelectReorder(
    val isActive: Boolean,
    val onSave: () -> Unit,
    val onReset: () -> Unit,
    val onCancel: () -> Unit
)

/**
 * Everything [MultiSelectBar] renders from state its own actions clear, kept together so it
 * can be frozen as a single value while the bar slides out. Anything that changes as a result
 * of using the bar belongs here rather than being read straight from a parameter.
 */
private data class MultiSelectDisplay(
    val count: Int,
    val total: Int,
    val reorder: MultiSelectReorder?,
    val actions: List<SelectionAction>
)

/**
 * Home screen's selection panel, which slides up while cards are being selected and turns into
 * the reorder panel while [reorder] is on. Back leaves whichever of the two is showing, the
 * reorder without saving.
 */
@Composable
internal fun MultiSelectBar(
    selectedCount: Int,
    totalCount: Int,
    visible: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCancel: () -> Unit,
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
    reorder: MultiSelectReorder? = null
) {
    val messages = koinInject<UserMessages>()
    val reorderDone = stringResource(R.string.reorder_done)
    val resetOrderDone = stringResource(R.string.reset_order_done)

    val selection = rememberWhileVisible(
        visible,
        MultiSelectDisplay(
            count = selectedCount,
            total = totalCount,
            reorder = reorder,
            actions = actions
        )
    )

    DockedPanel(
        visible = visible,
        modifier = modifier,
        onBack = { if (reorder?.isActive == true) reorder.onCancel() else onCancel() }
    ) {
        AnimatedContent(
            targetState = selection.reorder?.isActive == true,
            transitionSpec = Animations.fadeCrossfade(200),
            label = "multibar_mode"
        ) { inReorder ->
            val activeReorder = selection.reorder
            if (inReorder && activeReorder != null) {
                ReorderPanel(
                    onDone = messages.confirming(reorderDone, activeReorder.onSave),
                    onReset = messages.confirming(resetOrderDone, activeReorder.onReset),
                    onCancel = activeReorder.onCancel
                )
            } else {
                SelectionActionBar(
                    selectedCount = selection.count,
                    totalCount = selection.total,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    onCancel = onCancel,
                    actions = selection.actions
                )
            }
        }
    }
}

/** What [CategoryActionBar] renders, frozen as one value while the bar slides out. */
private data class CategoryDisplay(
    val title: String?,
    val inReorderMode: Boolean,
    val showEditActions: Boolean
)

/**
 * Slide-up panel for the long-pressed category or source header, which turns into the reorder
 * panel while the headers are being dragged into a new order. A source group only offers
 * reordering: its name and its apps come from the source, not from the user.
 *
 * @param onBack Closes the panel from either mode, dropping a reorder that was not saved.
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showEditActions: Boolean = true
) {
    val cancelLabel = stringResource(android.R.string.cancel)
    val renameLabel = stringResource(R.string.rename)
    val reorderListLabel = stringResource(R.string.reorder_list)
    val deleteLabel = stringResource(R.string.delete)

    val category = rememberWhileVisible(
        visible,
        CategoryDisplay(
            title = activeCategoryTitle,
            inReorderMode = isReorderMode,
            showEditActions = showEditActions
        )
    )

    DockedPanel(visible = visible, modifier = modifier, onBack = onBack) {
        AnimatedContent(
            targetState = category.inReorderMode,
            transitionSpec = Animations.fadeCrossfade(200),
            label = "category_bar_mode"
        ) { inReorder ->
            if (inReorder) {
                ReorderPanel(onDone = onExitReorder)
            } else {
                Column {
                    PanelHeader(title = { category.title?.let { PanelTitle(text = it) } }) {
                        TitleAction(
                            icon = Icons.Outlined.Close,
                            contentDescription = cancelLabel,
                            onClick = onCancel,
                            style = TitleActionStyle.Neutral
                        )
                    }
                    PanelActions(
                        actions = buildList {
                            if (category.showEditActions) {
                                add(SelectionAction(Icons.Outlined.Edit, renameLabel, onRename))
                            }
                            add(SelectionAction(Icons.Outlined.Reorder, reorderListLabel, onEnterReorder))
                            if (category.showEditActions) {
                                add(
                                    SelectionAction(
                                        icon = Icons.Outlined.Delete,
                                        label = deleteLabel,
                                        onClick = onDelete,
                                        tone = ActionTone.Destructive
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Drag hint over the ways out of a reorder: [onDone] keeps the new order, [onReset] goes back to
 * the default one, and [onCancel] drops the changes. A reorder without the last two offers only
 * the first.
 */
@Composable
private fun ReorderPanel(
    onDone: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    val reorderListHint = stringResource(R.string.reorder_list_hint)
    val doneLabel = stringResource(R.string.done)
    val resetOrderLabel = stringResource(R.string.reset_order)
    val cancelLabel = stringResource(android.R.string.cancel)

    Column {
        PanelHeader(title = { PanelTitle(text = reorderListHint) }) {
            if (onCancel != null) {
                TitleAction(
                    icon = Icons.Outlined.Close,
                    contentDescription = cancelLabel,
                    onClick = onCancel,
                    style = TitleActionStyle.Neutral
                )
            }
        }
        PanelActions(
            actions = listOfNotNull(
                SelectionAction(
                    icon = Icons.Outlined.Check,
                    label = doneLabel,
                    onClick = onDone,
                    tone = ActionTone.Primary
                ),
                onReset?.let {
                    SelectionAction(
                        icon = Icons.Outlined.Restore,
                        label = resetOrderLabel,
                        onClick = it,
                        tone = ActionTone.Destructive
                    )
                }
            )
        )
    }
}
