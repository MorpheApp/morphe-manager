/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Particles background - small particles drift with velocity, friction and soft edge bounce.
 * Nearby particles connect with faint lines (same as constellation but more dynamic and dense).
 * Uses a dedicated frame loop so physics run at display rate regardless of [speedMultiplier].
 * [speedMultiplier] controls the drift velocity scale.
 * On patching completion all particles explode outward from the screen centre then drift back.
 */
@Composable
fun ParticlesBackground(
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
        sensitivity = 0.2f,
        context = context,
        coroutineScope = coroutineScope
    )

    // Particle state - mutable so physics loop can update positions each frame
    val particles = remember {
        mutableStateListOf<Particle>().apply {
            repeat(65) {

                // Create 3 particle size groups for more natural depth
                val radius = when (Random.nextFloat()) {
                    in 0f..0.6f -> 2.5f + Random.nextFloat() * 3f // small particles
                    in 0.6f..0.9f -> 4f + Random.nextFloat() * 4f // medium particles
                    else -> 7f + Random.nextFloat() * 5f // large particles
                }

                add(
                    Particle(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        vx = (Random.nextFloat() - 0.5f) * 0.00018f,
                        vy = (Random.nextFloat() - 0.5f) * 0.00018f,
                        radius = radius,
                        colorIndex = it % 3
                    )
                )
            }
        }
    }

    // targetSpeedState updated via SideEffect so the physics loop stays reactive
    val targetSpeedState = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeedState.floatValue = speedMultiplier }

    // explodeProgress 0→1: particles burst from centre, then drift back naturally
    val explodeProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            explodeProgress.snapTo(0f)
            // Burst outward
            explodeProgress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
            // Smooth return — ep goes 1→0 so push gradually decreases to zero
            explodeProgress.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
        }
    }

    // Physics loop - runs every display frame independently of Compose animation clock
    LaunchedEffect(Unit) {
        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = targetSpeedState.floatValue
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs

                // Lerp speed for smooth transitions
                currentSpeed += (targetSpeedState.floatValue - currentSpeed) * (delta / 1000f) * 2.5f
                val speedScale = currentSpeed * (delta / 16.67f)

                particles.forEachIndexed { index, p ->
                    var nx  = p.x  + p.vx * speedScale
                    var ny  = p.y  + p.vy * speedScale
                    var nvx = p.vx
                    var nvy = p.vy

                    // Soft edge bounce - reverse velocity and nudge back inside
                    if (nx < 0.02f) { nvx = abs(nvx); nx = 0.02f }
                    if (nx > 0.98f) { nvx = -abs(nvx); nx = 0.98f }
                    if (ny < 0.02f) { nvy = abs(nvy); ny = 0.02f }
                    if (ny > 0.98f) { nvy = -abs(nvy); ny = 0.98f }

                    // Tiny random drift to avoid completely straight paths
                    nvx += (Random.nextFloat() - 0.5f) * 0.000004f
                    nvy += (Random.nextFloat() - 0.5f) * 0.000004f

                    // Speed cap so particles never rocket across the screen
                    val speed = sqrt(nvx * nvx + nvy * nvy)
                    val maxSpeed = 0.00025f
                    if (speed > maxSpeed) {
                        nvx = nvx / speed * maxSpeed
                        nvy = nvy / speed * maxSpeed
                    }

                    particles[index] = p.copy(x = nx, y = ny, vx = nvx, vy = nvy)
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val ep    = explodeProgress.value
        val cx    = size.width  * 0.5f
        val cy    = size.height * 0.5f

        val connectDist   = size.width * 0.22f
        val connectDistSq = connectDist * connectDist

        // Compute screen positions with parallax and explosion offset
        val positions = particles.map { p ->
            val parallaxStrength = 25f
            val baseX = p.x * size.width  + tiltX * parallaxStrength
            val baseY = p.y * size.height + tiltY * parallaxStrength

            // During explosion push outward from centre — ep smoothly goes 0→1→0
            // so return is just the same offset shrinking back to zero (no snap)
            if (ep > 0f) {
                val dirX  = baseX - cx
                val dirY  = baseY - cy
                val eased = ep * ep
                Offset(baseX + dirX * eased * 1.2f, baseY + dirY * eased * 1.2f)
            } else {
                Offset(baseX, baseY)
            }
        }

        // Draw connection lines
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val dx     = positions[i].x - positions[j].x
                val dy     = positions[i].y - positions[j].y
                val distSq = dx * dx + dy * dy
                if (distSq < connectDistSq) {
                    val proximity = 1f - sqrt(distSq) / connectDist
                    val alpha = proximity * proximity * 0.10f + ep * 0.05f * proximity
                    val color = when ((particles[i].colorIndex + particles[j].colorIndex) % 3) {
                        0    -> primaryColor
                        1    -> secondaryColor
                        else -> tertiaryColor
                    }
                    drawLine(
                        color       = color.copy(alpha = alpha.coerceIn(0f, 0.18f)),
                        start       = positions[i],
                        end         = positions[j],
                        strokeWidth = 1.2f
                    )
                }
            }
        }

        // Draw particles
        particles.forEachIndexed { index, p ->
            val pos   = positions[index]
            val color = when (p.colorIndex) {
                0    -> primaryColor
                1    -> secondaryColor
                else -> tertiaryColor
            }
            val alpha  = 0.55f + ep * 0.3f
            val radius = p.radius * (1f + ep * 0.8f)

            drawCircle(color = color.copy(alpha = alpha * 0.2f), radius = radius * 2f, center = pos)
            drawCircle(color = color.copy(alpha = alpha),        radius = radius,      center = pos)
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val colorIndex: Int
)
