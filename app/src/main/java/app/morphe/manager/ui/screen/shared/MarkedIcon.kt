/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.util.contrastingContent
import app.morphe.manager.util.endEdgeX
import app.morphe.manager.util.isRtl
import app.morphe.manager.util.startEdgeX

/** The most a mark hangs past the corner of its icon, room a caller may need to leave for it. */
val IconMarkOverhang = 6.dp

/** Width of the gap cut out of the icon around a mark. */
private val IconMarkGap = 3.dp

/**
 * [content] in a square of [iconSize] with [mark] cut into its bottom end corner and [startMark]
 * into its bottom start one, each on a disc of [markColor] and left out while null. A mark grows
 * in and out as it comes and goes.
 *
 * @param markSize Diameter of a mark's disc, the gap around it aside.
 * @param markContentColor Color of a mark's own icon on its disc.
 * @param contentFraction Share of the square the icon covers, as [AppIconContentFraction] for an
 *   app's icon, so a mark hangs off the icon's own edge rather than the square's empty corner.
 */
@Composable
fun MarkedIcon(
    mark: ImageVector?,
    markColor: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    startMark: ImageVector? = null,
    markSize: Dp = iconSize * 0.4f - IconMarkGap * 2,
    markContentColor: Color = markColor.contrastingContent(),
    contentFraction: Float = 1f,
    content: @Composable () -> Unit
) {
    val cutSize = markSize + IconMarkGap * 2
    val overhang = IconMarkOverhang - iconSize * (1f - contentFraction) / 2f
    val endCorner = cornerMark(mark, label = "icon_end_mark")
    val startCorner = cornerMark(startMark, label = "icon_start_mark")

    Box(modifier = modifier.size(iconSize)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The gaps are cleared from the icon rather than painted over it, so they show
                // whatever lies behind. Composited apart only while there is a gap to clear
                .graphicsLayer {
                    val hasGap = endCorner.presence.value > 0f || startCorner.presence.value > 0f
                    compositingStrategy =
                        if (hasGap) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .drawWithContent {
                    drawContent()
                    val cutRadius = cutSize.toPx() / 2f
                    val inset = cutRadius - overhang.toPx()
                    // The end corner is the left one in RTL, and the start corner the right one
                    val rtl = layoutDirection.isRtl
                    val endX = endEdgeX(size.width, rtl, inset)
                    val startX = startEdgeX(size.width, rtl, inset)
                    val y = size.height - inset
                    clearMarkGap(endCorner.presence.value, cutRadius, Offset(endX, y))
                    clearMarkGap(startCorner.presence.value, cutRadius, Offset(startX, y))
                }
        ) {
            content()
        }
        endCorner.shown?.let {
            IconMark(
                icon = it,
                color = markColor,
                contentColor = markContentColor,
                size = markSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = overhang, y = overhang)
                    .markPresence(endCorner.presence)
                    .padding(IconMarkGap)
            )
        }
        startCorner.shown?.let {
            IconMark(
                icon = it,
                color = markColor,
                contentColor = markContentColor,
                size = markSize,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = -overhang, y = overhang)
                    .markPresence(startCorner.presence)
                    .padding(IconMarkGap)
            )
        }
    }
}

/** The mark a corner shows, the one on its way out included, and how far it has grown in. */
private class CornerMark(val shown: ImageVector?, val presence: State<Float>)

@Composable
private fun cornerMark(mark: ImageVector?, label: String): CornerMark {
    val transition = updateTransition(mark, label = label)
    // Read while drawing, so the mark grows without recomposing the icon under it
    val presence = transition.animateFloat(label = "${label}_presence") {
        if (it != null) 1f else 0f
    }
    // The mark on its way out is the one it was until then
    return CornerMark(transition.targetState ?: transition.currentState, presence)
}

/** Scales a mark to how far it has grown in, around its own center where its gap is cut. */
private fun Modifier.markPresence(presence: State<Float>): Modifier = graphicsLayer {
    scaleX = presence.value
    scaleY = presence.value
}

/** Clears the gap around a mark centered at [center], grown in as far as [scale]. */
private fun DrawScope.clearMarkGap(scale: Float, radius: Float, center: Offset) {
    if (scale <= 0f) return
    drawCircle(
        color = Color.Black,
        radius = radius * scale,
        center = center,
        blendMode = BlendMode.Clear
    )
}

/** [icon] on a disc of [color] and [size], the mark [MarkedIcon] cuts into an icon's corner. */
@Composable
private fun IconMark(
    icon: ImageVector,
    color: Color,
    size: Dp,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.75f)
        )
    }
}
