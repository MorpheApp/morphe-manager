/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

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
import app.morphe.manager.util.isRtl
import app.morphe.manager.util.startToEndGradient
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.launch

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
 * it once let go past the threshold. A side without an action does not give at all. A screen
 * reader, which has no gesture to offer, gets the actions in the card's own menu instead.
 *
 * @param enabled Whether it swipes. Turning it off puts a dragged card back in place.
 * @param offsetX Where the card is dragged to, hoisted only by a card that moves it on its own too.
 * @param accessibilityActions What else the card offers a screen reader beside its swipe actions.
 */
@Composable
internal fun SwipeableCardContainer(
    modifier: Modifier = Modifier,
    start: SwipeAction? = null,
    end: SwipeAction? = null,
    enabled: Boolean = true,
    offsetX: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
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

    val swipeActions = if (enabled) listOfNotNull(start, end) else emptyList()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                customActions = accessibilityActions + swipeActions.map { action ->
                    CustomAccessibilityAction(action.config.label) { action.onTrigger(); true }
                }
            }
    ) {
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
