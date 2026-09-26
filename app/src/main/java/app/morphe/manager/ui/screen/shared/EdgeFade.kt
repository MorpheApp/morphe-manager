/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** How far into a row its edge fades, enough to read as more to come without hiding a label. */
val EdgeFadeWidth = 24.dp

/**
 * Clips to bounds and fades the start and end edges, each in step with how many pixels of content
 * it hides, so an edge only fades while something is actually cut off behind it.
 *
 * Fades the content itself rather than laying a strip of one color over it, so it works on any
 * surface, tinted ones included, which no single gradient color could match.
 *
 * @param length How far in from each edge the fade reaches.
 * @param orientation Which edges fade: start and end of a row, or top and bottom of a column.
 */
fun Modifier.edgeFade(
    length: Dp,
    hiddenAtStart: () -> Float,
    hiddenAtEnd: () -> Float,
    orientation: Orientation = Orientation.Horizontal
): Modifier = clipToBounds()
    .graphicsLayer {
        // The fade masks what is already drawn, which takes a layer of its own
        compositingStrategy = if (hiddenAtStart() > 0f || hiddenAtEnd() > 0f) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        val extent = if (orientation == Orientation.Horizontal) size.width else size.height
        val fadeLength = length.toPx().coerceAtMost(extent / 2)
        if (fadeLength <= 0f) return@drawWithContent
        // A row starts on the right in a right-to-left layout, a column always at the top
        val startIsFirst = orientation == Orientation.Vertical || layoutDirection == LayoutDirection.Ltr
        fadeEdge(orientation, atFirst = startIsFirst, strength = hiddenAtStart() / fadeLength, length = fadeLength)
        fadeEdge(orientation, atFirst = !startIsFirst, strength = hiddenAtEnd() / fadeLength, length = fadeLength)
    }

/** [edgeFade] for a row that scrolls sideways, fading whichever end [scrollState] has more past. */
fun Modifier.horizontalScrollFade(scrollState: ScrollState, length: Dp = EdgeFadeWidth): Modifier =
    scrollFade(scrollState, length, Orientation.Horizontal)

/** [edgeFade] for a column that scrolls, fading whichever end [scrollState] has more past. */
fun Modifier.verticalScrollFade(scrollState: ScrollState, length: Dp = EdgeFadeWidth): Modifier =
    scrollFade(scrollState, length, Orientation.Vertical)

private fun Modifier.scrollFade(scrollState: ScrollState, length: Dp, orientation: Orientation) = edgeFade(
    length = length,
    hiddenAtStart = { scrollState.value.toFloat() },
    hiddenAtEnd = { (scrollState.maxValue - scrollState.value).toFloat() },
    orientation = orientation
)

/** Fades one edge: the left or top one when [atFirst], the right or bottom one otherwise. */
private fun DrawScope.fadeEdge(orientation: Orientation, atFirst: Boolean, strength: Float, length: Float) {
    val fraction = strength.coerceIn(0f, 1f)
    if (fraction == 0f) return
    val edge = Color.Black.copy(alpha = 1f - fraction)
    val colors = if (atFirst) listOf(edge, Color.Black) else listOf(Color.Black, edge)

    if (orientation == Orientation.Horizontal) {
        val left = if (atFirst) 0f else size.width - length
        drawRect(
            brush = Brush.horizontalGradient(colors, startX = left, endX = left + length),
            topLeft = Offset(left, 0f),
            size = Size(length, size.height),
            blendMode = BlendMode.DstIn
        )
    } else {
        val top = if (atFirst) 0f else size.height - length
        drawRect(
            brush = Brush.verticalGradient(colors, startY = top, endY = top + length),
            topLeft = Offset(0f, top),
            size = Size(size.width, length),
            blendMode = BlendMode.DstIn
        )
    }
}
