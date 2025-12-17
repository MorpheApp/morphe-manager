package app.revanced.manager.ui.component.morphe.shared

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import app.morphe.manager.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Types of animated backgrounds available in the app
 */
enum class BackgroundType(val displayNameResId: Int) {
    CIRCLES(R.string.morphe_background_type_circles),
    RINGS(R.string.morphe_background_type_rings),
    WAVES(R.string.morphe_background_type_waves),
    PARTICLES(R.string.morphe_background_type_particles),
    SHAPES(R.string.morphe_background_type_shapes),
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
        BackgroundType.SHAPES -> LogosBackground(modifier)
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

    // Create particles that move in their zones
    val particles = remember {
        listOf(
            // Top left zone
            ParticleConfig(0.1f, 0.1f, 0.25f, 0.2f, 50f, 18000),
            ParticleConfig(0.15f, 0.2f, 0.3f, 0.15f, 45f, 16000),

            // Top right zone
            ParticleConfig(0.75f, 0.15f, 0.9f, 0.25f, 42f, 20000),
            ParticleConfig(0.8f, 0.25f, 0.7f, 0.1f, 48f, 17000),

            // Middle left zone
            ParticleConfig(0.1f, 0.4f, 0.25f, 0.55f, 46f, 19000),
            ParticleConfig(0.2f, 0.5f, 0.15f, 0.35f, 44f, 15000),

            // Middle right zone
            ParticleConfig(0.75f, 0.45f, 0.85f, 0.6f, 43f, 21000),
            ParticleConfig(0.85f, 0.55f, 0.7f, 0.4f, 49f, 16500),

            // Lower left zone
            ParticleConfig(0.15f, 0.7f, 0.3f, 0.85f, 47f, 18500),
            ParticleConfig(0.25f, 0.8f, 0.1f, 0.65f, 41f, 17500),

            // Lower right zone
            ParticleConfig(0.7f, 0.75f, 0.9f, 0.85f, 45f, 19500),
            ParticleConfig(0.8f, 0.85f, 0.75f, 0.7f, 48f, 16200),

            // Top center zone
            ParticleConfig(0.4f, 0.15f, 0.55f, 0.25f, 43f, 20500),
            ParticleConfig(0.5f, 0.2f, 0.45f, 0.1f, 46f, 15500),

            // Bottom center zone
            ParticleConfig(0.45f, 0.75f, 0.6f, 0.85f, 44f, 18200),
            ParticleConfig(0.55f, 0.8f, 0.4f, 0.7f, 47f, 17800)
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

/**
 * Geometric shapes floating in space
 */
@Composable
private fun LogosBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "shapes")

    // Create shapes with varied trajectories
    val shapes = remember {
        listOf(
            // Top area - horizontal movements
            ShapeConfig(0.1f, 0.15f, 0.3f, 0.2f, 30000, 0f, ShapeType.TRIANGLE),
            ShapeConfig(0.7f, 0.1f, 0.9f, 0.15f, 28000, 0f, ShapeType.SQUARE),

            // Upper middle - diagonal movements
            ShapeConfig(0.15f, 0.35f, 0.4f, 0.45f, 32000, 0f, ShapeType.DIAMOND),
            ShapeConfig(0.85f, 0.4f, 0.65f, 0.3f, 29000, 0f, ShapeType.TRIANGLE),

            // Lower middle - varied movements
            ShapeConfig(0.2f, 0.6f, 0.35f, 0.7f, 31000, 180f, ShapeType.SQUARE),
            ShapeConfig(0.8f, 0.65f, 0.6f, 0.55f, 27000, 0f, ShapeType.DIAMOND),

            // Bottom area - horizontal movements
            ShapeConfig(0.15f, 0.85f, 0.35f, 0.9f, 33000, 180f, ShapeType.TRIANGLE),
            ShapeConfig(0.75f, 0.9f, 0.55f, 0.85f, 26000, 0f, ShapeType.SQUARE)
        )
    }

    val shapeAnimations = shapes.map { config ->
        val x = infiniteTransition.animateFloat(
            initialValue = config.startX,
            targetValue = config.endX,
            animationSpec = infiniteRepeatable(
                animation = tween(config.duration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shapeX${config.startX}"
        )
        val y = infiniteTransition.animateFloat(
            initialValue = config.startY,
            targetValue = config.endY,
            animationSpec = infiniteRepeatable(
                animation = tween((config.duration * 1.1f).toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shapeY${config.startY}"
        )
        val rotation = infiniteTransition.animateFloat(
            initialValue = config.initialRotation,
            targetValue = config.initialRotation + 360f,
            animationSpec = infiniteRepeatable(
                animation = tween((config.duration * 2), easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shapeRot${config.initialRotation}"
        )
        Triple(x, y, rotation)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        shapeAnimations.forEachIndexed { index, (x, y, rotation) ->
            val color = when (index % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }.copy(alpha = 0.12f)

            val centerX = size.width * x.value
            val centerY = size.height * y.value
            val shapeSize = 140f

            // Rotate and draw shape
            rotate(rotation.value, Offset(centerX, centerY)) {
                when (shapes[index].type) {
                    ShapeType.TRIANGLE -> drawTriangle(centerX, centerY, shapeSize, color)
                    ShapeType.SQUARE -> drawSquare(centerX, centerY, shapeSize, color)
                    ShapeType.DIAMOND -> drawDiamond(centerX, centerY, shapeSize, color)
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
    val path = Path().apply {
        val height = size * 0.866f // Height of equilateral triangle
        val halfSize = size / 2f

        // Top point
        moveTo(centerX, centerY - height / 2f)
        // Bottom right
        lineTo(centerX + halfSize, centerY + height / 2f)
        // Bottom left
        lineTo(centerX - halfSize, centerY + height / 2f)
        close()
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 4f)
    )
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
    val path = Path().apply {
        val halfSize = size / 2f

        moveTo(centerX - halfSize, centerY - halfSize)
        lineTo(centerX + halfSize, centerY - halfSize)
        lineTo(centerX + halfSize, centerY + halfSize)
        lineTo(centerX - halfSize, centerY + halfSize)
        close()
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 4f)
    )
}

/**
 * Draw diamond outline
 */
private fun DrawScope.drawDiamond(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        val halfSize = size / 2f

        // Top point
        moveTo(centerX, centerY - halfSize)
        // Right point
        lineTo(centerX + halfSize, centerY)
        // Bottom point
        lineTo(centerX, centerY + halfSize)
        // Left point
        lineTo(centerX - halfSize, centerY)
        close()
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 4f)
    )
}

private enum class ShapeType {
    TRIANGLE,
    SQUARE,
    DIAMOND
}

private data class ShapeConfig(
    val startX: Float,      // Starting X position (0-1)
    val startY: Float,      // Starting Y position (0-1)
    val endX: Float,        // Ending X position (0-1)
    val endY: Float,        // Ending Y position (0-1)
    val duration: Int,      // Animation duration in ms
    val initialRotation: Float, // Starting rotation angle
    val type: ShapeType     // Type of shape to draw
)

private data class ParticleConfig(
    val startX: Float,      // Starting X position (0-1)
    val startY: Float,      // Starting Y position (0-1)
    val endX: Float,        // Ending X position (0-1)
    val endY: Float,        // Ending Y position (0-1)
    val size: Float,        // Particle size
    val duration: Int       // Animation duration in ms
)
