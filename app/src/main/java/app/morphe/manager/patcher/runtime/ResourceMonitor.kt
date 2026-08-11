package app.morphe.manager.patcher.runtime

import android.os.Process
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.runtime.usage.CpuUsageSampler
import app.morphe.manager.patcher.runtime.usage.IoUsageSampler
import java.lang.Runtime
import kotlin.also
import kotlin.collections.buildList
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.math.max


/**
 * Polls the resources the patcher run consumes and reports them through the logger, which is the
 * only channel back to the app when patching happens in a process of its own.
 */
object ResourceMonitor {
    const val LOG_MEMORY_PREFIX_DONE = "Heap after patching:"
    const val LOG_MEMORY_PREFIX_CURRENT = "Heap: current="
    const val LOG_MEMORY_FIELD_AVERAGE = "average"
    const val LOG_MEMORY_FIELD_MAX = "max"

    const val LOG_USAGE_PREFIX_CURRENT = "Usage:"
    const val LOG_USAGE_FIELD_CPU = "cpu"
    const val LOG_USAGE_FIELD_IO_READ = "ioRead"
    const val LOG_USAGE_FIELD_IO_WRITE = "ioWrite"

    private const val MONITOR_INTERVAL = 2000L

    @Volatile
    private var polling = false

    @Volatile
    private var pollingThread: Thread? = null

    @Volatile
    private var memoryPollSamples = 0

    @Volatile
    private var memoryUsedAverage = 0L

    @Volatile
    private var memoryUsedMax = 0L

    fun startPolling(logger: Logger) {
        // A queued run starts the monitor again while the previous thread may still be sleeping
        // off its last interval, and two of them would report over each other forever
        pollingThread?.interrupt()

        memoryPollSamples = 0
        memoryUsedAverage = 0
        memoryUsedMax = 0
        polling = true

        pollingThread = Thread {
            // Reporting on the run must never take a slice the run itself could have used
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

            val rt = Runtime.getRuntime()
            val cpuSampler = CpuUsageSampler()
            val ioSampler = IoUsageSampler()

            while (polling) {
                val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                memoryUsedMax = max(memoryUsedMax, used)

                memoryUsedAverage =
                    (memoryUsedAverage * memoryPollSamples + used) / ++memoryPollSamples

                logger.info(
                    "$LOG_MEMORY_PREFIX_CURRENT${used}MB " +
                            "average=${memoryUsedAverage}MB " +
                            "max=${memoryUsedMax}MB"
                )

                logUsage(logger, cpuSampler, ioSampler)

                try {
                    Thread.sleep(MONITOR_INTERVAL)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.also { it.start() }
    }

    fun stopPolling(logger: Logger) {
        polling = false
        // Ends the last interval now instead of leaving the thread asleep on it
        pollingThread?.interrupt()
        pollingThread = null

        logger.info(
            "$LOG_MEMORY_PREFIX_DONE $LOG_MEMORY_FIELD_AVERAGE=${memoryUsedAverage}MB " +
                    "$LOG_MEMORY_FIELD_MAX=${memoryUsedMax}MB"
        )
    }

    /**
     * Reports whatever this device lets the process see. A metric the kernel does not expose is
     * left out of the line entirely, so the graph for it stays absent instead of reading zero.
     */
    private fun logUsage(logger: Logger, cpuSampler: CpuUsageSampler, ioSampler: IoUsageSampler) {
        val coreLoads = cpuSampler.sample()
        val io = ioSampler.sample()

        val fields = buildList {
            if (coreLoads.isNotEmpty()) {
                add("$LOG_USAGE_FIELD_CPU=${coreLoads.joinToString(",")}")
            }
            if (io != null) {
                add("$LOG_USAGE_FIELD_IO_READ=${io.readKbPerSec}")
                add("$LOG_USAGE_FIELD_IO_WRITE=${io.writeKbPerSec}")
            }
        }

        if (fields.isNotEmpty()) {
            logger.info("$LOG_USAGE_PREFIX_CURRENT ${fields.joinToString(" ")}")
        }
    }
}
