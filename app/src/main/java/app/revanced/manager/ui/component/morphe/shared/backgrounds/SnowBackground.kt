package app.revanced.manager.ui.component.morphe.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.revanced.manager.ui.component.morphe.utils.isDarkBackground
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
            SnowflakeData(
                x = Random.nextFloat(),
                initialProgress = Random.nextFloat(),
                fallSpeed = 10000 + Random.nextInt(5000), // 10-15 seconds
                swayAmplitude = 0.03f + Random.nextFloat() * 0.04f,
                swayFrequency = 1.5f + Random.nextFloat() * 1.5f, // Slower sway
                size = 0.7f + Random.nextFloat() * 0.6f, // 0.7-1.3
                rotationSpeed = 20000 + Random.nextInt(15000), // Slower rotation
                initialRotation = Random.nextFloat() * 360f,
                swayPhaseOffset = Random.nextFloat() * 2f * PI.toFloat()
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "snow")

    // Single global time for smoother animations
    val globalTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing), // 1 minute cycle
            repeatMode = RepeatMode.Restart
        ),
        label = "globalTime"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        snowflakes.forEach { flake ->
            // Calculate fall progress based on global time
            val normalizedSpeed = 60000f / flake.fallSpeed
            val fallProgress = ((flake.initialProgress + globalTime * normalizedSpeed) % 1f)

            // Calculate sway using global time
            val swayTime = globalTime * 60000f / flake.fallSpeed
            val sway = sin((swayTime * 2f * PI.toFloat() * flake.swayFrequency) + flake.swayPhaseOffset) * flake.swayAmplitude

            // Calculate rotation
            val rotation = (globalTime * 60000f / flake.rotationSpeed * 360f + flake.initialRotation) % 360f

            val centerX = (flake.x + sway) * width
            val centerY = fallProgress * height
            val drawSize = 30f * flake.size

            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(centerX, centerY)
                canvas.rotate(rotation)

                // Draw cached bitmap
                canvas.drawImageRect(
                    image = snowflakeBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(snowflakeBitmap.width, snowflakeBitmap.height),
                    dstOffset = IntOffset((-drawSize / 2).toInt(), (-drawSize / 2).toInt()),
                    dstSize = IntSize(drawSize.toInt(), drawSize.toInt()),
                    paint = Paint().apply {
                        alpha = 0.65f + (flake.size * 0.25f) // Alpha based on size
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
    val swayPhaseOffset: Float
)
