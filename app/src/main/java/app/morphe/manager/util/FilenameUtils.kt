/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/util/FilenameUtils.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Utility helpers for working with filenames.
 */
object FilenameUtils {
    // DateTimeFormatter is immutable and thread-safe, safe as a shared instance
    private val TIMESTAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneId.systemDefault())

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
            .replace(Regex("_{2,}"), "_")
            .replace(Regex("-{2,}"), "-")
            .trim('_', '-')
    }

    /**
     * Insert a "yyyy-MM-dd-HHmmss" timestamp before the extension of [fileName].
     */
    fun timestamped(fileName: String): String {
        val timestamp = TIMESTAMP_FORMAT.format(Instant.now())
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            "${fileName.substring(0, dotIndex)}-$timestamp${fileName.substring(dotIndex)}"
        } else {
            "$fileName-$timestamp"
        }
    }
}
