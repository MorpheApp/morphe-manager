package app.revanced.manager.ui.component.morphe.home

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE_MUSIC
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.network.api.MORPHE_API_URL
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.screen.QuickPatchParams
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.util.RequestInstallAppsContract
import app.revanced.manager.util.tag
import app.revanced.manager.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder.encode

/**
 * Bundle update status for snackbar display
 */
enum class BundleUpdateStatus {
    Updating,    // Update in progress
    Success,     // Update completed successfully
    Error        // Error occurred (including no internet)
}

/**
 * Dialog state for unsupported version warning
 */
data class UnsupportedVersionDialogState(
    val packageName: String,
    val version: String,
    val recommendedVersion: String?
)

/**
 * Dialog state for wrong package warning
 */
data class WrongPackageDialogState(
    val expectedPackage: String,
    val actualPackage: String
)

/**
 * Main state holder for MorpheHomeScreen
 * Manages all dialogs, user interactions, and APK processing
 */
@Stable
class HomeStates(
    private val dashboardViewModel: DashboardViewModel,
    private val optionsRepository: PatchOptionsRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStartQuickPatch: (QuickPatchParams) -> Unit,
    val usingMountInstall: Boolean
) {
    // Dialog visibility states
    var showAndroid11Dialog by mutableStateOf(false)
    var showBundlesSheet by mutableStateOf(false)
    var isRefreshingBundle by mutableStateOf(false)
    var showPatchesSheet by mutableStateOf(false)
    var showChangelogSheet by mutableStateOf(false)

    // APK selection flow dialogs (3-step process)
    var showApkAvailabilityDialog by mutableStateOf(false)      // Step 1: "Do you have APK?"
    var showDownloadInstructionsDialog by mutableStateOf(false) // Step 2: Download guide
    var showFilePickerPromptDialog by mutableStateOf(false)     // Step 3: Select file prompt

    // Error/warning dialogs
    var showUnsupportedVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
    var showWrongPackageDialog by mutableStateOf<WrongPackageDialogState?>(null)

    // Pending data during APK selection process
    var pendingPackageName by mutableStateOf<String?>(null)
    var pendingAppName by mutableStateOf<String?>(null)
    var pendingRecommendedVersion by mutableStateOf<String?>(null)
    var pendingSelectedApp by mutableStateOf<SelectedApp?>(null)
    var resolvedDownloadUrl by mutableStateOf<String?>(null)

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Manager update dialog state
    var hasCheckedForUpdates by mutableStateOf(false)
    val shouldShowUpdateDialog: Boolean
        get() = !hasCheckedForUpdates &&
                dashboardViewModel.prefs.showManagerUpdateDialogOnLaunch.getBlocking() &&
                !dashboardViewModel.updatedManagerVersion.isNullOrEmpty()

    // Activity result launchers
    lateinit var installAppsPermissionLauncher: ActivityResultLauncher<String>
    lateinit var storagePickerLauncher: ActivityResultLauncher<String>

    // Bundle data
    var apiBundle: PatchBundleSource? = null
        private set

    var recommendedVersions: Map<String, String> = emptyMap()
        private set

    /**
     * Update bundle data when sources or bundle info changes
     */
    fun updateBundleData(sources: List<PatchBundleSource>, bundleInfo: Map<Int, Any>) {
        apiBundle = sources.firstOrNull { it.uid == DEFAULT_SOURCE_UID }
        recommendedVersions = extractRecommendedVersions(bundleInfo)
    }

    /**
     * Handle app button click (YouTube or YouTube Music)
     * Validates state before showing APK selection dialog
     */
    fun handleAppClick(
        packageName: String,
        availablePatches: Int,
        bundleUpdateInProgress: Boolean,
        android11BugActive: Boolean
    ) {
        // Check if patches are being fetched
        if (availablePatches <= 0 || bundleUpdateInProgress) {
            context.toast(context.getString(R.string.morphe_home_patches_are_loading))
            return
        }

        // Check for Android 11 installation bug
        if (android11BugActive) {
            showAndroid11Dialog = true
            return
        }

        // Show APK availability dialog to start selection process
        pendingPackageName = packageName
        pendingAppName = getAppName(packageName)
        pendingRecommendedVersion = recommendedVersions[packageName]
        showApkAvailabilityDialog = true
    }

    /**
     * Handle APK file selection from storage picker
     */
    fun handleApkSelection(uri: Uri?) {
        if (uri == null || pendingPackageName == null) {
            cleanupPendingData()
            return
        }

        scope.launch {
            val selectedApp = withContext(Dispatchers.IO) {
                loadLocalApk(context, uri)
            }

            if (selectedApp != null) {
                processSelectedApp(selectedApp)
            } else {
                context.toast(context.getString(R.string.failed_to_load_apk))
            }
        }
    }

    /**
     * Process selected APK file
     * Validates package name and patch availability
     */
    private suspend fun processSelectedApp(selectedApp: SelectedApp) {
        // Validate package name matches expected
        if (selectedApp.packageName != pendingPackageName) {
            showWrongPackageDialog = WrongPackageDialogState(
                expectedPackage = pendingPackageName!!,
                actualPackage = selectedApp.packageName
            )
            cleanupPendingData()
            return
        }

        val allowIncompatible = dashboardViewModel.prefs.disablePatchVersionCompatCheck.getBlocking()

        // Get available patches for this app version
        val bundles = withContext(Dispatchers.IO) {
            dashboardViewModel.patchBundleRepository
                .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
                .first()
        }

        val patches = bundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }
        val totalPatches = patches.values.sumOf { it.size }

        // Check if any patches are available for this version
        if (totalPatches == 0) {
            pendingSelectedApp = selectedApp
            showUnsupportedVersionDialog = UnsupportedVersionDialogState(
                packageName = selectedApp.packageName,
                version = selectedApp.version ?: "unknown",
                recommendedVersion = recommendedVersions[selectedApp.packageName]
            )
            cleanupPendingData(keepSelectedApp = true)
            return
        }

        // Start patching with validated app
        startPatchingWithApp(selectedApp, allowIncompatible)
        cleanupPendingData()
    }

    /**
     * Start patching process with selected app
     */
    suspend fun startPatchingWithApp(selectedApp: SelectedApp, allowIncompatible: Boolean) {
        val allBundles = withContext(Dispatchers.IO) {
            dashboardViewModel.patchBundleRepository
                .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
                .first()
        }

        // Filter to only use default bundle (bundle 0) in Morphe mode to prevent conflicts with custom patch bundles
        val bundles = allBundles.filter { it.uid == PatchBundleRepository.DEFAULT_SOURCE_UID }

        val patches = bundles.toPatchSelection(allowIncompatible) { _, patch ->
            patch.include &&
                    // Exclude GmsCore support patch in root mode
                    // Eventually this will be improved with patcher changes
                    (!usingMountInstall || !patch.name.equals("GmsCore support", ignoreCase = true))
        }

        val bundlePatches = bundles.associate { scoped ->
            scoped.uid to scoped.patches.associateBy { it.name }
        }

        val options = withContext(Dispatchers.IO) {
            optionsRepository.getOptions(selectedApp.packageName, bundlePatches)
        }

        val params = QuickPatchParams(
            selectedApp = selectedApp,
            patches = patches,
            options = options
        )

        onStartQuickPatch(params)
    }

    // TODO: Move this logic somewhere more appropriate.
    fun resolveDownloadRedirect() {
        fun resolveUrlRedirect(url: String): String {
            return try {
                val originalUrl = URL(url)
                val connection = originalUrl.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000

                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")

                    if (location.isNullOrBlank()) {
                        Log.d(tag, "Location tag is blank: ${connection.responseMessage}")
                        getApiOfflineWebSearchUrl()
                    } else {
                        val resolved =
                            if (location.startsWith("http://") || location.startsWith("https://")) {
                                location
                            } else {
                                val prefix = "${originalUrl.protocol}://${originalUrl.host}"
                                if (location.startsWith("/")) "$prefix$location" else "$prefix/$location"
                            }
                        Log.d(tag, "Result: $resolved")
                        resolved
                    }
                } else {
                    Log.d(tag, "Unexpected response code: $responseCode")
                    getApiOfflineWebSearchUrl()
                }
            } catch (ex: SocketTimeoutException) {
                Log.d(tag, "Timeout while resolving search redirect: $ex")
                // Timeout may be because the network is very slow.
                // Still use web-search api call in external browser.
                url
            } catch (ex: Exception) {
                Log.d(tag, "Exception while resolving search redirect: $ex")
                getApiOfflineWebSearchUrl()
            }
        }

        // Must not escape colon search term separator, but recommended version must be escaped
        // because Android version string can be almost anything.
        val escapedVersion = encode(pendingRecommendedVersion, "UTF-8")
        val searchQuery = "$pendingPackageName:$escapedVersion:${Build.SUPPORTED_ABIS.first()}"
        // To test client fallback logic in getApiOfflineWebSearchUrl(), change this an invalid url.
        val searchUrl = "$MORPHE_API_URL/v1/web-search/$searchQuery"
        Log.d(tag, "Using search url: $searchUrl")

        // Use API web-search if user clicks thru faster than redirect resolving can occur.
        resolvedDownloadUrl = searchUrl

        scope.launch(Dispatchers.IO) {
            var resolved = resolveUrlRedirect(searchUrl)

            // If redirect stays on api.morphe.software, try resolving again
            if (resolved.startsWith(MORPHE_API_URL)) {
                Log.d(tag, "Redirect still on API host, resolving again")
                resolved = resolveUrlRedirect(resolved)
            }

            withContext(Dispatchers.Main) {
                resolvedDownloadUrl = resolved
            }
        }
    }

    fun getApiOfflineWebSearchUrl(): String {
        val architecture = if (pendingPackageName == PACKAGE_YOUTUBE_MUSIC) {
            // YT Music requires architecture. This logic could be improved
            " (${Build.SUPPORTED_ABIS.first()})"
        } else {
            "nodpi"
        }

        val searchQuery = "\"$pendingPackageName\" \"$pendingRecommendedVersion\" \"$architecture\" site:APKMirror.com"

        val searchUrl = "https://google.com/search?q=${encode(searchQuery, "UTF-8")}"
        Log.d(tag, "Using search query: $searchQuery")
        return searchUrl
    }

    /**
     * Handle download instructions dialog continue action
     * Opens browser to APKMirror search and shows file picker prompt.
     */
    fun handleDownloadInstructionsContinue(uriHandler: UriHandler) {
        val urlToOpen = resolvedDownloadUrl!!

        try {
            uriHandler.openUri(urlToOpen)
            showDownloadInstructionsDialog = false
            showFilePickerPromptDialog = true
        } catch (ex: Exception) {
            Log.d(tag, "Failed to open URL: $ex")
            context.toast(context.getString(R.string.morphe_home_failed_to_open_url))
            showDownloadInstructionsDialog = false
            cleanupPendingData()
        }
    }

    /**
     * Get localized app name for package
     */
    fun getAppName(packageName: String): String {
        return when (packageName) {
            PACKAGE_YOUTUBE -> context.getString(R.string.morphe_home_youtube)
            PACKAGE_YOUTUBE_MUSIC -> context.getString(R.string.morphe_home_youtube_music)
            else -> packageName
        }
    }

    /**
     * Clean up all pending data
     */
    fun cleanupPendingData(keepSelectedApp: Boolean = false) {
        pendingPackageName = null
        pendingAppName = null
        pendingRecommendedVersion = null
        resolvedDownloadUrl = null
        if (!keepSelectedApp) {
            // Delete temporary file if exists
            pendingSelectedApp?.let { app ->
                if (app is SelectedApp.Local && app.temporary) {
                    app.file.delete()
                }
            }
            pendingSelectedApp = null
        }
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
    }

    /**
     * Extract recommended versions from bundle info
     */
    private fun extractRecommendedVersions(bundleInfo: Map<Int, Any>): Map<String, String> {
        return bundleInfo[0]?.let { apiBundleInfo ->
            val info = apiBundleInfo as? PatchBundleInfo
            info?.let { it ->
                mapOf(
                    PACKAGE_YOUTUBE to it.patches
                        .filter { patch ->
                            patch.compatiblePackages?.any { pkg -> pkg.packageName == PACKAGE_YOUTUBE } == true
                        }
                        .flatMap { patch ->
                            patch.compatiblePackages
                                ?.firstOrNull { pkg -> pkg.packageName == PACKAGE_YOUTUBE }
                                ?.versions
                                ?: emptyList()
                        }
                        .maxByOrNull { it }
                        .orEmpty(),
                    PACKAGE_YOUTUBE_MUSIC to it.patches
                        .filter { patch ->
                            patch.compatiblePackages?.any { pkg -> pkg.packageName == PACKAGE_YOUTUBE_MUSIC } == true
                        }
                        .flatMap { patch ->
                            patch.compatiblePackages
                                ?.firstOrNull { pkg -> pkg.packageName == PACKAGE_YOUTUBE_MUSIC }
                                ?.versions
                                ?: emptyList()
                        }
                        .maxByOrNull { it }
                        .orEmpty()
                ).filterValues { it.isNotEmpty() }
            } ?: emptyMap()
        } ?: emptyMap()
    }
}

