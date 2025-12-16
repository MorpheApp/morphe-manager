package app.revanced.manager.ui.component.morphe.home

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import app.morphe.manager.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Types of animated backgrounds available in the app
 */
enum class BackgroundType(val displayNameResId: Int) {
    CIRCLES(R.string.morphe_background_type_circles),
    RINGS(R.string.morphe_background_type_rings),
    WAVES(R.string.morphe_background_type_waves),
    PARTICLES(R.string.morphe_background_type_particles),
    NONE(R.string.morphe_background_type_none);

    companion object {
        val DEFAULT = CIRCLES
    }
}

/**
 * Animated background with multiple visual styles
 * Creates subtle floating effects that can be used across all screens
 */
@Composable
fun AnimatedBackground(
    type: BackgroundType = BackgroundType.CIRCLES,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    when (type) {
        BackgroundType.CIRCLES -> CirclesBackground(modifier)
        BackgroundType.RINGS -> RingsBackground(modifier)
        BackgroundType.WAVES -> WavesBackground(modifier)
        BackgroundType.PARTICLES -> ParticlesBackground(modifier)
        BackgroundType.NONE -> {} // No background
    }
}

/**
 * Original circles background
 */
@Composable
private fun CirclesBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "circles")

    // Circle 1 - large top left
    val circle1X = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle1X"
    )
    val circle1Y = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle1Y"
    )

    // Circle 2 - medium top right
    val circle2X = infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle2X"
    )
    val circle2Y = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle2Y"
    )

    // Circle 3 - small center right
    val circle3X = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle3X"
    )
    val circle3Y = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle3Y"
    )

    // Circle 4 - medium bottom right
    val circle4X = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(9500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle4X"
    )
    val circle4Y = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle4Y"
    )

    // Circle 5 - small bottom left
    val circle5X = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(8200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle5X"
    )
    val circle5Y = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.73f,
        animationSpec = infiniteRepeatable(
            animation = tween(6800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle5Y"
    )

    // Circle 6 - bottom center
    val circle6X = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(8800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle6X"
    )
    val circle6Y = infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.87f,
        animationSpec = infiniteRepeatable(
            animation = tween(7800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle6Y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Circle 1 - large top left
        drawCircle(
            color = primaryColor.copy(alpha = 0.05f),
            radius = 400f,
            center = Offset(size.width * circle1X.value, size.height * circle1Y.value)
        )

        // Circle 2 - medium top right
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.035f),
            radius = 280f,
            center = Offset(size.width * circle2X.value, size.height * circle2Y.value)
        )

        // Circle 3 - small center right
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.04f),
            radius = 200f,
            center = Offset(size.width * circle3X.value, size.height * circle3Y.value)
        )

        // Circle 4 - medium bottom right
        drawCircle(
            color = secondaryColor.copy(alpha = 0.035f),
            radius = 320f,
            center = Offset(size.width * circle4X.value, size.height * circle4Y.value)
        )

        // Circle 5 - small bottom left
        drawCircle(
            color = primaryColor.copy(alpha = 0.04f),
            radius = 180f,
            center = Offset(size.width * circle5X.value, size.height * circle5Y.value)
        )

        // Circle 6 - bottom center
        drawCircle(
            color = secondaryColor.copy(alpha = 0.04f),
            radius = 220f,
            center = Offset(size.width * circle6X.value, size.height * circle6Y.value)
        )
    }
}

/**
 * Rings background - concentric circles with stroke
 */
