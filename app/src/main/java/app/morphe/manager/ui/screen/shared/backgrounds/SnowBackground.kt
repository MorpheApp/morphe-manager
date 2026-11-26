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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.theme.isDarkTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
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

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.2f
    )

    // Create cached snowflake bitmaps with different detail levels, sized in dp so a flake looks
    // the same on any screen
    val density = LocalDensity.current.density
    val snowflakeBitmaps = remember(snowColor, density) {
        listOf(
            createDetailedSnowflakeBitmap(flakePx(13.3f, density), snowColor, DetailLevel.HIGH),   // Close - highly detailed
            createDetailedSnowflakeBitmap(flakePx(10f, density), snowColor, DetailLevel.MEDIUM),   // Middle - medium detail
            createDetailedSnowflakeBitmap(flakePx(6.7f, density), snowColor, DetailLevel.LOW)      // Far - simple
        )
    }
    // The far layer is out of focus, so it is drawn as soft dots rather than as flakes
    val bokehBitmap = remember(snowColor, density) { createBokehBitmap(flakePx(13.3f, density), snowColor) }

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

            // Milliseconds per fall down the screen
            val fallMs = when (layer) {
                0 -> 15000f + Random.nextInt(5000)    // Fast (close)
                1 -> 22000f + Random.nextInt(6000)    // Medium
                else -> 30000f + Random.nextInt(8000) // Slow (far)
            }
            val passes = wholeLoops(fallMs)
            val swaysPerPass = 1.2f + Random.nextFloat() * 1.0f
            // Milliseconds per full turn, at a pace of its own
            val turnMs = fallMs * (8f + Random.nextFloat() * 22f)

            SnowflakeData(
                x = Random.nextFloat(),
                initialProgress = Random.nextFloat(),
                passes = passes,
                swayAmplitude = when (layer) {
                    0 -> 0.04f + Random.nextFloat() * 0.03f
                    1 -> 0.03f + Random.nextFloat() * 0.02f
                    else -> 0.02f + Random.nextFloat() * 0.015f
                },
                sways = (passes * swaysPerPass).roundToInt(),
                size = when (layer) {
                    0 -> 0.75f + Random.nextFloat() * 0.35f  // 0.75-1.1
                    1 -> 0.6f + Random.nextFloat() * 0.25f   // 0.6-0.85
                    else -> 0.45f + Random.nextFloat() * 0.2f // 0.45-0.65
                },
                turns = wholeLoops(turnMs) * (if (Random.nextBoolean()) 1 else -1),
                initialRotation = Random.nextFloat() * 360f,
                swayPhaseOffset = Random.nextFloat() * TWO_PI,
                seed = Random.nextFloat() * 1000f,
                depth = depth,
                layer = layer
            )
        }.sortedBy { it.depth }
    }

    // One paint for every flake, its alpha set per draw, rather than a fresh one per flake per frame
    val flakePaint = remember { Paint() }

    // Frame-based time - accumulates in ms at speed 1x, respects speedMultiplier smoothly
    val animatedTime = rememberAnimatedTime(speedMultiplier)

    // Blizzard burst: snowflakes suddenly fly upward and fade out on completion,
    // then settle back down and fade in gently
    val burstProgress = rememberCompletionPulse(patchingCompleted, riseMillis = 1400, fallMillis = 600)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val tiltX = parallaxState.tiltX.value * density
        val tiltY = parallaxState.tiltY.value * density
        val time = animatedTime.value
        // Every motion of a flake repeats a whole number of times per loop of the clock, so the
        // fall carries on across the wrap without a seam
        val loop = time / BACKGROUND_LOOP_MS
        val bp = burstProgress.value
        val burstFade = if (bp > 0f) (1f - bp).coerceIn(0f, 1f) else 1f
        val edge = 17.dp.toPx()

        // Wind: two slow waves, both whole fractions of the loop, make gusts that come and go.
        // Squaring keeps the calm stretches calm and lets the gusts stand out
        val wave = 0.65f * sin(time * TWO_PI / 24000f) +
                0.35f * sin(time * TWO_PI / 8000f + 1.3f)
        val gust = wave * abs(wave)

        snowflakes.forEach { flake ->
            // Calculate continuous fall progress
            val travel = flake.initialProgress + loop * flake.passes
            val fallProgress = fract(travel)

            // Every pass down the screen starts from a fresh column and melts at its own height,
            // so the fall never repeats the same tracks. The pass in flight when the clock wraps
            // counts as the first of the next loop, so it keeps its column
            val pass = (travel.toInt() % flake.passes).toFloat()
            val column = fract(sin(pass * 12.9898f + flake.seed) * 43758.547f)
            val meltAt = MELT_FROM + (1f - MELT_FROM) * fract(sin(pass * 78.233f + flake.seed) * 12543.123f).pow(1.4f)
            val fadeIn = (fallProgress / FLAKE_FADE).coerceIn(0f, 1f)
            val fadeOut = ((meltAt - fallProgress) / FLAKE_FADE).coerceIn(0f, 1f)
            val life = min(fadeIn, fadeOut).let { it * it * (3f - 2f * it) }
            if (life <= 0f) return@forEach

            // Calculate sway - continuous wave
            val sway = sin(loop * TWO_PI * flake.sways + flake.swayPhaseOffset) * flake.swayAmplitude

            // Calculate rotation - continuous, each flake at its own pace and in its own direction
            val rotation = (loop * 360f * flake.turns + flake.initialRotation) % 360f

            // Apply parallax with depth-based strength
            val parallaxStrength = flake.depth * 13f
            val parallaxX = tiltX * parallaxStrength
            val parallaxY = tiltY * parallaxStrength

            // Calculate position with smooth wrapping
            // During burst: flakes fly upward (negative Y offset) proportional to speed
            val burstLift = if (bp > 0f) bp * (height + edge * 4f) * (1f + flake.depth * 0.5f) else 0f
            val wind = gust * (0.03f + 0.07f * flake.depth)
            val baseX = (fract(flake.x + column) + sway + wind) * width + parallaxX
            val baseY = fallProgress * (height + edge * 2f) - edge + parallaxY - burstLift

            // Wrap X position for horizontal parallax
            val centerX = when {
                baseX < -edge -> baseX + width + edge * 2f
                baseX > width + edge -> baseX - width - edge * 2f
                else -> baseX
            }

            // Get bitmap for this layer
            val bitmap = if (flake.layer == 2) bokehBitmap else snowflakeBitmaps[flake.layer]
            val drawSize = bitmap.width.toFloat() * flake.size

            // Alpha fades in and out past the top and bottom edges
            val depthAlpha = 0.35f + (flake.depth * 0.55f)
            val edgeFade = when {
                baseY < 0f -> ((baseY + edge) / edge).coerceIn(0f, 1f)
                baseY > height -> ((height + edge - baseY) / edge).coerceIn(0f, 1f)
                else -> 1f
            }

            // Burst fade: flakes vanish as they fly up
            val finalAlpha = depthAlpha * (0.7f + flake.size * 0.3f) * edgeFade * burstFade * life

            // Only draw if visible
            if (finalAlpha > 0.01f && baseY > -edge && baseY < height + edge) {
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

/** Side of a flake bitmap drawn [dp] wide, in whole pixels. */
private fun flakePx(dp: Float, density: Float) = (dp * density).roundToInt().coerceAtLeast(1)

// Shortest share of the fall a flake lives before it melts away, and how much of the fall its fade
// in and out each take
private const val MELT_FROM = 0.4f
private const val FLAKE_FADE = 0.12f

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
    val passes: Int, // Falls down the screen per loop of the clock
    val swayAmplitude: Float,
    val sways: Int, // Sways per loop of the clock
    val size: Float,
    val turns: Int, // Turns per loop of the clock, negative for counterclockwise
    val initialRotation: Float,
    val swayPhaseOffset: Float,
    val seed: Float, // Varies where each pass of the flake starts and melts
    val depth: Float,
    val layer: Int // 0 = close, 1 = middle, 2 = far
)
