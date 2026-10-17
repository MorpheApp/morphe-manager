/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width each of [labels] takes on a single line in [style], measured again only when either changes. */
@Composable
fun rememberLabelWidths(labels: List<String>, style: TextStyle): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(labels, style, density, measurer) {
        labels.map { label ->
            with(density) { measurer.measure(label, style, maxLines = 1).size.width.toDp() }
        }
    }
}

/**
 * Whether every one of [labels], set in [style], fits on one line in an equal share of [width]
 * with [spacing] between the shares, once each has spent [inset] on everything but its label.
 */
@Composable
fun labelsFitSlots(labels: List<String>, style: TextStyle, width: Dp, spacing: Dp, inset: Dp): Boolean {
    val widths = rememberLabelWidths(labels, style)
    if (widths.isEmpty()) return false

    val labelWidth = (width - spacing * (widths.size - 1)) / widths.size - inset
    return labelWidth > 0.dp && widths.all { it <= labelWidth }
}
