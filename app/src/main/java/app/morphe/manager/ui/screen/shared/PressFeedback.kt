/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp

/** How far a control sinks while held. Shared so every button gives the same push-back. */
const val PressedScale = 0.97f

/** Share of its rounding a button's corners keep while held, the press of an expressive button. */
private const val PressedCornerScale = 0.5f

/**
 * Shared spring-based press-scale factor.
 */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = PressedScale,
    label: String = "press_scale"
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()

    return animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = label
    )
}

/**
 * Sinks the node while [interactionSource] reports a press. The animated value is read inside
 * the layer, so holding a button repaints it instead of recomposing it on every frame.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = PressedScale,
    label: String = "press_scale"
): Modifier {
    val scale = rememberPressScale(interactionSource, enabled, pressedScale, label)

    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * How far into a press [interactionSource] is, from 0 at rest to 1 held, sprung so a quick tap
 * still plays out. Drives the press feedback that changes a button's own geometry.
 */
@Composable
fun rememberPressProgress(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    label: String = "press_progress"
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()

    return animateFloatAsState(
        targetValue = if (enabled && isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = label
    )
}

/**
 * [shape] with its corners rounding down as [interactionSource] reports a press, so the button
 * answers the touch with its outline as well. A shape without corners to round is kept as it is.
 */
@Composable
fun rememberPressShape(
    interactionSource: InteractionSource,
    shape: Shape,
    enabled: Boolean = true
): Shape {
    val progress by rememberPressProgress(interactionSource, enabled, label = "press_shape")
    return remember(shape, progress) { shape.pressed(progress) }
}

/** [this] shape [progress] of the way into a press, see [rememberPressShape]. */
fun Shape.pressed(progress: Float): Shape {
    if (this !is CornerBasedShape || progress == 0f) return this
    val scale = lerp(1f, PressedCornerScale, progress)
    return copy(
        topStart = ScaledCornerSize(topStart, scale),
        topEnd = ScaledCornerSize(topEnd, scale),
        bottomEnd = ScaledCornerSize(bottomEnd, scale),
        bottomStart = ScaledCornerSize(bottomStart, scale)
    )
}

/** [base] scaled by [scale], so a percentage corner keeps following the size of its shape. */
private data class ScaledCornerSize(val base: CornerSize, val scale: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = base.toPx(shapeSize, density) * scale
}

/**
 * Wraps [onClick] so it fires a [HapticFeedbackConstants.VIRTUAL_KEY] pulse first. The
 * returned lambda is remembered against [onClick] so repeated recompositions keep the
 * same reference.
 */
@Composable
fun rememberHapticClick(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return remember(view, onClick) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }
}
