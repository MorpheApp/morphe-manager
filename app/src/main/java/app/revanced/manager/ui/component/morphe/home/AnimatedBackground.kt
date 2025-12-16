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
    MORPHE(R.string.app_name),
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
        BackgroundType.MORPHE -> LogosBackground(modifier)
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
 * Morphe logos floating in space
 */
@Composable
private fun LogosBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "logos")

    // Create logos with varied trajectories
    val logos = remember {
        listOf(
            // Top area - horizontal movements
            LogoConfig(0.1f, 0.15f, 0.3f, 0.2f, 30000, 0f),
            LogoConfig(0.7f, 0.1f, 0.9f, 0.15f, 28000, 45f),

            // Upper middle - diagonal movements
            LogoConfig(0.15f, 0.35f, 0.4f, 0.45f, 32000, 90f),
            LogoConfig(0.85f, 0.4f, 0.65f, 0.3f, 29000, 135f),

            // Lower middle - varied movements
            LogoConfig(0.2f, 0.6f, 0.35f, 0.7f, 31000, 180f),
            LogoConfig(0.8f, 0.65f, 0.6f, 0.55f, 27000, 225f),

            // Bottom area - horizontal movements
            LogoConfig(0.15f, 0.85f, 0.35f, 0.9f, 33000, 270f),
            LogoConfig(0.75f, 0.9f, 0.55f, 0.85f, 26000, 315f)
        )
    }

    val logoAnimations = logos.map { config ->
        val x = infiniteTransition.animateFloat(
            initialValue = config.startX,
            targetValue = config.endX,
            animationSpec = infiniteRepeatable(
                animation = tween(config.duration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "logoX${config.startX}"
        )
        val y = infiniteTransition.animateFloat(
            initialValue = config.startY,
            targetValue = config.endY,
            animationSpec = infiniteRepeatable(
                animation = tween((config.duration * 1.1f).toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "logoY${config.startY}"
        )
        val rotation = infiniteTransition.animateFloat(
            initialValue = config.initialRotation,
            targetValue = config.initialRotation + 360f,
            animationSpec = infiniteRepeatable(
                animation = tween((config.duration * 2), easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "logoRot${config.initialRotation}"
        )
        Triple(x, y, rotation)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        logoAnimations.forEachIndexed { index, (x, y, rotation) ->
            val color = when (index % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }.copy(alpha = 0.15f)

            val centerX = size.width * x.value
            val centerY = size.height * y.value
            val logoSize = 180f

            // Rotate and draw logo
            rotate(rotation.value, Offset(centerX, centerY)) {
                drawMorpheLogo(centerX, centerY, logoSize, color)
            }
        }
    }
}

/**
 * Draw Morphe logo outline
 */
private fun DrawScope.drawMorpheLogo(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color
) {
    val scale = size / 24f // Scale from 24dp viewBox to desired size

    // Translate to center the logo
    val offsetX = centerX - (24f * scale / 2f)
    val offsetY = centerY - (24f * scale / 2f)

    // Combined path - both M shape and right part
    val path = Path().apply {
        // Path 1 - main M shape
        moveTo(4.716f * scale + offsetX, 3.50031f * scale + offsetY)

        cubicTo(
            2.84895f * scale + offsetX, 3.50031f * scale + offsetY,
            2.50031f * scale + offsetX, 4.91697f * scale + offsetY,
            2.50031f * scale + offsetX, 5.76697f * scale + offsetY
        )

        lineTo(2.50031f * scale + offsetX, 18.347f * scale + offsetY)

        cubicTo(
            2.55522f * scale + offsetX, 19.5369f * scale + offsetY,
            3.34356f * scale + offsetX, 20.5003f * scale + offsetY,
            4.49674f * scale + offsetX, 20.5003f * scale + offsetY
        )

        cubicTo(
            5.64992f * scale + offsetX, 20.5003f * scale + offsetY,
            6.63835f * scale + offsetX, 19.537f * scale + offsetY,
            6.69327f * scale + offsetX, 18.347f * scale + offsetY
        )

        lineTo(6.69327f * scale + offsetX, 10.697f * scale + offsetY)

        cubicTo(
            8.23084f * scale + offsetX, 12.5103f * scale + offsetY,
            9.30955f * scale + offsetX, 15.627f * scale + offsetY,
            12.0003f * scale + offsetX, 15.7403f * scale + offsetY
        )

        cubicTo(
            15.7934f * scale + offsetX, 15.7403f * scale + offsetY,
            17.9174f * scale + offsetX, 6.59783f * scale + offsetY,
            21.5003f * scale + offsetX, 5.96401f * scale + offsetY
        )

        cubicTo(
            21.4703f * scale + offsetX, 4.73698f * scale + offsetY,
            21.0961f * scale + offsetX, 3.50031f * scale + offsetY,
            19.284f * scale + offsetX, 3.50031f * scale + offsetY
        )

        cubicTo(
            17.417f * scale + offsetX, 3.50031f * scale + offsetY,
            15.8991f * scale + offsetX, 6.33364f * scale + offsetY,
            14.9656f * scale + offsetX, 7.5803f * scale + offsetY
        )

        cubicTo(
            14.0321f * scale + offsetX, 8.82696f * scale + offsetY,
            13.5928f * scale + offsetX, 10.187f * scale + offsetY,
            12.0003f * scale + offsetX, 10.187f * scale + offsetY
        )

        cubicTo(
            10.4078f * scale + offsetX, 10.187f * scale + offsetY,
            9.9136f * scale + offsetX, 8.77034f * scale + offsetY,
            9.03499f * scale + offsetX, 7.58034f * scale + offsetY
        )

        cubicTo(
            8.10146f * scale + offsetX, 6.33364f * scale + offsetY,
            6.58305f * scale + offsetX, 3.50031f * scale + offsetY,
            4.716f * scale + offsetX, 3.50031f * scale + offsetY
        )

        close()

        // Path 2 - right part
        moveTo(17.3067f * scale + offsetX, 15.8f * scale + offsetY)
        lineTo(17.3067f * scale + offsetX, 18.347f * scale + offsetY)

        cubicTo(
            17.3616f * scale + offsetX, 19.537f * scale + offsetY,
            18.35f * scale + offsetX, 20.5003f * scale + offsetY,
            19.5032f * scale + offsetX, 20.5003f * scale + offsetY
        )

        cubicTo(
            20.6564f * scale + offsetX, 20.5003f * scale + offsetY,
            21.4453f * scale + offsetX, 19.537f * scale + offsetY,
            21.5002f * scale + offsetX, 18.347f * scale + offsetY
        )

        lineTo(21.5002f * scale + offsetX, 9.2f * scale + offsetY)

        cubicTo(
            21.5002f * scale + offsetX, 7.8742f * scale + offsetY,
            17.3067f * scale + offsetX, 13.9316f * scale + offsetY,
            17.3067f * scale + offsetX, 15.8f * scale + offsetY
        )

        close()
    }

    // Draw path with stroke
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3f)
    )
}

private data class ParticleConfig(
    val startX: Float,      // Starting X position (0-1)
    val startY: Float,      // Starting Y position (0-1)
    val endX: Float,        // Ending X position (0-1)
    val endY: Float,        // Ending Y position (0-1)
    val size: Float,        // Particle size
    val duration: Int       // Animation duration in ms
)

private data class LogoConfig(
    val startX: Float,      // Starting X position (0-1)
    val startY: Float,      // Starting Y position (0-1)
    val endX: Float,        // Ending X position (0-1)
    val endY: Float,        // Ending Y position (0-1)
    val duration: Int,      // Animation duration in ms
    val initialRotation: Float // Starting rotation angle
)
