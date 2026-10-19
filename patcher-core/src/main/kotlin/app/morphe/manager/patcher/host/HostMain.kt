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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Entry point of the JVM the app embeds for patching. The JVM is started before the run is known,
 * so it can load ahead of time while the user still picks what to patch.
 *
 * @param args The read end of a pipe the app writes the run to, followed by the patch bundles that
 *             run is likely to patch with, which are loaded while waiting for it. The run is two
 *             lines: the file the [PatchRequest] was written to, and the file the events go to,
 *             which is the write end of a pipe the app reads.
 */
fun main(args: Array<String>) {
    val commandsFile = args.first()
    val preloaded = args.drop(1).associateWith(::PreloadedBundle)

    val (requestFile, eventsFile) = File(commandsFile).bufferedReader().use { reader ->
        listOf(reader.readLine(), reader.readLine())
    }
    // The app gave up on the run before handing it over
    if (requestFile == null || eventsFile == null) exitProcess(0)

    val code = try {
        HostEvents(FileOutputStream(eventsFile)).use { events ->
            events.send(HostEvent.Started)

            val failure = runCatching {
                val request = Json.decodeFromString(PatchRequest.serializer(), File(requestFile).readText())
                runBlocking {
                    PatchPipeline(request, events.logger, events.listener) { bundle ->
                        preloaded[bundle.patchesFile]?.take(events.logger)
                            ?: loadPatchesFromJar(setOf(File(bundle.patchesFile)))
                    }.run()
                }
            }.exceptionOrNull()

            events.send(HostEvent.Finished(failure?.let(HostEvent::Failure)))
        }
        0
    } catch (_: IOException) {
        // The app stopped reading, so there is nobody left to report to
        1
    }

    // Threads the patches left running must not keep the process, which exists for this run alone
    exitProcess(code)
}

/**
 * The patches of a bundle, loaded on a thread of their own from the moment the JVM starts. The
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
