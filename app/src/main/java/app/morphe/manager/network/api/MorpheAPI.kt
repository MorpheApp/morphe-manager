package app.morphe.manager.network.api

import android.util.Log
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.dto.*
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.network.utils.APIFailure
import app.morphe.manager.network.utils.APIResponse
import app.morphe.manager.network.utils.getOrNull
import app.morphe.manager.util.*
import io.ktor.client.request.header
import io.ktor.client.request.url
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * High-level network layer for Morphe.
 *
 * Responsible for:
 *  - Fetching manager and patches update metadata (GitHub Releases, static JSON, Morphe API)
 *  - Fetching and parsing CHANGELOG.md files from repositories
 *  - Resolving GitHub pull request build artifacts
 *
 * All methods return [APIResponse] or nullable results — no exceptions propagate to callers
 * except [getAssetFromPullRequest], which throws on hard failure.
 */
class MorpheAPI(
    private val client: HttpService,
    private val prefs: PreferencesManager
) {
    /**
     * Parsed GitHub repository coordinates, derived from a github.com or api.github.com URL.
     * Used to build API and raw file URLs without repetition.
     */
    private data class RepoConfig(
        val owner: String,
        val name: String,
        val apiBase: String,
        val htmlUrl: String
    ) {
        /** Returns a raw.githubusercontent.com URL for a file at a specific [branch] and [path]. */
        fun rawFileUrl(branch: String, path: String): String =
            "https://raw.githubusercontent.com/$owner/$name/$branch/$path"
    }

    // Lazy so URL parsing doesn't happen on construction — only when first needed
    private val managerConfig: RepoConfig by lazy { parseRepoUrl(MANAGER_REPO_URL) }
    private val patchesConfig: RepoConfig by lazy { parseRepoUrl(SOURCE_REPO_URL) }

    /**
     * Accepts both human-readable GitHub URLs (https://github.com/owner/repo)
     * and API URLs (https://api.github.com/repos/owner/repo).
     */
    private fun parseRepoUrl(repoUrl: String): RepoConfig {
        val trimmed = repoUrl.removeSuffix("/")
        return when {
            trimmed.startsWith("https://github.com/") -> parseGitHubUrl(trimmed, repoUrl)
            trimmed.startsWith("https://api.github.com/") -> parseGitHubApiUrl(trimmed, repoUrl)
            else -> throw IllegalArgumentException("Unsupported repository URL: $repoUrl")
        }
    }

    private fun parseGitHubUrl(url: String, originalUrl: String): RepoConfig {
        val parts = url.removePrefix("https://github.com/")
            .removeSuffix(".git")
            .split("/")
            .filter { it.isNotBlank() }
        require(parts.size >= 2) { "Invalid GitHub URL: $originalUrl" }
        val (owner, name) = parts
        return RepoConfig(
            owner = owner,
            name = name,
            apiBase = "https://api.github.com/repos/$owner/$name",
            htmlUrl = "https://github.com/$owner/$name"
        )
    }

    private fun parseGitHubApiUrl(url: String, originalUrl: String): RepoConfig {
        val parts = url.removePrefix("https://api.github.com/")
            .trim('/')
            .removeSuffix(".git")
            .split("/")
            .filter { it.isNotBlank() }
        val idx = parts.indexOf("repos")
        val owner = parts.getOrNull(idx + 1)
            ?: throw IllegalArgumentException("Invalid GitHub API URL: $originalUrl")
        val name = parts.getOrNull(idx + 2)
            ?: throw IllegalArgumentException("Invalid GitHub API URL: $originalUrl")
        return RepoConfig(
            owner = owner,
            name = name,
            apiBase = "https://api.github.com/repos/$owner/$name",
            htmlUrl = "https://github.com/$owner/$name"
        )
    }

    /**
     * Makes a GitHub REST API request to [path] relative to the repository API base.
     * Attaches the user's PAT (if configured) as a Bearer token.
     */
    private suspend inline fun <reified T> githubRequest(
        config: RepoConfig,
        path: String
    ): APIResponse<T> {
        val pat = prefs.gitHubPat.get()
        return client.request {
            pat.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            url("${config.apiBase}/${path.trimStart('/')}")
        }
    }

    /**
     * Makes a request to the Morphe backend API at [route], with generic retry on failure.
     *
     * Note: [HttpService.request] already handles 429 retry internally. This adds an extra
     * [HttpService.runWithRetry] layer for transient network errors specific to Morphe API calls.
     */
    private suspend inline fun <reified T> apiRequest(route: String): APIResponse<T> {
        val url = "$MORPHE_API_URL/v2/${route.trimStart('/')}"
        return client.runWithRetry(route) {
            client.request { url(url) }
        }
    }

    /**
     * Fetches a raw file directly from GitHub (raw.githubusercontent.com).
     * Does not attach auth headers — raw files are always public.
     */
    private suspend inline fun <reified T> rawFileRequest(
        config: RepoConfig,
        branch: String,
        path: String
    ): APIResponse<T> {
        val url = config.rawFileUrl(branch, path)
        Log.d(tag, "rawFileRequest: $url")
        return client.request { url(url) }
    }

    /**
     * Maps a [GitHubRelease] + its [GitHubAsset] into the unified [MorpheAsset] format.
     *
     * The release page URL is always set to the GitHub Releases tag page.
     * A matching `.sig` / `.asc` signature asset is automatically attached if present.
     */
    private fun mapReleaseToAsset(
        config: RepoConfig,
        release: GitHubRelease,
        asset: GitHubAsset
    ): MorpheAsset {
        val timestamp = requireNotNull(release.publishedAt ?: release.createdAt) {
            "Release ${release.tagName} has no timestamp"
        }
        return MorpheAsset(
            downloadUrl = asset.downloadUrl,
            createdAt = parseTimestamp(timestamp),
            signatureDownloadUrl = findSignatureUrl(release, asset),
            pageUrl = "${config.htmlUrl}/releases/tag/${release.tagName}",
            description = release.body?.ifBlank { release.name.orEmpty() } ?: release.name.orEmpty(),
            version = release.tagName
        )
    }

    private fun mapManagerJsonToAsset(
        config: RepoConfig,
        releaseInfo: ManagerReleaseInfo
    ): MorpheAsset {
        val version = normalizeVersion(releaseInfo.version)
        return MorpheAsset(
            downloadUrl = releaseInfo.downloadUrl,
            createdAt = parseTimestamp(releaseInfo.createdAt),
            signatureDownloadUrl = releaseInfo.signatureDownloadUrl,
            pageUrl = "${config.htmlUrl}/releases/tag/$version",
            description = releaseInfo.description,
            version = version
        )
    }

    private fun mapPatchesJsonToAsset(
        config: RepoConfig,
        releaseInfo: PatchesReleaseInfo
    ): MorpheAsset {
        val version = normalizeVersion(releaseInfo.version)
        return MorpheAsset(
            downloadUrl = releaseInfo.downloadUrl,
            createdAt = parseTimestamp(releaseInfo.createdAt),
            // Treat empty string the same as absent — some JSON files emit ""
            signatureDownloadUrl = releaseInfo.signatureDownloadUrl?.ifBlank { null },
            pageUrl = "${config.htmlUrl}/releases/tag/$version",
            description = releaseInfo.description,
            version = version
        )
    }

    /**
     * Searches [release] assets for a detached signature file matching [asset].
     *
     * Candidates checked in priority order:
     *  1. `<full-name>.sig`
     *  2. `<full-name>.asc`
     *  3. `<name-without-extension>.sig`
     *  4. `<name-without-extension>.asc`
     */
    private fun findSignatureUrl(release: GitHubRelease, asset: GitHubAsset): String? {
        val base = asset.name.substringBeforeLast('.', asset.name)
        val candidates = setOf(
            "${asset.name}.sig",
            "${asset.name}.asc",
            "$base.sig",
            "$base.asc"
        )
        return release.assets.firstOrNull { it.name in candidates }?.downloadUrl
    }

    /**
     * Parses ISO-8601 / GitHub timestamp strings into [LocalDateTime] (UTC).
     *
     * Handles:
     *  - Full ISO-8601 with Z suffix: `2024-01-15T12:00:00Z`
     *  - With offset: `2024-01-15T12:00:00+02:00`
     *  - Without timezone (assumed UTC): `2024-01-15T12:00:00`
     */
    private fun parseTimestamp(timestamp: String): LocalDateTime {
        val normalized = when {
            timestamp.endsWith("Z", ignoreCase = true) -> timestamp
            timestamp.contains("+") -> timestamp
            timestamp.contains("T") && timestamp.lastIndexOf("-") > 10 -> timestamp
            else -> "${timestamp}Z" // no timezone info — assume UTC
        }
        return Instant.parse(normalized).toLocalDateTime(TimeZone.UTC)
    }

    /** Ensures a version string is prefixed with `v` (e.g. `1.2.3` → `v1.2.3`). */
    private fun normalizeVersion(version: String): String =
        if (version.startsWith("v")) version else "v$version"

    /** Returns true if [asset] looks like an Android APK by name or content-type. */
    private fun isManagerAsset(asset: GitHubAsset): Boolean =
        asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType?.contains("android.package-archive", ignoreCase = true) == true

    /** True when the currently installed manager is a dev/pre-release build. */
    val isDevBuild: Boolean
        get() = BuildConfig.VERSION_NAME.contains('-')

    /** Fetches the latest manager release metadata from GitHub Releases. */
    private suspend fun getManagerFromGitHub(): APIResponse<MorpheAsset> {
        val includePrerelease = prefs.useManagerPrereleases.get()
        return fetchReleaseAsset(managerConfig, includePrerelease, ::isManagerAsset)
    }

    /**
     * Fetches manager metadata from the static JSON endpoint (bypasses GitHub API rate limits).
     *
     * [branch] determines which JSON URL is used (`dev` → prerelease, anything else → stable).
     * Cache-Control: no-cache ensures we never get a stale CDN response.
     */
    private suspend fun getManagerFromJson(branch: String): APIResponse<MorpheAsset> {
        val url = if (branch == "dev") MANAGER_PRERELEASE_JSON_URL else MANAGER_RELEASE_JSON_URL
        return when (val response = client.request<ManagerReleaseInfo> {
            url(url)
            header("Cache-Control", "no-cache")
        }) {
            is APIResponse.Success -> runCatching {
                mapManagerJsonToAsset(managerConfig, response.data).also {
                    Log.d(tag, "Manager JSON ($branch): ${it.version} → ${it.downloadUrl}")
                }
            }.fold(
                onSuccess = { APIResponse.Success(it) },
                onFailure = {
                    Log.w(tag, "Failed to map manager JSON ($branch)", it)
                    APIResponse.Failure(APIFailure(it, null))
                }
            )

            is APIResponse.Error -> APIResponse.Error(response.error)
            is APIResponse.Failure -> APIResponse.Failure(response.error)
        }
    }

    /**
     * Returns a newer [MorpheAsset] if one is available, or null if the app is up to date.
     *
     * Channel selection logic (mirrors FCM subscription matrix in [app.morphe.manager.util.syncFcmTopics]):
     *  - `usePrereleases == true` OR current build is dev → use `dev` branch / prerelease channel
     *  - Otherwise → use `main` branch / stable channel
     *
     * Update sources:
     *  - Primary: static JSON file ([USE_MANAGER_DIRECT_JSON] == true)
     *  - Fallback: GitHub Releases API
     */
    suspend fun getAppUpdate(): MorpheAsset? {
        val usePrereleases = prefs.useManagerPrereleases.get()
        val currentWeight = versionWeight(BuildConfig.VERSION_NAME.removePrefix("v"))
        val branch = if (usePrereleases || isDevBuild) "dev" else "main"

        val candidate = if (USE_MANAGER_DIRECT_JSON) {
            getManagerFromJson(branch).fallbackTo {
                Log.w(tag, "Manager JSON unavailable, falling back to GitHub API")
                getManagerFromGitHub()
            }
        } else {
            getManagerFromGitHub()
        }.getOrNull()

        // Return only if the remote version is strictly newer than what's installed
        return candidate?.takeIf {
            versionWeight(it.version.removePrefix("v")) > currentWeight
        }
    }

    /**
     * Converts a semver-like version string to a comparable [Long] weight.
     *
     * Format: `MAJOR.MINOR.PATCH[-prerelease.N]`
     *
     * Stable releases rank strictly above pre-releases with the same core version:
     * e.g. `1.2.3` > `1.2.3-beta.5`.
     */
    private fun versionWeight(version: String): Long {
        val dashIdx = version.indexOf('-')
        val core = if (dashIdx >= 0) version.substring(0, dashIdx) else version
        val pre = if (dashIdx >= 0) version.substring(dashIdx + 1) else null
        val parts = core.split('.').map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }.toLong()
        val minor = parts.getOrElse(1) { 0 }.toLong()
        val patch = parts.getOrElse(2) { 0 }.toLong()
        // Stable gets a 100_000 bonus so it always beats any pre-release of the same version
        val preWeight = if (pre == null) 100_000L
        else pre.split('.').lastOrNull()?.toLongOrNull() ?: 0L
        return major * 1_000_000_000L + minor * 1_000_000L + patch * 100_000L + preWeight
    }

    /**
     * Fetches patches update from the Morphe backend API.
     * Uses the `/patches/prerelease` endpoint when [usePrerelease] is true.
     */
    private suspend fun getPatchesFromApi(usePrerelease: Boolean): APIResponse<MorpheAsset> {
        val route = if (usePrerelease) "patches/prerelease" else "patches"
        return apiRequest(route)
    }

    /**
     * Fetches patches update from a static `patches-bundle.json` file in the repository.
     * Uses the `dev` branch for pre-releases, `main` for stable.
     */
    private suspend fun getPatchesFromJson(usePrerelease: Boolean): APIResponse<MorpheAsset> {
        val branch = if (usePrerelease) "dev" else "main"
        return when (val r = rawFileRequest<PatchesReleaseInfo>(patchesConfig, branch, "patches-bundle.json")) {
            is APIResponse.Success -> runCatching {
                mapPatchesJsonToAsset(patchesConfig, r.data).also {
                    Log.d(tag, "Patches JSON ($branch): ${it.version} → ${it.downloadUrl}")
                }
            }.fold(
                onSuccess = { APIResponse.Success(it) },
                onFailure = {
                    Log.w(tag, "Failed to map patches JSON ($branch)", it)
                    APIResponse.Failure(APIFailure(it, null))
                }
            )

            is APIResponse.Error -> APIResponse.Error(r.error)
            is APIResponse.Failure -> APIResponse.Failure(r.error)
        }
    }

    /**
     * Returns the latest patches release for a given bundle.
     *
     * [usePrerelease] is passed explicitly so each bundle can track its own channel
     * independently (one bundle may use stable while another uses dev).
     *
     * Source priority: static JSON → Morphe API (fallback).
     */
    suspend fun getPatchesUpdate(usePrerelease: Boolean): APIResponse<MorpheAsset> {
        return if (USE_PATCHES_DIRECT_JSON) {
            getPatchesFromJson(usePrerelease).fallbackTo {
                Log.w(tag, "Patches JSON unavailable, falling back to Morphe API")
                getPatchesFromApi(usePrerelease)
            }
        } else {
            getPatchesFromApi(usePrerelease)
        }
    }

    /**
     * Fetches the most recent GitHub release that passes [matcher] for its assets.
     *
     * Pre-releases are included only when [includePrerelease] is true.
     * Draft releases are always skipped.
     */
    private suspend fun fetchReleaseAsset(
        config: RepoConfig,
        includePrerelease: Boolean,
        matcher: (GitHubAsset) -> Boolean
    ): APIResponse<MorpheAsset> {
        return when (val r = githubRequest<List<GitHubRelease>>(config, "releases")) {
            is APIResponse.Success -> runCatching {
                val release = r.data.firstOrNull { release ->
                    !release.draft
                            && (includePrerelease || !release.prerelease)
                            && release.assets.any(matcher)
                } ?: throw IllegalStateException("No matching release found in ${config.name}")

                mapReleaseToAsset(config, release, release.assets.first(matcher))
            }.fold(
                onSuccess = { APIResponse.Success(it) },
                onFailure = { APIResponse.Failure(APIFailure(it, null)) }
            )

            is APIResponse.Error -> APIResponse.Error(r.error)
            is APIResponse.Failure -> APIResponse.Failure(r.error)
        }
    }

    /** Fetches and parses the manager's CHANGELOG.md from the appropriate branch. */
    suspend fun fetchManagerChangelog(): List<ChangelogEntry> {
        val branch = if (isDevBuild) "dev" else "main"
        return fetchChangelogFromRepo(managerConfig, branch, "app/CHANGELOG.md")
    }

    /** Fetches and parses CHANGELOG.md from the first-party patches repository. */
    suspend fun fetchPatchesChangelog(branch: String = "main"): List<ChangelogEntry> =
        fetchChangelogFromRepo(patchesConfig, branch)

    /**
     * Fetches and parses CHANGELOG.md from an arbitrary raw URL.
     * Used for third-party bundles that follow the Morphe changelog format.
     */
    suspend fun fetchChangelogFromUrl(changelogUrl: String): List<ChangelogEntry> {
        Log.d(tag, "fetchChangelogFromUrl: $changelogUrl")
        return when (val r = client.request<String> { url(changelogUrl) }) {
            is APIResponse.Success -> ChangelogParser.parse(r.data)
            is APIResponse.Error, is APIResponse.Failure -> {
                Log.w(tag, "Failed to fetch changelog from $changelogUrl")
                emptyList()
            }
        }
    }

    private suspend fun fetchChangelogFromRepo(
        config: RepoConfig,
        branch: String,
        path: String = "CHANGELOG.md"
    ): List<ChangelogEntry> {
        val url = config.rawFileUrl(branch, path)
        Log.d(tag, "fetchChangelog: $url")
        return when (val r = client.request<String> { url(url) }) {
            is APIResponse.Success -> ChangelogParser.parse(r.data)
            is APIResponse.Error, is APIResponse.Failure -> {
                Log.w(tag, "Failed to fetch $path for ${config.name}@$branch")
                emptyList()
            }
        }
    }

    /**
     * Derives a raw CHANGELOG.md URL from a `patches-bundle.json` endpoint URL.
     *
     * Examples:
     * ```
     * https://raw.githubusercontent.com/MorpheApp/morphe-patches/main/patches-bundle.json
     * → https://raw.githubusercontent.com/MorpheApp/morphe-patches/main/CHANGELOG.md
     *
     * https://github.com/MorpheApp/morphe-patches/tree/main/...
     * → https://raw.githubusercontent.com/MorpheApp/morphe-patches/main/CHANGELOG.md
     * ```
     *
     * Returns null for unrecognized URL formats.
     */
    fun changelogUrlFromBundleEndpoint(endpoint: String): String? {
        return try {
            val uri = java.net.URI(endpoint)
            val host = uri.host?.lowercase(java.util.Locale.US) ?: return null
            val parts = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() } ?: return null

            when (host) {
                "raw.githubusercontent.com" -> {
                    // path: owner/repo/branch/...
                    if (parts.size < 3) return null
                    val base = parts.take(3).joinToString("/")
                    "https://raw.githubusercontent.com/$base/CHANGELOG.md"
                }

                "github.com" -> {
                    // path: owner/repo  or  owner/repo/tree/branch/...
                    if (parts.size < 2) return null
                    val branch = if (parts.size >= 4 && parts[2] in listOf("tree", "blob")) parts[3] else "main"
                    "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/$branch/CHANGELOG.md"
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches the latest GitHub Actions artifact for a pull request.
     *
     * Steps:
     * 1. Resolve the PR's HEAD commit SHA.
     * 2. Find the workflow run that matches that SHA (paginates through runs if needed).
     * 3. Return the first artifact from that run.
     *
     * @throws Exception if the PR, matching run, or artifact cannot be found.
     */
    suspend fun getAssetFromPullRequest(
        owner: String,
        repo: String,
        pullRequestNumber: String
    ): MorpheAsset {
        val config = RepoConfig(
            owner = owner,
            name = repo,
            apiBase = "https://api.github.com/repos/$owner/$repo",
            htmlUrl = "https://github.com/$owner/$repo"
        )
        val run = getPullRequestRun(config, pullRequestNumber)
        val artifact = getRunArtifact(config, run.id, pullRequestNumber)
        return MorpheAsset(
            downloadUrl = artifact.archiveDownloadUrl,
            createdAt = parseTimestamp(artifact.createdAt),
            pageUrl = "${config.htmlUrl}/pull/$pullRequestNumber",
            description = run.displayTitle,
            version = run.headSha
        )
    }

    /**
     * Finds the GitHub Actions workflow run for a given PR by matching the PR's HEAD SHA.
     *
     * Paginates through runs 100 at a time. Stops when a match is found or the list is empty.
     */
    private suspend fun getPullRequestRun(
        config: RepoConfig,
        pullRequestNumber: String
    ): GitHubActionRun {
        val pull = githubRequest<GitHubPullRequest>(config, "pulls/$pullRequestNumber")
            .successOrThrow("PR #$pullRequestNumber")

        val targetSha = pull.head.sha
        var page = 1

        while (true) {
            val runs = githubRequest<GitHubActionRuns>(
                config,
                "actions/runs?per_page=100&page=$page"
            ).successOrThrow("Workflow runs for PR #$pullRequestNumber (page $page)")

            runs.workflowRuns.firstOrNull { it.headSha == targetSha }?.let { return it }

            if (runs.workflowRuns.isEmpty()) {
                throw Exception("No GitHub Actions run found for PR #$pullRequestNumber (SHA: $targetSha)")
            }

            page++
        }
    }

    private suspend fun getRunArtifact(
        config: RepoConfig,
        runId: String,
        pullRequestNumber: String
    ): GitHubActionArtifact {
        val artifacts = githubRequest<GitHubActionRunArtifacts>(
            config,
            "actions/runs/$runId/artifacts"
        ).successOrThrow("Artifacts for PR #$pullRequestNumber (run $runId)")

        return artifacts.artifacts.firstOrNull()
            ?: throw Exception("No artifacts found for PR #$pullRequestNumber — did the GitHub Action run successfully?")
    }

    /**
     * Returns this response if successful; otherwise invokes [fallback] and returns its result.
     * Used to chain primary → fallback data sources cleanly.
     */
    private inline fun <T> APIResponse<T>.fallbackTo(
        fallback: () -> APIResponse<T>
    ): APIResponse<T> = when (this) {
        is APIResponse.Success -> this
        is APIResponse.Error, is APIResponse.Failure -> fallback()
    }
}

/**
 * Unwraps an [APIResponse] to its data value, or throws a descriptive exception on error.
 * Used where a missing result is unrecoverable (e.g. PR artifact resolution).
 */
fun <T> APIResponse<T>.successOrThrow(context: String): T = when (this) {
    is APIResponse.Success -> data
    is APIResponse.Error -> throw Exception("Failed fetching $context: ${error.message}", error)
    is APIResponse.Failure -> throw Exception("Failed fetching $context: ${error.message}", error)
}
