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
import androidx.compose.ui.graphics.Brush
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
    leftProgress: Float,
    rightProgress: Float,
    leftConfig: SwipeActionConfig?,
    rightConfig: SwipeActionConfig?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Left edge
        if (leftConfig != null && leftProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterEnd)
                    .background(
                        Brush.horizontalGradient(
                            0f to leftConfig.containerColor.copy(alpha = 0f),
                            1f to leftConfig.containerColor.copy(alpha = 0.85f * leftProgress)
                        )
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .graphicsLayer { alpha = leftProgress },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = leftConfig.icon,
                        contentDescription = null,
                        tint = leftConfig.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = leftConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = leftConfig.contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Right edge
        if (rightConfig != null && rightProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            0f to rightConfig.containerColor.copy(alpha = 0.85f * rightProgress),
                            1f to rightConfig.containerColor.copy(alpha = 0f)
                        )
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .graphicsLayer { alpha = rightProgress },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = rightConfig.icon,
                        contentDescription = null,
                        tint = rightConfig.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = rightConfig.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = rightConfig.contentColor,
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
    onLeftSwipe: () -> Unit,
    onRightSwipe: () -> Unit,
    leftHaptic: Int = HapticFeedbackConstants.LONG_PRESS,
    rightHaptic: Int = HapticFeedbackConstants.VIRTUAL_KEY,
    enabled: Boolean = true,
    background: @Composable BoxScope.(leftProgress: Float, rightProgress: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Progress values for background reveal [0..1]
    val leftProgress by remember { derivedStateOf { (-offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }
    val rightProgress by remember { derivedStateOf { (offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }

    Box(modifier = modifier.fillMaxWidth()) {
        background(leftProgress, rightProgress)

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value }
                .then(
                    if (enabled) Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offsetX.value < -actionThresholdPx -> {
                                            view.performHapticFeedback(leftHaptic)
                                            offsetX.animateTo(0f, tween(200))
                                            onLeftSwipe()
                                        }
                                        offsetX.value > actionThresholdPx -> {
                                            view.performHapticFeedback(rightHaptic)
                                            offsetX.animateTo(0f, tween(200))
                                            onRightSwipe()
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
                                    val clamped = (offsetX.value + dragAmount)
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
 * - Swipe LEFT  → reveal hide action
 * - Swipe RIGHT → reveal patches dialog
 *
 * On first appearance plays a one-time nudge hint animation.
 */
@Composable
internal fun DynamicAppCard(
    modifier: Modifier = Modifier,
    item: HomeAppItem,
    isLoading: Boolean,
    hasUpdate: Boolean,
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

    // Hint animation: nudge right then left, once (only first card)
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
    val moveUpLabel = stringResource(R.string.accessibility_move_up)
    val moveDownLabel = stringResource(R.string.accessibility_move_down)
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val leftConfig = remember(hideLabel, errorContainer, onErrorContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.VisibilityOff,
            label = hideLabel,
            containerColor = errorContainer,
            contentColor = onErrorContainer
        )
    }
    val rightConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Extension,
            label = patchesLabel,
            containerColor = primaryContainer,
            contentColor = onPrimaryContainer
        )
    }

    Box(modifier = modifier.fillMaxWidth().semantics {
        customActions = buildList {
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
            onLeftSwipe = { showHideDialog.value = true },
            onRightSwipe = onShowPatches,
            enabled = swipeActionsEnabled && !isMultiSelectMode,
            background = { leftProgress, rightProgress ->
                SwipeBackground(
                    leftProgress = leftProgress,
                    rightProgress = rightProgress,
                    leftConfig = leftConfig,
                    rightConfig = rightConfig,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        ) {
            SelectableCard(
                isSelected = isSelected,
                isSelectionMode = isMultiSelectMode
            ) {
                Crossfade(
                    targetState = isLoading,
                    animationSpec = tween(300),
                    label = "app_card_crossfade_${item.packageName}"
                ) { loading ->
                    if (loading) {
                        AppLoadingCard(gradientColors = item.gradientColors)
                    } else {
                        if (item.installedApp != null) {
                            InstalledAppCard(
                                installedApp = item.installedApp,
                                packageInfo = item.packageInfo,
                                displayName = item.displayName,
                                gradientColors = item.gradientColors,
                                onClick = onAppClick,
                                hasUpdate = hasUpdate,
                                isAppDeleted = item.isDeleted,
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onLongPress()
                                }
                            )
                        } else {
                            AppButton(
                                packageName = item.packageName,
                                displayName = item.displayName,
                                packageInfo = item.packageInfo,
                                gradientColors = item.gradientColors,
                                onClick = onAppClick,
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onLongPress()
                                }
                            )
                        }
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
 * - Swipe LEFT  → Patches dialog
 * - Swipe RIGHT → Unhide
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

    val leftConfig = remember(unhideLabel, tertiaryContainer, onTertiaryContainer) {
        SwipeActionConfig(
            icon = Icons.Outlined.Visibility,
            label = unhideLabel,
            containerColor = tertiaryContainer,
            contentColor = onTertiaryContainer
        )
    }
    val rightConfig = remember(patchesLabel, primaryContainer, onPrimaryContainer) {
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
            onLeftSwipe = onUnhide,
            onRightSwipe = onShowPatches,
            leftHaptic = HapticFeedbackConstants.LONG_PRESS,
            rightHaptic = HapticFeedbackConstants.VIRTUAL_KEY,
            background = { leftProgress, rightProgress ->
                SwipeBackground(
                    leftProgress = leftProgress,
                    rightProgress = rightProgress,
                    leftConfig = leftConfig,
                    rightConfig = rightConfig,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        ) {
            if (item.installedApp != null) {
                InstalledAppCard(
                    installedApp = item.installedApp,
                    packageInfo = item.packageInfo,
                    displayName = item.displayName,
                    gradientColors = item.gradientColors,
                    onClick = onAppClick,
                    hasUpdate = item.hasUpdate,
                    isAppDeleted = item.isDeleted,
                    onLongClick = {}
                )
            } else {
                AppButton(
                    packageName = item.packageName,
                    displayName = item.displayName,
                    packageInfo = item.packageInfo,
                    gradientColors = item.gradientColors,
                    onClick = onAppClick,
                    onLongClick = {}
                )
            }
        }
    }
}
