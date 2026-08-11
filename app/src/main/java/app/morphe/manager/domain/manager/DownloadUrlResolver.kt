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
import java.net.URI
import java.net.URLEncoder

/**
 * Works out where a specific version of an app can be downloaded from.
 */
class DownloadUrlResolver(private val morpheAPI: MorpheAPI) {

    /**
     * Resolves the download page for [packageName] at [version], falling back to a plain web
     * search when the API cannot point anywhere useful.
     *
     * Every redirect along the way is followed: the API sometimes points at itself once more,
     * and the download sites redirect for reasons of their own, so only the end of the chain
     * says what the user will actually get.
     */
    suspend fun resolve(packageName: String, version: String?): String {
        val searchUrl = apiSearchUrl(packageName, version)
        Log.d(tag, "Using search url: $searchUrl")

        var resolved = morpheAPI.resolveRedirect(searchUrl) ?: run {
            Log.w(tag, "No redirect location for: $searchUrl")
            return webSearchUrl(packageName, version)
        }

        repeat(MAX_REDIRECTS) {
            // A URL that redirects nowhere is the end of the chain
            val next = morpheAPI.resolveRedirect(resolved) ?: return finalUrl(resolved, packageName, version)
            Log.i(tag, "Following redirect to: $next")
            resolved = next
        }

        Log.w(tag, "Gave up after $MAX_REDIRECTS redirects")
        return webSearchUrl(packageName, version)
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
        val query = "\"$packageName\" $versionPart $architecture $SEARCH_SITES"
        Log.d(tag, "Using search query: $query")
        return "https://google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
    }

    /** Hands back [url], or a web search when it cannot lead to the version that was asked for. */
    private fun finalUrl(url: String, packageName: String, version: String?): String {
        // Uptodown answers a retired download id with its "latest version" page, which is not
        // the build the patches need. Only its per-build pages end in "-x"
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val onUptodown = uri.host?.lowercase()?.endsWith("uptodown.com") == true
        if (onUptodown && !uri.path.orEmpty().trimEnd('/').endsWith("-x")) {
            Log.w(tag, "Uptodown link lost its build, searching instead: $url")
            return webSearchUrl(packageName, version)
        }
        return url
    }

    private companion object {
        // Enough for the API pointing at itself and a site or two moving the page, while still
        // ending a redirect loop
        const val MAX_REDIRECTS = 5

        // Kept in step with the mirrors the API falls back to, so a search started here and one
        // started by the API lead to the same set of sites
        const val SEARCH_SITES =
            "(site:apkmirror.com OR site:uptodown.com OR site:apkpure.com OR site:apkcombo.com)"
    }
}
