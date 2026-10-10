/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.ThemeTraitsDefaults
import kotlinx.coroutines.launch

/**
 * A [ModalBottomSheet] that never overlaps the status bar.
 *
 * @param onDismissRequest   Called when the user dismisses the sheet.
 * @param modifier           Modifier applied to the sheet surface.
 * @param sheetState         Controls the sheet expand/collapse animation.
 * @param shape              Shape of the sheet (top corners).
 * @param containerColor     Background color of the sheet, the dialogs' own by default so the cards
 *                           and headers tinted over it read the same in both.
 * @param contentColor       Preferred content color inside the sheet.
 * @param scrimColor         Color of the scrim behind the sheet.
 * @param showDragHandle     Whether to show the drag handle pill. Default true.
 * @param content            Sheet content - passed directly to [ModalBottomSheet],
 *                           preserving nested-scroll behavior for inner lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    ),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveContainerColor = ThemeTraitsDefaults.surfaceColor(containerColor)
    val backProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // statusBarsPadding() on the modifier is what physically stops the sheet
        // surface from entering the status-bar zone at the window-layout level.
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = Defaults.SheetSideInset)
            .predictiveBackSlide(backProgress),
        sheetState = sheetState,
        shape = shape,
        containerColor = effectiveContainerColor,
        contentColor = contentColor,
        scrimColor = scrimColor,
        // Drawn with the content instead, inside the edge below
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        // The sheet's own predictive back shrinks the content harder than the surrounding surface,
        // leaving bare sheet below whatever list it holds, so back is handled below instead
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = true
        ),
        content = {
            // The gesture draws the whole sheet down, and letting go slides it out from there.
            // Registered ahead of the content, so back handlers the content adds still win
            PredictiveBackSlideHandler(backProgress) {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismissRequest()
                }
            }
            // Shared with dialogs, so what the sheet holds is drawn in the sheet's own content color
            CompositionLocalProvider(
                LocalDialogTextColor provides contentColor,
                LocalDialogSecondaryTextColor provides contentColor.copy(alpha = 0.7f)
            ) {
                // On the dialogs' background the sheet has nothing but an edge of its own to tell
                // it from the screen dimmed behind it. The sheet's modifier sits outside the offset
                // it slides in by, so the edge goes around the content, handle included
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardBorder(CardBorder.neutral, shape)
                ) {
                    if (showDragHandle) SheetDragHandle(contentColor, onDismissRequest)
                    content()
                }
            }
        }
    )
}

@Composable
private fun SheetDragHandle(contentColor: Color, onDismissRequest: () -> Unit) {
    val closeLabel = stringResource(R.string.close)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The sheet already stops short of the status bar, which leaves this at zero there. It
            // keeps the handle clear of the bar wherever the insets reach this far
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics {
                contentDescription = closeLabel
                dismiss { onDismissRequest(); true }
            },
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
