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
 * Reusable animation specifications for backgrounds
 */
object BackgroundAnimationSpecs {
    /**
     * Generic float animation with customizable duration
     */
    fun floatAnimation(
        duration: Int,
        easing: Easing = LinearEasing,
        repeatMode: RepeatMode = RepeatMode.Reverse
    ): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(duration, easing = easing),
        repeatMode = repeatMode
    )

    /**
     * Very slow movement (10-12s)
     */
    fun verySlowFloat(duration: Int = 11000) = floatAnimation(duration)

    /**
     * Slow movement (8-10s)
     */
    fun slowFloat(duration: Int = 9000) = floatAnimation(duration)

    /**
     * Medium movement (6-8s)
     */
    fun mediumFloat(duration: Int = 7000) = floatAnimation(duration)

    /**
     * Fast movement (4-6s)
     */
    fun fastFloat(duration: Int = 5000) = floatAnimation(duration)

    /**
     * Rotation animation that restarts (for continuous spinning)
     */
    fun rotationAnimation(duration: Int = 20000): InfiniteRepeatableSpec<Float> =
        infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
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
