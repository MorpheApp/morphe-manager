/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/network/dto/GitHubRelease.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.network.dto

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
    /**
     * API URL of the asset. Serves the same bytes as [downloadUrl] without touching github.com,
     * which matters where that host is unreachable but the API is not.
     */
    val url: String? = null,
    @SerialName("content_type")
    val contentType: String? = null,
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
