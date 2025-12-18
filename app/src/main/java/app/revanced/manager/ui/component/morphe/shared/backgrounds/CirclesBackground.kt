package app.revanced.manager.ui.component.morphe.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * Original circles background
 */
@Composable
fun CirclesBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "circles")

    // Circle 1 - large top left
    val circle1X = infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000), repeatMode = RepeatMode.Reverse
        ), label = "circle1X"
    )
    val circle1Y = infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000), repeatMode = RepeatMode.Reverse
        ), label = "circle1Y"
    )

    // Circle 2 - medium top right
    val circle2X = infiniteTransition.animateFloat(
        initialValue = 0.88f, targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000), repeatMode = RepeatMode.Reverse
        ), label = "circle2X"
    )
    val circle2Y = infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500), repeatMode = RepeatMode.Reverse
        ), label = "circle2Y"
    )

    // Circle 3 - small center right
    val circle3X = infiniteTransition.animateFloat(
        initialValue = 0.75f, targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500), repeatMode = RepeatMode.Reverse
        ), label = "circle3X"
    )
    val circle3Y = infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500), repeatMode = RepeatMode.Reverse
        ), label = "circle3Y"
    )

    // Circle 4 - medium bottom right
    val circle4X = infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(9500), repeatMode = RepeatMode.Reverse
        ), label = "circle4X"
    )
    val circle4Y = infiniteTransition.animateFloat(
        initialValue = 0.75f, targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200), repeatMode = RepeatMode.Reverse
        ), label = "circle4Y"
    )

    // Circle 5 - small bottom left
    val circle5X = infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(8200), repeatMode = RepeatMode.Reverse
        ), label = "circle5X"
    )
    val circle5Y = infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.73f,
        animationSpec = infiniteRepeatable(
            animation = tween(6800), repeatMode = RepeatMode.Reverse
        ), label = "circle5Y"
    )

    // Circle 6 - bottom center
    val circle6X = infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(8800), repeatMode = RepeatMode.Reverse
        ), label = "circle6X"
    )
    val circle6Y = infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 0.87f,
        animationSpec = infiniteRepeatable(
            animation = tween(7800), repeatMode = RepeatMode.Reverse
        ), label = "circle6Y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawCircle(
            color = primaryColor.copy(alpha = 0.05f),
            radius = 400f,
            center = Offset(size.width * circle1X.value, size.height * circle1Y.value)
        )
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.035f),
            radius = 280f,
            center = Offset(size.width * circle2X.value, size.height * circle2Y.value)
        )
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.04f),
            radius = 200f,
            center = Offset(size.width * circle3X.value, size.height * circle3Y.value)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.035f),
            radius = 320f,
            center = Offset(size.width * circle4X.value, size.height * circle4Y.value)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.04f),
            radius = 180f,
            center = Offset(size.width * circle5X.value, size.height * circle5Y.value)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.04f),
            radius = 220f,
            center = Offset(size.width * circle6X.value, size.height * circle6Y.value)
        )
    }
}
