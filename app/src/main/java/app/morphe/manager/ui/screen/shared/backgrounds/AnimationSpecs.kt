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
import android.view.Surface
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.ui.screen.shared.isInDialog
import app.morphe.manager.ui.screen.shared.opaqueModalOpen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Tilt shifts the artwork by about a dozen pixels at full range, so smaller steps land under a pixel
private const val TILT_TARGET_THRESHOLD = 0.02f

// How far from its calibrated pose the device can lean before the artwork stops following, in m/s²
// of gravity (about 18 degrees). A bounded shift lets a background overscan just enough to hide its edges
private const val MAX_TILT_ACCELERATION = 3f

// Backgrounds drift slowly enough that a 60 Hz step is indistinguishable from a 120 Hz one,
// and every skipped step is a full-screen repaint the GPU does not have to make.
// The cadence stays the same behind sheets and dialogs, where the backdrop is still on screen
internal const val BACKGROUND_STEP_INTERVAL_MS = 16f

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
    // A dialog's own backdrop, such as the background picker's preview, is what the dialog shows
    val covered = opaqueModalOpen() && !isInDialog()

    LaunchedEffect(lifecycleOwner, covered) {
        if (covered) return@LaunchedEffect

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            frameLoop()
        }
    }
}

// A background eased down to this share of its speed stands still to the eye
private const val STOPPED_SPEED = 0.01f

// How long the shared clock runs before it wraps. A Float of milliseconds this large still resolves
// a quarter of a millisecond, where an unbounded one stops resolving the 16 ms step after a day.
// It is a whole number of the cycles backgrounds wrap their own motion at, so those stay seamless
private const val ANIMATED_TIME_WRAP_MS = 3_600_000f

/**
 * Steps a background once per [BACKGROUND_STEP_INTERVAL_MS], handing [onStep] the milliseconds of
 * animation since the previous step: real time scaled by a speed that eases toward
 * [speedMultiplier] instead of jumping to it, along with that eased speed itself. Shared by the
 * clock most backgrounds read and by the ones that integrate their own physics. At a speed of 0 it
 * eases to a stop and takes no more frames until the speed changes.
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
        fun boostedTarget(): Float {
            val target = targetSpeed.floatValue
            return if (target == 0f) 0f else 1f + (target - 1f) * boost
        }

        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = boostedTarget()
        // Real elapsed time gates the step, while the scaled time is what the background reads,
        // so changing the speed never changes how often the canvas is invalidated
        var elapsedMs = 0f
        var pendingMs = 0f
        while (true) {
            if (targetSpeed.floatValue == 0f && currentSpeed < STOPPED_SPEED) {
                currentSpeed = 0f
                snapshotFlow { targetSpeed.floatValue }.first { it != 0f }
                lastFrameMs = withInfiniteAnimationFrameMillis { it }
            }
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
 * Parallax sensor state holder. [tiltX] and [tiltY] follow the screen's own axes and never leave
 * the range of plus or minus [maxTilt].
 */
data class ParallaxState(
    val tiltX: State<Float>,
    val tiltY: State<Float>,
    val maxTilt: Float
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
    sensitivity: Float = 0.3f
): ParallaxState {
    val smoothTiltX = remember { Animatable(0f) }
    val smoothTiltY = remember { Animatable(0f) }
    val tiltTarget = remember { MutableStateFlow(Offset.Zero) }

    // A single pair of springs chases the latest target. Starting a fresh pair per sensor event
    // meant a hundred coroutines a second, each cancelling the spring the one before had just begun
    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            tiltTarget.value = Offset.Zero
            launch { smoothTiltX.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
            launch { smoothTiltY.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
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

    // Registered only while resumed: the background outlives a stopped activity, and a listener
    // left running would keep the accelerometer awake for an app nobody is looking at
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    LaunchedEffect(enableParallax, lifecycleOwner) {
        if (!enableParallax) return@LaunchedEffect

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // No accelerometer available
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return@LaunchedEffect

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Calibration belongs to one registration, so the baseline resets with the listener itself
            var baselineX = 0f
            var baselineY = 0f
            var calibratedRotation = -1
            val display = ContextCompat.getDisplayOrDefault(context)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val rotation = display.rotation
                    val (screenX, screenY) = event.toScreenAxes(rotation)

                    // The activity handles rotation itself, so the listener survives it. A pose
                    // calibrated for the old orientation would read as a steep tilt in the new one
                    if (rotation != calibratedRotation) {
                        baselineX = screenX
                        baselineY = screenY
                        calibratedRotation = rotation
                    }

                    val rawTiltX = (screenX - baselineX)
                        .coerceIn(-MAX_TILT_ACCELERATION, MAX_TILT_ACCELERATION)
                    val rawTiltY = -(screenY - baselineY)
                        .coerceIn(-MAX_TILT_ACCELERATION, MAX_TILT_ACCELERATION)
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
            try {
                awaitCancellation()
            } finally {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    // Return State objects directly
    return ParallaxState(
        tiltX = smoothTiltX.asState(),
        tiltY = smoothTiltY.asState(),
        maxTilt = MAX_TILT_ACCELERATION * sensitivity
    )
}

/**
 * The accelerometer reading along the screen's x and y axes for a display at [rotation]. The sensor
 * reports in the device's natural orientation, whichever way the screen is turned.
 */
private fun SensorEvent.toScreenAxes(rotation: Int): Pair<Float, Float> {
    val x = values[0]
    val y = values[1]
    return when (rotation) {
        Surface.ROTATION_90  -> -y to x
        Surface.ROTATION_180 -> -x to -y
        Surface.ROTATION_270 -> y to -x
        else                 -> x to y
    }
}
