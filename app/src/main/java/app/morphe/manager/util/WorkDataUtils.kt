/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

private const val HEAD_BYTES = 3072
private const val TAIL_BYTES = 4096

/**
 * Caps the UTF-8 size of a string headed for a WorkManager output, which throws once its whole
 * serialized form passes 10 KiB. Keeps the start and the end of the text, since a stack trace
 * names the failure first and the frame that raised it last.
 */
fun String.truncateForWorkData(maxBytes: Int = 8192): String {
    val bytes = toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return this

    // Cut on a character start so neither piece ends in half a code point
    var headEnd = HEAD_BYTES
    while (headEnd > 0 && bytes[headEnd].isContinuationByte()) headEnd--
    var tailStart = bytes.size - TAIL_BYTES
    while (tailStart < bytes.size && bytes[tailStart].isContinuationByte()) tailStart++

    val omitted = tailStart - headEnd
    return String(bytes, 0, headEnd, Charsets.UTF_8) +
            "\n\n... [truncated $omitted bytes] ...\n\n" +
            String(bytes, tailStart, bytes.size - tailStart, Charsets.UTF_8)
}

private fun Byte.isContinuationByte() = toInt() and 0xC0 == 0x80
