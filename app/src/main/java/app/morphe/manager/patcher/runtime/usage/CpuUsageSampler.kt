package app.morphe.manager.patcher.runtime.usage

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Per-core CPU load.
 *
 * Read from /proc/stat where the device allows it, which is the load of the whole system. Recent
 * Android versions hide that file from apps, and there the patcher's own threads are attributed
 * to the core each of them last ran on: the numbers then only cover this process, which is what
 * the run is spending anyway.
 */
internal class CpuUsageSampler {
    private val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val clockTicksPerSecond =
        Os.sysconf(OsConstants._SC_CLK_TCK).takeIf { it > 0L } ?: DEFAULT_CLOCK_TICKS

    private val previousTotal = LongArray(coreCount)
    private val previousIdle = LongArray(coreCount)
    private var hasSystemTicks = false
    private var systemTicksReadable = true

    private var previousThreadTicks = emptyMap<Int, Long>()
    private var previousUptimeMs = 0L

    /**
     * Load of every core in percent. Empty until a second reading gives the first one something
     * to compare against.
     */
    fun sample(): List<Int> {
        if (systemTicksReadable) {
            sampleSystemCores()?.let { return it }
            // A policy that hides /proc/stat keeps hiding it, so stop paying for the attempt
            systemTicksReadable = false
        }

        return sampleOwnThreads()
    }

    /** Null when /proc/stat cannot be read, which is what picks the per-thread path instead. */
    private fun sampleSystemCores(): List<Int>? {
        val total = LongArray(coreCount)
        val idle = LongArray(coreCount)
        if (!readSystemTicks(total, idle)) return null

        val loads = List(coreCount) { core ->
            val totalDelta = total[core] - previousTotal[core]
            val idleDelta = idle[core] - previousIdle[core]

            if (totalDelta <= 0L) {
                0
            } else {
                (((totalDelta - idleDelta) * 100) / totalDelta).toInt().coerceIn(0, 100)
            }
        }

        total.copyInto(previousTotal)
        idle.copyInto(previousIdle)

        val hadTicks = hasSystemTicks
        hasSystemTicks = true

        return if (hadTicks) loads else emptyList()
    }

    /**
     * Fills [total] and [idle] with the tick counters of every online core, keeping each core at
     * its own index so a core going offline leaves a gap rather than shifting the others.
     */
    private fun readSystemTicks(total: LongArray, idle: LongArray): Boolean {
        var parsedAny = false

        try {
            File(PROC_STAT).useLines { lines ->
                for (line in lines) {
                    // Core lines come first, and what follows them is an interrupt counter per
                    // IRQ that costs more to read than everything above it
                    if (!line.startsWith(CPU_LINE_PREFIX)) break

                    val fields = line.split(' ').filter { it.isNotEmpty() }
                    // The aggregated "cpu" line carries no index and is covered by the per-core ones
                    val core = fields.first().removePrefix(CPU_LINE_PREFIX).toIntOrNull() ?: continue
                    if (core >= total.size) continue

                    val ticks = fields.drop(1).mapNotNull(String::toLongOrNull)
                    if (ticks.size <= IOWAIT_FIELD) continue

                    total[core] = ticks.sum()
                    // Waiting on storage is idle as far as the core is concerned
                    idle[core] = ticks[IDLE_FIELD] + ticks[IOWAIT_FIELD]
                    parsedAny = true
                }
            }
        } catch (_: Exception) {
            return false
        }

        return parsedAny
    }

    /**
     * Load each core carried for this process, taken from the CPU time of its threads.
     *
     * A thread reports the core it last ran on rather than everywhere it has been, so its time is
     * booked there in full. Over a poll that is an approximation, but a thread that keeps a core
     * busy is the one the scheduler keeps leaving on it.
     */
    private fun sampleOwnThreads(): List<Int> {
        val tasks = File(PROC_SELF_TASK).listFiles() ?: return emptyList()

        val uptimeMs = SystemClock.elapsedRealtime()
        val elapsed = uptimeMs - previousUptimeMs
        val previous = previousThreadTicks
        val current = HashMap<Int, Long>(tasks.size)
        val coreTicks = LongArray(coreCount)

        for (task in tasks) {
            val tid = task.name.toIntOrNull() ?: continue
            // Threads come and go while patching, so one disappearing mid-read is expected
            val stat = try {
                File(task, THREAD_STAT_FILE).readText()
            } catch (_: Exception) {
                continue
            }

            // The thread name sits in parentheses and can contain spaces of its own
            val fields = stat.substringAfterLast(')').trim().split(' ')
            if (fields.size <= PROCESSOR_FIELD) continue

            val userTicks = fields[UTIME_FIELD].toLongOrNull() ?: continue
            val systemTicks = fields[STIME_FIELD].toLongOrNull() ?: continue
            val ticks = userTicks + systemTicks
            current[tid] = ticks

            val core = fields[PROCESSOR_FIELD].toIntOrNull() ?: continue
            if (core >= coreCount) continue

            coreTicks[core] += ticks - (previous[tid] ?: ticks)
        }

        previousThreadTicks = current
        previousUptimeMs = uptimeMs

        if (previous.isEmpty() || elapsed <= 0L) return emptyList()

        return coreTicks.map { ticks ->
            ((ticks * MILLIS_PER_SECOND * 100) / (clockTicksPerSecond * elapsed))
                .toInt()
                .coerceIn(0, 100)
        }
    }

    private companion object {
        const val PROC_STAT = "/proc/stat"
        const val CPU_LINE_PREFIX = "cpu"
        const val PROC_SELF_TASK = "/proc/self/task"
        const val THREAD_STAT_FILE = "stat"

        const val MILLIS_PER_SECOND = 1000L
        const val DEFAULT_CLOCK_TICKS = 100L

        // Field positions of a /proc/stat core line, as documented in proc(5)
        const val IDLE_FIELD = 3
        const val IOWAIT_FIELD = 4

        // Field positions of a thread's stat line, counted from the one after its name
        const val UTIME_FIELD = 11
        const val STIME_FIELD = 12
        const val PROCESSOR_FIELD = 36
    }
}
