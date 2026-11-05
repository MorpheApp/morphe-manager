/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.host

import app.morphe.manager.patcher.PatchPipeline
import app.morphe.manager.patcher.PatchRequest
import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.runtime.heapLimitMebibytes
import app.morphe.manager.patcher.split.SplitPreparationEvent
import app.morphe.manager.util.bytesToMebibytes
import app.morphe.manager.util.toLogString
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Entry point of the JVM the app embeds for patching, see [serveRun].
 *
 * @param args The read end of a pipe the app writes [HostCommand]s to, one per line, then the write
 *             end of a pipe the app reads [HostEvent]s from.
 */
fun main(args: Array<String>) {
    // Patches tell Android from a desktop host by java.vendor, which ART sets to this and the
    // embedded OpenJDK build leaves as N/A
    System.setProperty("java.vendor", "The Android Project")
    val (commandsFile, eventsFile) = args
    val collectors = ManagementFactory.getGarbageCollectorMXBeans().toTypedArray()
    val gcClock = GcClock {
        var total = 0L
        for (collector in collectors) total += collector.collectionTime
        total
    }
    serveRun(commandsFile, eventsFile, gcClock) { loadPatchesFromJar(setOf(it)) }
}

/**
 * The time the VM spent collecting garbage so far, in milliseconds. A primitive rather than a
 * lambda returning Long, which would box: it is read when the heap has no room left for that.
 */
fun interface GcClock {
    fun collectedMillis(): Long
}

/**
 * Serves the single run of a process of its own, in whichever VM it runs, and ends the process
 * with it. The host is started before the run is known, so it can load ahead while the user still
 * picks what to patch.
 *
 * @param commandsFile What the app writes [HostCommand]s to, one per line.
 * @param eventsFile What the app reads [HostEvent]s from.
 * @param gcClock The time the VM spent collecting garbage so far, or null if it does not tell.
 * @param loadPatches Reads the patches of a bundle, which is the one step that depends on the VM.
 */
fun serveRun(
    commandsFile: String,
    eventsFile: String,
    gcClock: GcClock?,
    loadPatches: (File) -> Collection<Patch>
): Nothing {
    val code = try {
        HostEvents(FileOutputStream(eventsFile)).use { events ->
            // Patches print what they did, which on Android goes to /dev/null or at best logcat.
            // The log is the one place the user sees it
            System.setOut(events.printer())
            System.setErr(events.printer())

            var preloaded = emptyMap<String, PreloadedBundle>()
            File(commandsFile).bufferedReader().useLines { lines ->
                for (line in lines) when (val command = HostCommand.decode(line)) {
                    is HostCommand.Preload ->
                        preloaded = command.bundles.associateWith { PreloadedBundle(File(it), loadPatches) }

                    is HostCommand.Run -> {
                        run(command, preloaded, gcClock, loadPatches, events)
                        break
                    }
                }
            }
        }
        0
    } catch (_: IOException) {
        // The app stopped reading, so there is nobody left to report to
        1
    }

    // Threads the patches left running must not keep the process, which exists for this run alone
    exitProcess(code)
}

private fun run(
    command: HostCommand.Run,
    preloaded: Map<String, PreloadedBundle>,
    gcClock: GcClock?,
    loadPatches: (File) -> Collection<Patch>,
    events: HostEvents
) {
    events.logger.info(vmDescription())
    events.watchForStalls()
    gcClock?.let(events::watchForGcOverhead)

    val failure = runCatching {
        val request = Json.decodeFromString(PatchRequest.serializer(), File(command.requestFile).readText())
        runBlocking {
            PatchPipeline(request, events.logger, events.listener) { bundle ->
                preloaded[bundle.patchesFile]?.take(events.logger)
                    ?: loadLogged(File(bundle.patchesFile), loadPatches, events.logger)
            }.run()
        }
    }.exceptionOrNull()

    events.send(HostEvent.Finished(failure?.let(HostEvent::Failure)))
}

/** The VM the run happens in as it describes itself, which tells a JIT from an interpreter and one port from another. */
private fun vmDescription(): String {
    fun property(name: String) = System.getProperty(name) ?: "?"
    return "Host VM: ${property("java.vm.name")} ${property("java.vm.version")} (${property("java.vm.info")}) " +
            "arch=${property("os.arch")} cores=${Runtime.getRuntime().availableProcessors()}"
}

