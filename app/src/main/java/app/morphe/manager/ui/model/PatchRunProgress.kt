/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import android.content.Context
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.toMutableStateList
import app.morphe.manager.R
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.runtime.MemoryMonitor.LOG_MEMORY_PREFIX_CURRENT
import app.morphe.manager.patcher.runtime.process.PatcherProcess.Companion.LOG_PROCESS_PREFIX_PROCESS_HEAP
import app.morphe.manager.patcher.split.SplitApkPreparer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Morphe Patcher"

/** Live state of one patcher run, as consumed by the patching screens. */
interface PatchProgressSource {
    val steps: List<Step>
    val logs: List<Pair<LogLevel, String>>
    val heapSamples: List<Int>
    val heapLimitMb: Int
}

/**
 * Step, log and progress bookkeeping for a single patcher run.
 *
 * Both the single-app flow and the batch queue drive one of these, which is what lets them
 * share the same patching screens: the screens only ever read from [PatchProgressSource].
 *
 * @param restoredSteps Steps handed back after process death, null for a fresh run.
 */
class PatchRunProgress(
    context: Context,
    private val scope: CoroutineScope,
    private val totalPatches: Int,
    splitStepActive: Boolean = false,
    restoredSteps: List<Step>? = null,
    restoredCompletedPatches: Int = 0
) : PatchProgressSource {

    private val appContext: Context = context.applicationContext

    override val steps = (restoredSteps ?: generatePatchSteps(context, splitStepActive)).toMutableStateList()

    /** Real-time log entries, collected from the patcher worker. */
    override val logs = mutableStateListOf<Pair<LogLevel, String>>()

    /** Heap usage samples (MB) collected every second while patching. */
    override val heapSamples = mutableStateListOf<Int>()

    /** Heap limit (MB) of the patcher process, parsed from the runtime's log line. */
    override var heapLimitMb by mutableIntStateOf(0)
        private set

    var completedPatches by mutableIntStateOf(restoredCompletedPatches)
        private set

    /** Share of the progress bar left for executing patches after the fixed steps. */
    private var patchesPercentage = max(0.0, 1.0 - steps.sumOf { it.progressPercentage })

    private var currentStepIndex = steps.indexOfFirst { it.state == State.RUNNING }.coerceAtLeast(0)

    private var requiresSplitPreparation = steps.any { it.id == StepId.PREPARE_SPLIT_APK }

    private var stallWatchJob: Job? = null

    private val _showLongStepWarning = MutableStateFlow(false)

    /** True once the current step has been running for over a minute without progress. */
    val showLongStepWarning: StateFlow<Boolean> = _showLongStepWarning.asStateFlow()

    val patchesProgress get() = completedPatches to totalPatches

    /** [0, 1] progress across every step of the run. */
    val progress by derivedStateOf {
        val current = steps.sumOf {
            if (it.state == State.COMPLETED && it.category != StepCategory.PATCHING) {
                it.progressPercentage
            } else {
                0.0
            }
        } + (completedPatches / totalPatches.coerceAtLeast(1).toDouble()) * patchesPercentage

        min(1.0, current).toFloat()
    }

    /** Logger handed to the patcher worker. */
    val logger = object : Logger() {
        override fun log(level: LogLevel, message: String) = record(level, message)
    }

    /**
     * Files the worker reports back as the real input, which is also where the split
     * preparation step is confirmed or dropped.
     */
    fun onProgress(name: String?, state: State?, message: String?) {
        scope.launch(Dispatchers.Main) {
            steps[currentStepIndex] = steps[currentStepIndex].run {
                copy(
                    name = name ?: this.name,
                    state = state ?: this.state,
                    message = message ?: this.message
                )
            }

            if (state == State.COMPLETED && currentStepIndex != steps.lastIndex) {
                currentStepIndex++
                steps[currentStepIndex] = steps[currentStepIndex].copy(state = State.RUNNING)
            }
        }
    }

    fun onPatchCompleted() {
        scope.launch(Dispatchers.Main) { completedPatches += 1 }
    }

    /**
     * Adds or removes the split preparation step as the worker learns what the input
     * actually is. [merged] marks the step as done once the archive has been merged.
     */
    fun updateSplitRequirement(
        file: File?,
        needsSplitOverride: Boolean? = null,
        merged: Boolean = false
    ) {
        val needsSplit = needsSplitOverride
            ?: merged
            || file?.let(SplitApkPreparer::isSplitArchive) == true

        when {
            needsSplit && !requiresSplitPreparation -> {
                requiresSplitPreparation = true
                addSplitStep()
            }

            !needsSplit && requiresSplitPreparation -> {
                requiresSplitPreparation = false
                removeSplitStep()
                return
            }
        }

        if (needsSplit && merged) {
            val index = steps.indexOfFirst { it.id == StepId.PREPARE_SPLIT_APK }
            if (index >= 0) {
                steps[index] = steps[index].copy(state = State.COMPLETED)
                if (currentStepIndex == index && index < steps.lastIndex) {
                    currentStepIndex++
                    steps[currentStepIndex] = steps[currentStepIndex].copy(state = State.RUNNING)
                }
            }
        }
    }

    /**
     * Starts watching for a stalled step. The warning clears whenever real progress moves
     * again, so a slow but advancing run never triggers it.
     */
    fun startStallWatch() {
        stallWatchJob?.cancel()
        stallWatchJob = scope.launch {
            var lastProgress = Snapshot.withoutReadObservation { progress }
            var stepStartTime = System.currentTimeMillis()

            while (isActive) {
                val now = System.currentTimeMillis()
                val current = Snapshot.withoutReadObservation { progress }
                if (current != lastProgress) {
                    lastProgress = current
                    stepStartTime = now
                    _showLongStepWarning.value = false
                } else if (!_showLongStepWarning.value && now - stepStartTime > STALL_THRESHOLD_MS) {
                    _showLongStepWarning.value = true
                }
                delay(250.milliseconds)
            }
        }
    }

    fun stopStallWatch() {
        stallWatchJob?.cancel()
        stallWatchJob = null
        _showLongStepWarning.value = false
    }

    private fun record(level: LogLevel, message: String) {
        level.androidLog(message)

        if (message.startsWith(LOG_PROCESS_PREFIX_PROCESS_HEAP)) {
            val mb = message.removePrefix(LOG_PROCESS_PREFIX_PROCESS_HEAP)
                .substringBefore("MB").trim().toIntOrNull()
            if (mb != null) scope.launch(Dispatchers.Main) { heapLimitMb = mb }
            // Still passed through to the log panel
        }

        if (message.startsWith(LOG_MEMORY_PREFIX_CURRENT)) {
            val mb = message.removePrefix(LOG_MEMORY_PREFIX_CURRENT)
                .substringBefore("MB").toIntOrNull()
            if (mb != null) {
                scope.launch(Dispatchers.Main) {
                    heapSamples.add(mb)
                    if (heapSamples.size > HEAP_SAMPLE_LIMIT) heapSamples.removeAt(0)
                }
            }
            return // Raw heap polls would drown out the log panel
        }

        if (level == LogLevel.TRACE) return

        scope.launch(Dispatchers.Main) { logs.add(level to message) }
    }

    private fun addSplitStep() {
        if (steps.any { it.id == StepId.PREPARE_SPLIT_APK }) return

        val loadIndex = steps.indexOfFirst { it.id == StepId.LOAD_PATCHES }
        val insertIndex = when {
            loadIndex >= 0 -> loadIndex + 1
            else -> steps.indexOfFirst { it.id == StepId.READ_APK }.takeIf { it >= 0 } ?: steps.size
        }
        val state = if (insertIndex <= currentStepIndex) State.COMPLETED else State.WAITING

        steps.add(insertIndex, buildSplitStep(appContext, state = state))

        if (insertIndex <= currentStepIndex) {
            currentStepIndex++
        }
    }

    private fun removeSplitStep() {
        val index = steps.indexOfFirst { it.id == StepId.PREPARE_SPLIT_APK }
        if (index == -1) return

        val removingCurrent = index == currentStepIndex
        steps.removeAt(index)

        when {
            currentStepIndex > index -> currentStepIndex--
            removingCurrent -> {
                currentStepIndex = index.coerceAtMost(steps.lastIndex).coerceAtLeast(0)
                if (steps.isNotEmpty()) {
                    val current = steps[currentStepIndex]
                    if (current.state == State.WAITING) {
                        steps[currentStepIndex] = current.copy(state = State.RUNNING)
                    }
                }
            }
        }
    }

    private companion object {
        const val HEAP_SAMPLE_LIMIT = 60
        const val STALL_THRESHOLD_MS = 60_000L

        fun LogLevel.androidLog(msg: String) = when (this) {
            LogLevel.TRACE -> Log.v(TAG, msg)
            LogLevel.INFO -> Log.i(TAG, msg)
            LogLevel.WARN -> Log.w(TAG, msg)
            LogLevel.ERROR -> Log.e(TAG, msg)
        }
    }
}

