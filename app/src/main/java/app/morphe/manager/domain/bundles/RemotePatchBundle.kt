package app.morphe.manager.domain.bundles

import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.network.utils.getOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import okhttp3.Protocol
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

    fun clearChangelogCache() {
        val key = "$uid|$endpoint"
        changelogCacheMutex.tryLock()
        try {
            changelogCache.remove(key)
        } finally {
            changelogCacheMutex.unlock()
        }
    }

    companion object {
        private const val CHANGELOG_CACHE_TTL = 10 * 60 * 1000L
        private val changelogCacheMutex = Mutex()
        private val changelogCache = mutableMapOf<String, CachedChangelog>()

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
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, autoUpdate, enabled) {
    override suspend fun getLatestInfo() = withContext(Dispatchers.IO) {
        val asset = http.request<MorpheAsset> {
            url(endpoint)
        }.getOrThrow()

        // If pageUrl is not set, try to infer it from the endpoint
        if (asset.pageUrl == null) {
            val inferredPageUrl = inferPageUrlFromEndpoint(endpoint)
            asset.copy(pageUrl = inferredPageUrl)
        } else {
            asset
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
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, autoUpdate, enabled) {
    private val api: MorpheAPI by inject()

    override suspend fun getLatestInfo() = api.getPatchesUpdate().getOrThrow()
    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        autoUpdate: Boolean,
        enabled: Boolean
    ) = APIPatchBundle(
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
        val gitHubPat = prefs.gitHubPat.get().also {
            if (it.isBlank()) throw RuntimeException("PAT is required")
        }

        val customHttpClient = HttpClient(OkHttp) {
            engine {
                config {
                    // Force HTTP/1.1 to avoid HTTP/2 PROTOCOL_ERROR stream resets when fetching
                    // PR artifacts from GitHub.
                    protocols(listOf(Protocol.HTTP_1_1))
                    followRedirects(true)
                    followSslRedirects(true)
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 2 * 60_000
                requestTimeoutMillis = 5 * 60_000
            }
        }

        try {
            with(customHttpClient) {
                prepareGet {
                    url(info.downloadUrl)
                    header("Authorization", "Bearer $gitHubPat")
                }.execute { httpResponse ->
                    val contentLength = httpResponse.contentLength()
                    val archiveSize = contentLength?.takeIf { it > 0 }

                    patchBundleOutputStream().use { patchOutput ->
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
                                throw IOException("No .mpp file found in the pull request artifact")
                            }
                            // Final progress - use actual copied bytes as total if we don't have size
                            val finalTotal = extractedTotal ?: archiveSize ?: copiedBytes
                            onProgress?.invoke(copiedBytes, finalTotal)
                        }
                    }
                }
            }
            requireNonEmptyPatchesFile("Downloading patch bundle")
        } catch (t: Throwable) {
            runCatching { patchesFile.delete() }
            throw t
        } finally {
            runCatching { customHttpClient.close() }
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
