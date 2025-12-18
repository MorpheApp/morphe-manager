package app.revanced.manager.ui.component.morphe.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/**
 * Waves background - flowing sine waves
 */
@Composable
fun WavesBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "waves")

    // Wave configurations
    val waveConfigs = remember {
        listOf(
            WaveConfig(0.14f, 4f, 80f, 12000, false, 8f),
            WaveConfig(0.3f, 3f, 85f, 15000, true, 7f),
            WaveConfig(0.46f, 3.5f, 90f, 10000, false, 7f),
            WaveConfig(0.62f, 5f, 85f, 13000, true, 7f),
            WaveConfig(0.78f, 4.5f, 75f, 14000, false, 6f),
            WaveConfig(0.92f, 3.8f, 70f, 11000, true, 6f)
        )
    }

    // Create phase animations for all waves
    val wavePhases = waveConfigs.map { config ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(config.duration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase${config.yPosition}"
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        waveConfigs.forEachIndexed { index, config ->
            val phase = wavePhases[index].value
            val baseY = height * config.yPosition
            val phaseMultiplier = if (config.invertPhase) -1f else 1f

            val path = Path().apply {
                // Calculate initial Y position
                val startY = baseY + sin(phase * phaseMultiplier) * config.amplitude
                moveTo(0f, startY)

                // Draw wave with optimized step size
                for (x in 0..width.toInt() step 5) {
                    val normalizedX = x / width
                    val yPos = baseY + sin(
                        normalizedX * config.frequency * PI.toFloat() +
                                phase * phaseMultiplier
                    ) * config.amplitude
                    lineTo(x.toFloat(), yPos)
                }
            }

            // Select color
            val color = when (index % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }

            // Calculate alpha based on position
            val alpha = 0.15f - (index * 0.01f)

            drawPath(
                path = path,
                color = color.copy(alpha = alpha),
                style = Stroke(width = config.strokeWidth)
            )
        }
    }
}

private data class WaveConfig(
    val yPosition: Float,       // Vertical position (0-1)
    val frequency: Float,       // Wave frequency
    val amplitude: Float,       // Wave amplitude
    val duration: Int,          // Animation duration
    val invertPhase: Boolean,   // Reverse phase direction
    val strokeWidth: Float      // Line thickness
)
