package app.revanced.manager.ui.screen.shared.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Animated polygon mesh background with chaotic motion
 */
@Composable
fun MeshBackground(
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
        sensitivity = 0.4f,
        context = context,
        coroutineScope = coroutineScope
    )

    val infiniteTransition = rememberInfiniteTransition(label = "mesh")

    // Generate mesh grid
    val meshNodes = remember {
        generateMeshGrid()
    }

    // Continuous time animation
    val time = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = PI.toFloat(), // Half cycle - will reverse back
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 20000, // 20 seconds forward, 20 seconds back = 40s total
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse // Goes back smoothly, no restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val t = time.value

        val rows = 12
        val cols = 12

        // 3D projection parameters
        val cameraZ = 1.6f
        val gridTilt = 10f

        // Draw triangular polygons
        meshNodes.forEachIndexed { index, node ->
            val row = index / cols
            val col = index % cols

            if (row < rows - 1 && col < cols - 1) {
                // Project node with chaotic motion
                fun projectNode(meshNode: MeshNode): Offset {
                    // Unique frequencies and phases for each node
                    val xFreq = 1.0f + meshNode.baseX * 0.5f
                    val yFreq = 1.2f + meshNode.baseY * 0.6f
                    val zFreq = 0.8f + (meshNode.baseX + meshNode.baseY) * 0.4f

                    val xPhase = meshNode.baseX * 2f * PI.toFloat()
                    val yPhase = meshNode.baseY * 3f * PI.toFloat()
                    val zPhase = (meshNode.baseX + meshNode.baseY) * 1.5f * PI.toFloat()

                    // Calculate position with sine/cosine for smooth looping
                    val x = meshNode.baseX + meshNode.offsetX * sin(t * xFreq + xPhase)
                    val y = meshNode.baseY + meshNode.offsetY * cos(t * yFreq + yPhase)
                    val z = meshNode.zAmplitude * sin(t * zFreq + zPhase)

                    // Normalize coordinates
                    val normalizedX = (x - 0.5f) * 3.8f
                    val normalizedY = (y - 0.5f) * 2.8f

                    // Apply tilt rotation
                    val tiltRad = Math.toRadians(gridTilt.toDouble())
                    val rotatedY = normalizedY * cos(tiltRad).toFloat() - z * sin(tiltRad).toFloat()
                    val rotatedZ = normalizedY * sin(tiltRad).toFloat() + z * cos(tiltRad).toFloat()

                    // Parallax effect
                    val parallaxStrength = meshNode.baseDepth * 60f
                    val parallaxX = tiltX * parallaxStrength
                    val parallaxY = tiltY * parallaxStrength

                    // Perspective projection
                    val perspective = cameraZ / (cameraZ - rotatedZ)
                    val projectedX = normalizedX * perspective
                    val projectedY = rotatedY * perspective

                    // Convert to screen coordinates
                    return Offset(
                        (projectedX * width * 0.48f) + (width * 0.5f) + parallaxX,
                        (projectedY * height * 0.48f) + (height * 0.5f) + parallaxY
                    )
                }

                val p1 = projectNode(meshNodes[index])
                val p2 = projectNode(meshNodes[index + 1])
                val p3 = projectNode(meshNodes[index + cols])
                val p4 = projectNode(meshNodes[index + cols + 1])

                // Select color
                val color = when ((row + col) % 3) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    else -> tertiaryColor
                }

                val alpha = 0.14f + node.baseDepth * 0.05f

                // Draw first triangle
                val path1 = Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                }
                drawPath(path1, color.copy(alpha = alpha), style = Stroke(width = 3.5f))

                // Draw second triangle
                val path2 = Path().apply {
                    moveTo(p2.x, p2.y)
                    lineTo(p4.x, p4.y)
                    lineTo(p3.x, p3.y)
                    close()
                }
                drawPath(path2, color.copy(alpha = alpha), style = Stroke(width = 3.5f))
            }
        }
    }
}

/**
 * Generate mesh grid with varying depth
 */
private fun generateMeshGrid(): List<MeshNode> {
    val rows = 12
    val cols = 12
    val nodes = mutableListOf<MeshNode>()

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val baseX = col / (cols - 1f)
            val baseY = row / (rows - 1f)

            // Random offset for chaotic movement
            val offsetX = (Random.nextFloat() - 0.5f) * 0.04f
            val offsetY = (Random.nextFloat() - 0.5f) * 0.04f
            val zAmplitude = Random.nextFloat() * 0.15f

            // Calculate depth based on distance from center
            val centerDistX = (baseX - 0.5f) * 2f
            val centerDistY = (baseY - 0.5f) * 2f
            val baseDepth = sqrt(
                centerDistX * centerDistX + centerDistY * centerDistY
            ) / sqrt(2f)

            nodes.add(
                MeshNode(
                    baseX = baseX,
                    baseY = baseY,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    baseDepth = baseDepth,
                    zAmplitude = zAmplitude
                )
            )
        }
    }

    return nodes
}

private data class MeshNode(
    val baseX: Float,
    val baseY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val baseDepth: Float,  // For parallax effect
    val zAmplitude: Float  // For wave animation
)
