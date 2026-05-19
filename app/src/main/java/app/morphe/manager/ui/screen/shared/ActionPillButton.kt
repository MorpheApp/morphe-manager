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
@Composable
fun ActionPillButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    label: String? = null,
    tooltip: String? = null,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors()
) {
    val height = if (large) 40.dp else 36.dp
    val minWidth = if (large) 80.dp else 72.dp
    val iconSize = if (large) 20.dp else 18.dp
    val textStyle = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall

    val button: @Composable () -> Unit = {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            shape = RoundedCornerShape(50),
            modifier = modifier
                .height(height)
                .widthIn(min = minWidth)
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
                        modifier = Modifier.size(iconSize)
                    )
                    Text(
                        text = label,
                        style = textStyle
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
