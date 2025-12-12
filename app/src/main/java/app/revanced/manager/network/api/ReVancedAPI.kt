package app.revanced.manager.network.api

import app.morphe.manager.BuildConfig
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.network.dto.GitHubActionRun
import app.revanced.manager.network.dto.GitHubActionRunArtifacts
import app.revanced.manager.network.dto.GitHubActionRuns
import app.revanced.manager.network.dto.GitHubAsset
import app.revanced.manager.network.dto.GitHubContributor
import app.revanced.manager.network.dto.GitHubPullRequest
import app.revanced.manager.network.dto.GitHubRelease
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.network.dto.ReVancedContributor
import app.revanced.manager.network.dto.ReVancedGitRepository
import app.revanced.manager.network.service.HttpService
import app.revanced.manager.network.utils.APIFailure
import app.revanced.manager.network.utils.APIResponse
import app.revanced.manager.network.utils.getOrNull
import io.ktor.client.request.header
import io.ktor.client.request.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ReVancedAPI(
    private val client: HttpService,
    private val prefs: PreferencesManager
) {
    @Serializable
    data class PatchBundleJson(
        @SerialName("created_at") val createdAt: String,
        val description: String,
        @SerialName("download_url") val downloadUrl: String,
        @SerialName("signature_download_url") val signatureDownloadUrl: String? = null,
        val version: String
    )

    private data class RepoConfig(
        val owner: String,
        val name: String,
        val apiBase: String,
        val htmlUrl: String,
    )

    private fun repoConfig(): RepoConfig = parseRepoUrl(MANAGER_REPO_URL)

    private fun parseRepoUrl(raw: String): RepoConfig {
        val trimmed = raw.removeSuffix("/")
        return when {
            trimmed.startsWith("https://github.com/") -> {
                val repoPath = trimmed.removePrefix("https://github.com/").removeSuffix(".git")
                val parts = repoPath.split("/").filter { it.isNotBlank() }
                require(parts.size >= 2) { "Invalid GitHub repository URL: $raw" }
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
                val owner = parts.getOrNull(reposIndex + 1) ?: throw IllegalArgumentException("Invalid GitHub API URL: $raw")
                val name = parts.getOrNull(reposIndex + 2) ?: throw IllegalArgumentException("Invalid GitHub API URL: $raw")
                RepoConfig(
                    owner = owner,
                    name = name,
                    apiBase = "https://api.github.com/repos/$owner/$name",
                    htmlUrl = "https://github.com/$owner/$name"
                )
            }

            else -> throw IllegalArgumentException("Unsupported repository URL: $raw")
        }
    }

    private suspend inline fun <reified T> githubRequest(config: RepoConfig, path: String): APIResponse<T> {
        val normalizedPath = path.trimStart('/')
        val pat = prefs.gitHubPat.get()
        return client.request {
            // PR #35: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/35
            pat.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            url("${config.apiBase}/$normalizedPath")
        }
    }

//    private suspend fun apiUrl(): String = prefs.api.get().trim().removeSuffix("/")
//
//    private suspend inline fun <reified T> apiRequest(route: String): APIResponse<T> {
//        val normalizedRoute = route.trimStart('/')
//        val baseUrl = apiUrl()
//        return client.request {
//            url("$baseUrl/v4/$normalizedRoute")
//        }
//    }

    private suspend fun fetchReleaseAsset(
        config: RepoConfig,
        includePrerelease: Boolean,
        matcher: (GitHubAsset) -> Boolean
    ): APIResponse<ReVancedAsset> {
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
    ): ReVancedAsset {
        val timestamp = release.publishedAt ?: release.createdAt
        require(timestamp != null) { "Release ${release.tagName} does not contain a timestamp" }
        val createdAt = Instant.parse(timestamp).toLocalDateTime(TimeZone.UTC)
        val signatureUrl = findSignatureUrl(release, asset)
        val description = release.body?.ifBlank { release.name.orEmpty() } ?: release.name.orEmpty()

        return ReVancedAsset(
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


    suspend fun getLatestAppInfo(): APIResponse<ReVancedAsset> {
        val config = repoConfig()
        val includePrerelease = prefs.useManagerPrereleases.get()
        return fetchReleaseAsset(config, includePrerelease, ::isManagerAsset)
    }

    suspend fun getAppUpdate(): ReVancedAsset? {
        val asset = getLatestAppInfo().getOrNull() ?: return null
        return asset.takeIf { it.version.removePrefix("v") != BuildConfig.VERSION_NAME }
    }

    /**
     * Fetches the latest patches bundle from the JSON file URL.
     * Uses direct JSON endpoint.
     */
    /**
     * Fetches the latest patches bundle from the JSON file URL.
     * Uses direct JSON endpoint.
     */
    suspend fun getPatchesUpdate(): APIResponse<ReVancedAsset> = withContext(Dispatchers.IO) {
        val jsonUrl = prefs.patchesBundleJsonUrl.get().trim()

        if (jsonUrl.isBlank()) {
            return@withContext APIResponse.Failure(
                APIFailure(IllegalStateException("Patches bundle JSON URL is not configured"), null)
            )
        }

        return@withContext when (val response = client.request<PatchBundleJson> {
            url(jsonUrl)
        }) {
            is APIResponse.Success -> {
                val bundleData = response.data
                val mapped = kotlin.runCatching {
                    // Parse the created_at timestamp
                    val createdAt = try {
                        Instant.parse(bundleData.createdAt).toLocalDateTime(TimeZone.UTC)
                    } catch (e: Exception) {
                        // Try parsing without time zone if ISO format fails
                        try {
                            LocalDateTime.parse(bundleData.createdAt.replace(" ", "T"))
                        } catch (e2: Exception) {
                            throw IllegalStateException("Invalid timestamp format: ${bundleData.createdAt}")
                        }
                    }

                    // Extract repository URL from the JSON URL
                    val repoUrl = extractRepoUrlFromJsonUrl(jsonUrl)
                    val pageUrl = "$repoUrl/releases/tag/${bundleData.version}"

                    ReVancedAsset(
                        downloadUrl = bundleData.downloadUrl,
                        createdAt = createdAt,
                        signatureDownloadUrl = bundleData.signatureDownloadUrl?.takeIf { it != "N/A" },
                        pageUrl = pageUrl,
                        description = bundleData.description,
                        version = bundleData.version
                    )
                }

                mapped.fold(
                    onSuccess = { APIResponse.Success(it) },
                    onFailure = { APIResponse.Failure(APIFailure(it, null)) }
                )
            }

            is APIResponse.Error -> APIResponse.Error(response.error)
            is APIResponse.Failure -> APIResponse.Failure(response.error)
        }
    }

    private fun extractRepoUrlFromJsonUrl(jsonUrl: String): String {
        // Extract repository URL from paths like:
        // https://raw.githubusercontent.com/OWNER/REPO/refs/heads/BRANCH/...
        // or https://github.com/OWNER/REPO/raw/BRANCH/...
        return when {
            jsonUrl.contains("raw.githubusercontent.com") -> {
                val parts = jsonUrl.removePrefix("https://raw.githubusercontent.com/")
                    .split("/")
                if (parts.size >= 2) {
                    "https://github.com/${parts[0]}/${parts[1]}"
                } else {
                    "https://github.com/MorpheApp/morphe-patches"
                }
            }
            jsonUrl.contains("github.com") && jsonUrl.contains("/raw/") -> {
                val match = Regex("https://github\\.com/([^/]+)/([^/]+)/raw/").find(jsonUrl)
                if (match != null) {
                    "https://github.com/${match.groupValues[1]}/${match.groupValues[2]}"
                } else {
                    "https://github.com/MorpheApp/morphe-patches"
                }
            }
            else -> "https://github.com/MorpheApp/morphe-patches"
        }
    }

    suspend fun getContributors(): APIResponse<List<ReVancedGitRepository>> {
        val config = repoConfig()
        return when (val response = githubRequest<List<GitHubContributor>>(config, "contributors")) {
            is APIResponse.Success -> {
                val contributors = response.data.map {
                    ReVancedContributor(username = it.login, avatarUrl = it.avatarUrl)
                }
                APIResponse.Success(
                    listOf(
                        ReVancedGitRepository(
                            name = config.name,
                            url = config.htmlUrl,
                            contributors = contributors
                        )
                    )
                )
            }

            is APIResponse.Error -> APIResponse.Error(response.error)
            is APIResponse.Failure -> APIResponse.Failure(response.error)
        }
    }

    // PR #35: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/35
    suspend fun getAssetFromPullRequest(owner: String, repo: String, pullRequestNumber: String): ReVancedAsset {
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
            ?: throw Exception("The lastest commit in this PR didn't have any artifacts. Did the GitHub action run correctly?")

        return ReVancedAsset(
            downloadUrl = artifact.archiveDownloadUrl,
            createdAt = Instant.parse(artifact.createdAt).toLocalDateTime(TimeZone.UTC),
            pageUrl = "${config.htmlUrl}/pull/$pullRequestNumber",
            description = currentRun.displayTitle,
            version = currentRun.headSha
        )
    }
}

// PR #35: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/35
fun <T> APIResponse<T>.successOrThrow(context: String): T {
    return when (this) {
        is APIResponse.Success -> data
        is APIResponse.Error -> throw Exception("Failed fetching $context: ${error.message}", error)
        is APIResponse.Failure -> throw Exception("Failed fetching $context: ${error.message}", error)
    }
}

private val MANAGER_REPO_URL = "https://github.com/MorpheApp/morphe-manager"
