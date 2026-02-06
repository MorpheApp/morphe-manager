package app.revanced.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Original circles background with parallax effect
 */
@Composable
fun CirclesBackground(
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

    val infiniteTransition = rememberInfiniteTransition(label = "circles")

    // Circle 1 - large top left
    val circle1X = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.25f,
        animationSpec = BackgroundAnimationSpecs.slowFloat(8000),
        label = "circle1X"
    )
    val circle1Y = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.2f,
        animationSpec = BackgroundAnimationSpecs.mediumFloat(7000),
        label = "circle1Y"
    )

    // Circle 2 - medium top right
    val circle2X = infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 0.82f,
        animationSpec = BackgroundAnimationSpecs.slowFloat(9000),
        label = "circle2X"
    )
    val circle2Y = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.22f,
        animationSpec = BackgroundAnimationSpecs.fastFloat(6500),
        label = "circle2Y"
    )

    // Circle 3 - small center right
    val circle3X = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.68f,
        animationSpec = BackgroundAnimationSpecs.mediumFloat(7500),
        label = "circle3X"
    )
    val circle3Y = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.48f,
        animationSpec = BackgroundAnimationSpecs.slowFloat(8500),
        label = "circle3Y"
    )

    // Circle 4 - medium bottom right
    val circle4X = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.78f,
        animationSpec = BackgroundAnimationSpecs.slowFloat(9500),
        label = "circle4X"
    )
    val circle4Y = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.82f,
        animationSpec = BackgroundAnimationSpecs.mediumFloat(7200),
        label = "circle4Y"
    )

    // Circle 5 - small bottom left
    val circle5X = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.28f,
        animationSpec = BackgroundAnimationSpecs.slowFloat(8200),
        label = "circle5X"
    )
    val circle5Y = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.73f,
        animationSpec = BackgroundAnimationSpecs.fastFloat(6800),
        label = "circle5Y"
    )

    // Circle 6 - bottom center
    val circle6X = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.55f,
        animationSpec = BackgroundAnimationSpecs.slowFloat(8800),
        label = "circle6X"
    )
    val circle6Y = infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.87f,
        animationSpec = BackgroundAnimationSpecs.mediumFloat(7800),
        label = "circle6Y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value

        // Circle configs with depth for parallax
        val circles = listOf(
            CircleData(circle1X.value, circle1Y.value, 400f, primaryColor, 0.05f, 0.8f),
            CircleData(circle2X.value, circle2Y.value, 280f, tertiaryColor, 0.035f, 0.6f),
            CircleData(circle3X.value, circle3Y.value, 200f, tertiaryColor, 0.04f, 0.4f),
            CircleData(circle4X.value, circle4Y.value, 320f, secondaryColor, 0.035f, 0.7f),
            CircleData(circle5X.value, circle5Y.value, 180f, primaryColor, 0.04f, 0.5f),
            CircleData(circle6X.value, circle6Y.value, 220f, secondaryColor, 0.04f, 0.6f)
        )

        circles.forEach { circle ->
            val parallaxStrength = circle.depth * 50f
            val parallaxX = tiltX * parallaxStrength
            val parallaxY = tiltY * parallaxStrength

            drawCircle(
                color = circle.color.copy(alpha = circle.alpha),
                radius = circle.radius,
                center = Offset(
                    size.width * circle.x + parallaxX,
                    size.height * circle.y + parallaxY
                )
            )
        }
    }
}

private data class CircleData(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: androidx.compose.ui.graphics.Color,
    val alpha: Float,
    val depth: Float
)
