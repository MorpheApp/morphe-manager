/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import java.net.URI
import java.util.Locale

/** Code host whose repository layout a source link can be read by. */
enum class CodeHost(val key: String, val host: String) {
    GITHUB("github", GITHUB_HOST),
    GITLAB("gitlab", GITLAB_HOST)
}

/**
 * A source link taken apart into its lowercased [host] and decoded path [segments]. Everything
 * read from a link, its repository, owner or branch, is read here, so every place agrees on it.
 */
class SourceUrl private constructor(val host: String, val segments: List<String>) {
    /** The code host whose layout the link follows, or null for any other host. */
    val codeHost: CodeHost? = when (host) {
        GITHUB_HOST, GITHUB_RAW_HOST -> CodeHost.GITHUB
        GITLAB_HOST -> CodeHost.GITLAB
        else -> null
    }

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

    /**
     * Where in [segments] the branch the link points at sits, or null when it names none:
     * - raw.githubusercontent.com/owner/repo/BRANCH/path, unless pinned through refs/heads
     * - github.com/owner/repo/tree|blob/BRANCH/path
     * - gitlab.com/owner/repo/-/raw/BRANCH/path
     */
    val branchIndex: Int?
        get() = when (host) {
            GITHUB_RAW_HOST -> 2.takeIf { segments.size > 2 && segments[2] != "refs" }
            GITHUB_HOST -> 3.takeIf { segments.size > 3 && segments[2] in GITHUB_BRANCH_VIEWS }
            GITLAB_HOST -> segments.indexOf("raw")
                .takeIf { it >= 1 && segments[it - 1] == "-" && it + 1 < segments.size }
                ?.plus(1)
            else -> null
        }

    /** The branch the link points at, see [branchIndex]. */
    val branch: String? get() = branchIndex?.let(segments::get)

    companion object {
        private val GITHUB_BRANCH_VIEWS = setOf("tree", "blob")

        /** Takes [url] apart, or returns null when it is not a URL with a host. */
        fun parse(url: String): SourceUrl? = try {
            val uri = URI(url)
            uri.host?.lowercase(Locale.US)?.let { host ->
                SourceUrl(host, uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() })
            }
        } catch (_: Exception) {
            null
        }
    }
}
