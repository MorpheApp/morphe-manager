/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.R
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.runtime.MemoryMonitor.LOG_MEMORY_FIELD_AVERAGE
import app.morphe.manager.patcher.runtime.MemoryMonitor.LOG_MEMORY_FIELD_MAX
import app.morphe.manager.patcher.runtime.MemoryMonitor.LOG_MEMORY_PREFIX_DONE
import app.morphe.manager.patcher.runtime.process.PatcherProcess.Companion.LOG_PROCESS_PREFIX_PROCESS_HEAP
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_PROCESS_PREFIX_COROUTINE_HEAP
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_ANDROID
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_API
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_ELAPSED
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_MEMORY_LIMIT
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_RAM_AVAIL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_RAM_TOTAL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_SIZE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_STORAGE_AVAIL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_STORAGE_TOTAL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_DEVICE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_RUNTIME
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_SUCCEEDED
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.screen.shared.contentPadding
import app.morphe.manager.ui.screen.shared.itemSpacing
import app.morphe.manager.ui.screen.shared.rememberWindowSize
import app.morphe.manager.ui.screen.shared.useTwoColumnLayout
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.delay

sealed interface LogItem {
    /**
     * Structured card shown at the start of patching.
     * Aggregates data from "Patching started at …", "Runtime: …",
     * "Process heap memory limit: …" and "Device: …" log lines.
     */
    data class StartBanner(
        val packageName: String,
        val version: String,
        val apkSizeMb: String,
        val patchCount: Int,
        val isSplit: Boolean,
        // null when using CoroutineRuntime
        val runtimeMemoryLimitMb: String?,
        // device environment
        val androidVersion: String?,
        val ramAvailable: String?,
        val ramTotal: String?,
        val storageAvailable: String?,
        val storageTotal: String?,
        val deviceManufacturer: String?,
        val deviceModel: String?,
    ) : LogItem

    /**
     * Structured card shown after patching succeeds.
     * Aggregates data from "Patching succeeded: …" and
     * "Process heap after patching: …" log lines.
     */
    data class SuccessSummary(
        val outputSizeMb: String,
        val elapsedSec: String,
        // null when using CoroutineRuntime (no separate process)
        val processHeapAverageMb: String?,
        val processHeapMaxMb: String?,
    ) : LogItem

    /** Standard single-line log entry. */
    data class Entry(val level: LogLevel, val message: String) : LogItem
}

/** Extracts a space-delimited key=value field from a flat log string.
 *  Supports quoted values (e.g. key="some value with spaces"). */
private fun String.logField(key: String): String? {
    val prefix = "$key="
    val start = indexOf(prefix).takeIf { it >= 0 } ?: return null
    val valueStart = start + prefix.length
    if (valueStart >= length) return null
    return if (this[valueStart] == '"') {
        val end = indexOf('"', valueStart + 1).takeIf { it >= 0 } ?: return null
        substring(valueStart + 1, end).ifBlank { null }
    } else {
        val end = indexOf(' ', valueStart).takeIf { it >= 0 } ?: length
        substring(valueStart, end).ifBlank { null }
    }
}

