/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.SelectableCard
import app.morphe.manager.util.isRtl
import app.morphe.manager.util.startToEndGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Data describing one side of a swipe action - icon, label, and colors. */
internal data class SwipeActionConfig(
    val icon: ImageVector,
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

/**
 * Semi-transparent background that reveals contextual action icons as the user drags the card.
 */
@Composable
internal fun SwipeBackground(
    startProgress: Float,
    endProgress: Float,
    startConfig: SwipeActionConfig?,
    endConfig: SwipeActionConfig?,
    modifier: Modifier = Modifier
) {
    val rtl = isRtl()

    Box(modifier = modifier) {
        // Dragging toward the start edge uncovers the end edge
        if (startConfig != null && startProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterEnd)
                    .background(
                        startToEndGradient(
                            startColor = startConfig.containerColor.copy(alpha = 0f),
                            endColor = startConfig.containerColor.copy(alpha = 0.85f * startProgress),
                            rtl = rtl
                        )
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .graphicsLayer { alpha = startProgress },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = startConfig.icon,
                        contentDescription = null,
                        tint = startConfig.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = startConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = startConfig.contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Dragging toward the end edge uncovers the start edge
        if (endConfig != null && endProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .background(
                        startToEndGradient(
                            startColor = endConfig.containerColor.copy(alpha = 0.85f * endProgress),
                            endColor = endConfig.containerColor.copy(alpha = 0f),
                            rtl = rtl
                        )
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .graphicsLayer { alpha = endProgress },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = endConfig.icon,
                        contentDescription = null,
                        tint = endConfig.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = endConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = endConfig.contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Shared container that handles horizontal swipe gestures and drives the [SwipeBackground] reveal animation.
 */
@Composable
internal fun SwipeableCardContainer(
    modifier: Modifier = Modifier,
    offsetX: Animatable<Float, AnimationVector1D>,
    actionThresholdPx: Float,
    onSwipeToStart: () -> Unit,
    onSwipeToEnd: () -> Unit,
    startHaptic: Int = HapticFeedbackConstants.LONG_PRESS,
    endHaptic: Int = HapticFeedbackConstants.VIRTUAL_KEY,
    enabled: Boolean = true,
    background: @Composable BoxScope.(startProgress: Float, endProgress: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // The offset is held in layout coordinates (negative drags the card toward the start edge),
    // while pointer deltas and translationX are physical, so both are mirrored in RTL
    val rtl = isRtl()
    val directionSign = if (rtl) -1f else 1f

    // Progress values for background reveal [0..1]
    val startProgress by remember { derivedStateOf { (-offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }
    val endProgress by remember { derivedStateOf { (offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }

    Box(modifier = modifier.fillMaxWidth()) {
        background(startProgress, endProgress)

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value * directionSign }
                .then(
                    if (enabled) Modifier.pointerInput(rtl) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offsetX.value < -actionThresholdPx -> {
                                            view.performHapticFeedback(startHaptic)
                                            offsetX.animateTo(0f, tween(200))
                                            onSwipeToStart()
                                        }
                                        offsetX.value > actionThresholdPx -> {
                                            view.performHapticFeedback(endHaptic)
                                            offsetX.animateTo(0f, tween(200))
                                            onSwipeToEnd()
                                        }
                                        else -> offsetX.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val clamped = (offsetX.value + dragAmount * directionSign)
                                        .coerceIn(-actionThresholdPx * 1.5f, actionThresholdPx * 1.5f)
                                    offsetX.snapTo(clamped)
                                }
                            }
                        )
                    } else Modifier
                )
        ) {
            content()
        }
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
    isLoading: Boolean,
    onAppClick: () -> Unit,
    onHide: () -> Unit,
    onShowPatches: () -> Unit,
    showGestureHint: Boolean,
    onGestureHintShown: () -> Unit,
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

    val actionThresholdPx = with(density) { 90.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    // When entering multi-select mode snap card back to center (no swipe visible)
    LaunchedEffect(isMultiSelectMode) {
        if (isMultiSelectMode) offsetX.animateTo(0f, tween(200))
    }

    // Hint animation: nudge toward the end then the start, once (only first card)
    LaunchedEffect(showGestureHint, isLoading) {
        if (!showGestureHint || isLoading) {
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

    val hideLabel = stringResource(R.string.hide)
    val patchesLabel = stringResource(R.string.patches)
    val selectLabel = stringResource(R.string.accessibility_select_app)
    val moveUpLabel = stringResource(R.string.accessibility_move_up)
    val moveDownLabel = stringResource(R.string.accessibility_move_down)
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val startConfig = remember(hideLabel, errorContainer, onErrorContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.VisibilityOff,
            label = hideLabel,
            containerColor = errorContainer,
            contentColor = onErrorContainer
        )
    }
    val endConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    Box(modifier = modifier.fillMaxWidth().semantics {
        customActions = buildList {
            // A long press is the only way into multi-select, and a gesture on its own is
            // nothing a screen reader can announce. In multi-select the card's own click
            // already toggles it, so the action would be a duplicate
            if (!isMultiSelectMode) {
                add(CustomAccessibilityAction(selectLabel) { onLongPress(); true })
            }
            if (swipeActionsEnabled) {
                add(CustomAccessibilityAction(hideLabel) { showHideDialog.value = true; true })
                add(CustomAccessibilityAction(patchesLabel) { onShowPatches(); true })
            }
            if (onMoveUp != null) {
                add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
            }
            if (onMoveDown != null) {
                add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
            }
        }
    }) {
        SwipeableCardContainer(
            offsetX = offsetX,
            actionThresholdPx = actionThresholdPx,
            onSwipeToStart = { showHideDialog.value = true },
            onSwipeToEnd = onShowPatches,
            enabled = swipeActionsEnabled && !isMultiSelectMode,
            background = { startProgress, endProgress ->
                SwipeBackground(
                    startProgress = startProgress,
                    endProgress = endProgress,
                    startConfig = startConfig,
                    endConfig = endConfig,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        ) {
            SelectableCard(
                isSelected = isSelected,
                isSelectionMode = isMultiSelectMode,
                // The drag handle already sits in the corner the check badge would land in
                showCheckmark = dragHandleModifier == null
            ) {
                Crossfade(
                    targetState = isLoading,
                    animationSpec = tween(300),
                    label = "app_card_crossfade_${item.id}"
                ) { loading ->
                    if (loading) {
                        AppLoadingCard(gradientColors = item.gradientColors)
                    } else {
                        HomeAppCard(
                            item = item,
                            onClick = onAppClick,
                            // The drag handle takes the end of the card over while reordering
                            showStatusBadges = dragHandleModifier == null,
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onLongPress()
                            }
                        )
                    }
                }
            }
        }

        if (dragHandleModifier != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        if (showHideDialog.value) {
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
    onShowPatches: () -> Unit
) {
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 90.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    val patchesLabel = stringResource(R.string.patches)
    val unhideLabel = stringResource(R.string.unhide)
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    val startConfig = remember(unhideLabel, tertiaryContainer, onTertiaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Visibility,
            label = unhideLabel,
            containerColor = tertiaryContainer,
            contentColor = onTertiaryContainer
        )
    }
    val endConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = 0.6f }
    ) {
        SwipeableCardContainer(
            offsetX = offsetX,
            actionThresholdPx = actionThresholdPx,
            onSwipeToStart = onUnhide,
            onSwipeToEnd = onShowPatches,
            startHaptic = HapticFeedbackConstants.LONG_PRESS,
            endHaptic = HapticFeedbackConstants.VIRTUAL_KEY,
            background = { startProgress, endProgress ->
                SwipeBackground(
                    startProgress = startProgress,
                    endProgress = endProgress,
                    startConfig = startConfig,
                    endConfig = endConfig,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        ) {
            HomeAppCard(
                item = item,
                onClick = onAppClick
            )
        }
    }
}
