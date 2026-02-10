package app.morphe.manager.util

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

    // Extract base version and pre-release suffix
    data class VersionParts(
        val base: String,
        val preRelease: String?
    )

    fun extractParts(version: String): VersionParts {
        val preReleasePattern = """^([\d.]+)[-._]?(dev|beta|rc|alpha|preview)""".toRegex(RegexOption.IGNORE_CASE)
        val match = preReleasePattern.find(version)

        return if (match != null) {
            VersionParts(match.groupValues[1], match.groupValues[2])
        } else {
            // Check if version contains non-numeric suffixes without keywords
            val numericPattern = """^([\d.]+)(.*)$""".toRegex()
            val numMatch = numericPattern.find(version)
            if (numMatch != null && numMatch.groupValues[2].isNotEmpty()) {
                VersionParts(numMatch.groupValues[1], numMatch.groupValues[2])
            } else {
                VersionParts(version, null)
            }
        }
    }

    val parts1 = extractParts(version1)
    val parts2 = extractParts(version2)

    // Compare base versions first
    val base1 = parts1.base.split(".").map { it.toIntOrNull() ?: 0 }
    val base2 = parts2.base.split(".").map { it.toIntOrNull() ?: 0 }

    val maxLength = maxOf(base1.size, base2.size)

    for (i in 0 until maxLength) {
        val part1 = base1.getOrNull(i) ?: 0
        val part2 = base2.getOrNull(i) ?: 0

        when {
            part1 < part2 -> return -1
            part1 > part2 -> return 1
        }
    }

    // Base versions are equal, check pre-release status
    return when {
        parts1.preRelease == null && parts2.preRelease == null -> 0  // Both stable
        parts1.preRelease == null && parts2.preRelease != null -> 1  // v1 is stable, v2 is pre-release (v1 > v2)
        parts1.preRelease != null && parts2.preRelease == null -> -1 // v1 is pre-release, v2 is stable (v1 < v2)
        else -> {
            // Both are pre-release, compare the full version string
            // This handles cases like dev.11 vs dev.12
            version1.compareTo(version2)
        }
    }
}

/**
 * Check if newVersion is newer than oldVersion
 */
fun isNewerVersion(oldVersion: String?, newVersion: String?): Boolean {
    return compareVersions(oldVersion, newVersion) < 0
}
