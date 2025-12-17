package app.revanced.manager.ui.component.morphe.shared

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unified Morphe Floating Action Button component with morphing animation.
 * Supports regular and small sizes, with optional badge and custom icon size.
 *
 * @param onClick Action to perform on click.
 * @param icon The icon to display.
 * @param contentDescription Accessibility description.
 * @param modifier Modifier for the FAB.
 * @param isSmall Whether to use smaller size (default: false).
 * @param showBadge Whether to show a badge (default: false).
 * @param iconSize Size of the icon (default: 24.dp for regular, 20.dp for small).
 * @param containerColor Custom container color (default: theme primaryContainer).
 * @param contentColor Custom content color (default: theme onPrimaryContainer).
 */
@Composable
fun MorpheFloatingButtons(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isSmall: Boolean = false,
    showBadge: Boolean = false,
    iconSize: Dp = if (isSmall) 20.dp else 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val fabSize = if (isSmall) 48.dp else 64.dp

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
        onClick = onClick,
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
            if (showBadge) {
                BadgedBox(
                    badge = {
                        Badge(
                            modifier = Modifier.size(10.dp),
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    }
                ) {
                    Icon(icon, contentDescription, Modifier.size(iconSize), tint = contentColor)
                }
            } else {
                Icon(icon, contentDescription, Modifier.size(iconSize), tint = contentColor)
            }
        }
    }
}
