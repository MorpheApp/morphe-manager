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
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f,
        context = context
    )

    val time = rememberAnimatedTime(speedMultiplier)

    // burstProgress 0→1 drives radius scale and alpha fade for each circle,
    // then eases back so circles return to normal size
    val burstProgress = rememberCompletionPulse(patchingCompleted, riseMillis = 1100, fallMillis = 450)

    Canvas(modifier = modifier.fillMaxSize()) {
        val t     = time.value
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val twoPi = 2f * PI.toFloat()
        val bp    = burstProgress.value

        // Circle configs with depth for parallax
        // X/Y oscillate with sin() at their original tween periods - identical motion at speed 1x
        // Circle 1 - large top left
        // Circle 2 - medium top right
        // Circle 3 - small center right
        // Circle 4 - medium bottom right
        // Circle 5 - small bottom left
        // Circle 6 - bottom center
        val circles = listOf(
            CircleData(0.20f  + 0.05f  * sin(t * twoPi / 8000f), 0.225f + 0.025f * sin(t * twoPi / 7000f), 133f, primaryColor,   0.05f,  0.8f),
            CircleData(0.85f  + 0.03f  * sin(t * twoPi / 9000f), 0.185f + 0.035f * sin(t * twoPi / 6500f), 93f,  tertiaryColor,  0.035f, 0.6f),
            CircleData(0.715f + 0.035f * sin(t * twoPi / 7500f), 0.44f  + 0.04f  * sin(t * twoPi / 8500f), 67f,  tertiaryColor,  0.04f,  0.4f),
            CircleData(0.815f + 0.035f * sin(t * twoPi / 9500f), 0.785f + 0.035f * sin(t * twoPi / 7200f), 107f, secondaryColor, 0.035f, 0.7f),
            CircleData(0.24f  + 0.04f  * sin(t * twoPi / 8200f), 0.765f + 0.035f * sin(t * twoPi / 6800f), 60f,  primaryColor,   0.04f,  0.5f),
            CircleData(0.525f + 0.025f * sin(t * twoPi / 8800f), 0.895f + 0.025f * sin(t * twoPi / 7800f), 73f,  secondaryColor, 0.04f,  0.6f),
        )

        circles.forEachIndexed { index, circle ->
            val parallaxStrength = circle.depth * 50f
            val center = Offset(
                size.width  * circle.x + tiltX * parallaxStrength,
                size.height * circle.y + tiltY * parallaxStrength
            )

            // Stagger burst per circle: index 0 starts at bp=0, index 5 starts at bp=0.35
            val groupDelay  = index / (circles.size - 1).toFloat() * 0.35f
            val localBp     = ((bp - groupDelay) / (1f - groupDelay)).coerceIn(0f, 1f)
            val radiusScale = if (bp > 0f) 1f + localBp * 2.2f else 1f
            val burstAlpha  = if (bp > 0f) (1f - localBp).coerceIn(0f, 1f) else 1f

            // Filled circle
            drawCircle(
                color  = circle.color.copy(alpha = circle.alpha * burstAlpha),
                radius = circle.radius * density * radiusScale,
                center = center
            )

            // During burst: stroke ring that expands further for a layered depth look
            if (bp > 0f && localBp > 0f) {
                val strokeRadius = circle.radius * density * (1f + localBp * 3.5f)
                val strokeAlpha  = ((1f - localBp) * 0.5f).coerceIn(0f, 1f)
                drawCircle(
                    color  = circle.color.copy(alpha = strokeAlpha),
                    radius = strokeRadius,
                    center = center,
                    style  = Stroke(width = 1.3f * density)
                )
            }
        }
    }
}

private data class CircleData(
    val x: Float,
    val y: Float,
    val radius: Float, // In dp
    val color: androidx.compose.ui.graphics.Color,
    val alpha: Float,
    val depth: Float // Depth for parallax effect
)
