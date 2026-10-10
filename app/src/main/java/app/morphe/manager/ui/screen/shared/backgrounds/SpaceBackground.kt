/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.ui.theme.isDarkTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Animated space background with stars moving towards the viewer (warp-style perspective).
 * Stars come in a few temperatures and the far ones twinkle, and once the speed picks up the
 * near stars stretch into warp streaks.
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
    val isDarkTheme = isDarkTheme()
    // Mostly white, with a share of blue and warm stars, as a real sky has
    val starTints = if (isDarkTheme) {
        listOf(Color.White, Color(0xFFCAD8FF), Color(0xFFFFE6C2))
    } else {
        listOf(Color(0xFF1A2530), Color(0xFF213A6B), Color(0xFF5C4223))
    }
    val starColor = starTints[0]
    val context = LocalContext.current

    // Parallax tilt from accelerometer
    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f,
        context = context
    )

    val stars = remember(isDarkTheme) {
        mutableStateListOf<StarData>().apply { addAll(generateStarPool()) }
    }

    // baseProgress drives the Z-depth of all stars each frame
    var baseProgress by remember { mutableFloatStateOf(0f) }

    // The eased speed the warp currently runs at, which stretches the near stars into streaks
    var warpSpeed by remember { mutableFloatStateOf(1f) }

    // flashAlpha 0→0.45→0: full-screen white flash on patching completion.
    // Snaps to its peak, the abruptness is intentional and feels like a camera flash,
    // then fades out slowly so the eye can adjust naturally
    val flashAlpha = rememberCompletionPulse(
        patchingCompleted,
        riseMillis = 100,
        fallMillis = 500,
        peak = 0.45f
    )

    // Stars lean three times as hard into a speed-up as the other backgrounds, and the 5.0/sec
    // ramp gives ~0.4s to reach full speed, matching the "warp engine" feel.
    // The warp integrates the time it skipped, so a slower step covers the same distance
    BackgroundStepEffect(speedMultiplier, boost = 3f, rampPerSecond = 5f) { scaledMs, speed ->
        // Normalize baseProgress increment to 60fps baseline so delta spikes don't jump
        baseProgress += 0.0025f * (scaledMs / 16.67f)
        warpSpeed = speed

        // Regenerate stars that have passed the camera (adjustedProgress wraps to 0..1)
        stars.forEachIndexed { index, star ->
            val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f
            if (adjustedProgress !in 0.01f..0.98f) {
                if (star.lastRegen != baseProgress.toInt()) {
                    // Pick a new random position outside the center exclusion zone
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

    // Meteor spawner - occasional shooting star every 40–60 seconds
    var meteor by remember { mutableStateOf<MeteorState?>(null) }
    val meteorProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(40000, 60000).milliseconds)
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
        // Streaks only once the warp is clearly faster than cruising, growing with the speed
        val streak = ((warpSpeed - 1.3f) / 4f).coerceIn(0f, 1f) * 0.12f

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
            // Far stars twinkle, near ones are too bright and too brief for it to read
            val twinkle = if (z > 0.5f) 0.7f + 0.3f * sin(baseProgress * 40f * star.twinkleRate + star.twinklePhase) else 1f
            val baseAlpha = (star.baseAlpha * distAlpha * fadeIn * fadeOut * twinkle).coerceIn(0f, 1f)
            val color = starTints[star.tint]
            val head = Offset(finalX, finalY)

            if (streak > 0f && z < 0.8f) {
                // Warp streak: the tail is where the star stood a little further away, so it
                // points back at the vanishing point and lengthens as the star closes in
                val tailFactor = 1f / (z + streak)
                val tail = Offset(
                    centerX + baseX * tailFactor + tiltX * parallaxStrength,
                    centerY + baseY * tailFactor + tiltY * parallaxStrength
                )
                drawLine(color, tail, head, strokeWidth = finalSize * 1.4f, cap = StrokeCap.Round, alpha = baseAlpha * 0.8f)
            } else {
                // Glow + solid dot
                drawCircle(color = color, radius = finalSize * 1.8f, center = head, alpha = baseAlpha * 0.2f)
            }
            drawCircle(color = color, radius = finalSize * 1.1f, center = head, alpha = baseAlpha)
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
    } while (distance < 0.15f) // Exclude center 10% area to avoid crowding the vanishing point

    StarData(
        x             = x,
        y             = y,
        size          = 2f + Random.nextFloat() * 3.5f,
        baseAlpha     = 0.6f + Random.nextFloat() * 0.4f,
        tint          = Random.nextFloat().let { if (it < 0.7f) 0 else if (it < 0.88f) 1 else 2 },
        twinkleRate   = 0.5f + Random.nextFloat(),
        twinklePhase  = Random.nextFloat() * 6.2832f,
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
    val tint: Int, // Index into the theme's star tints: 0 white, 1 blue, 2 warm
    val twinkleRate: Float,
    val twinklePhase: Float,
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
