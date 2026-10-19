/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm

import android.app.Service
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import app.morphe.manager.util.tag
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Hosts the JVM that patches, in a process of its own declared for it. The system accounts for
 * that process like any other part of the app, and the JVM keeps its heap in native memory, out
 * of reach of the limit ART puts on the app.
 *
 * Bound by [app.morphe.manager.patcher.runtime.JvmRuntime] for a single run. The process ends with the run, which is also the only
 * way to stop the JVM, and the next run starts in a fresh one.
 */
class PatcherService : Service() {
    private val started = AtomicBoolean()

    private val binder = object : IPatcherService.Stub() {
        override fun start(launch: JvmLaunch, requestFile: String, events: ParcelFileDescriptor): Int {
            check(started.compareAndSet(false, true)) { "The patcher JVM runs once per process" }

            thread(name = "PatcherJvm") {
                // The host opens the pipe again through the fd, which has to stay open until it did
                val code = events.use {
                    EmbeddedJvm.run(launch, listOf(requestFile, "/proc/self/fd/${events.fd}"))
                }
                // The host ends the process once the run is over, so getting here means it never ran
                Log.e(tag, "Patcher JVM returned $code")
                Process.killProcess(Process.myPid())
            }

            return Process.myPid()
        }
    }

    override fun onBind(intent: Intent) = binder

    // The run is over or was abandoned, and a JVM that is still running cannot be stopped otherwise
    override fun onUnbind(intent: Intent): Boolean {
        Process.killProcess(Process.myPid())
        return false
    }
}
