/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.service

import android.util.Log
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.api.ReleaseAPI
import app.morphe.manager.network.api.isRawGitHubUrl
import app.morphe.manager.util.tag
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Downloads release assets, rerouting through the GitHub API when the direct link cannot be
 * reached.
 *
 * Manifests and release metadata point at github.com because that is what release tooling emits,
 * but networks that drop that host while leaving api.github.com alone are common enough to make
 * both patch bundles and manager updates unreachable. Everything that fetches a release asset
 * goes through here so neither has to know about the detour.
 *
 * The same detour reaches assets of a private repository: github.com does not take a token on
 * its download links, while the API serves them to a PAT that can read the repository.
 */
class AssetDownloader(
    private val http: HttpService,
    private val api: ReleaseAPI,
    private val prefs: PreferencesManager
) {
    /**
     * Downloads [downloadUrl] into [saveLocation].
     *
     * An attempt that does not run to completion leaves nothing behind. The parallel downloader
     * fills the file at independent offsets, so a partial one is sparse. There is nothing to
     * resume from, and what stays on disk would pass for a whole file.
     */
    suspend fun downloadToFile(
        downloadUrl: String,
        saveLocation: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        try {
            try {
                direct(downloadUrl, saveLocation, onProgress)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                when {
                    isTransientNetworkError(e) -> {
                        val signedUrl = resolveThroughApi(downloadUrl) ?: throw e
                        Log.i(tag, "Retrying $downloadUrl through the GitHub API")
                        direct(signedUrl, saveLocation, onProgress)
                    }
                    e is HttpService.HttpException && e.status == HttpStatusCode.NotFound ->
                        if (!downloadPrivate(downloadUrl, saveLocation, onProgress)) throw e
                    else -> throw e
                }
            }
        } catch (error: Throwable) {
            saveLocation.delete()
            throw error
        }
    }

    private suspend fun direct(
        url: String,
        saveLocation: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
        pat: String? = null
    ) = http.downloadToFile(
        saveLocation = saveLocation,
        builder = {
            url(url)
            pat?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        },
        onProgress = onProgress
    )

    /**
     * Retries a GitHub asset the direct link reported missing with the PAT, in case it lives in a
     * private repository. Returns false when there is no PAT or GitHub route to retry with.
     */
    private suspend fun downloadPrivate(
        downloadUrl: String,
        saveLocation: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?
    ): Boolean {
        val pat = prefs.gitHubPat.get().ifBlank { return false }
        if (isRawGitHubUrl(downloadUrl)) {
            Log.i(tag, "Retrying $downloadUrl with the GitHub PAT")
            direct(downloadUrl, saveLocation, onProgress, pat)
            return true
        }
        val signedUrl = resolveThroughApi(downloadUrl) ?: return false
        Log.i(tag, "Retrying $downloadUrl through the GitHub API with the GitHub PAT")
        direct(signedUrl, saveLocation, onProgress)
        return true
    }

    /**
     * Resolves the pre-signed URL serving the same asset, or null when [downloadUrl] is not a
     * GitHub release link or the asset cannot be located.
     *
     * The signed URL is deliberately not cached: it expires within the hour, and resolving it per
     * download is cheaper than handling a rejected signature midway through one.
     */
    private suspend fun resolveThroughApi(downloadUrl: String): String? {
        val assetUrl = api.releaseAssetApiUrl(downloadUrl) ?: return null
        val pat = prefs.gitHubPat.get()

        // The API serves the asset bytes only when octet-stream is the sole Accept value, and the
        // shared client always appends its own JSON one. Resolving the redirect gives a signed URL
        // that carries the content type itself and needs no headers to download.
        return http.resolveRedirect(assetUrl) {
            header(HttpHeaders.Accept, ContentType.Application.OctetStream.toString())
            pat.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
    }
}
