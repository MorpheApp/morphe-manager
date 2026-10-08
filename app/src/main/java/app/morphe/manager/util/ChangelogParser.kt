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
 * [unscopedBullets] are the remaining bullet bodies, matched by what their text mentions.
 */
data class ChangelogEntry(
    val version: String,
    val date: String?,
    val content: String,
    val scopedBullets: Map<String, List<String>> = emptyMap(),
    val unscopedBullets: List<String> = emptyList(),
)

/**
 * What a changelog may call one app, to narrow it to that app: [appNames] in a bullet's scope or
 * text, the distinctive segments of [packageName] in a scope, and [patchNames], the patches
 * applied to it, quoted in backticks in an unscoped bullet. [otherAppNames], the rest of the
 * apps the same bundle patches, keep their names from counting for this one.
 */
data class ChangelogSubject(
    val appNames: Set<String>,
    val patchNames: Set<String> = emptySet(),
    val packageName: String? = null,
    val otherAppNames: Set<String> = emptySet()
)

/**
 * True when version carries a semantic versioning pre-release suffix (e.g. `1.2.3-dev.4`).
 * Stable releases have no dash in the version string.
 */
val ChangelogEntry.isPrerelease: Boolean get() = version.contains('-')

/** The changes of one release under a single heading, such as its features or its fixes. */
data class ChangelogSection(
    val kind: Kind,
    val title: String?,
    val items: List<ChangelogItem>
) {
    /** Declared in the order a release lists its sections. */
    enum class Kind { FEATURES, FIXES, IMPROVEMENTS, PERFORMANCE, APP_SUPPORT, OTHER }
}

/**
 * One change, with the app or patch its bullet is scoped to when it names one.
 *
 * @param isBullet False for a line of prose between the bullets, which reads as a note on the
 *   release rather than as a change of its own.
 */
data class ChangelogItem(
    val scope: String?,
    val text: String,
    val isBullet: Boolean = true
)

/** Number of changes in the section, the notes between them left out. */
val ChangelogSection.changeCount: Int get() = items.count { it.isBullet }

/** Number of changes of [kind] across these sections. */
fun List<ChangelogSection>.countOf(kind: ChangelogSection.Kind): Int =
    sumOf { section -> if (section.kind == kind) section.changeCount else 0 }

