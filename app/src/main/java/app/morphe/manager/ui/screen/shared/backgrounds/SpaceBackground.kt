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

    // Parallax tilt from accelerometer
    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f
    )

    // The stars are moved in place by the step, which bumps step once per pass so the canvas
    // redraws once, rather than a snapshot write per star
    val stars = remember { StarField() }
    val step = remember { mutableIntStateOf(0) }

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
        stars.advance(scaledMs, speed)
        step.intValue++
    }

    // Meteor spawner - occasional shooting star every 40-60 seconds, only while the backdrop is
    // on screen, so a meteor never streaks across a sky nobody can see
    var meteor by remember { mutableStateOf<MeteorState?>(null) }
    val meteorProgress = remember { Animatable(0f) }
    val currentSpeedMultiplier by rememberUpdatedState(speedMultiplier)
    AnimationFrameEffect {
        try {
            while (true) {
                delay(Random.nextLong(40000, 60000).milliseconds)
                if (currentSpeedMultiplier == 0f) continue
                val direction = Random.nextInt(2)
                val angle = when (direction) {
                    0    -> 130f + Random.nextFloat() * 20f // Right to left
                    else -> 30f  + Random.nextFloat() * 20f // Left to right
                }
                meteor = MeteorState(
                    startX    = Random.nextFloat(),
                    startY    = Random.nextFloat() * 0.3f,
                    angle     = angle,
                    length    = 67f + Random.nextFloat() * 50f,
                    depth     = 0.4f + Random.nextFloat() * 0.6f,
                    thickness = 1.3f
                )
                meteorProgress.snapTo(0f)
                meteorProgress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
                meteor = null
            }
        } finally {
            // Cut short when the backdrop is covered, so no meteor hangs frozen mid-flight
            meteor = null
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read so every step invalidates the draw
        step.intValue
        val width   = size.width
        val height  = size.height
        val centerX = width  / 2f
        val centerY = height / 2f
        val tiltX   = parallaxState.tiltX.value * density
        val tiltY   = parallaxState.tiltY.value * density
        val fa = flashAlpha.value // 0..1, drives white flash overlay
        // Streaks only once the warp is clearly faster than cruising, growing with the speed
        val streak = ((stars.warpSpeed - 1.3f) / 4f).coerceIn(0f, 1f) * 0.12f
        val cullMargin = 50f * density

        // Render stars
        for (i in 0 until STAR_COUNT) {
            val z = (1f - stars.progress[i]).coerceAtLeast(0.01f)
            if (z !in 0.05f..1.5f) continue

            val perspectiveFactor = 1f / z
            val baseX = stars.x[i] * width  * 0.5f
            val baseY = stars.y[i] * height * 0.5f

            // Perspective projection - stars expand outward as z shrinks
            val projectedX = baseX * perspectiveFactor
            val projectedY = baseY * perspectiveFactor

            val parallaxStrength = stars.depth[i] * 50f * (1f - z.coerceIn(0f, 1f))
            val finalX = centerX + projectedX + tiltX * parallaxStrength
            val finalY = centerY + projectedY + tiltY * parallaxStrength

            // Cull stars outside screen bounds
            if (finalX < -cullMargin || finalX > width + cullMargin ||
                finalY < -cullMargin || finalY > height + cullMargin) continue

            val sizeFactor  = perspectiveFactor * 0.45f
            val finalSize   = stars.size[i] * density * sizeFactor

            // Fade in from far distance, fade out when very close to camera
            val fadeIn    = if (z > 1.3f) ((1.5f - z) / 0.2f).coerceIn(0f, 1f) else 1f
            val fadeOut   = if (z < 0.15f) (z / 0.15f).coerceIn(0f, 1f) else 1f
            val distAlpha = when {
                z > 0.6f -> ((1f - z) / 0.4f).coerceIn(0f, 1f)
                z < 0.3f -> (z / 0.3f).coerceIn(0f, 1f)
                else     -> 1f
            }
            // Far stars twinkle, near ones are too bright and too brief for it to read
            val twinkle = if (z > 0.5f) 0.7f + 0.3f * sin(stars.twinkle[i]) else 1f
            val baseAlpha = (stars.baseAlpha[i] * distAlpha * fadeIn * fadeOut * twinkle).coerceIn(0f, 1f)
            val color = starTints[stars.tint[i]]
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
            val parallaxX = tiltX * m.depth * 40f
            val parallaxY = tiltY * m.depth * 40f
            val travelDist = width * 2f * p
            val cosA = cos(angleRad); val sinA = sin(angleRad)
            val length = m.length * density
            val curX = m.startX * width  + travelDist * cosA + parallaxX
            val curY = m.startY * height + travelDist * sinA + parallaxY
            val tailX = curX - length * cosA
            val tailY = curY - length * sinA
            val thickness = m.thickness * density

            // Outer glow
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = 0.3f),
                    0.6f to starColor.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY), end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY), end = Offset(tailX, tailY),
                strokeWidth = thickness * 4f, cap = StrokeCap.Round
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
                strokeWidth = thickness, cap = StrokeCap.Round
            )
        }

        // White flash overlay
        // Full-screen rect that snaps to white then fades - clean "camera flash" feel
        if (fa > 0f) {
            drawRect(color = Color.White.copy(alpha = fa), size = size)
        }
    }
}

