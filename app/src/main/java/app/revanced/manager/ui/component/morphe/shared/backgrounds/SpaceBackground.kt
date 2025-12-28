package app.revanced.manager.ui.component.morphe.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import app.revanced.manager.ui.component.morphe.utils.isDarkBackground
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Space background with twinkling stars and shooting meteors
 */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    val isDarkTheme = MaterialTheme.colorScheme.background.isDarkBackground()
    val starColor = if (isDarkTheme) Color.White else Color(0xFF1A2530)

    // Memoize star data to prevent recalculation on every recomposition
    val stars = remember(isDarkTheme) {
        List(60) {
            StarData(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = 3f + Random.nextFloat() * 3f,
                baseAlpha = 0.4f + Random.nextFloat() * 0.6f,
                phaseShift = Random.nextFloat() * Math.PI.toFloat() * 2f
            )
        }
    }

    // Continuous animation for the twinkling effect
    val infiniteTransition = rememberInfiniteTransition(label = "starAnimation")
    val globalProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = BackgroundAnimationSpecs.floatAnimation(
            duration = 15000,
            easing = LinearEasing,
            repeatMode = RepeatMode.Restart
        ),
        label = "globalProgress"
    )

    // State management for random meteor appearances
    var meteor by remember { mutableStateOf<MeteorState?>(null) }
    val meteorProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            // Random delay between 5 to 10 seconds for the next meteor
            delay(Random.nextLong(5000, 10000))

            val newMeteor = MeteorState(
                startX = Random.nextFloat(),
                startY = Random.nextFloat() * 0.4f, // Start from the upper part of the screen
                angle = 130f + Random.nextFloat() * 20f, // Diagonal trajectory
                length = 180f + Random.nextFloat() * 120f
            )

            meteor = newMeteor
            meteorProgress.snapTo(0f)
            meteorProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1000, easing = LinearOutSlowInEasing)
            )
            meteor = null
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height

        // Pre-calculate twinkle base to avoid redundant math in the loop
        val twinkleBase = globalProgress * Math.PI.toFloat() * 6f

        // Draw stars: using individual drawCircle calls
        // Alpha is applied directly to the draw call for better performance than color.copy()
        stars.forEach { star ->
            val twinkle = (sin(twinkleBase + star.phaseShift) + 1f) * 0.35f + 0.3f

            drawCircle(
                color = starColor,
                radius = star.size,
                center = Offset(star.x * width, star.y * height),
                alpha = star.baseAlpha * twinkle
            )
        }

        // Draw meteor if currently active
        meteor?.let { m ->
            val p = meteorProgress.value
            val angleRad = m.angle * (Math.PI / 180f).toFloat()
            val travelDistance = width * 1.5f * p

            val cosAngle = cos(angleRad)
            val sinAngle = sin(angleRad)

            // Current head position of the meteor
            val curX = (m.startX * width) + (travelDistance * cosAngle)
            val curY = (m.startY * height) + (travelDistance * sinAngle)

            // Calculated tail position based on meteor length
            val tailX = curX - (m.length * cosAngle)
            val tailY = curY - (m.length * sinAngle)

            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = 0.7f),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY),
                    end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY),
                end = Offset(tailX, tailY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

private data class StarData(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseAlpha: Float,
    val phaseShift: Float
)

private data class MeteorState(
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val length: Float
)
