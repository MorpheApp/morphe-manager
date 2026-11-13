/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.service

import app.morphe.manager.notification.OngoingContent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The notification has to show work that is still under way, and the foreground has to go with
 * the last of it. Anything else leaves an ongoing notification behind that the user cannot get rid
 * of, as an install that never finished or a run that ended long ago.
 */
class ForegroundWorkTest {
    private val events = mutableListOf<String>()
    private val work = ForegroundWork(object : ForegroundHost {
        override fun show(content: OngoingContent) {
            events += "show ${content.title}"
        }

        override fun release() {
            events += "release"
        }
    })

    private fun content(title: String) = OngoingContent(title)

    @Test
    fun `newest work is shown`() {
        work.hold(content("batch"))
        work.hold(content("run"))

        assertEquals(listOf("show batch", "show run"), events)
    }

    @Test
    fun `closing the newest work hands the notification back to the work around it`() {
        work.hold(content("batch"))
        val run = work.hold(content("run"))

        run.close()

        assertEquals("show batch", events.last())
    }

    @Test
    fun `closing older work leaves the newest shown`() {
        val run = work.hold(content("run"))
        work.hold(content("install"))
        events.clear()

        run.close()

        assertEquals(emptyList(), events)
    }

    @Test
    fun `older work updated meanwhile is shown as updated once it is the newest again`() {
        val batch = work.hold(content("batch"))
        val run = work.hold(content("run"))
        events.clear()

        batch.update(content("preparing next"))
        run.close()

        assertEquals(listOf("show preparing next"), events)
    }

    @Test
    fun `newest work is shown as updated at once`() {
        val run = work.hold(content("loading"))

        run.update(content("applying"))

        assertEquals("show applying", events.last())
    }

    @Test
    fun `foreground goes with the last work once however often it is closed`() {
        val install = work.hold(content("install"))

        install.close()
        install.close()

        assertEquals(listOf("show install", "release"), events)
    }

    @Test
    fun `closed work is neither shown nor handed the notification`() {
        val run = work.hold(content("run"))
        val install = work.hold(content("install"))
        run.close()
        events.clear()

        run.update(content("late progress"))
        install.close()

        assertEquals(listOf("release"), events)
    }

    @Test
    fun `work after the foreground went takes it again`() {
        work.hold(content("run")).close()
        events.clear()

        work.hold(content("install"))

        assertEquals(listOf("show install"), events)
    }
}