private const val STAR_COUNT = 300

/**
 * The star pool in flat arrays, moved in place by [advance] so stepping and drawing allocate
 * nothing. Every star keeps its own place along the Z-axis in [progress], 0 far away and 1 at the
 * camera, wrapping on its own, so no shared counter grows until a Float can no longer resolve it.
 */
private class StarField {
    val x = FloatArray(STAR_COUNT)
    val y = FloatArray(STAR_COUNT)
    val size = FloatArray(STAR_COUNT) // In dp
    val baseAlpha = FloatArray(STAR_COUNT)
    val tint = IntArray(STAR_COUNT) // Index into the theme's star tints: 0 white, 1 blue, 2 warm
    val depth = FloatArray(STAR_COUNT)
    val progress = FloatArray(STAR_COUNT)
    val twinkle = FloatArray(STAR_COUNT) // Phase of the twinkle, in radians
    private val speed = FloatArray(STAR_COUNT)
    private val twinkleRate = FloatArray(STAR_COUNT)

    /** The eased speed the warp currently runs at, which stretches the near stars into streaks. */
    var warpSpeed = 1f
        private set

    init {
        for (i in 0 until STAR_COUNT) {
            val depthLayer = i / STAR_COUNT.toFloat()
            // Golden angle for even angular distribution
            place(i, angleDegrees = (i * 137.5f) % 360f)
            size[i] = 0.67f + Random.nextFloat() * 1.17f
            baseAlpha[i] = 0.6f + Random.nextFloat() * 0.4f
            tint[i] = Random.nextFloat().let { if (it < 0.7f) 0 else if (it < 0.88f) 1 else 2 }
            twinkleRate[i] = 0.5f + Random.nextFloat()
            twinkle[i] = Random.nextFloat() * TWO_PI
            depth[i] = depthLayer
            speed[i] = 0.3f + depthLayer * 2.2f
            progress[i] = Random.nextFloat() // Stagger stars along Z-axis for density
        }
    }

    fun advance(scaledMs: Float, warpSpeed: Float) {
        this.warpSpeed = warpSpeed
        // Normalize the increment to a 60fps baseline so delta spikes don't jump
        val travel = 0.0025f * (scaledMs / 16.67f)
        for (i in 0 until STAR_COUNT) {
            twinkle[i] = (twinkle[i] + travel * 40f * twinkleRate[i]) % TWO_PI
            progress[i] += travel * speed[i]
            // Passed the camera: comes back far away from a new direction
            if (progress[i] >= 1f) {
                progress[i] = fract(progress[i])
                place(i, angleDegrees = Random.nextFloat() * 360f)
            }
        }
    }

    /** Puts star [i] at [angleDegrees] around the vanishing point, outside the crowded center. */
    private fun place(i: Int, angleDegrees: Float) {
        val angleRad = angleDegrees * (Math.PI / 180f).toFloat()
        var distance: Float
        do {
            distance = sqrt(Random.nextFloat()) * 1.5f
        } while (distance < 0.15f) // Exclude center 10% area to avoid crowding the vanishing point
        x[i] = cos(angleRad) * distance
        y[i] = sin(angleRad) * distance
    }
}

private data class MeteorState(
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val length: Float, // In dp
    val depth: Float,
    val thickness: Float // In dp
)
