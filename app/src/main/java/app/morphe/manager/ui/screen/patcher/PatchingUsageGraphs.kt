/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.ui.model.IoSample
import app.morphe.manager.ui.model.PatchProgressSource
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.screen.shared.WindowHeightSizeClass
import app.morphe.manager.ui.screen.shared.WindowWidthSizeClass
import app.morphe.manager.ui.screen.shared.rememberWindowSize

/** Slots a history graph spans, which fixes the time axis so readings scroll in from the right. */
private const val HISTORY_SLOTS = 60
private const val HISTORY_SLOTS_COMPACT = 30

/** Fraction above which a reading starts ramping towards the warning color. */
private const val WARN_RAMP_START = 0.7f

/** Floor under every bar, so a slot that was measured never looks like one that was not. */
private const val MINIMUM_BAR_FRACTION = 0.04f

/** Samples the heap color trails behind, so one spike does not flash the whole graph red. */
private const val COLOR_AVERAGE_SAMPLES = 3

private val BarCornerRadius = 2.dp

/** Ceiling for a core bar, which a device with only a couple of cores would otherwise blow past. */
private val CoreBarMaxWidth = 20.dp

/**
 * How far apart capped bars may stand, as a multiple of their width. Four cores given a quarter
 * of a landscape column each would sit far enough apart to read as scattered rather than as a row.
 */
private const val MAX_SLOT_TO_BAR_RATIO = 2f

/**
 * A history graph mostly holds low readings, and a full-height track behind each one buries them,
 * so there the track only hints at the grid. A core that is idle has nothing else to show for it.
 */
private const val HISTORY_TRACK_ALPHA = 0.12f
private const val CORE_TRACK_ALPHA = 0.3f

/** Sizes the panels are drawn at, which is what lets a stack of them fit a short window. */
private data class UsageMetrics(
    val graphHeight: Dp,
    val headlineSize: TextUnit,
    val contentPadding: Dp,
    val itemSpacing: Dp,
    val panelSpacing: Dp
)

/**
 * A phone on its side has to fit three stacked panels into the height a tablet gives one, so the
 * graphs shrink with the window rather than pushing the column past its bounds.
 */
@Composable
private fun usageMetrics(compact: Boolean): UsageMetrics {
    val windowSize = rememberWindowSize()

    // Side by side, where the panels are as wide as a third of the window lets them be
    if (compact) {
        return if (windowSize.widthSizeClass == WindowWidthSizeClass.Compact) {
            UsageMetrics(28.dp, 12.sp, 10.dp, 3.dp, 8.dp)
        } else {
            UsageMetrics(40.dp, 15.sp, 14.dp, 4.dp, 10.dp)
        }
    }

    // Stacked, where the height of the column is what has to be shared
    return when (windowSize.heightSizeClass) {
        WindowHeightSizeClass.Compact -> UsageMetrics(22.dp, 13.sp, 10.dp, 2.dp, 6.dp)
        WindowHeightSizeClass.Medium -> UsageMetrics(36.dp, 15.sp, 12.dp, 4.dp, 8.dp)
        WindowHeightSizeClass.Expanded -> UsageMetrics(48.dp, 16.sp, 14.dp, 5.dp, 10.dp)
    }
}

/**
 * Live resource graphs of the patcher run: heap, CPU cores and storage throughput.
 *
 * [compact] puts the panels next to each other for the phone layout, where they share a single
 * row; the landscape layout gives each panel a row of its own to fill the left column.
 */
