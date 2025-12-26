package app.revanced.manager.util

/**
 * Format bytes into readable format (B, KB, MB, GB)
 */
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * Format megabytes from bytes
 */
fun formatMegabytes(bytes: Long): Float =
    if (bytes <= 0) 0f else bytes / 1_000_000f
