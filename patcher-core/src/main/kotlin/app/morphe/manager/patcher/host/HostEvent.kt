/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.host

import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.runtime.isHeapExhausted
import app.morphe.manager.patcher.split.SplitPreparationEvent
import app.morphe.patcher.PatcherTask
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the patcher host sends back to the app, one per line. Both ends are built from this very
 * code, so the format never has to outlive a release.
 */
@Serializable
sealed interface HostEvent {
    /** The first event of a host, which tells a VM that started from one that ended on the way. */
    @Serializable
    @SerialName("started")
    data object Started : HostEvent

    /**
     * The end of the process of an embedded JVM, which the service it runs for reports, as the
     * host cannot report its own crash or kill.
     *
     * @param status The exit code, or 128 plus the signal that ended the process.
     * @param output The last lines the JVM wrote to its standard streams, which the host only takes
     *               over once it runs, so they tell why a JVM ended before that.
     */
    @Serializable
    @SerialName("jvm_ended")
    data class JvmEnded(val status: Int, val output: List<String>) : HostEvent {
        /** The signal that ended the process, or null if it exited. */
        val signal get() = (status - SIGNAL_EXIT_BASE).takeIf { status > SIGNAL_EXIT_BASE }

        /** Whether the JVM crashed rather than exited or was killed, which disabling it may get past. */
        val crashed get() = signal in CRASH_SIGNALS

        fun describe() = when (val signal = signal) {
            SIGSYS -> "made a system call Android blocks (signal $signal)"
            null -> "exited with $status"
            in CRASH_SIGNALS -> "crashed with signal $signal"
            else -> "was killed by signal $signal"
        }

        companion object {
            /** An exit status above this is 128 plus the signal that ended the process, as a shell reports it. */
            const val SIGNAL_EXIT_BASE = 128

            private const val SIGSYS = 31

            // SIGILL, SIGABRT, SIGBUS, SIGFPE, SIGSEGV and SIGSYS
            private val CRASH_SIGNALS = setOf(4, 6, 7, 8, 11, SIGSYS)
        }
    }

    @Serializable
    @SerialName("log")
    data class Log(val level: LogLevel, val message: String) : HostEvent

    /** [PatchingListener.stepCompleted]. */
    @Serializable
    @SerialName("step")
    data object StepCompleted : HostEvent

    @Serializable
    @SerialName("split")
    data class SplitPreparation(val event: SplitPreparationEvent) : HostEvent

    @Serializable
    @SerialName("patches")
    data object PatchesStarted : HostEvent

    /** [PatchingListener.patchExecuting]. */
    @Serializable
    @SerialName("patch_executing")
    data class PatchExecuting(val name: String) : HostEvent

    @Serializable
    @SerialName("patch")
    data class PatchSucceeded(val name: String) : HostEvent

    /** [PatchingListener.taskProgress]. */
    @Serializable
    @SerialName("task")
    data class TaskProgress(val task: PatcherTask, val done: Long, val total: Long) : HostEvent

    @Serializable
    @SerialName("patch_failed")
    data class PatchFailed(val name: String?, val stackTrace: String) : HostEvent

    @Serializable
    @SerialName("patches_done")
    data class PatchesFinished(val count: Int) : HostEvent

    /** The last event of a run, with what it failed on, if it did. */
    @Serializable
    @SerialName("finished")
    data class Finished(val failure: Failure?) : HostEvent

    /**
     * @param outOfMemory Whether the run filled the heap, which no smaller one could have avoided.
     */
    @Serializable
    data class Failure(val stackTrace: String, val outOfMemory: Boolean) {
        constructor(error: Throwable) : this(
            error.stackTraceToString(),
            error.isHeapExhausted()
        )
    }

    companion object {
        fun encode(event: HostEvent): String = Json.encodeToString(serializer(), event)

        fun decode(line: String): HostEvent = Json.decodeFromString(serializer(), line)
    }
}
