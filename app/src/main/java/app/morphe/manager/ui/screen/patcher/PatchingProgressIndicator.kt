/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.screen.shared.rememberAccessibilityEnabled
import app.morphe.manager.ui.screen.shared.rememberProgressColor
import app.morphe.manager.ui.theme.MorpheBrandBlue
import app.morphe.manager.ui.theme.MorpheBrandTeal
import app.morphe.manager.util.isSlowPatchingDevice
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/** Wait between two frames of the wave. The next one the display shows makes it 30 a second at most. */
private val WAVE_FRAME_WAIT = 28.milliseconds

/** How long the wave takes to move on by its own length. */
private const val WAVE_PERIOD_NANOS = 1_000_000_000L

/** How long the wave takes to rise or to lie flat. */
private const val WAVE_RISE_MILLIS = 500

/** Degrees between two points that a wavy ring is drawn through. */
private const val RING_STEP_DEGREES = 2f

private const val TWO_PI = (2 * PI).toFloat()

private val BarStrokeWidth = 6.dp
private val BarWaveHeight = 5.dp
private val BarWavelength = 40.dp

/** Height of a bar, with room above and below its stroke for the wave. */
private val BarHeight = 16.dp

private val RingStrokeWidth = 10.dp
private val RingWaveHeight = 3.5.dp
private val RingWavelength = 40.dp

/** Gap between the progress and the track left of it. */
private val TrackGap = 4.dp

/** Part of the progress along which the wave is up, as it lies flat near both ends. */
private val WavyProgress = 0.1f..0.95f

/**
 * Patching progress along a line: a moving wave, or a plain bar in Morphe's colors on a slow device,
 * where drawing a wave takes CPU time from the patcher, see [isSlowPatchingDevice].
 *
 * @param accentColor Color of the app being patched, for the wave. Without one, or with one too dark
 *   or too light to read as a color, the wave takes the theme's primary.
 */
@Composable
fun PatchingProgressBar(
    progress: () -> Float,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val wave = rememberProgressWave(progress)
    val brush = rememberProgressBrush(accentColor) {
        Brush.horizontalGradient(listOf(MorpheBrandBlue, MorpheBrandTeal))
    }
    val path = remember { Path() }

    Canvas(
        modifier = modifier
            .progressSemantics(progress)
            .height(BarHeight)
    ) {
        val (shown, height, phase) = wave.value
        // Drawn from left to right, and mirrored as a whole where text runs the other way
        scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
            drawBar(path, shown, height, phase, brush, trackColor)
        }
    }
}

/**
 * [PatchingProgressBar] as a ring, sized by [modifier] to hold its own stats at the center, so both
 * patching modes show progress alike.
 */
@Composable
fun PatchingProgressRing(
    progress: () -> Float,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val wave = rememberProgressWave(progress)
    val brush = rememberProgressBrush(accentColor) {
        // Runs back into the first color, so a full ring has no seam
        Brush.sweepGradient(0f to MorpheBrandBlue, 0.9f to MorpheBrandTeal, 1f to MorpheBrandBlue)
    }
    val path = remember { Path() }

    Canvas(modifier = modifier.progressSemantics(progress)) {
        val (shown, height, phase) = wave.value
        // Starts at the top, and the gradient with it
        rotate(-90f) {
            drawRing(path, shown, height, phase, brush, trackColor)
        }
    }
}

private fun DrawScope.drawBar(
    path: Path,
    progress: Float,
    waveHeight: Float,
    phase: Float,
    brush: Brush,
    trackColor: Color
) {
    val strokeWidth = BarStrokeWidth.toPx()
    val cap = strokeWidth / 2
    val centerY = size.height / 2
    val start = cap
    val end = size.width - cap
    if (end <= start) return

    val head = (progress * size.width).coerceIn(start, end)
    val trackStart = if (progress > 0f) head + strokeWidth + TrackGap.toPx() else start
    if (trackStart < end) {
        drawLine(trackColor, Offset(trackStart, centerY), Offset(end, centerY), strokeWidth, StrokeCap.Round)
    }
    if (progress <= 0f) return

    if (waveHeight <= 0f || head <= start) {
        // A line with no length draws no cap, so it keeps some
        drawLine(brush, Offset(start, centerY), Offset(maxOf(head, start + 1f), centerY), strokeWidth, StrokeCap.Round)
        return
    }

    val amplitude = BarWaveHeight.toPx() * waveHeight
    val wavelength = BarWavelength.toPx()
    val step = wavelength / 20
    fun y(x: Float) = centerY + amplitude * sin(TWO_PI * (x / wavelength + phase))

    path.rewind()
    path.moveTo(start, y(start))
    var x = start + step
    while (x < head) {
        path.lineTo(x, y(x))
        x += step
    }
    path.lineTo(head, y(head))
    drawPath(path, brush, style = Stroke(strokeWidth, cap = StrokeCap.Round))
}