private fun formatElapsed(ms: Long?): String {
    if (ms == null || ms < 0) return "?"
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

/**
 * Converts the full raw log list into display [LogItem]s in a single stateful pass.
 *
 * Lines that carry metadata for the banner/summary cards (Runtime, heap limit,
 * heap-after-patching) are consumed and never emitted as plain [LogItem.Entry]s.
 */
internal fun List<Pair<LogLevel, String>>.toLogItems(): List<LogItem> {
    // Pre-scan for auxiliary lines so banner cards can be built in one pass.
    var runtimeMemoryLimitMb: String? = null
    var processHeapAverageMb: String? = null
    var processHeapMaxMb: String? = null
    var androidVersion: String? = null
    var ramAvailable: String? = null
    var ramTotal: String? = null
    var storageAvailable: String? = null
    var storageTotal: String? = null
    var deviceManufacturer: String? = null
    var deviceModel: String? = null

    for ((_, message) in this) {
        when {
            message.startsWith(LOG_WORKER_PREFIX_RUNTIME) -> {
                runtimeMemoryLimitMb = message.logField(LOG_WORKER_FIELD_MEMORY_LIMIT)?.let { "${it}MB" }
            }
            message.startsWith(LOG_WORKER_PREFIX_DEVICE) -> {
                androidVersion = message.logField(LOG_WORKER_FIELD_ANDROID)?.let { v ->
                    message.logField(LOG_WORKER_FIELD_API)?.let { "$v (API $it)" } ?: v
                }
                ramAvailable = message.logField(LOG_WORKER_FIELD_RAM_AVAIL)
                ramTotal     = message.logField(LOG_WORKER_FIELD_RAM_TOTAL)
                storageAvailable = message.logField(LOG_WORKER_FIELD_STORAGE_AVAIL)
                storageTotal     = message.logField(LOG_WORKER_FIELD_STORAGE_TOTAL)
            }
            message.startsWith(LOG_MEMORY_PREFIX_DONE) -> {
                processHeapAverageMb  = message.logField(LOG_MEMORY_FIELD_AVERAGE)
                processHeapMaxMb   = message.logField(LOG_MEMORY_FIELD_MAX)
            }
        }
    }

    val skipPrefixes = setOf(
        LOG_PROCESS_PREFIX_PROCESS_HEAP,
        LOG_PROCESS_PREFIX_COROUTINE_HEAP,
        LOG_MEMORY_PREFIX_DONE,
        LOG_WORKER_PREFIX_DEVICE,
        LOG_WORKER_PREFIX_RUNTIME
    )

    val result = mutableListOf<LogItem>()
    for ((level, message) in this) {
        when {
            skipPrefixes.any { message.startsWith(it) } -> { /* consumed above */ }

            message.startsWith("Patching started at ") -> {
                val pkg = message.logField("pkg")
                deviceManufacturer = message.logField("device")
                deviceModel = message.logField("model")
                if (pkg != null) {
                    result += LogItem.StartBanner(
                        packageName = pkg,
                        version = message.logField("version") ?: "?",
                        apkSizeMb = "%.1f MB".format(
                            (message.logField("size")?.toLongOrNull() ?: 0L) / 1_048_576.0
                        ),
                        patchCount = message.logField("patches")?.toIntOrNull() ?: 0,
                        isSplit = message.logField("split") == "true",
                        runtimeMemoryLimitMb = runtimeMemoryLimitMb,
                        androidVersion = androidVersion,
                        ramAvailable = ramAvailable,
                        ramTotal = ramTotal,
                        storageAvailable = storageAvailable,
                        storageTotal = storageTotal,
                        deviceManufacturer = deviceManufacturer,
                        deviceModel = deviceModel,
                    )
                } else {
                    result += LogItem.Entry(level, message)
                }
            }

            message.startsWith(LOG_WORKER_PREFIX_SUCCEEDED) -> {
                result += LogItem.SuccessSummary(
                    outputSizeMb = "%.1f MB".format(
                        (message.logField(LOG_WORKER_FIELD_SIZE)?.toLongOrNull() ?: 0L) / 1_048_576.0
                    ),
                    elapsedSec = formatElapsed(
                        message.logField(LOG_WORKER_FIELD_ELAPSED)?.filter { it.isDigit() }?.toLongOrNull()
                    ),
                    processHeapAverageMb  = processHeapAverageMb,
                    processHeapMaxMb   = processHeapMaxMb,
                )
            }

            else -> result += LogItem.Entry(level, message)
        }
    }
    return result
}

/**
 * Expert mode patching screen.
 *
 * Shows a horizontal linear progress bar, step pipeline, and real-time log
 * output sourced directly from [PatcherViewModel.logs].
 */
@Composable
fun ExpertPatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    patcherViewModel: PatcherViewModel,
    patcherSucceeded: Boolean? = null,
    onCancelClick: () -> Unit,
    onInstallClick: () -> Unit = {},
    onHomeClick: () -> Unit
) {
    val (completed, total) = patchesProgress
    val rawLogs = patcherViewModel.logs
    val initialIndex = (rawLogs.size - 1).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val windowSize = rememberWindowSize()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Formats all raw log entries as plain text for clipboard
    fun buildLogsText(): String = rawLogs.joinToString(separator = "\n") { (level, message) ->
        "[${level.name}] $message"
    }

    LaunchedEffect(rawLogs.size) {
        if (rawLogs.isNotEmpty()) {
            // Small delay so AnimatedVisibility places the new item in layout
            // before we scroll to it - otherwise the item stays off-screen.
            delay(50)
            listState.animateScrollToItem(rawLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Content area
        if (windowSize.useTwoColumnLayout) {
            // Landscape: header left, log right
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding)
                    .padding(top = windowSize.contentPadding),
                horizontalArrangement = Arrangement.spacedBy(windowSize.contentPadding),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    ExpertProgressHeader(
                        progress = progress,
                        completed = completed,
                        total = total,
                        patcherViewModel = patcherViewModel,
                        patcherSucceeded = patcherSucceeded
                    )
                }

                ExpertLogPanel(
                    patcherViewModel = patcherViewModel,
                    listState = listState,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Portrait: header on top, log fills remaining space
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding)
                    .padding(top = windowSize.contentPadding),
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing)
            ) {
                ExpertProgressHeader(
                    progress = progress,
                    completed = completed,
                    total = total,
                    patcherViewModel = patcherViewModel,
                    patcherSucceeded = patcherSucceeded
                )

                ExpertLogPanel(
                    patcherViewModel = patcherViewModel,
                    listState = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }

        // Spacing above the action bar so it doesn't feel attached to the log panel
        Spacer(Modifier.height(12.dp))

        PatcherBottomActionBar(
            showCancelButton = patcherSucceeded == null,
            showHomeButton = patcherSucceeded == true,
            showInstallButton = patcherSucceeded == true,
            showSaveButton = false,
            showErrorButton = false,
            showCopyLogsButton = true,
            onCancelClick = onCancelClick,
            onHomeClick = onHomeClick,
            onInstallClick = onInstallClick,
            onSaveClick = {},
            onErrorClick = {},
            onCopyLogsClick = {
                clipboardManager.setText(AnnotatedString(buildLogsText()))
            }
        )
    }
}

