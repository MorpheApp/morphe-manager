package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Pill-shaped action button
 */
@Composable
fun ActionPillButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 96.dp)
    ) {
        Icon(icon, contentDescription)
    }
}
