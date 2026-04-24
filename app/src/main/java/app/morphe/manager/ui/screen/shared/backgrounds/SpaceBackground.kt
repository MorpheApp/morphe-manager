/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.util.isDarkBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Animated space background with stars moving towards the viewer (warp-style perspective).
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion fires a white flash overlay that fades out slowly.
 */
@Composable
fun SpaceBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.isDarkBackground()
    val starColor = if (isDarkTheme) Color.White else Color(0xFF1A2530)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Parallax tilt from accelerometer
    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f,
        context = context,
        coroutineScope = coroutineScope
    )

    val stars = remember(isDarkTheme) {
        mutableStateListOf<StarData>().apply { addAll(generateStarPool()) }
    }

    // baseProgress drives the Z-depth of all stars each frame
    var baseProgress by remember { mutableFloatStateOf(0f) }

    // flashAlpha 0→1→0: full-screen white flash on patching completion
    // Ramps up instantly then fades out slowly so it reads as a "success" moment
    val flashAlpha = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            flashAlpha.snapTo(0f)
            // Snap to peak - the abruptness is intentional, feels like a camera flash
            flashAlpha.animateTo(0.45f, tween(100, easing = FastOutSlowInEasing))
            // Slow fade out so the eye can adjust naturally
            flashAlpha.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    // targetSpeedState is written every recomposition via SideEffect so the frame loop
    // can read it reactively without restarting LaunchedEffect.
    val targetSpeedState = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeedState.floatValue = speedMultiplier }

    // Main star animation loop - runs every frame via withInfiniteAnimationFrameMillis
    LaunchedEffect(Unit) {
        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = targetSpeedState.floatValue
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs

                val speedBoost = 3f // Local boost on top of global speedMultiplier; increase for more dramatic warp

                // Lerp toward target speed with a local 2x boost on top of the global speedMultiplier -
                // stars accelerate twice as fast as other backgrounds during patching.
                // 5.0/sec ramp gives ~0.4s to reach full speed, matching the "warp engine" feel.
                currentSpeed += (1f + (targetSpeedState.floatValue - 1f) * speedBoost - currentSpeed) * (delta / 1000f) * 5.0f

                // Normalize baseProgress increment to 60fps baseline so delta spikes don't jump
                baseProgress += 0.0025f * currentSpeed * (delta / 16.67f)

                // Regenerate stars that have passed the camera (adjustedProgress wraps to 0..1)
                stars.forEachIndexed { index, star ->
                    val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f
                    if (adjustedProgress !in 0.01f..0.98f) {
                        if (star.lastRegen != baseProgress.toInt()) {
                            // Pick a new random position outside the centre exclusion zone
                            var newX: Float; var newY: Float; var newDistance: Float
                            do {
                                val newAngle = Random.nextFloat() * 360f
                                newDistance = sqrt(Random.nextFloat()) * 1.5f
                                val newAngleRad = newAngle * (Math.PI / 180f).toFloat()
                                newX = cos(newAngleRad) * newDistance
                                newY = sin(newAngleRad) * newDistance
                            } while (newDistance < 0.15f)
                            stars[index] = star.copy(x = newX, y = newY, lastRegen = baseProgress.toInt())
                        }
                    }
                }
            }
        }
    }

    // Meteor spawner - occasional shooting star every 40–60 seconds
    var meteor by remember { mutableStateOf<MeteorState?>(null) }
    val meteorProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(40000, 60000))
            val direction = Random.nextInt(2)
            val angle = when (direction) {
                0    -> 130f + Random.nextFloat() * 20f // Right to left
                else -> 30f  + Random.nextFloat() * 20f // Left to right
            }
            meteor = MeteorState(
                startX    = Random.nextFloat(),
                startY    = Random.nextFloat() * 0.3f,
                angle     = angle,
                length    = 200f + Random.nextFloat() * 150f,
                depth     = 0.4f + Random.nextFloat() * 0.6f,
                thickness = 4f
            )
            meteorProgress.snapTo(0f)
            meteorProgress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
            meteor = null
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width   = size.width
        val height  = size.height
        val centerX = width  / 2f
        val centerY = height / 2f
        val tiltX   = parallaxState.tiltX.value
        val tiltY   = parallaxState.tiltY.value
        val fa = flashAlpha.value // 0..1, drives white flash overlay

        // Render stars
        stars.forEach { star ->
            val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f
            val z = (1f - adjustedProgress).coerceAtLeast(0.01f)
            if (z !in 0.05f..1.5f) return@forEach

            val perspectiveFactor = 1f / z
            val baseX = star.x * width  * 0.5f
            val baseY = star.y * height * 0.5f

            // Perspective projection - stars expand outward as z shrinks
            val projectedX = baseX * perspectiveFactor
            val projectedY = baseY * perspectiveFactor

            val parallaxStrength = star.depth * 150f * (1f - z.coerceIn(0f, 1f))
            val finalX = centerX + projectedX + tiltX * parallaxStrength
            val finalY = centerY + projectedY + tiltY * parallaxStrength

            // Cull stars outside screen bounds
            if (finalX < -150 || finalX > width + 150 || finalY < -150 || finalY > height + 150) return@forEach

            val sizeFactor  = perspectiveFactor * 0.45f
            val finalSize   = star.size * sizeFactor

            // Fade in from far distance, fade out when very close to camera
            val fadeIn    = if (z > 1.3f) ((1.5f - z) / 0.2f).coerceIn(0f, 1f) else 1f
            val fadeOut   = if (z < 0.15f) (z / 0.15f).coerceIn(0f, 1f) else 1f
            val distAlpha = when {
                z > 0.6f -> ((1f - z) / 0.4f).coerceIn(0f, 1f)
                z < 0.3f -> (z / 0.3f).coerceIn(0f, 1f)
                else     -> 1f
            }
            val baseAlpha = (star.baseAlpha * distAlpha * fadeIn * fadeOut).coerceIn(0f, 1f)

            // Glow + solid dot
            drawCircle(color = starColor, radius = finalSize * 1.8f, center = Offset(finalX, finalY), alpha = baseAlpha * 0.2f)
            drawCircle(color = starColor, radius = finalSize * 1.1f, center = Offset(finalX, finalY), alpha = baseAlpha)
        }

        // Render meteor
        meteor?.let { m ->
            val p        = meteorProgress.value
            val angleRad = m.angle * (Math.PI / 180f).toFloat()
            val parallaxX = tiltX * m.depth * 120f
            val parallaxY = tiltY * m.depth * 120f
            val travelDist = width * 2f * p
            val cosA = cos(angleRad); val sinA = sin(angleRad)
            val curX = m.startX * width  + travelDist * cosA + parallaxX
            val curY = m.startY * height + travelDist * sinA + parallaxY
            val tailX = curX - m.length * cosA
            val tailY = curY - m.length * sinA

            // Outer glow
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = 0.3f),
                    0.6f to starColor.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY), end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY), end = Offset(tailX, tailY),
                strokeWidth = m.thickness * 4f, cap = StrokeCap.Round
            )
            // Core trail
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = 0.95f),
                    0.5f to starColor.copy(alpha = 0.6f),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY), end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY), end = Offset(tailX, tailY),
                strokeWidth = m.thickness, cap = StrokeCap.Round
            )
        }

        // White flash overlay
        // Full-screen rect that snaps to white then fades - clean "camera flash" feel
        if (fa > 0f) {
            drawRect(color = Color.White.copy(alpha = fa), size = size)
        }
    }
}