private fun DrawScope.drawRing(
    path: Path,
    progress: Float,
    waveHeight: Float,
    phase: Float,
    brush: Brush,
    trackColor: Color
) {
    val strokeWidth = RingStrokeWidth.toPx()
    // Leaves room for the wave at any height, so the ring keeps its size as the wave rises
    val radius = (size.minDimension - strokeWidth) / 2 - RingWaveHeight.toPx()
    if (radius <= 0f) return

    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val sweep = (progress * 360f).coerceIn(0f, 360f)

    // The caps of the progress and of the track reach into the gap from both sides
    val gap = if (progress > 0f) (strokeWidth + TrackGap.toPx()) / radius * 360f / TWO_PI else 0f
    val trackSweep = 360f - sweep - gap * 2
    if (trackSweep > 0f) {
        drawArc(trackColor, sweep + gap, trackSweep, useCenter = false, topLeft, arcSize, style = stroke)
    }
    if (progress <= 0f) return

    if (waveHeight <= 0f) {
        drawArc(brush, 0f, sweep, useCenter = false, topLeft, arcSize, style = stroke)
        return
    }

    val amplitude = RingWaveHeight.toPx() * waveHeight
    // Whole waves only, so the last one meets the first
    val waves = (TWO_PI * radius / RingWavelength.toPx()).roundToInt().coerceAtLeast(1)

    path.rewind()
    fun addPoint(degrees: Float) {
        val angle = degrees / 360f * TWO_PI
        val pointRadius = radius + amplitude * sin(waves * angle + TWO_PI * phase)
        val x = center.x + pointRadius * cos(angle)
        val y = center.y + pointRadius * sin(angle)
        if (degrees == 0f) path.moveTo(x, y) else path.lineTo(x, y)
    }

    var degrees = 0f
    while (degrees < sweep) {
        addPoint(degrees)
        degrees += RING_STEP_DEGREES
    }
    addPoint(sweep)
    drawPath(path, brush, style = stroke)
}

/** What a frame of the progress shows: the progress, how far the wave is up, and where along its length it is. */
private data class ProgressWave(val progress: Float, val height: Float, val phase: Float)

/**
 * The frames of the progress. A wave takes the progress over with its own frames, so it redraws 30
 * times a second at most however often the progress changes. Without a wave only the progress counts.
 */
@Composable
private fun rememberProgressWave(progress: () -> Float): State<ProgressWave> {
    val currentProgress by rememberUpdatedState(progress)
    // The wave never moves nor rises when accessibility services ask for less motion
    if (isSlowPatchingDevice || rememberAccessibilityEnabled()) {
        return remember { derivedStateOf { ProgressWave(currentProgress().coerceIn(0f, 1f), height = 0f, phase = 0f) } }
    }

    val isWavy by remember { derivedStateOf { currentProgress() in WavyProgress } }
    val height = animateFloatAsState(
        targetValue = if (isWavy) 1f else 0f,
        animationSpec = tween(WAVE_RISE_MILLIS),
        label = "progressWaveHeight"
    )
    val wave = remember {
        mutableStateOf(ProgressWave(currentProgress().coerceIn(0f, 1f), height.value, phase = 0f))
    }
    LaunchedEffect(Unit) {
        while (true) {
            // Waits for the display as well, which stands still while the screen is off or covered
            withFrameNanos { time ->
                wave.value = ProgressWave(
                    progress = currentProgress().coerceIn(0f, 1f),
                    height = height.value,
                    // A flat wave looks the same wherever it is, so it draws no frames for moving on
                    phase = if (height.value == 0f) 0f else (time % WAVE_PERIOD_NANOS) / WAVE_PERIOD_NANOS.toFloat()
                )
            }
            delay(WAVE_FRAME_WAIT)
        }
    }
    return wave
}

/** The app's color, see [rememberProgressColor], or [brandBrush] on a slow device. */
@Composable
private fun rememberProgressBrush(accentColor: Color?, brandBrush: () -> Brush): Brush {
    if (isSlowPatchingDevice) return remember { brandBrush() }
    val color = rememberProgressColor(accentColor)
    return remember(color) { SolidColor(color) }
}

/** Reports whole percents, as reporting every step of the progress would keep services busy. */
@Composable
private fun Modifier.progressSemantics(progress: () -> Float): Modifier {
    val currentProgress by rememberUpdatedState(progress)
    val percent by remember { derivedStateOf { (currentProgress().coerceIn(0f, 1f) * 100).toInt() } }
    return semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
    }
}
