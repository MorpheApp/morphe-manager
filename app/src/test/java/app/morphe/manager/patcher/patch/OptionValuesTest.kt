/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import app.morphe.manager.patcher.logger.LogLevel
import app.morphe.manager.patcher.logger.Logger
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelectionUtils.sanitizeForPatcher
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.patch.longOption
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.patch.stringsOption
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A value that reaches the patcher as the wrong type aborts the run, not just the one patch. */
class OptionValuesTest {
    @Test
    fun `int option takes the numeric types the manager stores`() {
        val type = typeOf<Int>()

        // A number typed into the dialog used to arrive as a Long and broke the run
        assertEquals(123, coerceOptionValue(type, 123L))
        assertEquals(123, coerceOptionValue(type, 123))
        assertEquals(123, coerceOptionValue(type, 123.0))
        assertEquals(123, coerceOptionValue(type, "123"))
        assertEquals(-5, coerceOptionValue(type, " -5 "))
    }

    @Test
    fun `int option rejects what it cannot hold`() {
        val type = typeOf<Int>()

        assertNull(coerceOptionValue(type, 12.5))
        assertNull(coerceOptionValue(type, Int.MAX_VALUE.toLong() + 1))
        assertNull(coerceOptionValue(type, Double.NaN))
        assertNull(coerceOptionValue(type, "twelve"))
        assertNull(coerceOptionValue(type, ""))
        assertNull(coerceOptionValue(type, true))
    }

    @Test
    fun `long option takes smaller integers and text`() {
        val type = typeOf<Long>()

        assertEquals(7L, coerceOptionValue(type, 7))
        assertEquals(7L, coerceOptionValue(type, 7L))
        assertEquals(7L, coerceOptionValue(type, "7"))
        assertEquals(Long.MAX_VALUE, coerceOptionValue(type, Long.MAX_VALUE))
        assertNull(coerceOptionValue(type, 7.5))
    }

    @Test
    fun `decimal options keep their own width`() {
        assertEquals(1.5f, coerceOptionValue(typeOf<Float>(), "1.5"))
        assertEquals(1.5f, coerceOptionValue(typeOf<Float>(), 1.5))
        assertEquals(2f, coerceOptionValue(typeOf<Float>(), 2))
        assertEquals(1.5, coerceOptionValue(typeOf<Double>(), "1.5"))
        assertEquals(2.0, coerceOptionValue(typeOf<Double>(), 2L))
        assertNull(coerceOptionValue(typeOf<Float>(), "1,5"))
    }

    @Test
    fun `boolean option only takes what unambiguously reads as one`() {
        val type = typeOf<Boolean>()

        assertEquals(true, coerceOptionValue(type, true))
        assertEquals(true, coerceOptionValue(type, "TRUE"))
        assertEquals(false, coerceOptionValue(type, " false "))
        assertNull(coerceOptionValue(type, "yes"))
        assertNull(coerceOptionValue(type, 1))
    }

    @Test
    fun `string option takes the scalars an older selection may carry`() {
        val type = typeOf<String>()

        assertEquals("abc", coerceOptionValue(type, "abc"))
        assertEquals("", coerceOptionValue(type, ""))
        assertEquals("123", coerceOptionValue(type, 123))
        assertEquals("true", coerceOptionValue(type, true))
        assertNull(coerceOptionValue(type, listOf("a")))
    }

    @Test
    fun `list options convert every element`() {
        assertEquals(listOf("a", "b"), coerceOptionValue(typeOf<List<String>>(), listOf("a", "b")))
        assertEquals(listOf("a", "b"), coerceOptionValue(typeOf<List<String>>(), "a, b"))
        assertEquals(listOf("1", "2"), coerceOptionValue(typeOf<List<String>>(), listOf(1, 2)))
        assertEquals(listOf(1, 2), coerceOptionValue(typeOf<List<Int>>(), listOf("1", 2L)))
        assertEquals(listOf("a"), coerceOptionValue(typeOf<List<String>>(), arrayOf("a")))
        assertEquals(emptyList<String>(), coerceOptionValue(typeOf<List<String>>(), emptyList<String>()))
    }

