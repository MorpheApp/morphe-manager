/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.SelectableCard
import app.morphe.manager.ui.screen.shared.SwipeAction
import app.morphe.manager.ui.screen.shared.SwipeActionConfig
import app.morphe.manager.ui.screen.shared.SwipeableCardContainer
import app.morphe.manager.ui.screen.shared.rememberPatchesSwipeAction
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Swipe action taking a card off the list. */
@Composable
internal fun rememberHideSwipeAction(): SwipeActionConfig {
    val label = stringResource(R.string.hide)
    val container = MaterialTheme.colorScheme.errorContainer
    val content = MaterialTheme.colorScheme.onErrorContainer
    return remember(label, container, content) {
        SwipeActionConfig(icon = MaterialSymbols.Rounded.Visibility_off, label = label, containerColor = container, contentColor = content)
    }
}

/** Swipe action bringing a hidden app back onto the list. */
@Composable
internal fun rememberUnhideSwipeAction(): SwipeActionConfig {
    val label = stringResource(R.string.unhide)
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val content = MaterialTheme.colorScheme.onTertiaryContainer
    return remember(label, container, content) {
        SwipeActionConfig(icon = MaterialSymbols.Rounded.Visibility, label = label, containerColor = container, contentColor = content)
    }
}

/**
 * Single dynamic app card with horizontal swipe gestures:
 * - Swipe toward the start → reveal hide action
 * - Swipe toward the end   → reveal patches dialog
 *
 * On first appearance plays a one-time nudge hint animation.
 */
@Composable
internal fun DynamicAppCard(
    modifier: Modifier = Modifier,
    item: HomeAppItem,
    onAppClick: () -> Unit,
    onHide: () -> Unit,
    onShowPatches: () -> Unit,
    showGestureHint: Boolean,
    onGestureHintShown: () -> Unit,
    onUpdate: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onLongPress: () -> Unit = {},
    swipeActionsEnabled: Boolean = true,
    dragHandleModifier: Modifier? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val showHideDialog = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current
    val offsetX = remember { Animatable(0f) }

    // Hint animation: nudge toward the end then the start, once (only first card)
    LaunchedEffect(showGestureHint) {
        if (!showGestureHint) {
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        delay(800.milliseconds)
        val nudge = with(density) { 72.dp.toPx() }
        offsetX.animateTo(nudge,  tween(500, easing = FastOutSlowInEasing))
        offsetX.animateTo(0f,     tween(400, easing = FastOutSlowInEasing))
        delay(250.milliseconds)
        offsetX.animateTo(-nudge, tween(500, easing = FastOutSlowInEasing))
        offsetX.animateTo(0f,     tween(400, easing = FastOutSlowInEasing))
        onGestureHintShown()
    }

    val selectLabel = stringResource(R.string.accessibility_select_app)
    val moveUpLabel = stringResource(R.string.accessibility_move_up)
    val moveDownLabel = stringResource(R.string.accessibility_move_down)

    val hideAction = SwipeAction(rememberHideSwipeAction()) { showHideDialog.value = true }
    val patchesAction = SwipeAction(rememberPatchesSwipeAction(item.packageName), onShowPatches)

    Box(modifier = modifier.fillMaxWidth()) {
        SwipeableCardContainer(
            start = hideAction,
            end = patchesAction,
            enabled = swipeActionsEnabled && !isMultiSelectMode,
            offsetX = offsetX,
            accessibilityActions = buildList {
                // A long press is the only way into multi-select, and a gesture on its own is
                // nothing a screen reader can announce. In multi-select the card's own click
                // already toggles it, so the action would be a duplicate
                if (!isMultiSelectMode) {
                    add(CustomAccessibilityAction(selectLabel) { onLongPress(); true })
                }
                if (onMoveUp != null) {
                    add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
                }
                if (onMoveDown != null) {
                    add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
                }
            }
        ) {
            SelectableCard(
                isSelected = isSelected,
                isSelectionMode = isMultiSelectMode
            ) {
                HomeAppCard(
                    item = item,
                    onClick = onAppClick,
                    onUpdate = onUpdate,
                    // The drag handle takes the end of the card over while reordering
                    selected = isSelected.takeIf { isMultiSelectMode && dragHandleModifier == null },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    },
                    dragHandleModifier = dragHandleModifier
                )
            }
        }

        DialogHost(visible = showHideDialog.value) {
            HideAppDialog(
                item = item,
                onDismiss = { showHideDialog.value = false },
                onHide = {
                    onHide()
                    showHideDialog.value = false
                }
            )
        }
    }
}

/**
 * App card for hidden apps shown in search results.
 * - Swipe toward the start → unhide
 * - Swipe toward the end   → patches dialog
 *
 * Rendered at reduced opacity to signal the hidden state.
 */
@Composable
internal fun HiddenSearchAppCard(
    modifier: Modifier = Modifier,
    item: HomeAppItem,
    onUnhide: () -> Unit,
    onAppClick: () -> Unit,
    onShowPatches: () -> Unit,
    onUpdate: (() -> Unit)? = null
) {
    SwipeableCardContainer(
        start = SwipeAction(rememberUnhideSwipeAction(), onUnhide),
        end = SwipeAction(rememberPatchesSwipeAction(item.packageName), onShowPatches),
        modifier = modifier.graphicsLayer { alpha = 0.6f }
    ) {
        HomeAppCard(
            item = item,
            onClick = onAppClick,
            onUpdate = onUpdate
        )
    }
}
