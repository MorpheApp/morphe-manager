package app.morphe.manager.patcher.runtime.usage

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Per-core CPU load.
 *
 * Read from /proc/stat where the device allows it, which is the load of the whole system. Recent
 * Android versions hide that file from apps, and the idle counters every core keeps in sysfs give
 * the same load the other way around: whatever a core did not spend idle, it spent working. Where
 * even those are missing the patcher's own threads are attributed to the core each of them last
 * ran on, and the numbers then only cover this process, which is what the run is spending anyway.
 */
internal class CpuUsageSampler {
    private val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val clockTicksPerSecond =
        Os.sysconf(OsConstants._SC_CLK_TCK).takeIf { it > 0L } ?: DEFAULT_CLOCK_TICKS

    private val previousTotal = LongArray(coreCount)
    private val previousIdle = LongArray(coreCount)
    private var hasSystemTicks = false
    private var systemTicksReadable = true

    private var idleStateFiles: List<List<File>>? = null
    private val previousIdleMicros = LongArray(coreCount)
    private var previousIdleUptimeMs = 0L
    private var hasIdleMicros = false
    private var idleTimesReadable = true

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

        if (idleTimesReadable) {
            sampleIdleTimes()?.let { return it }
            idleTimesReadable = false
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
     * Load of every core taken from how long it stayed idle, which the kernel counts per core in
     * microseconds and hands out where /proc/stat is off limits. Busy time is what the interval
     * leaves over, so this still covers the whole system rather than only this process.
     *
     * Null when the device keeps its idle counters to itself, which is what picks the per-thread
     * path instead.
     */
    private fun sampleIdleTimes(): List<Int>? {
        val stateFiles = idleStateFiles
            ?: findIdleStateFiles()?.also { idleStateFiles = it }
            ?: return null

        val uptimeMs = SystemClock.uptimeMillis()
        // Both clocks stand still while the device sleeps, so an interval never counts time no
        // core was there to spend
        val elapsedMicros = (uptimeMs - previousIdleUptimeMs) * MICROS_PER_MILLISECOND

        var readAny = false
        val idleMicros = LongArray(coreCount)
        for (core in 0 until coreCount) {
            val micros = readIdleMicros(stateFiles[core])
            idleMicros[core] = micros ?: CORE_UNREADABLE
            readAny = readAny || micros != null
        }

        if (!readAny) return null

        val loads = List(coreCount) { core ->
            val previous = previousIdleMicros[core]
            val current = idleMicros[core]
            val unreadable = previous == CORE_UNREADABLE || current == CORE_UNREADABLE

            // A core the scheduler parked counts no idle time either, and would read as fully
            // busy for as long as it is asleep
            if (unreadable || elapsedMicros <= 0L || !isCoreOnline(core)) {
                0
            } else {
                (100 - ((current - previous) * 100) / elapsedMicros).toInt().coerceIn(0, 100)
            }
        }

        idleMicros.copyInto(previousIdleMicros)
        previousIdleUptimeMs = uptimeMs

        val hadMicros = hasIdleMicros
        hasIdleMicros = true

        return if (hadMicros) loads else emptyList()
    }

    /**
     * Time a core spent across all of its idle states. Null once a state file is gone, which is a
     * core that went offline after the set was found and comes back with it.
     */
    private fun readIdleMicros(stateFiles: List<File>): Long? {
        var micros = 0L
        for (file in stateFiles) {
            micros += readCounter(file) ?: return null
        }

        return micros
    }

    /**
     * The idle time file of every state of every core, which is what the per-core sum is read
     * from. Null unless the whole set is there, since a core missing from it would report the
     * busiest reading there is.
     */
    private fun findIdleStateFiles(): List<List<File>>? {
        val cores = List(coreCount) { core ->
            File(coreDir(core), CPUIDLE_DIR).listFiles()
                ?.filter { it.name.startsWith(IDLE_STATE_PREFIX) }
                ?.map { File(it, IDLE_TIME_FILE) }
                ?.filter(File::canRead)
                .orEmpty()
        }

        return cores.takeIf { it.all(List<File>::isNotEmpty) }
    }

    /**
     * Cores go offline and come back while patching. A core that cannot be asked, the first one
     * usually, is one that has no switch to be turned off by.
     */
    private fun isCoreOnline(core: Int) = readCounter(File(coreDir(core), CPU_ONLINE_FILE)) != 0L

    private fun coreDir(core: Int) = File(CPU_DIR_PREFIX + core)

    private fun readCounter(file: File): Long? = try {
        file.readText().trim().toLongOrNull()
    } catch (_: Exception) {
        null
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

        const val CPU_DIR_PREFIX = "/sys/devices/system/cpu/cpu"
        const val CPU_ONLINE_FILE = "online"
        const val CPUIDLE_DIR = "cpuidle"
        const val IDLE_STATE_PREFIX = "state"
        const val IDLE_TIME_FILE = "time"
        const val CORE_UNREADABLE = -1L

        const val MILLIS_PER_SECOND = 1000L
        const val MICROS_PER_MILLISECOND = 1000L
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
