/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.domain.apk.DownloadedApk
import app.morphe.manager.domain.apk.DownloadedApkFinder
import kotlinx.coroutines.delay

// Short enough to pick a download up soon after the browser finishes it, and a folder listing
// is all a check costs once the files in it were read
private const val POLL_INTERVAL_MS = 2_500L

/**
 * Takes the APK of an app the user was sent to download as soon as the browser saves it, so they
 * never have to pick the file they just downloaded. Owned by the ViewModel of whichever screen
 * sent them, so the single-app and the batch flows pick the download up the same way.
 *
 * @param onFound Gets the APK, which is then used as if the user had picked it.
 */
class DownloadedApkLookup(
    private val finder: DownloadedApkFinder,
    private val onFound: (DownloadedApk) -> Unit
) {
    private data class Download(val packageName: String, val since: Long)

    private var download: Download? = null

    /**
     * Waits on a download of [packageName] the user is being sent to. Only files saved from now
     * on count, so an older copy in the folder is never taken for it.
     */
    fun expect(packageName: String) {
        download = Download(packageName, System.currentTimeMillis())
    }

    fun clear() {
        download = null
    }

    /**
     * Looks in the Download folder until the APK is there, and hands it to [onFound]. Run while
     * the user is in the app, as the download may finish after they come back.
     */
    suspend fun watch() {
        while (true) {
            val current = download ?: return
            val apk = finder.find(current.packageName, current.since)
            if (apk != null && download == current) {
                download = null
                onFound(apk)
                return
            }
            delay(POLL_INTERVAL_MS)
        }
    }
}
