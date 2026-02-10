package app.morphe.manager.network.api

import android.util.Log
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.dto.*
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.network.utils.APIFailure
import app.morphe.manager.network.utils.APIResponse
import app.morphe.manager.network.utils.getOrNull
import app.morphe.manager.util.MANAGER_REPO_URL
import app.morphe.manager.util.MORPHE_API_URL
import app.morphe.manager.util.tag
import io.ktor.client.request.header
import io.ktor.client.request.url
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MorpheAPI(
    private val client: HttpService,
    private val prefs: PreferencesManager
) {
    private data class RepoConfig(
        val owner: String,
        val name: String,
        val apiBase: String,
        val htmlUrl: String,
    )

    private fun repoConfig(): RepoConfig {
        val trimmed = MANAGER_REPO_URL.removeSuffix("/")
        return when {
            trimmed.startsWith("https://github.com/") -> {
                val repoPath = trimmed.removePrefix("https://github.com/").removeSuffix(".git")
                val parts = repoPath.split("/").filter { it.isNotBlank() }
                require(parts.size >= 2) { "Invalid GitHub repository URL: $MANAGER_REPO_URL" }
                val owner = parts[0]
                val name = parts[1]
                RepoConfig(
                    owner = owner,
                    name = name,
                    apiBase = "https://api.github.com/repos/$owner/$name",
                    htmlUrl = "https://github.com/$owner/$name"
                )
            }

            trimmed.startsWith("https://api.github.com/") -> {
                val repoPath = trimmed.removePrefix("https://api.github.com/").trim('/').removeSuffix(".git")
                val parts = repoPath.split("/").filter { it.isNotBlank() }
                val reposIndex = parts.indexOf("repos")
                val owner = parts.getOrNull(reposIndex + 1)
                    ?: throw IllegalArgumentException("Invalid GitHub API URL: $MANAGER_REPO_URL")
                val name = parts.getOrNull(reposIndex + 2)
                    ?: throw IllegalArgumentException("Invalid GitHub API URL: $MANAGER_REPO_URL")
                RepoConfig(
                    owner = owner,
                    name = name,
                    apiBase = "https://api.github.com/repos/$owner/$name",
                    htmlUrl = "https://github.com/$owner/$name"
                )
            }

            else -> throw IllegalArgumentException("Unsupported repository URL: $MANAGER_REPO_URL")
        }
    }

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

    private fun apiUrl(): String = MORPHE_API_URL

    private suspend inline fun <reified T> apiRequest(route: String): APIResponse<T> {
        val normalizedRoute = route.trimStart('/')
        val baseUrl = apiUrl()
        return client.request {
            url("$baseUrl/v2/$normalizedRoute")
        }
    }

    private suspend fun fetchReleaseAsset(
        config: RepoConfig,
        includePrerelease: Boolean,
        matcher: (GitHubAsset) -> Boolean
    ): APIResponse<MorpheAsset> {
        return when (val releasesResponse = githubRequest<List<GitHubRelease>>(config, "releases")) {
            is APIResponse.Success -> {
                val mapped = runCatching {
                    val release = releasesResponse.data.firstOrNull { release ->
                        !release.draft && (includePrerelease || !release.prerelease) && release.assets.any(matcher)
                    } ?: throw IllegalStateException("No matching release found")

                    val asset = release.assets.first(matcher)
                    mapReleaseToAsset(config, release, asset)
                }

                mapped.fold(
                    onSuccess = { APIResponse.Success(it) },
                    onFailure = { APIResponse.Failure(APIFailure(it, null)) }
                )
            }

            is APIResponse.Error -> APIResponse.Error(releasesResponse.error)
            is APIResponse.Failure -> APIResponse.Failure(releasesResponse.error)
        }
    }

    private fun mapReleaseToAsset(
        config: RepoConfig,
        release: GitHubRelease,
        asset: GitHubAsset
    ): MorpheAsset {
        val timestamp = release.publishedAt ?: release.createdAt
        require(timestamp != null) { "Release ${release.tagName} does not contain a timestamp" }
        val createdAt = Instant.parse(timestamp).toLocalDateTime(TimeZone.UTC)
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

    private fun isManagerAsset(asset: GitHubAsset) =
        asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType?.contains("android.package-archive", ignoreCase = true) == true


    suspend fun getLatestAppInfo(): APIResponse<MorpheAsset> {
        val config = repoConfig()
        val includePrerelease = prefs.useManagerPrereleases.get()
        return fetchReleaseAsset(config, includePrerelease, ::isManagerAsset)
    }

    /**
     * Get manager release info from static JSON file
     *
     * @param usePrerelease If true, fetches from 'dev' branch, otherwise from 'main'
     */
    private suspend fun getManagerReleaseFromJson(usePrerelease: Boolean): APIResponse<ManagerReleaseInfo> {
        val config = repoConfig()
        val branch = if (usePrerelease) "dev" else "main"
        val jsonUrl = "https://raw.githubusercontent.com/${config.owner}/${config.name}/$branch/app/app-release.json"

        Log.d(tag, "Fetching manager info from JSON: $jsonUrl")

        return client.request<ManagerReleaseInfo> {
            url(jsonUrl)
        }
    }

    /**
     * Get latest manager info using static JSON file
     */
    suspend fun getLatestAppInfoFromJson(): APIResponse<MorpheAsset> {
        val config = repoConfig()
        val includePrerelease = prefs.useManagerPrereleases.get()

        // Try to get from JSON first
        return when (val jsonResponse = getManagerReleaseFromJson(includePrerelease)) {
            is APIResponse.Success -> {
                val mapped = runCatching {
                    val releaseInfo = jsonResponse.data
                    val version = "v${releaseInfo.version}"
                    val pageUrl = "${config.htmlUrl}/releases/tag/$version"

                    // Parse the timestamp from JSON
                    val createdAt = Instant.parse(releaseInfo.createdAt)
                        .toLocalDateTime(TimeZone.UTC)

                    Log.d(tag, "Manager: version=$version, downloadUrl=${releaseInfo.downloadUrl}, createdAt=$createdAt")

                    // All data is available in JSON
                    MorpheAsset(
                        downloadUrl = releaseInfo.downloadUrl,
                        createdAt = createdAt,
                        signatureDownloadUrl = releaseInfo.signatureDownloadUrl,
                        pageUrl = pageUrl,
                        description = releaseInfo.description,
                        version = version
                    )
                }

                mapped.fold(
                    onSuccess = { APIResponse.Success(it) },
                    onFailure = {
                        // If JSON parsing fails, fall back to GitHub API
                        getLatestAppInfo()
                    }
                )
            }
            is APIResponse.Error -> getLatestAppInfo() // Fall back to GitHub API
            is APIResponse.Failure -> getLatestAppInfo() // Fall back to GitHub API
        }
    }

    /**
     * Get app update using static JSON
     * Returns update info only if a newer version is available
     */
    suspend fun getAppUpdate(): MorpheAsset? {
        val asset = getLatestAppInfoFromJson().getOrNull() ?: return null
        return asset.takeIf { it.version.removePrefix("v") != BuildConfig.VERSION_NAME }
    }

    /**
     * Get manager release by specific version tag
     * Used for displaying changelog of currently installed version
     */
    suspend fun getManagerReleaseByVersion(version: String): APIResponse<MorpheAsset> {
        val config = repoConfig()
        val normalizedVersion = if (version.startsWith("v")) version else "v$version"

        return when (val releaseResponse = githubRequest<GitHubRelease>(config, "releases/tags/$normalizedVersion")) {
            is APIResponse.Success -> {
                val mapped = runCatching {
                    val release = releaseResponse.data
                    val asset = release.assets.firstOrNull(::isManagerAsset)
                        ?: throw IllegalStateException("No manager APK found in release $normalizedVersion")
                    mapReleaseToAsset(config, release, asset)
                }

                mapped.fold(
                    onSuccess = { APIResponse.Success(it) },
                    onFailure = { APIResponse.Failure(APIFailure(it, null)) }
                )
            }

            is APIResponse.Error -> APIResponse.Error(releaseResponse.error)
            is APIResponse.Failure -> APIResponse.Failure(releaseResponse.error)
        }
    }

    suspend fun getPatchesUpdate(): APIResponse<MorpheAsset> = apiRequest(
        if (prefs.usePatchesPrereleases.get()) "patches/prerelease" else "patches"
    )

    suspend fun getAssetFromPullRequest(
        owner: String,
        repo: String,
        pullRequestNumber: String
    ): MorpheAsset {
        suspend fun getPullWithRun(
            pullRequestNumber: String,
            config: RepoConfig
        ): GitHubActionRun {
            val pull = githubRequest<GitHubPullRequest>(config, "pulls/$pullRequestNumber")
                .successOrThrow("PR #$pullRequestNumber")

            val targetSha = pull.head.sha

            var page = 1
            while (true) {
                val actionsRuns = githubRequest<GitHubActionRuns>(
                    config,
                    "actions/runs?per_page=100&page=$page"
                ).successOrThrow("Workflow runs for PR #$pullRequestNumber (page $page)")

                val match = actionsRuns.workflowRuns.firstOrNull { it.headSha == targetSha }
                if (match != null) return match

                if (actionsRuns.workflowRuns.isEmpty())
                    throw Exception("No GitHub Actions run found for PR #$pullRequestNumber with SHA $targetSha")

                page++
            }
        }

        val config = RepoConfig(
            owner = owner,
            name = repo,
            apiBase = "https://api.github.com/repos/$owner/$repo",
            htmlUrl = "https://github.com/$owner/$repo"
        )

        val currentRun = getPullWithRun(pullRequestNumber, config)

        val artifacts = githubRequest<GitHubActionRunArtifacts>(
            config,
            "actions/runs/${currentRun.id}/artifacts"
        )
            .successOrThrow("PR artifacts for PR #$pullRequestNumber")
            .artifacts

        val artifact = artifacts.firstOrNull()
            ?: throw Exception("The latest commit in this PR didn't have any artifacts. Did the GitHub action run correctly?")

        return MorpheAsset(
            downloadUrl = artifact.archiveDownloadUrl,
            createdAt = Instant.parse(artifact.createdAt).toLocalDateTime(TimeZone.UTC),
            pageUrl = "${config.htmlUrl}/pull/$pullRequestNumber",
            description = currentRun.displayTitle,
            version = currentRun.headSha
        )
    }
}

fun <T> APIResponse<T>.successOrThrow(context: String): T {
    return when (this) {
        is APIResponse.Success -> data
        is APIResponse.Error -> throw Exception("Failed fetching $context: ${error.message}", error)
        is APIResponse.Failure -> throw Exception("Failed fetching $context: ${error.message}", error)
    }
}
