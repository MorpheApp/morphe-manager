package app.morphe.manager.util

/**
 * Represents a single version entry parsed from a CHANGELOG.md file.
 */
data class ChangelogEntry(
    val version: String,
    val date: String?,
    val content: String,
    val affectedScopes: Set<String> = emptySet(),
)

/**
 * Parses the CHANGELOG.md formats used by Morphe repositories.
 *
 * Third-party repos using the Morphe template are expected to follow one of
 * these patterns. Unknown heading formats are silently skipped.
 */
object ChangelogParser {

    /**
     * Matches every changelog heading style emitted by conventional-changelog:
     *   `# [VERSION](url) (DATE)`      - patches / no-label style with compare URL
     *   `# app [VERSION](url) (DATE)`  - manager / labeled style with compare URL
     *   `# VERSION (DATE)`             - initial release, no compare URL (first tag)
     *
     * Capture groups:
     *   1 -> version string when wrapped in `[...](url)`
     *   2 -> version string when written as a bare token (initial release)
     *   3 -> date string
     * Exactly one of group 1 or group 2 is populated per match.
     */
    private val VERSION_HEADING = Regex(
        """^#{1,3}\s+(?:\S+\s+)?(?:\[([^]]+)]\([^)]*\)|([^\s\[(]+))\s+\((\d{4}-\d{2}-\d{2})\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Matches the bold scope prefix in a conventional-changelog bullet:
     *   `* **YouTube - Hide ads:** text`  →  group 1 = `YouTube - Hide ads`
     *   `* **Reddit:** text`              →  group 1 = `Reddit`
     *
     * The colon sits *inside* the bold span (`**scope:**`) as emitted by
     * conventional-changelog. Lines without this pattern are unscoped (global)
     * and are intentionally ignored to avoid false-positive update badges.
     */
    private val BULLET_SCOPE_RE = Regex("""^\* \*\*(.+?):\*\*""")

    // Commit hash links: ([abc1234](https://...commit/...)) → removed, noise with no value in UI
    private val COMMIT_LINK_REGEX = Regex("""\s*\(\[([0-9a-f]{7,})]\([^)]+/commit/[^)]+\)\)""")

    private fun String.sanitizeContent(): String = this
        .replace(COMMIT_LINK_REGEX, "")
        .trimEnd()

    /**
     * Extracts the set of raw scope prefixes from one version entry's content.
     */
    private fun resolveAffectedScopes(content: String): Set<String> {
        val scopes = mutableSetOf<String>()
        for (line in content.lines()) {
            val scope = BULLET_SCOPE_RE.find(line.trim())?.groupValues?.get(1) ?: continue
            scopes += scope
        }
        return scopes
    }

    /**
     * Parse raw CHANGELOG.md text into a list of [ChangelogEntry], ordered
     * newest-first (same order as in the file).
     *
     * When [stopAfterFirstStable] is true the parser stops as soon as the first
     * stable release (no pre-release suffix) has been collected and the next
     * heading is encountered, skipping the rest of the file. Useful for dev-branch
     * changelogs that accumulate many old entries below the last stable baseline.
     */
    fun parse(markdown: String, stopAfterFirstStable: Boolean = false): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()
        val lines = markdown.lines()

        var currentVersion: String? = null
        var currentDate: String? = null
        val currentContent = StringBuilder()

        fun flush() {
            val v = currentVersion ?: return
            val raw = currentContent.toString()
            entries += ChangelogEntry(
                version = v,
                date = currentDate,
                content = raw.sanitizeContent(),
                affectedScopes = resolveAffectedScopes(raw),
            )
        }

        for (line in lines) {
            val match = VERSION_HEADING.find(line)
            if (match != null) {
                flush()
                if (stopAfterFirstStable && entries.lastOrNull()?.version?.contains("-") == false) {
                    currentVersion = null
                    break
                }
                // Group 1 = bracketed version, group 2 = bare version (initial release).
                // Exactly one is populated; the other is an empty string.
                currentVersion = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
                currentDate = match.groupValues[3]
                currentContent.clear()
            } else if (currentVersion != null) {
                currentContent.appendLine(line)
            }
        }
        flush()

        return entries
    }

    /**
     * Returns all entries with versions strictly newer than [installedVersion].
     * If [installedVersion] is null, returns all entries.
     * Results are ordered newest-first (same as the file).
     */
    fun entriesNewerThan(
        entries: List<ChangelogEntry>,
        installedVersion: String?
    ): List<ChangelogEntry> {
        if (installedVersion == null) return entries
        val installedDate = findVersion(entries, installedVersion)?.date
        return entries.filter { entry ->
            isNewerVersion(installedVersion, entry.version) &&
                    (installedDate == null || entry.date == null || entry.date >= installedDate)
        }
    }

    /**
     * Returns true if any changelog entry newer than [installedVersion] has a
     * scoped bullet whose scope exactly matches one of [appNames] or starts with
     * `"$appName - "`. Comparison is case-insensitive.
     *
     * Multiple candidates are accepted because the same app can be referenced by
     * different names across sources: the canonical Compatibility declaration name
     * from the bundle, and the (localized) system PM label. Matching any one is
     * enough to trigger the badge.
     */
    fun hasChangesFor(
        entries: List<ChangelogEntry>,
        installedVersion: String?,
        appNames: Collection<String>,
    ): Boolean {
        if (appNames.isEmpty()) return false
        val newerEntries = entriesNewerThan(entries, installedVersion)
        if (newerEntries.isEmpty()) return false
        return newerEntries.any { entry ->
            entry.affectedScopes.any { scope ->
                appNames.any { name ->
                    scope.equals(name, ignoreCase = true) ||
                            scope.startsWith("$name - ", ignoreCase = true)
                }
            }
        }
    }

    /**
     * Find the single entry for an exact [version].
     */
    fun findVersion(entries: List<ChangelogEntry>, version: String): ChangelogEntry? {
        val normalized = version.removePrefix("v").trim()
        return entries.firstOrNull { it.version.removePrefix("v").trim() == normalized }
    }
}
