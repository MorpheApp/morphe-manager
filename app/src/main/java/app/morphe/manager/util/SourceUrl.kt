/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import io.ktor.http.Url
import io.ktor.http.hostWithPortIfSpecified
import java.net.URI
import java.util.Locale

/**
 * Code host whose repository layout a source link can be read by. Everything that differs from
 * one host to another, the raw file, issues and release paths and the owner's avatar, is here.
 *
 * @param key Name of the host in add-source links, the Morphe API and the blocklist.
 * @param host Host of the repository pages.
 * @param label Name of the host as the user knows it.
 * @param rawHost Host serving the raw files of a repository.
 */
enum class CodeHost(
    val key: String,
    val host: String,
    val label: String,
    val rawHost: String = host
) {
    GITHUB("github", GITHUB_HOST, "GitHub", rawHost = GITHUB_RAW_HOST) {
        override val issuesPath = "issues"
        override fun releasePath(tag: String) = "releases/tag/$tag"
        override fun rawFileUrl(owner: String, repo: String, branch: String, path: String) =
            "https://$GITHUB_RAW_HOST/$owner/$repo/$branch/$path"
        override fun avatarUrl(owner: String) = "https://$GITHUB_HOST/$owner.png"

        override fun isRawFile(host: String, segments: List<String>) = host == GITHUB_RAW_HOST

        // raw.githubusercontent.com/owner/repo/BRANCH/path, unless pinned through refs/heads,
        // or github.com/owner/repo/tree|blob/BRANCH/path
        override fun branchIndex(host: String, segments: List<String>): Int? =
            if (host == GITHUB_RAW_HOST) 2.takeIf { segments.size > 2 && segments[2] != "refs" }
            else 3.takeIf { segments.size > 3 && segments[2] in BRANCH_VIEWS }
    },

    GITLAB("gitlab", GITLAB_HOST, "GitLab") {
        override val issuesPath = "-/issues"
        override fun releasePath(tag: String) = "-/releases/$tag"
        override fun rawFileUrl(owner: String, repo: String, branch: String, path: String) =
            "https://$GITLAB_HOST/$owner/$repo/-/raw/$branch/$path"
        // GitLab serves no avatar by username
        override fun avatarUrl(owner: String) = "https://unavatar.io/gitlab/$owner"

        override fun isRawFile(host: String, segments: List<String>) = viewIndex(segments, RAW_VIEW) != null

        // gitlab.com/owner/repo/-/raw|tree|blob/BRANCH/path, GitLab puts its own pages after the dash
        override fun branchIndex(host: String, segments: List<String>): Int? =
            GITLAB_VIEWS.firstNotNullOfOrNull { viewIndex(segments, it) }
                ?.plus(1)?.takeIf { it < segments.size }

        private fun viewIndex(segments: List<String>, view: String) =
            segments.indexOf(view).takeIf { it >= 3 && segments[it - 1] == "-" }
    },

    CODEBERG("codeberg", CODEBERG_HOST, "Codeberg") {
        override val issuesPath = "issues"
        override fun releasePath(tag: String) = "releases/tag/$tag"
        override fun rawFileUrl(owner: String, repo: String, branch: String, path: String) =
            "https://$CODEBERG_HOST/$owner/$repo/raw/branch/$branch/$path"
        override fun avatarUrl(owner: String) = "https://$CODEBERG_HOST/$owner.png"

        override fun isRawFile(host: String, segments: List<String>) = segments.getOrNull(2) == RAW_VIEW

        // codeberg.org/owner/repo/raw|src/branch/BRANCH/path. A raw link may also name a tag or
        // a commit, or the branch right after raw, and a source on those does not follow a channel
        override fun branchIndex(host: String, segments: List<String>): Int? =
            4.takeIf { segments.size > 4 && segments[2] in CODEBERG_VIEWS && segments[3] == "branch" }
    };

    /** Path of the issues page under the repository page. */
    abstract val issuesPath: String

    /** Path of the page of the release tagged [tag] under the repository page. */
    abstract fun releasePath(tag: String): String

    /** The file at [path] on [branch] of the repository, as the host serves it raw. */
    abstract fun rawFileUrl(owner: String, repo: String, branch: String, path: String): String

    /** Picture of the user or organization [owner]. */
    abstract fun avatarUrl(owner: String): String

    /** Whether a link on [host] with path [segments] already points at a raw file. */
    abstract fun isRawFile(host: String, segments: List<String>): Boolean

    /** Where in [segments] the branch a link on [host] points at sits, or null when it names none. */
    abstract fun branchIndex(host: String, segments: List<String>): Int?

    companion object {
        private const val RAW_VIEW = "raw"
        private val BRANCH_VIEWS = setOf("tree", "blob")
        private val GITLAB_VIEWS = BRANCH_VIEWS + RAW_VIEW
        private val CODEBERG_VIEWS = setOf(RAW_VIEW, "src")

        /** The code host serving pages or raw files on the lowercased [host], or null for any other. */
        fun of(host: String): CodeHost? = entries.firstOrNull { host == it.host || host == it.rawHost }
    }
}

