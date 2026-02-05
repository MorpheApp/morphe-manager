package app.revanced.manager.ui.screen.shared.backgrounds

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometric shapes floating in space with parallax effect
 */
@Composable
fun ShapesBackground(
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

    val infiniteTransition = rememberInfiniteTransition(label = "shapes")

    // Shape configurations
    val shapes = remember {
        listOf(
            ShapeConfig(0.1f, 0.15f, 0.3f, 0.2f, 30000, 0f, ShapeType.TRIANGLE, 0.8f),
            ShapeConfig(0.7f, 0.1f, 0.9f, 0.15f, 28000, 0f, ShapeType.SQUARE, 0.6f),
            ShapeConfig(0.15f, 0.38f, 0.35f, 0.48f, 32000, 0f, ShapeType.PENTAGON, 0.5f),
            ShapeConfig(0.8f, 0.4f, 0.68f, 0.32f, 29000, 0f, ShapeType.TRIANGLE, 0.4f),
            ShapeConfig(0.22f, 0.62f, 0.35f, 0.72f, 31000, 180f, ShapeType.SQUARE, 0.7f),
            ShapeConfig(0.75f, 0.65f, 0.62f, 0.55f, 27000, 0f, ShapeType.PENTAGON, 0.6f),
            ShapeConfig(0.15f, 0.85f, 0.32f, 0.92f, 33000, 180f, ShapeType.TRIANGLE, 0.5f),
            ShapeConfig(0.72f, 0.88f, 0.58f, 0.82f, 26000, 0f, ShapeType.SQUARE, 0.4f)
        )
    }

    // Create animations for all shapes
    val shapeAnimations = shapes.map { config ->
        val x = infiniteTransition.animateFloat(
            initialValue = config.startX,
            targetValue = config.endX,
            animationSpec = BackgroundAnimationSpecs.verySlowFloat(config.duration),
            label = "shapeX${config.startX}"
        )
        val y = infiniteTransition.animateFloat(
            initialValue = config.startY,
            targetValue = config.endY,
            animationSpec = BackgroundAnimationSpecs.verySlowFloat((config.duration * 1.1f).toInt()),
            label = "shapeY${config.startY}"
        )
        val rotation = infiniteTransition.animateFloat(
            initialValue = config.initialRotation,
            targetValue = config.initialRotation + 360f,
            animationSpec = BackgroundAnimationSpecs.rotationAnimation(config.duration * 2),
            label = "shapeRot${config.initialRotation}"
        )
        Triple(x, y, rotation)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value

        shapeAnimations.forEachIndexed { index, (x, y, rotation) ->
            val color = when (index % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }.copy(alpha = 0.12f)

            val config = shapes[index]
            val parallaxStrength = config.depth * 50f
            val parallaxX = tiltX * parallaxStrength
            val parallaxY = tiltY * parallaxStrength

            val centerX = size.width * x.value + parallaxX
            val centerY = size.height * y.value + parallaxY
            val shapeSize = 200f

            // Rotate and draw shape
            rotate(rotation.value, Offset(centerX, centerY)) {
                when (config.type) {
                    ShapeType.TRIANGLE -> drawTriangle(centerX, centerY, shapeSize, color)
                    ShapeType.SQUARE -> drawSquare(centerX, centerY, shapeSize, color)
                    ShapeType.PENTAGON -> drawPentagon(centerX, centerY, shapeSize, color)
                }
            }
        }
    }
}

/**
 * Draw triangle outline
 */
private fun DrawScope.drawTriangle(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color
) {
    val height = size * 0.866f // sqrt(3)/2
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
 * Draw square outline
 */
private fun DrawScope.drawSquare(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color
) {
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
 * Draw regular pentagon outline
 */
private fun DrawScope.drawPentagon(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color
) {
    val radius = size / 2f
    val path = Path()

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
    val depth: Float  // Depth for parallax effect
)
