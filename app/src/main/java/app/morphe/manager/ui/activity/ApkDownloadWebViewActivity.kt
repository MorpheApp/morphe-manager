/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.activity

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.theme.ManagerTheme
import app.morphe.manager.ui.screen.patcher.PatcherBottomActionBar
import app.morphe.manager.ui.screen.shared.AnimatedBackground
import app.morphe.manager.ui.screen.shared.BackgroundType
import app.morphe.manager.ui.screen.shared.ConfirmDialog
import app.morphe.manager.ui.viewmodel.HomeAndPatcherMessages
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) {
        return "$bytes B"
    }

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1

    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    return if (value >= 100) {
        "%.0f %s".format(Locale.getDefault(), value, units[unitIndex])
    } else if (value >= 10) {
        "%.1f %s".format(Locale.getDefault(), value, units[unitIndex])
    } else {
        "%.2f %s".format(Locale.getDefault(), value, units[unitIndex])
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 60) {
        return "${seconds}s"
    }

    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    if (minutes < 60) {
        return if (remainingSeconds == 0L) {
            "${minutes}m"
        } else {
            "${minutes}m ${remainingSeconds}s"
        }
    }

    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return if (remainingMinutes == 0L) {
        "${hours}h"
    } else {
        "${hours}h ${remainingMinutes}m"
    }
}

/**
 * In-app browser for APK download pages.
 *
 * It intercepts the website download button through WebView's download callback and forwards the
 * archive to DownloadManager, then returns the downloaded Uri back to the caller.
 */
class ApkDownloadWebViewActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "app.morphe.manager.extra.APK_WEBVIEW_URL"
        private const val EXTRA_PACKAGE_NAME = "app.morphe.manager.extra.APK_WEBVIEW_PACKAGE"
        private const val EXTRA_VERSION_NAME = "app.morphe.manager.extra.APK_WEBVIEW_VERSION"

        private const val EXTRA_RESULT_URI = "app.morphe.manager.extra.APK_WEBVIEW_RESULT_URI"
        private const val EXTRA_RESULT_STATUS = "app.morphe.manager.extra.APK_WEBVIEW_RESULT_STATUS"

        const val RESULT_STATUS_COMPLETED = "completed"
        const val RESULT_STATUS_ABORTED = "aborted"
        const val RESULT_STATUS_FAILED = "failed"
        const val RESULT_STATUS_RETURN_TO_APK_HELP = "return_to_apk_help"

        fun createIntent(context: Context, url: String, packageName: String, version: String?): Intent =
            Intent(context, ApkDownloadWebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_VERSION_NAME, version)
            }

        fun resultUri(intent: Intent?): Uri? =
            intent?.getStringExtra(EXTRA_RESULT_URI)?.let(Uri::parse)

        fun resultStatus(intent: Intent?): String? =
            intent?.getStringExtra(EXTRA_RESULT_STATUS)
    }

    private var downloadId: Long? = null
    private var progressJob: Job? = null

    private lateinit var webView: WebView

    private lateinit var downloadScreen: View

    private var downloadFileName by mutableStateOf("")
    private var downloadPercent by mutableIntStateOf(-1)
    private var downloadIsFinalizing by mutableStateOf(false)
    private var showCancelConfirmation by mutableStateOf(false)
    private var downloadBytes by mutableLongStateOf(0L)
    private var downloadTotalBytes by mutableLongStateOf(-1L)
    private var downloadSpeed by mutableLongStateOf(0L)
    private var downloadEtaSeconds by mutableLongStateOf(-1L)

    private val blockedAdHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adservice.google.",
        "adnxs.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "adsystem.com",
        "scorecardresearch.com"
    )

    private val blockedPathHints = listOf(
        "/ads",
        "adservice",
        "doubleclick",
        "googlesyndication",
        "googletagmanager",
        "google-analytics",
        "analytics"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val version = intent.getStringExtra(EXTRA_VERSION_NAME)

        if (url.isNullOrBlank() || packageName.isNullOrBlank()) {
            finishWithStatus(RESULT_STATUS_FAILED)
            return
        }

        val content = buildContentView()
        setContentView(content)

        onBackPressedDispatcher.addCallback(this) {
            if (showCancelConfirmation) {
                showCancelConfirmation = false
            } else if (downloadId != null) {
                showCancelConfirmation = true
            } else if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finishWithStatus(RESULT_STATUS_ABORTED)
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (downloadId != null) {
                    // Download progress is updated by DownloadManager polling.
                }
            }
        }

        try {
            webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                try {
                    startDownload(
                        downloadUrl = downloadUrl,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimeType,
                        packageName = packageName,
                        version = version
                    )
                } catch (e: Throwable) {
                    // Report and finish with a graceful failure
                    e.printStackTrace()
                    finishWithStatus(RESULT_STATUS_FAILED)
                }
            }

            webView.loadUrl(url)
        } catch (e: Throwable) {
            // Protect against WebView setup failures (manufacturer bugs, OOM, etc.)
            e.printStackTrace()
            finishWithStatus(RESULT_STATUS_FAILED)
            return
        }
    }

    override fun onDestroy() {
        progressJob?.cancel()

        if (::webView.isInitialized) {
            webView.destroy()
        }

        super.onDestroy()
    }

    private fun buildContentView(): View {
        val root = FrameLayout(this)

        webView = WebView(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        downloadScreen = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT);
            isVisible = false
            addView(
                ComposeView(context).apply {
                    setContent {
                        val prefs: PreferencesManager = koinInject()

                        val theme by prefs.theme.getAsState()
                        val themeStyle by prefs.themeStyle.getAsState()
                        val pureBlackTheme by prefs.pureBlackTheme.getAsState()
                        val customAccentColor by prefs.customAccentColor.getAsState()
                        val customThemeColor by prefs.customThemeColor.getAsState()
                        val appCardColorMode by prefs.appCardColorMode.getAsState()
                        val customAppCardColors by prefs.customAppCardColors.getAsState()

                        val appCardColorValues = remember(customAppCardColors) {
                            app.morphe.manager.util.AppCardColorDefaults.decodeColorValues(
                                customAppCardColors
                            )
                        }

                        val supportsDynamicColor =
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

                        val effectiveThemeStyle =
                            app.morphe.manager.ui.theme.resolveThemeStyle(
                                themeStyle,
                                supportsDynamicColor
                            )

                        val darkTheme = when (theme) {
                            app.morphe.manager.ui.theme.Theme.LIGHT -> false
                            app.morphe.manager.ui.theme.Theme.DARK -> true
                            app.morphe.manager.ui.theme.Theme.SYSTEM ->
                                androidx.compose.foundation.isSystemInDarkTheme()
                        }

                        app.morphe.manager.ui.theme.ManagerTheme(
                            darkTheme = darkTheme,
                            dynamicColor =
                                effectiveThemeStyle ==
                                    app.morphe.manager.ui.theme.ThemeStyle.MATERIAL_YOU,
                            pureBlackTheme = pureBlackTheme,
                            monochromeTheme =
                                effectiveThemeStyle ==
                                    app.morphe.manager.ui.theme.ThemeStyle.MONOCHROME,
                            accentColorHex = customAccentColor.takeUnless { it.isBlank() },
                            themeColorHex = customThemeColor.takeUnless { it.isBlank() },
                            appCardColorMode = appCardColorMode,
                            appCardColorValues = appCardColorValues
                        ) {
                            DownloadProgressScreen(
                                fileName = downloadFileName,
                                percent = downloadPercent,
                                downloadedBytes = downloadBytes,
                                totalBytes = downloadTotalBytes,
                                speedBytesPerSecond = downloadSpeed,
                                etaSeconds = downloadEtaSeconds,
                                finalizing = downloadIsFinalizing,
                                showCancelConfirmation = showCancelConfirmation,
                                onCancelClick = { showCancelConfirmation = true },
                                onCancelConfirm = {
                                    showCancelConfirmation = false
                                    cancelActiveDownload(returnToApkHelp = true)
                                },
                                onCancelDismiss = {
                                    showCancelConfirmation = false
                                }
                            )
                        }
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        root.addView(
            downloadScreen,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        return root
    }

    private fun startDownload(
        downloadUrl: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        packageName: String,
        version: String?
    ) {
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val guessedName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
        val fileName = if (guessedName.endsWith(".apk", ignoreCase = true) ||
            guessedName.endsWith(".apkm", ignoreCase = true) ||
            guessedName.endsWith(".apks", ignoreCase = true) ||
            guessedName.endsWith(".xapk", ignoreCase = true)
        ) {
            guessedName
        } else {
            buildString {
                append(packageName)
                version?.takeIf { it.isNotBlank() }?.let {
                    append("-")
                    append(it)
                }
                append(".apk")
            }
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(packageName)
            setDescription(version ?: getString(R.string.home_recommended_version))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(
                this@ApkDownloadWebViewActivity,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            mimeType?.let { setMimeType(it) }
            userAgent?.let { addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(downloadUrl)?.let { addRequestHeader("Cookie", it) }
        }

        downloadFileName = fileName
        downloadPercent = -1
        downloadBytes = 0L
        downloadTotalBytes = -1L
        downloadSpeed = 0L
        downloadEtaSeconds = -1L
        downloadIsFinalizing = false

        downloadId = manager.enqueue(request)
        webView.isVisible = false
        downloadScreen.isVisible = true
        downloadFileName = fileName
        downloadPercent = -1
        downloadIsFinalizing = false
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            val activeId = downloadId ?: return@launch

            var previousBytes = 0L
            var previousTime = System.currentTimeMillis()

            // Keep the last known valid values.
            var lastKnownTotal = -1L
            var lastKnownSpeed = 0L

            while (isActive) {
                val query = DownloadManager.Query().setFilterById(activeId)

                manager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        finishWithStatus(RESULT_STATUS_FAILED)
                        return@launch
                    }

                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS
                        )
                    )

                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                    )

                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                        )
                    )

                    // DownloadManager can temporarily report -1.
                    // Only replace the total when it is actually valid.
                    if (total > 0L) {
                        lastKnownTotal = total
                        downloadTotalBytes = total
                    }

                    // Downloaded bytes should normally never be negative.
                    if (downloaded >= 0L) {
                        downloadBytes = downloaded
                    }

                    // Calculate speed.
                    val now = System.currentTimeMillis()
                    val elapsedMillis = now - previousTime

                    if (elapsedMillis >= 250L) {
                        val bytesDelta = downloaded - previousBytes

                        if (bytesDelta >= 0L) {
                            val speed = (
                                bytesDelta * 1000L / elapsedMillis
                            )

                            // Only update the displayed speed if we have
                            // actually measured some data.
                            if (speed > 0L) {
                                lastKnownSpeed = speed
                                downloadSpeed = speed
                            }
                        }

                        previousBytes = downloaded
                        previousTime = now
                    }

                    val effectiveTotal = lastKnownTotal

                    if (effectiveTotal > 0L) {
                        val percent = ((downloaded * 100L) / effectiveTotal)
                            .toInt()
                            .coerceIn(0, 100)

                        downloadPercent = percent

                        if (lastKnownSpeed > 0L) {
                            val remainingBytes = (effectiveTotal - downloaded)
                                .coerceAtLeast(0L)

                            downloadEtaSeconds =
                                remainingBytes / lastKnownSpeed
                        }

                        downloadIsFinalizing =
                            percent >= 99 &&
                            status != DownloadManager.STATUS_SUCCESSFUL
                    } else {
                        downloadPercent = -1
                        downloadEtaSeconds = -1L
                        downloadIsFinalizing = false
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val localUri = cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_LOCAL_URI
                                )
                            )

                            val uri = localUri?.let(Uri::parse)

                            if (uri == null) {
                                finishWithStatus(RESULT_STATUS_FAILED)
                            } else {
                                finishWithStatus(
                                    RESULT_STATUS_COMPLETED,
                                    uri
                                )
                            }

                            return@launch
                        }

                        DownloadManager.STATUS_FAILED -> {
                            finishWithStatus(RESULT_STATUS_FAILED)
                            return@launch
                        }
                    }
                }

                delay(300)
            }
        }
    }

    private fun cancelActiveDownload(returnToApkHelp: Boolean = false) {
        val id = downloadId
        if (id != null) {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.remove(id)
            downloadId = null
        }
        if (returnToApkHelp) {
            finishWithStatus(RESULT_STATUS_RETURN_TO_APK_HELP)
        } else {
            finishWithStatus(RESULT_STATUS_ABORTED)
        }
    }

    private fun finishWithStatus(status: String, uri: Uri? = null) {
        progressJob?.cancel()
        downloadId = null

        val resultIntent = Intent().apply {
            putExtra(EXTRA_RESULT_STATUS, status)
            uri?.let { putExtra(EXTRA_RESULT_URI, it.toString()) }
        }

        if (status == RESULT_STATUS_COMPLETED && uri != null) {
            setResult(RESULT_OK, resultIntent)
        } else {
            setResult(RESULT_CANCELED, resultIntent)
        }
        finish()
    }
}

