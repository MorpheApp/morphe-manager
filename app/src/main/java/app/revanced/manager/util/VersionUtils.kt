package app.revanced.manager.util

/**
 * Compare two version strings
 * Returns: -1 if v1 < v2, 0 if v1 == v2, 1 if v1 > v2
 */
fun compareVersions(v1: String?, v2: String?): Int {
    if (v1 == null && v2 == null) return 0
    if (v1 == null) return -1
    if (v2 == null) return 1

    // Remove 'v' prefix if present
    val version1 = v1.removePrefix("v").trim()
    val version2 = v2.removePrefix("v").trim()

    if (version1 == version2) return 0

    // Split by dots and compare each part
    val parts1 = version1.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
    val parts2 = version2.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }

    val maxLength = maxOf(parts1.size, parts2.size)

    for (i in 0 until maxLength) {
        val part1 = parts1.getOrNull(i) ?: 0
        val part2 = parts2.getOrNull(i) ?: 0

        when {
            part1 < part2 -> return -1
            part1 > part2 -> return 1
        }
    }

    return 0
}

/**
 * Check if newVersion is newer than oldVersion
 */
fun isNewerVersion(oldVersion: String?, newVersion: String?): Boolean {
    return compareVersions(oldVersion, newVersion) < 0
}
