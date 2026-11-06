/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import app.morphe.manager.R
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.logger.logField
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_HEAP_LIMIT_PREFIX
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_MEMORY_PREFIX_CURRENT
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_FIELD_CPU
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_FIELD_IO_READ
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_FIELD_IO_WRITE
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_PREFIX_CURRENT
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.screen.patcher.LogItem
import app.morphe.manager.ui.screen.patcher.LogItemAccumulator
import app.morphe.patcher.PatcherTask
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Morphe Patcher"

/** One storage throughput sample of the patcher run, in kilobytes per second. */
data class IoSample(val readKbPerSec: Int, val writeKbPerSec: Int) {
    val totalKbPerSec get() = readKbPerSec + writeKbPerSec
}

/** Live state of one patcher run, as consumed by the patching screens. */
interface PatchProgressSource {
    val steps: List<Step>
    val logs: List<Pair<LogLevel, String>>
    val logItems: List<LogItem>
    val heapSamples: List<Int>
    val heapLimitMb: Int

    /** Load of every CPU core in percent, empty while the device exposes no core counters. */
    val cpuCoreLoads: List<Int>

    /** Storage throughput samples, collected alongside [heapSamples]. */
    val ioSamples: List<IoSample>

    /** Whether this run printed a log that is no longer available to show. */
    val logsLost: Boolean

    /** Cumulative number of log entries recorded during this run. */
    val totalLogCount: Int get() = logs.size

