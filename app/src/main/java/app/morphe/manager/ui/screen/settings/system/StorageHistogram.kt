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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.screen.shared.LocalDialogSecondaryTextColor
import app.morphe.manager.ui.screen.shared.LocalDialogTextColor
import app.morphe.manager.util.formatBytes
import app.morphe.manager.util.formatUsedFree

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
 * byte size changes. [deviceFreeBytes] renders as a subtitle above the bar for context only.
 */
@Composable
fun StorageHistogram(
    used: Long,
    deviceFreeBytes: Long,
    segments: List<StorageSegment>,
    modifier: Modifier = Modifier
) {
    val segmentsSum = remember(segments) { segments.sumOf { it.bytes }.coerceAtLeast(1L) }
    val visibleLegend = remember(segments) { segments.filter { it.bytes > 0 } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = formatUsedFree(used = used, free = deviceFreeBytes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = LocalDialogTextColor.current
        )

        HistogramLayout(
            bar = {
                HistogramBar(
                    segments = segments,
                    safeTotal = segmentsSum,
                    modifier = Modifier.fillMaxSize()
                )
            },
            legend = { HistogramLegend(visibleSegments = visibleLegend) }
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
    val trackColor = LocalDialogSecondaryTextColor.current.copy(alpha = 0.12f)

    // Captures the height assigned by [HistogramLayout] so segments can use absolute Dp values
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barHeightDp = with(LocalDensity.current) { barHeightPx.toDp() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
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
    visibleSegments: List<StorageSegment>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        visibleSegments.forEach { segment ->
            LegendItem(segment)
        }
    }
}

@Composable
private fun LegendItem(segment: StorageSegment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                color = LocalDialogTextColor.current,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatBytes(segment.bytes),
                style = MaterialTheme.typography.bodySmall,
                color = LocalDialogSecondaryTextColor.current
            )
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
