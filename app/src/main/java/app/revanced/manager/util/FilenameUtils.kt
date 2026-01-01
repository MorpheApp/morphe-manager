package app.revanced.manager.util

/**
 * Utility helpers for working with filenames.
 */
object FilenameUtils {
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
}
