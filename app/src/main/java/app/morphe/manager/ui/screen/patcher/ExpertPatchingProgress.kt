/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.morphe.manager.R
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.logField
import app.morphe.manager.patcher.patch.PatchSourceRef
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_MEMORY_FIELD_AVERAGE
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_MEMORY_FIELD_MAX
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_MEMORY_PREFIX_DONE
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_FIELD_IO_PEAK
import app.morphe.manager.patcher.runtime.ResourceMonitor.LOG_USAGE_PREFIX_DONE
import app.morphe.manager.patcher.runtime.process.PatcherProcess.Companion.LOG_PROCESS_PREFIX_PROCESS_HEAP
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_PROCESS_PREFIX_COROUTINE_HEAP
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_ANDROID
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_API
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_DEVICE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_ELAPSED
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_MANAGER
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_MEMORY_LIMIT
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_MODEL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_NAME
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_NATIVE_LIBS
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_PACKAGE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_PATCHER
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_PATCHES
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_RAM_AVAIL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_RAM_TOTAL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_SIZE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_SPLIT
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_STORAGE_AVAIL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_STORAGE_TOTAL
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_FIELD_VERSION
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_BUILD
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_DEVICE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_RUNTIME
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_SOURCE
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_STARTED
import app.morphe.manager.patcher.worker.PatcherWorker.Companion.LOG_WORKER_PREFIX_SUCCEEDED
import app.morphe.manager.ui.model.PatchProgressSource
import app.morphe.manager.ui.model.State
import app.morphe.manager.ui.screen.patcher.game.MiniGameContent
import app.morphe.manager.ui.screen.patcher.game.MiniGameState
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.theme.MorpheBrandTeal
import app.morphe.manager.util.formatBytesForReport
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Inset every element inside a patcher card keeps from the edge of the card carrying it. */
internal val PatcherCardPadding = 10.dp

/** What a card's own margin and the surrounding list each contribute to that same inset. */
internal val PatcherCardMargin = PatcherCardPadding / 2

private const val SCROLL_THROTTLE_MS = 150L



