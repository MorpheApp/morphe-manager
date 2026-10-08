/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.screen.shared.dialogSecondaryTextColor
import app.morphe.manager.ui.screen.shared.dialogTextColor
import app.morphe.manager.ui.screen.shared.ShimmerText
import app.morphe.manager.util.formatBytes

/** A single stacked-bar segment. Order in the caller-provided list determines stacking order. */
data class StorageSegment(
    val key: String,
    val label: String,
    val bytes: Long,
    val color: Color
)

private const val SEGMENT_ANIMATION_MS = 700
private val BAR_MIN_HEIGHT = 200.dp
private val BAR_WIDTH = 56.dp
private val BAR_LEGEND_SPACING = 20.dp

/**
 * Stacked vertical bar of [segments] (proportional to their byte sum) with a legend on the
 * right. Segments animate up from zero on first composition and animate smoothly when their
 * byte size changes. The total they add up to is left to whatever heads the bar.
 *
 * Every segment keeps its row in the legend, an empty one dimmed, so the histogram holds one
 * height whatever the sizes turn out to be.
 *
 * @param loading Whether the sizes are still being read, which shows placeholders in their place.
 */
@Composable
fun StorageHistogram(
    segments: List<StorageSegment>,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    val segmentsSum = remember(segments) { segments.sumOf { it.bytes }.coerceAtLeast(1L) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HistogramLayout(
            bar = {
                HistogramBar(
                    segments = segments,
                    safeTotal = segmentsSum,
                    modifier = Modifier.fillMaxSize()
                )
            },
            legend = { HistogramLegend(segments = segments, loading = loading) }
        )
    }
}

/**
 * Places [bar] on the left and [legend] on the right, sizing the bar to match the legend's
 * natural height with a floor of [BAR_MIN_HEIGHT]. Height propagates one way: legend measures
 * first at unbounded height, then the bar fits into it. A plain `Row(IntrinsicSize.Max)` would
 * feedback-loop because the animated segment stack reports its current height as its intrinsic
 * max, preventing the row from ever shrinking.
 */
@Composable
private fun HistogramLayout(
    bar: @Composable () -> Unit,
    legend: @Composable () -> Unit
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) { constraints ->
        val barWidthPx = BAR_WIDTH.roundToPx()
        val spacingPx = BAR_LEGEND_SPACING.roundToPx()
        val minHeightPx = BAR_MIN_HEIGHT.roundToPx()
        val legendWidthPx = (constraints.maxWidth - barWidthPx - spacingPx).coerceAtLeast(0)

        val legendPlaceable = subcompose(SlotId.Legend, legend).first().measure(
            Constraints(
                minWidth = legendWidthPx,
                maxWidth = legendWidthPx,
                minHeight = 0,
                maxHeight = Constraints.Infinity
            )
        )

        val rowHeightPx = maxOf(legendPlaceable.height, minHeightPx)

        val barPlaceable = subcompose(SlotId.Bar, bar).first().measure(
            Constraints.fixed(barWidthPx, rowHeightPx)
        )

        layout(constraints.maxWidth, rowHeightPx) {
            barPlaceable.place(0, 0)
            legendPlaceable.place(
                x = barWidthPx + spacingPx,
                y = (rowHeightPx - legendPlaceable.height) / 2
            )
        }
    }
}

private enum class SlotId { Bar, Legend }

@Composable
private fun HistogramBar(
    segments: List<StorageSegment>,
    safeTotal: Long,
    modifier: Modifier = Modifier
) {
    // Captures the height assigned by [HistogramLayout] so segments can use absolute Dp values
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barHeightDp = with(LocalDensity.current) { barHeightPx.toDp() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(dialogSecondaryTextColor().copy(alpha = 0.12f))
            .onSizeChanged { barHeightPx = it.height }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Render every segment (even zero-byte) so animation state stays keyed by position
            segments.forEach { segment ->
                key(segment.key) {
                    AnimatedSegment(
                        targetFraction = (segment.bytes.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f),
                        barHeight = barHeightDp,
                        color = segment.color
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedSegment(targetFraction: Float, barHeight: Dp, color: Color) {
    // `animate` flag drives the entry animation: target is 0 on first frame, real value after
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }

    val fraction by animateFloatAsState(
        targetValue = if (animate) targetFraction else 0f,
        animationSpec = tween(durationMillis = SEGMENT_ANIMATION_MS, easing = EaseOutCubic),
        label = "storageSegmentFraction"
    )

    if (fraction <= 0f) return
    // Absolute Dp: `fillMaxHeight(fraction)` compounds under `Arrangement.Bottom` in Column
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight * fraction)
            .background(
                Brush.verticalGradient(
                    colors = listOf(color, color.copy(alpha = 0.82f))
                )
            )
    )
}

@Composable
private fun HistogramLegend(
    segments: List<StorageSegment>,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        segments.forEach { segment ->
            LegendItem(segment, loading)
        }
    }
}

@Composable
private fun LegendItem(segment: StorageSegment, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (loading || segment.bytes > 0) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(segment.color)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = segment.label,
                style = MaterialTheme.typography.bodyMedium,
                color = dialogTextColor(),
                fontWeight = FontWeight.Medium
            )
            val sizeStyle = MaterialTheme.typography.bodySmall
            if (loading) {
                // A line's height, so the row keeps the size the reading lands in
                ShimmerText(
                    widthFraction = 0.35f,
                    height = with(LocalDensity.current) { sizeStyle.lineHeight.toDp() }
                )
            } else {
                Text(
                    text = LocalContext.current.formatBytes(segment.bytes),
                    style = sizeStyle,
                    color = dialogSecondaryTextColor()
                )
            }
        }
    }
}

/** Fixed palette used for storage categories. Kept distinct across theme variants. */
object StorageColors {
    val OriginalApks = Color(0xFF4285F4)
    val PatchedApks = Color(0xFFAB47BC)
    val PatchBundles = Color(0xFF66BB6A)
    val Keystore = Color(0xFFFFB300)
    val AppData = Color(0xFF5C6BC0)
    val HttpCache = Color(0xFFFF7043)
    val InstallerShare = Color(0xFFEF5350)
    val PatcherWorkspace = Color(0xFF26C6DA)
    val Temporary = Color(0xFF78909C)
}
