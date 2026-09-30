/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.apkdownload

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.morphe.manager.ui.theme.ManagerTheme
import app.morphe.manager.ui.theme.Theme
import app.morphe.manager.ui.theme.ThemeStyle
import app.morphe.manager.ui.theme.ThemeTraits
import app.morphe.manager.ui.theme.resolveThemeStyle
import app.morphe.manager.ui.viewmodel.SettingsViewModel
import app.morphe.manager.util.AppCardColorDefaults
import org.koin.androidx.compose.koinViewModel

private const val TAG = "Morphe ApkCapture"

/**
 * What a capture produced: the file URL, and the headers the request for it has to be replayed
 * with. A signed URL is rejected when it is asked for without the session that produced it, so
 * these travel together or not at all.
 */
data class ApkDownloadCapture(
    val url: String,
    val referer: String?,
    val userAgent: String?,
    val cookie: String?,
)

/**
 * Loads a download page in a WebView and takes the first APK-family file it produces.
 *
 * The page is left to the user: mirrors gate the file behind a challenge and a confirmation step
 * that only a real browser session gets through, so nothing here tries to fetch the file over
 * plain HTTP. Once the page's own script asks for the archive, [WebViewClient.shouldOverrideUrlLoading]
 * or [WebView.setDownloadListener] sees it first and the URL is handed back instead of loaded.
 *
 * There is deliberately no timeout. Choosing a variant is the user's pace, and a capture that
 * expired underneath them would throw away the navigation they already did.
 */
class ApkDownloadCaptureActivity : ComponentActivity() {