/**
 * Header section: title, animated progress bar, step name, patch counter, and long-step warning.
 */
@Composable
private fun ExpertProgressHeader(
    progress: Float,
    completed: Int,
    total: Int,
    patcherViewModel: PatcherViewModel,
    patcherSucceeded: Boolean? = null
) {
    val currentStep by remember {
        derivedStateOf {
            patcherViewModel.steps.firstOrNull { it.state == State.RUNNING }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Title + percentage badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.patching_app),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            PercentageBadge(progress = progress)
        }

        // Progress bar
        ExpertLinearProgressBar(progress = progress)

        // Current step name + patch counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = currentStep?.name,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "expert_step_name",
                modifier = Modifier.weight(1f)
            ) { stepName ->
                Text(
                    text = stepName ?: stringResource(R.string.patcher_success_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (total > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (patcherSucceeded == true)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$completed / $total",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (patcherSucceeded == true)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Memory graph
        val heapSamples = patcherViewModel.heapSamples
        val heapLimitMb = patcherViewModel.heapLimitMb
        AnimatedVisibility(
            visible = heapSamples.isNotEmpty(),
            enter = fadeIn(tween(500)) + expandVertically(tween(500)),
        ) {
            HeapUsageGraph(
                samples = heapSamples,
                maxHeapMb = heapLimitMb.takeIf { it > 0 }
                    ?: (Runtime.getRuntime().maxMemory().toInt() / (1024 * 1024)),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Step pipeline
        ExpertStepPipeline(patcherViewModel = patcherViewModel)
    }
}

/**
 * Pill badge showing current progress as an integer percentage.
 */
@Composable
private fun PercentageBadge(progress: Float) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

/**
 * Horizontal progress bar with a gradient fill.
 */
@Composable
private fun ExpertLinearProgressBar(progress: Float) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "expert_linear_progress"
    )
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animated.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(container, primary)))
        )
    }
}

/**
 * Live bar graph showing heap usage over the last 60 seconds.
 */
