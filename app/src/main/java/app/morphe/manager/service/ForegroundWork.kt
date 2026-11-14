/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.service

import app.morphe.manager.notification.OngoingContent

/**
 * Work that must not die with the app once it leaves the screen, and the one ongoing notification
 * that shows it: a patch run up to the install it hands over to, a batch run between the runs of
 * its apps, the run itself, or an install.
 *
 * Some systems end a process without a foreground service as soon as the app leaves the screen,
 * and refuse one started from the background. A [Hold] taken on screen keeps the app in the
 * foreground until the last one is closed, and lets a worker started meanwhile take its own
 * foreground as well.
 *
 * The notification shows the newest work still under way. Work started inside other work, as a
 * run inside a batch run, shows over it and hands the notification back once closed, so nothing
 * that has finished is left on screen.
 */
class ForegroundWork internal constructor(private val host: ForegroundHost) {
    // Oldest first, so the last one is what the notification shows
    private val holds = mutableListOf<Hold>()

    /** Holds the foreground for work shown as [content], until the [Hold] is closed. */
    fun hold(content: OngoingContent): Hold = synchronized(this) {
        Hold(content).also {
            holds += it
            host.show(content)
        }
    }

    /** A hold on the foreground for one piece of work. */
    inner class Hold internal constructor(private var content: OngoingContent) : AutoCloseable {
        /** Shows the work as [content] from now on, at once while it is the newest. */
        fun update(content: OngoingContent) {
            synchronized(this@ForegroundWork) {
                this.content = content
                if (holds.lastOrNull() === this) host.show(content)
            }
        }

        /** Ends the hold. A canceled run may get here twice, and only the first call counts. */
        override fun close() {
            synchronized(this@ForegroundWork) {
                val newest = holds.lastOrNull() === this
                if (!holds.remove(this)) return
                when {
                    holds.isEmpty() -> host.release()
                    newest -> host.show(holds.last().content)
                }
            }
        }
    }
}

/** What [ForegroundWork] holds the foreground through, a service outside tests. */
internal interface ForegroundHost {
    /** Takes the foreground or keeps it, showing [content]. */
    fun show(content: OngoingContent)

    /** Lets the foreground go, and the notification with it. */
    fun release()
}
