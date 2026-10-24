/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.SelectableCard
import app.morphe.manager.ui.screen.shared.appAccentContent
import app.morphe.manager.ui.screen.shared.rememberAppColor
import app.morphe.manager.ui.screen.shared.usableAppAccent
import app.morphe.manager.util.isRtl
import app.morphe.manager.util.startToEndGradient
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
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

/** One side of a card's swipe: the action it reveals and what letting go past the threshold does. */
internal class SwipeAction(val config: SwipeActionConfig, val onTrigger: () -> Unit)

/** How far a card is dragged before letting go of it triggers the action uncovered. */
private val SwipeActionThreshold = 90.dp

/**
 * Swipe action opening an app's patches, in the app's own color where its sources declare one, as
 * its card and dialogs wear it, or the theme where they do not.
 */
@Composable
internal fun rememberPatchesSwipeAction(packageName: String): SwipeActionConfig {
    val label = stringResource(R.string.patches)
    val accent = usableAppAccent(rememberAppColor(packageName))
    val container = accent ?: MaterialTheme.colorScheme.primaryContainer
    val content = if (accent != null) appAccentContent(container) else MaterialTheme.colorScheme.onPrimaryContainer
    return remember(label, container, content) {
        SwipeActionConfig(icon = MaterialSymbols.Rounded.Extension, label = label, containerColor = container, contentColor = content)
    }
}

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
 * Semi-transparent edge a dragged card uncovers, fading its action in with [progress]. Dragging
 * toward the start uncovers the end edge, so [atEnd] is set for the action of that side.
 */
@Composable
private fun BoxScope.SwipeReveal(config: SwipeActionConfig, progress: Float, atEnd: Boolean) {
    val tint = config.containerColor.copy(alpha = 0.85f * progress)
    val clear = config.containerColor.copy(alpha = 0f)
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                startToEndGradient(
                    startColor = if (atEnd) clear else tint,
                    endColor = if (atEnd) tint else clear,
                    rtl = isRtl()
                )
            ),
        contentAlignment = if (atEnd) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .graphicsLayer { alpha = progress },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                tint = config.contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = config.label,
                style = MaterialTheme.typography.labelSmall,
                color = config.contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Card that swipes horizontally, uncovering the action of each side as it is dragged and running
 * it once let go past the threshold. A side without an action does not give at all.
 *
 * @param enabled Whether it swipes. Turning it off puts a dragged card back in place.
 * @param offsetX Where the card is dragged to, hoisted only by a card that moves it on its own too.
 */
@Composable
internal fun SwipeableCardContainer(
    start: SwipeAction?,
    end: SwipeAction?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    offsetX: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val actionThresholdPx = with(LocalDensity.current) { SwipeActionThreshold.toPx() }
    // The gesture outlives recompositions, while the card it acts on may have been updated since
    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)

    LaunchedEffect(enabled) {
        if (!enabled) offsetX.animateTo(0f, tween(200))
    }

    // The offset is held in layout coordinates (negative drags the card toward the start edge),
    // while pointer deltas and translationX are physical, so both are mirrored in RTL
    val rtl = isRtl()
    val directionSign = if (rtl) -1f else 1f

    // Progress values for background reveal [0..1]
    val startProgress by remember { derivedStateOf { (-offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }
    val endProgress by remember { derivedStateOf { (offsetX.value / actionThresholdPx).coerceIn(0f, 1f) } }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(Defaults.CornerLargeIncreased))
        ) {
            start?.takeIf { startProgress > 0.01f }?.let { SwipeReveal(it.config, startProgress, atEnd = true) }
            end?.takeIf { endProgress > 0.01f }?.let { SwipeReveal(it.config, endProgress, atEnd = false) }
        }

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value * directionSign }
                .then(
                    if (enabled) Modifier.pointerInput(rtl) {
                        val settle = spring<Float>(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                        val limit = actionThresholdPx * 1.5f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val (action, haptic) = when {
                                    offsetX.value < -actionThresholdPx ->
                                        currentStart to HapticFeedbackConstants.LONG_PRESS
                                    offsetX.value > actionThresholdPx ->
                                        currentEnd to HapticFeedbackConstants.VIRTUAL_KEY
                                    else -> null to null
                                }
                                scope.launch {
                                    if (action != null && haptic != null) {
                                        view.performHapticFeedback(haptic)
                                        offsetX.animateTo(0f, tween(200))
                                        action.onTrigger()
                                    } else {
                                        offsetX.animateTo(0f, settle)
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offsetX.animateTo(0f, settle) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val clamped = (offsetX.value + dragAmount * directionSign).coerceIn(
                                        if (currentStart != null) -limit else 0f,
                                        if (currentEnd != null) limit else 0f
                                    )
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

    Box(modifier = modifier.fillMaxWidth().semantics {
        customActions = buildList {
            // A long press is the only way into multi-select, and a gesture on its own is
            // nothing a screen reader can announce. In multi-select the card's own click
            // already toggles it, so the action would be a duplicate
            if (!isMultiSelectMode) {
                add(CustomAccessibilityAction(selectLabel) { onLongPress(); true })
            }
            if (swipeActionsEnabled) {
                for (action in listOf(hideAction, patchesAction)) {
                    add(CustomAccessibilityAction(action.config.label) { action.onTrigger(); true })
                }
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
            start = hideAction,
            end = patchesAction,
            enabled = swipeActionsEnabled && !isMultiSelectMode,
            offsetX = offsetX
        ) {
            SelectableCard(
                isSelected = isSelected,
                isSelectionMode = isMultiSelectMode
            ) {
                HomeAppCard(
                    item = item,
                    onClick = onAppClick,
                    // The drag handle takes the end of the card over while reordering
                    showStatusBadges = dragHandleModifier == null,
                    selected = isSelected.takeIf { isMultiSelectMode && dragHandleModifier == null },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    }
                )
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
                    imageVector = MaterialSymbols.Rounded.Drag_handle,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    onShowPatches: () -> Unit
) {
    SwipeableCardContainer(
        start = SwipeAction(rememberUnhideSwipeAction(), onUnhide),
        end = SwipeAction(rememberPatchesSwipeAction(item.packageName), onShowPatches),
        modifier = modifier.graphicsLayer { alpha = 0.6f }
    ) {
        HomeAppCard(
            item = item,
            onClick = onAppClick
        )
    }
}
