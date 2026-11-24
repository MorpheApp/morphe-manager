/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val RingStrokeWidth = 10.dp

/**
 * Large wavy progress ring with rounded ends, sized by [modifier] to hold its own stats at the center.
 * The wave keeps moving while it runs, so it lies flat when accessibility services ask for less motion.
 *
 * @param progress The share done, or null while the total is unknown to spin indeterminately.
 * @param wavelength Length of one wave along the ring, scaled with the ring so its crests stay even.
 * @param accentColor Color of the app the progress belongs to. Without one, or with one too dark or
 *   too light to read as a color, the ring takes the theme's primary.
 * @param amplitude Height of the wave as a share of the most the ring allows, from flat at 0 to full at 1.
 * @param strokeWidth Thickness of the ring and its track, thinner for a ring small enough to sit
 *   around an icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyProgressRing(
    progress: (() -> Float)?,
    wavelength: Dp,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    amplitude: Float = 1f,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Dp = RingStrokeWidth
) {
    val stroke = with(LocalDensity.current) { Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round) }
    val waveHeight = rememberWaveHeight(amplitude)
    val color = rememberProgressColor(accentColor)

    if (progress != null) {
        CircularWavyProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            stroke = stroke,
            trackStroke = stroke,
            amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) * waveHeight },
            wavelength = wavelength
        )
    } else {
        CircularWavyProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            stroke = stroke,
            trackStroke = stroke,
            amplitude = waveHeight,
            wavelength = wavelength
        )
    }
}

/** Share of the wave shown: [amplitude], or flat when accessibility services ask for less motion. */
@Composable
private fun rememberWaveHeight(amplitude: Float): Float = if (rememberAccessibilityEnabled()) 0f else amplitude

/**
 * The app's color where it reads as one, or the theme's primary, eased since the color can land a
 * moment after the indicator or change between queued apps.
 */
@Composable
internal fun rememberProgressColor(accentColor: Color?): Color {
    val color by animateColorAsState(
        targetValue = usableAppAccent(accentColor) ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "wavyProgressColor"
    )
    return color
}
