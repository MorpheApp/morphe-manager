/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val YOUTUBE = "com.google.android.youtube"

class HomeAppSlotsTest {
    private fun record(currentPackageName: String, isClone: Boolean = false) = InstalledApp(
        currentPackageName = currentPackageName,
        originalPackageName = YOUTUBE,
        version = "19.16.39",
        installType = InstallType.DEFAULT,
        isClone = isClone
    )

    private fun clone(currentPackageName: String) = record(currentPackageName, isClone = true)

    @Test
    fun `an app nothing was patched from shows a single empty card`() {
        val slots = homeAppSlots(YOUTUBE, emptyList())

        assertEquals(listOf(HomeAppSlot(YOUTUBE, YOUTUBE, null, isClone = false)), slots)
    }

    @Test
    fun `an unrenamed install takes the app's own card`() {
        val installed = record(YOUTUBE)

        assertEquals(
            listOf(HomeAppSlot(YOUTUBE, YOUTUBE, installed, isClone = false)),
            homeAppSlots(YOUTUBE, listOf(installed))
        )
    }

    @Test
    fun `an install the patches renamed still takes the app's own card`() {
        val installed = record("app.morphe.android.youtube")

        assertEquals(
            listOf(HomeAppSlot(YOUTUBE, YOUTUBE, installed, isClone = false)),
            homeAppSlots(YOUTUBE, listOf(installed))
        )
    }

    @Test
    fun `every clone gets a card of its own beside the app`() {
        val second = clone("$YOUTUBE.morphe2")
        val first = clone("$YOUTUBE.morphe")
        val own = record(YOUTUBE)

        assertEquals(
            listOf(
                HomeAppSlot(YOUTUBE, YOUTUBE, own, isClone = false),
                HomeAppSlot("$YOUTUBE.morphe", YOUTUBE, first, isClone = true),
                HomeAppSlot("$YOUTUBE.morphe2", YOUTUBE, second, isClone = true)
            ),
            homeAppSlots(YOUTUBE, listOf(second, first, own))
        )
    }

    @Test
    fun `a clone beside a renamed install leaves the app's card to the install`() {
        val renamed = record("app.morphe.android.youtube")
        val copy = clone("$YOUTUBE.morphe")

        assertEquals(
            listOf(
                HomeAppSlot(YOUTUBE, YOUTUBE, renamed, isClone = false),
                HomeAppSlot("$YOUTUBE.morphe", YOUTUBE, copy, isClone = true)
            ),
            homeAppSlots(YOUTUBE, listOf(copy, renamed))
        )
    }

    @Test
    fun `a second install of the app itself keeps a card without counting as a copy`() {
        val mounted = record(YOUTUBE)
        val renamed = record("app.morphe.android.youtube")

        val slots = homeAppSlots(YOUTUBE, listOf(renamed, mounted))

        assertEquals(
            listOf(
                HomeAppSlot(YOUTUBE, YOUTUBE, mounted, isClone = false),
                HomeAppSlot("app.morphe.android.youtube", YOUTUBE, renamed, isClone = false)
            ),
            slots
        )
        assertTrue(slots.none { it.isClone })
    }

    @Test
    fun `apps only records name get cards after the listed ones`() {
        val other = InstalledApp(
            currentPackageName = "com.example.other",
            originalPackageName = "com.example.other",
            version = "1.0",
            installType = InstallType.DEFAULT
        )
        val own = record(YOUTUBE)

        assertEquals(
            listOf(
                HomeAppSlot(YOUTUBE, YOUTUBE, own, isClone = false),
                HomeAppSlot("com.example.other", "com.example.other", other, isClone = false)
            ),
            homeAppSlots(listOf(YOUTUBE), listOf(other, own))
        )
    }

    @Test
    fun `a build patched from a clone is shown without taking the clone's card`() {
        val copy = clone("$YOUTUBE.morphe")
        val repatched = InstalledApp(
            currentPackageName = "$YOUTUBE.morphe.morphe",
            originalPackageName = "$YOUTUBE.morphe",
            version = "19.16.39",
            installType = InstallType.DEFAULT,
            isClone = true
        )

        val slots = homeAppSlots(listOf(YOUTUBE), listOf(repatched, copy))

        assertEquals(
            listOf(
                HomeAppSlot(YOUTUBE, YOUTUBE, null, isClone = false),
                HomeAppSlot("$YOUTUBE.morphe", YOUTUBE, copy, isClone = true),
                HomeAppSlot("$YOUTUBE.morphe.morphe", "$YOUTUBE.morphe", repatched, isClone = true)
            ),
            slots
        )
        assertEquals(slots.size, slots.distinctBy { it.id }.size)
    }

    @Test
    fun `a listed package an install was renamed to gets no card beside the install`() {
        val renamed = clone("app.morphe.android.youtube")

        val slots = homeAppSlots(listOf(YOUTUBE, "app.morphe.android.youtube"), listOf(renamed))

        assertEquals(
            listOf(
                HomeAppSlot(YOUTUBE, YOUTUBE, null, isClone = false),
                HomeAppSlot("app.morphe.android.youtube", YOUTUBE, renamed, isClone = true)
            ),
            slots
        )
    }

    @Test
    fun `the app keeps a card to be cloned from again once only clones are left`() {
        val copy = clone("$YOUTUBE.morphe")

        val slots = homeAppSlots(YOUTUBE, listOf(copy))

        assertEquals(2, slots.size)
        assertNull(slots.first().installedApp)
        assertEquals(YOUTUBE, slots.first().id)
        assertFalse(slots.first().isClone)
    }
}
