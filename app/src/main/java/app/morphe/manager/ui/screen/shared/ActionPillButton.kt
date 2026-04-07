package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Pill-shaped action button with an icon, optional text label, and optional long-press tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPillButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    label: String? = null,
    tooltip: String? = null,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
    val button: @Composable () -> Unit = {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .height(36.dp)
                .widthIn(min = 72.dp)
        ) {
            if (label != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (tooltip != null) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(tooltip) } },
            state = rememberTooltipState()
        ) {
            button()
        }
    } else {
        button()
    }
}
