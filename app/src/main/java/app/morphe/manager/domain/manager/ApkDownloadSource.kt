/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.util.MORPHE_API_URL
import java.net.URI

/**
 * The site a download link leads to, which the download instructions are worded for.
 *
 * The API answers the version lookup with a single redirect and picks the host itself, so the
 * destination is read off the resolved URL instead of assuming everything comes from APKMirror.
 *
 * @param label Site name shown to the user. Null while the destination is not known yet.
 */
sealed class ApkDownloadSource(val label: String?) {
    /**
     * @param onDownloadPage True for the page carrying the download button. A release page stops
     * short of it, with the APK variants still to be picked from.
     */
    data class ApkMirror(val onDownloadPage: Boolean) : ApkDownloadSource("APKMirror.com")

    data object Uptodown : ApkDownloadSource("Uptodown.com")

    /**
     * A GitHub release, whose APK is one of the files attached to it.
     *
     * @param release Tag of the release the link points at. Null for the list of every release.
     */
    data class GitHub(val release: String?) : ApkDownloadSource("GitHub.com")

    /**
     * @param versionPinned True for the download page pinned to one version by its `?version=`.
     * The app's own page, or a download page reached without it, leaves the version to be picked.
     */
    data class ApkCube(val versionPinned: Boolean) : ApkDownloadSource("APKCube.com")

    /** Search results rather than one app page, used when the API knows no direct link. */
    data object WebSearch : ApkDownloadSource("Google")

    /** A link to the APK itself, which the browser downloads without putting up a page. */
    data class DirectFile(val host: String) : ApkDownloadSource(host)

    /** Any other host the API points at, named after itself so the button never promises the wrong site. */
    data class Other(val host: String) : ApkDownloadSource(host)

    /** The redirect has not been followed yet, so nothing site specific can be shown. */
    data object Unresolved : ApkDownloadSource(null)

    companion object {
        private val DOWNLOADABLE_EXTENSIONS = setOf("apk", "apkm", "apks", "xapk")

        /**
         * Works out where [url] leads.
         *
         * Until the redirect is followed the URL still points at the API, which says nothing
         * about where the user will end up, so that case resolves to [Unresolved].
         */
        fun from(url: String?): ApkDownloadSource {
            if (url == null || url.startsWith(MORPHE_API_URL)) return Unresolved

            val uri = runCatching { URI(url) }.getOrNull() ?: return Unresolved
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return Unresolved
            val path = uri.path.orEmpty().trimEnd('/')
            val extension = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

            return when {
                extension in DOWNLOADABLE_EXTENSIONS -> DirectFile(host)
                host.endsWith("apkmirror.com") ->
                    ApkMirror(onDownloadPage = path.endsWith("-android-apk-download"))
                host.endsWith("uptodown.com") -> Uptodown
                host == "github.com" && RELEASES_SEGMENT in path.split('/') -> GitHub(releaseTag(uri))
                host == "apkcube.com" || host.endsWith(".apkcube.com") ->
                    ApkCube(versionPinned = path.endsWith("/download") && hasQueryValue(uri, "version"))
                host.endsWith("google.com") -> WebSearch
                else -> Other(host)
            }
        }

        private const val RELEASES_SEGMENT = "releases"

        private fun hasQueryValue(uri: URI, name: String): Boolean =
            uri.rawQuery.orEmpty().split('&').any { it.startsWith("$name=") && it.length > name.length + 1 }

        /**
         * Tag of the one release [uri] leads to, either its own page or its card on the list of
         * releases, which GitHub anchors as "release-<tag>".
         */
        private fun releaseTag(uri: URI): String? =
            uri.path.substringAfter("/$RELEASES_SEGMENT/tag/", "").trimEnd('/').ifEmpty { null }
                ?: uri.fragment?.substringAfter("release-", "")?.ifEmpty { null }
    }
}
