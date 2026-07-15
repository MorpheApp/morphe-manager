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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Shared spring-based press-scale factor.
 */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    label: String = "press_scale"
): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = label
    )
    return scale
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
