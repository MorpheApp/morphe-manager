package app.revanced.manager.network.api

import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.network.dto.*
import app.revanced.manager.network.service.HttpService
import app.revanced.manager.network.utils.APIFailure
import app.revanced.manager.network.utils.APIResponse
import app.revanced.manager.network.utils.getOrNull
import app.universal.revanced.manager.BuildConfig
import io.ktor.client.request.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ReVancedAPI(
    private val client: HttpService,
    private val prefs: PreferencesManager
) {
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
                val owner =
                    parts.getOrNull(reposIndex + 1) ?: throw IllegalArgumentException("Invalid GitHub API URL: $raw")
                val name =
                    parts.getOrNull(reposIndex + 2) ?: throw IllegalArgumentException("Invalid GitHub API URL: $raw")
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
        return client.request {
            prefs.gitHubPat.get().let {
                // If the user provided a PAT, use it also here to prevent time limits
                if (it != "") header("Authorization", "Bearer $it")
            }
            url("${config.apiBase}/$normalizedPath")
        }
    }

    private suspend fun apiUrl(): String = prefs.api.get().trim().removeSuffix("/")

    private suspend inline fun <reified T> apiRequest(route: String): APIResponse<T> {
        val normalizedRoute = route.trimStart('/')
        return client.request {
            url("${apiUrl()}/v4/$normalizedRoute")
        }
    }

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

    suspend fun getPatchesUpdate(): APIResponse<ReVancedAsset> =
        apiRequest("patches?prerelease=${prefs.usePatchesPrereleases.get()}")

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
                if (match != null)
                    return match

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

        val artifact = artifacts.firstOrNull() ?: throw Exception("The lastest commit in this PR didn't have any artifacts. Did the GitHub action run correctly?")

        return ReVancedAsset(
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

private const val MANAGER_REPO_URL = "https://github.com/Jman-Github/universal-revanced-manager"
