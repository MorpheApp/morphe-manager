package app.revanced.manager.domain.bundles

import app.revanced.manager.data.redux.ActionContext
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.network.api.ReVancedAPI
import app.revanced.manager.network.dto.ReVancedAsset
import app.revanced.manager.network.service.HttpService
import app.revanced.manager.network.utils.getOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.zip.ZipInputStream

data class PatchBundleDownloadResult(
    val versionSignature: String,
    val assetCreatedAtMillis: Long?
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
    val autoUpdate: Boolean,
) : PatchBundleSource(name, uid, displayName, createdAt, updatedAt, error, directory), KoinComponent {
    protected val http: HttpService by inject()

    protected abstract suspend fun getLatestInfo(): ReVancedAsset
    abstract fun copy(
        error: Throwable? = this.error,
        name: String = this.name,
        displayName: String? = this.displayName,
        createdAt: Long? = this.createdAt,
        updatedAt: Long? = this.updatedAt,
        autoUpdate: Boolean = this.autoUpdate
    ): RemotePatchBundle

    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?
    ): RemotePatchBundle = copy(error, name, displayName, createdAt, updatedAt, this.autoUpdate)

    protected open suspend fun download(info: ReVancedAsset) = withContext(Dispatchers.IO) {
        patchBundleOutputStream().use {
            http.streamTo(it) {
                url(info.downloadUrl)
            }
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
    suspend fun ActionContext.downloadLatest(): PatchBundleDownloadResult = download(getLatestInfo())

    suspend fun ActionContext.update(): PatchBundleDownloadResult? = withContext(Dispatchers.IO) {
        val info = getLatestInfo()
        if (hasInstalled() && info.version == installedVersionSignatureInternal)
            return@withContext null

        download(info)
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
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, autoUpdate) {
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
        autoUpdate: Boolean
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
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, autoUpdate) {
    private val api: ReVancedAPI by inject()

    override suspend fun getLatestInfo() = api.getPatchesUpdate().getOrThrow()
    override fun copy(
        error: Throwable?,
        name: String,
        displayName: String?,
        createdAt: Long?,
        updatedAt: Long?,
        autoUpdate: Boolean
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
    autoUpdate: Boolean
) : RemotePatchBundle(name, uid, displayName, createdAt, updatedAt, installedVersionSignature, error, directory, endpoint, autoUpdate) {

    private val api: ReVancedAPI by inject()

    override suspend fun getLatestInfo() = withContext(Dispatchers.IO) {
        val (owner, repo, prNumber) = endpoint.split("/").let { parts ->
            Triple(parts[3], parts[4], parts[6])
        }

        api.getAssetFromPullRequest(owner, repo, prNumber)
    }

    override suspend fun download(info: ReVancedAsset) = withContext(Dispatchers.IO) {
        val prefs: PreferencesManager by inject()

        val gitHubPat = prefs.gitHubPat.get().also {
            if (it == "") throw RuntimeException("PAT is required.")
        }

        // Custom http client since the default client only accepts content type json
        val customHttpClient = HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(true)
                    followSslRedirects(true)
                }
            }
        }

        with(customHttpClient) {
            prepareGet {
                url(info.downloadUrl)

                header("Authorization", "Bearer $gitHubPat")
            }.execute { httpResponse ->
                patchBundleOutputStream().use { patchOutput ->
                    val zipInputStream = httpResponse.bodyAsChannel().toInputStream()
                    ZipInputStream(zipInputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".rvp")) {
                                zis.copyTo(patchOutput)
                                break
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
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
        autoUpdate: Boolean
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
        autoUpdate
    )
}

private data class CachedChangelog(val asset: ReVancedAsset, val timestamp: Long)
