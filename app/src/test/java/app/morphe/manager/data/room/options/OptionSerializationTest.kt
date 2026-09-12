/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.data.room.options

import app.morphe.manager.patcher.patch.Option as PatchOption
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Saved options outlive their bundle version, so they must read back as the declared type. */
class OptionSerializationTest {
    @Test
    fun `values survive a round trip as the declared type`() {
        assertEquals(123, roundTrip(typeOf<Int>(), 123))
        assertEquals(123, roundTrip(typeOf<Int>(), 123L))
        assertEquals(123L, roundTrip(typeOf<Long>(), 123))
        assertEquals(1.5f, roundTrip(typeOf<Float>(), 1.5f))
        assertEquals(1.5, roundTrip(typeOf<Double>(), 1.5))
        assertEquals(true, roundTrip(typeOf<Boolean>(), true))
        assertEquals("abc", roundTrip(typeOf<String>(), "abc"))
        assertEquals(listOf("a", "b"), roundTrip(typeOf<List<String>>(), listOf("a", "b")))
        assertEquals(listOf(1, 2), roundTrip(typeOf<List<Int>>(), listOf(1, 2)))
    }

    @Test
    fun `a value stored before the option changed type is read as the type it has now`() {
        // Older releases stored every number as a Long
        assertEquals(250_000, deserialize(typeOf<Int>(), "250000"))
        assertEquals("250000", deserialize(typeOf<String>(), "250000"))
        assertEquals(250_000L, deserialize(typeOf<Long>(), "\"250000\""))
        assertEquals(listOf(1, 2), deserialize(typeOf<List<Int>>(), "[\"1\", 2]"))
    }

    @Test
    fun `a value that cannot stand for the declared type is rejected`() {
        assertFailsWith<Option.SerializationException> { deserialize(typeOf<Int>(), "\"abc\"") }
        assertFailsWith<Option.SerializationException> { deserialize(typeOf<Int>(), "[1]") }
        assertFailsWith<Option.SerializationException> { deserialize(typeOf<List<Int>>(), "[1, null]") }
    }

    @Test
    fun `an explicitly empty value stays empty`() {
        assertNull(deserialize(typeOf<Int>(), "null"))
        assertEquals("", roundTrip(typeOf<String>(), ""))
    }

    private fun roundTrip(type: KType, value: Any?) = deserialize(
        type,
        Option.SerializedValue.fromValue(value).toJsonString()
    )

    private fun deserialize(type: KType, json: String) =
        Option.SerializedValue.fromJsonString(json).deserializeFor(option(type))

    private fun option(type: KType) = PatchOption<Any>(
        title = "Title",
        key = "key",
        description = "",
        required = false,
        type = type,
        default = null,
        presets = null,
        validator = { true }
    )
}
