/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

    val buttonModifier = Modifier
        .height(height)
        .widthIn(min = minWidth)

    val button: @Composable (Modifier) -> Unit = { outerModifier ->
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            shape = RoundedCornerShape(50),
            modifier = outerModifier.then(buttonModifier)
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
                        style = textStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }

    if (tooltip != null) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(tooltip) } },
            state = rememberTooltipState(),
            modifier = modifier
        ) {
            button(modifier)
        }
    } else {
        button(modifier)
    }
}

private enum class ActionPillRowSlot { Natural, Compressed }

/**
 * Row that lays out its [ActionPillButton] children at their natural width and centers them.
 * If the natural total overflows the available width, all pills are compressed equally to fit.
 */
@Composable
fun ActionPillRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val spacingPx = spacing.roundToPx()
        val looseConstraints = constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth)

        val naturalMeasurables = subcompose(ActionPillRowSlot.Natural) { content() }
        if (naturalMeasurables.isEmpty()) {
            return@SubcomposeLayout layout(constraints.maxWidth, 0) {}
        }

        val naturalPlaceables = naturalMeasurables.map { it.measure(looseConstraints) }
        val n = naturalPlaceables.size
        val totalSpacing = spacingPx * (n - 1)
        val naturalWidth = naturalPlaceables.sumOf { it.width } + totalSpacing

        val finalPlaceables = if (naturalWidth <= constraints.maxWidth) {
            naturalPlaceables
        } else {
            val itemWidth = ((constraints.maxWidth - totalSpacing) / n).coerceAtLeast(0)
            val itemConstraints = constraints.copy(minWidth = itemWidth, maxWidth = itemWidth)
            val compressed = subcompose(ActionPillRowSlot.Compressed) { content() }
            compressed.map { it.measure(itemConstraints) }
        }

        val contentWidth = finalPlaceables.sumOf { it.width } + totalSpacing
        val height = finalPlaceables.maxOfOrNull { it.height } ?: 0
        val xStart = ((constraints.maxWidth - contentWidth) / 2).coerceAtLeast(0)

        layout(constraints.maxWidth, height) {
            var x = xStart
            finalPlaceables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + spacingPx
            }
        }
    }
}
