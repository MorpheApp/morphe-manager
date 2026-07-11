/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Wraps [content] with the shared selection affordances used by the home app grid and the
 * saved-APK dialog: an animated check badge in the top-right corner when [isSelected] is true,
 * and a dim overlay when [isSelectionMode] is active but this card is not selected.
 */
@Composable
fun SelectableCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    checkmarkContentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "check_scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (isSelectionMode && !isSelected) 0.55f else 1f,
        animationSpec = tween(200),
        label = "card_alpha"
    )

    Box(modifier = modifier) {
        Box(modifier = Modifier.graphicsLayer { alpha = cardAlpha }) {
            content()
        }

        // Animated checkmark badge - top-right corner
        if (checkScale > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .graphicsLayer { scaleX = checkScale; scaleY = checkScale }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = checkmarkContentDescription,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
