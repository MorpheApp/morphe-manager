package app.revanced.manager.ui.component.morphe.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * Particles background - blobs moving in defined zones
 */
@Composable
fun ParticlesBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    // Particle configurations
    val particles = remember {
        listOf(
            // Top zone
            ParticleConfig(0.1f, 0.1f, 0.25f, 0.2f, 50f, 18000),
            ParticleConfig(0.75f, 0.15f, 0.9f, 0.25f, 42f, 20000),
            ParticleConfig(0.4f, 0.15f, 0.55f, 0.25f, 43f, 20500),

            // Upper middle zone
            ParticleConfig(0.15f, 0.35f, 0.3f, 0.45f, 46f, 19000),
            ParticleConfig(0.75f, 0.4f, 0.85f, 0.5f, 43f, 21000),
            ParticleConfig(0.45f, 0.38f, 0.58f, 0.48f, 44f, 19500),

            // Lower middle zone
            ParticleConfig(0.2f, 0.55f, 0.35f, 0.65f, 44f, 17500),
            ParticleConfig(0.7f, 0.58f, 0.85f, 0.68f, 49f, 16500),
            ParticleConfig(0.48f, 0.57f, 0.62f, 0.67f, 46f, 18200),

            // Bottom zone
            ParticleConfig(0.15f, 0.75f, 0.3f, 0.85f, 47f, 18500),
            ParticleConfig(0.7f, 0.78f, 0.88f, 0.88f, 45f, 19500),
            ParticleConfig(0.42f, 0.77f, 0.58f, 0.87f, 47f, 17800)
        )
    }

    // Create animations for all particles
    val particleAnimations = particles.map { config ->
        val x = infiniteTransition.animateFloat(
            initialValue = config.startX,
            targetValue = config.endX,
            animationSpec = infiniteRepeatable(
                animation = tween(config.duration, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "particleX${config.startX}"
        )
        val y = infiniteTransition.animateFloat(
            initialValue = config.startY,
            targetValue = config.endY,
            animationSpec = infiniteRepeatable(
                animation = tween((config.duration * 0.85f).toInt(), easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "particleY${config.startY}"
        )
        Pair(x, y)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        particleAnimations.forEachIndexed { index, (x, y) ->
            // Cycle through colors
            val color = when (index % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }

            drawCircle(
                color = color.copy(alpha = 0.14f),
                radius = particles[index].size,
                center = Offset(size.width * x.value, size.height * y.value)
            )
        }
    }
}

private data class ParticleConfig(
    val startX: Float,      // Starting X position (0-1)
    val startY: Float,      // Starting Y position (0-1)
    val endX: Float,        // Ending X position (0-1)
    val endY: Float,        // Ending Y position (0-1)
    val size: Float,        // Particle radius
    val duration: Int       // Animation duration in ms
)