    /** The task the patcher is busy with besides executing patches, null while there is none. */
    val runningTask: TaskStatus?
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
    totalPatches: Int,
    splitStepActive: Boolean = false,
    restoredSteps: List<Step>? = null,
    restoredCompletedPatches: Int = 0,
    restoredFailedPatch: String? = null
) : PatchProgressSource, AutoCloseable {

    private val appContext: Context = context.applicationContext

    override val steps = (restoredSteps ?: generatePatchSteps(context, splitStepActive)).toMutableStateList()

    /** Real-time raw log entries, collected from the patcher worker. Kept intact for export/copying. */
    override val logs = mutableStateListOf<Pair<LogLevel, String>>()

    /** Display log items rendered by the expert log panel. */
    override val logItems = mutableStateListOf<LogItem>()

    override var totalLogCount by mutableIntStateOf(0)
        private set

    private val accumulator = LogItemAccumulator(targetList = logItems, maxItems = DISPLAYED_LOG_ITEMS_LIMIT)
    private val logChannel = Channel<Pair<LogLevel, String>>(Channel.UNLIMITED)
    private var consumerJob: Job? = null

    init {
        consumerJob = scope.launch(Dispatchers.Main.immediate) {
            val buffer = ArrayList<Pair<LogLevel, String>>()
            try {
                for (first in logChannel) {
                    buffer.add(first)
                    while (true) {
                        val next = logChannel.tryReceive().getOrNull() ?: break
                        buffer.add(next)
                    }
                    totalLogCount += buffer.size
                    logs.addAll(buffer)
                    accumulator.appendAll(buffer)
                    buffer.clear()
                }
            } catch (_: CancellationException) {
                // Canceled when run finishes or closes
            }
        }
    }

    /**
     * The log of a run that was interrupted by process death is gone: it only ever lived in
     * memory, and the steps came back from the saved state without it.
     */
    override val logsLost = restoredSteps != null

    /** Heap usage samples (MB) collected every second while patching. */
    override val heapSamples = mutableStateListOf<Int>()

    /** Heap limit (MB) of the runtime that patches, parsed from its log line. */
    override var heapLimitMb by mutableIntStateOf(0)
        private set

    /**
     * Replaced as a whole rather than mutated in place: the cores are read as one snapshot and
     * only mean anything next to each other.
     */
    override var cpuCoreLoads by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Storage throughput samples (KB/s) collected while patching. */
    override val ioSamples = mutableStateListOf<IoSample>()

    /** Patches the run applies, fewer than picked when some left their source before it started. */
    var totalPatches by mutableIntStateOf(totalPatches)

    var completedPatches by mutableIntStateOf(restoredCompletedPatches)
        private set

    /** The patch the run failed on, null while none has. */
    var failedPatch by mutableStateOf(restoredFailedPatch)
        private set

    private var currentStepIndex = steps.indexOfFirst { it.state == State.RUNNING }.coerceAtLeast(0)

    private var requiresSplitPreparation = steps.any { it.id == StepId.PREPARE_SPLIT_APK }

    private var stallWatchJob: Job? = null

    private val _showLongStepWarning = MutableStateFlow(false)

    /** True once the current step has been running for over a minute without progress. */
    val showLongStepWarning: StateFlow<Boolean> = _showLongStepWarning.asStateFlow()

    val patchesProgress get() = completedPatches to totalPatches

    /** How far the patcher got through each of its tasks, never going back within a run. */
    private val taskFractions = mutableStateMapOf<PatcherTask, Float>()

    override var runningTask by mutableStateOf<TaskStatus?>(null)
        private set

    /**
     * [0, 1] progress across every step of the run. Each step fills its share as it completes,
     * and the steps made of patcher tasks fill theirs as the tasks and patches get through.
     */
    val progress by derivedStateOf {
        var done = 0.0
        var total = 0.0
        steps.forEach { step ->
            total += step.progressPercentage
            done += step.progressPercentage * when (step.state) {
                State.COMPLETED -> 1.0
                State.RUNNING, State.FAILED -> stepFraction(step.id)
                State.WAITING -> 0.0
            }
        }
        if (total > 0) min(1.0, done / total).toFloat() else 0f
    }

    /** How far through its parts the step is, 0 for a step not made of any. */
    private fun stepFraction(step: StepId): Double {
        val parts = STEP_PARTS[step] ?: return 0.0
        return parts.entries.sumOf { (part, share) ->
            share * when (part) {
                StepPart.Patches -> completedPatches / totalPatches.coerceAtLeast(1).toDouble()
                is StepPart.Task -> taskFractions[part.task]?.toDouble() ?: 0.0
            }
        } / parts.values.sum()
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

    fun onTaskProgress(task: PatcherTask, done: Long, total: Long) {
        scope.launch(Dispatchers.Main) {
            val fraction = if (total > 0) done.toFloat() / total else 0f
            taskFractions[task] = max(taskFractions[task] ?: 0f, fraction)
            runningTask = TaskStatus(task, done, total).takeIf { done < total }
        }
    }

    fun onPatchCompleted() {
        scope.launch(Dispatchers.Main) { completedPatches += 1 }
    }

    fun onPatchFailed(patchName: String) {
        scope.launch(Dispatchers.Main) { failedPatch = patchName }
    }

    /**
     * Drops everything the abandoned attempt reported and puts the pipeline back at its first
     * step, so the retry that follows counts from zero instead of on top of it.
     *
     * The log survives: it is the only record of why the run started over. Whether the input is
     * a split archive is a property of the input rather than of the attempt, so that is kept too.
     */
    fun onRestart() {
        scope.launch(Dispatchers.Main) {
            completedPatches = 0
            failedPatch = null
            currentStepIndex = 0
            steps.clear()
            steps.addAll(generatePatchSteps(appContext, requiresSplitPreparation))
            taskFractions.clear()
            runningTask = null
            // A fallback to ART reports a heap limit of its own
            heapLimitMb = 0
            heapSamples.clear()
            ioSamples.clear()
            cpuCoreLoads = emptyList()
            _showLongStepWarning.value = false
            accumulator.reset()
            totalLogCount = logs.size
            accumulator.appendAll(logs)
        }
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

    /**
     * Holds back the display of a run that is waiting on an answer before it starts, so the
     * screen behind the dialog shows neither progress nor a warning about a slow step.
     */
    fun holdBeforeStart() {
        stopStallWatch()
        setOpeningStepState(State.WAITING)
    }

    /** Puts back what [holdBeforeStart] held, once the run is free to start. */
    fun resumeBeforeStart() {
        setOpeningStepState(State.RUNNING)
        startStallWatch()
    }

    /** Moves the opening step between waiting and running, leaving one past that point alone. */
    private fun setOpeningStepState(state: State) {
        val opening = steps.firstOrNull() ?: return
        if (opening.state != State.WAITING && opening.state != State.RUNNING) return

        steps[0] = opening.copy(state = state)
    }

    fun stopStallWatch() {
        stallWatchJob?.cancel()
        stallWatchJob = null
        _showLongStepWarning.value = false
    }

    private fun record(level: LogLevel, message: String) {
        level.androidLog(message)

        if (message.startsWith(LOG_HEAP_LIMIT_PREFIX)) {
            val mb = message.removePrefix(LOG_HEAP_LIMIT_PREFIX)
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
                    if (heapSamples.size > SAMPLE_HISTORY_LIMIT) heapSamples.removeAt(0)
                }
            }
            return // Raw heap polls would drown out the log panel
        }

        if (message.startsWith(LOG_USAGE_PREFIX_CURRENT)) {
            val coreLoads = message.logField(LOG_USAGE_FIELD_CPU)
                ?.split(',')
                ?.mapNotNull(String::toIntOrNull)
            val read = message.logField(LOG_USAGE_FIELD_IO_READ)?.toIntOrNull()
            val write = message.logField(LOG_USAGE_FIELD_IO_WRITE)?.toIntOrNull()

            scope.launch(Dispatchers.Main) {
                if (!coreLoads.isNullOrEmpty()) cpuCoreLoads = coreLoads

                if (read != null && write != null) {
                    ioSamples.add(IoSample(read, write))
                    if (ioSamples.size > SAMPLE_HISTORY_LIMIT) ioSamples.removeAt(0)
                }
            }
            return // Raw usage polls would drown out the log panel
        }

        if (level == LogLevel.TRACE) return

        logChannel.trySend(level to message)
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

    /** Closes background log consuming coroutines and channels when the run finishes. */
    fun finish() {
        stopStallWatch()
        logChannel.close()
        consumerJob?.cancel()
    }

    override fun close() = finish()

    private companion object {
        const val SAMPLE_HISTORY_LIMIT = 60
        const val STALL_THRESHOLD_MS = 60_000L
        const val DISPLAYED_LOG_ITEMS_LIMIT = 2000

        fun LogLevel.androidLog(msg: String) = when (this) {
            LogLevel.TRACE -> Log.v(TAG, msg)
            LogLevel.INFO -> Log.i(TAG, msg)
            LogLevel.WARN -> Log.w(TAG, msg)
            LogLevel.ERROR -> Log.e(TAG, msg)
        }
    }
}

/** A patcher task with how far it got, as the patcher last reported it. */
@Immutable
data class TaskStatus(val task: PatcherTask, val done: Long, val total: Long)

/** Part of a step whose progress the patcher reports on its way. */
private sealed interface StepPart {
    data class Task(val task: PatcherTask) : StepPart

    data object Patches : StepPart
}

/**
 * What the steps that report progress on their way are made of, with the share of the step each
 * part takes in a typical run. A part a run skips leaves its share to be filled when the step completes.
 */
private val STEP_PARTS: Map<StepId, Map<StepPart, Double>> = mapOf(
    // Decoding happens when the first patches need it, so it runs among them
    StepId.EXECUTE_PATCHES to mapOf(
        StepPart.Task(PatcherTask.DECODING_DEX) to 0.06,
        StepPart.Task(PatcherTask.EXTRACTING_RESOURCES) to 0.04,
        StepPart.Task(PatcherTask.DECODING_RESOURCES) to 0.23,
        StepPart.Task(PatcherTask.INDEXING_CLASSES) to 0.1,
        StepPart.Patches to 0.57
    ),
    StepId.WRITE_PATCHED_APK to mapOf(
        StepPart.Task(PatcherTask.COMPILING_DEX) to 0.21,
        StepPart.Task(PatcherTask.COMPILING_RESOURCES) to 0.55,
        StepPart.Task(PatcherTask.WRITING_APK) to 0.24
    )
)

/**
 * Builds the step pipeline of a patcher run. The percentages are the share of time each step takes
 * in a typical run, relative to each other, so a split step that comes or goes keeps them adding up.
 */
fun generatePatchSteps(context: Context, splitStepActive: Boolean): List<Step> = listOfNotNull(
    Step(
        id = StepId.LOAD_PATCHES,
        name = context.getString(R.string.patcher_step_load_patches),
        category = StepCategory.PREPARING,
        state = State.RUNNING,
        progressPercentage = 0.04
    ),
    buildSplitStep(context).takeIf { splitStepActive },
    Step(
        id = StepId.READ_APK,
        name = context.getString(R.string.patcher_step_unpack),
        category = StepCategory.PREPARING,
        progressPercentage = 0.02
    ),
    Step(
        id = StepId.EXECUTE_PATCHES,
        name = context.getString(R.string.applying_patches),
        category = StepCategory.PATCHING,
        progressPercentage = 0.53
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
        progressPercentage = 0.01
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