/**
 * Builds the step pipeline of a patcher run. The percentages add up to less than one, the
 * remainder is what executing patches is worth.
 */
fun generatePatchSteps(context: Context, splitStepActive: Boolean): List<Step> = listOfNotNull(
    Step(
        id = StepId.LOAD_PATCHES,
        name = context.getString(R.string.patcher_step_load_patches),
        category = StepCategory.PREPARING,
        state = State.RUNNING,
        progressPercentage = 0.05
    ),
    buildSplitStep(context).takeIf { splitStepActive },
    Step(
        id = StepId.READ_APK,
        name = context.getString(R.string.patcher_step_unpack),
        category = StepCategory.PREPARING,
        progressPercentage = 0.05
    ),
    Step(
        id = StepId.EXECUTE_PATCHES,
        name = context.getString(R.string.applying_patches),
        category = StepCategory.PATCHING,
        // Takes whatever percentage the other steps did not claim
        progressPercentage = 0.0
    ),
    Step(
        id = StepId.WRITE_PATCHED_APK,
        name = context.getString(R.string.patcher_step_write_patched),
        category = StepCategory.SAVING,
        progressPercentage = 0.4
    ),
    Step(
        id = StepId.SIGN_PATCHED_APK,
        name = context.getString(R.string.patcher_step_sign_apk),
        category = StepCategory.SAVING,
        progressPercentage = 0.1
    )
)

fun buildSplitStep(
    context: Context,
    state: State = State.WAITING,
    message: String? = null
) = Step(
    id = StepId.PREPARE_SPLIT_APK,
    name = context.getString(R.string.patcher_step_prepare_split_apk),
    category = StepCategory.PREPARING,
    state = state,
    message = message,
    progressPercentage = 0.1
)