private fun loadLogged(file: File, loadPatches: (File) -> Collection<Patch>, logger: Logger): Collection<Patch> {
    val started = TimeSource.Monotonic.markNow()
    return loadPatches(file).also {
        logger.info("Loaded ${it.size} patches of ${file.name} in ${started.elapsedNow().toLogString()}")
    }
}

/** Hands each line written to it over to [onLine], without its line break. */
private class LineOutputStream(private val onLine: (String) -> Unit) : OutputStream() {
    private val line = ByteArrayOutputStream()

    @Synchronized
    override fun write(b: Int) {
        if (b == '\n'.code) {
            onLine(line.toString(Charsets.UTF_8.name()).removeSuffix("\r"))
            line.reset()
        } else {
            line.write(b)
        }
    }
}

/**
 * The patches of a bundle, loaded on a thread of their own from the moment they are asked for. The
 * file may be replaced by an update before the run, so they are only used if it is unchanged.
 */
private class PreloadedBundle(private val file: File, loadPatches: (File) -> Collection<Patch>) {
    private val stamp = stampOf(file)
    private val started = TimeSource.Monotonic.markNow()

    @Volatile
    private var loadedIn = 0L

    private val patches = FutureTask {
        loadPatches(file).also { loadedIn = started.elapsedNow().inWholeMilliseconds }
    }.also {
        thread(isDaemon = true, name = "PreloadPatches") { it.run() }
    }

    /** The loaded patches, or null if the file changed or loading failed, for them to be loaded again. */
    fun take(logger: Logger): Collection<Patch>? {
        if (stampOf(file) != stamp) {
            logger.info("${file.name} changed since it was loaded ahead, loading it again")
            return null
        }
        // A run that waits here for long is held up by the load itself rather than by what follows
        if (!patches.isDone) {
            logger.info("Waiting for ${file.name}, loading ahead for ${started.elapsedNow().toLogString()}")
        }
        return try {
            patches.get().also { logger.info("Loaded ${it.size} patches of ${file.name} ahead in ${loadedIn.milliseconds.toLogString()}") }
        } catch (e: ExecutionException) {
            logger.warn("Loading ${file.name} ahead of the run failed, loading it again: ${e.cause}")
            null
        }
    }

    private companion object {
        fun stampOf(file: File) = file.lastModified() to file.length()
    }
}

/** Sends the run's events to the app, from whichever thread reports them. */
private class HostEvents(private val stream: OutputStream) : AutoCloseable {
    private val writer = stream.bufferedWriter()

    // When the run last moved on, which a log line alone does not count as
    @Volatile
    private var progressedAt = System.nanoTime()

