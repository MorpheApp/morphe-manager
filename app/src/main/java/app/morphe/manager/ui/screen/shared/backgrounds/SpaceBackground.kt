package app.morphe.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.util.isDarkBackground
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated space background with stars moving towards the viewer
 */
@Composable
fun SpaceBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.isDarkBackground()
    val starColor = if (isDarkTheme) Color.White else Color(0xFF1A2530)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Use new parallax system
    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.3f,
        context = context,
        coroutineScope = coroutineScope
    )

    val stars = remember(isDarkTheme) {
        mutableStateListOf<StarData>().apply {
            addAll(generateStarPool())
        }
    }

    var baseProgress by remember { mutableFloatStateOf(0f) }

    // Continuous animation loop
    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameMillis {
                baseProgress += 0.0008f

                // Regenerate stars that have passed the camera
                stars.forEachIndexed { index, star ->
                    val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f

                    // Regenerate only when star is far behind camera
                    if (adjustedProgress > 0.98f || adjustedProgress < 0.01f) {
                        if (star.lastRegen != baseProgress.toInt()) {
                            // Generate new random position avoiding center
                            var newX: Float
                            var newY: Float
                            var newDistance: Float

                            do {
                                val newAngle = Random.nextFloat() * 360f
                                newDistance = kotlin.math.sqrt(Random.nextFloat()) * 1.5f
                                val newAngleRad = newAngle * (Math.PI / 180f).toFloat()
                                newX = cos(newAngleRad) * newDistance
                                newY = sin(newAngleRad) * newDistance
                            } while (newDistance < 0.15f) // Exclude center 10% area

                            stars[index] = star.copy(
                                x = newX,
                                y = newY,
                                lastRegen = baseProgress.toInt()
                            )
                        }
                    }
                }
            }
        }
    }

    var meteor by remember { mutableStateOf<MeteorState?>(null) }
    val meteorProgress = remember { Animatable(0f) }

    // Meteor spawner
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(40000, 60000)) // 40-60 seconds

            val direction = Random.nextInt(2)

            val angle = when (direction) {
                0 -> 130f + Random.nextFloat() * 20f  // Right to left
                else -> 30f + Random.nextFloat() * 20f  // Left to right
            }

            val newMeteor = MeteorState(
                startX = Random.nextFloat(),
                startY = Random.nextFloat() * 0.3f,
                angle = angle,
                length = 200f + Random.nextFloat() * 150f,
                depth = 0.4f + Random.nextFloat() * 0.6f,
                thickness = 4f
            )

            meteor = newMeteor
            meteorProgress.snapTo(0f)
            meteorProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1200, easing = LinearOutSlowInEasing)
            )
            meteor = null
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f

        // Use new parallax state
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value

        // Render stars
        stars.forEach { star ->
            val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f
            val z = 1f - adjustedProgress

            // Only render stars in visible depth range
            if (z < 0.05f || z > 1.2f) return@forEach

            val perspectiveFactor = 1f / z.coerceAtLeast(0.1f)

            val baseX = star.x * width * 0.5f
            val baseY = star.y * height * 0.5f

            val projectedX = baseX * perspectiveFactor
            val projectedY = baseY * perspectiveFactor

            val parallaxStrength = star.depth * 150f * (1f - z.coerceIn(0f, 1f))
            val parallaxOffsetX = tiltX * parallaxStrength
            val parallaxOffsetY = tiltY * parallaxStrength

            val finalX = centerX + projectedX + parallaxOffsetX
            val finalY = centerY + projectedY + parallaxOffsetY

            // Cull stars outside screen bounds with larger margin
            if (finalX < -150 || finalX > width + 150 ||
                finalY < -150 || finalY > height + 150) {
                return@forEach
            }

            // Scale size based on distance
            val sizeFactor = perspectiveFactor * 0.65f
            val finalSize = star.size * sizeFactor

            // Smooth fade in from distance
            val fadeIn = when {
                z > 1.0f -> ((1.2f - z) / 0.2f).coerceIn(0f, 1f)
                else -> 1f
            }

            // Smooth fade out when approaching camera
            val fadeOut = when {
                z < 0.15f -> (z / 0.15f).coerceIn(0f, 1f)
                else -> 1f
            }

            // Base distance-based alpha
            val distanceAlpha = when {
                z > 0.6f -> ((1f - z) / 0.4f).coerceIn(0f, 1f)
                z < 0.3f -> (z / 0.3f).coerceIn(0f, 1f)
                else -> 1f
            }

            val combinedAlpha = (star.baseAlpha * distanceAlpha * fadeIn * fadeOut).coerceIn(0f, 1f)

            // Outer glow
            drawCircle(
                color = starColor,
                radius = finalSize * 1.8f,
                center = Offset(finalX, finalY),
                alpha = combinedAlpha * 0.2f
            )

            // Main star body
            drawCircle(
                color = starColor,
                radius = finalSize * 1.1f,
                center = Offset(finalX, finalY),
                alpha = combinedAlpha
            )
        }

        // Render meteor with parallax
        meteor?.let { m ->
            val p = meteorProgress.value
            val angleRad = m.angle * (Math.PI / 180f).toFloat()

            val meteorParallax = m.depth * 120f
            val parallaxX = tiltX * meteorParallax
            val parallaxY = tiltY * meteorParallax

            val travelDistance = width * 2f * p

            val cosAngle = cos(angleRad)
            val sinAngle = sin(angleRad)

            val curX = (m.startX * width) + (travelDistance * cosAngle) + parallaxX
            val curY = (m.startY * height) + (travelDistance * sinAngle) + parallaxY

            val tailX = curX - (m.length * cosAngle)
            val tailY = curY - (m.length * sinAngle)

            // Outer glow
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = 0.3f),
                    0.6f to starColor.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY),
                    end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY),
                end = Offset(tailX, tailY),
                strokeWidth = m.thickness * 4f,
                cap = StrokeCap.Round
            )

            // Core trail
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = 0.95f),
                    0.5f to starColor.copy(alpha = 0.6f),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY),
                    end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY),
                end = Offset(tailX, tailY),
                strokeWidth = m.thickness,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Generates initial pool of stars with varied properties
 */
private fun generateStarPool(): List<StarData> {
    return List(300) { index ->
        // Evenly distribute depth across the entire pool
        val depthLayer = index / 300f

        // Use golden angle for even circular distribution
        // Regenerate position until we get one outside the center exclusion zone
        var x: Float
        var y: Float
        var distance: Float

        do {
            val angle = (index * 137.5f) % 360f
            distance = kotlin.math.sqrt(Random.nextFloat()) * 1.5f
            val angleRad = angle * (Math.PI / 180f).toFloat()
            x = cos(angleRad) * distance
            y = sin(angleRad) * distance
        } while (distance < 0.15f) // Exclude center 10% area

        StarData(
            x = x,
            y = y,
            size = 2f + Random.nextFloat() * 3.5f,
            baseAlpha = 0.6f + Random.nextFloat() * 0.4f,
            depth = depthLayer,
            speed = 0.5f + depthLayer * 1f,
            initialOffset = Random.nextFloat(), // Stagger stars along Z-axis
            lastRegen = -1
        )
    }
}

private data class StarData(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseAlpha: Float,
    val depth: Float,
    val speed: Float,
    val initialOffset: Float, // Offset along Z-axis to distribute stars
    val lastRegen: Int // Track last regeneration to avoid duplicates
)

private data class MeteorState(
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val length: Float,
    val depth: Float,
    val thickness: Float
)
