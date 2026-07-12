/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

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
import androidx.compose.ui.text.font.FontWeight
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
private val BAR_HEIGHT = 300.dp
private val BAR_WIDTH = 56.dp

/**
 * Vertical stacked bar with a legend column on the right. Segments animate up from zero
 * on first composition, and animate smoothly whenever their byte size changes.
 *
 * The bar always fills its full height with the composition of [segments] (proportional to
 * the sum of their bytes). [deviceFreeBytes] is only used to render the free-space subtitle
 * above the bar for context.
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistogramBar(
                segments = segments,
                safeTotal = segmentsSum,
                modifier = Modifier.width(BAR_WIDTH).height(BAR_HEIGHT)
            )
            HistogramLegend(
                visibleSegments = visibleLegend,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HistogramBar(
    segments: List<StorageSegment>,
    safeTotal: Long,
    modifier: Modifier = Modifier
) {
    val trackColor = LocalDialogSecondaryTextColor.current.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Render all segments (including zero-byte ones) so per-segment animation state
            // is remembered by position, not by presence
            segments.forEach { segment ->
                key(segment.key) {
                    AnimatedSegment(
                        targetFraction = (segment.bytes.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f),
                        color = segment.color
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedSegment(targetFraction: Float, color: Color) {
    // Start at zero so the first frame draws nothing, then animate to the real value
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }

    val fraction by animateFloatAsState(
        targetValue = if (animate) targetFraction else 0f,
        animationSpec = tween(durationMillis = SEGMENT_ANIMATION_MS, easing = EaseOutCubic),
        label = "storageSegmentFraction"
    )

    if (fraction <= 0f) return
    // Use an absolute Dp height so the parent Column's `Arrangement.Bottom` does not shrink
    // subsequent segments proportionally to the remaining space
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT * fraction)
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
    if (visibleSegments.isEmpty()) return
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
