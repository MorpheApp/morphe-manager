/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Rings background - concentric stroke circles with parallax effect.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion each ring group surges outward in radius and fades - staggered by
 * group index - then eases back to normal.
 */
@Composable
fun RingsBackground(
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
        sensitivity = 0.3f,
        context = context,
        coroutineScope = coroutineScope
    )

    // Ring configurations - defined once, positions oscillate via sin() each frame
    val ringConfigs = remember {
        listOf(
            RingConfig(0.2f,  0.2f,  0.3f,  0.25f, 9000,  8000, listOf(140f, 190f, 240f), 0.8f),
            RingConfig(0.85f, 0.15f, 0.8f,  0.2f,  10000, 7500, listOf(130f, 180f),       0.6f),
            RingConfig(0.5f,  0.5f,  0.55f, 0.55f, 8500,  9500, listOf(110f, 160f, 210f), 0.5f),
            RingConfig(0.15f, 0.75f, 0.2f,  0.8f,  7000,  8000, listOf(150f, 200f),       0.7f),
            RingConfig(0.8f,  0.85f, 0.85f, 0.8f,  8800,  7600, listOf(120f, 170f, 220f), 0.6f),
            RingConfig(0.75f, 0.4f,  0.8f,  0.45f, 9200,  8400, listOf(135f, 185f),       0.4f)
        )
    }

    val time = rememberAnimatedTime(speedMultiplier)

    // burstProgress 0→1: each ring group expands radius and fades, staggered by group index.
    // Snaps back to 0f after completion so rings return to normal state.
    val burstProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
            )
            // Smooth return - rings ease back to normal radius and alpha
            burstProgress.animateTo(
                targetValue   = 0f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t     = time.value
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val twoPi = 2f * PI.toFloat()
        val bp    = burstProgress.value

        ringConfigs.forEachIndexed { index, config ->
            // Each ring oscillates between start/end using sin - mirrors the original Reverse tween
            val halfX = (config.endX - config.startX) / 2f
            val halfY = (config.endY - config.startY) / 2f
            val cx = config.startX + halfX + halfX * sin(t * twoPi / config.durationX)
            val cy = config.startY + halfY + halfY * sin(t * twoPi / config.durationY)

            val parallaxStrength = config.depth * 50f
            val center = Offset(
                size.width  * cx + tiltX * parallaxStrength,
                size.height * cy + tiltY * parallaxStrength
            )

            // Select color based on index
            val baseColor = when (index % 3) {
                0    -> primaryColor
                1    -> secondaryColor
                else -> tertiaryColor
            }

            // Stagger burst: group 0 starts immediately, last group starts at bp=0.4
            val groupDelay = index / (ringConfigs.size - 1).toFloat() * 0.4f
            val localBp    = ((bp - groupDelay) / (1f - groupDelay)).coerceIn(0f, 1f)
            // Radius surges then fades; alpha fades out during burst
            val radiusScale = if (bp > 0f) 1f + localBp * 1.8f else 1f
            val burstAlpha  = if (bp > 0f) (1f - localBp).coerceIn(0f, 1f) else 1f

            // Draw multiple concentric rings per group
            config.radii.forEachIndexed { ringIndex, radius ->
                val alpha = when (ringIndex) {
                    0    -> 0.14f
                    1    -> 0.10f
                    2    -> 0.07f
                    else -> 0.06f
                }
                val strokeWidth = when (ringIndex) {
                    0    -> 6f
                    1    -> 5f
                    else -> 4f
                }
                drawCircle(
                    color  = baseColor.copy(alpha = alpha * burstAlpha),
                    radius = radius * radiusScale,
                    center = center,
                    style  = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

private data class RingConfig(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationX: Int,
    val durationY: Int,
    val radii: List<Float>,
    val depth: Float // Depth for parallax effect
)