    fun send(event: HostEvent) {
        // Encoded before the lock is taken, so sendAndHalt only ever waits for a line being written
        val line = HostEvent.encode(event)
        synchronized(this) {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
        if (event !is HostEvent.Log) progressedAt = System.nanoTime()
    }

    /**
     * Logs what every thread does once the run has not moved on for [STALL_TIMEOUT], and again
     * after every further one, a few times at most. A run that hangs on some devices alone then
     * shows where in the log the user copies, which nothing else on the device can tell.
     */
    fun watchForStalls() {
        progressedAt = System.nanoTime()
        thread(isDaemon = true, name = "StallWatch") {
            var dumpedAt = progressedAt
            repeat(STALL_DUMPS) {
                do {
                    try {
                        Thread.sleep(STALL_POLL.inWholeMilliseconds)
                    } catch (_: InterruptedException) {
                        return@thread
                    }
                    val since = maxOf(progressedAt, dumpedAt)
                } while ((System.nanoTime() - since).nanoseconds < STALL_TIMEOUT)

                dumpedAt = System.nanoTime()
                // Logged apart first, as taking the stacks waits for every thread to reach a
                // safepoint, which a VM hung on one never gets past
                logger.warn("No progress for ${(dumpedAt - progressedAt).nanoseconds.inWholeSeconds}s, taking the stacks of all threads")
                logger.warn(threadDump())
            }
        }
    }

    /**
     * Ends the run as out of memory once collecting garbage took most of the time for a while. A
     * heap that barely holds what the run keeps alive is collected over and over for little each
     * time, and the run crawls on long past the point where the user gives up on it. Not every
     * collector gives up on its own, the serial one of a 32-bit JVM never does.
     *
     * By then the heap has no room for even the message, so the watch allocates nothing once it
     * runs: it samples into arrays made up front and sends a verdict encoded before it is needed.
     */
    fun watchForGcOverhead(gcClock: GcClock) {
        val verdict = ('\n' + HostEvent.encode(HostEvent.Finished(GC_OVERHEAD_FAILURE)) + '\n').toByteArray()
        val samples = (GC_OVERHEAD_WINDOW / GC_OVERHEAD_POLL).toInt()
        // When each sample was taken, in nanoseconds, and the time spent collecting by then, in milliseconds
        val takenAt = LongArray(samples)
        val collectedAt = LongArray(samples)

        thread(isDaemon = true, name = "GcOverheadWatch") {
            var taken = 0
            while (true) {
                try {
                    Thread.sleep(GC_OVERHEAD_POLL.inWholeMilliseconds)
                } catch (_: InterruptedException) {
                    return@thread
                }

                val now = System.nanoTime()
                val collected = gcClock.collectedMillis()
                // Holds the sample a whole window back, once there is one
                val slot = taken++ % samples
                if (taken > samples) {
                    val windowMillis = (now - takenAt[slot]) / NANOS_PER_MILLI
                    if ((collected - collectedAt[slot]) * 100 >= windowMillis * GC_OVERHEAD_PERCENT) {
                        sendAndHalt(verdict)
                    }
                }
                takenAt[slot] = now
                collectedAt[slot] = collected
            }
        }
    }

    /**
     * Sends an event encoded ahead and ends the process, without the shutdown that exit runs. The
     * event starts on a line of its own, as a send the full heap cut short may have left half of one.
     */
    private fun sendAndHalt(encoded: ByteArray) {
        synchronized(this) {
            stream.write(encoded)
            stream.flush()
        }
        Runtime.getRuntime().halt(0)
    }

    val logger = object : Logger() {
        override fun log(level: LogLevel, message: String) = send(HostEvent.Log(level, message))
    }

    val listener = object : PatchingListener {
        override suspend fun stepCompleted() = send(HostEvent.StepCompleted)

        override suspend fun splitPreparation(event: SplitPreparationEvent) =
            send(HostEvent.SplitPreparation(event))

        override suspend fun patchesStarted() = send(HostEvent.PatchesStarted)

        override fun patchExecuting(name: String) = send(HostEvent.PatchExecuting(name))

        override suspend fun patchSucceeded(name: String) = send(HostEvent.PatchSucceeded(name))

        override suspend fun patchFailed(name: String?, stackTrace: String) =
            send(HostEvent.PatchFailed(name, stackTrace))

        override suspend fun patchesFinished(count: Int) = send(HostEvent.PatchesFinished(count))
    }

    /** A stream whose lines go to the log as warnings, as nothing is meant to be printed. */
    fun printer() = PrintStream(
        LineOutputStream { line -> logger.warn("[STDIO]: $line") },
        true,
        Charsets.UTF_8.name()
    )

    override fun close() = writer.close()

    private companion object {
        // Writing TikTok reports no progress for nearly 3 minutes on a slow 32-bit phone
        val STALL_TIMEOUT = 5.minutes
        val STALL_POLL = 5.seconds
        const val STALL_DUMPS = 5

        // A run that patched TikTok on a 960MB heap spent about 70% of the time collecting
        val GC_OVERHEAD_WINDOW = 120.seconds
        val GC_OVERHEAD_POLL = 10.seconds
        const val GC_OVERHEAD_PERCENT = 60
        const val NANOS_PER_MILLI = 1_000_000L

        val GC_OVERHEAD_FAILURE = HostEvent.Failure(
            "${OutOfMemoryError::class.java.name}: Collecting garbage took over $GC_OVERHEAD_PERCENT% " +
                    "of the last ${GC_OVERHEAD_WINDOW.inWholeSeconds}s",
            outOfMemory = true
        )
    }
}

/** The stacks of all threads and how much of the heap is in use, for a run that stopped moving on. */
private fun threadDump(): String {
    return buildString {
        append("Threads, heap=${heapUsedMebibytes()}MB/${heapLimitMebibytes()}MB:")
        Thread.getAllStackTraces().entries.sortedBy { it.key.name }.forEach { (thread, frames) ->
            append("\n\"${thread.name}\" ${thread.state}")
            if (thread.isDaemon) append(" daemon")
            frames.take(DUMP_FRAMES).forEach { append("\n    at ").append(it) }
            if (frames.size > DUMP_FRAMES) append("\n    ... ${frames.size - DUMP_FRAMES} more")
        }
    }
}

private const val DUMP_FRAMES = 40

private fun heapUsedMebibytes(): Long {
    val runtime = Runtime.getRuntime()
    return bytesToMebibytes(runtime.totalMemory() - runtime.freeMemory())
}
