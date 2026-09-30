/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.R
import app.morphe.manager.util.toast

/**
 * The action that opens a download page in the capture WebView, or null while no page is known.
 *
 * [onCaptured] receives the file the page produced together with the headers its request has to
 * be replayed with. A cancel or a dismissed page reports nothing, so it is not an error and is
 * not surfaced as one.
 *
 * @param downloadUrl The page to open. Null while the redirect has not landed, because the link
 *     on hand until then is the unfollowed one, which is not a page.
 * @param appName Named in the capture screen, so the user knows which app they are fetching.
 */
@Composable
fun rememberApkDownloadCaptureAction(
    downloadUrl: String?,
    appName: String,
    onCaptured: (ApkDownloadCapture) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val capture = ApkDownloadCaptureActivity.captureFrom(result.resultCode, result.data)
        if (capture == null) {
            context.toast(context.getString(R.string.apk_capture_cancelled))
            return@rememberLauncherForActivityResult
        }
        onCaptured(capture)
    }

    val url = downloadUrl?.takeIf { it.isNotBlank() } ?: return null

    return remember(url, appName) {
        {
            launcher.launch(
                Intent(context, ApkDownloadCaptureActivity::class.java)
                    .putExtra(ApkDownloadCaptureActivity.EXTRA_URL, url)
                    .putExtra(ApkDownloadCaptureActivity.EXTRA_APP_NAME, appName)
            )
        }
    }
}
