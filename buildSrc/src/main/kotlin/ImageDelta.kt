/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

// The format JvmImageDelta in the app reads, see there
private const val OP_COPY = 0
private const val OP_DATA = 1
private const val OP_END = 2

// Long enough for a match to be no accident, short enough to find the few bytes around each change
private const val BLOCK_SIZE = 64

/**
 * [target] as the parts of [base] it shares, and the bytes it does not. The runtimes of the ABIs
 * link the same classes, all but a few of them alike, into images that differ in a few hundred
 * kilobytes at most, so one image and the delta of each other one take the place of them all.
 *
 * The delta is checked against [target] before it is returned, as a wrong one would only show
 * when a device unpacks it.
 */
internal fun imageDelta(base: ByteArray, target: ByteArray): ByteArray {
    val blocks = BlockIndex(base)

    val delta = ByteArrayOutputStream()
    DataOutputStream(delta).use { out ->
        val literal = ByteArrayOutputStream()
        fun flushLiteral() {
            if (literal.size() == 0) return
            out.writeByte(OP_DATA)
            out.writeInt(literal.size())
            literal.writeTo(out)
            literal.reset()
        }

        var position = 0
        while (position < target.size) {
            var match = if (position + BLOCK_SIZE <= target.size) blocks.find(blockHash(target, position)) else -1
            if (match < 0 || !target.regionMatches(position, base, match, BLOCK_SIZE)) {
                literal.write(target[position].toInt())
                position++
                continue
            }

            // Grown backwards into the bytes not matched yet, then forwards as far as both agree
            var start = position
            val pending = literal.toByteArray()
            var kept = pending.size
            while (kept > 0 && match > 0 && base[match - 1] == pending[kept - 1]) {
                kept--
                match--
                start--
            }
            literal.reset()
            literal.write(pending, 0, kept)
            flushLiteral()

            var length = position - start + BLOCK_SIZE
            while (start + length < target.size && match + length < base.size && target[start + length] == base[match + length]) {
                length++
            }
            out.writeByte(OP_COPY)
            out.writeLong(match.toLong())
            out.writeInt(length)
            position = start + length
        }
        flushLiteral()

        out.writeByte(OP_END)
        out.writeLong(target.size.toLong())
        out.writeInt(CRC32().apply { update(target) }.value.toInt())
    }

    return delta.toByteArray().also { check(it, base, target) }
}

/** The offset of every block of [base] by its hash, the first of blocks alike, in arrays the size of an image calls for. */
private class BlockIndex(base: ByteArray) {
    private val mask = Integer.highestOneBit(maxOf(base.size / BLOCK_SIZE, 1) * 2) * 2 - 1
    private val hashes = LongArray(mask + 1)
    private val offsets = IntArray(mask + 1) { -1 }

    init {
        for (offset in 0..base.size - BLOCK_SIZE step BLOCK_SIZE) {
            val hash = blockHash(base, offset)
            var slot = slotOf(hash)
            while (offsets[slot] >= 0 && hashes[slot] != hash) slot = (slot + 1) and mask
            if (offsets[slot] < 0) {
                hashes[slot] = hash
                offsets[slot] = offset
            }
        }
    }

    /** The offset of the block with [hash], or -1. */
    fun find(hash: Long): Int {
        var slot = slotOf(hash)
        while (offsets[slot] >= 0) {
            if (hashes[slot] == hash) return offsets[slot]
            slot = (slot + 1) and mask
        }
        return -1
    }

    private fun slotOf(hash: Long) = (hash xor (hash ushr 32)).toInt() and mask
}

private fun blockHash(bytes: ByteArray, offset: Int): Long {
    // FNV-1a
    var hash = -0x340d631b7bdddcdbL
    for (i in offset until offset + BLOCK_SIZE) {
        hash = (hash xor (bytes[i].toLong() and 0xff)) * 0x100000001b3L
    }
    return hash
}

private fun ByteArray.regionMatches(offset: Int, other: ByteArray, otherOffset: Int, length: Int) =
    (0 until length).all { this[offset + it] == other[otherOffset + it] }

private fun check(delta: ByteArray, base: ByteArray, target: ByteArray) {
    var position = 0
    fun expect(bytes: ByteArray, offset: Int, length: Int) {
        if (position + length > target.size || !target.regionMatches(position, bytes, offset, length)) {
            throw GradleException("The image delta does not rebuild the image")
        }
        position += length
    }

    DataInputStream(delta.inputStream()).use { input ->
        while (true) {
            when (input.readByte().toInt()) {
                OP_COPY -> {
                    val offset = input.readLong().toInt()
                    expect(base, offset, input.readInt())
                }
                OP_DATA -> ByteArray(input.readInt()).also(input::readFully).let { expect(it, 0, it.size) }
                OP_END -> break
            }
        }
    }
    if (position != target.size) throw GradleException("The image delta does not rebuild the image")
}
