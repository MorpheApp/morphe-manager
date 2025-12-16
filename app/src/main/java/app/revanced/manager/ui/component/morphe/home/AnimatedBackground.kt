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

    // Ring 2 animations
    val ring2X = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring2X"
    )
    val ring2Y = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.35f,
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
        initialValue = 0.6f,
        targetValue = 0.65f,
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

    Canvas(modifier = modifier.fillMaxSize()) {
        // Ring 1 - triple rings
        val center1 = Offset(size.width * ring1X.value, size.height * ring1Y.value)
        drawCircle(
            color = primaryColor.copy(alpha = 0.06f),
            radius = 150f,
            center = center1,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.04f),
            radius = 200f,
            center = center1,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.02f),
            radius = 250f,
            center = center1,
            style = Stroke(width = 2f)
        )

        // Ring 2 - double rings
        val center2 = Offset(size.width * ring2X.value, size.height * ring2Y.value)
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.05f),
            radius = 180f,
            center = center2,
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = tertiaryColor.copy(alpha = 0.03f),
            radius = 240f,
            center = center2,
            style = Stroke(width = 2f)
        )

        // Ring 3 - triple rings
        val center3 = Offset(size.width * ring3X.value, size.height * ring3Y.value)
        drawCircle(
            color = secondaryColor.copy(alpha = 0.05f),
            radius = 120f,
            center = center3,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.04f),
            radius = 170f,
            center = center3,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.02f),
            radius = 220f,
            center = center3,
            style = Stroke(width = 2f)
        )

        // Ring 4 - double rings
        val center4 = Offset(size.width * ring4X.value, size.height * ring4Y.value)
        drawCircle(
            color = primaryColor.copy(alpha = 0.04f),
            radius = 160f,
            center = center4,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.02f),
            radius = 210f,
            center = center4,
            style = Stroke(width = 2f)
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
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val phase3 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Wave 1 - top
        val path1 = Path().apply {
            moveTo(0f, height * 0.2f)
            for (x in 0..width.toInt() step 10) {
                val y = height * 0.2f + sin((x / width * 4 + phase1.value) * Math.PI / 180) * 80f
                lineTo(x.toFloat(), y.toFloat())
            }
        }
        drawPath(
            path = path1,
            color = primaryColor.copy(alpha = 0.06f),
            style = Stroke(width = 3f)
        )

        // Wave 2 - middle
        val path2 = Path().apply {
            moveTo(0f, height * 0.5f)
            for (x in 0..width.toInt() step 10) {
                val y = height * 0.5f + sin((x / width * 3 + phase2.value) * Math.PI / 180) * 60f
                lineTo(x.toFloat(), y.toFloat())
            }
        }
        drawPath(
            path = path2,
            color = secondaryColor.copy(alpha = 0.05f),
            style = Stroke(width = 2.5f)
        )

        // Wave 3 - bottom
        val path3 = Path().apply {
            moveTo(0f, height * 0.7f)
            for (x in 0..width.toInt() step 10) {
                val y = height * 0.7f + sin((x / width * 5 + phase3.value) * Math.PI / 180) * 50f
                lineTo(x.toFloat(), y.toFloat())
            }
        }
        drawPath(
            path = path3,
            color = tertiaryColor.copy(alpha = 0.04f),
            style = Stroke(width = 2f)
        )

        // Additional subtle wave
        val path4 = Path().apply {
            moveTo(0f, height * 0.35f)
            for (x in 0..width.toInt() step 10) {
                val y = height * 0.35f + sin((x / width * 6 - phase1.value) * Math.PI / 180) * 40f
                lineTo(x.toFloat(), y.toFloat())
            }
        }
        drawPath(
            path = path4,
            color = primaryColor.copy(alpha = 0.03f),
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Particles background - small moving dots
 */
@Composable
private fun ParticlesBackground(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    // Create multiple particle animations
    val particles = remember {
        (0 until 20).map { index ->
            Triple(
                index * 0.05f,
                (index * 37) % 100 / 100f,
                (index * 73) % 100 / 100f
            )
        }
    }

    val particleAnimations = particles.map { (delay, startX, startY) ->
        val x = infiniteTransition.animateFloat(
            initialValue = startX,
            targetValue = if (startX > 0.5f) startX - 0.15f else startX + 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween((7000 + delay * 1000).toInt()),
                repeatMode = RepeatMode.Reverse
            ),
            label = "particleX$startX"
        )
        val y = infiniteTransition.animateFloat(
            initialValue = startY,
            targetValue = if (startY > 0.5f) startY - 0.15f else startY + 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween((8000 + delay * 800).toInt()),
                repeatMode = RepeatMode.Reverse
            ),
            label = "particleY$startY"
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
                color = color.copy(alpha = 0.08f),
                radius = when {
                    index % 4 == 0 -> 25f
                    index % 4 == 1 -> 20f
                    index % 4 == 2 -> 15f
                    else -> 18f
                },
                center = Offset(size.width * x.value, size.height * y.value)
            )
        }
    }
}
