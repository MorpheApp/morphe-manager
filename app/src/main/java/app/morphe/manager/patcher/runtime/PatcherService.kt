/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.Process
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import androidx.work.ForegroundInfo
import app.morphe.manager.patcher.host.HostEvent
import app.morphe.manager.patcher.host.serveRun
import app.morphe.manager.patcher.patch.PatchBundle
import app.morphe.manager.patcher.runtime.jvm.EmbeddedJvm
import app.morphe.manager.patcher.runtime.jvm.JvmLaunch
import app.morphe.manager.util.LOG_TAG
import kotlinx.parcelize.Parcelize
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Hosts a run in a process of its own declared for it, in the JVM the app embeds, which runs as a
 * child of that process, or on the ART of the process. When the system runs short of memory for a
 * large run, or the heap of the run fills up, it is this process that ends, and the app is left to
 * report it.
 *
 * Bound by [PatcherProcesses] for a single run. The host is started first and waits for the run,
 * so it can be started before the run is known. The process ends with the run, the JVM included,
 * which is also the only way to stop one, and the next run starts in a fresh one.
 */
class PatcherService : Service() {
    private var started = false

    private val binder = object : IPatcherService.Stub() {
        @Synchronized
        override fun startJvm(
            launch: JvmLaunch,
            commands: ParcelFileDescriptor,
            events: ParcelFileDescriptor
        ): HostStarted {
            claim()
            val heapLimitMb = launch.heapLimitMb
            val options = listOfNotNull(
                "-Xmx${heapLimitMb}m",
                // The heap starts this large rather than at the 64th of the RAM HotSpot starts with, so
                // the collector runs less often while the heap grows to what a large run needs. That
                // matters most on slow devices, and for the serial collector of a 32-bit JVM, which
                // has no concurrent phases and stops the run for each collection
                "-Xms${minOf(INITIAL_HEAP_MB, heapLimitMb)}m",
                // A cap on the code cache, reserved apart from the heap in address space a 32-bit heap needs more
                "-XX:ReservedCodeCacheSize=${CODE_CACHE_32_BIT_MB}m".takeUnless { Process.is64Bit() }
            ) + launch.options // Last, so a flag the app gives again wins

            val jvmPid = startProcess(launch.command + options + launch.mainClass, commands, events)
            return HostStarted(Process.myPid(), heapLimitMb, jvmPid)
        }

        @Synchronized
        override fun startArt(commands: ParcelFileDescriptor, events: ParcelFileDescriptor): HostStarted {
            claim()
            val (commandsFile, eventsFile) = hostArgs(commands, events)
            thread(name = "PatcherHost") {
                // ART reports no time spent collecting, and throws once its heap is spent anyway
                serveRun(commandsFile, eventsFile, null) { PatchBundle.Loader.patches(PatchBundle(it.path)) }
            }
            return HostStarted(Process.myPid(), heapLimitMebibytes())
        }
    }

    private fun claim() {
        check(!started) { "The patcher runs once per process" }
        started = true
    }

    // Owned by the host from here on, which uses them for as long as the process lives
    private fun hostArgs(commands: ParcelFileDescriptor, events: ParcelFileDescriptor) =
        listOf(commands, events).map { "/proc/self/fd/${it.detachFd()}" }

    /**
     * Starts the JVM as a process of its own with [command], handing it the pipes of the run under
     * the numbers it is told, and returns its pid. Its output goes to the log, and this process
     * ends with it, see [endWith].
     *
     * The process is started and waited for on a thread of its own, which lives as long as it
     * does, as the process is killed once the thread that started it ends.
     */
    private fun startProcess(command: List<String>, commands: ParcelFileDescriptor, events: ParcelFileDescriptor): Int {
        // Kept to write the end to, as the app only learns of it once every copy of the pipe is closed
        val endReport = events.dup()
        val (output, outputSink) = ParcelFileDescriptor.createPipe()
        val fds = intArrayOf(commands.fd, events.fd)
        val args = command + fds.map { "/proc/self/fd/$it" }
        val pid = CompletableFuture<Int>()
        val jvmOutput = JvmOutput(output)

        thread(name = "PatcherJvm") {
            val started = try {
                EmbeddedJvm.spawn(args, fds, outputSink.fd)
            } catch (e: Throwable) {
                endReport.close()
                pid.completeExceptionally(e)
                return@thread
            }
            pid.complete(started)
            if (started <= 0) {
                endReport.close()
                return@thread
            }
            endWith(endReport, EmbeddedJvm.waitProcess(started), jvmOutput)
        }
        val started = try {
            pid.get()
        } catch (e: ExecutionException) {
            throw IllegalStateException("The patcher JVM did not start", e.cause)
        } finally {
            // The process has its copies, and the pipes end with it
            outputSink.close()
            commands.close()
            events.close()
        }
        check(started > 0) { "The patcher JVM did not start (errno ${-started})" }
        return started
    }

