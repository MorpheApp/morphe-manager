/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A [ModalBottomSheet] that never overlaps the status bar.
 *
 * @param onDismissRequest   Called when the user dismisses the sheet.
 * @param modifier           Modifier applied to the sheet surface.
 * @param sheetState         Controls the sheet expand/collapse animation.
 * @param shape              Shape of the sheet (top corners).
 * @param containerColor     Background color of the sheet.
 * @param contentColor       Preferred content color inside the sheet.
 * @param scrimColor         Color of the scrim behind the sheet.
 * @param showDragHandle     Whether to show the drag handle pill. Default true.
 * @param content            Sheet content - passed directly to [ModalBottomSheet],
 *                           preserving nested-scroll behavior for inner lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorpheBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = contentColorFor(containerColor),
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // statusBarsPadding() on the modifier is what physically stops the sheet
        // surface from entering the status-bar zone at the window-layout level.
        modifier = modifier.statusBarsPadding(),
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        scrimColor = scrimColor,
        // The drag handle is rendered by ModalBottomSheet *above* the content area.
        // Because statusBarsPadding() only affects the sheet surface (not the drag-handle slot),
        // we add windowInsetsPadding(statusBars) to the handle Box so
        // it shifts down by exactly the status-bar height and stays visible below it.
        dragHandle = if (showDragHandle) {
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Push the handle below the status bar inside the drag-handle slot
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 12.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp),
                        shape = RoundedCornerShape(50),
                        color = contentColor.copy(alpha = 0.4f)
                    ) {}
                }
            }
        } else null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        content = content
    )
}