@Composable
fun PatchingUsageGraphs(
    patchProgress: PatchProgressSource,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val heapSamples = patchProgress.heapSamples
    val coreLoads = patchProgress.cpuCoreLoads
    val ioSamples = patchProgress.ioSamples

    // The runtime reports its limit over the log, which the app's own heap stands in for until then
    val heapLimitMb = patchProgress.heapLimitMb.takeIf { it > 0 }
        ?: (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()

    val metrics = usageMetrics(compact)

    // CPU and storage are rates, so they only exist once a second poll has something to subtract.
    // Waiting for it brings the whole group in at once instead of heap first and the rest after.
    AnimatedVisibility(
        visible = heapSamples.size > 1,
        enter = Animations.expandFadeEnter,
        modifier = modifier
    ) {
        // Both histories sit together, and the per-core bars close the group rather than split it
        UsagePanelLayout(compact = compact, metrics = metrics) { panelModifier ->
            HeapUsagePanel(heapSamples, heapLimitMb, compact, metrics, panelModifier)

            if (ioSamples.isNotEmpty()) {
                IoUsagePanel(ioSamples, compact, metrics, panelModifier)
            }
            if (coreLoads.isNotEmpty()) {
                CpuUsagePanel(coreLoads, compact, metrics, panelModifier)
            }
        }
    }
}

/**
 * Arranges the panels along the axis that has room and hands each one the modifier that makes it
 * take an equal share of it.
 */
@Composable
private fun UsagePanelLayout(
    compact: Boolean,
    metrics: UsageMetrics,
    content: @Composable (panelModifier: Modifier) -> Unit
) {
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.panelSpacing)
        ) {
            content(Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(metrics.panelSpacing)
        ) {
            content(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HeapUsagePanel(
    samples: List<Int>,
    limitMb: Int,
    compact: Boolean,
    metrics: UsageMetrics,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val warnColor = MaterialTheme.colorScheme.error

    // Recomputed on every sample: once the history is full its size stops changing while its
    // contents keep moving, which nothing can be keyed on
    val fractions = samples.map {
        if (limitMb > 0) (it / limitMb.toFloat()).coerceIn(0f, 1f) else 0f
    }
    val current = fractions.lastOrNull() ?: 0f

    // Bars keep their real height, but their color follows a rolling average: a lone spike is
    // worth seeing, not worth painting the graph red over
    var rollingAverage = 0f
    val colorFractions = fractions.map { fraction ->
        rollingAverage =
            (rollingAverage * COLOR_AVERAGE_SAMPLES + fraction) / (COLOR_AVERAGE_SAMPLES + 1)
        rollingAverage
    }

    UsagePanel(
        label = stringResource(R.string.memory_usage),
        // The limit rides along in the detail, which the compact layout has no room for anyway
        headline = "${samples.lastOrNull() ?: 0} MB",
        detail = "${current.asPercent()} of $limitMb MB",
        // The dot reads the same average as the bars, so the two cannot disagree on the tint
        accentColor = lerp(accentColor, warnColor, warnRamp(colorFractions.lastOrNull() ?: 0f)),
        compact = compact,
        metrics = metrics,
        modifier = modifier
    ) {
        UsageHistoryBars(
            fractions = fractions,
            color = accentColor,
            warnColor = warnColor,
            compact = compact,
            height = metrics.graphHeight,
            warnFractions = colorFractions
        )
    }
}

@Composable
private fun CpuUsagePanel(
    coreLoads: List<Int>,
    compact: Boolean,
    metrics: UsageMetrics,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.tertiary
    val warnColor = MaterialTheme.colorScheme.error
    val average = remember(coreLoads) { coreLoads.average().toInt() }

    UsagePanel(
        label = stringResource(R.string.cpu_usage),
        headline = "$average%",
        detail = "${coreLoads.size} cores",
        accentColor = lerp(accentColor, warnColor, warnRamp(average / 100f)),
        compact = compact,
        metrics = metrics,
        modifier = modifier
    ) {
        CoreLoadBars(coreLoads, accentColor, warnColor, compact, metrics.graphHeight)
    }
}

@Composable
private fun IoUsagePanel(
    samples: List<IoSample>,
    compact: Boolean,
    metrics: UsageMetrics,
    modifier: Modifier = Modifier
) {
    // Storage has no limit to plot against, so the busiest sample on screen sets the scale.
    // That peak eventually scrolls out of the history, and rescaling every bar in one frame
    // looks like a burst of activity that never happened, so the scale itself is what moves.
    val peak by animateFloatAsState(
        targetValue = (samples.maxOfOrNull { it.totalKbPerSec } ?: 0).toFloat(),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "io_scale"
    )
    val fractions = samples.map {
        if (peak > 0f) (it.totalKbPerSec / peak).coerceIn(0f, 1f) else 0f
    }
    val current = samples.last()
    val accentColor = MaterialTheme.colorScheme.secondary

    UsagePanel(
        label = stringResource(R.string.storage_io_usage),
        headline = formatRate(current.totalKbPerSec),
        detail = "↓ ${formatRate(current.readKbPerSec)}  ↑ ${formatRate(current.writeKbPerSec)}",
        accentColor = accentColor,
        compact = compact,
        metrics = metrics,
        modifier = modifier
    ) {
        // A tall bar here means "the most so far" rather than "nearly out of headroom", so
        // there is no ceiling for it to redden against
        UsageHistoryBars(fractions, accentColor, warnColor = null, compact, metrics.graphHeight)
    }
}

/**
 * Shell every usage panel shares: an accent dot and title, the current reading, and the graph.
 * The secondary detail is dropped in the compact layout, where a panel only gets a third of a row.
 */
@Composable
private fun UsagePanel(
    label: String,
    headline: String,
    detail: String,
    accentColor: Color,
    compact: Boolean,
    metrics: UsageMetrics,
    modifier: Modifier = Modifier,
    graph: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = metrics.contentPadding,
                    vertical = metrics.contentPadding - 2.dp
                ),
            verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (!compact) {
                    Spacer(Modifier.weight(1f))

                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = metrics.headlineSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            graph()
        }
    }
}

/**
 * History of a metric as one bar per reading, filled from the right so the newest sample always
 * sits at the same edge no matter how long the run has been going.
 */
@Composable
private fun UsageHistoryBars(
    fractions: List<Float>,
    color: Color,
    warnColor: Color?,
    compact: Boolean,
    height: Dp,
    warnFractions: List<Float> = fractions
) {
    val slotCount = if (compact) HISTORY_SLOTS_COMPACT else HISTORY_SLOTS

    UsageBars(
        // Both lists are trimmed the same way so a bar keeps the color that belongs to it
        fractions = fractions.takeLast(slotCount),
        slotCount = slotCount,
        spacing = 1.dp,
        color = color,
        warnColor = warnColor,
        height = height,
        trackAlpha = HISTORY_TRACK_ALPHA,
        warnFractions = warnFractions.takeLast(slotCount)
    )
}

/**
 * Current load of every core as its own bar. Cores keep their slot across samples, so one that
 * stays pinned while the others idle is visible as such.
 */
@Composable
private fun CoreLoadBars(
    loads: List<Int>,
    color: Color,
    warnColor: Color,
    compact: Boolean,
    height: Dp
) {
    // Polling is slow enough that stepping straight to each reading reads as noise
    val fractions = loads.mapIndexed { core, load ->
        key(core) {
            animateFloatAsState(
                targetValue = (load / 100f).coerceIn(0f, 1f),
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "cpu_core_load"
            ).value
        }
    }

    UsageBars(
        fractions = fractions,
        slotCount = fractions.size,
        spacing = if (compact) 2.dp else 4.dp,
        color = color,
        warnColor = warnColor,
        height = height,
        trackAlpha = CORE_TRACK_ALPHA,
        // Four cores across a landscape column would be slabs rather than bars
        maxBarWidth = CoreBarMaxWidth
    )
}

/**
 * Bars drawn in a single canvas rather than a layout node apiece, which is what keeps a graph
 * that redraws every couple of seconds cheap.
 *
 * [fractions] are aligned to the right of [slotCount] slots, so a history that has not filled up
 * yet leaves its empty slots blank instead of stretching over them. A [warnColor] ramps the bars
 * towards it as they approach full; metrics with no ceiling to approach pass null. [maxBarWidth]
 * caps how wide a bar may grow when there are few of them to share the row, and a bar held under
 * its slot sits in the middle of it so the row stays evenly spread either way.
 *
 * [warnFractions] is what that ramp reads, for metrics whose color should follow something
 * steadier than the height of the individual bar.
 */
@Composable
private fun UsageBars(
    fractions: List<Float>,
    slotCount: Int,
    spacing: Dp,
    color: Color,
    warnColor: Color?,
    height: Dp,
    trackAlpha: Float,
    maxBarWidth: Dp? = null,
    warnFractions: List<Float> = fractions
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = trackAlpha)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (fractions.isEmpty() || slotCount <= 0) return@Canvas

        val gap = spacing.toPx()
        val evenSlot = size.width / slotCount
        val evenWidth = (evenSlot - gap).coerceAtLeast(1f)
        val barWidth = maxBarWidth?.toPx()?.let(evenWidth::coerceAtMost) ?: evenWidth

        // Bars that fill their slot keep the row they were given; capped ones stop spreading
        // once the space between them outgrows them, and the tighter row is centred instead
        val slotWidth = if (barWidth < evenWidth) {
            evenSlot.coerceAtMost(barWidth * MAX_SLOT_TO_BAR_RATIO)
        } else {
            evenSlot
        }
        val leadingOffset = (size.width - slotWidth * slotCount) / 2f
        // Whatever the bar does not use of its slot is split either side of it
        val barInset = (slotWidth - barWidth) / 2f

        val corner = CornerRadius(BarCornerRadius.toPx().coerceAtMost(barWidth / 2f))
        val minimumHeight = size.height * MINIMUM_BAR_FRACTION

        val firstSlot = slotCount - fractions.size

        fractions.forEachIndexed { index, fraction ->
            val left = leadingOffset + (firstSlot + index) * slotWidth + barInset

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = corner
            )

            val barHeight = (size.height * fraction.coerceIn(0f, 1f)).coerceAtLeast(minimumHeight)

            drawRoundRect(
                color = warnColor?.let {
                    lerp(color, it, warnRamp(warnFractions.getOrElse(index) { fraction }))
                } ?: color,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner
            )
        }
    }
}

/** Ramps from 0 to 1 across the top of the range, so only a genuinely loaded graph turns red. */
private fun warnRamp(fraction: Float) =
    ((fraction - WARN_RAMP_START) / (1f - WARN_RAMP_START)).coerceIn(0f, 1f)

private fun Float.asPercent() = "${(this * 100).toInt()}%"

private fun formatRate(kbPerSec: Int) = if (kbPerSec >= 1024) {
    "%.1f MB/s".format(kbPerSec / 1024f)
} else {
    "$kbPerSec KB/s"
}
