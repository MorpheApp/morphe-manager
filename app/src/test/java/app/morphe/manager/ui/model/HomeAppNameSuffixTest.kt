/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val TELEGRAM = "org.telegram.messenger"
private const val TELEGRAM_WEB = "org.telegram.messenger.web"

class HomeAppNameSuffixTest {
    private fun item(packageName: String, displayName: String, id: String = packageName) =
        homeAppItem(id = id, packageName = packageName, displayName = displayName)

    @Test
    fun `the package another only extends gets no suffix`() {
        assertNull(packageNameSuffix(TELEGRAM, listOf(TELEGRAM_WEB)))
        assertEquals("web", packageNameSuffix(TELEGRAM_WEB, listOf(TELEGRAM)))
    }

    @Test
    fun `packages apart in the middle both get the segment that differs`() {
        assertEquals("foo", packageNameSuffix("com.foo.app", listOf("com.bar.app")))
        assertEquals("bar", packageNameSuffix("com.bar.app", listOf("com.foo.app")))
    }

    @Test
    fun `only cards sharing a name with another app are suffixed`() {
        val items = listOf(
            item(TELEGRAM, "Telegram"),
            item(TELEGRAM_WEB, "Telegram"),
            item("com.google.android.youtube", "YouTube")
        ).withNameSuffixes()

        assertEquals(listOf(null, "web", null), items.map { it.nameSuffix })
    }

    @Test
    fun `clones of one app are not told apart by a suffix`() {
        val items = listOf(
            item(TELEGRAM, "Telegram"),
            item(TELEGRAM, "Telegram", id = "$TELEGRAM.clone")
        ).withNameSuffixes()

        assertEquals(listOf(null, null), items.map { it.nameSuffix })
    }
}
