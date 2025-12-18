package app.revanced.manager.ui.component.morphe.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Rings background - concentric circles with stroke
 */
@Composable
fun RingsBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "rings")

    // Ring configurations - defined once
    val ringConfigs = remember {
        listOf(
            RingConfig(0.2f, 0.2f, 0.3f, 0.25f, 9000, 8000, listOf(140f, 190f, 240f)),
            RingConfig(0.85f, 0.15f, 0.8f, 0.2f, 10000, 7500, listOf(130f, 180f)),
            RingConfig(0.5f, 0.5f, 0.55f, 0.55f, 8500, 9500, listOf(110f, 160f, 210f)),
            RingConfig(0.15f, 0.75f, 0.2f, 0.8f, 7000, 8000, listOf(150f, 200f)),
            RingConfig(0.8f, 0.85f, 0.85f, 0.8f, 8800, 7600, listOf(120f, 170f, 220f)),
            RingConfig(0.75f, 0.4f, 0.8f, 0.45f, 9200, 8400, listOf(135f, 185f))
        )
    }

    // Create animations for all rings
    val ringAnimations = ringConfigs.map { config ->
        val x = infiniteTransition.animateFloat(
            initialValue = config.startX,
            targetValue = config.endX,
            animationSpec = infiniteRepeatable(
                animation = tween(config.durationX),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ringX${config.startX}"
        )
        val y = infiniteTransition.animateFloat(
            initialValue = config.startY,
            targetValue = config.endY,
            animationSpec = infiniteRepeatable(
                animation = tween(config.durationY),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ringY${config.startY}"
        )
        Pair(x, y)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        ringAnimations.forEachIndexed { index, (x, y) ->
            val center = Offset(size.width * x.value, size.height * y.value)
            val config = ringConfigs[index]

            // Select color based on index
            val baseColor = when (index % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }

            // Draw multiple concentric rings
            config.radii.forEachIndexed { ringIndex, radius ->
                val alpha = when (ringIndex) {
                    0 -> 0.14f
                    1 -> 0.10f
                    2 -> 0.07f
                    else -> 0.06f
                }
                val strokeWidth = when (ringIndex) {
                    0 -> 6f
                    1 -> 5f
                    else -> 4f
                }

                drawCircle(
                    color = baseColor.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}

private data class RingConfig(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationX: Int,
    val durationY: Int,
    val radii: List<Float>  // Multiple ring radii
)
