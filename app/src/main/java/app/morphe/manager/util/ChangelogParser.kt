/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import app.morphe.manager.util.ChangelogParser.EXPERIMENTAL_VERSION_ADDITION_RE
import app.morphe.manager.util.ChangelogParser.hasChangesFor


/**
 * Represents a single version entry parsed from a CHANGELOG.md file.
 *
 * [scopedBullets] preserves the individual bullet bodies per scope so that
 * [ChangelogParser.hasChangesFor] can distinguish substantive changes from
 * bookkeeping bullets (e.g. "Add experimental support for X.Y.Z").
 */
data class ChangelogEntry(
    val version: String,
    val date: String?,
    val content: String,
    val scopedBullets: Map<String, List<String>> = emptyMap(),
)

/**
 * True when version carries a semver pre-release suffix (e.g. `1.2.3-dev.4`).
 * Stable releases have no dash in the version string.
 */
val ChangelogEntry.isPrerelease: Boolean get() = version.contains('-')

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

    /**
     * Matches a bullet that *only* adds experimental support for a new app
     * version, e.g. "Add experimental support for `21.25.523`" (or the past
     * tense "Added ..."). Conventional-changelog emits these for every new
     * experimental version target, even when no patch logic actually changed.
     *
     * Such bullets don't affect anyone who isn't already running that exact
     * experimental version, so they shouldn't by themselves count as a "real"
     * change for the purposes of [hasChangesFor] (see #622). A scope is still
     * considered changed if it has at least one *other* bullet alongside this.
     */
    private val EXPERIMENTAL_VERSION_ADDITION_RE = Regex(
        """^Add(?:ed)?\s+experimental\s+support\s+for\b""",
        RegexOption.IGNORE_CASE
    )

    // Commit hash links: ([abc1234](https://...commit/...)) → removed, noise with no value in UI
    private val COMMIT_LINK_REGEX = Regex("""\s*\(\[([0-9a-f]{7,})]\([^)]+/commit/[^)]+\)\)""")

    private fun String.sanitizeContent(): String = this
        .replace(COMMIT_LINK_REGEX, "")
        .trimEnd()

    /**
     * Extracts, per scope, the list of raw bullet bodies (the text following
     * the `**Scope:**` prefix) from one version entry's content.
     */
    private fun resolveScopedBullets(content: String): Map<String, List<String>> {
        val scoped = mutableMapOf<String, MutableList<String>>()
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            val match = BULLET_SCOPE_RE.find(line) ?: continue
            val scope = match.groupValues[1]
            val body = line.substring(match.value.length).trim()
            scoped.getOrPut(scope) { mutableListOf() }.add(body)
        }
        return scoped
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
                scopedBullets = resolveScopedBullets(raw),
            )
        }

        for (line in lines) {
            val match = VERSION_HEADING.find(line)
            if (match != null) {
                flush()
                if (stopAfterFirstStable && entries.lastOrNull()?.isPrerelease == false) {
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
     * `"$appName - "`, and that bullet is more than just adding support for a
     * new experimental version. Comparison is case-insensitive.
     *
     * Multiple candidates are accepted because the same app can be referenced by
     * different names across sources: the canonical Compatibility declaration name
     * from the bundle, and the (localized) system PM label. Matching any one is
     * enough to trigger the badge.
     *
     * A bullet that purely adds experimental version support (see
     * [EXPERIMENTAL_VERSION_ADDITION_RE]) is ignored: it doesn't affect anyone
     * who isn't already on that experimental version, so it shouldn't by
     * itself trigger an update badge (#622). A scope with at least one other,
     * non-experimental-addition bullet still counts as changed.
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
            entry.scopedBullets.any { (scope, bullets) ->
                val scopeMatches = appNames.any { name ->
                    scope.equals(name, ignoreCase = true) ||
                            scope.startsWith("$name - ", ignoreCase = true)
                }
                scopeMatches && bullets.any { bullet ->
                    !EXPERIMENTAL_VERSION_ADDITION_RE.containsMatchIn(bullet)
                }
            }
        }
    }

    /**
     * Find the single entry for an exact [version].
     */
    fun findVersion(entries: List<ChangelogEntry>, version: String): ChangelogEntry? {
        val normalized = version.normalizeVersion()
        return entries.firstOrNull { it.version.normalizeVersion() == normalized }
    }
}
