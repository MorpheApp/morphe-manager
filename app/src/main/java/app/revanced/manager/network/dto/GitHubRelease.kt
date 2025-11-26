package app.revanced.manager.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val body: String? = null,
    val name: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url")
    val downloadUrl: String,
    @SerialName("content_type")
    val contentType: String? = null,
)

@Serializable
data class GitHubContributor(
    val login: String,
    @SerialName("avatar_url")
    val avatarUrl: String,
)

@Serializable
data class GitHubPullRequest(
    val url: String,
    val head: GitHubPullRequestHead
)

@Serializable
data class GitHubPullRequestHead(
    val sha: String
)

@Serializable
data class GitHubActionRuns(
    @SerialName("workflow_runs")
    val workflowRuns: List<GitHubActionRun> = emptyList()
)

@Serializable
data class GitHubActionRun(
    val id: String,
    @SerialName("head_sha")
    val headSha: String,
    @SerialName("display_title")
    val displayTitle: String
)

@Serializable
data class GitHubActionRunArtifacts(
    @SerialName("artifacts")
    val artifacts: List<GitHubActionArtifact> = emptyList()
)

@Serializable
data class GitHubActionArtifact(
    @SerialName("archive_download_url")
    val archiveDownloadUrl: String,
    @SerialName("created_at")
    val createdAt: String
)
