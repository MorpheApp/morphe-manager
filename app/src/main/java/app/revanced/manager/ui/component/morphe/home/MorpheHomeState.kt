package app.revanced.manager.ui.component.morphe.home

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.*
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.repository.PatchOptionsRepository
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

private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
private const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

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
class MorpheHomeState(
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
    var showPatchesDialog by mutableStateOf(false)
    var showChangelogDialog by mutableStateOf(false)

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
        apiBundle = sources.firstOrNull { it.uid == 0 }
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
        // Check if patches are being fetched or if no patches available
        if (bundleUpdateInProgress || availablePatches < 1) {
            val message = if (bundleUpdateInProgress) {
                context.getString(R.string.morphe_home_patches_are_loading)
            } else {
                context.getString(R.string.no_patch_found)
            }
            context.toast(message)
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
        val bundles = withContext(Dispatchers.IO) {
            dashboardViewModel.patchBundleRepository
                .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
                .first()
        }

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

    /**
     * Handle download instructions dialog continue action
     * Opens browser to APKMirror search and shows file picker prompt
     */
    fun handleDownloadInstructionsContinue(uriHandler: androidx.compose.ui.platform.UriHandler) {
        val baseQuery = if (pendingPackageName == PACKAGE_YOUTUBE) {
            pendingPackageName
        } else {
            // Some versions of YT Music don't show when the package name is used, use the app name instead
            "YouTube Music"
        }

        val architecture = if (pendingPackageName == PACKAGE_YOUTUBE_MUSIC) {
            // YT Music requires architecture. This logic could be improved
            " (${Build.SUPPORTED_ABIS.first()})"
        } else {
            ""
        }

        val version = pendingRecommendedVersion ?: ""
        // Backslash search parameter opens the first search result
        // Use quotes to ensure it's an exact match of all search terms
        val searchQuery = "\\$baseQuery $version $architecture (nodpi) site:apkmirror.com".replace("  ", " ")
        val searchUrl = "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}"
        Log.d(tag, "Using search query: $searchQuery")

        try {
            uriHandler.openUri(searchUrl)
            // After opening browser, show file picker prompt
            showDownloadInstructionsDialog = false
            showFilePickerPromptDialog = true
        } catch (_: Exception) {
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
            val info = apiBundleInfo as? app.revanced.manager.patcher.patch.PatchBundleInfo
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
): MorpheHomeState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val optionsRepository: PatchOptionsRepository = koinInject()

    val state = remember(dashboardViewModel) {
        MorpheHomeState(
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
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
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
            android.content.pm.PackageManager.GET_META_DATA
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
