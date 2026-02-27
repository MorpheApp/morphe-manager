package app.morphe.manager.patcher.runtime

import app.morphe.manager.patcher.logger.Logger
import java.lang.Runtime
import kotlin.math.max

object MemoryMonitor {

    const val MEMORY_LOG_PREFIX = "Heap after patching:"
    const val MEMORY_LOG_FIELD_AVERAGE = "average"
    const val MEMORY_LOG_FIELD_MAX = "max"

    // Change to true to log all memory checks.
    private const val MEMORY_MONITOR_LOG_UPDATES = true

    private const val MEMORY_MONITOR_INTERVAL = 1000L

    @Volatile
    private var memoryPollUsage = false

    @Volatile
    var memoryPollSamples = 0

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

                if (MEMORY_MONITOR_LOG_UPDATES) {
                    logger.info(
                        "Heap: current=${used}MB " +
                                "average=${memoryUsedAverage}MB " +
                                "max=${memoryUsedMax}MB"
                    )
                }

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
            "$MEMORY_LOG_PREFIX $MEMORY_LOG_FIELD_AVERAGE=${memoryUsedAverage}MB " +
                    "$MEMORY_LOG_FIELD_MAX=${memoryUsedMax}MB"
        )
    }
}
