package app.revanced.manager.ui.component.morphe.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.revanced.manager.ui.component.morphe.shared.isDarkBackground
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Snow background with falling snowflakes
 */
@Composable
fun SnowBackground(modifier: Modifier = Modifier) {
    val isDarkTheme = MaterialTheme.colorScheme.background.isDarkBackground()
    val snowColor = if (isDarkTheme) Color.White else Color(0xFF4A5F7A)

    // Create and cache snowflake bitmap
    val snowflakeBitmap = remember(snowColor) {
        createSnowflakeBitmap(30, snowColor)
    }

    // Generate snowflakes once
    val snowflakes = remember {
        List(30) {
            val size = 0.7f + Random.nextFloat() * 0.6f // 0.7-1.3 size multiplier
            SnowflakeData(
                x = Random.nextFloat(),
                initialProgress = Random.nextFloat(),
                fallSpeed = 8000 + Random.nextInt(4000),
                swayAmplitude = 0.02f + Random.nextFloat() * 0.03f,
                swayFrequency = 2f + Random.nextFloat() * 2f,
                size = size,
                rotationSpeed = 15000 + Random.nextInt(10000),
                initialRotation = Random.nextFloat() * 360f,
                depth = size // Use size for depth sorting
            )
        }.sortedBy { it.depth } // Smaller snowflakes drawn first
    }

    val infiniteTransition = rememberInfiniteTransition(label = "snow")

    // Create animations
    val snowflakeAnimations = snowflakes.map { flake ->
        val fallProgress = infiniteTransition.animateFloat(
            initialValue = flake.initialProgress,
            targetValue = flake.initialProgress + 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(flake.fallSpeed, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fall${flake.x}"
        )

        val swayPhase = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(flake.fallSpeed, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sway${flake.x}"
        )

        val rotation = infiniteTransition.animateFloat(
            initialValue = flake.initialRotation,
            targetValue = flake.initialRotation + 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(flake.rotationSpeed, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation${flake.x}"
        )

        Triple(fallProgress, swayPhase, rotation)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        snowflakeAnimations.forEachIndexed { index, (fallProgress, swayPhase, rotation) ->
            val flake = snowflakes[index]

            val progress = fallProgress.value % 1f
            val sway = sin(swayPhase.value * flake.swayFrequency) * flake.swayAmplitude

            val centerX = (flake.x + sway) * width
            val centerY = progress * height
            val drawSize = 30f * flake.size

            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(centerX, centerY)
                canvas.rotate(rotation.value)

                // Draw cached bitmap
                canvas.drawImageRect(
                    image = snowflakeBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(snowflakeBitmap.width, snowflakeBitmap.height),
                    dstOffset = IntOffset((-drawSize / 2).toInt(), (-drawSize / 2).toInt()),
                    dstSize = IntSize(drawSize.toInt(), drawSize.toInt()),
                    paint = Paint().apply {
                        alpha = 0.7f + (flake.depth * 0.3f) // Vary alpha by depth
                    }
                )

                canvas.restore()
            }
        }
    }
}

/**
 * Create cached snowflake bitmap
 */
private fun createSnowflakeBitmap(size: Int, color: Color): ImageBitmap {
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        strokeWidth = 1.5f
        strokeCap = StrokeCap.Round
    }

    val center = size / 2f
    val radius = size / 2.5f

    // Draw 6 arms
    for (i in 0..5) {
        val angle = (i * 60f) * (PI / 180f).toFloat()
        val endX = center + cos(angle) * radius
        val endY = center + sin(angle) * radius

        canvas.drawLine(
            Offset(center, center),
            Offset(endX, endY),
            paint
        )
    }

    // Center dot
    paint.style = PaintingStyle.Fill
    canvas.drawCircle(Offset(center, center), size / 10f, paint)

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
    val depth: Float
)
