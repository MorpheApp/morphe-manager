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
import app.morphe.manager.patcher.split.SplitPreparationEvent
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import kotlin.concurrent.thread

/**
 * Entry point of the JVM the app embeds for patching. HotSpot cannot be started twice in a
 * process, so the JVM lives as long as the app does and serves each of its runs.
 *
 * @param args The read end of a pipe the app writes [HostCommand]s to, one per line.
 */
fun main(args: Array<String>) {
    val host = Host()
    File(args.single()).bufferedReader().useLines { lines ->
        lines.forEach { host.handle(HostCommand.decode(it)) }
    }
}

/** Carries out what the app asks for. The app hands over a run only once the one before it finished. */
private class Host {
    // Taken by the run that follows, so a bundle is loaded ahead for one run at most
    @Volatile
    private var preloaded = emptyMap<String, PreloadedBundle>()

    // Kept once completed, which a cancel then leaves as it is
    @Volatile
    private var run: Job? = null

    fun handle(command: HostCommand) {
        when (command) {
            is HostCommand.Preload -> {
                preloaded = command.bundles.associateWith(::PreloadedBundle)
                // A run in progress releases what it leaves once it ends
                if (command.bundles.isEmpty() && run?.isActive != true) releaseMemory()
            }

            is HostCommand.Run -> {
                // Set before the thread starts, so a cancel that follows right away is not lost
                val job = Job()
                run = job
                thread(name = "PatcherRun") { run(command, job) }
            }

            HostCommand.Cancel -> run?.cancel()
        }
    }

    private fun run(command: HostCommand.Run, job: CompletableJob) {
        val bundles = preloaded
        preloaded = emptyMap()
        try {
            HostEvents(FileOutputStream(command.eventsFile)).use { events ->
                val failure = runCatching {
                    val request = Json.decodeFromString(PatchRequest.serializer(), File(command.requestFile).readText())
                    runBlocking(job) {
                        PatchPipeline(request, events.logger, events.listener) { bundle ->
                            bundles[bundle.patchesFile]?.take(events.logger)
                                ?: loadPatchesFromJar(setOf(File(bundle.patchesFile)))
                        }.run()
                    }
                }.exceptionOrNull()

                events.send(HostEvent.Finished(failure?.let(HostEvent::Failure)))
            }
        } catch (_: IOException) {
            // The app stopped reading, so there is nobody left to report to
        } finally {
            job.complete()
            releaseMemory()
        }
    }

    /**
     * Collects in full, which is when the collector shrinks the heap. A run leaves hundreds of
     * megabytes committed that the app's process would otherwise keep until the next one.
     */
    private fun releaseMemory() = System.gc()
}

/**
 * The patches of a bundle, loaded on a thread of their own from the moment they are asked for. The
 * file may be replaced by an update before the run, so they are only used if it is unchanged.
 */
private class PreloadedBundle(path: String) {
    private val file = File(path)
    private val stamp = stampOf(file)
    private val patches = FutureTask { loadPatchesFromJar(setOf(file)) }.also {
        thread(isDaemon = true, name = "PreloadPatches") { it.run() }
    }

    /** The loaded patches, or null if the file changed or loading failed, for them to be loaded again. */
    fun take(logger: Logger): Set<Patch<*>>? {
        if (stampOf(file) != stamp) return null
        return try {
            patches.get()
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

    @Synchronized
    fun send(event: HostEvent) {
        writer.write(HostEvent.encode(event))
        writer.newLine()
        writer.flush()
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

    override fun close() = writer.close()
}
