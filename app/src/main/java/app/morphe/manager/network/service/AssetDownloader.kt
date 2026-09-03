/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.service

import android.util.Log
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.util.tag
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
 */
class AssetDownloader(
    private val http: HttpService,
    private val api: MorpheAPI,
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
                if (e is CancellationException || !isTransientNetworkError(e)) throw e

                val signedUrl = resolveThroughApi(downloadUrl) ?: throw e
                Log.i(tag, "Retrying $downloadUrl through the GitHub API")
                direct(signedUrl, saveLocation, onProgress)
            }
        } catch (error: Throwable) {
            saveLocation.delete()
            throw error
        }
    }

    private suspend fun direct(
        url: String,
        saveLocation: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?
    ) = http.downloadToFile(
        saveLocation = saveLocation,
        builder = { url(url) },
        onProgress = onProgress
    )

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
