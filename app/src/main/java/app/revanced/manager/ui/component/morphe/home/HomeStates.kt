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
import app.revanced.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.network.api.MORPHE_API_URL
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.revanced.manager.ui.component.morphe.utils.rememberFilePickerWithPermission
import app.revanced.manager.ui.component.morphe.utils.toFilePath
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.screen.QuickPatchParams
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.util.*
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
    val dashboardViewModel: DashboardViewModel,
    val optionsRepository: PatchOptionsRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStartQuickPatch: (QuickPatchParams) -> Unit,
    val usingMountInstall: Boolean
) {
    // Dialog visibility states
    var showAndroid11Dialog by mutableStateOf(false)
    var showPatchesSheet by mutableStateOf(false)
    var showChangelogSheet by mutableStateOf(false)
    var showBundleManagementSheet by mutableStateOf(false)
    var showAddBundleDialog by mutableStateOf(false)
    var bundleToRename by mutableStateOf<PatchBundleSource?>(null)
    var showRenameBundleDialog by mutableStateOf(false)

    // Expert mode state
    var showExpertModeDialog by mutableStateOf(false)
    var expertModeSelectedApp by mutableStateOf<SelectedApp?>(null)
    var expertModeBundles by mutableStateOf<List<PatchBundleInfo.Scoped>>(emptyList())
    var expertModePatches by mutableStateOf<PatchSelection>(emptyMap())
    var expertModeOptions by mutableStateOf<Options>(emptyMap())

    // Bundle file selection
    var selectedBundleUri by mutableStateOf<Uri?>(null)
    var selectedBundlePath by mutableStateOf<String?>(null)

    // Bundle picker launcher
    lateinit var openBundlePicker: () -> Unit

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
        if (uri == null) {
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
                context.toast(context.getString(R.string.morphe_home_invalid_apk))
            }
        }
    }

    /**
     * Process selected APK file
     * Validates package name and patch availability
     */
    private suspend fun processSelectedApp(selectedApp: SelectedApp) {
        // If specific package is expected, validate it matches
        if (pendingPackageName != null && selectedApp.packageName != pendingPackageName) {
            showWrongPackageDialog = WrongPackageDialogState(
                expectedPackage = pendingPackageName!!,
                actualPackage = selectedApp.packageName
            )
            // Clean up temporary file
            if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                selectedApp.file.delete()
            }
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
            // Only show unsupported version dialog if we have a recommended version
            // For "other apps", we don't have recommended version, so proceed anyway
            val recommendedVersion = pendingPackageName?.let { recommendedVersions[it] }

            if (recommendedVersion != null) {
                pendingSelectedApp = selectedApp
                showUnsupportedVersionDialog = UnsupportedVersionDialogState(
                    packageName = selectedApp.packageName,
                    version = selectedApp.version ?: "unknown",
                    recommendedVersion = recommendedVersion
                )
                cleanupPendingData(keepSelectedApp = true)
                return
            } else {
                // No patches available for "Other apps" and no recommended version
                context.toast(context.getString(R.string.morphe_home_no_patches_for_app))
                // Clean up temporary file
                if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                    selectedApp.file.delete()
                }
                cleanupPendingData()
                return
            }
        }

        // Start patching with validated app
        startPatchingWithApp(selectedApp, allowIncompatible)
        cleanupPendingData()
    }

    /**
     * Start patching flow with optional expert mode
     */
    suspend fun startPatchingWithApp(selectedApp: SelectedApp, allowIncompatible: Boolean) {
        // Check if expert mode is enabled
        val expertModeEnabled = dashboardViewModel.prefs.useExpertMode.getBlocking()

        val bundles = dashboardViewModel.patchBundleRepository
            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
            .first()

        if (bundles.isEmpty()) {
            context.toast(context.getString(R.string.morphe_home_no_patches_available))
            cleanupPendingData()
            return
        }

        if (expertModeEnabled) {
            // Expert mode: Allow user to select patches from all bundles
            // In Expert mode, always allow incompatible patches so user can see all options
            val effectiveAllowIncompatible = true

            // Get default patch selection (all patches marked with include=true)
            val patches = bundles.toPatchSelection(effectiveAllowIncompatible) { _, patch -> patch.include }

            // Get saved options from repository
            val savedOptions = optionsRepository.getOptions(
                selectedApp.packageName,
                bundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
            )

            // Show Expert mode dialog for patch selection review
            expertModeSelectedApp = selectedApp
            expertModeBundles = bundles
            expertModePatches = patches.toMutableMap()
            expertModeOptions = savedOptions.toMutableMap()
            showExpertModeDialog = true
        } else {
            // Simple mode: Use only default bundle with include=true patches
            // Filter bundles based on allowIncompatible setting
            val defaultBundle = bundles.firstOrNull { it.uid == 0 }
            if (defaultBundle == null) {
                context.toast(context.getString(R.string.morphe_home_no_patches_available))
                cleanupPendingData()
                return
            }

            // Get patches only from default bundle with include=true
            // In Simple mode, respect the allowIncompatible setting
            val allPatches = defaultBundle.patchSequence(allowIncompatible)
                .filter { it.include }
                .map { it.name }
                .toSet()

            val patches = mapOf(0 to allPatches).filterValues { it.isNotEmpty() }

            if (patches.isEmpty() || patches[0]?.isEmpty() == true) {
                context.toast(context.getString(R.string.morphe_home_no_patches_available))
                cleanupPendingData()
                return
            }

            // Empty options - will be loaded from preferences in PatcherViewModel
            val emptyOptions = emptyMap<Int, Map<String, Map<String, Any?>>>()

            // Directly start patching with default bundle patches and empty options placeholder
            proceedWithPatching(selectedApp, patches, emptyOptions)
        }
    }

    /**
     * Proceed with patching after expert mode confirmation or directly
     */
    fun proceedWithPatching(
        selectedApp: SelectedApp,
        patches: PatchSelection,
        options: Options
    ) {
        onStartQuickPatch(
            QuickPatchParams(
                selectedApp = selectedApp,
                patches = patches,
                options = options
            )
        )
        cleanupPendingData()
    }

    /**
     * Toggle patch selection in expert mode
     */
    fun togglePatchInExpertMode(bundleUid: Int, patchName: String) {
        val currentPatches = expertModePatches.toMutableMap()
        val bundlePatches = currentPatches[bundleUid]?.toMutableSet() ?: return

        if (patchName in bundlePatches) {
            bundlePatches.remove(patchName)
        } else {
            bundlePatches.add(patchName)
        }

        if (bundlePatches.isEmpty()) {
            currentPatches.remove(bundleUid)
        } else {
            currentPatches[bundleUid] = bundlePatches
        }

        expertModePatches = currentPatches
    }

    /**
     * Update option value in expert mode
     */
    fun updateOptionInExpertMode(
        bundleUid: Int,
        patchName: String,
        optionKey: String,
        value: Any?
    ) {
        val currentOptions = expertModeOptions.toMutableMap()
        val bundleOptions = currentOptions[bundleUid]?.toMutableMap() ?: mutableMapOf()
        val patchOptions = bundleOptions[patchName]?.toMutableMap() ?: mutableMapOf()

        if (value == null) {
            patchOptions.remove(optionKey)
        } else {
            patchOptions[optionKey] = value
        }

        // Clean up empty maps
        if (patchOptions.isEmpty()) {
            bundleOptions.remove(patchName)
        } else {
            bundleOptions[patchName] = patchOptions
        }

        if (bundleOptions.isEmpty()) {
            currentOptions.remove(bundleUid)
        } else {
            currentOptions[bundleUid] = bundleOptions
        }

        expertModeOptions = currentOptions
    }

    /**
     * Reset options for a patch in expert mode
     */
    fun resetOptionsInExpertMode(bundleUid: Int, patchName: String) {
        val currentOptions = expertModeOptions.toMutableMap()
        val bundleOptions = currentOptions[bundleUid]?.toMutableMap() ?: return

        bundleOptions.remove(patchName)

        if (bundleOptions.isEmpty()) {
            currentOptions.remove(bundleUid)
        } else {
            currentOptions[bundleUid] = bundleOptions
        }

        expertModeOptions = currentOptions
    }

    /**
     * Clean up expert mode data
     */
    fun cleanupExpertModeData() {
        showExpertModeDialog = false
        expertModeSelectedApp = null
        expertModeBundles = emptyList()
        expertModePatches = emptyMap()
        expertModeOptions = emptyMap()
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
        val searchUrl = "$MORPHE_API_URL/v2/web-search/$searchQuery"
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

    // Initialize bundle picker
    state.openBundlePicker = rememberFilePickerWithPermission(
        mimeTypes = MPP_FILE_MIME_TYPES,
        onFilePicked = { uri ->
            state.selectedBundleUri = uri
            state.selectedBundlePath = uri.toFilePath()
        }
    )

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