/**
 * Generates initial pool of stars with varied properties.
 * Uses golden angle distribution for even coverage and staggered Z offsets.
 */
private fun generateStarPool(): List<StarData> = List(300) { index ->
    val depthLayer = index / 300f
    var x: Float; var y: Float; var distance: Float
    do {
        val angle = (index * 137.5f) % 360f // Golden angle for even angular distribution
        distance = sqrt(Random.nextFloat()) * 1.5f
        val angleRad = angle * (Math.PI / 180f).toFloat()
        x = cos(angleRad) * distance
        y = sin(angleRad) * distance
    } while (distance < 0.15f) // Exclude centre 10% area to avoid crowding the vanishing point

    StarData(
        x             = x,
        y             = y,
        size          = 2f + Random.nextFloat() * 3.5f,
        baseAlpha     = 0.6f + Random.nextFloat() * 0.4f,
        depth         = depthLayer,
        speed         = 0.3f + depthLayer * 2.2f,
        initialOffset = Random.nextFloat(), // Stagger stars along Z-axis for density
        lastRegen     = -1
    )
}

private data class StarData(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseAlpha: Float,
    val depth: Float,
    val speed: Float,
    val initialOffset: Float, // Offset along Z-axis to distribute stars
    val lastRegen: Int // Tracks last regen frame to avoid duplicate regeneration
)

private data class MeteorState(
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val length: Float,
    val depth: Float,
    val thickness: Float
)
