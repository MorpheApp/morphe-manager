/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Frame-based time accumulator that respects a [speedMultiplier].
 * Returns a [State<Float>] that increases every frame by (deltaMs * speedMultiplier).
 * This allows smooth speed changes without restarting animations.
 */
@Composable
fun rememberAnimatedTime(speedMultiplier: Float): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    // targetSpeed is updated every recomposition via SideEffect (composition thread, safe to read in frame callback)
    val targetSpeed = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeed.floatValue = speedMultiplier }

    LaunchedEffect(Unit) {
        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = targetSpeed.floatValue
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs
                // Smooth lerp: 2.5/sec ramp — ~0.8s to reach target speed.
                // High enough to feel reactive, low enough to avoid jarring jumps.
                currentSpeed += (targetSpeed.floatValue - currentSpeed) * (delta / 1000f) * 2.5f
                time.floatValue += delta * currentSpeed
            }
        }
    }
    return time
}


/**
 * Fires [onCompleted] exactly once when [patchingCompleted] flips to true.
 * Named with uppercase as required by Compose convention for Unit-returning Composables.
 */
@Composable
fun CompletionEffect(patchingCompleted: Boolean, onCompleted: () -> Unit) {
    LaunchedEffect(patchingCompleted) {
        if (patchingCompleted) onCompleted()
    }
}

/**
 * Parallax sensor state holder
 */
data class ParallaxState(
    val tiltX: State<Float>,
    val tiltY: State<Float>
)

/**
 * Reusable parallax effect using device accelerometer
 * Returns ParallaxState with current tilt values as State objects
 *
 * @param enableParallax Whether parallax effect is enabled
 * @param sensitivity Multiplier for tilt sensitivity (default 0.3f)
 */
@Composable
fun rememberParallaxState(
    enableParallax: Boolean,
    sensitivity: Float = 0.3f,
    context: Context,
    coroutineScope: CoroutineScope
): ParallaxState {
    val smoothTiltX = remember { Animatable(0f) }
    val smoothTiltY = remember { Animatable(0f) }

    var baselineX by remember { mutableFloatStateOf(0f) }
    var baselineY by remember { mutableFloatStateOf(0f) }
    var isCalibrated by remember { mutableStateOf(false) }

    // Reset when parallax is toggled
    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            smoothTiltX.snapTo(0f)
            smoothTiltY.snapTo(0f)
            isCalibrated = false
            baselineX = 0f
            baselineY = 0f
        } else {
            // Reset calibration when enabling
            isCalibrated = false
            baselineX = 0f
            baselineY = 0f
        }
    }

    DisposableEffect(enableParallax) {
        if (!enableParallax) {
            // Early exit if parallax is disabled
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            // No accelerometer available
            return@DisposableEffect onDispose { }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!isCalibrated) {
                    baselineX = event.values[0]
                    baselineY = event.values[1]
                    isCalibrated = true
                }

                val rawTiltX = event.values[0] - baselineX
                val rawTiltY = event.values[1] - baselineY

                coroutineScope.launch {
                    smoothTiltX.animateTo(
                        targetValue = rawTiltX * sensitivity,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                coroutineScope.launch {
                    smoothTiltY.animateTo(
                        targetValue = rawTiltY * sensitivity,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Return State objects directly
    return ParallaxState(
        tiltX = smoothTiltX.asState(),
        tiltY = smoothTiltY.asState()
    )
}
