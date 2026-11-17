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
import app.morphe.manager.patcher.host.serveRun
import app.morphe.manager.patcher.patch.PatchBundle
import app.morphe.manager.patcher.runtime.jvm.EmbeddedJvm
import app.morphe.manager.patcher.runtime.jvm.JvmLaunch
import app.morphe.manager.util.LOG_TAG
import kotlinx.parcelize.Parcelize
import kotlin.concurrent.thread

/**
 * Hosts a run in a process of its own declared for it, in the JVM the app embeds or on the ART of
 * the process. When the system runs short of memory for a large run, or the heap of the run fills
 * up, it is this process that ends, and the app is left to report it.
 *
 * Bound by [PatcherProcesses] for a single run. The host is started first and waits for the run,
 * so it can be started before the run is known. The process ends with the run, which is also the
 * only way to stop a JVM, and the next run starts in a fresh one.
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
            val heapLimitMb = fittedHeapLimitMb(launch.heapLimitMb)
            check(heapLimitMb >= HEAP_LIMIT_USABLE_MB) { "The patcher JVM would get a heap of only ${heapLimitMb}MB" }

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

            val code = EmbeddedJvm.start(launch, options, hostArgs(commands, events))
            check(code == 0) { "The patcher JVM did not start ($code)" }
            return HostStarted(Process.myPid(), heapLimitMb)
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
     * [limitMb], or less in a 32-bit process: no more than its address space still has in one
     * piece, which a fresh process has more of than the app's.
     */
    private fun fittedHeapLimitMb(limitMb: Int): Int {
        if (Process.is64Bit()) return limitMb
        val reservable = EmbeddedJvm.largestReservationMb(limitMb + RESERVATION_MARGIN_32_BIT_MB)
        return minOf(limitMb, reservable - RESERVATION_MARGIN_32_BIT_MB)
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

        // Address space left beside the heap for the code cache and metaspace, reserved apart from it
        private const val RESERVATION_MARGIN_32_BIT_MB = CODE_CACHE_32_BIT_MB + 64

        // A heap below this is no bigger than ART gives a 32-bit app, so ART patches instead
        private const val HEAP_LIMIT_USABLE_MB = 512

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
 * @param pid The process it runs in.
 * @param heapLimitMb The heap it got.
 */
@Parcelize
data class HostStarted(val pid: Int, val heapLimitMb: Int) : Parcelable
