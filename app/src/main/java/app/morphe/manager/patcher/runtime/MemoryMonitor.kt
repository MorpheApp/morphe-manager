package app.morphe.manager.patcher.runtime

import app.morphe.manager.patcher.logger.Logger
import java.lang.Runtime
import kotlin.math.max


object MemoryMonitor {
    const val LOG_MEMORY_PREFIX_DONE = "Heap after patching:"
    const val LOG_MEMORY_PREFIX_CURRENT = "Heap: current="
    const val LOG_MEMORY_FIELD_AVERAGE = "average"
    const val LOG_MEMORY_FIELD_MAX = "max"

    private const val MEMORY_MONITOR_INTERVAL = 2000L

    @Volatile
    private var memoryPollUsage = false

    @Volatile
    private var memoryPollSamples = 0

    @Volatile
    var memoryUsedAverage = 0L

    @Volatile
    var memoryUsedMax = 0L

    fun startMemoryPolling(logger: Logger) {
        memoryPollSamples = 0
        memoryUsedAverage = 0
        memoryUsedMax = 0
        memoryPollUsage = true

        Thread {
            val rt = Runtime.getRuntime()

            while (memoryPollUsage) {
                val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                memoryUsedMax = max(memoryUsedMax, used)

                memoryUsedAverage =
                    (memoryUsedAverage * memoryPollSamples + used) / ++memoryPollSamples

                logger.info(
                    "$LOG_MEMORY_PREFIX_CURRENT${used}MB " +
                            "average=${memoryUsedAverage}MB " +
                            "max=${memoryUsedMax}MB"
                )

                try {
                    Thread.sleep(MEMORY_MONITOR_INTERVAL)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.start()
    }

    fun stopMemoryPolling(logger: Logger) {
        memoryPollUsage = false
        logger.info(
            "$LOG_MEMORY_PREFIX_DONE $LOG_MEMORY_FIELD_AVERAGE=${memoryUsedAverage}MB " +
                    "$LOG_MEMORY_FIELD_MAX=${memoryUsedMax}MB"
        )
    }
}
