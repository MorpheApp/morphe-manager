/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.ForegroundInfo
import app.morphe.manager.BuildConfig
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.host.HostCommand
import app.morphe.manager.patcher.host.HostEvent
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.patcher.runtime.jvm.JvmInstallation
import app.morphe.manager.patcher.runtime.jvm.JvmLaunch
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.bytesToMebibytes
import app.morphe.manager.util.tag
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Where a run happens, both in a process of its own, see [PatcherService]. */
enum class PatcherVm {
    /** The JVM the app embeds, whose heap is bound by what the device spares. */
    JVM,

    /** The runtime of Android, within the heap it gives the app. */
    ART
}

/**
 * Starts the process that patches, one for every run and ahead of the run where it can. Starting
 * it and loading the patch bundles takes a second or two, which is spent while the user still
 * picks what to patch rather than once patching starts.
 *
 * A large run can take more memory than the device has to spare, and the system then ends the
 * process that holds it, or the heap of the run fills up wherever it happens to allocate. Kept
 * apart from the app, either ends the run alone, and the app lives on to say so.
 */
class PatcherProcesses(
    private val app: Application,
    private val fs: Filesystem,
    private val installation: JvmInstallation,
    private val patchBundleRepository: PatchBundleRepository,
    private val prefs: PreferencesManager,
    private val scope: AppCoroutineScope
) {
    private val lock = Mutex()
    private var ahead: PatcherProcess? = null
    private var expiry: Job? = null

    // Set from when a process that records for the AOT cache starts until the cache is made from it
    private val aotRecording = AtomicBoolean(false)

    /** How a JVM takes part in the AOT cache of the runtime and host, see [JvmInstallation.Installed.aotCache]. */
    private enum class AotUse {
        /** Without one, as making it failed, or another run is recording for it. */
        NONE,

        /** Records what the run loads and profiles, for the cache to be made from once it exits. */
        RECORD,

        /** Starts from the cache. One that does not fit the JVM is left unused, which the JVM checks. */
        USE;

        fun options(installed: JvmInstallation.Installed) = when (this) {
            NONE -> emptyList()
            RECORD -> listOf(
                "-XX:AOTMode=record",
                "-XX:AOTConfiguration=${installed.aotConfiguration}",
                AOT_LOG_ERRORS_ONLY
            )
            USE -> listOf("-XX:AOTCache=${installed.aotCache}")
        }
    }

    /**
     * The heap a JVM is started with: a third of the RAM within bounds, or a fixed one in a 32-bit
     * process, which may find room for less, see [PatcherService].
     */
    private val jvmHeapLimitMb by lazy {
        if (Process.is64Bit()) {
            val memoryInfo = ActivityManager.MemoryInfo()
            app.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
            (bytesToMebibytes(memoryInfo.totalMem) / 3).toInt()
                .coerceIn(HEAP_LIMIT_MINIMUM_MB, HEAP_LIMIT_MAXIMUM_MB)
        } else {
            HEAP_LIMIT_32_BIT_MB
        }
    }

    /** The heap a run on [vm] gets. ART gives the process of the run what it gives the app's. */
    fun heapLimitMb(vm: PatcherVm) = when (vm) {
        PatcherVm.JVM -> jvmHeapLimitMb
        PatcherVm.ART -> heapLimitMebibytes()
    }

    /** The JVM where the user left it on and the device can run it, ART otherwise. */
    suspend fun preferredVm() =
        if (installation.isAvailable && prefs.useJvmRuntime.get()) PatcherVm.JVM else PatcherVm.ART

    /**
     * Starts a process for the run the user is setting up, which loads the enabled patch bundles
     * while it waits. Unpacks the JVM first after an install or update, which takes a few seconds
     * that the user spends picking the APK and patches. Not done when the app starts, as writing
     * the files then held up its first frames. One that is not used within [AHEAD_LIFETIME] is
     * ended, as it holds memory for nothing.
     */
    fun startAhead() = scope.launch {
        val vm = preferredVm()
        lock.withLock {
            if (ahead?.let { it.vm == vm && it.isAlive } == true) return@withLock
            ahead?.close()
            ahead = null

            ahead = try {
                start(enabledBundleFiles(), vm)
            } catch (e: Exception) {
                Log.w(tag, "Could not start the patcher ahead of a run", e)
                return@withLock
            }
            expiry?.cancel()
            expiry = scope.launch {
                delay(AHEAD_LIFETIME)
                lock.withLock {
                    ahead?.close()
                    ahead = null
                }
            }
        }
    }

    /**
     * Patches on [vm] with the request written to [requestFile], which takes [bundles], reporting
     * the run to [logger] and [listener]. Its process holds [foreground] while the run lasts.
     *
     * @throws JvmUnavailableException If the JVM could not be started, before anything was patched.
     * @throws PatcherKilledException If the process ended in the middle of the run.
     * @throws HeapExhaustedException If the run filled the heap.
     * @throws PatcherFailureException If the run failed on anything else.
     */
    suspend fun run(
        requestFile: File,
        bundles: List<String>,
        vm: PatcherVm,
        foreground: ForegroundInfo,
        logger: Logger,
        listener: PatchingListener
    ) = take(bundles, vm).use { it.run(requestFile, foreground, logger, listener) }

    /**
     * A process for a run on [vm] that patches with [bundles]: the one started ahead while it is
     * still alive, otherwise a new one. Android may end a waiting one to free memory.
     */
    private suspend fun take(bundles: List<String>, vm: PatcherVm): PatcherProcess = lock.withLock {
        expiry?.cancel()
        val started = ahead
        ahead = null
        if (started != null && started.vm == vm && started.isAlive) return@withLock started
        started?.close()
        start(bundles, vm)
    }

    private suspend fun enabledBundleFiles(): List<String> {
        val enabled = patchBundleRepository.enabledBundlesInfoFlow.first().keys
        return patchBundleRepository.bundles.first().filterKeys { it in enabled }.values.map { it.patchesJar }
    }

    private suspend fun start(bundles: List<String>, vm: PatcherVm): PatcherProcess {
        val process = try {
            startHost(vm)
        } catch (e: Exception) {
            // What the run falls back to ART on
            throw if (vm == PatcherVm.JVM && (e is IllegalStateException || e is RemoteException)) {
                JvmUnavailableException(e.message ?: "The patcher JVM did not start", e)
            } else {
                e
            }
        }

        return process.apply {
            try {
                send(HostCommand.Preload(bundles))
            } catch (_: IOException) {
                // Gone already, which isAlive tells whoever takes it
            }
        }
    }

    private suspend fun startHost(vm: PatcherVm): PatcherProcess {
        val installed = if (vm == PatcherVm.JVM) installation.install() else null
        val aot = installed?.let { aotUse(it) } ?: AotUse.NONE
        val tuning = installed?.let { jvmTuning(aot.options(it)) }.orEmpty()
        val launch = installed?.let { jvmLaunch(it, tuning) }
        val aotRecord = installed.takeIf { aot == AotUse.RECORD }
        try {
            val connection = PatcherConnection.connect(app)
            val host = try {
                startIn(connection, launch)
            } catch (e: Exception) {
                connection.close()
                throw e
            }
            return PatcherProcess(vm, connection, host.started, host.commands, host.events, launch, tuning, aotRecord)
        } catch (e: Exception) {
            // Nothing was recorded, which leaves recording to the next run
            if (aotRecord != null) aotRecording.set(false)
            throw e
        }
    }

    /** A host just started, with the ends of its pipes the app keeps, see [startIn]. */
    private class StartedHost(
        val started: HostStarted,
        val commands: ParcelFileDescriptor,
        val events: ParcelFileDescriptor
    ) : Closeable {
        override fun close() {
            commands.close()
            events.close()
        }
    }

    /** Starts the host in the process of [connection], in the JVM of [launch], or on ART if null. */
    private suspend fun startIn(connection: PatcherConnection, launch: JvmLaunch?): StartedHost {
        val (commands, commandsSink) = ParcelFileDescriptor.createPipe()
        val (events, eventsSink) = ParcelFileDescriptor.createPipe()
        val started = try {
            // Ends the process gets copies of, closed here so that its end closes the pipes
            commands.use {
                eventsSink.use {
                    withContext(Dispatchers.IO) {
                        if (launch != null) connection.service.startJvm(launch, commands, eventsSink)
                        else connection.service.startArt(commands, eventsSink)
                    }
                }
            }
        } catch (e: Exception) {
            commandsSink.close()
            events.close()
            throw e
        }
        return StartedHost(started, commandsSink, events)
    }

    private fun jvmLaunch(installed: JvmInstallation.Installed, tuning: List<String>) = JvmLaunch(
        libjvm = installed.libjvm.path,
        preload = installed.preload,
        options = jvmOptions(installed) + tuning,
        mainClass = HOST_MAIN_CLASS,
        heapLimitMb = jvmHeapLimitMb
    )

    /**
     * How a JVM started now takes part in the AOT cache, see [AotUse]. Only one run records at a
     * time, and none while the cache is being made from what one recorded.
     */
    private fun aotUse(installed: JvmInstallation.Installed) = when {
        // JDK 17, which a 32-bit process runs, has no AOT cache
        !Process.is64Bit() -> AotUse.NONE
        installed.aotCache.isFile -> AotUse.USE
        aotFailures(installed) >= AOT_ATTEMPTS -> AotUse.NONE
        aotRecording.compareAndSet(false, true) -> AotUse.RECORD
        else -> AotUse.NONE
    }

    /** How many attempts to make the AOT cache failed, one line each in [JvmInstallation.Installed.aotFailed]. */
    private fun aotFailures(installed: JvmInstallation.Installed) = try {
        installed.aotFailed.takeIf { it.isFile }?.readLines()?.size ?: 0
    } catch (_: IOException) {
        AOT_ATTEMPTS
    }

    /**
     * Makes the AOT cache from what a run recorded, in a JVM of its own that exits once the cache
     * is written, which takes about 2 seconds on a recent phone. A failure is left on record, and
     * after [AOT_ATTEMPTS] of them runs go on without a cache instead of each recording again.
     */
    private suspend fun createAotCache(installed: JvmInstallation.Installed) {
        val configuration = installed.aotConfiguration
        val staging = installed.aotStaging.apply { delete() }
        try {
            if (!configuration.isFile) throw IOException("The run recorded no AOT configuration")

            val launch = jvmLaunch(
                installed,
                jvmTuning(
                    listOf(
                        "-XX:AOTMode=create",
                        "-XX:AOTConfiguration=$configuration",
                        "-XX:AOTCache=$staging",
                        AOT_LOG_ERRORS_ONLY
                    )
                )
            )
            PatcherConnection.connect(app).use { connection ->
                val host = try {
                    startIn(connection, launch)
                } catch (_: RemoteException) {
                    // The JVM exits once it wrote the cache, and its process with it
                    null
                }
                if (host != null) {
                    host.close()
                    throw IllegalStateException("The JVM to make the AOT cache started the host instead of exiting")
                }
            }

            if (!staging.isFile) throw IOException("The JVM made no AOT cache")
            if (!staging.renameTo(installed.aotCache)) throw IOException("Could not move the AOT cache into place")
            Log.i(tag, "Made the AOT cache of the patcher JVM, ${bytesToMebibytes(installed.aotCache.length())}MB")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Could not make the AOT cache of the patcher JVM", e)
            withContext(Dispatchers.IO) {
                try {
                    installed.aotFailed.appendText("${e.toString().replace('\n', ' ')}\n")
                } catch (markFailure: IOException) {
                    Log.e(tag, "Could not note that the AOT cache failed", markFailure)
                }
            }
        } finally {
            configuration.delete()
            staging.delete()
        }
    }

    /** The options any JVM needs to run inside an app, the same on every device. */
    private fun jvmOptions(installed: JvmInstallation.Installed) = listOf(
        "-Djava.class.path=${installed.hostJar}",
        // The JVM falls back to /tmp, which an app cannot write to
        "-Djava.io.tmpdir=${fs.tempDir}",
        // Patches that ship a JNI library load it from an unnamed module
        "--enable-native-access=ALL-UNNAMED",
        // Leaves the signals ART relies on to ART, such as the one that dumps its threads
        "-Xrs",
        // Nothing reads the performance counters, which would otherwise be kept in a file of their own
        "-XX:-UsePerfData",
        // The working directory of an app is not writable, which is where the report would go
        "-XX:ErrorFile=${errorFile("%p")}"
    )

    /**
     * The options that tune the JVM to the process, then [aot], the options of the AOT cache, and
     * last those of a debug build, so a debug build can override any flag by giving it again. A
     * 64-bit process runs JDK 25 and a 32-bit one JDK 17, see embeddedJvms of the app.
     */
    private suspend fun jvmTuning(aot: List<String>) = (if (Process.is64Bit()) listOfNotNull(
        // C1 only for a run that neither starts from an AOT cache nor records for one. Most runs
        // are too short for C2 to pay off then, and its compiles take cores the run needs. With
        // the profiles of the cache, C2 compiles early enough to pay off on short runs as well,
        // and long ones gain the most from it. A recording run needs the profiling tiers too, as
        // C1 alone profiles nothing that the cache could hold
        "-XX:TieredStopAtLevel=1".takeIf { aot.isEmpty() },
        // 8 byte object headers instead of 12. A run keeps millions of small objects alive, and
        // smaller ones leave the heap and the collector less to do. The AOT cache only works with
        // the headers it was made with
        "-XX:+UseCompactObjectHeaders"
    ) + aot else listOf(
        // The simplest collector, with no threads of its own, on a port that runs few others. A
        // 32-bit JVM is the client VM, which only has C1 to begin with
        "-XX:+UseSerialGC"
    )) + debugOptions()

    /** Where the JVM in process [pid] reports a crash, %p standing for the pid of whichever does. */
    private fun errorFile(pid: Any) = fs.tempDir.resolve("patcher-jvm-error-$pid.log")

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
     * A started host waiting for its run, which takes [HostCommand]s through [commands] and reports
     * on [events]. Closing it ends its process.
     *
     * @param launch How its JVM was started, or null on ART.
     * @param tuning The options of [launch] that differ between devices and builds, for the run to log.
     * @param aotRecord The installation whose AOT cache the run records for, or null if it does not.
     */
    private inner class PatcherProcess(
        val vm: PatcherVm,
        private val connection: PatcherConnection,
        private val started: HostStarted,
        commands: ParcelFileDescriptor,
        events: ParcelFileDescriptor,
        private val launch: JvmLaunch?,
        private val tuning: List<String>,
        private val aotRecord: JvmInstallation.Installed?
    ) : Closeable {
        private val startedAt = System.currentTimeMillis()

        private val writer = ParcelFileDescriptor.AutoCloseOutputStream(commands).bufferedWriter()

        // Read from the start, as patches loaded ahead of the run may already print
        private val events = readEvents(events)

        // Set once the process ended on its own, after which its pid may belong to another
        private var ended = false

        // Set once the run started its service in the foreground, which only the app stops
        private var heldForeground = false

        // Set once a run that records for the AOT cache succeeded, to make the cache from
        private var recorded = false

        val isAlive get() = connection.service.asBinder().isBinderAlive

        @Synchronized
        fun send(command: HostCommand) {
            writer.write(HostCommand.encode(command))
            writer.newLine()
            writer.flush()
        }

        suspend fun run(requestFile: File, foreground: ForegroundInfo, logger: Logger, listener: PatchingListener) {
            logDescription(logger)
            holdForeground(foreground)
            try {
                send(HostCommand.Run(requestFile.path))
            } catch (_: IOException) {
                // The process is gone, which the events end on as well
            }

            val finished = relay(logger, listener)
            ended = true
            if (finished == null) {
                val killed = killedException()
                val errorFile = errorFile(started.pid)
                if (errorFile.exists()) {
                    logger.error("Patcher JVM error report:\n${errorFile.readText()}")
                    errorFile.delete()
                }
                throw killed
            }

            recorded = aotRecord != null && finished.failure == null

            val failure = finished.failure ?: return
            throw if (failure.outOfMemory) HeapExhaustedException(started.heapLimitMb, failure.stackTrace)
            else PatcherFailureException(failure.stackTrace)
        }

        /**
         * Has the process hold [foreground], the notification the app already shows for the run.
         * Bound by the app alone, it would count as idle on systems that end such a process once
         * the app leaves the screen, as MIUI and HyperOS do.
         */
        private fun holdForeground(foreground: ForegroundInfo) {
            try {
                app.startForegroundService(PatcherService.foregroundIntent(app, foreground))
                heldForeground = true
            } catch (e: IllegalStateException) {
                // Refused to a run started from the background, which goes on bound alone
                Log.w(tag, "Could not hold the patcher process in the foreground", e)
            }
        }

        /**
         * Stops what [holdForeground] started, from here, as the process ends as soon as its run
         * is done, at times before the system tells it of the unbinding. A service started in the
         * foreground that is unbound from a process ended so stays on record with the system, and
         * keeps the notification up, which the app cannot cancel, until the app itself ends.
         */
        private fun releaseForeground() {
            if (heldForeground) app.stopService(Intent(app, PatcherService::class.java))
        }

        /**
         * What the run happens in, as the process and the device report it. A run that fails on
         * some devices alone is mostly told apart by these.
         */
        private fun logDescription(logger: Logger) {
            val bits = if (Process.is64Bit()) 64 else 32
            val age = (System.currentTimeMillis() - startedAt) / 1000
            logger.info(
                "Patcher process: pid=${started.pid} vm=$vm heap=${started.heapLimitMb}MB bits=$bits " +
                        "abis=${Build.SUPPORTED_ABIS.joinToString(",")} age=${age}s"
            )
            launch?.let {
                logger.info("Patcher JVM: libjvm=${it.libjvm.substringAfter("/lib/")} options=${tuning.joinToString(" ")}")
            }
        }

        /**
         * Hands the events of the run over as they arrive, up to its end, or null if the process
         * ended first. A run that ends in the middle of a patch names it, as it reported nothing
         * of that patch yet.
         */
        private suspend fun relay(logger: Logger, listener: PatchingListener) = coroutineScope {
            val runningPatch = RunningPatchWatch(this, logger)
            try {
                for (event in events) when (event) {
                    is HostEvent.Log -> logger.log(event.level, event.message)
                    HostEvent.StepCompleted -> listener.stepCompleted()
                    is HostEvent.SplitPreparation -> listener.splitPreparation(event.event)
                    HostEvent.PatchesStarted -> listener.patchesStarted()
                    is HostEvent.PatchExecuting -> runningPatch.started(event.name)
                    is HostEvent.PatchSucceeded -> listener.patchSucceeded(event.name)
                    is HostEvent.TaskProgress -> listener.taskProgress(event.task, event.done, event.total)
                    is HostEvent.PatchFailed -> {
                        runningPatch.stopped()
                        listener.patchFailed(event.name, event.stackTrace)
                    }
                    is HostEvent.PatchesFinished -> {
                        runningPatch.stopped()
                        listener.patchesFinished(event.count)
                    }
                    is HostEvent.Finished -> {
                        if (event.failure?.outOfMemory == true) {
                            runningPatch.current?.let { logger.error("$it was running when the heap ran out") }
                        }
                        return@coroutineScope event
                    }
                }
                runningPatch.current?.let { logger.error("$it was running when the patcher process ended") }
                null
            } finally {
                runningPatch.stopped()
            }
        }

        /**
         * The end of the process as the system recorded it. Asked a few times, as the record is only
         * written once the system has dealt with the death, which can lag behind the pipe closing.
         */
        private suspend fun killedException(): PatcherKilledException {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return PatcherKilledException(NO_EXIT_RECORD, crashed = false)

            val activityManager = app.getSystemService(ActivityManager::class.java)
            return withTimeoutOrNull(EXIT_RECORD_TIMEOUT) {
                var info: ApplicationExitInfo?
                do {
                    info = activityManager.getHistoricalProcessExitReasons(app.packageName, started.pid, 1).firstOrNull()
                    if (info == null) delay(EXIT_RECORD_POLL)
                } while (info == null)
                val crashed = info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                        info.reason == ApplicationExitInfo.REASON_CRASH
                PatcherKilledException(info.describe(), crashed)
            } ?: PatcherKilledException(NO_EXIT_RECORD, crashed = false)
        }

        // The run is over or was given up on, and nothing but the end of its process stops a JVM
        override fun close() {
            if (!ended) Process.killProcess(started.pid)
            runCatching { writer.close() }
            releaseForeground()
            val installed = aotRecord
            when {
                installed == null -> connection.close()
                // Undispatched, so the lock is taken before a process started next could take it
                recorded -> scope.launch(start = CoroutineStart.UNDISPATCHED) { finishAotRecord(installed) }
                else -> {
                    connection.close()
                    aotRecording.set(false)
                }
            }
        }

        /**
         * Makes the AOT cache from what the run recorded, after the result was already handed on.
         * The JVM writes the recording while it exits, which unbinding first would cut short by
         * ending the process. Holds the lock until the cache is made, as a process started in the
         * meantime would be this one, the service being bound already.
         */
        private suspend fun finishAotRecord(installed: JvmInstallation.Installed) = lock.withLock {
            try {
                withTimeoutOrNull(AOT_RECORD_EXIT_TIMEOUT) {
                    while (isAlive) delay(EXIT_RECORD_POLL)
                } ?: Log.w(tag, "The patcher JVM recording for the AOT cache did not exit in time")
                connection.close()
                createAotCache(installed)
            } finally {
                aotRecording.set(false)
            }
        }
    }

    /** The events written to [source], read on a thread of their own, as a blocking read cannot be canceled. */
    private fun readEvents(source: ParcelFileDescriptor): ReceiveChannel<HostEvent> {
        val channel = Channel<HostEvent>(Channel.UNLIMITED)
        thread(isDaemon = true, name = "PatcherEvents") {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(source).bufferedReader().useLines { lines ->
                    lines.filter(String::isNotBlank).forEach { channel.trySend(decodeEvent(it)) }
                }
                channel.close()
            } catch (e: Exception) {
                channel.close(e)
            }
        }
        return channel
    }

    /**
     * The event of a line, or the line itself as a warning if it holds none. A host that ran out of
     * heap halfway through a send leaves half a line, and the events after it still count.
     */
    private fun decodeEvent(line: String) = try {
        HostEvent.decode(line)
    } catch (_: SerializationException) {
        HostEvent.Log(LogLevel.WARN, "Unreadable event of the patcher process: $line")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.describe(): String {
        val cause = when (reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
            ApplicationExitInfo.REASON_SIGNALED -> "killed by signal $status"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for excessive resource usage"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped by the user"
            ApplicationExitInfo.REASON_EXIT_SELF -> "exited with $status"
            else -> "ended for reason $reason"
        }
        // Both are reported in kilobytes, and left at 0 for a process killed before they were read
        val memory = "pss=${bytesToMebibytes(pss * 1024)}MB rss=${bytesToMebibytes(rss * 1024)}MB".takeIf { rss > 0 }
        return listOfNotNull(cause, description?.takeIf { it.isNotBlank() }, memory).joinToString(", ")
    }

    /** A binding to [PatcherService] for a single run, which ends the service's process once closed. */
    private class PatcherConnection private constructor(private val context: Context) : ServiceConnection, Closeable {
        private var continuation: CancellableContinuation<IPatcherService>? = null
        lateinit var service: IPatcherService
            private set

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            continuation?.resume(IPatcherService.Stub.asInterface(binder))
            continuation = null
        }

        // A process that dies is noticed through the events of the run instead
        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onNullBinding(name: ComponentName) = fail("bound to nothing")

        override fun onBindingDied(name: ComponentName) = fail("binding died")

        private fun fail(reason: String) {
            continuation?.resumeWithException(IllegalStateException("Patcher service $reason"))
            continuation = null
        }

        override fun close() = context.unbindService(this)

        companion object {
            suspend fun connect(context: Context): PatcherConnection {
                val connection = PatcherConnection(context)
                val service = try {
                    withTimeoutOrNull(CONNECT_TIMEOUT) {
                        suspendCancellableCoroutine<IPatcherService> { continuation ->
                            connection.continuation = continuation
                            val bound = context.bindService(
                                Intent(context, PatcherService::class.java),
                                connection,
                                // Keeps the process off the slow background cores
                                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
                            )
                            if (!bound) connection.fail("could not be bound")
                        }
                    }
                } catch (e: Throwable) {
                    connection.close()
                    throw e
                }

                if (service == null) {
                    connection.close()
                    throw IllegalStateException("Patcher service did not connect within $CONNECT_TIMEOUT")
                }
                return connection.apply { this.service = service }
            }
        }
    }

    private companion object {
        const val HOST_MAIN_CLASS = "app/morphe/manager/patcher/host/HostMainKt"
        const val DEBUG_OPTIONS_FILE = "jvm-options.txt"
        const val NO_EXIT_RECORD = "no exit record"

        // The patcher keeps close to a gigabyte alive for the largest apps. The JVM only takes what
        // it uses, so the limit is a ceiling that keeps a runaway patch from taking the device
        const val HEAP_LIMIT_MINIMUM_MB = 1536
        const val HEAP_LIMIT_MAXIMUM_MB = 3072

        // The heap is reserved in one piece, which a 32 bit process seldom finds much more of in
        // its address space
        const val HEAP_LIMIT_32_BIT_MB = 1024

        // Generous because a slow device on a cold start can take many seconds to start a process
        val CONNECT_TIMEOUT = 30.seconds

        val EXIT_RECORD_TIMEOUT = 2.seconds
        val EXIT_RECORD_POLL = 200.milliseconds

        // Writing the recording took about 2 seconds on a recent phone and can take many times
        // that on a slow one. Giving up cuts the recording short, and that device gets no cache
        val AOT_RECORD_EXIT_TIMEOUT = 2.minutes

        // Enough for a phone that was slow once, too few to record on every run where it never works
        const val AOT_ATTEMPTS = 3

        // Making the cache warns of every class it leaves out, hundreds of lines in logcat
        const val AOT_LOG_ERRORS_ONLY = "-Xlog:aot*=error"

        // Long enough to pick an APK and the patches, short enough not to hold memory long for nothing
        val AHEAD_LIFETIME = 5.minutes
    }
}
