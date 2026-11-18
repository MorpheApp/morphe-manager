/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import io.ktor.http.Url
import io.ktor.http.hostWithPortIfSpecified
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

        /**
         * Turns a link to a source as the user pastes it into the bundle URL the source is read
         * from, so the same source added through different links resolves to one endpoint:
         * - a GitHub repository or tree/blob page becomes its raw.githubusercontent.com file,
         *   a pull request link is kept as is
         * - a GitLab repository or tree/blob page becomes its `/-/raw/` file
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

            val host = parsed.host
            val segments = parsed.encodedPath.trim('/').split('/').filter { it.isNotBlank() }
            val path = "/" + segments.joinToString("/")
            val query = parsed.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()

            return when {
                host.equals(GITHUB_HOST, ignoreCase = true) -> {
                    require(segments.size >= 2) { "Invalid GitHub repository URL" }
                    if (segments.getOrNull(2) == "pull") {
                        val scheme = if (parsed.protocol.name.equals("https", ignoreCase = true)) "https" else "http"
                        "$scheme://$host$path$query"
                    } else {
                        githubRawBundleUrl(segments)
                    }
                }

                host.equals(GITLAB_HOST, ignoreCase = true) -> {
                    require(segments.size >= 2) { "Invalid GitLab repository URL" }
                    // owner/repo/-/VIEW/..., where GitLab puts its own pages after the dash
                    fun viewIndex(view: String) =
                        segments.indexOf(view).takeIf { it >= 2 && segments[it - 1] == "-" }

                    if (viewIndex("raw") != null) {
                        "https://$GITLAB_HOST${requireJsonPath(path)}$query"
                    } else {
                        val branch = (viewIndex("tree") ?: viewIndex("blob"))
                            ?.let { segments.getOrNull(it + 1) }
                            ?: BRANCH_STABLE
                        "https://$GITLAB_HOST/${segments[0]}/${segments[1]}/-/raw/$branch/$DEFAULT_BUNDLE_FILE"
                    }
                }

                host.equals(GITHUB_RAW_HOST, ignoreCase = true) -> {
                    require(segments.size >= 3) { "Invalid raw GitHub URL" }
                    "https://$host${requireJsonPath(path)}$query"
                }

                // Rebuilt from the host and port rather than the host alone, so that a bundle served on a
                // custom port is not silently requested on the protocol default one
                else -> "https://${parsed.hostWithPortIfSpecified}${requireJsonPath(path)}$query"
            }
        }

        /**
         * The raw file a github.com repository page points at. A page without a file points at the
         * default bundle, a branch it does not name is the stable one.
         */
        private fun githubRawBundleUrl(segments: List<String>): String {
            val (owner, repo) = segments
            val (branch, file) = when {
                segments.size >= 4 && segments[2] == "tree" -> {
                    // A tree branch can span segments, such as refs/heads/main
                    val branchSegments = segments.drop(3).takeWhile { !it.endsWith(".$JSON_EXTENSION") }
                    branchSegments.joinToString("/") to segments.drop(3 + branchSegments.size)
                }
                segments.size >= 4 && segments[2] == "blob" -> segments[3] to segments.drop(4)
                else -> BRANCH_STABLE to segments.drop(2)
            }
            val filePath = file.joinToString("/")

            return buildString {
                append("https://$GITHUB_RAW_HOST/$owner/$repo/$branch")
                if (filePath.isNotEmpty()) append("/$filePath")
                if (!filePath.endsWith(".$JSON_EXTENSION", ignoreCase = true)) append("/$DEFAULT_BUNDLE_FILE")
            }
        }

        private fun requireJsonPath(path: String): String {
            require(path.endsWith(".$JSON_EXTENSION", ignoreCase = true)) { "Patch bundle URL must point to a .json file." }
            return path
        }
    }
}
