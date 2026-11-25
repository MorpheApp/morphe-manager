/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import app.morphe.manager.ui.model.PatchRunProgress
import app.morphe.manager.ui.screen.shared.rememberAccessibilityEnabled
import app.morphe.manager.util.isSlowPatchingDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.milliseconds

/** How long a slow device shows the progress of a run before it takes over the next. */
private val SHOWN_PROGRESS_INTERVAL = 500.milliseconds

/** Progress of a patcher run as the screen shows it, from [rememberDisplayedPatchProgress]. */
@Stable
class DisplayedPatchProgress internal constructor(
    private val targetState: State<Float>,
    private val shownState: State<Float>
) {
    /** The progress the run reports, ahead of the progress shown. */
    val target: Float get() = targetState.value

    /** The progress to draw. */
    val value: Float get() = shownState.value
}

/**
 * Progress of a patcher run as the screen shows it: the progress the run reports, eased toward
 * each new value rather than jumping to it. Easing keeps the screen busy on every frame, so a slow
 * device takes the progress over twice a second at most while the run goes, see [isSlowPatchingDevice].
 *
 * @param run The run to follow. A queue that moves on to the next app starts over from its progress.
 * @param succeeded Outcome of the run, null while it is still going. Success fills the progress,
 * which every device eases toward, as nothing runs beside it anymore.
 */
@Composable
fun rememberDisplayedPatchProgress(
    run: PatchRunProgress,
    succeeded: Boolean?
): DisplayedPatchProgress {
    val target = remember(run, succeeded) {
        derivedStateOf { if (succeeded == true) 1f else run.progress }
    }

    // A new run starts from its own progress instead of sliding back from where the last one ended
    val shown = remember(run) { Animatable(target.value) }
    // Skip the easing when TalkBack is active so the main thread isn't busy interpolating and can
    // serve accessibility events instead
    val reduceMotion = rememberAccessibilityEnabled()
    LaunchedEffect(shown, target, reduceMotion) {
        if (isSlowPatchingDevice && succeeded == null) {
            // Reports that arrive during the delay are left out but for the last of them
            snapshotFlow { target.value }.collect {
                shown.snapTo(it)
                delay(SHOWN_PROGRESS_INTERVAL)
            }
        }
        // A spring keeps its velocity when the target moves again, so the progress follows the
        // frequent small steps smoothly instead of restarting its easing on each of them
        snapshotFlow { target.value }.collectLatest {
            if (reduceMotion) {
                shown.snapTo(it)
            } else {
                shown.animateTo(it, spring(stiffness = Spring.StiffnessVeryLow))
            }
        }
    }
    return remember(target, shown) { DisplayedPatchProgress(target, shown.asState()) }
}

/**
 * Ramps the patcher background up with [progress] while [active], and settles it back at normal
 * speed once the run ends or the screen goes. A slow device has no CPU time to spare for it while
 * patching, so there the background stands still instead, see [isSlowPatchingDevice].
 *
 * @param progress Reads the progress the speed follows, polled four times a second.
 * @param run Identifies the run, so a queue that moves on to the next app ramps up from rest again.
 */
@Composable
fun PatchingBackgroundSpeedEffect(
    active: Boolean,
    progress: () -> Float,
    onSpeedChange: (Float) -> Unit,
    run: Any? = null
) {
    val currentProgress by rememberUpdatedState(progress)
    val currentOnSpeedChange by rememberUpdatedState(onSpeedChange)

    // Drive background speed: ramps 1x→3x during patching, resets on completion/failure.
    // Uses a coroutine loop so speed tracks displayProgress in real time without recomposition churn
    LaunchedEffect(active, run) {
        if (!active) {
            currentOnSpeedChange(1f)
            return@LaunchedEffect
        }
        if (isSlowPatchingDevice) {
            currentOnSpeedChange(0f)
            return@LaunchedEffect
        }
        // Exponential moving average to smooths sudden progress jumps
        var movingAverage = 0.0f
        // Lower factor has more abrupt animation changes
        val smoothingFactor = 0.25f
        // Patching in progress - poll displayProgress every 250ms (same cadence as progress loop)
        while (true) {
            movingAverage = (1 - smoothingFactor) * movingAverage +
                    smoothingFactor * currentProgress()
            currentOnSpeedChange(1 + movingAverage)
            delay(250.milliseconds)
        }
    }

    // Restore speed when leaving the screen
    DisposableEffect(Unit) {
        onDispose { currentOnSpeedChange(1f) }
    }
}
