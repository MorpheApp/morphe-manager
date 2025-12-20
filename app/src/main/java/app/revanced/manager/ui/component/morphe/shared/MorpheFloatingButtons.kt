package app.revanced.manager.ui.component.morphe.shared

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * Unified Morphe Floating Action Button component with morphing animation and haptic feedback.
 *
 * @param onClick Action to perform on click.
 * @param icon The icon to display.
 * @param contentDescription Accessibility description.
 * @param modifier Modifier for the FAB.
 * @param containerColor Custom container color (default: theme primaryContainer).
 * @param contentColor Custom content color (default: theme onPrimaryContainer).
 */
@Composable
fun MorpheFloatingButtons(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val fabSize = 64.dp
    val iconSize = 24.dp
    val view = LocalView.current

    // Track press state
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate corner radius - stays circular
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) (fabSize * 0.4f) else (fabSize / 2),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "corner_radius"
    )

    // Animate scale
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "scale"
    )

    // Animate elevation
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "elevation"
    )

    Surface(
        onClick = {
            // Trigger haptic feedback on click
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        modifier = modifier
            .size(fabSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        shadowElevation = elevation,
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    contentColor.copy(alpha = 0.3f),
                    contentColor.copy(alpha = 0.1f)
                )
            )
        ),
        interactionSource = interactionSource
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = contentColor
            )
        }
    }
}
