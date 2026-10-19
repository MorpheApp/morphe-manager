/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import app.morphe.manager.BuildConfig
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.runtime.jvm.IPatcherService
import app.morphe.manager.patcher.runtime.jvm.JvmInstallation
import app.morphe.manager.patcher.runtime.jvm.JvmLaunch
import app.morphe.manager.patcher.runtime.jvm.PatcherService
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.tag
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Starts the patcher JVM, ahead of a run where it can. Starting it and loading the patch bundles
 * takes a second or two, which is spent while the user still picks what to patch rather than once
 * patching starts.
 */
class PatcherJvms(
    private val app: Application,
    private val fs: Filesystem,
    private val installation: JvmInstallation,
    private val patchBundleRepository: PatchBundleRepository,
    private val prefs: PreferencesManager,
    private val scope: AppCoroutineScope
) {
    private val lock = Mutex()
    private var ahead: PatcherJvm? = null
    private var expiry: Job? = null

    private suspend fun isEnabled() = installation.isAvailable && prefs.useJvmRuntime.get()

    /**
     * Unpacks the JVM in the background, so the first run after the app was installed or updated
     * does not wait for it.
     */
    fun unpackAhead() = scope.launch {
        if (!isEnabled()) return@launch
        try {
            installation.install()
        } catch (e: JvmUnavailableException) {
            Log.w(tag, "Could not unpack the patcher JVM ahead of a run", e)
        }
    }

    /**
     * Starts a JVM for the run the user is setting up, which loads the enabled patch bundles while it
     * waits. One that is not used within [AHEAD_LIFETIME] is ended, as it holds memory for nothing.
     */
    fun startAhead() = scope.launch {
        if (!isEnabled()) return@launch
        lock.withLock {
            if (ahead?.isAlive == true) return@withLock
            ahead?.close()
            ahead = null

            ahead = try {
                start(enabledBundleFiles())
            } catch (e: JvmUnavailableException) {
                Log.w(tag, "Could not start the patcher JVM ahead of a run", e)
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
     * A JVM for a run that patches with [bundles]: the one started ahead while it is still alive,
     * otherwise a new one. Android may end a waiting one to free memory.
     */
    suspend fun take(bundles: List<String>): PatcherJvm = lock.withLock {
        expiry?.cancel()
        val started = ahead
        ahead = null
        if (started != null && started.isAlive) return@withLock started
        started?.close()
        start(bundles)
    }

    private suspend fun enabledBundleFiles(): List<String> {
        val enabled = patchBundleRepository.enabledBundlesInfoFlow.first().keys
        return patchBundleRepository.bundles.first().filterKeys { it in enabled }.values.map { it.patchesJar }
    }

    private suspend fun start(bundles: List<String>): PatcherJvm {
        val installed = installation.install()
        val heapLimitMb = JvmRuntime.heapLimitMb(app)
        val errorFile = fs.tempDir.resolve(ERROR_FILE).apply { delete() }
        val debugOptions = debugOptions()

        val connection = PatcherConnection.connect(app)
        val pid = try {
            connection.service.prepare(launch(installed, heapLimitMb, errorFile, debugOptions), bundles.toTypedArray())
        } catch (e: Exception) {
            connection.close()
            throw e
        }
        return PatcherJvm(connection, pid, heapLimitMb, errorFile, debugOptions)
    }

    private fun launch(
        installed: JvmInstallation.Installed,
        heapLimitMb: Int,
        errorFile: File,
        debugOptions: List<String>
    ) = JvmLaunch(
        libjvm = installed.libjvm.path,
        preload = installed.preload,
        options = listOf(
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
            // 8 byte object headers instead of 12. A run keeps millions of small objects alive, and
            // smaller ones leave the collector and the caches less to do
            "-XX:+UseCompactObjectHeaders",
            // The working directory of an app is not writable, which is where the report would go
            "-XX:ErrorFile=$errorFile"
        ) + debugOptions, // Last, so a flag given again there wins
        mainClass = HOST_MAIN_CLASS
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
     * A started JVM waiting for its run. Closing it ends its process.
     *
     * @param debugOptions The options a debug build added, for the run to log.
     */
    class PatcherJvm internal constructor(
        private val connection: PatcherConnection,
        val pid: Int,
        val heapLimitMb: Int,
        val errorFile: File,
        val debugOptions: List<String>
    ) : Closeable {
        val isAlive get() = connection.service.asBinder().isBinderAlive

        /** Hands over the run, whose events go to [events]. */
        fun run(requestFile: String, events: ParcelFileDescriptor) = try {
            connection.service.run(requestFile, events)
        } catch (e: DeadObjectException) {
            throw JvmUnavailableException("The patcher JVM ended before the run", e)
        }

        override fun close() = connection.close()
    }

    /** A binding to [PatcherService] for a single run, which ends the service's process once closed. */
    internal class PatcherConnection private constructor(private val context: Context) : ServiceConnection, Closeable {
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
            continuation?.resumeWithException(JvmUnavailableException("Patcher service $reason"))
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
                    throw JvmUnavailableException("Patcher service did not connect within $CONNECT_TIMEOUT")
                }
                return connection.apply { this.service = service }
            }
        }
    }

    private companion object {
        const val HOST_MAIN_CLASS = "app/morphe/manager/patcher/host/HostMainKt"
        const val DEBUG_OPTIONS_FILE = "jvm-options.txt"
        const val ERROR_FILE = "patcher-jvm-error.log"

        // Generous because a slow device on a cold start can take many seconds to start a process
        val CONNECT_TIMEOUT = 30.seconds

        // Long enough to pick an APK and the patches, short enough not to hold memory long for nothing
        val AHEAD_LIFETIME = 5.minutes
    }
}
