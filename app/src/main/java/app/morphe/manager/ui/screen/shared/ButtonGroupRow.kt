/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/** Share of its width a pressed button of a group gains from its neighbors. */
private const val PressedWidthRatio = 0.15f

/**
 * Row of a button group, its [buttonGroupItem] buttons equally wide and as tall as the tallest. A
 * pressed one widens at its neighbors' expense, as an expressive button group answers a press.
 */
@Composable
fun ButtonGroupRow(
    modifier: Modifier = Modifier,
    spacing: Dp = ButtonGroupDefaults.ConnectedSpaceBetween,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.maxWidth, 0) {}

        val items = measurables.map { it.parentData as? ButtonGroupItemNode }
        val spacingPx = spacing.roundToPx()
        val fixed = items.map { it?.width?.roundToPx() }
        val sharing = measurables.indices.filter { fixed[it] == null }
        val room = (constraints.maxWidth - spacingPx * (measurables.size - 1) - fixed.sumOf { it ?: 0 })
            .coerceAtLeast(0)

        // Spread the remainder so the widths add up exactly
        val widths = IntArray(measurables.size) { fixed[it] ?: 0 }
        sharing.forEachIndexed { position, index ->
            widths[index] = room / sharing.size + if (position < room % sharing.size) 1 else 0
        }

        // A neighbor's press never cuts into the content, so no label is ever cut short
        val floor = IntArray(measurables.size) { index ->
            items[index]?.minWidth?.roundToPx()
                ?: minOf(widths[index], measurables[index].maxIntrinsicWidth(constraints.maxHeight))
        }
        val press = FloatArray(measurables.size) { (items[it]?.press?.invoke() ?: 0f).coerceAtLeast(0f) }
        widenPressed(widths, press, floor)

        val height = measurables.indices.maxOf { measurables[it].minIntrinsicHeight(widths[it]) }
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixed(widths[index], height))
        }

        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + spacingPx
            }
        }
    }
}

/**
 * Marks a button of a [ButtonGroupRow], widened by presses on its own [interactionSource]. [width]
 * fixes its share, [minWidth] bounds a neighbor's press in place of the width of its content.
 */
@Composable
fun Modifier.buttonGroupItem(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    width: Dp? = null,
    minWidth: Dp? = null
): Modifier {
    val press = rememberPressProgress(interactionSource, enabled, label = "button_group_press")
    val amount = remember(press) { { press.value } }
    return this then ButtonGroupItemElement(amount, width, minWidth)
}

private data class ButtonGroupItemElement(
    val press: () -> Float,
    val width: Dp?,
    val minWidth: Dp?
) : ModifierNodeElement<ButtonGroupItemNode>() {
    override fun create() = ButtonGroupItemNode(press, width, minWidth)

    override fun update(node: ButtonGroupItemNode) {
        node.press = press
        node.width = width
        node.minWidth = minWidth
    }

    // The lambda itself tells the inspector nothing, the value it reads right now does
    override fun InspectorInfo.inspectableProperties() {
        name = "buttonGroupItem"
        properties["press"] = press()
        properties["width"] = width
        properties["minWidth"] = minWidth
    }
}

private class ButtonGroupItemNode(
    var press: () -> Float,
    var width: Dp?,
    var minWidth: Dp?
) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = this@ButtonGroupItemNode
}

/**
 * Widens each button by [PressedWidthRatio] of its width at full [press], taking that room from
 * the buttons either side of it, none of which drops below its [floor]. The total stays the same.
 */
internal fun widenPressed(widths: IntArray, press: FloatArray, floor: IntArray) {
    press.forEachIndexed { index, amount ->
        if (amount == 0f) return@forEachIndexed
        val neighbors = listOf(index - 1, index + 1).filter { it in widths.indices }
        if (neighbors.isEmpty()) return@forEachIndexed
        val wanted = (widths[index] * PressedWidthRatio * amount).roundToInt()
        neighbors.forEach { neighbor ->
            val cut = minOf(wanted / neighbors.size, (widths[neighbor] - floor[neighbor]).coerceAtLeast(0))
            widths[neighbor] -= cut
            widths[index] += cut
        }
    }
}
