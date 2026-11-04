/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime

import app.morphe.manager.patcher.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps track of the patch a run is busy with and names it once it runs unusually long, as the
 * run reports a patch only after it ends. Watched from the app, whose heap is not the one a stuck
 * patch may have filled.
 *
 * @param scope Where the reports are timed, which must not outlive the run.
 */
internal class RunningPatchWatch(private val scope: CoroutineScope, private val logger: Logger) {
    /** The patch running right now, or null outside of one. */
    var current: String? = null
        private set

    private var reporting: Job? = null

    fun started(name: String) {
        stopped()
        current = name
        reporting = scope.launch {
            // Each report waits as long as the patch has run, so one stuck for minutes adds a
            // handful of lines rather than hundreds
            var running = FIRST_REPORT
            delay(running)
            while (true) {
                logger.warn("$name has been running for ${running.inWholeSeconds}s")
                delay(running)
                running *= 2
            }
        }
    }

    fun stopped() {
        reporting?.cancel()
        reporting = null
        current = null
    }

    private companion object {
        val FIRST_REPORT = 30.seconds
    }
}