    private var captured = false
    private var currentPageUrl: String? = null
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)

        val pageUrl = intent.getStringExtra(EXTRA_URL)
        if (pageUrl.isNullOrBlank()) {
            finish()
            return
        }

        // Restoring the WebView is not attempted: the page state the user built up is not
        // something this app can reconstruct, and a restore that quietly starts from the top
        // is worse than the Activity coming back empty
        val webView = createWebView()
        this.webView = webView

        // Annotated as returning Unit: this content block holds early returns, and leaving its
        // type to be inferred widens what the enclosing function is taken to return, which then
        // breaks overload resolution for the platform calls made around it
        setContent {
            val vm: SettingsViewModel = koinViewModel()
            val theme by vm.prefs.theme.getAsState()
            val themeStyle by vm.prefs.themeStyle.getAsState()
            val pureBlackTheme by vm.prefs.pureBlackTheme.getAsState()
            val colorAccents by vm.prefs.colorAccents.getAsState()
            val outlines by vm.prefs.outlines.getAsState()
            val customAccentColor by vm.prefs.customAccentColor.getAsState()
            val customThemeColor by vm.prefs.customThemeColor.getAsState()
            val appCardColorMode by vm.prefs.appCardColorMode.getAsState()
            val customAppCardColors by vm.prefs.customAppCardColors.getAsState()
            val appCardColorValues = remember(customAppCardColors) {
                AppCardColorDefaults.decodeColorValues(customAppCardColors)
            }
            val effectiveThemeStyle = resolveThemeStyle(themeStyle, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            val darkTheme = when (theme) {
                Theme.LIGHT -> false
                Theme.DARK -> true
                Theme.SYSTEM -> isSystemInDarkTheme()
            }

            ManagerTheme(
                darkTheme = darkTheme,
                dynamicColor = effectiveThemeStyle == ThemeStyle.MATERIAL_YOU,
                pureBlackTheme = pureBlackTheme,
                traits = ThemeTraits(
                    monochrome = effectiveThemeStyle == ThemeStyle.MONOCHROME,
                    colorAccents = colorAccents,
                    outlines = outlines
                ),
                accentColorHex = customAccentColor.takeUnless { it.isBlank() },
                themeColorHex = customThemeColor.takeUnless { it.isBlank() },
                appCardColorMode = appCardColorMode,
                appCardColorValues = appCardColorValues
            ) {
                ApkCaptureScreen(
                    appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty(),
                    webView = webView,
                    onCancel = { finishWith(Activity.RESULT_CANCELED, null) }
                )
            }
        }

        webView.loadUrl(pageUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        // A mirror that opens its file in a new window would otherwise be dropped, losing the
        // navigation that reaches the archive
        settings.setSupportMultipleWindows(true)
        // Holds the cookie a challenge hands out, so it is still there for the download
        CookieManager.getInstance().setAcceptCookie(true)

        webChromeClient = WebChromeClient()

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (url != null) currentPageUrl = url
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null) currentPageUrl = url
                // A fresh document carries none of the hooks the previous one was given, and
                // whatever they hold by the time they are installed is a capture already
                view?.installCaptureHooks { captured ->
                    if (isApkArchiveDownload(captured)) capture(view, captured, null)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // Subresources are not navigations, and capturing one would take the user out of
                // a page that is still loading
                if (!request.isForMainFrame) return false

                Log.d(TAG, "Apk capture: navigation to $url")
                if (isApkArchiveUrl(url)) {
                    capture(view, url, null)
                    return true
                }
                // A page can learn the file's URL and *then* navigate somewhere that does not
                // name it, so what the hooks hold is worth taking before this navigation goes on
                view?.readCapturedUrl { captured ->
                    if (isApkArchiveDownload(captured)) capture(view, captured, null)
                }
                return false
            }
        }

        // Annotated because DownloadListener is a Java interface, so its parameters arrive as
        // platform types that leave the lambda's own parameters inferred as Any?
        setDownloadListener { url: String?, _: String?, contentDisposition: String?, mimeType: String?, _: Long ->
            Log.d(TAG, "Apk capture: download listener $url ($mimeType, $contentDisposition)")
            capture(this, url, mimeType)
        }
    }

    /**
     * Takes [url] if it is an APK-family archive, ending the Activity with it either way.
     *
     * Returns whether it was taken, so the navigation that produced it can be stopped.
     */
    private fun capture(view: WebView?, url: String?, mimeType: String?): Boolean {
        if (captured) return true
        if (!isApkArchiveDownload(url, mimeType)) {
            Log.w(TAG, "Apk capture: ignored $url ($mimeType)")
            return false
        }

        captured = true
        val downloadUrl = url ?: return false

        val referer = currentPageUrl
        val cookie = runCatching {
            CookieManager.getInstance().getCookie(downloadUrl)
                ?: CookieManager.getInstance().getCookie(referer ?: downloadUrl)
        }.getOrNull()

        Log.i(TAG, "Apk capture: took $downloadUrl")

        finishWith(
            Activity.RESULT_OK,
            ApkDownloadCapture(
                url = downloadUrl,
                referer = referer,
                userAgent = view?.settings?.userAgentString,
                cookie = cookie,
            )
        )
        return true
    }

    private fun finishWith(resultCode: Int, capture: ApkDownloadCapture?) {
        if (captured && capture == null) return
        captured = true
        setResult(
            resultCode,
            capture?.let { Intent().putCapture(it) },
        )
        finish()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            // Detaching the client first stops the callbacks a teardown can still fire from
            // reaching a finished Activity
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "app.morphe.manager.extra.APK_CAPTURE_URL"
        const val EXTRA_APP_NAME = "app.morphe.manager.extra.APK_CAPTURE_APP_NAME"
        const val EXTRA_CAPTURE_URL = "app.morphe.manager.extra.APK_CAPTURE_RESULT_URL"
        const val EXTRA_REFERER = "app.morphe.manager.extra.APK_CAPTURE_REFERER"
        const val EXTRA_USER_AGENT = "app.morphe.manager.extra.APK_CAPTURE_USER_AGENT"
        const val EXTRA_COOKIE = "app.morphe.manager.extra.APK_CAPTURE_COOKIE"

        /**
         * Whether the answer is a capture worth downloading, as opposed to a cancel or a
         * malformed result.
         */
        fun captureFrom(resultCode: Int, data: Intent?): ApkDownloadCapture? {
            if (resultCode != Activity.RESULT_OK) return null
            val url = data?.getStringExtra(EXTRA_CAPTURE_URL)?.takeIf { it.isNotBlank() } ?: return null
            return ApkDownloadCapture(
                url = url,
                referer = data.getStringExtra(EXTRA_REFERER),
                userAgent = data.getStringExtra(EXTRA_USER_AGENT),
                cookie = data.getStringExtra(EXTRA_COOKIE),
            )
        }

        private fun Intent.putCapture(capture: ApkDownloadCapture) = apply {
            putExtra(EXTRA_CAPTURE_URL, capture.url)
            putExtra(EXTRA_REFERER, capture.referer)
            putExtra(EXTRA_USER_AGENT, capture.userAgent)
            putExtra(EXTRA_COOKIE, capture.cookie)
        }
    }
}