@Composable
private fun RingsBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "rings")

    // Ring 1 animations
    val ring1X = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring1X"
    )
    val ring1Y = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring1Y"
    )

    // Ring 2 animations - top right
    val ring2X = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring2X"
    )
    val ring2Y = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring2Y"
    )

    // Ring 3 animations
    val ring3X = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring3X"
    )
    val ring3Y = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(9500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring3Y"
    )

    // Ring 4 animations
    val ring4X = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring4X"
    )
    val ring4Y = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring4Y"
    )

    // Ring 5 animations - bottom right
    val ring5X = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(8800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring5X"
    )
    val ring5Y = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(7600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring5Y"
    )

    // Ring 6 animations - center right
    val ring6X = infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(9200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring6X"
    )
    val ring6Y = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(8400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring6Y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Ring 1 - triple rings (top left)
        val center1 = Offset(size.width * ring1X.value, size.height * ring1Y.value)
        drawCircle(
            color = primaryColor.copy(alpha = 0.14f),
            radius = 140f,
            center = center1,
            style = Stroke(width = 6f)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.1f),
            radius = 190f,
            center = center1,
            style = Stroke(width = 5f)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.07f),
            radius = 240f,
            center = center1,
            style = Stroke(width = 4f)
        )

        // Ring 2 - double rings (top right)
        val center2 = Offset(size.width * ring2X.value, size.height * ring2Y.value)
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.12f),
            radius = 130f,
            center = center2,
            style = Stroke(width = 6f)
        )
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.08f),
            radius = 180f,
            center = center2,
            style = Stroke(width = 5f)
        )

        // Ring 3 - triple rings (center)
        val center3 = Offset(size.width * ring3X.value, size.height * ring3Y.value)
        drawCircle(
            color = secondaryColor.copy(alpha = 0.12f),
            radius = 110f,
            center = center3,
            style = Stroke(width = 6f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.1f),
            radius = 160f,
            center = center3,
            style = Stroke(width = 5f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.06f),
            radius = 210f,
            center = center3,
            style = Stroke(width = 4f)
        )

        // Ring 4 - double rings (bottom left)
        val center4 = Offset(size.width * ring4X.value, size.height * ring4Y.value)
        drawCircle(
            color = primaryColor.copy(alpha = 0.1f),
            radius = 150f,
            center = center4,
            style = Stroke(width = 6f)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.07f),
            radius = 200f,
            center = center4,
            style = Stroke(width = 5f)
        )

        // Ring 5 - triple rings (bottom right)
        val center5 = Offset(size.width * ring5X.value, size.height * ring5Y.value)
        drawCircle(
            color = secondaryColor.copy(alpha = 0.12f),
            radius = 120f,
            center = center5,
            style = Stroke(width = 6f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.09f),
            radius = 170f,
            center = center5,
            style = Stroke(width = 5f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.06f),
            radius = 220f,
            center = center5,
            style = Stroke(width = 4f)
        )

        // Ring 6 - double rings (center right)
        val center6 = Offset(size.width * ring6X.value, size.height * ring6Y.value)
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.11f),
            radius = 135f,
            center = center6,
            style = Stroke(width = 6f)
        )
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.07f),
            radius = 185f,
            center = center6,
            style = Stroke(width = 5f)
        )
    }
}

/**
 * Waves background - flowing sine waves
 */
