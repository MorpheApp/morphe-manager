package app.revanced.manager.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.consumeHorizontalScroll(scrollState: ScrollState): Modifier =
    pointerInput(scrollState) {
        detectDragGestures { change, dragAmount ->
            val deltaX = dragAmount.x
            if (deltaX != 0f) {
                change.consumePositionChange()
                scrollState.dispatchRawDelta(-deltaX)
            }
        }
    }