    /**
     * Ends this process with the JVM, which ended with [status], as the app tells the end of a run
     * by the end of the process. The host reports its own end and exits right after. A crash, a
     * kill or a JVM that never got as far as running leaves that to the exit status and the
     * [output] of the JVM, so those are reported here.
     */
    private fun endWith(endReport: ParcelFileDescriptor, status: Int, output: JvmOutput): Nothing {
        if (status != 0) Log.w(JVM_LOG_TAG, "The patcher JVM ended with $status")
        val ended = HostEvent.JvmEnded(status, output.tail())
        ParcelFileDescriptor.AutoCloseOutputStream(endReport).bufferedWriter().use { writer ->
            // The app may have stopped reading, as it does once the run finished
            runCatching {
                writer.write(HostEvent.encode(ended))
                writer.newLine()
            }
        }
        exitProcess(status)
    }

    /**
     * Logs what the JVM writes to its standard streams, which is little once the host runs, as it
     * takes them over, and keeps the last lines for [endWith].
     */
    private class JvmOutput(source: ParcelFileDescriptor) {
        private val lines = ArrayDeque<String>()

        private val reader = thread(isDaemon = true, name = "PatcherJvmOutput") {
            ParcelFileDescriptor.AutoCloseInputStream(source).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    Log.i(JVM_LOG_TAG, line)
                    synchronized(this) {
                        this.lines.addLast(line)
                        if (this.lines.size > TAIL_LINES) this.lines.removeFirst()
                    }
                }
            }
        }

        /** The last lines, once the JVM ended and what it wrote last was read. */
        fun tail(): List<String> {
            reader.join(DRAIN_TIMEOUT_MS)
            return synchronized(this) { lines.toList() }
        }

        private companion object {
            // Enough for the error a JVM that could not start prints, and the summary of a crash
            const val TAIL_LINES = 30
            const val DRAIN_TIMEOUT_MS = 1000L
        }
    }

    // Started once the run begins, as some systems end a process that holds no foreground service
    // of its own as soon as the app leaves the screen, whichever process it is busy for
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val notification = requireNotNull(
                IntentCompat.getParcelableExtra(intent, EXTRA_NOTIFICATION, Notification::class.java)
            )
            try {
                ServiceCompat.startForeground(
                    this,
                    intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
                    notification,
                    intent.getIntExtra(EXTRA_SERVICE_TYPE, 0)
                )
            } catch (e: RuntimeException) {
                // Refused where the system withholds the service type's permission or a start from
                // the background, as it does for the app's own foreground service then. The run goes
                // on bound alone, and the start is dropped so the system does not wait for the foreground
                Log.w(LOG_TAG, "Could not hold the patcher process in the foreground", e)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent) = binder

    // The run is over or was given up on, and a host that is still running cannot be stopped otherwise
    override fun onUnbind(intent: Intent): Boolean {
        Process.killProcess(Process.myPid())
        return false
    }

    companion object {
        private const val CODE_CACHE_32_BIT_MB = 64

        // What the output of the JVM is logged under
        private const val JVM_LOG_TAG = "EmbeddedJvm"

        // The heap the JVM starts with, or its limit if that is smaller. A gigabyte made no
        // difference on a recent 64-bit phone, so this stays small enough to cost little memory
        private const val INITIAL_HEAP_MB = 512

        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_NOTIFICATION = "notification"
        private const val EXTRA_SERVICE_TYPE = "service_type"

        /**
         * Starts the service in the foreground under [foreground], the notification the app shows
         * for the run, so both share the one notification.
         */
        fun foregroundIntent(context: Context, foreground: ForegroundInfo): Intent =
            Intent(context, PatcherService::class.java)
                .putExtra(EXTRA_NOTIFICATION_ID, foreground.notificationId)
                .putExtra(EXTRA_NOTIFICATION, foreground.notification)
                .putExtra(EXTRA_SERVICE_TYPE, foreground.foregroundServiceType)
    }
}

/**
 * A host [PatcherService] started.
 *
 * @param pid The process of the service, which the system records the end of.
 * @param heapLimitMb The heap it got.
 * @param jvmPid The process the JVM runs in, the service's own on ART.
 */
@Parcelize
data class HostStarted(val pid: Int, val heapLimitMb: Int, val jvmPid: Int = pid) : Parcelable
