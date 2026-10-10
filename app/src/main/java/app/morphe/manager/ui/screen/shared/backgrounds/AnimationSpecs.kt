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
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.ui.screen.shared.FullscreenDialogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Tilt shifts the artwork by about a dozen pixels at full range, so smaller steps land under a pixel
private const val TILT_TARGET_THRESHOLD = 0.02f

// Backgrounds drift slowly enough that a 60 Hz step is indistinguishable from a 120 Hz one,
// and every skipped step is a full-screen repaint the GPU does not have to make.
// The cadence stays the same behind sheets and dialogs, where the backdrop is still on screen
internal const val BACKGROUND_STEP_INTERVAL_MS = 16f

/**
 * True for a background drawn inside a full-screen dialog as its own backdrop, such as the preview
 * of the background picker. Nothing covers that one, so it keeps animating while the dialog is up.
 */
val LocalBackdropInDialog = staticCompositionLocalOf { false }

/**
 * Runs [frameLoop] for as long as the host stays resumed and nothing opaque covers it, cancelling
 * the moment either stops holding. A background left ticking behind the lock screen, another
 * activity or a full-screen dialog keeps the frame clock awake and repaints the canvas for nobody.
 * Sheets are deliberately not included: the backdrop still shows around them.
 * Named with uppercase as required by Compose convention for Unit-returning Composables.
 */
@Composable
fun AnimationFrameEffect(frameLoop: suspend CoroutineScope.() -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val covered = FullscreenDialogs.anyOpen && !LocalBackdropInDialog.current

    LaunchedEffect(lifecycleOwner, covered) {
        if (covered) return@LaunchedEffect

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            frameLoop()
        }
    }
}

// How long the shared clock runs before it wraps. A Float of milliseconds this large still resolves
// a quarter of a millisecond, where an unbounded one stops resolving the 16 ms step after a day.
// It is a whole number of the cycles backgrounds wrap their own motion at, so those stay seamless
private const val ANIMATED_TIME_WRAP_MS = 3_600_000f

/**
 * Steps a background once per [BACKGROUND_STEP_INTERVAL_MS], handing [onStep] the milliseconds of
 * animation since the previous step: real time scaled by a speed that eases toward
 * [speedMultiplier] instead of jumping to it, along with that eased speed itself. Shared by the
 * clock most backgrounds read and by the ones that integrate their own physics.
 * Named with uppercase as required by Compose convention for Unit-returning Composables.
 *
 * @param boost How much further than [speedMultiplier] this background leans into a speed-up,
 *   where 1 follows it exactly.
 * @param rampPerSecond How quickly the speed closes on its target.
 */
@Composable
fun BackgroundStepEffect(
    speedMultiplier: Float,
    boost: Float = 1f,
    rampPerSecond: Float = 2.5f,
    onStep: (scaledMs: Float, speed: Float) -> Unit
) {
    // targetSpeed is updated every recomposition via SideEffect (composition thread, safe to read in frame callback)
    val targetSpeed = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeed.floatValue = speedMultiplier }
    val currentOnStep by rememberUpdatedState(onStep)

    AnimationFrameEffect {
        fun boostedTarget() = 1f + (targetSpeed.floatValue - 1f) * boost

        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = boostedTarget()
        // Real elapsed time gates the step, while the scaled time is what the background reads,
        // so changing the speed never changes how often the canvas is invalidated
        var elapsedMs = 0f
        var pendingMs = 0f
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs
                // Smooth lerp: at the default 2.5/sec ramp, ~0.8s to reach target speed.
                // High enough to feel reactive, low enough to avoid jarring jumps.
                currentSpeed += (boostedTarget() - currentSpeed) * (delta / 1000f) * rampPerSecond

                elapsedMs += delta
                pendingMs += delta * currentSpeed
                if (elapsedMs >= BACKGROUND_STEP_INTERVAL_MS) {
                    currentOnStep(pendingMs, currentSpeed)
                    // Carry the remainder so the step keeps its cadence on 90 Hz panels too
                    elapsedMs -= BACKGROUND_STEP_INTERVAL_MS
                    pendingMs = 0f
                }
            }
        }
    }
}

/**
 * Frame-based time accumulator that respects a [speedMultiplier].
 * Returns a [State<Float>] of milliseconds that advances by (deltaMs * speedMultiplier) every
 * [BACKGROUND_STEP_INTERVAL_MS], so a high refresh rate display does not repaint the backdrop more
 * often than its slow drift can show. This allows smooth speed changes without restarting
 * animations. The value wraps every hour of animation to keep its precision.
 */
@Composable
fun rememberAnimatedTime(speedMultiplier: Float): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    BackgroundStepEffect(speedMultiplier) { scaledMs, _ ->
        time.floatValue = (time.floatValue + scaledMs) % ANIMATED_TIME_WRAP_MS
    }
    return time
}

/**
 * A 0 → [peak] → 0 pulse played each time [patchingCompleted] flips to true, which every background
 * reads to stage its own celebration. It runs in a scope of its own, so it plays out in full even if
 * the flag drops back mid-way.
 */
@Composable
fun rememberCompletionPulse(
    patchingCompleted: Boolean,
    riseMillis: Int,
    fallMillis: Int,
    peak: Float = 1f,
    riseEasing: Easing = FastOutSlowInEasing
): State<Float> {
    val pulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(patchingCompleted) {
        if (!patchingCompleted) return@LaunchedEffect
        scope.launch {
            pulse.snapTo(0f)
            pulse.animateTo(peak, tween(riseMillis, easing = riseEasing))
            pulse.animateTo(0f, tween(fallMillis, easing = FastOutSlowInEasing))
        }
    }
    return pulse.asState()
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
    context: Context
): ParallaxState {
    val smoothTiltX = remember { Animatable(0f) }
    val smoothTiltY = remember { Animatable(0f) }
    val tiltTarget = remember { MutableStateFlow(Offset.Zero) }

    // A single pair of springs chases the latest target. Starting a fresh pair per sensor event
    // meant a hundred coroutines a second, each cancelling the spring the one before had just begun
    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            tiltTarget.value = Offset.Zero
            smoothTiltX.snapTo(0f)
            smoothTiltY.snapTo(0f)
            return@LaunchedEffect
        }

        tiltTarget.collectLatest { target ->
            coroutineScope {
                launch {
                    smoothTiltX.animateTo(
                        targetValue = target.x,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                launch {
                    smoothTiltY.animateTo(
                        targetValue = target.y,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }
    }

    DisposableEffect(enableParallax) {
        if (!enableParallax) {
            // Early exit if parallax is disabled
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: // No accelerometer available
            return@DisposableEffect onDispose { }

        // Calibration belongs to one registration, so the baseline resets with the listener itself
        var baselineX = 0f
        var baselineY = 0f
        var isCalibrated = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!isCalibrated) {
                    baselineX = event.values[0]
                    baselineY = event.values[1]
                    isCalibrated = true
                }

                val rawTiltX = event.values[0] - baselineX
                val rawTiltY = -(event.values[1] - baselineY)
                val target = Offset(rawTiltX * sensitivity, rawTiltY * sensitivity)

                // Sensor noise alone would retarget the springs forever, keeping the frame clock
                // awake even with the device flat on a table
                if ((target - tiltTarget.value).getDistance() > TILT_TARGET_THRESHOLD) {
                    tiltTarget.value = target
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // The spring does the smoothing, so a faster stream only produces targets it never reaches
        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
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
