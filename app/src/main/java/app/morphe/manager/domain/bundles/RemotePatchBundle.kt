/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/bundles/RemotePatchBundle.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.bundles

import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.network.service.AssetDownloader
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.network.utils.getOrThrow
import app.morphe.manager.util.*
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

data class PatchBundleDownloadResult(
    val versionSignature: String,
    val assetCreatedAtMillis: Long?
)

typealias PatchBundleDownloadProgress = (bytesRead: Long, bytesTotal: Long?) -> Unit

/** What a remote bundle reaches its source with, handed to it by whoever builds the bundle. */
class RemoteBundleServices(
    val http: HttpService,
    val api: MorpheAPI,
    val assetDownloader: AssetDownloader,
    val prefs: PreferencesManager
)

sealed class RemotePatchBundle(
    name: String,
    uid: Int,
    displayName: String?,
    createdAt: Long?,
    updatedAt: Long?,
    private val installedVersionSignatureInternal: String?,
    error: Throwable?,
    directory: File,
    val endpoint: String,
    enabled: Boolean,
    protected val services: RemoteBundleServices,
) : PatchBundleSource(name, uid, displayName, createdAt, updatedAt, error, directory, enabled) {
    protected val http = services.http
    protected val api = services.api
    private val refusedReleaseFile = directory.resolve("refused-release")

    protected abstract suspend fun getLatestInfo(): MorpheAsset
    abstract override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        enabled: Boolean
    ): RemotePatchBundle

    protected open suspend fun download(info: MorpheAsset, onProgress: PatchBundleDownloadProgress? = null) =
        withContext(Dispatchers.IO) {
            installPatchBundle("Downloading patch bundle", publishedKey = info.publicKey) { staging ->
                services.assetDownloader.downloadToFile(
                    downloadUrl = info.downloadUrl,
                    saveLocation = staging,
                    onProgress = onProgress
                )
            }

            PatchBundleDownloadResult(
                versionSignature = info.version,
                assetCreatedAtMillis = runCatching {
                    info.createdAt.toInstant(TimeZone.UTC).toEpochMilliseconds()
                }.getOrNull()
            )
        }

    /**
     * [getLatestInfo] with the manifest page URL remembered, so [browsePageUrl] can use it without
     * a network request of its own.
     */
    private suspend fun latestInfo(): MorpheAsset = getLatestInfo().also { asset ->
        asset.pageUrl?.let { pageUrls[uid] = it }
    }

    /**
     * Downloads the latest version regardless if there is a new update available.
     */
    suspend fun downloadLatest(onProgress: PatchBundleDownloadProgress? = null): PatchBundleDownloadResult =
        download(latestInfo(), onProgress)

    suspend fun update(onProgress: PatchBundleDownloadProgress? = null): PatchBundleDownloadResult? =
        withContext(Dispatchers.IO) {
            val info = latestInfo()
            val remoteCreatedAt = runCatching {
                info.createdAt.toInstant(TimeZone.UTC).toEpochMilliseconds()
            }.getOrNull()

            if (hasInstalled()
                && info.version == installedVersionSignatureInternal
                && (remoteCreatedAt == null || remoteCreatedAt == createdAt)
            ) return@withContext null

            downloadUnlessRefused(info, onProgress)
        }

    /**
     * [download], unless an earlier download refused the same release for its signer while the
     * source trusted the same key as now, which would only download it to refuse it again.
     */
    private suspend fun downloadUnlessRefused(
        info: MorpheAsset,
        onProgress: PatchBundleDownloadProgress?
    ): PatchBundleDownloadResult {
        val release = listOf(info.version, info.createdAt, readSigningKey().orEmpty()).joinToString("|")
        val refused = refusedReleaseFile.takeIf { it.exists() }?.readLines()
        if (refused?.firstOrNull() == release) {
            throw PatchBundleSignerChangedException(refused.getOrNull(1)?.takeIf { it.isNotEmpty() })
        }

        return try {
            download(info, onProgress).also { refusedReleaseFile.delete() }
        } catch (e: PatchBundleSignerChangedException) {
            refusedReleaseFile.writeText(listOf(release, e.signer.orEmpty()).joinToString("\n"))
            throw e
        }
    }

    suspend fun fetchLatestReleaseInfo(): MorpheAsset {
        val key = "$uid|$endpoint"
        releaseInfoCache[key]?.let { return it }

        return latestInfo().also { releaseInfoCache[key] = it }
    }

    /**
     * Page URL declared by the manifest of the most recently fetched release, if any.
     */
    private val manifestPageUrl: String? get() = pageUrls[uid]

    /**
     * Where the "open in browser" action takes the user: the source itself, not a single release.
     *
     * Hosts other than GitHub and GitLab have no known repository layout, so those fall back to the
     * page URL the manifest declares and finally to the endpoint, keeping self-hosted sources on
     * their own server instead of an unrelated repository.
     */
    open val browsePageUrl: String
        get() = inferPageUrlFromEndpoint(endpoint) ?: manifestPageUrl ?: endpoint

    /**
     * Where the "report an issue" action takes the user: the repository's issues page.
     *
     * Follows the same order as [browsePageUrl] and appends the host's issues path
     * (GitHub: /issues, GitLab: /-/issues). Hosts other than GitHub and GitLab have no known
     * issues layout, so those land on the browse page instead of a guessed path.
     */
    open val issuesPageUrl: String
        get() = inferIssuesUrlFromEndpoint(endpoint)
            ?: manifestPageUrl?.let { issuesUrlForRepoUrl(it) }
            ?: browsePageUrl

    /** Add-source link for this source, see [addSourceLinkForEndpoint]. */
    open val addSourceLink: String?
        get() = addSourceLinkForEndpoint(endpoint, name)

    /**
     * Shared cache logic for [fetchChangelogEntries] and its overrides.
     */
    protected suspend fun fetchAndCacheEntries(
        cacheKey: String,
        fetch: suspend () -> List<ChangelogEntry>
    ): List<ChangelogEntry> = entriesCache[cacheKey] ?: fetch().also { entriesCache[cacheKey] = it }

    /**
     * Fetches entries from CHANGELOG.md next to the bundle endpoint.
     * Results cached for [CHANGELOG_CACHE_TTL]; invalidate via [clearChangelogCache].
     */
    open suspend fun fetchChangelogEntries(): List<ChangelogEntry> {
        val changelogUrl = api.changelogUrlFromBundleEndpoint(endpoint) ?: return emptyList()
        return fetchAndCacheEntries("$uid|$changelogUrl") {
            api.fetchChangelogFromUrl(changelogUrl)
        }
    }

    /**
     * [fetchChangelogEntries] as the source's channel reads it. The stable changelog also keeps
     * the dev builds merged into each release, which the release itself already sums up.
     */
    suspend fun fetchChannelChangelogEntries(): List<ChangelogEntry> {
        val entries = fetchChangelogEntries()
        return if (usesPrerelease) entries else entries.filterNot { it.isPrerelease }
    }

    /**
     * Releases on the source's channel that an app patched with [version] has yet to get, see
     * [ChangelogParser.entriesSince]. The stable history a dev changelog stops short of is read
     * only when needed, and the dev entries alone stand in for it when it cannot be.
     */
    suspend fun fetchChangelogSince(version: String?): List<ChangelogEntry> {
        val entries = fetchChannelChangelogEntries()
        val stableHistory = if (usesPrerelease && ChangelogParser.stopsAbove(entries, version)) {
            try {
                fetchFullChangelogEntries()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()
        return ChangelogParser.entriesSince(entries, version, stableHistory)
    }

    /**
     * Full stable history from `main`, ignoring `stopAfterFirstStable`. Older history
     * consists of stable releases by definition, so we always fetch from `main`.
     * Cached separately from [fetchChangelogEntries] with the `|full` suffix.
     */
    open suspend fun fetchFullChangelogEntries(): List<ChangelogEntry> = emptyList()

    /** Drops every cached read for this source, so the next one goes to the network. */
    fun clearChangelogCache() {
        pageUrls.remove(uid)
        releaseInfoCache.remove("$uid|$endpoint")
        entriesCache.removeKeys { it.startsWith("$uid|") }
    }

    companion object {
        const val BRANCH_STABLE = "main"
        const val BRANCH_DEV = "dev"

        /** Bundle file a repository-only link resolves to. */
        private const val DEFAULT_BUNDLE_FILE = "patches-bundle.json"

        internal const val CHANGELOG_CACHE_TTL = 10 * 60 * 1000L
        private val releaseInfoCache = TimedCache<String, MorpheAsset>(CHANGELOG_CACHE_TTL)
        private val entriesCache = TimedCache<String, List<ChangelogEntry>>(CHANGELOG_CACHE_TTL)

        // Manifest page URLs by source uid, kept here because bundle instances are recreated on every reload
        private val pageUrls = ConcurrentHashMap<Int, String>()

        /**
         * Infer the repository page URL from various endpoint formats.
         * Returns null for hosts other than GitHub and GitLab, whose repository layout is unknown.
         */
        fun inferPageUrlFromEndpoint(endpoint: String): String? {
            val (host, segments) = endpointParts(endpoint) ?: return null
            if (segments.size < 2) return null

            return when (host) {
                "raw.githubusercontent.com", "github.com" -> "https://github.com/${segments[0]}/${segments[1]}"
                // gitlab.com/owner/repo/-/raw/branch/... or gitlab.com/owner/repo
                "gitlab.com" -> "https://gitlab.com/${segments[0]}/${segments[1]}"
                else -> null
            }
        }

        /**
         * Add-source link for [endpoint], or null when it would add a different source. The link
         * carries only owner/repo, which expands to the root bundle file, so only that layout on
         * the stable or dev branch qualifies.
         */
        fun addSourceLinkForEndpoint(endpoint: String, name: String?): String? {
            val (host, segments) = endpointParts(endpoint) ?: return null
            val branches = setOf(BRANCH_STABLE, BRANCH_DEV)

            val provider = when (host) {
                "raw.githubusercontent.com" -> "github".takeIf {
                    segments.size == 4 && segments[2] in branches && segments[3] == DEFAULT_BUNDLE_FILE
                }

                "gitlab.com" -> "gitlab".takeIf {
                    segments.size == 6 && segments[2] == "-" && segments[3] == "raw" &&
                            segments[4] in branches && segments[5] == DEFAULT_BUNDLE_FILE
                }

                else -> null
            } ?: return null

            return buildString {
                append("$MORPHE_WEBSITE_URL$ADD_SOURCE_PATH?$provider=${segments[0]}/${segments[1]}")
                name?.takeIf { it.isNotBlank() }?.let {
                    append("&name=").append(URLEncoder.encode(it, "UTF-8").replace("+", "%20"))
                }
            }
        }

        /** Lowercased host and path segments of [endpoint], or null when it is not a valid URL. */
        private fun endpointParts(endpoint: String): Pair<String, List<String>>? = try {
            val uri = java.net.URI(endpoint)
            uri.host?.lowercase(java.util.Locale.US)?.let { host ->
                host to uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }

        /**
         * Infer the issues page URL from various endpoint formats.
         * Returns null for hosts other than GitHub and GitLab, whose issues layout is unknown.
         */
        fun inferIssuesUrlFromEndpoint(endpoint: String): String? =
            inferPageUrlFromEndpoint(endpoint)?.let { issuesUrlForRepoUrl(it) }

        /** Appends the host's issues path to an already resolved repository URL. */
        fun issuesUrlForRepoUrl(repoUrl: String): String? = when {
            repoUrl.startsWith("https://github.com/") -> "$repoUrl/issues"
            repoUrl.startsWith("https://gitlab.com/") -> "$repoUrl/-/issues"
            else -> null
        }
    }

    val installedVersionSignature: String? get() = installedVersionSignatureInternal
}

class JsonPatchBundle(
    name: String,
    uid: Int,
    displayName: String?,
    createdAt: Long?,
    updatedAt: Long?,
    installedVersionSignature: String?,
    error: Throwable?,
    directory: File,
    endpoint: String,
    enabled: Boolean,
    services: RemoteBundleServices,
    val usePrerelease: Boolean = false,
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, enabled, services) {

    /**
     * The branch the endpoint URL currently points to.
     * Returns null if the URL uses refs/heads/... or is not a recognized format.
     */
    val endpointBranch: String? get() = extractBranch(endpoint)

    /**
     * Returns [url] with its branch segment replaced by [targetBranch].
     *
     * Supports:
     * - https://raw.githubusercontent.com/owner/repo/branch/path/file.json
     * - https://github.com/owner/repo/tree|blob/branch/path/file.json
     * - https://gitlab.com/owner/repo/-/raw/branch/path/file.json
     *
     * Returns the original [url] unchanged for unrecognized hosts, `refs/heads/...` links,
     * or when parsing fails.
     */
    private fun switchBranchInUrl(url: String, targetBranch: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase(java.util.Locale.US)
            when (host) {
                "raw.githubusercontent.com" -> {
                    val parts = uri.path.trim('/').split('/')
                    if (parts.size < 3) return url
                    // Don't modify refs/heads/... - it's a direct immutable link
                    if (parts[2] == "refs") return url
                    val newPath = "/${parts[0]}/${parts[1]}/$targetBranch/${parts.drop(3).joinToString("/")}"
                    "https://raw.githubusercontent.com$newPath"
                }
                "github.com" -> {
                    // Parse: /owner/repo/tree|blob/branch/path/to/file.json
                    val pathParts = uri.path?.trim('/')?.split('/') ?: return url
                    if (pathParts.size < 5) return url // Need at least: owner, repo, tree/blob, branch, file
                    val type = pathParts[2] // "tree" or "blob"
                    if (type !in listOf("tree", "blob")) return url
                    val owner = pathParts[0]
                    val repo = pathParts[1]
                    val filePath = pathParts.drop(4).joinToString("/")
                    "https://raw.githubusercontent.com/$owner/$repo/$targetBranch/$filePath"
                }
                "gitlab.com" -> {
                    // Format: owner/repo/-/raw/BRANCH/path/file.json
                    val parts = uri.path.trim('/').split('/').toMutableList()
                    val rawIndex = parts.indexOf("raw")
                    if (rawIndex >= 0 && parts.getOrNull(rawIndex - 1) == "-") {
                        parts[rawIndex + 1] = targetBranch
                        "https://gitlab.com/${parts.joinToString("/")}"
                    } else url
                }
                else -> url // Unknown host, return as-is
            }
        } catch (_: Exception) {
            url // If parsing fails, return original URL
        }
    }

    /**
     * The endpoint on [branch], see [switchBranchInUrl]. Only an endpoint on `BRANCH_STABLE` or
     * `BRANCH_DEV` follows the channel, one on any other branch was picked on purpose and stays.
     */
    private fun endpointOn(branch: String) =
        if (supportsPrerelease) switchBranchInUrl(endpoint, branch) else endpoint

    /** The endpoint for the current [usePrerelease] state, see [endpointOn]. */
    private val activeEndpoint get() = endpointOn(if (usePrerelease) BRANCH_DEV else BRANCH_STABLE)

    /**
     * Returns true if this bundle supports prerelease toggling.
     * Only bundles whose endpoint explicitly points to `BRANCH_STABLE` or `BRANCH_DEV` branch support.
     */
    val supportsPrerelease: Boolean get() {
        val branch = endpointBranch ?: return false
        return branch == BRANCH_STABLE || branch == BRANCH_DEV
    }

    /**
     * Fetches the latest release metadata for this bundle.
     *
     * When [usePrerelease] is enabled and [supportsPrerelease] is true, both the `BRANCH_DEV` and
     * `BRANCH_STABLE` JSON endpoints are fetched in parallel and the one with the
     * higher version is returned. This prevents missing a stable release when a third-party
     * source hasn't updated its `BRANCH_DEV` JSON after shipping a new stable build.
     *
     * When [usePrerelease] is disabled (or the endpoint does not support branch switching),
     * only the [activeEndpoint] is fetched.
     *
     * @throws IllegalStateException if both channels are unreachable.
     */
    override suspend fun getLatestInfo() = withContext(Dispatchers.IO) {
        val asset = if (usePrerelease && supportsPrerelease) {
            // Fetch both BRANCH_DEV and BRANCH_STABLE in parallel; return whichever has the newer version.
            // Needed because third-party devs sometimes don't update the BRANCH_DEV JSON when a new
            // stable release is tagged, so the dev channel would otherwise stay on an older
            // pre-release and the stable update would go unnoticed
            coroutineScope {
                val devDeferred = async {
                    runCatching { fetchManifest(endpointOn(BRANCH_DEV)) }.getOrNull()
                }
                val stableDeferred = async {
                    runCatching { fetchManifest(endpointOn(BRANCH_STABLE)) }.getOrNull()
                }
                val devAsset = devDeferred.await()
                val stableAsset = stableDeferred.await()
                when {
                    devAsset == null && stableAsset == null -> error("No release found for $name")
                    devAsset == null -> stableAsset!!
                    stableAsset == null -> devAsset
                    compareVersions(devAsset.version, stableAsset.version) >= 0 -> devAsset
                    else -> stableAsset
                }
            }
        } else {
            fetchManifest(activeEndpoint)
        }

        // If pageUrl is not set, try to infer it from the endpoint and add version tag
        if (asset.pageUrl == null) {
            val repoUrl = inferPageUrlFromEndpoint(endpoint)
            val inferredPageUrl = if (repoUrl != null && asset.version.isNotBlank()) {
                releasePageUrl(repoUrl, asset.version)
            } else {
                // Fallback to repository URL if version is missing
                repoUrl
            }
            asset.copy(pageUrl = inferredPageUrl)
        } else {
            asset
        }
    }

    private suspend fun fetchManifest(url: String) = api.rawFileRequest<MorpheAsset>(url).getOrThrow()

    override suspend fun fetchChangelogEntries(): List<ChangelogEntry> {
        val changelogUrl = api.changelogUrlFromBundleEndpoint(activeEndpoint) ?: return emptyList()
        return fetchAndCacheEntries("$uid|$changelogUrl") {
            api.fetchChangelogFromUrl(changelogUrl, stopAfterFirstStable = usePrerelease)
        }
    }

    override suspend fun fetchFullChangelogEntries(): List<ChangelogEntry> {
        val changelogUrl = api.changelogUrlFromBundleEndpoint(endpointOn(BRANCH_STABLE)) ?: return emptyList()
        return fetchAndCacheEntries("$uid|$changelogUrl|full") {
            api.fetchChangelogFromUrl(changelogUrl, stopAfterFirstStable = false)
        }
    }

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        enabled: Boolean
    ) = JsonPatchBundle(
        name, uid, displayName, createdAt, updatedAt,
        installedVersionSignature, error, directory, endpoint, enabled, services, usePrerelease,
    )

    fun copy(usePrerelease: Boolean) = JsonPatchBundle(
        name, uid, displayName, createdAt, updatedAt,
        installedVersionSignature, error, directory, endpoint, enabled, services, usePrerelease,
    )

    companion object {
        /**
         * Extracts the branch name from a GitHub or GitLab URL.
         *
         * Supported formats:
         * - raw.githubusercontent.com/owner/repo/BRANCH/path  (returns null for refs/heads/...)
         * - github.com/owner/repo/tree|blob/BRANCH/path
         * - gitlab.com/owner/repo/-/raw/BRANCH/path
         */
        internal fun extractBranch(url: String): String? {
            return try {
                val uri = java.net.URI(url)
                val host = uri.host?.lowercase(java.util.Locale.US)
                val parts = uri.path.trim('/').split('/')
                when (host) {
                    "raw.githubusercontent.com" -> {
                        // Format: owner/repo/BRANCH/path...
                        // But refs/heads/BRANCH is a direct file link - not switchable
                        val branch = parts.getOrNull(2) ?: return null
                        if (branch == "refs") null else branch
                    }
                    "github.com" -> {
                        if (parts.size >= 4 && parts[2] in listOf("tree", "blob")) parts[3] else null
                    }
                    "gitlab.com" -> {
                        // Format: owner/repo/-/raw/BRANCH/path...
                        val rawIndex = parts.indexOf("raw")
                        if (rawIndex >= 0 && parts.getOrNull(rawIndex - 1) == "-")
                            parts.getOrNull(rawIndex + 1)
                        else null
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

class APIPatchBundle(
    name: String,
    uid: Int,
    displayName: String?,
    createdAt: Long?,
    updatedAt: Long?,
    installedVersionSignature: String?,
    error: Throwable?,
    directory: File,
    endpoint: String,
    enabled: Boolean,
    services: RemoteBundleServices,
    val usePrerelease: Boolean = false,
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, enabled, services) {
    override suspend fun getLatestInfo() = api.getPatchesUpdate(usePrerelease).getOrThrow()

    // The endpoint is the API identifier rather than a browsable URL
    override val browsePageUrl: String get() = SOURCE_REPO_URL

    override val issuesPageUrl: String get() = "$SOURCE_REPO_URL/issues"

    // Every install already has the default source
    override val addSourceLink: String? get() = null

    override suspend fun fetchChangelogEntries(): List<ChangelogEntry> {
        val branch = if (usePrerelease) BRANCH_DEV else BRANCH_STABLE
        return fetchAndCacheEntries("$uid|$branch") {
            api.fetchPatchesChangelog(branch, stopAfterFirstStable = usePrerelease)
        }
    }

    override suspend fun fetchFullChangelogEntries(): List<ChangelogEntry> =
        fetchAndCacheEntries("$uid|$BRANCH_STABLE|full") {
            api.fetchPatchesChangelog(BRANCH_STABLE, stopAfterFirstStable = false)
        }

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        enabled: Boolean
    ) = APIPatchBundle(
        name, uid, displayName, createdAt, updatedAt,
        installedVersionSignature, error, directory, endpoint, enabled, services, usePrerelease,
    )

    fun copy(usePrerelease: Boolean) = APIPatchBundle(
        name, uid, displayName, createdAt, updatedAt,
        installedVersionSignature, error, directory, endpoint, enabled, services, usePrerelease,
    )
}

class GitHubPullRequestBundle(
    name: String,
    uid: Int,
    displayName: String?,
    createdAt: Long?,
    updatedAt: Long?,
    installedVersionSignature: String?,
    error: Throwable?,
    directory: File,
    endpoint: String,
    enabled: Boolean,
    services: RemoteBundleServices
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, enabled, services) {

    override suspend fun getLatestInfo() = withContext(Dispatchers.IO) {
        val (owner, repo, prNumber) = endpoint.split("/").let { parts ->
            Triple(parts[3], parts[4], parts[6])
        }

        api.getAssetFromPullRequest(owner, repo, prNumber)
    }

    override suspend fun download(info: MorpheAsset, onProgress: PatchBundleDownloadProgress?) = withContext(Dispatchers.IO) {
        val gitHubPat = services.prefs.gitHubPat.get().also {
            if (it.isBlank()) throw RuntimeException("PAT is required")
        }

        installPatchBundle("Downloading patch bundle") { staging ->
            with(http.http) {
                prepareGet {
                    url(info.downloadUrl)
                    header("Authorization", "Bearer $gitHubPat")
                    // The client's request timeout covers the whole body, which a large bundle on a
                    // slow link outlasts while bytes are still arriving. The socket timeout still
                    // ends a stalled connection.
                    timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
                }.execute { httpResponse ->
                    val contentType = httpResponse.contentType()?.toString() ?: ""
                    val contentLength = httpResponse.contentLength()
                    val archiveSize = contentLength?.takeIf { it > 0 }

                    // GitHub Actions artifacts can be either:
                    //  - a zip archive (default): Content-Type application/zip or octet-stream with zip magic bytes
                    //  - a raw .mpp file (when compression is disabled in the workflow)
                    val isZip = contentType.contains("zip", ignoreCase = true)
                            || info.downloadUrl.endsWith(".zip", ignoreCase = true)

                    staging.outputStream().use { patchOutput ->
                        if (isZip) {
                            ZipInputStream(httpResponse.bodyAsChannel().toInputStream()).use { zis ->
                                // Use larger buffer for faster I/O (512 KB)
                                val buffer = ByteArray(512 * 1024)
                                var copiedBytes = 0L
                                var lastReportedBytes = 0L
                                var lastReportedAt = 0L
                                var extractedTotal: Long? = null

                                var entry = zis.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory && entry.name.endsWith(".mpp")) {
                                        extractedTotal = entry.size.takeIf { it > 0 }

                                        while (true) {
                                            val read = zis.read(buffer)
                                            if (read == -1) break
                                            patchOutput.write(buffer, 0, read)
                                            copiedBytes += read.toLong()
                                            val now = System.currentTimeMillis()
                                            // Report progress less frequently: every 256KB or 500ms
                                            if (copiedBytes - lastReportedBytes >= 256 * 1024 || now - lastReportedAt >= 500) {
                                                lastReportedBytes = copiedBytes
                                                lastReportedAt = now
                                                // Update total size if we now have extracted size
                                                val currentTotal = extractedTotal ?: archiveSize
                                                onProgress?.invoke(copiedBytes, currentTotal)
                                            }
                                        }
                                        break
                                    }
                                    zis.closeEntry()
                                    entry = zis.nextEntry
                                }

                                if (copiedBytes <= 0L) {
                                    throw IOException("No .mpp file found in the PR artifact")
                                }
                                // Final progress - use actual copied bytes as total if we don't have size
                                val finalTotal = extractedTotal ?: archiveSize ?: copiedBytes
                                onProgress?.invoke(copiedBytes, finalTotal)
                            }
                        } else {
                            // Raw .mpp artifact - stream directly without unzipping
                            val buffer = ByteArray(512 * 1024)
                            val channel = httpResponse.bodyAsChannel()
                            var copiedBytes = 0L
                            var lastReportedBytes = 0L
                            var lastReportedAt = 0L

                            while (!channel.isClosedForRead) {
                                val read = channel.readAvailable(buffer)
                                if (read <= 0) continue
                                patchOutput.write(buffer, 0, read)
                                copiedBytes += read.toLong()
                                val now = System.currentTimeMillis()
                                // Report progress less frequently: every 256KB or 500ms
                                if (copiedBytes - lastReportedBytes >= 256 * 1024 || now - lastReportedAt >= 500) {
                                    lastReportedBytes = copiedBytes
                                    lastReportedAt = now
                                    // Update total size if we now have extracted size
                                    onProgress?.invoke(copiedBytes, archiveSize)
                                }
                            }

                            if (copiedBytes <= 0L) {
                                throw IOException("Empty .mpp artifact received from PR")
                            }
                            onProgress?.invoke(copiedBytes, archiveSize ?: copiedBytes)
                        }
                    }
                }
            }
        }

        PatchBundleDownloadResult(
            versionSignature = info.version,
            assetCreatedAtMillis = runCatching {
                info.createdAt.toInstant(TimeZone.UTC).toEpochMilliseconds()
            }.getOrNull()
        )
    }

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        enabled: Boolean
    ) = GitHubPullRequestBundle(
        name,
        uid,
        displayName,
        createdAt,
        updatedAt,
        installedVersionSignature,
        error,
        directory,
        endpoint,
        enabled,
        services
    )
}