/** Copy of these sections with the text of every change passed through [transform]. */
inline fun List<ChangelogSection>.mapItemTexts(transform: (String) -> String): List<ChangelogSection> =
    map { section -> section.copy(items = section.items.map { it.copy(text = transform(it.text)) }) }

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
     * The colon sits *inside* the bold span, as in `**scope:**`, which is how
     * conventional-changelog emits it. Lines without this pattern are unscoped.
     */
    private val BULLET_SCOPE_RE = Regex("""^[*+-] \*\*(.+?):\*\*""")

    /** Any changelog bullet, scoped or not, whichever Markdown list marker it uses. */
    private val BULLET_RE = Regex("""^[*+-]\s""")

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

    /** Section heading inside an entry, such as `### Bug Fixes`. */
    private val SECTION_HEADING_RE = Regex("""^\s*#{1,6}\s+(.+?)\s*$""")

    /** Emoji and symbols some changelogs lead a heading with, as in `### 🐛 Bug Fixes`. */
    private val HEADING_DECORATION_RE = Regex("""^[^\p{L}\p{N}]+""")

    /** List item of any Markdown style and depth, bulleted or numbered, capturing its body. */
    private val LIST_ITEM_RE = Regex("""^\s*(?:[*+-]|\d+[.)])\s+(.+?)\s*$""")

    /**
     * Lines that carry nothing to read: rules, code fences, merge conflict markers left behind,
     * bare HTML tags and commit trailers.
     */
    private val NOISE_LINE_RE = Regex(
        """^\s*(?:[-=_*~]{3,}|[<=>]{7}.*|```.*|<[^>]+>|(?:signed-off-by|co-authored-by):.*)\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** Quote marker opening a line of release notes. */
    private val QUOTE_PREFIX_RE = Regex("""^\s*>\s?""")

    /** Images, which a list of changes has no room to show. */
    private val IMAGE_RE = Regex("""!\[[^]]*]\([^)]*\)""")

    /** Bullet body opening with a bold scope, `**Scope:** text`. */
    private val SCOPED_BODY_RE = Regex("""^\*\*(.+?):\*\*\s*(.*)$""")

    /** Target of a Markdown link, which names the repository rather than what changed. */
    private val LINK_TARGET_RE = Regex("""]\([^)]*\)""")

    /** Shorter app names, such as `X`, turn up in too many unrelated words to count as a mention. */
    private const val MIN_MENTIONED_NAME_LENGTH = 3

    /** Package name segments too common across apps to tell one apart in a scope. */
    private val GENERIC_PACKAGE_SEGMENTS = setOf(
        "com", "org", "net", "app", "apps", "android", "google", "mobile", "client", "free", "pro",
        "lite", "plus", "beta", "dev", "release", "global", "official", "messenger", "music", "browser"
    )

    /**
     * Extracts the raw bullet bodies of one version entry's content: per scope the text
     * following the `**Scope:**` prefix, and the unscoped ones whole.
     */
    private fun resolveBullets(content: String): Pair<Map<String, List<String>>, List<String>> {
        val scoped = mutableMapOf<String, MutableList<String>>()
        val unscoped = mutableListOf<String>()
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            val match = BULLET_SCOPE_RE.find(line)
            if (match != null) {
                val body = line.substring(match.value.length).trim()
                scoped.getOrPut(match.groupValues[1]) { mutableListOf() }.add(body)
            } else if (BULLET_RE.containsMatchIn(line)) {
                unscoped += line.replaceFirst(BULLET_RE, "").trim()
            }
        }
        return scoped to unscoped
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
            val (scoped, unscoped) = resolveBullets(raw)
            entries += ChangelogEntry(
                version = v,
                date = currentDate,
                content = raw.sanitizeContent(),
                scopedBullets = scoped,
                unscopedBullets = unscoped,
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
     * True when [entries], a dev changelog read up to its last stable release, stops above
     * [version], so the releases between them are missing from it.
     */
    fun stopsAbove(entries: List<ChangelogEntry>, version: String?): Boolean {
        val baseline = entries.lastOrNull()?.takeUnless { it.isPrerelease } ?: return false
        return version != null && isNewerVersion(version, baseline.version)
    }

    /**
     * Releases an app patched with [version] catches up on: [entries] newer than it, then the
     * releases of [stableHistory] between it and the last of [entries], which a dev changelog
     * stopping above [version] leaves out.
     */
    fun entriesSince(
        entries: List<ChangelogEntry>,
        version: String?,
        stableHistory: List<ChangelogEntry> = emptyList()
    ): List<ChangelogEntry> {
        val newer = entriesNewerThan(entries, version)
        if (!stopsAbove(entries, version)) return newer
        val baseline = entries.last().version
        return newer + stableHistory.filter {
            !it.isPrerelease && isNewerVersion(version, it.version) && isNewerVersion(it.version, baseline)
        }
    }

    /**
     * Returns true if any changelog entry newer than [installedVersion] has a bullet for
     * [subject] that is more than just adding support for a new experimental version.
     *
     * Third-party changelogs rarely follow one convention, so a bullet is matched heuristically:
     * - a scoped one by its scope, which names one of the app names or one of its sub-scopes
     *   (`YouTube - Hide ads`), or a distinctive package name segment (`brave` for
     *   `com.brave.browser`). Only letters and digits are compared, so `google-photos` is
     *   Google Photos. Its text is not read, as the scope already says whose change it is
     * - an unscoped one by its text, which names the app as a whole word outside a dotted
     *   identifier, or quotes one of its patches in backticks. Plain patch names are too often
     *   common words to count
     *
     * Several app names are accepted because the same app is called differently across sources:
     * the bundle's Compatibility name and the (localized) system PM label.
     *
     * A bullet that purely adds experimental version support (see
     * [EXPERIMENTAL_VERSION_ADDITION_RE]) is ignored: it doesn't affect anyone
     * who isn't already on that experimental version, so it shouldn't by
     * itself trigger an update badge (#622).
     */
    fun hasChangesFor(
        entries: List<ChangelogEntry>,
        installedVersion: String?,
        subject: ChangelogSubject,
    ): Boolean {
        if (subject.appNames.isEmpty()) return false
        val matcher = SubjectMatcher(subject)
        return entriesNewerThan(entries, installedVersion).any { it.hasChangesFor(matcher) }
    }

    /**
     * Narrows [entries] to [subject], dropping entries with no substantive bullet for it. Kept
     * entries hold its bullets and, under [generalHeading], the ones for no app at all.
     */
    fun entriesFor(
        entries: List<ChangelogEntry>,
        subject: ChangelogSubject?,
        generalHeading: String? = null,
    ): List<ChangelogEntry> {
        if (subject == null || subject.appNames.isEmpty()) return entries
        val matcher = SubjectMatcher(subject)
        return entries
            .filter { it.hasChangesFor(matcher) }
            .map { entry ->
                entry.copy(
                    content = entry.content.keepScopedLines(matcher, generalHeading),
                    scopedBullets = entry.scopedBullets.filterKeys(matcher::matchesScope),
                    unscopedBullets = entry.unscopedBullets.filter(matcher::mentions)
                )
            }
    }

    /** Letters and digits only, lowercased. */
    private fun String.compact() = filter(Char::isLetterOrDigit).lowercase()

    /** [ChangelogSubject] prepared once, as a changelog runs to thousands of bullets. */
    private class SubjectMatcher(subject: ChangelogSubject) {
        private val names = subject.appNames.map { it.compact() }.filterTo(mutableSetOf(), String::isNotEmpty)
        private val otherNames = subject.otherAppNames.mapTo(mutableSetOf()) { it.compact() }
        private val segments = subject.packageName?.split('.').orEmpty()
            .filter { it.length >= MIN_MENTIONED_NAME_LENGTH && it.lowercase() !in GENERIC_PACKAGE_SEGMENTS }
            .mapTo(mutableSetOf()) { it.compact() }

        // Neither a longer word nor a segment of a package name such as org.telegram.plus
        private val nameRegex = subject.appNames
            .filter { it.length >= MIN_MENTIONED_NAME_LENGTH }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("|", prefix = "(?:", postfix = ")") { Regex.escape(it) }
            ?.let { names ->
                Regex(
                    """(?<![\p{L}\p{N}])(?<![\p{L}\p{N}]\.)$names(?![\p{L}\p{N}])(?!\.[\p{L}\p{N}])""",
                    RegexOption.IGNORE_CASE
                )
            }

        // Longer names of other apps that hold this one's, such as Telegram Web, are theirs
        private val shadowingNames = subject.otherAppNames.filter { other ->
            subject.appNames.any { other.length > it.length && other.contains(it, ignoreCase = true) }
        }
        private val quotedPatches = subject.patchNames.map { "`$it`" }

        /** True when the scope names the app, exactly or as one of its sub-scopes. */
        fun matchesScope(scope: String): Boolean {
            val keys = setOf(scope.compact(), scope.substringBefore(" - ").compact())
            // A segment can be the vendor's, as facebook is Messenger's, so another app's name wins
            return names.any { it in keys } || keys.none { it in otherNames } && keys.any { it in segments }
        }

        /** True when unscoped bullet text names the app or quotes one of its patches. */
        fun mentions(bullet: String): Boolean {
            val text = shadowingNames.fold(bullet.replace(LINK_TARGET_RE, "]")) { text, other ->
                text.replace(other, " ", ignoreCase = true)
            }
            return nameRegex?.containsMatchIn(text) == true ||
                    quotedPatches.any { text.contains(it, ignoreCase = true) }
        }
    }

    /** True for a bullet that is more than bookkeeping. */
    private fun String.isSubstantive() = !EXPERIMENTAL_VERSION_ADDITION_RE.containsMatchIn(this)

    private fun ChangelogEntry.hasChangesFor(matcher: SubjectMatcher) =
        scopedBullets.any { (scope, bullets) ->
            matcher.matchesScope(scope) && bullets.any { it.isSubstantive() }
        } || unscopedBullets.any { it.isSubstantive() && matcher.mentions(it) }

    /**
     * Rebuilds the entry's markdown from the bullets [matcher] accepts, dropping emptied headings.
     * Other unscoped bullets follow under [generalHeading], and are left out when it is null.
     */
    private fun String.keepScopedLines(
        matcher: SubjectMatcher,
        generalHeading: String?
    ): String {
        val kept = mutableListOf<String>()
        val general = mutableListOf<String>()
        var pendingHeading: String? = null
        for (rawLine in lines()) {
            val line = rawLine.trim()
            if (line.startsWith("#")) {
                pendingHeading = rawLine
                continue
            }
            if (!BULLET_RE.containsMatchIn(line)) continue

            val scope = BULLET_SCOPE_RE.find(line)?.groupValues?.get(1)
            val forSubject = scope?.let(matcher::matchesScope) ?: matcher.mentions(line)
            if (scope == null && !forSubject) {
                if (generalHeading != null) general += rawLine
                continue
            }
            if (!forSubject) continue

            pendingHeading?.let {
                if (kept.isNotEmpty()) kept += ""
                kept += it
                kept += ""
                pendingHeading = null
            }
            kept += rawLine
        }

        // Everything the release changed beyond this app, gathered under one heading of its own
        if (general.isNotEmpty()) {
            if (kept.isNotEmpty()) kept += ""
            kept += "### $generalHeading"
            kept += ""
            kept += general
        }
        return kept.joinToString("\n")
    }

    /**
     * Splits the content of an entry into its sections. Changes ahead of the first heading form a
     * section without a title.
     *
     * Changelogs are mostly flat lists, but handwritten ones mix in prose, nested lists and the
     * odd leftover. The list is flattened, prose is kept as notes and the leftovers are dropped,
     * so any entry reads as sections of changes.
     *
     * Sections come in the order of [ChangelogSection.Kind], features first, whatever order the
     * changelog wrote them in, so every release reads the same way.
     */
    fun sections(content: String): List<ChangelogSection> {
        val sections = mutableListOf<ChangelogSection>()
        var title: String? = null
        var items = mutableListOf<ChangelogItem>()

        fun flush() {
            if (items.isEmpty()) return
            sections += ChangelogSection(sectionKindOf(title), title, items)
            items = mutableListOf()
        }

        // Release notes taken straight from GitHub still carry their commit links
        val cleaned = content.replace(COMMIT_LINK_REGEX, "").replace(IMAGE_RE, "")
        for (rawLine in cleaned.lines()) {
            val line = rawLine.replace(QUOTE_PREFIX_RE, "")
            if (line.isBlank() || NOISE_LINE_RE.matches(line)) continue

            val heading = SECTION_HEADING_RE.matchEntire(line)
            if (heading != null) {
                flush()
                // The section draws an icon of its own, so a decoration would only repeat it
                title = heading.groupValues[1].replace(HEADING_DECORATION_RE, "").ifEmpty { heading.groupValues[1] }
                continue
            }

            val body = LIST_ITEM_RE.matchEntire(line)?.groupValues?.get(1)
            if (body == null) {
                items += ChangelogItem(scope = null, text = line.trim(), isBullet = false)
                continue
            }
            val scoped = SCOPED_BODY_RE.matchEntire(body)
            items += if (scoped != null) {
                ChangelogItem(scope = scoped.groupValues[1], text = scoped.groupValues[2])
            } else {
                ChangelogItem(scope = null, text = body)
            }
        }
        flush()

        return sections.sortedBy { it.kind.ordinal }
    }

    /**
     * The headings conventional-changelog emits, in its plain and its emoji presets alike.
     * The rest keep the title they were given.
     */
    private fun sectionKindOf(title: String?): ChangelogSection.Kind = when (title?.lowercase()) {
        "features", "new features" -> ChangelogSection.Kind.FEATURES
        "bug fixes" -> ChangelogSection.Kind.FIXES
        "improvements" -> ChangelogSection.Kind.IMPROVEMENTS
        "performance improvements" -> ChangelogSection.Kind.PERFORMANCE
        "updated app support" -> ChangelogSection.Kind.APP_SUPPORT
        else -> ChangelogSection.Kind.OTHER
    }

    /**
     * Find the single entry for an exact [version].
     */
    fun findVersion(entries: List<ChangelogEntry>, version: String): ChangelogEntry? {
        val normalized = version.normalizeVersion()
        return entries.firstOrNull { it.version.normalizeVersion() == normalized }
    }
}
