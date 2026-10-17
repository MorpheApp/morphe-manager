/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

/**
 * Lays [items] out [columns] to a row, padding a short last row so every tile keeps one width.
 * Tiles in a row share the height of the tallest, so one whose label wraps does not stand out.
 */
@Composable
fun <T> OptionGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    item: @Composable (item: T, modifier: Modifier) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                row.forEach { item(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A single choice among a few [items], as one connected group whose buttons widen under a press,
 * as an expressive button group's do. The picked tile rounds itself out, see [SelectionTile].
 */
@Composable
fun <T> OptionGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    item: @Composable (item: T, shape: CornerBasedShape, interactionSource: MutableInteractionSource, modifier: Modifier) -> Unit
) {
    ButtonGroupRow(modifier = modifier) {
        items.forEachIndexed { index, option ->
            val interactionSource = remember { MutableInteractionSource() }
            item(
                option,
                ConnectedShapes.Pill.inRow(index, items.size),
                interactionSource,
                Modifier.buttonGroupItem(interactionSource)
            )
        }
    }
}

/**
 * Standard icon-based option card for appearance settings.
 * Used for backgrounds, themes, and other icon-based selections.
 *
 * @param compact Shorter, with a smaller icon, for a row of choices that sits inline on the tab
 *        rather than in a picker of its own.
 */
@Composable
fun ModernIconOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    shape: CornerBasedShape = SelectionTileShape,
    interactionSource: MutableInteractionSource? = null
) {
    val windowSize = rememberWindowSize()
    val iconSize = when {
        compact -> 22.dp
        windowSize.widthSizeClass == WindowWidthSizeClass.Compact -> 32.dp
        windowSize.widthSizeClass == WindowWidthSizeClass.Medium -> 36.dp
        else -> 40.dp
    }

    // A floor rather than a fixed height, so a label under a large font scale grows the tile
    val cardHeight = when {
        compact -> if (isLandscape()) 72.dp else 64.dp
        else -> if (isLandscape()) 92.dp else 80.dp
    }

    SelectionTile(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        stateDescription = stringResource(
            if (selected) R.string.selected else R.string.not_selected
        ),
        modifier = modifier.heightIn(min = cardHeight),
        shape = shape,
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Defaults.SpaceMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                // Selected tiles carry what the tile resolved against its own fill, the rest keep
                // the accent they are drawn in
                tint = if (selected) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.primary
                }.copy(alpha = if (enabled) 1f else 0.5f),
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(Defaults.SpaceExtraSmall))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.onSurface
                }.copy(alpha = if (enabled) 1f else 0.5f),
                maxLines = 2, // Allow 2 lines for text wrapping
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.2
            )
        }
    }
}
