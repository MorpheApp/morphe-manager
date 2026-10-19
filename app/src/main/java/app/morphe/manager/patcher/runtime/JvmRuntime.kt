/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.annotation.RequiresApi
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.PatchRequest
import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.host.HostEvent
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.util.bytesToMebibytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Patches in the JVM the app embeds, run by [PatcherService] in a process of its own. The JVM
 * keeps its heap in native memory, so a run is limited by what the device can spare rather than
 * by the heap ART gives a process.
 */
class JvmRuntime(
    context: Context,
    private val fs: Filesystem,
    patchBundlesRepo: PatchBundleRepository,
    private val jvms: PatcherJvms
) : Runtime(context, fs, patchBundlesRepo) {
    override suspend fun patch(request: PatchRequest, logger: Logger, listener: PatchingListener) {
        val requestFile = fs.tempDir.resolve("patch-request.json")
        try {
            withContext(Dispatchers.IO) {
                requestFile.writeText(Json.encodeToString(PatchRequest.serializer(), request))
            }

            jvms.take(request.bundles.map { it.patchesFile }).use { jvm ->
                if (jvm.debugOptions.isNotEmpty()) logger.info("Debug JVM options: ${jvm.debugOptions.joinToString(" ")}")

                val (events, eventsSink) = ParcelFileDescriptor.createPipe()
                // Closed here once handed over, or the pipe would stay open after the process is gone
                eventsSink.use { jvm.run(requestFile.path, it) }

                var end: RunEnd? = null
                try {
                    end = relay(events, logger, listener, jvm.heapLimitMb)
                } finally {
                    // A run cut short leaves the JVM running, and nothing but the end of its process stops it
                    if (end != RunEnd.FINISHED) Process.killProcess(jvm.pid)
                }
                if (end == RunEnd.FINISHED) return

                val reason = exitReason(jvm.pid)
                if (jvm.errorFile.exists()) logger.error("Patcher JVM error report:\n${jvm.errorFile.readText()}")
                throw if (end == RunEnd.ENDED_RUNNING) PatcherKilledException(reason)
                else JvmUnavailableException("The patcher JVM did not start: $reason")
            }
        } finally {
            requestFile.delete()
        }
    }

    private enum class RunEnd { FINISHED, ENDED_BEFORE_START, ENDED_RUNNING }

    /**
     * Hands the events of the run over as they arrive, until the run reports its end or the pipe
     * closes without it. Throws what the run failed on.
     */
    private suspend fun relay(
        events: ParcelFileDescriptor,
        logger: Logger,
        listener: PatchingListener,
        heapLimitMb: Int
    ): RunEnd {
        val channel = Channel<HostEvent>(Channel.UNLIMITED)
        // A thread of its own, as a blocking read cannot be canceled: it ends once the process does
        thread(isDaemon = true, name = "PatcherEvents") {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(events).bufferedReader().useLines { lines ->
                    lines.forEach { channel.trySend(HostEvent.decode(it)) }
                }
                channel.close()
            } catch (e: Exception) {
                channel.close(e)
            }
        }

        var started = false
        for (event in channel) when (event) {
            HostEvent.Started -> started = true
            is HostEvent.Log -> logger.log(event.level, event.message)
            HostEvent.StepCompleted -> listener.stepCompleted()
            is HostEvent.SplitPreparation -> listener.splitPreparation(event.event)
            HostEvent.PatchesStarted -> listener.patchesStarted()
            is HostEvent.PatchSucceeded -> listener.patchSucceeded(event.name)
            is HostEvent.PatchFailed -> listener.patchFailed(event.name, event.stackTrace)
            is HostEvent.PatchesFinished -> listener.patchesFinished(event.count)
            is HostEvent.Finished -> {
                val failure = event.failure ?: return RunEnd.FINISHED
                throw if (failure.outOfMemory) HeapExhaustedException(heapLimitMb, failure.stackTrace)
                else RemoteFailureException(failure.stackTrace)
            }
        }
        return if (started) RunEnd.ENDED_RUNNING else RunEnd.ENDED_BEFORE_START
    }

    /**
     * What the system recorded about the end of [pid], in words for the log. Asked a few times, as
     * the record is only written once the system has dealt with the death, which can lag behind
     * the pipe closing.
     */
    private suspend fun exitReason(pid: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return NO_EXIT_RECORD

        val activityManager = context.getSystemService(ActivityManager::class.java)
        return withTimeoutOrNull(EXIT_RECORD_TIMEOUT) {
            var info: ApplicationExitInfo?
            do {
                info = activityManager.getHistoricalProcessExitReasons(context.packageName, pid, 1).firstOrNull()
                if (info == null) delay(EXIT_RECORD_POLL)
            } while (info == null)
            info.describe()
        } ?: NO_EXIT_RECORD
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.describe(): String {
        val cause = when (reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
            ApplicationExitInfo.REASON_SIGNALED -> "killed by signal $status"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for excessive resource usage"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped by the user"
            ApplicationExitInfo.REASON_EXIT_SELF -> "exited with $status"
            else -> "ended for reason $reason"
        }
        // Both are reported in kilobytes
        val memory = "pss=${bytesToMebibytes(pss * 1024)}MB rss=${bytesToMebibytes(rss * 1024)}MB"
        return listOfNotNull(cause, description, memory).joinToString(", ")
    }

    companion object {
        private const val NO_EXIT_RECORD = "no exit record"

        private val EXIT_RECORD_TIMEOUT = 2.seconds
        private val EXIT_RECORD_POLL = 200.milliseconds

        // The patcher keeps several hundred megabytes alive for a large app. The JVM only takes
        // what it uses, so the limit is a ceiling that keeps a runaway patch from taking the device
        private const val HEAP_LIMIT_MINIMUM_MB = 1024
        private const val HEAP_LIMIT_MAXIMUM_MB = 3072

        // The heap is reserved in one piece, which a 32 bit process seldom finds much more of in
        // its address space. A reservation that fails leaves the run to the app's own process
        private const val HEAP_LIMIT_32_BIT_MB = 1024

        /** The heap the patcher JVM gets on this device: a third of its RAM, within bounds. */
        fun heapLimitMb(context: Context): Int {
            val memoryInfo = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
            val limit = (bytesToMebibytes(memoryInfo.totalMem) / 3).toInt()
                .coerceIn(HEAP_LIMIT_MINIMUM_MB, HEAP_LIMIT_MAXIMUM_MB)
            return if (Process.is64Bit()) limit else limit.coerceAtMost(HEAP_LIMIT_32_BIT_MB)
        }
    }
}
