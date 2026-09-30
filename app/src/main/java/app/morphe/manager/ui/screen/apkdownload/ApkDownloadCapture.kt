/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import app.morphe.manager.util.APK_EXTENSIONS
import app.morphe.manager.util.APK_MIMETYPE
import app.morphe.manager.util.BIN_MIMETYPE

/**
 * Extensions that arrive as a bare path for a downloaded archive. Declared here rather than
 * derived from [APK_EXTENSIONS] so a new extension the patcher learns to open does not silently
 * widen what a WebView capture accepts.
 */
private val CAPTURED_EXTENSIONS = APK_EXTENSIONS

/**
 * Whether a URL a WebView is about to load points at an APK-family archive.
 *
 * The query and fragment are dropped first: mirrors sign their file URLs, and a token that
 * happens to contain a dot must not decide the answer. Page URLs are deliberately not matched —
 * earlier revisions matched bare `/download`, `cdn` and `apk-download` and captured the page the
 * user was navigating instead of the file, which is worse than missing the file.
 */
fun isApkArchiveUrl(url: String?): Boolean {
    val path = url?.lowercase()?.substringBefore('?')?.substringBefore('#') ?: return false
    return CAPTURED_EXTENSIONS.any { path.endsWith(".$it") }
}

/**
 * Whether a response's declared type is an APK-family archive.
 *
 * This is the signal that survives a host we have never seen. A CDN handing out a signed URL
 * usually serves a path with no extension at all, so the URL says nothing and only the type does.
 * `application/octet-stream` is accepted because it is what a server falls back to for any binary
 * it does not name.
 */
fun isApkArchiveMimeType(mimeType: String?): Boolean {
    val type = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return false
    return type == APK_MIMETYPE || type.startsWith("application/vnd.android.package") ||
        type.startsWith("application/x-apk") || type == BIN_MIMETYPE
}

/**
 * Whether a captured URL is worth taking the user out of the page for.
 *
 * Either signal is enough on its own, because they cover opposite cases: a mirror serves the
 * archive from a URL that names it, and a CDN serves one from a URL that does not.
 */
fun isApkArchiveDownload(url: String?, mimeType: String? = null): Boolean =
    isApkArchiveUrl(url) || isApkArchiveMimeType(mimeType)
