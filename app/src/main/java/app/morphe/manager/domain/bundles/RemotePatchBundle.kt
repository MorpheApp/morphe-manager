package app.morphe.manager.domain.bundles

import app.morphe.manager.domain.bundles.RemotePatchBundle.Companion.CHANGELOG_CACHE_TTL
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.network.service.AssetDownloader
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.network.utils.getOrThrow
import app.morphe.manager.util.ChangelogEntry
import app.morphe.manager.util.ChangelogParser
import app.morphe.manager.util.SOURCE_REPO_URL
import app.morphe.manager.util.TimedCache
import app.morphe.manager.util.compareVersions
import app.morphe.manager.util.releasePageUrl
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

data class PatchBundleDownloadResult(
    val versionSignature: String,
    val assetCreatedAtMillis: Long?
)

typealias PatchBundleDownloadProgress = (bytesRead: Long, bytesTotal: Long?) -> Unit

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
    val autoUpdate: Boolean,
    enabled: Boolean,
) : PatchBundleSource(name, uid, displayName, createdAt, updatedAt, error, directory, enabled), KoinComponent {
    protected val http: HttpService by inject()
    private val assetDownloader: AssetDownloader by inject()

    protected abstract suspend fun getLatestInfo(): MorpheAsset
    abstract fun copy(
        error: Throwable? = this.error,
        name: String = this.name,
        displayName: String? = this.displayName,
        createdAt: Long? = this.createdAt,
        updatedAt: Long? = this.updatedAt,
        autoUpdate: Boolean = this.autoUpdate,
        enabled: Boolean = this.enabled
    ): RemotePatchBundle

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        enabled: Boolean
    ): RemotePatchBundle = copy(error, name, displayName, createdAt, updatedAt, this.autoUpdate, enabled)

    protected open suspend fun download(info: MorpheAsset, onProgress: PatchBundleDownloadProgress? = null) =
        withContext(Dispatchers.IO) {
            installPatchBundle("Downloading patch bundle") { staging ->
                assetDownloader.downloadToFile(
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

            download(info, onProgress)
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

    /**
     * Shared cache logic for [fetchChangelogEntries] and its overrides.
     */
    protected suspend fun fetchAndCacheEntries(
        cacheKey: String,
        sinceVersion: String?,
        fetch: suspend () -> List<ChangelogEntry>
    ): List<ChangelogEntry> {
        val allEntries = entriesCache[cacheKey] ?: fetch().also { entriesCache[cacheKey] = it }

        return if (sinceVersion != null)
            ChangelogParser.entriesNewerThan(allEntries, sinceVersion)
        else allEntries
    }

    /**
     * Fetches entries from CHANGELOG.md next to the bundle endpoint.
     * Results cached for [CHANGELOG_CACHE_TTL]; invalidate via [clearChangelogCache].
     */
    open suspend fun fetchChangelogEntries(
        sinceVersion: String? = null
    ): List<ChangelogEntry> {
        val api: MorpheAPI by inject()
        val changelogUrl = api.changelogUrlFromBundleEndpoint(endpoint) ?: return emptyList()
        return fetchAndCacheEntries("$uid|$changelogUrl", sinceVersion) {
            api.fetchChangelogFromUrl(changelogUrl)
        }
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
            return try {
                val uri = java.net.URI(endpoint)
                val host = uri.host?.lowercase(java.util.Locale.US)
                val segments = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() }

                when (host) {
                    "raw.githubusercontent.com", "github.com" -> {
                        segments?.takeIf { it.size >= 2 }
                            ?.let { "https://github.com/${it[0]}/${it[1]}" }
                    }
                    "gitlab.com" -> {
                        // gitlab.com/owner/repo/-/raw/branch/... or gitlab.com/owner/repo
                        segments?.takeIf { it.size >= 2 }
                            ?.let { "https://gitlab.com/${it[0]}/${it[1]}" }
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
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
    autoUpdate: Boolean,
    enabled: Boolean,
    val usePrerelease: Boolean = false,
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, autoUpdate, enabled) {

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
     * Resolves the effective fetch URL for the current [usePrerelease] state.
     *
     * Delegates to [switchBranchInUrl]: uses `BRANCH_DEV` when pre-releases are enabled,
     * `BRANCH_STABLE` otherwise.
     */
    private fun resolveBranchUrl(url: String) =
        switchBranchInUrl(url, if (usePrerelease) BRANCH_DEV else BRANCH_STABLE)

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
     * only the [resolveBranchUrl]-resolved endpoint is fetched, as before.
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
                    runCatching { http.request<MorpheAsset> { url(switchBranchInUrl(endpoint, BRANCH_DEV)) }.getOrThrow() }.getOrNull()
                }
                val stableDeferred = async {
                    runCatching { http.request<MorpheAsset> { url(switchBranchInUrl(endpoint, BRANCH_STABLE)) }.getOrThrow() }.getOrNull()
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
            http.request<MorpheAsset> { url(resolveBranchUrl(endpoint)) }.getOrThrow()
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

    override suspend fun fetchChangelogEntries(sinceVersion: String?): List<ChangelogEntry> {
        // endpoint stores the original branch - rebuild the URL for the active branch
        val api: MorpheAPI by inject()
        val activeEndpoint = resolveBranchUrl(endpoint)
        val changelogUrl = api.changelogUrlFromBundleEndpoint(activeEndpoint) ?: return emptyList()
        return fetchAndCacheEntries("$uid|$changelogUrl", sinceVersion) {
            api.fetchChangelogFromUrl(changelogUrl, stopAfterFirstStable = usePrerelease)
        }
    }

    override suspend fun fetchFullChangelogEntries(): List<ChangelogEntry> {
        val api: MorpheAPI by inject()
        val stableEndpoint = switchBranchInUrl(endpoint, BRANCH_STABLE)
        val changelogUrl = api.changelogUrlFromBundleEndpoint(stableEndpoint) ?: return emptyList()
        return fetchAndCacheEntries("$uid|$changelogUrl|full", sinceVersion = null) {
            api.fetchChangelogFromUrl(changelogUrl, stopAfterFirstStable = false)
        }
    }

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        autoUpdate: Boolean,
        enabled: Boolean
    ) = JsonPatchBundle(
        name, uid, displayName, createdAt, updatedAt,
        installedVersionSignature, error, directory, endpoint, autoUpdate, enabled, usePrerelease,
    )

    fun copy(usePrerelease: Boolean) = JsonPatchBundle(
        name, uid, displayName, createdAt, updatedAt,
        installedVersionSignature, error, directory, endpoint, autoUpdate, enabled, usePrerelease,
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
    autoUpdate: Boolean,
    enabled: Boolean,
    val usePrerelease: Boolean = false,
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, autoUpdate, enabled) {
    private val api: MorpheAPI by inject()

    override suspend fun getLatestInfo() = api.getPatchesUpdate(usePrerelease).getOrThrow()

    // The endpoint is the API identifier rather than a browsable URL
    override val browsePageUrl: String get() = SOURCE_REPO_URL

    override val issuesPageUrl: String get() = "$SOURCE_REPO_URL/issues"

    override suspend fun fetchChangelogEntries(sinceVersion: String?): List<ChangelogEntry> {
        val branch = if (usePrerelease) BRANCH_DEV else BRANCH_STABLE
        return fetchAndCacheEntries("$uid|$branch", sinceVersion) {
            api.fetchPatchesChangelog(branch, stopAfterFirstStable = usePrerelease)
        }
    }

    override suspend fun fetchFullChangelogEntries(): List<ChangelogEntry> =
        fetchAndCacheEntries("$uid|$BRANCH_STABLE|full", sinceVersion = null) {
            api.fetchPatchesChangelog(BRANCH_STABLE, stopAfterFirstStable = false)
        }

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        autoUpdate: Boolean,
        enabled: Boolean
    ) = APIPatchBundle(
        name, uid, displayName, createdAt, updatedAt,
        installedVersionSignature, error, directory, endpoint, autoUpdate, enabled, usePrerelease,
    )

    fun copy(usePrerelease: Boolean) = APIPatchBundle(
        name, uid, displayName, createdAt, updatedAt,
        installedVersionSignature, error, directory, endpoint, autoUpdate, enabled, usePrerelease,
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
    autoUpdate: Boolean,
    enabled: Boolean
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, autoUpdate, enabled) {

    private val api: MorpheAPI by inject()

    override suspend fun getLatestInfo() = withContext(Dispatchers.IO) {
        val (owner, repo, prNumber) = endpoint.split("/").let { parts ->
            Triple(parts[3], parts[4], parts[6])
        }

        api.getAssetFromPullRequest(owner, repo, prNumber)
    }

    override suspend fun download(info: MorpheAsset, onProgress: PatchBundleDownloadProgress?) = withContext(Dispatchers.IO) {
        val prefs: PreferencesManager by inject()
        val http: HttpService by inject()
        val gitHubPat = prefs.gitHubPat.get().also {
            if (it.isBlank()) throw RuntimeException("PAT is required")
        }

        installPatchBundle("Downloading patch bundle") { staging ->
            with(http.http) {
                prepareGet {
                    url(info.downloadUrl)
                    header("Authorization", "Bearer $gitHubPat")
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
        autoUpdate: Boolean,
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
        autoUpdate,
        enabled
    )
}
