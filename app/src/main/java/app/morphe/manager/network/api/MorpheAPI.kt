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

class MorpheAPI(
    private val client: HttpService,
    private val prefs: PreferencesManager
) {
    // ============================================================================
    // REGION: Data Models
    // ============================================================================

    private data class RepoConfig(
        val owner: String,
        val name: String,
        val apiBase: String,
        val htmlUrl: String,
    ) {
        fun rawFileUrl(branch: String, path: String): String =
            "https://raw.githubusercontent.com/$owner/$name/$branch/$path"
    }

    // ============================================================================
    // REGION: Repository Configuration
    // ============================================================================

    private val managerConfig: RepoConfig by lazy { parseRepoUrl(MANAGER_REPO_URL) }
    private val patchesConfig: RepoConfig by lazy { parseRepoUrl(SOURCE_REPO_URL) }

    private fun parseRepoUrl(repoUrl: String): RepoConfig {
        val trimmed = repoUrl.removeSuffix("/")

        return when {
            trimmed.startsWith("https://github.com/") -> parseGitHubUrl(trimmed, repoUrl)
            trimmed.startsWith("https://api.github.com/") -> parseGitHubApiUrl(trimmed, repoUrl)
            else -> throw IllegalArgumentException("Unsupported repository URL: $repoUrl")
        }
    }

    private fun parseGitHubUrl(url: String, originalUrl: String): RepoConfig {
        val repoPath = url.removePrefix("https://github.com/").removeSuffix(".git")
        val parts = repoPath.split("/").filter { it.isNotBlank() }
        require(parts.size >= 2) { "Invalid GitHub repository URL: $originalUrl" }

        val owner = parts[0]
        val name = parts[1]

        return RepoConfig(
            owner = owner,
            name = name,
            apiBase = "https://api.github.com/repos/$owner/$name",
            htmlUrl = "https://github.com/$owner/$name"
        )
    }

    private fun parseGitHubApiUrl(url: String, originalUrl: String): RepoConfig {
        val repoPath = url.removePrefix("https://api.github.com/").trim('/').removeSuffix(".git")
        val parts = repoPath.split("/").filter { it.isNotBlank() }
        val reposIndex = parts.indexOf("repos")

        val owner = parts.getOrNull(reposIndex + 1)
            ?: throw IllegalArgumentException("Invalid GitHub API URL: $originalUrl")
        val name = parts.getOrNull(reposIndex + 2)
            ?: throw IllegalArgumentException("Invalid GitHub API URL: $originalUrl")

        return RepoConfig(
            owner = owner,
            name = name,
            apiBase = "https://api.github.com/repos/$owner/$name",
            htmlUrl = "https://github.com/$owner/$name"
        )
    }

    // ============================================================================
    // REGION: HTTP Request Helpers
    // ============================================================================

    private suspend inline fun <reified T> githubRequest(
        config: RepoConfig,
        path: String
    ): APIResponse<T> {
        val normalizedPath = path.trimStart('/')
        val pat = prefs.gitHubPat.get()

        return client.request {
            pat.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            url("${config.apiBase}/$normalizedPath")
        }
    }

    private suspend inline fun <reified T> apiRequest(route: String): APIResponse<T> {
        val normalizedRoute = route.trimStart('/')
        val urlString = "$MORPHE_API_URL/v2/$normalizedRoute"
        return client.runWithRetry(route) {
            client.request {
                url(urlString)
            }
        }
    }

    private suspend inline fun <reified T> rawFileRequest(
        config: RepoConfig,
        branch: String,
        path: String
    ): APIResponse<T> {
        val url = config.rawFileUrl(branch, path)
        Log.d(tag, "Fetching from raw file: $url")

        return client.request {
            url(url)
        }
    }

    // ============================================================================
    // REGION: Asset Mapping & Utilities
    // ============================================================================

    private fun mapReleaseToAsset(
        config: RepoConfig,
        release: GitHubRelease,
        asset: GitHubAsset
    ): MorpheAsset {
        val timestamp = release.publishedAt ?: release.createdAt
        require(timestamp != null) { "Release ${release.tagName} does not contain a timestamp" }

        val createdAt = parseTimestamp(timestamp)
        val signatureUrl = findSignatureUrl(release, asset)
        val description = release.body?.ifBlank { release.name.orEmpty() } ?: release.name.orEmpty()

        return MorpheAsset(
            downloadUrl = asset.downloadUrl,
            createdAt = createdAt,
            signatureDownloadUrl = signatureUrl,
            pageUrl = "${config.htmlUrl}/releases/tag/${release.tagName}",
            description = description,
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
            signatureDownloadUrl = releaseInfo.signatureDownloadUrl?.ifBlank { null },
            pageUrl = "${config.htmlUrl}/releases/tag/$version",
            description = releaseInfo.description,
            version = version
        )
    }

    private fun findSignatureUrl(release: GitHubRelease, asset: GitHubAsset): String? {
        val base = asset.name.substringBeforeLast('.', asset.name)
        val candidates = listOf(
            "${asset.name}.sig",
            "${asset.name}.asc",
            "$base.sig",
            "$base.asc"
        )
        return release.assets.firstOrNull { it.name in candidates }?.downloadUrl
    }

    private fun parseTimestamp(timestamp: String): LocalDateTime {
        // Handle different timestamp formats
        val normalized = when {
            // Already has timezone info
            timestamp.endsWith("Z", ignoreCase = true) ||
                    timestamp.contains("+") ||
                    timestamp.contains("T") && timestamp.lastIndexOf("-") > 10 -> timestamp

            // Missing timezone - add Z for UTC
            else -> "${timestamp}Z"
        }

        return Instant.parse(normalized).toLocalDateTime(TimeZone.UTC)
    }

    private fun normalizeVersion(version: String): String =
        if (version.startsWith("v")) version else "v$version"

    private fun isManagerAsset(asset: GitHubAsset): Boolean =
        asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType?.contains("android.package-archive", ignoreCase = true) == true

    // ============================================================================
    // REGION: Manager Updates
    // ============================================================================

    /**
     * Get latest manager update using GitHub API
     */
    private suspend fun getManagerFromGitHub(): APIResponse<MorpheAsset> {
        val includePrerelease = prefs.useManagerPrereleases.get()
        return fetchReleaseAsset(managerConfig, includePrerelease, ::isManagerAsset)
    }

    /**
     * Get manager release info from static JSON file
     */
    private suspend fun getManagerFromJson(): APIResponse<MorpheAsset> {
        val includePrerelease = prefs.useManagerPrereleases.get()
        val branch = if (includePrerelease) "dev" else "main"

        return when (val response = rawFileRequest<ManagerReleaseInfo>(managerConfig, branch, "app/app-release.json")) {
            is APIResponse.Success -> {
                val mapped = runCatching {
                    mapManagerJsonToAsset(managerConfig, response.data).also { asset ->
                        Log.d(tag, "Manager from JSON: version=${asset.version}, url=${asset.downloadUrl}")
                    }
                }

                mapped.fold(
                    onSuccess = { APIResponse.Success(it) },
                    onFailure = { error ->
                        Log.w(tag, "Failed to parse manager JSON", error)
                        APIResponse.Failure(APIFailure(error, null))
                    }
                )
            }
            is APIResponse.Error -> APIResponse.Error(response.error)
            is APIResponse.Failure -> APIResponse.Failure(response.error)
        }
    }

    /**
     * Get latest manager info - uses JSON or GitHub API based on configuration
     */
    suspend fun getLatestAppInfoFromJson(): APIResponse<MorpheAsset> {
        return if (USE_MANAGER_DIRECT_JSON) {
            getManagerFromJson().fallbackTo {
                Log.w(tag, "Falling back to GitHub API for manager")
                getManagerFromGitHub()
            }
        } else {
            getManagerFromGitHub()
        }
    }

    /**
     * Get latest manager info using GitHub API only
     */
    suspend fun getLatestAppInfo(): APIResponse<MorpheAsset> = getManagerFromGitHub()

    /**
     * Get app update - returns update info only if newer version is available
     */
    suspend fun getAppUpdate(): MorpheAsset? {
        val asset = getLatestAppInfoFromJson().getOrNull() ?: return null
        return asset.takeIf { it.version.removePrefix("v") != BuildConfig.VERSION_NAME }
    }

    /**
     * Get manager release by specific version tag
     */
    suspend fun getManagerReleaseByVersion(version: String): APIResponse<MorpheAsset> {
        val normalizedVersion = normalizeVersion(version)

        return when (val response = githubRequest<GitHubRelease>(managerConfig, "releases/tags/$normalizedVersion")) {
            is APIResponse.Success -> {
                runCatching {
                    val release = response.data
                    val asset = release.assets.firstOrNull(::isManagerAsset)
                        ?: throw IllegalStateException("No manager APK found in release $normalizedVersion")
                    mapReleaseToAsset(managerConfig, release, asset)
                }.fold(
                    onSuccess = { APIResponse.Success(it) },
                    onFailure = { APIResponse.Failure(APIFailure(it, null)) }
                )
            }
            is APIResponse.Error -> APIResponse.Error(response.error)
            is APIResponse.Failure -> APIResponse.Failure(response.error)
        }
    }

    // ============================================================================
    // REGION: Patches Updates
    // ============================================================================

    /**
     * Get patches update from Morphe API
     */
    private suspend fun getPatchesFromApi(): APIResponse<MorpheAsset> {
        val usePrerelease = prefs.usePatchesPrereleases.get()
        val route = if (usePrerelease) "patches/prerelease" else "patches"
        return apiRequest(route)
    }

    /**
     * Get patches update from static JSON file
     */
    private suspend fun getPatchesFromJson(): APIResponse<MorpheAsset> {
        val usePrerelease = prefs.usePatchesPrereleases.get()
        val branch = if (usePrerelease) "dev" else "main"

        return when (val response = rawFileRequest<PatchesReleaseInfo>(patchesConfig, branch, "patches-bundle.json")) {
            is APIResponse.Success -> {
                val mapped = runCatching {
                    mapPatchesJsonToAsset(patchesConfig, response.data).also { asset ->
                        Log.d(tag, "Patches from JSON: version=${asset.version}, url=${asset.downloadUrl}")
                    }
                }

                mapped.fold(
                    onSuccess = { APIResponse.Success(it) },
                    onFailure = { error ->
                        Log.w(tag, "Failed to parse patches JSON", error)
                        APIResponse.Failure(APIFailure(error, null))
                    }
                )
            }
            is APIResponse.Error -> APIResponse.Error(response.error)
            is APIResponse.Failure -> APIResponse.Failure(response.error)
        }
    }

    /**
     * Get patches update - uses JSON or API based on configuration
     */
    suspend fun getPatchesUpdate(): APIResponse<MorpheAsset> {
        return if (USE_PATCHES_DIRECT_JSON) {
            getPatchesFromJson().fallbackTo {
                Log.w(tag, "Falling back to Morphe API for patches")
                getPatchesFromApi()
            }
        } else {
            getPatchesFromApi()
        }
    }

    // ============================================================================
    // REGION: GitHub Release Asset Fetching
    // ============================================================================

    private suspend fun fetchReleaseAsset(
        config: RepoConfig,
        includePrerelease: Boolean,
        matcher: (GitHubAsset) -> Boolean
    ): APIResponse<MorpheAsset> {
        return when (val response = githubRequest<List<GitHubRelease>>(config, "releases")) {
            is APIResponse.Success -> {
                runCatching {
                    val release = response.data.firstOrNull { release ->
                        !release.draft &&
                                (includePrerelease || !release.prerelease) &&
                                release.assets.any(matcher)
                    } ?: throw IllegalStateException("No matching release found")

                    val asset = release.assets.first(matcher)
                    mapReleaseToAsset(config, release, asset)
                }.fold(
                    onSuccess = { APIResponse.Success(it) },
                    onFailure = { APIResponse.Failure(APIFailure(it, null)) }
                )
            }
            is APIResponse.Error -> APIResponse.Error(response.error)
            is APIResponse.Failure -> APIResponse.Failure(response.error)
        }
    }

    // ============================================================================
    // REGION: Pull Request Artifacts
    // ============================================================================

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
                throw Exception("No GitHub Actions run found for PR #$pullRequestNumber with SHA $targetSha")
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
        ).successOrThrow("PR artifacts for PR #$pullRequestNumber")

        return artifacts.artifacts.firstOrNull()
            ?: throw Exception("The latest commit in this PR didn't have any artifacts. Did the GitHub action run correctly?")
    }

    // ============================================================================
    // REGION: Helper Extensions
    // ============================================================================

    private inline fun <T> APIResponse<T>.fallbackTo(
        fallback: () -> APIResponse<T>
    ): APIResponse<T> {
        return when (this) {
            is APIResponse.Success -> this
            is APIResponse.Error, is APIResponse.Failure -> fallback()
        }
    }
}

fun <T> APIResponse<T>.successOrThrow(context: String): T {
    return when (this) {
        is APIResponse.Success -> data
        is APIResponse.Error -> throw Exception("Failed fetching $context: ${error.message}", error)
        is APIResponse.Failure -> throw Exception("Failed fetching $context: ${error.message}", error)
    }
}
