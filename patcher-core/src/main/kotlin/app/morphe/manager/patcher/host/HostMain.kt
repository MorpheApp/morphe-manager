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
    val (commandsFile, eventsFile) = args
    val collectors = ManagementFactory.getGarbageCollectorMXBeans()
    serveRun(commandsFile, eventsFile, { collectors.sumOf { it.collectionTime } }) { loadPatchesFromJar(setOf(it)) }
}

/**
 * Serves the single run of a process of its own, in whichever VM it runs, and ends the process
 * with it. The host is started before the run is known, so it can load ahead while the user still
 * picks what to patch.
 *
 * @param commandsFile What the app writes [HostCommand]s to, one per line.
 * @param eventsFile What the app reads [HostEvent]s from.
 * @param gcMillis The time the VM spent collecting garbage so far, or null if it does not tell.
 * @param loadPatches Reads the patches of a bundle, which is the one step that depends on the VM.
 */
fun serveRun(
    commandsFile: String,
    eventsFile: String,
    gcMillis: (() -> Long)?,
    loadPatches: (File) -> Collection<Patch<*>>
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
                        run(command, preloaded, gcMillis, loadPatches, events)
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
    gcMillis: (() -> Long)?,
    loadPatches: (File) -> Collection<Patch<*>>,
    events: HostEvents
) {
    events.logger.info(vmDescription())
    events.watchForStalls()
    gcMillis?.let(events::watchForGcOverhead)

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

private fun loadLogged(file: File, loadPatches: (File) -> Collection<Patch<*>>, logger: Logger): Collection<Patch<*>> {
    val started = TimeSource.Monotonic.markNow()
    return loadPatches(file).also {
        logger.info("Loaded ${it.size} patches of ${file.name} in ${started.elapsedNow().inWholeMilliseconds}ms")
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
private class PreloadedBundle(private val file: File, loadPatches: (File) -> Collection<Patch<*>>) {
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
    fun take(logger: Logger): Collection<Patch<*>>? {
        if (stampOf(file) != stamp) {
            logger.info("${file.name} changed since it was loaded ahead, loading it again")
            return null
        }
        // A run that waits here for long is held up by the load itself rather than by what follows
        if (!patches.isDone) {
            logger.info("Waiting for ${file.name}, loading ahead for ${started.elapsedNow().inWholeMilliseconds}ms")
        }
        return try {
            patches.get().also { logger.info("Loaded ${it.size} patches of ${file.name} ahead in ${loadedIn}ms") }
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
private class HostEvents(stream: OutputStream) : AutoCloseable {
    private val writer = stream.bufferedWriter()

    // When the run last moved on, which a log line alone does not count as
    @Volatile
    private var progressedAt = System.nanoTime()

    @Synchronized
    fun send(event: HostEvent) {
        writer.write(HostEvent.encode(event))
        writer.newLine()
        writer.flush()
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
     * @param gcMillis The time the VM spent collecting garbage so far.
     */
    fun watchForGcOverhead(gcMillis: () -> Long) {
        thread(isDaemon = true, name = "GcOverheadWatch") {
            val started = TimeSource.Monotonic.markNow()
            // When each sample was taken and the time spent collecting by then, in milliseconds
            val samples = ArrayDeque<Pair<Long, Long>>()
            while (true) {
                try {
                    Thread.sleep(GC_OVERHEAD_POLL.inWholeMilliseconds)
                } catch (_: InterruptedException) {
                    return@thread
                }

                val now = started.elapsedNow().inWholeMilliseconds
                samples.addLast(now to gcMillis())
                val (windowStart, windowGc) = samples.first()
                val window = now - windowStart
                if (window < GC_OVERHEAD_WINDOW.inWholeMilliseconds) continue
                samples.removeFirst()

                val share = (samples.last().second - windowGc).toDouble() / window
                if (share < GC_OVERHEAD_LIMIT) continue

                val error = OutOfMemoryError(
                    "Collecting garbage took ${(share * 100).toInt()}% of the last " +
                            "${window.milliseconds.inWholeSeconds}s, heap=${heapUsedMebibytes()}MB/${heapLimitMebibytes()}MB"
                )
                send(HostEvent.Finished(HostEvent.Failure(error)))
                exitProcess(0)
            }
        }
    }

    val logger = object : Logger() {
        override fun log(level: LogLevel, message: String) = send(HostEvent.Log(level, message))
    }

    val listener = object : PatchingListener {
        override suspend fun stepCompleted() = send(HostEvent.StepCompleted)

        override suspend fun splitPreparation(event: SplitPreparationEvent) =
            send(HostEvent.SplitPreparation(event))

        override suspend fun patchesStarted() = send(HostEvent.PatchesStarted)

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
        val STALL_TIMEOUT = 60.seconds
        val STALL_POLL = 5.seconds
        const val STALL_DUMPS = 5

        // A run that patched TikTok on a 960MB heap spent about 70% of the time collecting
        val GC_OVERHEAD_WINDOW = 120.seconds
        val GC_OVERHEAD_POLL = 10.seconds
        const val GC_OVERHEAD_LIMIT = 0.6
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