    @Test
    fun `a list drops entirely when one element does not fit`() {
        assertNull(coerceOptionValue(typeOf<List<Int>>(), listOf(1, "x")))
        assertNull(coerceOptionValue(typeOf<List<Int>>(), 1))
    }

    @Test
    fun `nullability and unknown types do not stand in the way`() {
        assertEquals(1, coerceOptionValue(typeOf<Int?>(), "1"))

        // An unmodelled type keeps working
        val value = Regex("a")
        assertSame(value, coerceOptionValue(typeOf<Regex>(), value))

        assertNull(coerceOptionValue(typeOf<Int>(), null))
    }

    @Test
    fun `patch options are set as the types their patches declare`() {
        val patches = patchesByName(
            bytecodePatch(name = "Numbers") {
                intOption("versionCode")
                longOption("maxDuration")
                stringOption("packageName")
                stringsOption("hosts")
            }
        )

        patches.applyPatchOptions(
            mapOf(
                "Numbers" to mapOf(
                    "versionCode" to 250_000L,
                    "maxDuration" to 60,
                    "packageName" to "com.example",
                    "hosts" to "a.com, b.com"
                )
            ),
            TestLogger()
        )

        val options = patches.getValue("Numbers").options
        assertEquals(250_000, options["versionCode"].value)
        assertEquals(60L, options["maxDuration"].value)
        assertEquals("com.example", options["packageName"].value)
        assertEquals(listOf("a.com", "b.com"), options["hosts"].value)
    }

    @Test
    fun `a stale or unusable value is reported instead of failing the run`() {
        val patches = patchesByName(
            bytecodePatch(name = "Numbers") {
                intOption("versionCode", default = 1)
            }
        )
        val logger = TestLogger()

        patches.applyPatchOptions(
            mapOf(
                "Numbers" to mapOf(
                    "versionCode" to "not a number",
                    "removedInThisBundleVersion" to "1"
                ),
                "RemovedPatch" to mapOf("whatever" to "1")
            ),
            logger
        )

        assertEquals(1, patches.getValue("Numbers").options["versionCode"].value)
        assertEquals(2, logger.warnings.size)
        assertTrue(logger.warnings.any { it.contains("versionCode") })
        assertTrue(logger.warnings.any { it.contains("removedInThisBundleVersion") })
    }

    @Test
    fun `an option without a value keeps the default of its patch`() {
        val patches = patchesByName(
            bytecodePatch(name = "Numbers") {
                intOption("versionCode", default = 7)
            }
        )

        patches.applyPatchOptions(mapOf("Numbers" to mapOf("versionCode" to null)), TestLogger())

        assertEquals(7, patches.getValue("Numbers").options["versionCode"].value)
    }

    @Test
    fun `a text field the user cleared leaves the default of its patch in place`() {
        val patches = patchesByName(
            bytecodePatch(name = "Branding") {
                stringOption("appName", default = "Morphe", required = true)
            }
        )

        // The dialog keeps the blank, and the run strips it before the patcher sees it
        val cleared: Options = mapOf(0 to mapOf("Branding" to mapOf("appName" to "")))
        patches.applyPatchOptions(cleared.sanitizeForPatcher()[0].orEmpty(), TestLogger())

        assertEquals("Morphe", patches.getValue("Branding").options["appName"].value)
    }

    @Test
    fun `a blank left over on a number keeps the default instead of failing the run`() {
        val patches = patchesByName(
            bytecodePatch(name = "Upload") {
                longOption("maxDuration", default = 60L)
            }
        )
        val logger = TestLogger()

        // Clearing a numeric field used to hand the patcher an empty string it rejected
        patches.applyPatchOptions(mapOf("Upload" to mapOf("maxDuration" to "")), logger)

        assertEquals(60L, patches.getValue("Upload").options["maxDuration"].value)
        assertTrue(logger.warnings.single().contains("maxDuration"))
    }

    private fun patchesByName(vararg patches: Patch<*>) = patches.associateBy { it.name!! }

    private class TestLogger : Logger() {
        val warnings = mutableListOf<String>()

        override fun log(level: LogLevel, message: String) {
            if (level == LogLevel.WARN) warnings += message
        }
    }
}