sealed interface LogItem {
    /**
     * Structured card shown at the start of patching.
     * Aggregates data from "Patching started at …", "Runtime: …", "Process heap memory limit: …"
     * and "Device: …" log lines.
     */
    data class StartBanner(
        val packageName: String,
        val version: String,
        val sources: List<PatchSourceRef>,
        val managerVersion: String?,
        val patcherVersion: String?,
        val stripsNativeLibs: Boolean?,
        val apkSize: String,
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
     * Aggregates data from "Patching succeeded: …" and "Process heap after patching: …" log lines.
     */
    data class SuccessSummary(
        val outputSize: String,
        val elapsedSec: String,
        // null when using CoroutineRuntime (no separate process)
        val processHeapAverageMb: String?,
        val processHeapMaxMb: String?,
        // null on devices that expose no I/O counters to sample
        val ioPeakRate: String?,
    ) : LogItem

    /** Standard single-line log entry. */
    data class Entry(val level: LogLevel, val message: String) : LogItem
}

/** Reads a byte count out of a log line, formatted the way the rest of the app shows sizes. */
private fun String.logBytes(field: String): String =
    formatBytesForReport(logField(field)?.toLongOrNull() ?: 0L)

private fun formatElapsed(ms: Long?): String {
    if (ms == null || ms < 0) return "?"
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

/**
 * Incremental state machine that converts raw log lines into display [LogItem]s.
 *
 * Consumes auxiliary metadata lines for [LogItem.StartBanner] and [LogItem.SuccessSummary],
 * updating banners in-place when metadata arrives before or after the banner marker.
 */
internal class LogItemAccumulator(
    private val targetList: MutableList<LogItem> = mutableListOf(),
    private val maxItems: Int = Int.MAX_VALUE
) {
    val items: List<LogItem> get() = targetList

    private var startBannerIndex = -1
    private var successSummaryIndex = -1

    // Metadata for StartBanner
    private var managerVersion: String? = null
    private var patcherVersion: String? = null
    private var stripsNativeLibs: Boolean? = null
    private val sources = mutableListOf<PatchSourceRef>()
    private var runtimeMemoryLimitMb: String? = null
    private var androidVersion: String? = null
    private var ramAvailable: String? = null
    private var ramTotal: String? = null
    private var storageAvailable: String? = null
    private var storageTotal: String? = null
    private var startPackage: String? = null
    private var startVersion: String? = null
    private var startApkSize: String? = null
    private var startPatchCount: Int = 0
    private var startIsSplit: Boolean = false
    private var deviceManufacturer: String? = null
    private var deviceModel: String? = null

    // Metadata for SuccessSummary
    private var successOutputSize: String? = null
    private var successElapsedSec: String? = null
    private var processHeapAverageMb: String? = null
    private var processHeapMaxMb: String? = null
    private var ioPeakKbPerSec: Int? = null

    fun append(level: LogLevel, message: String): List<LogItem> {
        when {
            message.startsWith(LOG_WORKER_PREFIX_BUILD) -> {
                managerVersion = message.logField(LOG_WORKER_FIELD_MANAGER)
                patcherVersion = message.logField(LOG_WORKER_FIELD_PATCHER)
                stripsNativeLibs = message.logField(LOG_WORKER_FIELD_NATIVE_LIBS)?.toBooleanStrictOrNull()
                updateStartBanner()
            }
            message.startsWith(LOG_WORKER_PREFIX_SOURCE) -> {
                message.logField(LOG_WORKER_FIELD_NAME)?.let { name ->
                    sources += PatchSourceRef(
                        name = name,
                        version = message.logField(LOG_WORKER_FIELD_VERSION)?.takeIf { it != "?" }
                    )
                    updateStartBanner()
                }
            }
            message.startsWith(LOG_WORKER_PREFIX_RUNTIME) -> {
                runtimeMemoryLimitMb = message.logField(LOG_WORKER_FIELD_MEMORY_LIMIT)?.let { "${it}MB" }
                updateStartBanner()
            }
            message.startsWith(LOG_WORKER_PREFIX_DEVICE) -> {
                androidVersion = message.logField(LOG_WORKER_FIELD_ANDROID)?.let { v ->
                    message.logField(LOG_WORKER_FIELD_API)?.let { "$v (API $it)" } ?: v
                }
                ramAvailable = message.logField(LOG_WORKER_FIELD_RAM_AVAIL)
                ramTotal     = message.logField(LOG_WORKER_FIELD_RAM_TOTAL)
                storageAvailable = message.logField(LOG_WORKER_FIELD_STORAGE_AVAIL)
                storageTotal     = message.logField(LOG_WORKER_FIELD_STORAGE_TOTAL)
                updateStartBanner()
            }
            message.startsWith(LOG_MEMORY_PREFIX_DONE) -> {
                processHeapAverageMb = message.logField(LOG_MEMORY_FIELD_AVERAGE)
                processHeapMaxMb = message.logField(LOG_MEMORY_FIELD_MAX)
                updateSuccessSummary()
            }
            message.startsWith(LOG_USAGE_PREFIX_DONE) -> {
                ioPeakKbPerSec = message.logField(LOG_USAGE_FIELD_IO_PEAK)?.toIntOrNull()
                updateSuccessSummary()
            }
            message.startsWith(LOG_PROCESS_PREFIX_PROCESS_HEAP) ||
                message.startsWith(LOG_PROCESS_PREFIX_COROUTINE_HEAP) -> {
                // Auxiliary lines consumed without emitting a LogItem
            }
            message.startsWith(LOG_WORKER_PREFIX_STARTED) -> {
                val pkg = message.logField(LOG_WORKER_FIELD_PACKAGE)
                deviceManufacturer = message.logField(LOG_WORKER_FIELD_DEVICE)
                deviceModel = message.logField(LOG_WORKER_FIELD_MODEL)
                if (pkg != null) {
                    startPackage = pkg
                    startVersion = message.logField(LOG_WORKER_FIELD_VERSION) ?: "?"
                    startApkSize = message.logBytes(LOG_WORKER_FIELD_SIZE)
                    startPatchCount = message.logField(LOG_WORKER_FIELD_PATCHES)?.toIntOrNull() ?: 0
                    startIsSplit = message.logField(LOG_WORKER_FIELD_SPLIT) == "true"
                    startBannerIndex = targetList.size
                    addItem(buildStartBanner())
                } else {
                    addItem(LogItem.Entry(level, message))
                }
            }
            message.startsWith(LOG_WORKER_PREFIX_SUCCEEDED) -> {
                successOutputSize = message.logBytes(LOG_WORKER_FIELD_SIZE)
                successElapsedSec = formatElapsed(
                    message.logField(LOG_WORKER_FIELD_ELAPSED)?.filter { it.isDigit() }?.toLongOrNull()
                )
                successSummaryIndex = targetList.size
                addItem(buildSuccessSummary())
            }
            else -> {
                addItem(LogItem.Entry(level, message))
            }
        }
        return targetList
    }

    fun appendAll(entries: List<Pair<LogLevel, String>>): List<LogItem> {
        for ((level, message) in entries) {
            append(level, message)
        }
        return targetList
    }

    fun reset() {
        targetList.clear()
        startBannerIndex = -1
        successSummaryIndex = -1
        managerVersion = null
        patcherVersion = null
        stripsNativeLibs = null
        sources.clear()
        runtimeMemoryLimitMb = null
        androidVersion = null
        ramAvailable = null
        ramTotal = null
        storageAvailable = null
        storageTotal = null
        startPackage = null
        startVersion = null
        startApkSize = null
        startPatchCount = 0
        startIsSplit = false
        deviceManufacturer = null
        deviceModel = null
        successOutputSize = null
        successElapsedSec = null
        processHeapAverageMb = null
        processHeapMaxMb = null
        ioPeakKbPerSec = null
    }

    private fun addItem(item: LogItem) {
        if (targetList.size >= maxItems) {
            val removeIndex = if (startBannerIndex == 0) 1 else 0
            if (removeIndex < targetList.size) {
                targetList.removeAt(removeIndex)
                if (successSummaryIndex > removeIndex) {
                    successSummaryIndex--
                }
            }
        }
        targetList.add(item)
    }

    private fun updateStartBanner() {
        if (startBannerIndex in targetList.indices) {
            targetList[startBannerIndex] = buildStartBanner()
        }
    }

    private fun buildStartBanner(): LogItem.StartBanner = LogItem.StartBanner(
        packageName = startPackage.orEmpty(),
        version = startVersion ?: "?",
        sources = sources.toList(),
        managerVersion = managerVersion,
        patcherVersion = patcherVersion,
        stripsNativeLibs = stripsNativeLibs,
        apkSize = startApkSize.orEmpty(),
        patchCount = startPatchCount,
        isSplit = startIsSplit,
        runtimeMemoryLimitMb = runtimeMemoryLimitMb,
        androidVersion = androidVersion,
        ramAvailable = ramAvailable,
        ramTotal = ramTotal,
        storageAvailable = storageAvailable,
        storageTotal = storageTotal,
        deviceManufacturer = deviceManufacturer,
        deviceModel = deviceModel,
    )

    private fun updateSuccessSummary() {
        if (successSummaryIndex in targetList.indices) {
            targetList[successSummaryIndex] = buildSuccessSummary()
        }
    }

    private fun buildSuccessSummary(): LogItem.SuccessSummary = LogItem.SuccessSummary(
        outputSize = successOutputSize.orEmpty(),
        elapsedSec = successElapsedSec ?: "?",
        processHeapAverageMb = processHeapAverageMb,
        processHeapMaxMb = processHeapMaxMb,
        ioPeakRate = ioPeakKbPerSec?.let(::formatRate),
    )
}

/**
 * Converts the full raw log list into display [LogItem]s in a single stateful pass.
 *
 * Lines that carry metadata for the banner/summary cards (Runtime, heap limit,
 * heap-after-patching) are consumed and never emitted as plain [LogItem.Entry]s.
 */
internal fun List<Pair<LogLevel, String>>.toLogItems(): List<LogItem> {
    val accumulator = LogItemAccumulator()
    return accumulator.appendAll(this).toList()
}

/**
 * Expert mode patching screen.
 *
 * Shows a horizontal linear progress bar, step pipeline, and real-time log
 * output sourced directly from [PatchProgressSource.logs].
 */
@Composable
fun ExpertPatchingInProgress(
    progress: Float,
    patchesProgress: Pair<Int, Int>,
    patchProgress: PatchProgressSource,
    packageName: String? = null,
    patcherSucceeded: Boolean? = null,
    miniGameState: MiniGameState,
    queueHeader: (@Composable () -> Unit)? = null,
    onCancelClick: () -> Unit,
    onInstallClick: () -> Unit = {},
    onHomeClick: () -> Unit
) {
    val (completed, total) = patchesProgress
    val rawLogs = patchProgress.logs
    val initialIndex = (rawLogs.size - 1).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val windowSize = rememberWindowSize()
    val copyToClipboard = rememberCopyToClipboard()

    // Formats all raw log entries as plain text for clipboard
    fun buildLogsText(): String = rawLogs.joinToString(separator = "\n") { (level, message) ->
        "[${level.name}] $message"
    }

    LaunchedEffect(patchProgress, patcherSucceeded) {
        var lastScrollTime = 0L
        snapshotFlow { patchProgress.totalLogCount }
            .collect { count ->
                if (count > 0 && patchProgress.logItems.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastScrollTime
                    if (elapsed < SCROLL_THROTTLE_MS && patcherSucceeded == null) {
                        delay(SCROLL_THROTTLE_MS - elapsed)
                    } else if (lastScrollTime == 0L) {
                        delay(50.milliseconds)
                    }
                    val targetIndex = (patchProgress.logItems.size - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(targetIndex)
                    lastScrollTime = System.currentTimeMillis()
                }
            }
    }

    val landscape = isLandscape()
    // The app's own color marks what is being patched, on the bar and the log panel alike
    val appColor = packageName?.let { rememberAppColor(it) }

    // The same bar in both orientations: only where it hangs and its padding differ, so it is
    // written once rather than kept in step across two branches
    val actionBar: @Composable (Dp) -> Unit = { horizontalPadding ->
        PatcherBottomActionBar(
            horizontalPadding = horizontalPadding,
            showCancelButton = patcherSucceeded == null,
            showHomeButton = patcherSucceeded == true,
            showInstallButton = patcherSucceeded == true,
            showCopyLogsButton = true,
            onCancelClick = onCancelClick,
            onHomeClick = onHomeClick,
            onInstallClick = onInstallClick,
            onCopyLogsClick = {
                copyToClipboard(buildLogsText())
            }
        )
    }

    // Header and log panel carry the same data either way, side by side or stacked
    val header: @Composable () -> Unit = {
        ExpertProgressHeader(
            progress = progress,
            completed = completed,
            total = total,
            patchProgress = patchProgress,
            packageName = packageName,
            accentColor = appColor,
            patcherSucceeded = patcherSucceeded,
            showScreenLabel = queueHeader == null
        )
    }
    val logPanel: @Composable (Modifier) -> Unit = { panelModifier ->
        ExpertLogPanel(
            patchProgress = patchProgress,
            listState = listState,
            miniGameState = miniGameState,
            accentColor = appColor,
            modifier = panelModifier
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Content area
        if (landscape) {
            // Landscape: header + action bar left, log right
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.contentPadding),
                horizontalArrangement = Arrangement.spacedBy(windowSize.contentPadding),
                verticalAlignment = Alignment.Top
            ) {
                // Left column: header + action bar
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                ) {
                    // The usage graphs can outgrow a short window, so the header scrolls while
                    // the action bar below it stays put
                    val headerScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScrollFade(headerScrollState)
                            .verticalScroll(headerScrollState)
                    ) {
                        queueHeader?.invoke()

                        header()

                        Spacer(Modifier.height(12.dp))
                    }

                    // Action bar inside left column
                    actionBar(0.dp)
                }

                // Right column: log panel
                logPanel(
                    Modifier
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
                    // Only enough to clear the status bar: the screen starts at the very top and
                    // a queue header brings padding of its own
                    .padding(top = Defaults.ContentPaddingSmall),
                verticalArrangement = Arrangement.spacedBy(windowSize.itemSpacing)
            ) {
                queueHeader?.invoke()

                header()

                logPanel(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }

        // Portrait-only: action bar below content
        if (!landscape) {
            Spacer(Modifier.height(12.dp))

            actionBar(Defaults.ContentPadding)
        }
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
    patchProgress: PatchProgressSource,
    packageName: String? = null,
    accentColor: Color? = null,
    patcherSucceeded: Boolean? = null,
    showScreenLabel: Boolean = true
) {
    // Keyed on the run: a queue swaps in a new source without leaving composition
    val currentStep by remember(patchProgress) {
        derivedStateOf {
            patchProgress.steps.firstOrNull { it.state == State.RUNNING }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rememberWindowSize().itemSpacing)
    ) {
        // Screen name, live step and both counters read as one block, with the bar closing it
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showScreenLabel) {
                    // Names what is being patched, and falls back to the screen's own name for a
                    // package no source can put a label to
                    AppLabel(
                        packageName = packageName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        ),
                        defaultText = stringResource(R.string.patching_app)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = currentStep?.name,
                        transitionSpec = Animations.fadeCrossfade(300),
                        label = "expert_step_name",
                        modifier = Modifier.weight(1f)
                    ) { stepName ->
                        Text(
                            text = stepName ?: stringResource(R.string.patcher_success_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = usableAppAccent(accentColor) ?: MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (total > 0) {
                        // A finished run wears the same success tone as the success card
                        StatusBadge(
                            text = stringResource(R.string.patcher_patches_progress_format, completed, total),
                            tone = if (patcherSucceeded == true) SemanticTone.Success else SemanticTone.Primary
                        )
                    }

                    StatusBadge(
                        text = stringResource(R.string.patcher_percentage, (progress * 100).toInt()),
                        tone = SemanticTone.Primary
                    )
                }
            }

            // The simple mode's wave laid flat, so both modes show progress alike. [progress]
            // arrives already eased by [rememberDisplayedPatchProgress], so it is drawn as is
            WavyProgressBar(
                progress = { progress },
                accentColor = accentColor,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Heap, CPU and storage graphs
        PatchingUsageGraphs(
            patchProgress = patchProgress,
            compact = !isLandscape(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val LOG_PANEL_TAB_LOGS = 0
private const val LOG_PANEL_TAB_GAMES = 1

/**
 * Scrollable log panel backed directly by [PatchProgressSource.logs].
 * Tabs over it switch between the logs and the mini-game, by a tap or a swipe.
 */
@Composable
private fun ExpertLogPanel(
    modifier: Modifier = Modifier,
    patchProgress: PatchProgressSource,
    listState: LazyListState,
    miniGameState: MiniGameState,
    accentColor: Color? = null
) {
    val rawLogs = patchProgress.logs
    val logItems = patchProgress.logItems
    // Decoration only: the log's own colors keep telling warnings and errors apart
    val appAccent = usableAppAccent(accentColor)
    val dotColor = appAccent ?: MorpheBrandTeal
    val totalLogs = patchProgress.totalLogCount

    var activeTab by rememberSaveable { mutableIntStateOf(LOG_PANEL_TAB_LOGS) }
    LaunchedEffect(activeTab) {
        if (activeTab != LOG_PANEL_TAB_GAMES) miniGameState.pauseActiveGame()
    }
    // Waits behind its pause overlay while the manager is away, rather than playing on the
    // moment it is back, before the player has their eyes on it again
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { miniGameState.pauseActiveGame() }
    // Lines there were when the logs were last in view, so the tab can say the run moved on
    var seenLogCount by rememberSaveable { mutableIntStateOf(totalLogs) }
    LaunchedEffect(activeTab, totalLogs) {
        if (activeTab == LOG_PANEL_TAB_LOGS) seenLogCount = totalLogs
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Defaults.CardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = CardBorder.of(appAccentBorder(appAccent))
    ) {
        // Handed to the log cards, which a queue renders without the screen's own accent around them
        ProvideAccent(appAccent) {
            SegmentedTabs(
                options = listOf(
                    SegmentedTab(
                        label = stringResource(R.string.patcher_tab_logs),
                        icon = Icons.Outlined.Terminal,
                        badge = activeTab != LOG_PANEL_TAB_LOGS && totalLogs > seenLogCount
                    ),
                    SegmentedTab(
                        label = stringResource(R.string.patcher_tab_game),
                        icon = Icons.Outlined.SportsEsports
                    )
                ),
                selectedIndex = activeTab,
                onSelect = { activeTab = it },
                spacing = 0.dp,
                compact = true,
                fillHeight = true,
                selectorPadding = PaddingValues(
                    start = PatcherCardPadding,
                    top = PatcherCardPadding,
                    end = PatcherCardPadding
                ),
                contentColor = MaterialTheme.colorScheme.onSurface,
                // A game in play takes the drags over it, the picker has none of its own
                pageSwipeEnabled = { page -> page != LOG_PANEL_TAB_GAMES || !miniGameState.hasOpenGame },
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    LOG_PANEL_TAB_GAMES -> MiniGameContent(state = miniGameState)
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScrollFade(listState),
                            contentPadding = PaddingValues(vertical = PatcherCardMargin)
                        ) {
                            if (logItems.isEmpty()) {
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
                                            // Nothing is going to arrive for a run whose log died
                                            // with its process, so the live dot would be a lie
                                            if (!patchProgress.logsLost) {
                                                LiveIndicatorDot(color = dotColor, size = 10.dp)
                                            }
                                            Text(
                                                text = stringResource(
                                                    if (patchProgress.logsLost) R.string.patcher_logs_lost
                                                    else R.string.patcher_logs_waiting
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    .copy(alpha = 0.45f),
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
                        }
                    }
                }
            }
        }
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
    // The start card wears the app's color on a neutral veil, the success one stays green
    val accentColor = when (variant) {
        CardVariant.Start   -> LocalAccent.current ?: MaterialTheme.colorScheme.primary
        CardVariant.Success -> SemanticTone.Success.accent
    }
    val bgColor = when (variant) {
        CardVariant.Start   -> neutralVeil()
        CardVariant.Success -> SemanticTone.Success.accent.copy(alpha = 0.10f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PatcherCardPadding, vertical = PatcherCardMargin),
        shape = RoundedCornerShape(Defaults.CompactCornerRadius),
        color = bgColor,
        tonalElevation = 0.dp,
        border = CardBorder.tinted(accentColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PatcherCardPadding),
            // Tight, because these cards carry a lot of short fields and are read at a glance
            verticalArrangement = Arrangement.spacedBy(7.dp)
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
    PatcherInfoCard(
        title = stringResource(R.string.patcher_card_started),
        variant = CardVariant.Start
    ) {
        BannerFieldCell(
            label = stringResource(R.string.patcher_field_package),
            value = item.packageName,
            modifier = Modifier.weight(1f)
        )

        BannerFieldRow {
            BannerFieldCell(
                label = stringResource(R.string.version),
                value = item.version,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(R.string.home_app_info_apk_size),
                value = item.apkSize,
                modifier = Modifier.weight(1f))
        }

        BannerFieldRow {
            BannerFieldCell(
                label = stringResource(R.string.patches),
                value = item.patchCount.toString(),
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(R.string.patcher_field_split),
                value = stringResource(if (item.isSplit) R.string.yes else R.string.no),
                modifier = Modifier.weight(1f),
                valueColor = if (item.isSplit) MaterialTheme.colorScheme.tertiary else null
            )
        }

        // A cell per source, so a version always sits under the name it belongs to. Laid out
        // two per row like everything else, and an odd one keeps it's half rather than stretching
        if (item.sources.isEmpty()) {
            BannerFieldCell(
                label = stringResource(R.string.patcher_field_source),
                value = stringResource(R.string.patcher_field_value_unknown),
                modifier = Modifier.weight(1f)
            )
        } else {
            item.sources.chunked(2).forEach { pair ->
                BannerFieldRow {
                    pair.forEach { source ->
                        BannerFieldCell(
                            label = source.name,
                            value = source.version ?: "?",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        HorizontalDivider(
            color = (LocalAccent.current ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.1f),
            thickness = 1.dp
        )

        BannerFieldRow {
            BannerFieldCell(
                label = stringResource(R.string.patcher_field_manager),
                value = item.managerVersion ?: "?",
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(R.string.patcher_field_patcher),
                value = item.patcherVersion ?: "?",
                modifier = Modifier.weight(1f))
        }

        BannerFieldRow {
            // The heap limit only exists for the process runtime, so it rides along with the
            // runtime name rather than taking a cell that is empty half the time
            BannerFieldCell(
                label = stringResource(R.string.patcher_field_runtime),
                value = item.runtimeMemoryLimitMb
                    ?.let { "Process $it" }
                    ?: "Coroutine",
                modifier = Modifier.weight(1f),
                valueColor = item.runtimeMemoryLimitMb?.let { LocalAccent.current ?: MaterialTheme.colorScheme.primary }
            )
            item.stripsNativeLibs?.let { strips ->
                BannerFieldCell(
                    label = stringResource(R.string.patcher_field_libraries),
                    value = stringResource(
                        if (strips) R.string.patcher_field_value_stripped
                        else R.string.patcher_field_value_kept
                    ),
                    modifier = Modifier.weight(1f),
                    valueColor = if (strips) MaterialTheme.colorScheme.tertiary else null
                )
            }
        }

        // Device environment only shown when data is available
        if (item.androidVersion != null || item.ramTotal != null || item.deviceManufacturer != null) {
            BannerFieldRow {
                item.androidVersion?.let {
                    BannerFieldCell(
                        label = stringResource(R.string.patcher_field_android),
                        value = it,
                        modifier = Modifier.weight(1f))
                }
                if (item.deviceManufacturer != null || item.deviceModel != null) {
                    val deviceLabel = remember(item.deviceManufacturer, item.deviceModel) {
                        listOfNotNull(item.deviceManufacturer, item.deviceModel).joinToString(" ")
                    }
                    BannerFieldCell(
                        label = stringResource(R.string.patcher_field_device),
                        value = deviceLabel,
                        modifier = Modifier.weight(1f))
                }
            }

            if (item.ramTotal != null) {
                BannerFieldRow {
                    BannerFieldCell(
                        label = stringResource(R.string.patcher_field_memory),
                        value = "${item.ramAvailable ?: "?"} / ${item.ramTotal}",
                        modifier = Modifier.weight(1f)
                    )
                    if (item.storageTotal != null) {
                        BannerFieldCell(
                            label = stringResource(R.string.patcher_field_storage),
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
    PatcherInfoCard(
        title = stringResource(R.string.patcher_card_succeeded),
        variant = CardVariant.Success,
        badge = "✓"
    ) {
        BannerFieldRow {
            BannerFieldCell(
                label = stringResource(R.string.patcher_field_output_size),
                value = item.outputSize,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(R.string.patcher_field_time),
                value = item.elapsedSec,
                modifier = Modifier.weight(1f))
        }

        if (item.processHeapAverageMb != null) {
            BannerFieldRow {
                BannerFieldCell(
                    label = stringResource(R.string.patcher_field_memory_average),
                    value = item.processHeapAverageMb,
                    modifier = Modifier.weight(1f))
                BannerFieldCell(
                    label = stringResource(R.string.patcher_field_memory_max),
                    value = item.processHeapMaxMb ?: "?",
                    modifier = Modifier.weight(1f))
            }
        }

        if (item.ioPeakRate != null) {
            BannerFieldRow {
                BannerFieldCell(
                    label = stringResource(R.string.patcher_field_io_peak),
                    value = item.ioPeakRate,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Row of two banner cells. Every field pair in the cards is laid out the same way, so the
 * spacing lives here instead of being restated at each pair.
 */
@Composable
private fun BannerFieldRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

/**
 * Label+value field used inside banner cards.
 */
@Composable
private fun BannerFieldCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color? = null,
    maxLines: Int = 1
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
            maxLines = maxLines,
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
            .padding(horizontal = PatcherCardPadding, vertical = PatcherCardMargin),
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
        rowBg   = SemanticTone.Warning.container.copy(alpha = 0.12f),
        badgeBg = SemanticTone.Warning.container.copy(alpha = 0.5f),
        text    = SemanticTone.Warning.accent
    )
    // The most common level, so its badge stays neutral rather than coloring the whole log
    LogLevel.INFO -> LogEntryColors(
        rowBg   = Color.Unspecified,
        badgeBg = neutralVeil(),
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

/** Pulsing dot of the empty log panel, in the color of the app being patched, while logs are awaited. */
@Composable
private fun LiveIndicatorDot(color: Color, size: Dp) {
    val alpha = rememberInfiniteTransition(label = "live_dot").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_alpha"
    )

    // The pulse is read while drawing rather than while composing, so the dot repaints
    // without recomposing itself on every frame of the patch run
    Box(
        modifier = Modifier
            .size(size)
            .drawBehind {
                drawCircle(color = color.copy(alpha = alpha.value))
            }
    )
}
