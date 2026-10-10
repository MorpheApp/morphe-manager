/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/network/api/ReVancedAPI.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val GITHUB_DOWNLOAD_PREFIX = "https://github.com/"

private const val GITHUB_RAW_HOST = "raw.githubusercontent.com"

/** Whether [url] is a file served from a repository by raw.githubusercontent.com. */
internal fun isRawGitHubUrl(url: String): Boolean = runCatching {
    java.net.URI(url).host.equals(GITHUB_RAW_HOST, ignoreCase = true)
}.getOrDefault(false)

/** Coordinates of a single asset inside a GitHub release download link. */
internal data class ReleaseAssetRef(
    val owner: String,
    val repo: String,
    val tag: String,
    val fileName: String
) {
    /** Asset names are compared against both forms because the link may percent-encode them. */
    val decodedFileName: String
        get() = runCatching {
            java.net.URLDecoder.decode(fileName, Charsets.UTF_8.name())
        }.getOrDefault(fileName)
}

/**
 * Splits a github.com release download link into its coordinates.
 *
 * Returns null for anything that is not `/{owner}/{repo}/releases/download/{tag}/{file}`, which
 * keeps callers from turning unrelated URLs into API lookups.
 */
internal fun parseReleaseAssetUrl(downloadUrl: String): ReleaseAssetRef? {
    if (!downloadUrl.startsWith(GITHUB_DOWNLOAD_PREFIX)) return null

    val parts = downloadUrl.removePrefix(GITHUB_DOWNLOAD_PREFIX)
        .substringBefore('?')
        .split("/")
    if (parts.size < 6 || parts[2] != "releases" || parts[3] != "download") return null
    if (parts.take(2).any { it.isBlank() } || parts[4].isBlank()) return null

    val fileName = parts.drop(5).joinToString("/")
    if (fileName.isBlank()) return null

    return ReleaseAssetRef(
        owner = parts[0],
        repo = parts[1],
        tag = parts[4],
        fileName = fileName
    )
}

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
    @PublishedApi internal val client: HttpService,
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

    /**
     * Token GitHub has already turned down, kept so it is not sent again.
     *
     * Repeated failed authentications count against the caller and can get the address throttled,
     * which would be a worse outcome than simply falling back to the anonymous rate limit. Holding
     * the token itself rather than a flag means a newly entered one is tried again.
     */
    @Volatile
    @PublishedApi
    internal var rejectedPat: String? = null

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
     *
     * A rejected token falls back to an anonymous request: most endpoints answer without
     * credentials, and a PAT that has expired or been revoked should cost the lower rate limit
     * rather than the endpoint itself.
     */
    private suspend inline fun <reified T> githubRequest(
        config: RepoConfig,
        path: String
    ): APIResponse<T> {
        val url = "${config.apiBase}/${path.trimStart('/')}"
        val pat = prefs.gitHubPat.get().takeIf { it.isNotBlank() && it != rejectedPat }
            ?: return client.request { url(url) }

        val response: APIResponse<T> = client.request {
            header("Authorization", "Bearer $pat")
            url(url)
        }

        val rejected = response is APIResponse.Error &&
                response.error.statusCode == HttpStatusCode.Unauthorized
        if (!rejected) return response

        rejectedPat = pat
        Log.w(tag, "GitHub rejected the configured PAT, retrying $path anonymously")
        return client.request { url(url) }
    }

    /**
     * Fetches a source's file, retrying a raw.githubusercontent.com 404 with the user's PAT. GitHub
     * answers a stale token with 404 even on public files, so the token only follows a miss.
     */
    suspend inline fun <reified T> rawFileRequest(url: String): APIResponse<T> {
        val response: APIResponse<T> = client.request { url(url) }
        val pat = patForMissingRawFile(url, response) ?: return response
        return client.request {
            header(HttpHeaders.Authorization, "Bearer $pat")
            url(url)
        }
    }

    /** The PAT to retry [url] with after [response], or null when a retry cannot help. */
    @PublishedApi
    internal suspend fun patForMissingRawFile(url: String, response: APIResponse<*>): String? {
        val missing = response is APIResponse.Error && response.error.statusCode == HttpStatusCode.NotFound
        if (!missing || !isRawGitHubUrl(url)) return null
        return prefs.gitHubPat.get().takeIf { it.isNotBlank() && it != rejectedPat }
    }

    /**
     * Makes a request to the Morphe backend API at [route].
     *
     * Note: [HttpService.request] already retries 429 and dropped connections internally, so
     * wrapping it in another retry layer here would only multiply the attempts.
     */
    private suspend inline fun <reified T> apiRequest(route: String): APIResponse<T> {
        val url = "$MORPHE_API_URL/v2/${route.trimStart('/')}"
        return client.request { url(url) }
    }

    /**
     * Fetches a raw file directly from GitHub (raw.githubusercontent.com).
     * Does not attach auth headers, as the Morphe repositories are public.
     */
    private suspend inline fun <reified T> rawPatchesBundleRequest(
        config: RepoConfig,
        branch: String
    ): APIResponse<T> {
        val url = cacheBusted(config.rawFileUrl(branch, "patches-bundle.json"))
        Log.d(tag, "rawPatchesBundleRequest: $url")
        return client.request { url(url) }
    }

    /**
     * Resolves the API URL serving the same bytes as a github.com release download link.
     *
     * Source manifests point their `download_url` at github.com because that is what release
     * tooling emits, but some networks drop that host while leaving api.github.com reachable.
     * The API route redirects to release-assets.githubusercontent.com and supports ranges, so a
     * download behaves identically once switched over.
     *
     * Returns null when [downloadUrl] is not a release link or the asset cannot be located,
     * which leaves the caller with its original error.
     */
    suspend fun releaseAssetApiUrl(downloadUrl: String): String? {
        val ref = parseReleaseAssetUrl(downloadUrl) ?: return null
        val config = runCatching {
            parseRepoUrl("$GITHUB_DOWNLOAD_PREFIX${ref.owner}/${ref.repo}")
        }.getOrNull() ?: return null

        val release = githubRequest<GitHubRelease>(config, "releases/tags/${ref.tag}").getOrNull()
            ?: return null

        return release.assets
            .firstOrNull { it.name == ref.fileName || it.name == ref.decodedFileName }
            ?.url
            .also { Log.d(tag, "releaseAssetApiUrl: $downloadUrl -> $it") }
    }

    /**
     * Appends a per-minute cache buster to a raw file URL.
     *
     * The GitHub raw CDN ignores request cache headers and serves an edge copy for several
     * minutes, which is long enough for the release JSON and CHANGELOG.md to disagree about
     * the newest version. A per-minute key bypasses that without defeating caching entirely.
     */
    private fun cacheBusted(url: String): String {
        val minute = System.currentTimeMillis() / 60_000L
        return if ('?' in url) "$url&t=$minute" else "$url?t=$minute"
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
            pageUrl = releasePageUrl(config.htmlUrl, release.tagName),
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
            pageUrl = releasePageUrl(config.htmlUrl, version),
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
            pageUrl = releasePageUrl(config.htmlUrl, version),
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

    /** Ensures a version string is prefixed with `v`, so `1.2.3` becomes `v1.2.3`. */
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
     * The request is cache busted because the raw CDN serves stale copies of freshly pushed files.
     */
    private suspend fun getManagerFromJson(branch: String): APIResponse<MorpheAsset> {
        val url = cacheBusted(if (branch == "dev") MANAGER_PRERELEASE_JSON_URL else MANAGER_RELEASE_JSON_URL)
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
     *
     * A newer version is reported only once its APK is downloadable, so a release that is
     * already announced but still uploading is treated as if it did not exist yet.
     */
    suspend fun getAppUpdate(): MorpheAsset? {
        val usePrereleases = prefs.useManagerPrereleases.get()
        val currentWeight = versionWeight(BuildConfig.VERSION_NAME.removePrefix("v"))
        val branch = if (usePrereleases) "dev" else "main"

        val candidate = if (USE_MANAGER_DIRECT_JSON) {
            getManagerFromJson(branch).fallbackTo {
                Log.w(tag, "Manager JSON unavailable, falling back to GitHub API")
                getManagerFromGitHub()
            }
        } else {
            getManagerFromGitHub()
        }.getOrNull()

        // Return only if the remote version is strictly newer than what's installed. The stable
        // channel offers stable builds alone: merging dev into main carries the dev release file
        // along, and it stays there until the stable release replaces it
        val update = candidate?.takeIf {
            (usePrereleases || !it.version.contains('-')) &&
                    versionWeight(it.version.removePrefix("v")) > currentWeight
        } ?: return null

        // Only a definitive "not there" hides the update: a check that could not run at all
        // must not keep a real release from being offered
        if (client.isReachable(update.downloadUrl) == false) {
            Log.d(tag, "Manager update ${update.version} is announced but its APK is not downloadable yet")
            return null
        }

        return update
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
        return when (val r = rawPatchesBundleRequest<PatchesReleaseInfo>(patchesConfig, branch)) {
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
    suspend fun fetchManagerChangelog(forDevBranch: Boolean = isDevBuild): List<ChangelogEntry> {
        val branch = if (forDevBranch) "dev" else "main"
        return fetchChangelogFromRepo(managerConfig, branch, "CHANGELOG.md")
    }

    /** Fetches and parses CHANGELOG.md from the first-party patches repository. */
    suspend fun fetchPatchesChangelog(branch: String = "main", stopAfterFirstStable: Boolean = false): List<ChangelogEntry> =
        fetchChangelogFromRepo(patchesConfig, branch, stopAfterFirstStable = stopAfterFirstStable)

    /**
     * Fetches and parses CHANGELOG.md from an arbitrary raw URL.
     * Used for third-party bundles that follow the Morphe changelog format.
     */
    suspend fun fetchChangelogFromUrl(changelogUrl: String, stopAfterFirstStable: Boolean = false): List<ChangelogEntry> {
        Log.d(tag, "fetchChangelogFromUrl: $changelogUrl")
        return parseChangelog(rawFileRequest<String>(changelogUrl), stopAfterFirstStable, changelogUrl)
    }

    private suspend fun fetchChangelogFromRepo(
        config: RepoConfig,
        branch: String,
        path: String = "CHANGELOG.md",
        stopAfterFirstStable: Boolean = false
    ): List<ChangelogEntry> {
        val url = cacheBusted(config.rawFileUrl(branch, path))
        Log.d(tag, "fetchChangelog: $url")
        val response = client.request<String> {
            url(url)
            header("Cache-Control", "no-cache")
        }
        return parseChangelog(response, stopAfterFirstStable, "$path for ${config.name}@$branch")
    }

    /**
     * Parses a fetched changelog, a missing file reading as an empty one. Any other failure, a lost
     * connection above all, is thrown, so it is neither shown nor cached as a changelog with nothing in it.
     */
    private suspend fun parseChangelog(
        response: APIResponse<String>,
        stopAfterFirstStable: Boolean,
        source: String
    ): List<ChangelogEntry> = when (response) {
        is APIResponse.Success -> withContext(Dispatchers.Default) {
            ChangelogParser.parse(response.data, stopAfterFirstStable)
        }
        is APIResponse.Error -> {
            Log.w(tag, "Failed to fetch changelog from $source: ${response.error.statusCode}")
            if (response.error.statusCode == HttpStatusCode.NotFound) emptyList() else throw response.error
        }
        is APIResponse.Failure -> {
            Log.w(tag, "Failed to fetch changelog from $source", response.error)
            throw response.error
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

                "gitlab.com" -> {
                    // path: owner/repo/-/raw/branch/...
                    if (parts.size < 2) return null
                    val rawIndex = parts.indexOf("raw")
                    val branch = if (rawIndex >= 0 && parts.getOrNull(rawIndex - 1) == "-") {
                        parts.getOrNull(rawIndex + 1) ?: "main"
                    } else "main"
                    "https://gitlab.com/${parts[0]}/${parts[1]}/-/raw/$branch/CHANGELOG.md"
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
     * Resolves a redirect URL by delegating a HEAD request to [HttpService.headRedirect].
     * Returns the final destination URL, or null if there is no redirect or an error occurs.
     */
    suspend fun resolveRedirect(url: String): String? = client.headRedirect(url)

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
