/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** The cases come from ML Kit translating patch descriptions of Morphe Patches into Ukrainian. */
class RestoreNameCasingTest {
    @Test
    fun `names the translator recased take their source spelling again`() {
        assertEquals(
            "Змінює ім'я програми Reddit на ім'я, вказане в параметрах патчів.",
            restoreNameCasing(
                source = "Changes the Reddit app name to the name specified in patch options.",
                translated = "Змінює ім'я програми RedDit на ім'я, вказане в параметрах патчів."
            )
        )
        assertEquals(
            "Додає можливість замінити Reddit Sans / Roboto з файлом TTF або OTF.",
            restoreNameCasing(
                source = "Adds an option to replace Reddit Sans / Roboto with a custom TTF or OTF font file.",
                translated = "Додає можливість замінити REDDIT SANS / ROBOTO з файлом TTF або OTF."
            )
        )
    }

    @Test
    fun `lowercase source words leave the translation as it is`() {
        // "app" is lowercase in the source, so a language that capitalizes it keeps its own spelling
        assertEquals(
            "Die App startet schneller",
            restoreNameCasing(source = "Start the app faster", translated = "Die App startet schneller")
        )
    }

    @Test
    fun `a name inside a sentence is restored wherever it lands`() {
        assertEquals(
            "Приховує Shorts у стрічці",
            restoreNameCasing(source = "Hides Shorts in the feed", translated = "Приховує SHORTS у стрічці")
        )
    }

    @Test
    fun `translated words are never touched`() {
        assertEquals(
            "Приховує рекламу",
            restoreNameCasing(source = "Hides ads", translated = "Приховує рекламу")
        )
    }
}