/**
 * A source link taken apart into its lowercased [host] and decoded path [segments]. Everything
 * read from a link, its repository, owner or branch, is read here, so every place agrees on it.
 */
class SourceUrl private constructor(val host: String, val segments: List<String>) {
    /** The code host whose layout the link follows, or null for any other host. */
    val codeHost: CodeHost? = CodeHost.of(host)

    /** Owner of the repository on a known code host, present as soon as the link names one. */
    val owner: String? get() = segments.firstOrNull()?.takeIf { codeHost != null }

    /** Name of the repository on a known code host. */
    val repo: String? get() = segments.getOrNull(1)?.takeIf { codeHost != null }

    /** Page of the repository, whatever file, branch or pull request the link points at. */
    val repositoryUrl: String?
        get() = repo?.let { "https://${codeHost!!.host}/$owner/$it" }

    /** The repository as `github=owner/repo`, the form the Morphe API and blocklist name it by. */
    val repositoryKey: String?
        get() = repo?.let { "${codeHost!!.key}=$owner/$it" }

    /** Issues page of the repository. */
    val issuesUrl: String?
        get() = repositoryUrl?.let { "$it/${codeHost!!.issuesPath}" }

    /** Picture of the repository owner, see [CodeHost.avatarUrl]. */
    val ownerAvatarUrl: String?
        get() = owner?.let { codeHost!!.avatarUrl(it) }

    /** Where in [segments] the branch the link points at sits, see [CodeHost.branchIndex]. */
    private val branchIndex: Int?
        get() = repo?.let { codeHost!!.branchIndex(host, segments) }

    /** The branch the link points at, see [branchIndex]. */
    val branch: String? get() = branchIndex?.let(segments::get)

    /** Path of the file the link points at within its branch, or null when it names no branch. */
    private val filePath: String?
        get() = branchIndex?.let { segments.drop(it + 1).joinToString("/") }

    /**
     * The raw file at [path] on [branch] of the repository, or null for a link to no repository.
     * [path] defaults to the file the link itself points at, and a link to no file gives null.
     */
    fun rawFileUrl(branch: String, path: String? = filePath): String? {
        val repo = repo ?: return null
        val file = path?.takeIf { it.isNotEmpty() } ?: return null
        return codeHost!!.rawFileUrl(owner!!, repo, branch, file)
    }

    companion object {
        /** Takes [url] apart, or returns null when it is not a URL with a host. */
        fun parse(url: String): SourceUrl? = try {
            val uri = URI(url)
            uri.host?.lowercase(Locale.US)?.let { host ->
                SourceUrl(host, uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() })
            }
        } catch (_: Exception) {
            null
        }

        /**
         * Turns a link to a source as the user pastes it into the bundle URL the source is read
         * from, so the same source added through different links resolves to one endpoint:
         * - a repository or branch page on a [CodeHost] becomes its raw file, the default bundle
         *   on the stable branch unless the page names others
         * - a GitHub pull request link is kept as is
         * - a raw or any other link must point at a JSON file and is kept with its query
         *
         * @throws IllegalArgumentException when [input] cannot name a bundle.
         */
        fun normalizeBundleUrl(input: String): String {
            // A link is usually pasted without its scheme, and every host here serves https
            val trimmed = input.trim().let { if ("://" in it) it else "https://$it" }
            val parsed = try {
                Url(trimmed)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid bundle URL: ${e.message ?: trimmed}")
            }

            val host = parsed.host.lowercase(Locale.US)
            val segments = parsed.encodedPath.trim('/').split('/').filter { it.isNotBlank() }
            val path = "/" + segments.joinToString("/")
            val query = parsed.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
            val codeHost = CodeHost.of(host)
                // Rebuilt from the host and port rather than the host alone, so that a bundle served on a
                // custom port is not silently requested on the protocol default one
                ?: return "https://${parsed.hostWithPortIfSpecified}${requireJsonPath(path)}$query"

            if (codeHost.isRawFile(host, segments)) {
                require(segments.size >= 3) { "Invalid raw ${codeHost.label} URL" }
                return "https://$host${requireJsonPath(path)}$query"
            }

            require(segments.size >= 2) { "Invalid ${codeHost.label} repository URL" }
            if (codeHost == CodeHost.GITHUB && segments.getOrNull(2) == "pull") {
                return "https://$host$path$query"
            }

            val branchIndex = codeHost.branchIndex(host, segments)
            val branch = branchIndex?.let(segments::get) ?: BRANCH_STABLE
            // A page names a folder or a file, and the bundle of a folder is the default one in it
            val file = branchIndex?.let { segments.drop(it + 1).joinToString("/") }.orEmpty()
            val bundleFile = when {
                file.endsWith(".$JSON_EXTENSION", ignoreCase = true) -> file
                file.isEmpty() -> DEFAULT_BUNDLE_FILE
                else -> "$file/$DEFAULT_BUNDLE_FILE"
            }
            return codeHost.rawFileUrl(segments[0], segments[1], branch, bundleFile)
        }

        private fun requireJsonPath(path: String): String {
            require(path.endsWith(".$JSON_EXTENSION", ignoreCase = true)) { "Patch bundle URL must point to a .json file." }
            return path
        }
    }
}
