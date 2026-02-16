package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext

/**
 * Rings background - concentric circles with stroke and parallax effect
 */
@Composable
fun RingsBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f,
        context = context,
        coroutineScope = coroutineScope
    )

    val infiniteTransition = rememberInfiniteTransition(label = "rings")

    // Ring configurations - defined once
    val ringConfigs = remember {
        listOf(
            RingConfig(0.2f, 0.2f, 0.3f, 0.25f, 9000, 8000, listOf(140f, 190f, 240f), 0.8f),
            RingConfig(0.85f, 0.15f, 0.8f, 0.2f, 10000, 7500, listOf(130f, 180f), 0.6f),
            RingConfig(0.5f, 0.5f, 0.55f, 0.55f, 8500, 9500, listOf(110f, 160f, 210f), 0.5f),
            RingConfig(0.15f, 0.75f, 0.2f, 0.8f, 7000, 8000, listOf(150f, 200f), 0.7f),
            RingConfig(0.8f, 0.85f, 0.85f, 0.8f, 8800, 7600, listOf(120f, 170f, 220f), 0.6f),
            RingConfig(0.75f, 0.4f, 0.8f, 0.45f, 9200, 8400, listOf(135f, 185f), 0.4f)
        )
    }

    // Create animations for all rings
    val ringAnimations = ringConfigs.map { config ->
        val x = infiniteTransition.animateFloat(
            initialValue = config.startX,
            targetValue = config.endX,
            animationSpec = BackgroundAnimationSpecs.slowFloat(config.durationX),
            label = "ringX${config.startX}"
        )
        val y = infiniteTransition.animateFloat(
            initialValue = config.startY,
            targetValue = config.endY,
            animationSpec = BackgroundAnimationSpecs.mediumFloat(config.durationY),
            label = "ringY${config.startY}"
        )
        Pair(x, y)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value

        ringAnimations.forEachIndexed { index, (x, y) ->
            val config = ringConfigs[index]
            val parallaxStrength = config.depth * 50f
            val parallaxX = tiltX * parallaxStrength
            val parallaxY = tiltY * parallaxStrength

            val center = Offset(
                size.width * x.value + parallaxX,
                size.height * y.value + parallaxY
            )

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
    val radii: List<Float>,
    val depth: Float  // Depth for parallax effect
)
