package app.revanced.manager.domain.bundles

import app.revanced.manager.data.redux.ActionContext
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.network.api.ReVancedAPI
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.network.service.HttpService
import app.revanced.manager.network.utils.getOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import okhttp3.Protocol

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

    protected abstract suspend fun getLatestInfo(): ReVancedAsset
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

    // PR #35: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/35
    protected open suspend fun download(info: ReVancedAsset, onProgress: PatchBundleDownloadProgress? = null) =
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

    suspend fun fetchLatestReleaseInfo(): ReVancedAsset {
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
        const val updateFailMsg = "Failed to update patches"
        private const val CHANGELOG_CACHE_TTL = 10 * 60 * 1000L
        private val changelogCacheMutex = Mutex()
        private val changelogCache = mutableMapOf<String, CachedChangelog>()
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
        http.request<ReVancedAsset> {
            url(endpoint)
        }.getOrThrow()
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
    private val api: ReVancedAPI by inject()

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

// PR #35: https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/35
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

    private val api: ReVancedAPI by inject()

    override suspend fun getLatestInfo() = withContext(Dispatchers.IO) {
        val (owner, repo, prNumber) = endpoint.split("/").let { parts ->
            Triple(parts[3], parts[4], parts[6])
        }

        api.getAssetFromPullRequest(owner, repo, prNumber)
    }

    override suspend fun download(info: ReVancedAsset, onProgress: PatchBundleDownloadProgress?) = withContext(Dispatchers.IO) {
        val prefs: PreferencesManager by inject()
        val gitHubPat = prefs.gitHubPat.get().also {
            if (it.isBlank()) throw RuntimeException("PAT is required.")
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
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
                requestTimeoutMillis = 5 * 60_000
            }
        }

        try {
            with(customHttpClient) {
                prepareGet {
                    url(info.downloadUrl)
                    header("Authorization", "Bearer $gitHubPat")
                }.execute { httpResponse ->
                    patchBundleOutputStream().use { patchOutput ->
                        ZipInputStream(httpResponse.bodyAsChannel().toInputStream()).use { zis ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
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
                                        if (copiedBytes - lastReportedBytes >= 64 * 1024 || now - lastReportedAt >= 200) {
                                            lastReportedBytes = copiedBytes
                                            lastReportedAt = now
                                            onProgress?.invoke(copiedBytes, extractedTotal)
                                        }
                                    }
                                    break
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }

                            if (copiedBytes <= 0L) {
                                throw IOException("No .mpp file found in the pull request artifact.")
                            }
                            onProgress?.invoke(copiedBytes, extractedTotal)
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

private data class CachedChangelog(val asset: ReVancedAsset, val timestamp: Long)
