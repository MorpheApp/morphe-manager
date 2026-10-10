/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Breathing Grid background - a grid of dots pulses in the sine waves of two sources that
 * wander slowly around the screen, their ripples crossing like those of two stones in water.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion a strong shockwave burst radiates from center, temporarily
 * expanding all dots before settling back.
 */
@Composable
fun GridBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.15f,
        context = context
    )

    val time = rememberAnimatedTime(speedMultiplier)

    // shockwaveProgress 0→1: a burst pulse radiates from center on completion
    val shockwaveProgress = rememberCompletionPulse(patchingCompleted, riseMillis = 1100, fallMillis = 500)

    Canvas(modifier = modifier.fillMaxSize()) {
        val t     = time.value
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val twoPi = 2f * PI.toFloat()
        val sw    = shockwaveProgress.value

        // Columns and rows follow the screen, so cells stay square in landscape and on tablets
        val spacing = GRID_SPACING_DP * density
        val cols  = (size.width  / spacing).roundToInt().coerceAtLeast(2) + 1
        val rows  = (size.height / spacing).roundToInt().coerceAtLeast(2) + 1
        val cellW = size.width  / (cols - 1).toFloat()
        val cellH = size.height / (rows - 1).toFloat()
        val maxDist = sqrt(size.width * size.width + size.height * size.height) * 0.5f

        // Two wave sources wander slowly around the screen. Their ripples cross and interfere, so
        // the pattern keeps reshaping instead of only ever spreading from the middle
        val sourceAX = size.width  * (0.5f + 0.32f * sin(t * twoPi / 23000f))
        val sourceAY = size.height * (0.5f + 0.30f * sin(t * twoPi / 31000f + 1.1f))
        val sourceBX = size.width  * (0.5f + 0.32f * sin(t * twoPi / 27000f + 2.4f))
        val sourceBY = size.height * (0.5f + 0.30f * cos(t * twoPi / 19000f + 0.4f))

        // Shockwave: a ring that expands outward - dots near the ring get a size boost
        val waveRadius   = sw * maxDist * 1.2f
        val waveWidth    = maxDist * 0.25f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val baseX = col * cellW + tiltX * 12f
                val baseY = row * cellH + tiltY * 12f

                // Distance from screen center - drives the shockwave and the edge dimming
                val dx = baseX - size.width  * 0.5f
                val dy = baseY - size.height * 0.5f
                val dist = sqrt(dx * dx + dy * dy)

                // Each source sends out its own continuous ripple, phase offset by distance so it
                // propagates outward, and the dot follows their sum
                val distA = sqrt((baseX - sourceAX) * (baseX - sourceAX) + (baseY - sourceAY) * (baseY - sourceAY))
                val distB = sqrt((baseX - sourceBX) * (baseX - sourceBX) + (baseY - sourceBY) * (baseY - sourceBY))
                val wave = 0.5f * (
                    sin(t * twoPi / 3800f - distA / density * 0.036f) +
                        sin(t * twoPi / 4300f - distB / density * 0.036f + 1.7f)
                )

                // Base dot radius oscillates with the wave
                val baseRadius = (1.7f + wave * 0.85f) * density

                // Shockwave: dots near the expanding ring get a strong size boost
                val distFromWave = kotlin.math.abs(dist - waveRadius)
                val shockBoost = if (sw > 0f && distFromWave < waveWidth) {
                    val localPhase = 1f - distFromWave / waveWidth
                    localPhase * localPhase * 2f * density * (1f - sw * 0.5f)
                } else 0f

                val finalRadius = (baseRadius + shockBoost).coerceAtLeast(0.27f * density)

                // Color cycles gently across the grid
                val colorPhase = (col + row) % 3
                val color = when (colorPhase) {
                    0    -> primaryColor
                    1    -> secondaryColor
                    else -> tertiaryColor
                }

                // Alpha: dims toward edges, brightens near shockwave ring
                val edgeDim   = (1f - dist / maxDist).coerceIn(0.55f, 1f)
                val baseAlpha = 0.30f + wave * 0.10f
                val shockAlpha = if (sw > 0f && distFromWave < waveWidth) {
                    (1f - distFromWave / waveWidth) * 0.18f
                } else 0f
                val finalAlpha = ((baseAlpha + shockAlpha) * edgeDim).coerceIn(0f, 0.50f)

                drawCircle(
                    color  = color.copy(alpha = finalAlpha),
                    radius = finalRadius,
                    center = Offset(baseX, baseY)
                )
            }
        }
    }
}

/** Distance between neighboring dots. */
private const val GRID_SPACING_DP = 40f
