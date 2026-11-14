/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Everything [MultiSelectBar] renders from state its own actions clear, kept together so it
 * can be frozen as a single value while the bar slides out. Anything that changes as a result
 * of using the bar belongs here rather than being read straight from a parameter.
 */
private data class MultiSelectDisplay(
    val count: Int,
    val total: Int,
    val onReorder: (() -> Unit)?,
    val actions: List<SelectionAction>
)

/**
 * Selection panel of a list whose items are long-pressed into a selection, which slides up while
 * they are being selected. Back leaves the selection.
 *
 * @param onReorder Leaves the selection for dragging the list into order, whose controls the page
 *   header then takes, see [ReorderHeaderActions]. Null while the list has no order to drag.
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
    onReorder: (() -> Unit)? = null
) {
    val selection = rememberWhileVisible(
        visible,
        MultiSelectDisplay(
            count = selectedCount,
            total = totalCount,
            onReorder = onReorder,
            actions = actions
        )
    )

    DockedPanel(visible = visible, modifier = modifier, onBack = onCancel) {
        SelectionActionBar(
            selectedCount = selection.count,
            totalCount = selection.total,
            onSelectAll = onSelectAll,
            onDeselectAll = onDeselectAll,
            onCancel = onCancel,
            onReorder = selection.onReorder,
            actions = selection.actions
        )
    }
}

/**
 * The handle a list item is dragged into a new order by, to be given the reorderable library's
 * `draggableHandle` through [modifier] so its state stays with the list.
 *
 * @param tint Color of the glyph, the card's own text color on one drawn in a color of its own.
 */
@Composable
internal fun DragHandle(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Drag_handle,
            contentDescription = stringResource(R.string.reorder_list),
            modifier = Modifier.size(24.dp),
            tint = tint
        )
    }
}
