/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The capture rule decides which URL takes the user out of a download page. Getting it wrong in
 * one direction strands them on a page they cannot leave, and in the other starts a download of
 * the page itself, so both signals are pinned here.
 */
class ApkDownloadClassificationTest {

    @Test
    fun `an archive name on the path is taken`() {
        assertTrue(isApkArchiveUrl("https://example.com/app.apk"))
        assertTrue(isApkArchiveUrl("https://example.com/app.apkm"))
        assertTrue(isApkArchiveUrl("https://example.com/app.xapk"))
        assertTrue(isApkArchiveUrl("https://example.com/app.apks"))
    }

    @Test
    fun `a bundle is not an archive this app can patch`() {
        // An App Bundle is a developer artefact no device installs, and APK_EXTENSIONS does not
        // name it. Taking one would download a file the patcher can only reject.
        assertFalse(isApkArchiveUrl("https://example.com/app.aab"))
    }

    @Test
    fun `a query is dropped before the name is read`() {
        // A mirror signs its URL, and a token containing a dot must not decide the answer
        assertTrue(isApkArchiveUrl("https://downloadr2.apkmirror.com/wp-content/uploads/x.apk?key=abc"))
        assertTrue(isApkArchiveUrl("https://example.com/a.apk#section"))
    }

    @Test
    fun `a token that merely looks like a name is not taken`() {
        // The mirror's own download page. Earlier revisions matched '/download' and
        // 'apk-download' and captured this, leaving the user on a page that never resolved
        assertFalse(isApkArchiveUrl("https://www.apkmirror.com/apk/google-inc/youtube/youtube-21-39-522-2-android-apk-download/"))
        assertFalse(isApkArchiveUrl("https://example.com/download?file=app.apk"))
        assertFalse(isApkArchiveUrl("https://example.com/page.apk.html"))
        assertFalse(isApkArchiveUrl("https://example.com/"))
        assertFalse(isApkArchiveUrl(null))
        assertFalse(isApkArchiveUrl(""))
    }

    @Test
    fun `the declared type carries a URL that names nothing`() {
        // A signed CDN URL usually has no extension at all, so only the type is left to read
        assertTrue(isApkArchiveMimeType("application/vnd.android.package-archive"))
        assertTrue(isApkArchiveMimeType("application/vnd.android.package-archive; charset=utf-8"))
        assertTrue(isApkArchiveMimeType("application/octet-stream"))
        assertTrue(isApkArchiveMimeType("Application/Octet-Stream"))
        assertFalse(isApkArchiveMimeType("text/html"))
        assertFalse(isApkArchiveMimeType("application/json"))
        assertFalse(isApkArchiveMimeType(null))
    }

    @Test
    fun `either signal is enough to take a download`() {
        // A CDN URL with no name, known only by its type
        assertTrue(
            isApkArchiveDownload(
                "https://abcdef.r2.cloudflarestorage.com/bucket/9f8c1e2d",
                "application/octet-stream",
            )
        )
        // A URL that names the archive, reached before any response type exists
        assertTrue(isApkArchiveDownload("https://example.com/app.apk"))
        assertFalse(isApkArchiveDownload("https://abc.r2.cloudflarestorage.com/bucket/9f8c1e2d", "text/html"))
        assertFalse(isApkArchiveDownload("https://example.com/", "text/html"))
    }
}
