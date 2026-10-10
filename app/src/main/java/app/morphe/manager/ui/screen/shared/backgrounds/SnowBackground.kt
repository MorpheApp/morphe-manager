/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.morphe.manager.ui.theme.isDarkTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Snowfall background with layered depth and parallax effect.
 * Near flakes are crisp and fast, far ones soft out-of-focus dots, and slow gusts of wind push
 * the whole fall sideways, the near flakes further than the far ones. Flakes drift down slowly and
 * melt away partway down, so the snow is thickest near the top and thins out toward the bottom.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion all snowflakes blast upward in a blizzard burst, then settle
 * back down into normal fall.
 */
@Composable
fun SnowBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val isDarkTheme = isDarkTheme()
    val snowColor = if (isDarkTheme) Color.White else Color(0xFF4A5F7A)
    val context = LocalContext.current

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.2f,
        context = context
    )

    // Create cached snowflake bitmaps with different detail levels
    val snowflakeBitmaps = remember(snowColor) {
        listOf(
            createDetailedSnowflakeBitmap(40, snowColor, DetailLevel.HIGH),    // Close - highly detailed
            createDetailedSnowflakeBitmap(30, snowColor, DetailLevel.MEDIUM),  // Middle - medium detail
            createDetailedSnowflakeBitmap(20, snowColor, DetailLevel.LOW)      // Far - simple
        )
    }
    // The far layer is out of focus, so it is drawn as soft dots rather than as flakes
    val bokehBitmap = remember(snowColor) { createBokehBitmap(40, snowColor) }

    // Generate snowflakes with depth layers, sorted far to close once for proper layering
    val snowflakes = remember {
        List(56) {
            // Depth runs from far (0) to close (1), and alpha and parallax grow with it, so the
            // layers that set size and speed have to follow the same direction
            val depth = Random.nextFloat()
            val layer = when {
                depth > 0.66f -> 0  // Close layer
                depth > 0.33f -> 1  // Middle layer
                else -> 2           // Far layer
            }

            SnowflakeData(
                x = Random.nextFloat(),
                initialProgress = Random.nextFloat(),
                fallSpeed = when (layer) {
                    0 -> 15000 + Random.nextInt(5000)    // Fast (close)
                    1 -> 22000 + Random.nextInt(6000)    // Medium
                    else -> 30000 + Random.nextInt(8000) // Slow (far)
                },
                swayAmplitude = when (layer) {
                    0 -> 0.04f + Random.nextFloat() * 0.03f
                    1 -> 0.03f + Random.nextFloat() * 0.02f
                    else -> 0.02f + Random.nextFloat() * 0.015f
                },
                swayFrequency = 1.2f + Random.nextFloat() * 1.0f,
                size = when (layer) {
                    0 -> 0.75f + Random.nextFloat() * 0.35f  // 0.75-1.1
                    1 -> 0.6f + Random.nextFloat() * 0.25f   // 0.6-0.85
                    else -> 0.45f + Random.nextFloat() * 0.2f // 0.45-0.65
                },
                rotationSpeed = 8000 + Random.nextInt(22000),
                rotationDirection = if (Random.nextBoolean()) 1f else -1f,
                initialRotation = Random.nextFloat() * 360f,
                swayPhaseOffset = Random.nextFloat() * 2f * PI.toFloat(),
                seed = Random.nextFloat() * 1000f,
                depth = depth,
                layer = layer
            )
        }.sortedBy { it.depth }
    }

    // One paint for every flake, its alpha set per draw, rather than a fresh one per flake per frame
    val flakePaint = remember { Paint() }

    // Frame-based time - accumulates in ms at speed 1x, respects speedMultiplier smoothly.
    // Wraps at 120 000 ms to keep values manageable (same cycle as original)
    val animatedTime = rememberAnimatedTime(speedMultiplier)

    // Blizzard burst: snowflakes suddenly fly upward and fade out on completion,
    // then settle back down and fade in gently
    val burstProgress = rememberCompletionPulse(patchingCompleted, riseMillis = 1400, fallMillis = 600)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val globalTime = animatedTime.value % 120000f

        // Calculate fade multiplier for smooth loop transition
        val fadeDuration = 2000f
        val cycleFade = when {
            globalTime < fadeDuration -> globalTime / fadeDuration
            globalTime > 120000f - fadeDuration -> (120000f - globalTime) / fadeDuration
            else -> 1f
        }

        // Wind: two slow waves, both whole fractions of the cycle, make gusts that come and go.
        // Squaring keeps the calm stretches calm and lets the gusts stand out
        val wave = 0.65f * sin(globalTime * 2f * PI.toFloat() / 24000f) +
                0.35f * sin(globalTime * 2f * PI.toFloat() / 8000f + 1.3f)
        val gust = wave * abs(wave)

        snowflakes.forEach { flake ->
            // Calculate continuous fall progress
            val timeProgress = globalTime / flake.fallSpeed
            val travel = flake.initialProgress + timeProgress
            val fallProgress = travel % 1f

            // Every pass down the screen starts from a fresh column and melts at its own height,
            // so the fall never repeats the same tracks
            val pass = floor(travel)
            val column = fract(sin(pass * 12.9898f + flake.seed) * 43758.547f)
            val meltAt = MELT_FROM + (1f - MELT_FROM) * fract(sin(pass * 78.233f + flake.seed) * 12543.123f).pow(1.4f)
            val fadeIn = (fallProgress / FLAKE_FADE).coerceIn(0f, 1f)
            val fadeOut = ((meltAt - fallProgress) / FLAKE_FADE).coerceIn(0f, 1f)
            val life = min(fadeIn, fadeOut).let { it * it * (3f - 2f * it) }
            if (life <= 0f) return@forEach

            // Calculate sway - continuous wave
            val swayPhase = timeProgress * 2f * PI.toFloat() * flake.swayFrequency + flake.swayPhaseOffset
            val sway = sin(swayPhase) * flake.swayAmplitude

            // Calculate rotation - continuous, each flake at its own pace and in its own direction
            val rotation = (flake.rotationDirection * timeProgress * 360000f / flake.rotationSpeed +
                    flake.initialRotation) % 360f

            // Apply parallax with depth-based strength
            val parallaxStrength = flake.depth * 40f
            val parallaxX = tiltX * parallaxStrength
            val parallaxY = tiltY * parallaxStrength

            // Calculate position with smooth wrapping
            // During burst: flakes fly upward (negative Y offset) proportional to speed
            val bp = burstProgress.value
            val burstLift = if (bp > 0f) bp * (height + 200f) * (1f + flake.depth * 0.5f) else 0f
            val wind = gust * (0.03f + 0.07f * flake.depth)
            val baseX = (fract(flake.x + column) + sway + wind) * width + parallaxX
            val baseY = fallProgress * (height + 100f) - 50f + parallaxY - burstLift

            // Wrap X position for horizontal parallax
            val centerX = when {
                baseX < -50f -> baseX + width + 100f
                baseX > width + 50f -> baseX - width - 100f
                else -> baseX
            }

            // Get bitmap for this layer
            val bitmap = if (flake.layer == 2) bokehBitmap else snowflakeBitmaps[flake.layer]
            val drawSize = bitmap.width.toFloat() * flake.size

            // Calculate alpha with edge fade for seamless loop
            val depthAlpha = 0.35f + (flake.depth * 0.55f)
            val edgeFade = when {
                baseY < 0f -> ((baseY + 50f) / 50f).coerceIn(0f, 1f)
                baseY > height -> ((height + 50f - baseY) / 50f).coerceIn(0f, 1f)
                else -> 1f
            }

            // Apply cycle fade + burst fade (flakes vanish as they fly up)
            val burstFade = if (burstProgress.value > 0f) (1f - burstProgress.value).coerceIn(0f, 1f) else 1f
            val finalAlpha = depthAlpha * (0.7f + flake.size * 0.3f) * edgeFade * cycleFade * burstFade * life

            // Only draw if visible
            if (finalAlpha > 0.01f && baseY > -50f && baseY < height + 50f) {
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.translate(centerX, baseY)
                    canvas.rotate(rotation)

                    canvas.drawImageRect(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset((-drawSize / 2).toInt(), (-drawSize / 2).toInt()),
                        dstSize = IntSize(drawSize.toInt(), drawSize.toInt()),
                        paint = flakePaint.apply { alpha = finalAlpha }
                    )

                    canvas.restore()
                }
            }
        }
    }
}