@Composable
private fun WavesBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "waves")

    val phase1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val phase3 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    val phase4 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(13000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase4"
    )

    val phase5 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase5"
    )

    val phase6 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase6"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Wave 1 - very top
        val path1 = Path().apply {
            val normalizedX = 0f
            val y = height * 0.14f + sin(normalizedX * 4f * PI.toFloat() + phase1.value) * 80f
            moveTo(0f, y)
            for (x in 0..width.toInt() step 4) {
                val nx = x / width
                val yPos = height * 0.14f + sin(nx * 4f * PI.toFloat() + phase1.value) * 80f
                lineTo(x.toFloat(), yPos)
            }
        }
        drawPath(
            path = path1,
            color = primaryColor.copy(alpha = 0.15f),
            style = Stroke(width = 8f)
        )

        // Wave 2 - upper area
        val path2 = Path().apply {
            val normalizedX = 0f
            val y = height * 0.3f + sin(normalizedX * 3f * PI.toFloat() - phase2.value) * 85f
            moveTo(0f, y)
            for (x in 0..width.toInt() step 4) {
                val nx = x / width
                val yPos = height * 0.3f + sin(nx * 3f * PI.toFloat() - phase2.value) * 85f
                lineTo(x.toFloat(), yPos)
            }
        }
        drawPath(
            path = path2,
            color = secondaryColor.copy(alpha = 0.13f),
            style = Stroke(width = 7f)
        )

        // Wave 3 - middle area
        val path3 = Path().apply {
            val normalizedX = 0f
            val y = height * 0.46f + sin(normalizedX * 3.5f * PI.toFloat() + phase3.value) * 90f
            moveTo(0f, y)
            for (x in 0..width.toInt() step 4) {
                val nx = x / width
                val yPos = height * 0.46f + sin(nx * 3.5f * PI.toFloat() + phase3.value) * 90f
                lineTo(x.toFloat(), yPos)
            }
        }
        drawPath(
            path = path3,
            color = tertiaryColor.copy(alpha = 0.12f),
            style = Stroke(width = 7f)
        )

        // Wave 4 - lower middle area
        val path4 = Path().apply {
            val normalizedX = 0f
            val y = height * 0.62f + sin(normalizedX * 5f * PI.toFloat() - phase4.value * 0.8f) * 85f
            moveTo(0f, y)
            for (x in 0..width.toInt() step 4) {
                val nx = x / width
                val yPos = height * 0.62f + sin(nx * 5f * PI.toFloat() - phase4.value * 0.8f) * 85f
                lineTo(x.toFloat(), yPos)
            }
        }
        drawPath(
            path = path4,
            color = primaryColor.copy(alpha = 0.11f),
            style = Stroke(width = 7f)
        )

        // Wave 5 - lower area
        val path5 = Path().apply {
            val normalizedX = 0f
            val y = height * 0.78f + sin(normalizedX * 4.5f * PI.toFloat() + phase5.value) * 75f
            moveTo(0f, y)
            for (x in 0..width.toInt() step 4) {
                val nx = x / width
                val yPos = height * 0.78f + sin(nx * 4.5f * PI.toFloat() + phase5.value) * 75f
                lineTo(x.toFloat(), yPos)
            }
        }
        drawPath(
            path = path5,
            color = secondaryColor.copy(alpha = 0.1f),
            style = Stroke(width = 6f)
        )

        // Wave 6 - very bottom
        val path6 = Path().apply {
            val normalizedX = 0f
            val y = height * 0.92f + sin(normalizedX * 3.8f * PI.toFloat() - phase6.value) * 70f
            moveTo(0f, y)
            for (x in 0..width.toInt() step 4) {
                val nx = x / width
                val yPos = height * 0.92f + sin(nx * 3.8f * PI.toFloat() - phase6.value) * 70f
                lineTo(x.toFloat(), yPos)
            }
        }
        drawPath(
            path = path6,
            color = tertiaryColor.copy(alpha = 0.09f),
            style = Stroke(width = 6f)
        )
    }
}

/**
 * Particles background - blobs moving chaotically across the screen
 */
@Composable
private fun ParticlesBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    // Create particles that move chaotically across the entire screen
    val particles = remember {
        listOf(
            ParticleConfig(0.1f, 0.15f, 0.6f, 0.35f, 50f, 18000),
            ParticleConfig(0.85f, 0.1f, 0.5f, 0.8f, 45f, 16000),
            ParticleConfig(0.2f, 0.8f, 0.75f, 0.2f, 42f, 20000),
            ParticleConfig(0.7f, 0.3f, 0.15f, 0.65f, 48f, 17000),
            ParticleConfig(0.4f, 0.6f, 0.85f, 0.45f, 46f, 19000),
            ParticleConfig(0.15f, 0.45f, 0.7f, 0.75f, 44f, 15000),
            ParticleConfig(0.9f, 0.7f, 0.25f, 0.2f, 43f, 21000),
            ParticleConfig(0.5f, 0.2f, 0.5f, 0.9f, 49f, 16500),
            ParticleConfig(0.3f, 0.85f, 0.65f, 0.3f, 47f, 18500),
            ParticleConfig(0.8f, 0.5f, 0.2f, 0.6f, 41f, 17500),
            ParticleConfig(0.25f, 0.3f, 0.8f, 0.55f, 45f, 19500),
            ParticleConfig(0.6f, 0.75f, 0.35f, 0.15f, 48f, 16200),
            ParticleConfig(0.45f, 0.15f, 0.55f, 0.85f, 43f, 20500),
            ParticleConfig(0.75f, 0.9f, 0.4f, 0.4f, 46f, 15500),
            ParticleConfig(0.35f, 0.55f, 0.9f, 0.7f, 44f, 18200),
            ParticleConfig(0.65f, 0.25f, 0.3f, 0.5f, 47f, 17800)
        )
    }

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
    val size: Float,        // Particle size
    val duration: Int       // Animation duration in ms
)
