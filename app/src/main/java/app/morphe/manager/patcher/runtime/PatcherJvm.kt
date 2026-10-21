/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

import android.app.ActivityManager
import android.app.Application
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import app.morphe.manager.BuildConfig
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.host.HostCommand
import app.morphe.manager.patcher.host.HostEvent
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.runtime.jvm.EmbeddedJvm
import app.morphe.manager.patcher.runtime.jvm.JvmInstallation
import app.morphe.manager.patcher.runtime.jvm.JvmLaunch
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.bytesToMebibytes
import app.morphe.manager.util.tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes

/**
 * The JVM that patches, started inside the app's process when the first run is set up. HotSpot
 * cannot be started twice in one process, so the JVM serves every run until the process ends.
 * Living in the app's process, it is stopped only along with the app.
 */
class PatcherJvm(
    private val app: Application,
    private val fs: Filesystem,
    private val installation: JvmInstallation,
    private val patchBundleRepository: PatchBundleRepository,
    private val prefs: PreferencesManager,
    private val scope: AppCoroutineScope
) {
    private val lock = Mutex()
    private var host: Host? = null
    // A JVM that did not start leaves the process unable to start another
    private var startFailure: JvmUnavailableException? = null
    private var expiry: Job? = null

    // One run at a time, as they share the app's files and the heap
    private val runLock = Mutex()

    private val errorFile = app.filesDir.resolve(ERROR_FILE)

    /** The heap the JVM gets on this device: a third of its RAM, within bounds. */
    val heapLimitMb by lazy {
        val memoryInfo = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        val limit = (bytesToMebibytes(memoryInfo.totalMem) / 3).toInt()
            .coerceIn(HEAP_LIMIT_MINIMUM_MB, HEAP_LIMIT_MAXIMUM_MB)
        if (Process.is64Bit()) limit else limit.coerceAtMost(HEAP_LIMIT_32_BIT_MB)
    }

    private suspend fun isEnabled() = installation.isAvailable && prefs.useJvmRuntime.get()

    /**
     * Starts the JVM for the run the user is setting up, which loads the enabled patch bundles while
     * it waits. Unpacks the JVM first after an install or update, which takes a few seconds that the
     * user spends picking the APK and patches. Not done when the app starts, as writing the files
     * then held up its first frames. Bundles no run takes within [AHEAD_LIFETIME] are dropped, as
     * they hold memory for nothing.
     */
    fun startAhead() = scope.launch {
        if (!isEnabled()) return@launch
        lock.withLock {
            val host = try {
                host()
            } catch (e: JvmUnavailableException) {
                Log.w(tag, "Could not start the patcher JVM ahead of a run", e)
                return@withLock
            }
            host.send(HostCommand.Preload(enabledBundleFiles()))

            expiry?.cancel()
            expiry = scope.launch {
                delay(AHEAD_LIFETIME)
                host.send(HostCommand.Preload(emptyList()))
            }
        }
    }

    /**
     * Patches with the request written to [requestFile], reporting the run to [logger] and
     * [listener]. Returns what the run failed on, if it did.
     *
     * @throws JvmUnavailableException If the JVM could not be started, before anything was patched.
     */
    suspend fun run(requestFile: File, logger: Logger, listener: PatchingListener): HostEvent.Failure? {
        val host = lock.withLock {
            expiry?.cancel()
            host()
        }
        if (host.debugOptions.isNotEmpty()) logger.info("Debug JVM options: ${host.debugOptions.joinToString(" ")}")
        // Written when the JVM brings the process down, so it can only be left from a previous one
        if (errorFile.exists()) {
            logger.error("Patcher JVM error report:\n${errorFile.readText()}")
            errorFile.delete()
        }

        return runLock.withLock {
            val (events, eventsSink) = ParcelFileDescriptor.createPipe()
            // Open for the whole run, as the JVM opens its own end only once it starts the run
            eventsSink.use {
                val channel = readEvents(events)
                host.send(HostCommand.Run(requestFile.path, "/proc/self/fd/${eventsSink.fd}"))
                try {
                    relay(channel, logger, listener)
                } catch (e: Throwable) {
                    // A run given up on goes on until it notices, and keeps using the app's files until then
                    host.send(HostCommand.Cancel)
                    withContext(NonCancellable) {
                        runCatching { for (event in channel) if (event is HostEvent.Finished) break }
                    }
                    throw e
                }
            }
        }
    }

    /** Hands the events of the run over as they arrive, up to its end. */
    private suspend fun relay(
        events: ReceiveChannel<HostEvent>,
        logger: Logger,
        listener: PatchingListener
    ): HostEvent.Failure? {
        for (event in events) when (event) {
            is HostEvent.Log -> logger.log(event.level, event.message)
            HostEvent.StepCompleted -> listener.stepCompleted()
            is HostEvent.SplitPreparation -> listener.splitPreparation(event.event)
            HostEvent.PatchesStarted -> listener.patchesStarted()
            is HostEvent.PatchSucceeded -> listener.patchSucceeded(event.name)
            is HostEvent.PatchFailed -> listener.patchFailed(event.name, event.stackTrace)
            is HostEvent.PatchesFinished -> listener.patchesFinished(event.count)
            is HostEvent.Finished -> return event.failure
        }
        error("The patcher JVM stopped reporting before the run finished")
    }

    /** The events written to [source], read on a thread of their own, as a blocking read cannot be canceled. */
    private fun readEvents(source: ParcelFileDescriptor): ReceiveChannel<HostEvent> {
        val channel = Channel<HostEvent>(Channel.UNLIMITED)
        thread(isDaemon = true, name = "PatcherEvents") {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(source).bufferedReader().useLines { lines ->
                    lines.forEach { channel.trySend(HostEvent.decode(it)) }
                }
                channel.close()
            } catch (e: Exception) {
                channel.close(e)
            }
        }
        return channel
    }

    private suspend fun enabledBundleFiles(): List<String> {
        val enabled = patchBundleRepository.enabledBundlesInfoFlow.first().keys
        return patchBundleRepository.bundles.first().filterKeys { it in enabled }.values.map { it.patchesJar }
    }

    /** The started JVM, started now if it was not yet. Called with [lock] held. */
    private suspend fun host(): Host {
        host?.let { return it }
        startFailure?.let { throw it }

        val installed = installation.install()
        val debugOptions = debugOptions()
        val (commands, commandsSink) = ParcelFileDescriptor.createPipe()
        // Owned by the JVM from here on, which reads it for as long as the process lives
        val commandsFd = commands.detachFd()
        val launch = JvmLaunch(
            libjvm = installed.libjvm.path,
            preload = installed.preload,
            options = options(installed) + debugOptions, // Last, so a flag given again there wins
            mainClass = HOST_MAIN_CLASS,
            args = listOf("/proc/self/fd/$commandsFd")
        )

        val code = withContext(Dispatchers.IO) { EmbeddedJvm.start(launch) }
        if (code != 0) {
            commandsSink.close()
            throw JvmUnavailableException("The patcher JVM did not start ($code)").also { startFailure = it }
        }
        return Host(commandsSink, debugOptions).also { host = it }
    }

    private fun options(installed: JvmInstallation.Installed) = listOf(
        "-Djava.class.path=${installed.hostJar}",
        // The JVM falls back to /tmp, which an app cannot write to
        "-Djava.io.tmpdir=${fs.tempDir}",
        "-Xmx${heapLimitMb}m",
        // Leaves the signals ART relies on to ART, such as the one that dumps its threads
        "-Xrs",
        // Nothing reads the performance counters, which would otherwise be kept in a file of their own
        "-XX:-UsePerfData",
        // C1 only. A run is too short for C2 to pay off, and its compiles take cores the run needs
        "-XX:TieredStopAtLevel=1",
        // The working directory of an app is not writable, which is where the report would go
        "-XX:ErrorFile=$errorFile"
    ) + listOfNotNull(
        // 8 byte object headers instead of 12. A run keeps millions of small objects alive, and
        // smaller ones leave the collector and the caches less to do. A 32-bit JVM does not
        // know the flag and will not start with it.
        "-XX:+UseCompactObjectHeaders".takeIf { Process.is64Bit() }
    )

    /**
     * Options a debug build adds to the JVM, read from [DEBUG_OPTIONS_FILE] in the app's files: one
     * per line, # starts a comment. Lets flags be tried and runs be profiled without rebuilding,
     * see patcher-core/README.md.
     */
    private suspend fun debugOptions(): List<String> {
        if (!BuildConfig.DEBUG) return emptyList()
        val file = app.filesDir.resolve(DEBUG_OPTIONS_FILE)
        return withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext emptyList()
            file.readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }
        }
    }

    /**
     * The started JVM, which takes [HostCommand]s through [commands].
     *
     * @param debugOptions The options a debug build added, for every run to log.
     */
    private class Host(commands: ParcelFileDescriptor, val debugOptions: List<String>) {
        private val writer = ParcelFileDescriptor.AutoCloseOutputStream(commands).bufferedWriter()

        @Synchronized
        fun send(command: HostCommand) {
            writer.write(HostCommand.encode(command))
            writer.newLine()
            writer.flush()
        }
    }

    private companion object {
        const val HOST_MAIN_CLASS = "app/morphe/manager/patcher/host/HostMainKt"
        const val DEBUG_OPTIONS_FILE = "jvm-options.txt"
        const val ERROR_FILE = "patcher-jvm-error.log"

        // The patcher keeps several hundred megabytes alive for a large app. The JVM only takes
        // what it uses, so the limit is a ceiling that keeps a runaway patch from taking the device
        const val HEAP_LIMIT_MINIMUM_MB = 1024
        const val HEAP_LIMIT_MAXIMUM_MB = 3072

        // The heap is reserved in one piece, which a 32 bit process seldom finds much more of in
        // its address space. A reservation that fails leaves the run to ART
        const val HEAP_LIMIT_32_BIT_MB = 1024

        // Long enough to pick an APK and the patches, short enough not to hold memory long for nothing
        val AHEAD_LIFETIME = 5.minutes
    }
}
