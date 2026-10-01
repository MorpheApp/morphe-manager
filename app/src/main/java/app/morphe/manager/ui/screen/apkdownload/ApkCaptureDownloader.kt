/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import android.content.Context
import android.net.Uri
import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.installer.InstallerFileProvider
import app.morphe.manager.network.service.HttpService
import app.morphe.manager.util.APK_EXTENSIONS
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "Morphe ApkCaptureDownload"

/**
 * Fetches a captured APK and hands it to the ordinary APK picker flow.
 *
 * The URL is signed, so the capture's own headers go with it: asked for without the session that
 * produced it, a CDN answers 403. `HttpService.downloadToFile` runs the builder on its probe and
 * on every byte range it asks for, which is what makes a multi-connection download of a signed
 * URL possible at all.
 *
 * Handing the file to the picker rather than straight to the patcher is deliberate. Everything
 * that guards a hand-picked APK — package name, split requirement, signature, version — then
 * applies to one this app fetched itself, and a capture that picked the wrong variant is caught
 * by the same code that would catch a wrong manual choice.
 */
object ApkCaptureDownloader : KoinComponent {
    private val http: HttpService by inject()
    private val filesystem: Filesystem by inject()

    /**
     * Why the last download failed, in the words of the exception that ended it.
     *
     * Kept for the caller to report. A failure here is answered by a site the app does not
     * control, so the reason is the only thing that says whether the capture was wrong, the
     * session had expired, or the file simply is not there any more.
     */
    @Volatile
    var lastError: String? = null
        private set

    /**
     * Downloads [capture] and returns the URI to hand to the APK picker, or null when nothing
     * usable came back.
     */
    suspend fun download(context: Context, capture: ApkDownloadCapture, appName: String): Uri? =
        withContext(Dispatchers.IO) {
            lastError = null
            val target = filesystem.uiTempDir.resolve(fileNameFor(capture, appName))
            // A previous attempt at the same app is replaced rather than appended to
            target.delete()

            Log.i(
                TAG,
                "Downloading captured APK for $appName from ${capture.url} " +
                    "(referer=${capture.referer != null}, userAgent=${capture.userAgent != null}, " +
                    "cookie=${capture.cookie != null})"
            )

            try {
                http.downloadToFile(
                    saveLocation = target,
                    builder = {
                        // The builder starts from an empty URL, and a request that carries none is
                        // answered as `http://localhost`, which the network security policy
                        // refuses before any of the headers below are ever looked at
                        url(capture.url)
                        // Only the headers a capture actually carries. A header is rejected for a
                        // null value, and a page reached by navigations alone has no cookie to
                        // give, so setting these unconditionally fails the whole download.
                        capture.referer?.takeIf { it.isNotBlank() }
                            ?.let { header(HttpHeaders.Referrer, it) }
                        capture.userAgent?.takeIf { it.isNotBlank() }
                            ?.let { header(HttpHeaders.UserAgent, it) }
                        capture.cookie?.takeIf { it.isNotBlank() }
                            ?.let { header(HttpHeaders.Cookie, it) }
                    }
                )
            } catch (t: Throwable) {
                lastError = "${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "Captured APK download failed for $appName: ${t.javaClass.name}: ${t.message}", t)
                target.delete()
                return@withContext null
            }

            if (!target.exists() || target.length() == 0L) {
                lastError = "empty response"
                Log.w(TAG, "Captured APK download produced no bytes for $appName")
                target.delete()
                null
            } else {
                Log.i(TAG, "Captured APK downloaded: ${target.name} (${target.length()} bytes)")
                // Through the provider the installers already use, so the picker reads it the
                // same way it reads any other content URI and gets the name from there
                InstallerFileProvider.getUriForFile(context, target)
            }
        }

    /**
     * Name for the downloaded file, which is load-bearing: the APK picker takes the archive's
     * extension from the display name a provider reports, and a split archive renamed to `.apk`
     * stops being read as one.
     */
    private fun fileNameFor(capture: ApkDownloadCapture, appName: String): String {
        val fromUrl = capture.url
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
        val extension = fromUrl
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in APK_EXTENSIONS }

        val safeName = appName.ifBlank { "apk" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(60)

        // A signed CDN URL names nothing, and a mirror serves a bundle far more often than a
        // plain APK for the apps worth patching, so the fallback is the bundle extension
        return "$safeName.${extension ?: "apkm"}"
    }
}
