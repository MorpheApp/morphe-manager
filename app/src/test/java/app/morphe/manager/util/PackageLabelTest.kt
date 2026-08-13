/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertEquals

class PackageLabelTest {
    @Test
    fun `brand containing a dot is kept whole`() {
        assertEquals("Mapy.com", cleanPackageLabel("Mapy.com", "cz.seznam.mapy"))
        assertEquals("Yandex.Music", cleanPackageLabel("Yandex.Music", "ru.yandex.music"))
    }

    @Test
    fun `label that is the package name is reduced to its last segment`() {
        assertEquals("mapy", cleanPackageLabel("cz.seznam.mapy", "cz.seznam.mapy"))
    }

    @Test
    fun `launcher class label drops its package and Application suffix`() {
        assertEquals("Main", cleanPackageLabel("com.example.app.MainApplication", "com.example.app"))
    }

    @Test
    fun `package-shaped label is reduced even when the record package differs`() {
        assertEquals("player", cleanPackageLabel("org.videolan.player", "com.example.other"))
    }

    @Test
    fun `plain names are untouched`() {
        assertEquals("SoundCloud", cleanPackageLabel("SoundCloud", "com.soundcloud.android"))
        assertEquals("YouTube Morphe", cleanPackageLabel("  YouTube Morphe  ", "app.morphe.android.youtube"))
    }

    @Test
    fun `dotted initialisms survive`() {
        assertEquals("N.O.V.A.", cleanPackageLabel("N.O.V.A.", "com.gameloft.android.nova"))
    }

    @Test
    fun `blank label stays blank`() {
        assertEquals("", cleanPackageLabel("   ", "com.example.app"))
    }
}
