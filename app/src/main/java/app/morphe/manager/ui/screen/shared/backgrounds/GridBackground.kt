/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Breathing Grid background - a grid of dots pulses in concentric sine waves
 * radiating from the screen centre, like ripples on water.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion a strong shockwave burst radiates from centre, temporarily
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
    val coroutineScope = rememberCoroutineScope()

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.15f,
        context = context,
        coroutineScope = coroutineScope
    )

    val time = rememberAnimatedTime(speedMultiplier)

    // shockwaveProgress 0→1: a burst pulse radiates from centre on completion
    val shockwaveProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            shockwaveProgress.snapTo(0f)
            shockwaveProgress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            shockwaveProgress.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t     = time.value
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val twoPi = 2f * PI.toFloat()
        val sw    = shockwaveProgress.value

        val cols  = 11
        val rows  = 20
        val cellW = size.width  / (cols - 1).toFloat()
        val cellH = size.height / (rows - 1).toFloat()
        val maxDist = sqrt(size.width * size.width + size.height * size.height) * 0.5f

        // Shockwave: a ring that expands outward - dots near the ring get a size boost
        val waveRadius   = sw * maxDist * 1.2f
        val waveWidth    = maxDist * 0.25f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val baseX = col * cellW + tiltX * 12f
                val baseY = row * cellH + tiltY * 12f

                // Distance from screen centre - drives the ripple phase offset
                val dx = baseX - size.width  * 0.5f
                val dy = baseY - size.height * 0.5f
                val dist = sqrt(dx * dx + dy * dy)

                // Continuous ripple wave: phase offset by distance so wave propagates outward
                val ripplePhase = dist * 0.012f
                val wave = sin(t * twoPi / 3800f - ripplePhase)

                // Base dot radius oscillates with the wave
                val baseRadius = 5.0f + wave * 2.5f

                // Shockwave: dots near the expanding ring get a strong size boost
                val distFromWave = kotlin.math.abs(dist - waveRadius)
                val shockBoost = if (sw > 0f && distFromWave < waveWidth) {
                    val localPhase = 1f - distFromWave / waveWidth
                    localPhase * localPhase * 6f * (1f - sw * 0.5f)
                } else 0f

                val finalRadius = (baseRadius + shockBoost).coerceAtLeast(0.8f)

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
