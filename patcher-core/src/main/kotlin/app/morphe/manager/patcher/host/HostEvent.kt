/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.host

import app.morphe.manager.patcher.PatchingListener
import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.split.SplitPreparationEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the patcher host sends back to the app, one per line. Both ends are built from this very
 * code, so the format never has to outlive a release.
 */
@Serializable
sealed interface HostEvent {
    /** The JVM is up and runs the host, so whatever ends the run from here on is no failure to start. */
    @Serializable
    @SerialName("started")
    data object Started : HostEvent

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

    @Serializable
    @SerialName("patch")
    data class PatchSucceeded(val name: String) : HostEvent

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
            generateSequence(error) { it.cause }.any { it is OutOfMemoryError }
        )
    }

    companion object {
        fun encode(event: HostEvent): String = Json.encodeToString(serializer(), event)

        fun decode(line: String): HostEvent = Json.decodeFromString(serializer(), line)
    }
}
