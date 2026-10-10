/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Rings background - groups of concentric stroke circles that ripple outward like rings on water,
 * each group at its own pace, with parallax effect.
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

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f,
        context = context
    )

    // Ring configurations - defined once, positions oscillate via sin() each frame
    val ringConfigs = remember {
        listOf(
            RingConfig(0.2f,  0.2f,  0.3f,  0.25f, 9000,  8000, listOf(47f, 63f, 80f), 0.8f),
            RingConfig(0.85f, 0.15f, 0.8f,  0.2f,  10000, 7500, listOf(43f, 60f),      0.6f),
            RingConfig(0.5f,  0.5f,  0.55f, 0.55f, 8500,  9500, listOf(37f, 53f, 70f), 0.5f),
            RingConfig(0.15f, 0.75f, 0.2f,  0.8f,  7000,  8000, listOf(50f, 67f),      0.7f),
            RingConfig(0.8f,  0.85f, 0.85f, 0.8f,  8800,  7600, listOf(40f, 57f, 73f), 0.6f),
            RingConfig(0.75f, 0.4f,  0.8f,  0.45f, 9200,  8400, listOf(45f, 62f),      0.4f)
        )
    }

    val time = rememberAnimatedTime(speedMultiplier)

    // burstProgress 0→1: each ring group expands radius and fades, staggered by group index,
    // then eases back so rings return to normal
    val burstProgress = rememberCompletionPulse(patchingCompleted, riseMillis = 1100, fallMillis = 450)

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

            // The rings of a group ripple outward like rings on water: each one is born at the
            // innermost radius, widens and thins out to the outermost, and fades as the next one
            // takes its place. Every group ripples at a pace of its own
            val inner   = config.radii.first()
            val outer   = config.radii.last()
            val count   = config.radii.size
            val spacing = (outer - inner) / (count - 1)
            val ripple  = (t / RIPPLE_PERIODS[index % RIPPLE_PERIODS.size]) % 1f
            for (ringIndex in 0 until count) {
                // 0 at the innermost radius, count - 1 at the outermost
                val position = ringIndex + ripple
                val travelled = position / count
                val fadeIn = (position / 0.6f).coerceAtMost(1f)
                val alpha = 0.15f * (1f - travelled) * fadeIn
                val strokeWidth = (2.0f - 0.8f * travelled) * density
                drawCircle(
                    color  = baseColor.copy(alpha = alpha * burstAlpha),
                    radius = (inner + position * spacing) * density * radiusScale,
                    center = center,
                    style  = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

// Milliseconds for one ring to ripple from the innermost radius to the next, a little different for
// every group so no two pulse together
private val RIPPLE_PERIODS = floatArrayOf(2600f, 3400f, 2200f, 3000f, 3700f, 2800f)

private data class RingConfig(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationX: Int,
    val durationY: Int,
    val radii: List<Float>, // In dp
    val depth: Float // Depth for parallax effect
)
