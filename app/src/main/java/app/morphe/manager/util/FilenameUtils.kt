package app.morphe.manager.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Utility helpers for working with filenames.
 */
object FilenameUtils {
    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US)

    /**
     * Sanitize a string so it can safely be used as part of a filename.
     */
    fun sanitize(segment: String): String {
        if (segment.isEmpty()) return ""
        val raw = buildString(segment.length) {
            segment.forEach { char ->
                val sanitized = when {
                    char in '0'..'9' || char in 'a'..'z' || char in 'A'..'Z' -> char
                    char == '-' || char == '_' || char == '.' -> char
                    char.isWhitespace() -> '_'
                    char == '\'' || char == '"' || char == '`' -> null
                    else -> '_'
                }
                sanitized?.let { append(it) }
            }
        }

        return raw
            .replace(Regex("[_]{2,}"), "_")
            .replace(Regex("[-]{2,}"), "-")
            .trim('_', '-')
    }

    /**
     * Insert a "yyyy-MM-dd-HHmmss" timestamp before the extension of [fileName],
     * e.g. "backup.zip" -> "backup-2026-07-05-153000.zip".
     */
    fun timestamped(fileName: String): String {
        val timestamp = TIMESTAMP_FORMAT.format(java.util.Date())
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            "${fileName.substring(0, dotIndex)}-$timestamp${fileName.substring(dotIndex)}"
        } else {
            "$fileName-$timestamp"
        }
    }
}
