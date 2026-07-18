/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
@OptIn(ExperimentalMaterial3Api::class)
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

/**
 * Configuration for a single button rendered inside [CardActionRow].
 * Set [destructive] to true for actions styled with the error container palette.
 */
@Immutable
data class CardAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false
)

/**
 * Wide action row anchored to the bottom of a card. Accepts one or two [CardAction]s.
 * A single action is centered at 50% width; two actions split the row equally.
 * Buttons are rendered as [ActionPillButton] with `large = true`.
 */
@Composable
fun CardActionRow(
    actions: List<CardAction>,
    modifier: Modifier = Modifier
) {
    require(actions.size in 1..2) { "CardActionRow supports 1 or 2 actions" }
    val hasBoth = actions.size == 2
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (hasBoth) Arrangement.spacedBy(8.dp) else Arrangement.Center
    ) {
        actions.forEach { action ->
            val colors = if (action.destructive) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            }
            ActionPillButton(
                onClick = action.onClick,
                icon = action.icon,
                contentDescription = action.label,
                label = action.label,
                enabled = action.enabled,
                large = true,
                modifier = if (hasBoth) Modifier.weight(1f) else Modifier.fillMaxWidth(0.5f),
                colors = colors
            )
        }
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
