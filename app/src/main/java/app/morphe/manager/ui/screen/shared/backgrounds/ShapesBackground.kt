package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometric shapes floating in space with parallax effect.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion shapes spin up and scatter outward from screen centre, then drift
 * back to their positions.
 */
@Composable
fun ShapesBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f,
        context = context,
        coroutineScope = coroutineScope
    )

    // Shape configurations
    val shapes = remember {
        listOf(
            ShapeConfig(0.1f,  0.15f, 0.3f,  0.2f,  30000, 0f,   ShapeType.TRIANGLE, 0.8f),
            ShapeConfig(0.7f,  0.1f,  0.9f,  0.15f, 28000, 0f,   ShapeType.SQUARE,   0.6f),
            ShapeConfig(0.15f, 0.38f, 0.35f, 0.48f, 32000, 0f,   ShapeType.PENTAGON, 0.5f),
            ShapeConfig(0.8f,  0.4f,  0.68f, 0.32f, 29000, 0f,   ShapeType.TRIANGLE, 0.4f),
            ShapeConfig(0.22f, 0.62f, 0.35f, 0.72f, 31000, 180f, ShapeType.SQUARE,   0.7f),
            ShapeConfig(0.75f, 0.65f, 0.62f, 0.55f, 27000, 0f,   ShapeType.PENTAGON, 0.6f),
            ShapeConfig(0.15f, 0.85f, 0.32f, 0.92f, 33000, 180f, ShapeType.TRIANGLE, 0.5f),
            ShapeConfig(0.72f, 0.88f, 0.58f, 0.82f, 26000, 0f,   ShapeType.SQUARE,   0.4f)
        )
    }

    val time = rememberAnimatedTime(speedMultiplier)

    // scatterProgress 0→1: shapes fly outward from screen centre with a spin boost, then fade.
    // Snaps back to 0f after completion so shapes return to normal state
    val scatterProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            scatterProgress.snapTo(0f)
            scatterProgress.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
            )
            // Smooth return - shapes drift back to their positions and fade in
            scatterProgress.animateTo(
                targetValue   = 0f,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t        = time.value
        val tiltX    = parallaxState.tiltX.value
        val tiltY    = parallaxState.tiltY.value
        val twoPi    = 2f * PI.toFloat()
        val sp       = scatterProgress.value
        val screenCx = size.width  * 0.5f
        val screenCy = size.height * 0.5f

        shapes.forEachIndexed { index, config ->
            val halfX = (config.endX - config.startX) / 2f
            val halfY = (config.endY - config.startY) / 2f
            val x = config.startX + halfX + halfX * sin(t * twoPi / config.duration)
            val y = config.startY + halfY + halfY * sin(t * twoPi / (config.duration * 1.1f))

            // Continuous rotation - full 360° per (duration * 2) ms.
            // During scatter: add up to +720° spin boost for a "flung away" feel
            val baseRotation = (config.initialRotation + t * 360f / (config.duration * 2f)) % 360f
            val scatterSpin  = sp * 720f * (1f + index * 0.15f)
            val rotation     = baseRotation + scatterSpin

            val parallaxStrength = config.depth * 50f
            val baseCx = size.width  * x + tiltX * parallaxStrength
            val baseCy = size.height * y + tiltY * parallaxStrength

            // Fly outward from screen centre; each shape goes in its own direction.
            // Ease-out so motion is fast at start and settles - avoids infinite drift
            val dirX  = baseCx - screenCx
            val dirY  = baseCy - screenCy
            val eased = 1f - (1f - sp) * (1f - sp)  // ease-out quad
            val centerX = baseCx + dirX * eased * 1.4f
            val centerY = baseCy + dirY * eased * 1.4f

            val alpha = if (sp > 0f) (1f - sp).coerceIn(0f, 1f) * 0.12f else 0.12f

            val color = when (index % 3) {
                0    -> primaryColor
                1    -> secondaryColor
                else -> tertiaryColor
            }.copy(alpha = alpha)

            // Rotate and draw shape
            rotate(rotation, Offset(centerX, centerY)) {
                when (config.type) {
                    ShapeType.TRIANGLE -> drawTriangle(centerX, centerY, 200f, color)
                    ShapeType.SQUARE   -> drawSquare  (centerX, centerY, 200f, color)
                    ShapeType.PENTAGON -> drawPentagon(centerX, centerY, 200f, color)
                }
            }
        }
    }
}

/**
 * Draw triangle outline.
 */
private fun DrawScope.drawTriangle(centerX: Float, centerY: Float, size: Float, color: Color) {
    val height   = size * 0.866f  // sqrt(3)/2
    val halfSize = size * 0.5f
    val path = Path().apply {
        moveTo(centerX, centerY - height * 0.5f)
        lineTo(centerX + halfSize, centerY + height * 0.5f)
        lineTo(centerX - halfSize, centerY + height * 0.5f)
        close()
    }
    drawPath(path, color, style = Stroke(width = 4f))
}

/**
 * Draw square outline.
 */
private fun DrawScope.drawSquare(centerX: Float, centerY: Float, size: Float, color: Color) {
    val halfSize = size * 0.5f
    val path = Path().apply {
        moveTo(centerX - halfSize, centerY - halfSize)
        lineTo(centerX + halfSize, centerY - halfSize)
        lineTo(centerX + halfSize, centerY + halfSize)
        lineTo(centerX - halfSize, centerY + halfSize)
        close()
    }
    drawPath(path, color, style = Stroke(width = 4f))
}

/**
 * Draw regular pentagon outline.
 */
private fun DrawScope.drawPentagon(centerX: Float, centerY: Float, size: Float, color: Color) {
    val radius = size / 2f
    val path   = Path()
    for (i in 0..5) {
        val angle = -PI / 2 + i * 2 * PI / 5
        val x = centerX + (radius * cos(angle)).toFloat()
        val y = centerY + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = 4f))
}

private enum class ShapeType {
    TRIANGLE,
    SQUARE,
    PENTAGON
}

private data class ShapeConfig(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val duration: Int,
    val initialRotation: Float,
    val type: ShapeType,
    val depth: Float // Depth for parallax effect
)
