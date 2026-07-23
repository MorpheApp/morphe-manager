/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Badge style variants.
 */
enum class InfoBadgeStyle {
    Default,
    Primary,
    Success,
    Warning,
    Error;

    /**
     * Get container and content colors for this badge style.
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

/** Sizing metrics for a single [InfoBadge] variant. */
private data class BadgeMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Dp,
    val shapeRadius: Dp,
    val itemSpacing: Dp,
    val fillWidth: Boolean
)

private val CompactMetrics = BadgeMetrics(
    horizontalPadding = 8.dp,
    verticalPadding = 2.dp,
    iconSize = 14.dp,
    shapeRadius = 6.dp,
    itemSpacing = 8.dp,
    fillWidth = false
)

private val DefaultMetrics = BadgeMetrics(
    horizontalPadding = 12.dp,
    verticalPadding = 8.dp,
    iconSize = 20.dp,
    shapeRadius = 12.dp,
    itemSpacing = 8.dp,
    fillWidth = true
)

private val ExpandedMetrics = BadgeMetrics(
    horizontalPadding = 16.dp,
    verticalPadding = 16.dp,
    iconSize = 24.dp,
    shapeRadius = 12.dp,
    itemSpacing = 12.dp,
    fillWidth = true
)

/**
 * Info badge with optional icon.
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
    modifier: Modifier = Modifier,
    text: String,
    style: InfoBadgeStyle = InfoBadgeStyle.Default,
    icon: ImageVector? = null,
    isCompact: Boolean = false,
    isExpanded: Boolean = false,
    isCentered: Boolean = false
) {
    val (containerColor, contentColor) = style.colors()

    // Expanded takes precedence, then compact, then default
    val metrics = when {
        isExpanded -> ExpandedMetrics
        isCompact -> CompactMetrics
        else -> DefaultMetrics
    }

    val textStyle = if (isExpanded) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodySmall
    }

    val surfaceModifier = if (metrics.fillWidth && !isCentered) {
        modifier.fillMaxWidth()
    } else {
        modifier.wrapContentWidth()
    }

    // Add zero-width space so long tokens can break at "/" and "." \u2014 cached per text value.
    val breakableText = remember(text) {
        text.replace("/", "/\u200B").replace(".", ".\u200B")
    }

    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(metrics.shapeRadius),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding
            ),
            horizontalArrangement = if (isCentered) {
                Arrangement.spacedBy(metrics.itemSpacing, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(metrics.itemSpacing)
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                MorpheIcon(icon = it, tint = contentColor, size = metrics.iconSize)
            }
            Text(
                text = breakableText,
                style = textStyle,
                color = contentColor,
                textAlign = if (isCentered) TextAlign.Center else TextAlign.Start
            )
        }
    }
}

/**
 * Compact fully-rounded pill badge used inline in cards.
 */
@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    style: InfoBadgeStyle = InfoBadgeStyle.Default,
    containerColor: Color? = null,
    contentColor: Color? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val (defaultContainer, defaultContent) = style.colors()
    val bg = containerColor ?: defaultContainer
    val fg = contentColor ?: defaultContent
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
