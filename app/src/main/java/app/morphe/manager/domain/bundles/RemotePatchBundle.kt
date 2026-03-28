package app.morphe.manager.domain.bundles

import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.network.utils.getOrThrow
import app.morphe.manager.util.ChangelogEntry
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
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
            try {
                patchesFile.parentFile?.mkdirs()
                patchesFile.setWritable(true, true)
                http.downloadToFile(
                    saveLocation = patchesFile,
                    builder = { url(info.downloadUrl) },
                    onProgress = onProgress
                )
                patchesFile.setReadOnly()
                requireNonEmptyPatchesFile("Downloading patch bundle")
            } catch (t: Throwable) {
                runCatching { patchesFile.setWritable(true, true) }
                runCatching { patchesFile.delete() }
                throw t
            }

            PatchBundleDownloadResult(
                versionSignature = info.version,
                assetCreatedAtMillis = runCatching {
                    info.createdAt.toInstant(TimeZone.UTC).toEpochMilliseconds()
                }.getOrNull()
            )
        }

    /**
     * Downloads the latest version regardless if there is a new update available.
     */
    suspend fun downloadLatest(onProgress: PatchBundleDownloadProgress? = null): PatchBundleDownloadResult =
        download(getLatestInfo(), onProgress)

    suspend fun update(onProgress: PatchBundleDownloadProgress? = null): PatchBundleDownloadResult? =
        withContext(Dispatchers.IO) {
            val info = getLatestInfo()
            if (hasInstalled() && info.version == installedVersionSignatureInternal)
                return@withContext null

            download(info, onProgress)
        }

    suspend fun fetchLatestReleaseInfo(): MorpheAsset {
        val key = "$uid|$endpoint"
        val now = System.currentTimeMillis()
        val cached = changelogCacheMutex.withLock {
            changelogCache[key]?.takeIf { now - it.timestamp <= CHANGELOG_CACHE_TTL }
        }
        if (cached != null) return cached.asset

        val asset = getLatestInfo()
        changelogCacheMutex.withLock {
            changelogCache[key] = CachedChangelog(asset, now)
        }
        return asset
    }

    /**
     * Shared cache logic for [fetchChangelogEntries] and its overrides.
     */
    protected suspend fun fetchAndCacheEntries(
        cacheKey: String,
        sinceVersion: String?,
        fetch: suspend () -> List<ChangelogEntry>
    ): List<ChangelogEntry> {
        val now = System.currentTimeMillis()
        val allEntries = entriesCacheMutex.withLock {
            entriesCache[cacheKey]?.takeIf { now - it.first <= CHANGELOG_CACHE_TTL }?.second
        } ?: run {
            val fetched = fetch()
            entriesCacheMutex.withLock { entriesCache[cacheKey] = now to fetched }
            fetched
        }
        return if (sinceVersion != null)
            app.morphe.manager.util.ChangelogParser.entriesNewerThan(allEntries, sinceVersion)
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

    fun clearChangelogCache() {
        val assetKey = "$uid|$endpoint"
        changelogCacheMutex.tryLock()
        try {
            changelogCache.remove(assetKey)
        } finally {
            changelogCacheMutex.unlock()
        }

        entriesCacheMutex.tryLock()

        try {
            entriesCache.keys.removeAll { it.startsWith("$uid|") }
        } finally {
            entriesCacheMutex.unlock()
        }
    }

    companion object {
        internal const val CHANGELOG_CACHE_TTL = 10 * 60 * 1000L
        private val changelogCacheMutex = Mutex()
        private val changelogCache = mutableMapOf<String, CachedChangelog>()
        internal val entriesCacheMutex = Mutex()
        internal val entriesCache = mutableMapOf<String, Pair<Long, List<ChangelogEntry>>>()

        /**
         * Infer GitHub page URL from various endpoint formats
         */
        fun inferPageUrlFromEndpoint(endpoint: String): String? {
            return try {
                val uri = java.net.URI(endpoint)
                val host = uri.host?.lowercase(java.util.Locale.US)

                when (host) {
                    "raw.githubusercontent.com", "github.com" -> {
                        uri.path?.trim('/')?.split('/')
                            ?.filter { it.isNotBlank() }
                            ?.takeIf { it.size >= 2 }
                            ?.let { "https://github.com/${it[0]}/${it[1]}" }
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
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
     * The branch the endpoint URL currently points to (e.g. "main", "dev").
     * Returns null if the URL uses refs/heads/... or is not a recognized format.
     */
    val endpointBranch: String? get() = extractBranch(endpoint)

    /**
     * The "stable" branch. Always "main" - prerelease toggling is only available when
     * the endpoint explicitly points to "main" or "dev", so switching back to stable
     * always means "main".
     */
    private val stableBranch: String get() = "main"

    /**
     * Parse GitHub URL and convert to raw.githubusercontent.com format.
     * If [usePrerelease] is true, uses "dev" branch; otherwise uses "main".
     * Only called when [supportsPrerelease] is true, i.e. the endpoint already
     * points to "main" or "dev" - so both branches are expected to exist.
     * Supports:
     * - https://github.com/owner/repo/tree/branch/path/file.json
     * - https://github.com/owner/repo/blob/branch/path/file.json
     * - https://raw.githubusercontent.com/owner/repo/branch/path/file.json (passthrough)
     */
    private fun parseGitHubUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase(java.util.Locale.US)
            val targetBranch = if (usePrerelease) "dev" else stableBranch

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

                    val owner = pathParts[0]
                    val repo = pathParts[1]
                    val type = pathParts[2] // "tree" or "blob"

                    if (type !in listOf("tree", "blob")) return url

                    val filePath = pathParts.drop(4).joinToString("/")

                    "https://raw.githubusercontent.com/$owner/$repo/$targetBranch/$filePath"
                }
                else -> url // Unknown host, return as-is
            }
        } catch (_: Exception) {
            url // If parsing fails, return original URL
        }
    }

    /**
     * Returns true if this bundle supports prerelease toggling.
     * Only bundles whose endpoint explicitly points to "main" or "dev" branch support.
     */
    val supportsPrerelease: Boolean get() {
        val branch = endpointBranch ?: return false
        return branch == "main" || branch == "dev"
    }

    override suspend fun getLatestInfo() = withContext(Dispatchers.IO) {
        val normalizedEndpoint = parseGitHubUrl(endpoint)

        val asset = http.request<MorpheAsset> {
            url(normalizedEndpoint)
        }.getOrThrow()

        // If pageUrl is not set, try to infer it from the endpoint and add version tag
        if (asset.pageUrl == null) {
            val repoUrl = inferPageUrlFromEndpoint(endpoint)
            val inferredPageUrl = if (repoUrl != null && asset.version.isNotBlank()) {
                // Normalize version to ensure it starts with 'v'
                val normalizedVersion = if (asset.version.startsWith("v")) {
                    asset.version
                } else {
                    "v${asset.version}"
                }
                // Create proper release page URL: https://github.com/owner/repo/releases/tag/v1.0.0-dev.1
                "$repoUrl/releases/tag/$normalizedVersion"
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
        val activeEndpoint = parseGitHubUrl(endpoint)
        val changelogUrl = api.changelogUrlFromBundleEndpoint(activeEndpoint) ?: return emptyList()
        return fetchAndCacheEntries("$uid|$changelogUrl", sinceVersion) {
            api.fetchChangelogFromUrl(changelogUrl)
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
         * Extracts the branch name from a GitHub URL.
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

    override suspend fun fetchChangelogEntries(sinceVersion: String?): List<ChangelogEntry> {
        val branch = if (usePrerelease) "dev" else "main"
        return fetchAndCacheEntries("$uid|$branch", sinceVersion) { api.fetchPatchesChangelog(branch) }
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

        try {
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

                    patchBundleOutputStream().use { patchOutput ->
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
            requireNonEmptyPatchesFile("Downloading patch bundle")
        } catch (t: Throwable) {
            runCatching { patchesFile.delete() }
            throw t
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

private data class CachedChangelog(val asset: MorpheAsset, val timestamp: Long)
