package app.revanced.manager.ui.component.morphe.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard clickable card for Morphe UI
 */
@Composable
fun MorpheClickableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alpha: Float = 0.5f,
    cornerRadius: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    ) {
        content()
    }
}

/**
 * Standard non-clickable card for Morphe UI
 */
@Composable
fun MorpheCard(
    modifier: Modifier = Modifier,
    alpha: Float = 0.5f,
    cornerRadius: Dp = 12.dp,
    color: Color? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = color ?: MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    ) {
        content()
    }
}

/**
 * Transparent card with outline only
 */
@Composable
fun MorpheOutlinedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Transparent
    ) {
        content()
    }
}
