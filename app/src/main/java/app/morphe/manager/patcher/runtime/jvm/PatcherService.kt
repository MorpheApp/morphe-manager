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
import kotlin.concurrent.thread

/**
 * Hosts the JVM that patches, in a process of its own declared for it. The system accounts for
 * that process like any other part of the app, and the JVM keeps its heap in native memory, out
 * of reach of the limit ART puts on the app.
 *
 * Bound by [app.morphe.manager.patcher.runtime.PatcherJvms] for a single run. The JVM is started
 * first and waits for the run, so it can be started before the run is known. The process ends with
 * the run, which is also the only way to stop the JVM, and the next run starts in a fresh one.
 */
class PatcherService : Service() {
    // The write end of the pipe the JVM waits on for the run, set once the JVM started
    private var commands: ParcelFileDescriptor? = null

    // Kept until the process ends, as the host opens the pipe again through the fd
    private var events: ParcelFileDescriptor? = null

    private val binder = object : IPatcherService.Stub() {
        @Synchronized
        override fun prepare(launch: JvmLaunch, bundles: Array<String>): Int {
            check(commands == null) { "The patcher JVM runs once per process" }

            val (commandsSource, commandsSink) = ParcelFileDescriptor.createPipe()
            commands = commandsSink
            thread(name = "PatcherJvm") {
                val code = commandsSource.use {
                    EmbeddedJvm.run(launch, listOf("/proc/self/fd/${commandsSource.fd}") + bundles)
                }
                // The host ends the process once the run is over, so getting here means it never ran
                Log.e(tag, "Patcher JVM returned $code")
                Process.killProcess(Process.myPid())
            }

            return Process.myPid()
        }

        @Synchronized
        override fun run(requestFile: String, events: ParcelFileDescriptor) {
            val commands = checkNotNull(commands) { "The patcher JVM was not started" }
            check(this@PatcherService.events == null) { "The patcher JVM runs once per process" }

            this@PatcherService.events = events
            ParcelFileDescriptor.AutoCloseOutputStream(commands).bufferedWriter().use { writer ->
                writer.write("$requestFile\n/proc/self/fd/${events.fd}\n")
            }
        }
    }

    override fun onBind(intent: Intent) = binder

    // The run is over or was abandoned, and a JVM that is still running cannot be stopped otherwise
    override fun onUnbind(intent: Intent): Boolean {
        Process.killProcess(Process.myPid())
        return false
    }
}
