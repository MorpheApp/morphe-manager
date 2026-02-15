package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.morphe.manager.util.isDarkBackground
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Snow background with falling snowflakes with parallax effect
 */
@Composable
fun SnowBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.isDarkBackground()
    val snowColor = if (isDarkTheme) Color.White else Color(0xFF4A5F7A)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.2f,
        context = context,
        coroutineScope = coroutineScope
    )

    // Create cached snowflake bitmaps with different detail levels
    val snowflakeBitmaps = remember(snowColor) {
        listOf(
            createDetailedSnowflakeBitmap(40, snowColor, DetailLevel.HIGH),    // Close - highly detailed
            createDetailedSnowflakeBitmap(30, snowColor, DetailLevel.MEDIUM),  // Middle - medium detail
            createDetailedSnowflakeBitmap(20, snowColor, DetailLevel.LOW)      // Far - simple
        )
    }

    // Generate snowflakes with depth layers
    val snowflakes = remember {
        List(40) {
            val depth = Random.nextFloat()
            val layer = when {
                depth < 0.33f -> 0  // Close layer
                depth < 0.66f -> 1  // Middle layer
                else -> 2           // Far layer
            }

            SnowflakeData(
                x = Random.nextFloat(),
                initialProgress = Random.nextFloat(),
                fallSpeed = when (layer) {
                    0 -> 8000 + Random.nextInt(3000)   // Fast (close)
                    1 -> 12000 + Random.nextInt(4000)  // Medium
                    else -> 16000 + Random.nextInt(5000) // Slow (far)
                },
                swayAmplitude = when (layer) {
                    0 -> 0.04f + Random.nextFloat() * 0.03f
                    1 -> 0.03f + Random.nextFloat() * 0.02f
                    else -> 0.02f + Random.nextFloat() * 0.015f
                },
                swayFrequency = 1.2f + Random.nextFloat() * 1.0f,
                size = when (layer) {
                    0 -> 0.9f + Random.nextFloat() * 0.4f   // 0.9-1.3
                    1 -> 0.7f + Random.nextFloat() * 0.3f   // 0.7-1.0
                    else -> 0.5f + Random.nextFloat() * 0.2f // 0.5-0.7
                },
                rotationSpeed = 15000 + Random.nextInt(10000),
                initialRotation = Random.nextFloat() * 360f,
                swayPhaseOffset = Random.nextFloat() * 2f * PI.toFloat(),
                depth = depth,
                layer = layer
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "snow")

    // Single global time for smoother animations
    val globalTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 120000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 120000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "globalTime"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value

        // Calculate fade multiplier for smooth loop transition
        val fadeDuration = 2000f // 2 seconds fade
        val cycleFade = when {
            globalTime < fadeDuration -> globalTime / fadeDuration // Fade in at start
            globalTime > 120000f - fadeDuration -> (120000f - globalTime) / fadeDuration // Fade out at end
            else -> 1f
        }

        // Sort snowflakes by depth (far to close) for proper layering
        snowflakes.sortedBy { it.depth }.forEach { flake ->
            // Calculate continuous fall progress
            val timeProgress = globalTime / flake.fallSpeed
            val fallProgress = (flake.initialProgress + timeProgress) % 1f

            // Calculate sway - continuous wave
            val swayPhase = timeProgress * 2f * PI.toFloat() * flake.swayFrequency + flake.swayPhaseOffset
            val sway = sin(swayPhase) * flake.swayAmplitude

            // Calculate rotation - continuous
            val rotation = (timeProgress * 360000f / flake.rotationSpeed + flake.initialRotation) % 360f

            // Apply parallax with depth-based strength
            val parallaxStrength = flake.depth * 40f
            val parallaxX = tiltX * parallaxStrength
            val parallaxY = tiltY * parallaxStrength

            // Calculate position with smooth wrapping
            val baseX = (flake.x + sway) * width + parallaxX
            val baseY = fallProgress * (height + 100f) - 50f + parallaxY

            // Wrap X position for horizontal parallax
            val centerX = when {
                baseX < -50f -> baseX + width + 100f
                baseX > width + 50f -> baseX - width - 100f
                else -> baseX
            }

            val centerY = baseY

            // Get bitmap for this layer
            val bitmap = snowflakeBitmaps[flake.layer]
            val drawSize = bitmap.width.toFloat() * flake.size

            // Calculate alpha with edge fade for seamless loop
            val depthAlpha = 0.35f + (flake.depth * 0.55f)
            val edgeFade = when {
                centerY < 0f -> ((centerY + 50f) / 50f).coerceIn(0f, 1f)
                centerY > height -> ((height + 50f - centerY) / 50f).coerceIn(0f, 1f)
                else -> 1f
            }

            // Apply cycle fade for smooth transitions
            val finalAlpha = depthAlpha * (0.7f + flake.size * 0.3f) * edgeFade * cycleFade

            // Only draw if visible
            if (finalAlpha > 0.01f && centerY > -50f && centerY < height + 50f) {
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.translate(centerX, centerY)
                    canvas.rotate(rotation)

                    canvas.drawImageRect(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset((-drawSize / 2).toInt(), (-drawSize / 2).toInt()),
                        dstSize = IntSize(drawSize.toInt(), drawSize.toInt()),
                        paint = Paint().apply {
                            alpha = finalAlpha
                        }
                    )

                    canvas.restore()
                }
            }
        }
    }
}

/**
 * Detail level for snowflake rendering
 */
private enum class DetailLevel {
    HIGH,    // Close - full detail with branches
    MEDIUM,  // Middle - main arms with minimal branches
    LOW      // Far - simple star shape
}

/**
 * Create snowflake bitmap with varying detail levels
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

private data class SnowflakeData(
    val x: Float,
    val initialProgress: Float,
    val fallSpeed: Int,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val size: Float,
    val rotationSpeed: Int,
    val initialRotation: Float,
    val swayPhaseOffset: Float,
    val depth: Float,
    val layer: Int  // 0 = close, 1 = middle, 2 = far
)
