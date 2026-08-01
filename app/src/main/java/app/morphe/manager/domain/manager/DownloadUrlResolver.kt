/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import android.os.Build
import android.util.Log
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.util.KnownApps
import app.morphe.manager.util.MORPHE_API_URL
import app.morphe.manager.util.tag
import io.ktor.http.encodeURLPath
import java.net.URLEncoder

/**
 * Works out where a specific version of an app can be downloaded from.
 */
class DownloadUrlResolver(private val morpheAPI: MorpheAPI) {

    /**
     * Resolves the download page for [packageName] at [version], falling back to a plain web
     * search when the API cannot point anywhere.
     *
     * The API answers with a redirect, and occasionally redirects to itself once more, so the
     * result is followed twice before giving up on it.
     */
    suspend fun resolve(packageName: String, version: String?): String {
        val searchUrl = apiSearchUrl(packageName, version)
        Log.d(tag, "Using search url: $searchUrl")

        var resolved = followRedirect(searchUrl, packageName, version)
        if (resolved.startsWith(MORPHE_API_URL)) {
            Log.i(tag, "Redirect still on API host, resolving again")
            resolved = followRedirect(resolved, packageName, version)
        }
        return resolved
    }

    /** The unresolved API URL, usable immediately while [resolve] is still working. */
    fun apiSearchUrl(packageName: String, version: String?): String {
        val query = "$packageName~${version ?: "any"}~${Build.SUPPORTED_ABIS.first()}".encodeURLPath()
        return "$MORPHE_API_URL/v2/web-search/$query"
    }

    /** Used when the API is unreachable, so the user still lands on something useful. */
    fun webSearchUrl(packageName: String, version: String?): String {
        val architecture = if (packageName == KnownApps.YOUTUBE_MUSIC) {
            " (${Build.SUPPORTED_ABIS.first()})"
        } else {
            "nodpi"
        }
        val versionPart = version?.let { "\"$it\"" } ?: ""
        val query = "\"$packageName\" $versionPart $architecture site:APKMirror.com"
        Log.d(tag, "Using search query: $query")
        return "https://google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
    }

    private suspend fun followRedirect(url: String, packageName: String, version: String?): String =
        morpheAPI.resolveRedirect(url) ?: run {
            Log.w(tag, "No redirect location for: $url")
            webSearchUrl(packageName, version)
        }
}
