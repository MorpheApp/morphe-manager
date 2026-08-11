package app.morphe.manager.patcher.logger

/**
 * Extracts a space-delimited key=value field from a flat log string.
 * Supports quoted values (e.g. key="some value with spaces").
 */
internal fun String.logField(key: String): String? {
    val prefix = "$key="
    val start = indexOf(prefix).takeIf { it >= 0 } ?: return null
    val valueStart = start + prefix.length
    if (valueStart >= length) return null
    return if (this[valueStart] == '"') {
        val end = indexOf('"', valueStart + 1).takeIf { it >= 0 } ?: return null
        substring(valueStart + 1, end).ifBlank { null }
    } else {
        val end = indexOf(' ', valueStart).takeIf { it >= 0 } ?: length
        substring(valueStart, end).ifBlank { null }
    }
}
