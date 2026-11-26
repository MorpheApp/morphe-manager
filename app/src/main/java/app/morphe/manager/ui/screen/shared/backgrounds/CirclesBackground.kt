/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin

/**
 * Circles background with parallax effect.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion each circle bursts outward in radius and fades - staggered across
 * circles - then eases back to normal size.
 */
@Composable
fun CirclesBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val palette = themePalette()

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f
    )

    val time = rememberAnimatedTime(speedMultiplier)

    // burstProgress 0→1 drives radius scale and alpha fade for each circle,
    // then eases back so circles return to normal size
    val burstProgress = rememberCompletionPulse(patchingCompleted, riseMillis = 1100, fallMillis = 450)

    Canvas(modifier = modifier.fillMaxSize()) {
        val t     = time.value
        val tiltX = parallaxState.tiltX.value * density
        val tiltY = parallaxState.tiltY.value * density
        val bp    = burstProgress.value

        CIRCLES.forEachIndexed { index, circle ->
            // X/Y oscillate with sin() at their original tween periods - identical motion at speed 1x
            val x = circle.x + circle.swayX * sin(t * TWO_PI / circle.periodX)
            val y = circle.y + circle.swayY * sin(t * TWO_PI / circle.periodY)
            val parallaxStrength = circle.depth * CIRCLE_PARALLAX_DP
            val center = Offset(
                size.width  * x + tiltX * parallaxStrength,
                size.height * y + tiltY * parallaxStrength
            )
            val color = palette[circle.colorIndex]

            // Stagger burst per circle: index 0 starts at bp=0, index 5 starts at bp=0.35
            val groupDelay  = index / (CIRCLES.size - 1).toFloat() * 0.35f
            val localBp     = ((bp - groupDelay) / (1f - groupDelay)).coerceIn(0f, 1f)
            val radiusScale = if (bp > 0f) 1f + localBp * 2.2f else 1f
            val burstAlpha  = if (bp > 0f) (1f - localBp).coerceIn(0f, 1f) else 1f

            // Filled circle
            drawCircle(
                color  = color,
                radius = circle.radius * density * radiusScale,
                center = center,
                alpha  = circle.alpha * burstAlpha
            )

            // During burst: stroke ring that expands further for a layered depth look
            if (bp > 0f && localBp > 0f) {
                val strokeRadius = circle.radius * density * (1f + localBp * 3.5f)
                val strokeAlpha  = ((1f - localBp) * 0.5f).coerceIn(0f, 1f)
                drawCircle(
                    color  = color,
                    radius = strokeRadius,
                    center = center,
                    alpha  = strokeAlpha,
                    style  = Stroke(width = 1.3f * density)
                )
            }
        }
    }
}

// How far the deepest circle follows a full tilt
private const val CIRCLE_PARALLAX_DP = 17f

private class CircleConfig(
    val x: Float,
    val swayX: Float,
    val periodX: Float,
    val y: Float,
    val swayY: Float,
    val periodY: Float,
    val radius: Float, // In dp
    val colorIndex: Int,
    val alpha: Float,
    val depth: Float // Depth for parallax effect
)

private val CIRCLES = listOf(
    // Large top left
    CircleConfig(0.20f,  0.05f,  8000f, 0.225f, 0.025f, 7000f, 133f, 0, 0.05f,  0.8f),
    // Medium top right
    CircleConfig(0.85f,  0.03f,  9000f, 0.185f, 0.035f, 6500f, 93f,  2, 0.035f, 0.6f),
    // Small center right
    CircleConfig(0.715f, 0.035f, 7500f, 0.44f,  0.04f,  8500f, 67f,  2, 0.04f,  0.4f),
    // Medium bottom right
    CircleConfig(0.815f, 0.035f, 9500f, 0.785f, 0.035f, 7200f, 107f, 1, 0.035f, 0.7f),
    // Small bottom left
    CircleConfig(0.24f,  0.04f,  8200f, 0.765f, 0.035f, 6800f, 60f,  0, 0.04f,  0.5f),
    // Bottom center
    CircleConfig(0.525f, 0.025f, 8800f, 0.895f, 0.025f, 7800f, 73f,  1, 0.04f,  0.6f)
)
