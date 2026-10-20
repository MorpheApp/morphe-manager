/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.CRC32
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * The device rebuilds the image of a runtime from the delta the build made (buildSrc/ImageDelta.kt),
 * and the JVM fails in ways far from the cause if a single byte of it is wrong.
 */
class JvmImageDeltaTest {
    // Larger than the buffer the delta is applied with, so parts span several reads
    private val base = Random(1).nextBytes(200_000)
    private val baseFile = File.createTempFile("image", ".base").apply { writeBytes(base) }

    @AfterTest
    fun deleteBase() {
        baseFile.delete()
    }

    /** A delta in the format the build writes, ending with the size and CRC-32 of [image] unless given others. */
    private fun delta(
        image: ByteArray,
        size: Long = image.size.toLong(),
        crc: Int = CRC32().apply { update(image) }.value.toInt(),
        parts: DataOutputStream.() -> Unit
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.parts()
            out.writeByte(OP_END)
            out.writeLong(size)
            out.writeInt(crc)
        }
    }.toByteArray()

    private fun DataOutputStream.copy(offset: Int, length: Int) {
        writeByte(OP_COPY)
        writeLong(offset.toLong())
        writeInt(length)
    }

    private fun DataOutputStream.data(content: ByteArray) {
        writeByte(OP_DATA)
        writeInt(content.size)
        write(content)
    }

    private fun apply(delta: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        RandomAccessFile(baseFile, "r").use { JvmImageDelta.apply(it, delta.inputStream(), output) }
    }.toByteArray()

    @Test
    fun `rebuilds the image from ranges of the base and bytes of its own`() {
        val own = "changed".toByteArray()
        // Ranges out of order, as an image that moved its parts around has them
        val image = base.copyOfRange(150_000, 200_000) + own + base.copyOfRange(0, 120_000) + own

        val delta = delta(image) {
            copy(150_000, 50_000)
            data(own)
            copy(0, 120_000)
            data(own)
        }

        assertContentEquals(image, apply(delta))
    }

    @Test
    fun `an image like the base is one range of it`() {
        assertContentEquals(base, apply(delta(base) { copy(0, base.size) }))
    }

    @Test
    fun `fails on an image of another checksum`() {
        val delta = delta(base, crc = CRC32().apply { update(base) }.value.toInt() + 1) { copy(0, base.size) }

        assertFailsWith<IOException> { apply(delta) }
    }

    @Test
    fun `fails on an image of another size`() {
        val delta = delta(base, size = base.size + 1L) { copy(0, base.size) }

        assertFailsWith<IOException> { apply(delta) }
    }

    @Test
    fun `fails on a range past the end of the base`() {
        val delta = delta(base) { copy(base.size - 10, 20) }

        assertFailsWith<IOException> { apply(delta) }
    }

    @Test
    fun `fails on a part it does not know`() {
        val delta = byteArrayOf(9) + delta(base) { copy(0, base.size) }

        assertFailsWith<IOException> { apply(delta) }
    }

    @Test
    fun `fails on a delta cut short`() {
        val delta = delta(base) { copy(0, base.size) }

        assertFailsWith<IOException> { apply(delta.copyOf(delta.size - 4)) }
    }

    private companion object {
        // The format of buildSrc/ImageDelta.kt
        const val OP_COPY = 0
        const val OP_DATA = 1
        const val OP_END = 2
    }
}
