package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Badge style variants
 */
enum class InfoBadgeStyle {
    Default,
    Primary,
    Success,
    Warning,
    Error;

    /**
     * Get container and content colors for this badge style
     */
    @Composable
    fun colors(): Pair<Color, Color> = when (this) {
        Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Success -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Warning -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Default -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Info badge with optional icon
 *
 * @param text Badge text content
 * @param style Visual style of the badge
 * @param icon Optional icon to display before text
 * @param isCompact Whether to use compact sizing (smaller padding and icon)
 * @param isExpanded Whether to use expanded variant (larger padding, centered content)
 * @param isCentered Whether to center content horizontally within the badge
 * @param modifier Modifier to be applied to the badge
 */
@Composable
fun InfoBadge(
    text: String,
    style: InfoBadgeStyle = InfoBadgeStyle.Default,
    icon: ImageVector? = null,
    isCompact: Boolean = false,
    isExpanded: Boolean = false,
    isCentered: Boolean = false,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = style.colors()

    // Determine sizing based on variant
    val horizontalPadding = when {
        isExpanded -> 16.dp
        isCompact -> 8.dp
        else -> 12.dp
    }

    val verticalPadding = when {
        isExpanded -> 16.dp
        isCompact -> 2.dp
        else -> 8.dp
    }

    val iconSize = when {
        isExpanded -> 24.dp
        isCompact -> 14.dp
        else -> 20.dp
    }

    val shapeRadius = when {
        isExpanded -> 12.dp
        isCompact -> 6.dp
        else -> 12.dp
    }

    val surfaceModifier = if (isCompact && !isExpanded) {
        modifier.wrapContentWidth()
    } else {
        modifier.fillMaxWidth()
    }

    val textStyle = if (isExpanded) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodySmall
    }

    val horizontalArrangement = if (isCentered) {
        Arrangement.spacedBy(if (isExpanded) 12.dp else 8.dp, Alignment.CenterHorizontally)
    } else {
        Arrangement.spacedBy(if (isExpanded) 12.dp else 8.dp)
    }

    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(shapeRadius),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                MorpheIcon(
                    icon = it,
                    tint = contentColor,
                    size = iconSize
                )
            }
            Text(
                // Add zero-width space, an invisible character that allows line breaks at this position
                text = text.replace("/", "/\u200B").replace(".", ".\u200B"),
                style = textStyle,
                color = contentColor,
                textAlign = if (isCentered) TextAlign.Center else TextAlign.Start
            )
        }
    }
}