// Shortest share of the fall a flake lives before it melts away, and how much of the fall its fade
// in and out each take
private const val MELT_FROM = 0.4f
private const val FLAKE_FADE = 0.12f

private fun fract(value: Float) = value - floor(value)

/**
 * Detail level for snowflake rendering.
 */
private enum class DetailLevel {
    HIGH,    // Close - full detail with branches
    MEDIUM,  // Middle - main arms with minimal branches
    LOW      // Far - simple star shape
}

/**
 * Create snowflake bitmap with varying detail levels.
 */
private fun createDetailedSnowflakeBitmap(size: Int, color: Color, detail: DetailLevel): ImageBitmap {
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        strokeCap = StrokeCap.Round
    }

    val center = size / 2f
    val mainRadius = size / 2.2f

    when (detail) {
        DetailLevel.HIGH -> {
            // Full detail with branches and decorations
            val branchRadius = mainRadius * 0.4f

            // Draw 6 main arms with branches
            for (i in 0..5) {
                val angle = (i * 60f) * (PI / 180f).toFloat()
                val endX = center + cos(angle) * mainRadius
                val endY = center + sin(angle) * mainRadius

                // Main arm
                paint.strokeWidth = size / 15f
                canvas.drawLine(
                    Offset(center, center),
                    Offset(endX, endY),
                    paint
                )

                // Side branches
                paint.strokeWidth = size / 25f
                for (j in 1..2) {
                    val branchStart = j / 3f
                    val branchX = center + cos(angle) * mainRadius * branchStart
                    val branchY = center + sin(angle) * mainRadius * branchStart

                    // Left branch
                    val leftAngle = angle - PI.toFloat() / 4
                    val leftEndX = branchX + cos(leftAngle) * branchRadius
                    val leftEndY = branchY + sin(leftAngle) * branchRadius
                    canvas.drawLine(
                        Offset(branchX, branchY),
                        Offset(leftEndX, leftEndY),
                        paint
                    )

                    // Right branch
                    val rightAngle = angle + PI.toFloat() / 4
                    val rightEndX = branchX + cos(rightAngle) * branchRadius
                    val rightEndY = branchY + sin(rightAngle) * branchRadius
                    canvas.drawLine(
                        Offset(branchX, branchY),
                        Offset(rightEndX, rightEndY),
                        paint
                    )
                }

                // Tip decoration
                paint.style = PaintingStyle.Fill
                canvas.drawCircle(Offset(endX, endY), size / 20f, paint)
                paint.style = PaintingStyle.Stroke
            }

            // Center hexagon
            paint.style = PaintingStyle.Fill
            val hexRadius = size / 8f
            val hexPath = Path().apply {
                for (i in 0..5) {
                    val hexAngle = (i * 60f) * (PI / 180f).toFloat()
                    val x = center + cos(hexAngle) * hexRadius
                    val y = center + sin(hexAngle) * hexRadius
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            canvas.drawPath(hexPath, paint)
        }

        DetailLevel.MEDIUM -> {
            // Medium detail - main arms with single short branches
            val branchRadius = mainRadius * 0.25f

            for (i in 0..5) {
                val angle = (i * 60f) * (PI / 180f).toFloat()
                val endX = center + cos(angle) * mainRadius
                val endY = center + sin(angle) * mainRadius

                // Main arm
                paint.strokeWidth = size / 18f
                canvas.drawLine(
                    Offset(center, center),
                    Offset(endX, endY),
                    paint
                )

                // Single pair of branches at midpoint
                paint.strokeWidth = size / 30f
                val branchStart = 0.5f
                val branchX = center + cos(angle) * mainRadius * branchStart
                val branchY = center + sin(angle) * mainRadius * branchStart

                val leftAngle = angle - PI.toFloat() / 3
                val leftEndX = branchX + cos(leftAngle) * branchRadius
                val leftEndY = branchY + sin(leftAngle) * branchRadius
                canvas.drawLine(Offset(branchX, branchY), Offset(leftEndX, leftEndY), paint)

                val rightAngle = angle + PI.toFloat() / 3
                val rightEndX = branchX + cos(rightAngle) * branchRadius
                val rightEndY = branchY + sin(rightAngle) * branchRadius
                canvas.drawLine(Offset(branchX, branchY), Offset(rightEndX, rightEndY), paint)
            }

            // Small center dot
            paint.style = PaintingStyle.Fill
            canvas.drawCircle(Offset(center, center), size / 12f, paint)
        }

        DetailLevel.LOW -> {
            // Simple detail - just 6 arms with center dot
            paint.strokeWidth = size / 20f

            for (i in 0..5) {
                val angle = (i * 60f) * (PI / 180f).toFloat()
                val endX = center + cos(angle) * mainRadius
                val endY = center + sin(angle) * mainRadius

                canvas.drawLine(
                    Offset(center, center),
                    Offset(endX, endY),
                    paint
                )
            }

            // Center dot
            paint.style = PaintingStyle.Fill
            canvas.drawCircle(Offset(center, center), size / 15f, paint)
        }
    }

    return bitmap
}

/**
 * A soft round dot fading out from its center, for flakes too far away to be in focus.
 */
private fun createBokehBitmap(size: Int, color: Color): ImageBitmap {
    val bitmap = ImageBitmap(size, size)
    val center = size / 2f
    Canvas(bitmap).drawCircle(
        center = Offset(center, center),
        radius = center,
        paint = Paint().apply {
            shader = RadialGradientShader(
                center = Offset(center, center),
                radius = center,
                colors = listOf(color, color.copy(alpha = 0.6f), Color.Transparent),
                colorStops = listOf(0f, 0.45f, 1f)
            )
        }
    )
    return bitmap
}

private data class SnowflakeData(
    val x: Float,
    val initialProgress: Float,
    val fallSpeed: Int,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val size: Float,
    val rotationSpeed: Int,
    val rotationDirection: Float,
    val initialRotation: Float,
    val swayPhaseOffset: Float,
    val seed: Float, // Varies where each pass of the flake starts and melts
    val depth: Float,
    val layer: Int // 0 = close, 1 = middle, 2 = far
)
