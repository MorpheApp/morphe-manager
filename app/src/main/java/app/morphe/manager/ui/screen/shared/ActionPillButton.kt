package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Pill-shaped icon-only action button.
 */
@Composable
fun ActionPillButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    isCompact: Boolean = false,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .height(if (isCompact) 36.dp else 44.dp)
            .widthIn(min = if (isCompact) 72.dp else 96.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = if (isCompact) Modifier.size(20.dp) else Modifier
        )
    }
}