@Composable
private fun HeapUsageGraph(
    samples: List<Int>,
    maxHeapMb: Int,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val warnColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.memory_usage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                Text(
                    text = "${samples.lastOrNull() ?: 0} MB / $maxHeapMb MB",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }

            // Bar graph — 60 slots, filled from right
            val slotCount = 60
            val padded = List(slotCount - samples.size) { 0 } + samples.takeLast(slotCount)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val redThresholdForMaxColor = 1.0f
                val smoothStart = 0.7f
                val memoryFractionRollingAverageSamples = 3
                var memoryFractionAverage = 0.0

                padded.forEach { sample ->
                    val memoryUsage = if (maxHeapMb > 0) {
                        (sample / maxHeapMb.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    memoryFractionAverage =
                        (memoryFractionAverage * memoryFractionRollingAverageSamples + memoryUsage) /
                                (memoryFractionRollingAverageSamples + 1)

                    // Only interpolate above 70%
                    val t = if (memoryFractionAverage <= smoothStart) {
                        0f
                    } else {
                        ((memoryFractionAverage - smoothStart) / (redThresholdForMaxColor - smoothStart))
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                    }

                    val color = androidx.compose.ui.graphics.lerp(barColor, warnColor, t)

                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(if (sample > 0) trackColor.copy(alpha = 0.3f) else Color.Transparent)
                        )
                        if (memoryUsage > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(memoryUsage)
                                    .align(Alignment.BottomCenter)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(color.copy(alpha = 0.75f))
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Row of dots connected by lines representing each patching step.
 */
@Composable
private fun ExpertStepPipeline(patcherViewModel: PatcherViewModel) {
    val steps = patcherViewModel.steps

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, step ->
                val isRunning = step.state == State.RUNNING
                val isCompleted = step.state == State.COMPLETED
                val isFailed = step.state == State.FAILED

                // Animate dot color on state transitions
                val targetDotColor = when {
                    isFailed    -> MaterialTheme.colorScheme.error
                    isRunning   -> MaterialTheme.colorScheme.primary
                    isCompleted -> MaterialTheme.colorScheme.tertiary
                    else        -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                }
                val dotColor by animateColorAsState(
                    targetValue = targetDotColor,
                    animationSpec = tween(400),
                    label = "step_dot_color_$index"
                )

                // Animate connector line fill when previous step completes
                if (index > 0) {
                    val prevCompleted = steps.getOrNull(index - 1)?.state == State.COMPLETED
                    val lineProgress by animateFloatAsState(
                        targetValue = if (prevCompleted) 1f else 0f,
                        animationSpec = tween(500, easing = FastOutSlowInEasing),
                        label = "step_line_$index"
                    )
                    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    val fillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)

                    Box(modifier = Modifier
                        .weight(1f)
                        .height(2.dp)) {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(trackColor))
                        Box(modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(lineProgress)
                            .background(fillColor))
                    }
                }

                val dotSize by animateDpAsState(
                    targetValue = if (isRunning) 16.dp else 11.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "step_dot_size_$index"
                )

                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner dot for running state → "ring" effect
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scrollable log panel backed directly by [PatcherViewModel.logs].
 */
@Composable
private fun ExpertLogPanel(
    patcherViewModel: PatcherViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val rawLogs = patcherViewModel.logs
    // Convert the full list in one stateful pass so banner cards can aggregate metadata from auxiliary lines.
    val logItems = remember(rawLogs.size) { rawLogs.toLogItems() }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LogPanelHeader()

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 6.dp),
            ) {
                if (rawLogs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LiveIndicatorDot(size = 10.dp)
                                Text(
                                    text = "Waiting for log output…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(
                    count = logItems.size,
                    key = { index -> index }
                ) { index ->
                    LogItemContent(logItems[index])
                }

                // Bottom padding so last item isn't clipped by rounded corners
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/**
 * Panel header with a live-indicator dot and "Patcher Logs" label.
 */
@Composable
private fun LogPanelHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveIndicatorDot(size = 8.dp)
        Text(
            text = "Patcher Logs",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


/**
 * Dispatches a [LogItem] to the appropriate composable.
 */
@Composable
private fun LogItemContent(item: LogItem) {
    when (item) {
        is LogItem.StartBanner -> StartBannerCard(item)
        is LogItem.SuccessSummary -> SuccessSummaryCard(item)
        is LogItem.Entry -> LogEntryRow(item.level, item.message)
    }
}

private enum class CardVariant { Start, Success }

/**
 * Universal banner card used for both the start and success log entries.
 */
@Composable
private fun PatcherInfoCard(
    title: String,
    variant: CardVariant,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentColor = when (variant) {
        CardVariant.Start   -> MaterialTheme.colorScheme.primary
        CardVariant.Success -> MaterialTheme.colorScheme.tertiary
    }
    val bgColor = when (variant) {
        CardVariant.Start   -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        CardVariant.Success -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: title + optional badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accentColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = accentColor.copy(alpha = 0.15f), thickness = 1.dp)

            content()
        }
    }
}

/**
 * Shown instead of the raw "Patching started at …" log line.
 * Also surfaces runtime mode, memory limit, and device environment.
 */
@Composable
private fun StartBannerCard(item: LogItem.StartBanner) {
    PatcherInfoCard(title = "Patching started", variant = CardVariant.Start) {
        BannerFieldFull("Package", item.packageName)
        BannerFieldFull("Version", item.version)

        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            thickness = 1.dp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell("APK size", item.apkSizeMb, Modifier.weight(1f))
            BannerFieldCell("Patches", item.patchCount.toString(), Modifier.weight(1f))
            BannerFieldCell(
                label = "Split APK",
                value = if (item.isSplit) "yes" else "no",
                modifier = Modifier.weight(1f),
                valueColor = if (item.isSplit) MaterialTheme.colorScheme.tertiary else null
            )
        }

        // Runtime row
        val runtimeLabel = if (item.runtimeMemoryLimitMb != null) "Process" else "Coroutine"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell("Runtime", runtimeLabel, Modifier.weight(1f))
            if (item.runtimeMemoryLimitMb != null) {
                BannerFieldCell(
                    label = "Heap limit",
                    value = item.runtimeMemoryLimitMb,
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Device environment only shown when data is available
        if (item.androidVersion != null || item.ramTotal != null || item.deviceManufacturer != null) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item.androidVersion?.let {
                    BannerFieldCell("Android", it, Modifier.weight(1f))
                }
                if (item.deviceManufacturer != null || item.deviceModel != null) {
                    val deviceLabel = listOfNotNull(item.deviceManufacturer, item.deviceModel)
                        .joinToString(" ")
                    BannerFieldCell("Device", deviceLabel, Modifier.weight(1f))
                }
            }

            if (item.ramTotal != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BannerFieldCell(
                        label = "RAM free",
                        value = "${item.ramAvailable ?: "?"} / ${item.ramTotal}",
                        modifier = Modifier.weight(1f)
                    )
                    if (item.storageTotal != null) {
                        BannerFieldCell(
                            label = "Storage free",
                            value = "${item.storageAvailable ?: "?"} / ${item.storageTotal}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shown instead of the raw "Patching succeeded: …" log line.
 */
@Composable
private fun SuccessSummaryCard(item: LogItem.SuccessSummary) {
    PatcherInfoCard(title = "Patching succeeded", variant = CardVariant.Success, badge = "✓") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell("Output size", item.outputSizeMb, Modifier.weight(1f))
            BannerFieldCell("Time", item.elapsedSec, Modifier.weight(1f))
        }

        if (item.processHeapAverageMb != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BannerFieldCell("Heap average", item.processHeapAverageMb, Modifier.weight(1f))
                BannerFieldCell("Heap max", item.processHeapMaxMb ?: "?", Modifier.weight(1f))
            }
        }
    }
}

/**
 * Full-width label+value field used inside banner cards for long strings (package, version).
 */
@Composable
private fun BannerFieldFull(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 9.sp, fontFamily = FontFamily.Monospace
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.5.sp
        )
    }
}

/**
 * Compact label+value cell used in multi-column rows inside banner cards.
 */
@Composable
private fun BannerFieldCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 9.sp, fontFamily = FontFamily.Monospace
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Standard single-line log entry - level badge + monospace message.
 */
@Composable
private fun LogEntryRow(level: LogLevel, message: String) {
    val colors = logLevelColors(level)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (colors.rowBg != Color.Unspecified) Modifier.background(colors.rowBg) else Modifier)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(shape = RoundedCornerShape(4.dp), color = colors.badgeBg) {
            Text(
                text = level.logBadge,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colors.text.copy(alpha = 0.85f),
            lineHeight = 17.sp,
            fontSize = 12.sp
        )
    }
}

private data class LogEntryColors(val rowBg: Color, val badgeBg: Color, val text: Color)

/**
 * Returns (rowBackground, badgeBackground, textColor) for a given [LogLevel].
 */
@Composable
private fun logLevelColors(level: LogLevel): LogEntryColors = when (level) {
    LogLevel.ERROR -> LogEntryColors(
        rowBg   = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
        badgeBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        text    = MaterialTheme.colorScheme.error
    )
    LogLevel.WARN -> LogEntryColors(
        rowBg   = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f),
        badgeBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        text    = MaterialTheme.colorScheme.tertiary
    )
    LogLevel.INFO -> LogEntryColors(
        rowBg   = Color.Unspecified,
        badgeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        text    = MaterialTheme.colorScheme.onSurface
    )
    LogLevel.TRACE -> LogEntryColors(
        rowBg   = Color.Unspecified,
        badgeBg = MaterialTheme.colorScheme.surfaceVariant,
        text    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    )
}

private val LogLevel.logBadge: String
    get() = when (this) {
        LogLevel.TRACE -> "T"
        LogLevel.INFO  -> "I"
        LogLevel.WARN  -> "W"
        LogLevel.ERROR -> "E"
    }

/**
 * Pulsing red dot used as a "live" indicator in the log panel header and empty state.
 */
@Composable
private fun LiveIndicatorDot(size: Dp = 8.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_alpha"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
    )
}