@Composable
private fun DownloadProgressScreen(
    fileName: String,
    percent: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    speedBytesPerSecond: Long,
    etaSeconds: Long,
    finalizing: Boolean,
    showCancelConfirmation: Boolean,
    onCancelClick: () -> Unit,
    onCancelConfirm: () -> Unit,
    onCancelDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs: PreferencesManager = koinInject()
    val backgroundType by prefs.backgroundType.getAsState()
    val enableParallax by prefs.enableBackgroundParallax.getAsState()
    val randomInterval by prefs.randomBackgroundInterval.getAsState()

    val resolvedRandomBackground = remember(backgroundType, randomInterval) {
        if (backgroundType == BackgroundType.RANDOM) {
            BackgroundType.RANDOMIZABLE.random(Random(System.currentTimeMillis()))
        } else {
            null
        }
    }

    val tipMessage = remember {
        mutableIntStateOf(HomeAndPatcherMessages.getPatcherMessage(context))
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(10.seconds)
            tipMessage.intValue = HomeAndPatcherMessages.getPatcherMessage(context)
        }
    }

    val progress = if (percent >= 0) percent / 100f else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedBackground(
            type = backgroundType,
            resolvedType = resolvedRandomBackground,
            enableParallax = enableParallax,
            speedMultiplier = { 1.25f }
        )

        if (showCancelConfirmation) {
            ConfirmDialog(
                title = stringResource(R.string.home_download_webview_cancel_title),
                message = stringResource(R.string.home_download_webview_cancel_message),
                primaryText = stringResource(R.string.yes),
                secondaryText = stringResource(R.string.no),
                onConfirm = onCancelConfirm,
                onDismiss = onCancelDismiss
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_download_webview_downloading),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                    )

                    if (fileName.isNotBlank()) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(280.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 12.dp,
                        )

                        if (percent >= 0) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 12.dp,
                                strokeCap = StrokeCap.Round,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 12.dp,
                                strokeCap = StrokeCap.Round,
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val valueText = if (percent >= 0) {
                                stringResource(R.string.home_download_webview_progress, percent)
                            } else {
                                stringResource(R.string.home_download_webview_progress_indeterminate)
                            }

                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (percent >= 56) 56.sp else 48.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )

                            if (finalizing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.home_download_webview_finalizing),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (totalBytes > 0 && !finalizing) {
                            Text(
                                text = "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (finalizing) {
                            Text(
                                text = stringResource(R.string.home_download_stats_bytes_downloaded, formatBytes(downloadedBytes)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (speedBytesPerSecond > 0) {
                            if (finalizing) {
                                Text(
                                    text = stringResource(R.string.home_download_stats_finalizing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "${formatBytes(speedBytesPerSecond)}/s",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (etaSeconds >= 0 && !finalizing) {
                            Text(
                                text = stringResource(R.string.home_download_stats_eta, formatDuration((etaSeconds * 1.5).toLong())),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            PatcherBottomActionBar(
                showCancelButton = true,
                showHomeButton = false,
                showSaveButton = false,
                showErrorButton = false,
                onCancelClick = onCancelClick,
                onHomeClick = {},
                onSaveClick = {},
                onErrorClick = {}
            )
        }
    }
}