/**
 * Remember MorpheHomeState with proper lifecycle management
 */
@Composable
fun rememberMorpheHomeState(
    dashboardViewModel: DashboardViewModel,
    sources: List<PatchBundleSource>,
    bundleInfo: Map<Int, Any>,
    onStartQuickPatch: (QuickPatchParams) -> Unit,
    usingMountInstall : Boolean
): HomeStates {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val optionsRepository: PatchOptionsRepository = koinInject()

    val state = remember(dashboardViewModel) {
        HomeStates(
            dashboardViewModel = dashboardViewModel,
            optionsRepository = optionsRepository,
            context = context,
            scope = scope,
            onStartQuickPatch = onStartQuickPatch,
            usingMountInstall = usingMountInstall
        )
    }

    // Initialize launchers
    state.installAppsPermissionLauncher = rememberLauncherForActivityResult(
        RequestInstallAppsContract
    ) { state.showAndroid11Dialog = false }

    state.storagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> state.handleApkSelection(uri) }

    // Update bundle data when sources or bundleInfo changes
    LaunchedEffect(sources, bundleInfo) {
        state.updateBundleData(sources, bundleInfo)
    }

    return state
}

/**
 * Load local APK file and extract package info
 */
suspend fun loadLocalApk(
    context: Context,
    uri: Uri
): SelectedApp.Local? = withContext(Dispatchers.IO) {
    try {
        val tempFile = File(context.cacheDir, "temp_apk_${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val pm = context.packageManager
        val packageInfo = pm.getPackageArchiveInfo(
            tempFile.absolutePath,
            PackageManager.GET_META_DATA
        )

        if (packageInfo == null) {
            tempFile.delete()
            return@withContext null
        }

        // Return SelectedApp without validation - let caller handle it
        SelectedApp.Local(
            packageName = packageInfo.packageName,
            version = packageInfo.versionName ?: "unknown",
            file = tempFile,
            temporary = true
        )
    } catch (_: Exception) {
        null
    }
}
