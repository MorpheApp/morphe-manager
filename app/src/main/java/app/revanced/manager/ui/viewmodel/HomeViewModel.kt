package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_REDDIT
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE_MUSIC
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.revanced.manager.domain.repository.PatchOptionsRepository
import app.revanced.manager.network.api.MORPHE_API_URL
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.patcher.split.SplitApkPreparer
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.tag
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder.encode
import java.util.zip.ZipInputStream

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
 * Quick patch parameters
 */
data class QuickPatchParams(
    val selectedApp: SelectedApp,
    val patches: PatchSelection,
    val options: Options
)

/**
 * ViewModel for MorpheHomeScreen
 * Manages all dialogs, user interactions, and APK processing
 */
class HomeViewModel(
    private val app: Application,
    private val patchBundleRepository: PatchBundleRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    // Dialog visibility states
    var showAndroid11Dialog by mutableStateOf(false)
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

    // APK selection flow dialogs
    var showApkAvailabilityDialog by mutableStateOf(false)
    var showDownloadInstructionsDialog by mutableStateOf(false)
    var showFilePickerPromptDialog by mutableStateOf(false)

    // Error/warning dialogs
    var showUnsupportedVersionDialog by mutableStateOf<UnsupportedVersionDialogState?>(null)
    var showWrongPackageDialog by mutableStateOf<WrongPackageDialogState?>(null)

    // Pending data during APK selection
    var pendingPackageName by mutableStateOf<String?>(null)
    var pendingAppName by mutableStateOf<String?>(null)
    var pendingRecommendedVersion by mutableStateOf<String?>(null)
    var pendingSelectedApp by mutableStateOf<SelectedApp?>(null)
    var resolvedDownloadUrl by mutableStateOf<String?>(null)

    // Bundle update snackbar state
    var showBundleUpdateSnackbar by mutableStateOf(false)
    var snackbarStatus by mutableStateOf(BundleUpdateStatus.Updating)

    // Loading state for installed apps
    var installedAppsLoading by mutableStateOf(true)

    // Bundle data
    private var apiBundle: PatchBundleSource? = null
    var recommendedVersions: Map<String, String> = emptyMap()
        private set

    // Using mount install (set externally)
    var usingMountInstall: Boolean = false

    // Callback for starting patch
    var onStartQuickPatch: ((QuickPatchParams) -> Unit)? = null

    // Main app packages that use default bundle only
    private val mainAppPackages = setOf(
        PACKAGE_YOUTUBE,
        PACKAGE_YOUTUBE_MUSIC,
        PACKAGE_REDDIT
    )

    /**
     * Update bundle data when sources or bundle info changes
     */
    fun updateBundleData(sources: List<PatchBundleSource>, bundleInfo: Map<Int, Any>) {
        apiBundle = sources.firstOrNull { it.uid == DEFAULT_SOURCE_UID }
        recommendedVersions = extractRecommendedVersions(bundleInfo)
    }

    /**
     * Update loading state
     */
    fun updateLoadingState(bundleUpdateInProgress: Boolean, hasInstalledApps: Boolean) {
        installedAppsLoading = bundleUpdateInProgress || !hasInstalledApps
    }

    /**
     * Handle app button click (YouTube or YouTube Music)
     */
    fun handleAppClick(
        packageName: String,
        availablePatches: Int,
        bundleUpdateInProgress: Boolean,
        android11BugActive: Boolean,
        installedApp: InstalledApp?
    ) {
        // If app is installed, allow click even during updates
        if (installedApp != null) {
            return // Caller will handle navigation
        }

        // Check if patches are being fetched
        if (availablePatches <= 0 || bundleUpdateInProgress) {
            app.toast(app.getString(R.string.morphe_home_sources_are_loading))
            return
        }

        // Check for Android 11 installation bug
        if (android11BugActive) {
            showAndroid11Dialog = true
            return
        }

        showPatchDialog(packageName)
    }

    /**
     * Show patch dialog
     */
    fun showPatchDialog(packageName: String) {
        pendingPackageName = packageName
        pendingAppName = getAppName(packageName)
        pendingRecommendedVersion = recommendedVersions[packageName]
        showApkAvailabilityDialog = true
    }

    /**
     * Handle APK file selection
     */
    fun handleApkSelection(uri: Uri?) {
        if (uri == null) {
            cleanupPendingData()
            return
        }

        viewModelScope.launch {
            val selectedApp = withContext(Dispatchers.IO) {
                loadLocalApk(app, uri)
            }

            if (selectedApp != null) {
                processSelectedApp(selectedApp)
            } else {
                app.toast(app.getString(R.string.morphe_home_invalid_apk))
            }
        }
    }

    /**
     * Process selected APK file
     */
    private suspend fun processSelectedApp(selectedApp: SelectedApp) {
        // Validate package name if expected
        if (pendingPackageName != null && selectedApp.packageName != pendingPackageName) {
            showWrongPackageDialog = WrongPackageDialogState(
                expectedPackage = pendingPackageName!!,
                actualPackage = selectedApp.packageName
            )
            if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                selectedApp.file.delete()
            }
            cleanupPendingData()
            return
        }

        val allowIncompatible = prefs.disablePatchVersionCompatCheck.getBlocking()

        // Get available patches
        val bundles = withContext(Dispatchers.IO) {
            patchBundleRepository
                .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
                .first()
        }

        val patches = bundles.toPatchSelection(allowIncompatible) { _, patch -> patch.include }
        val totalPatches = patches.values.sumOf { it.size }

        // Check if any patches available
        if (totalPatches == 0) {
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
                app.toast(app.getString(R.string.morphe_home_no_patches_for_app))
                if (selectedApp is SelectedApp.Local && selectedApp.temporary) {
                    selectedApp.file.delete()
                }
                cleanupPendingData()
                return
            }
        }

        startPatchingWithApp(selectedApp, allowIncompatible)
    }

    /**
     * Start patching flow
     */
    suspend fun startPatchingWithApp(
        selectedApp: SelectedApp,
        allowIncompatible: Boolean
    ) {
        val expertModeEnabled = prefs.useExpertMode.getBlocking()

        val allBundles = patchBundleRepository
            .scopedBundleInfoFlow(selectedApp.packageName, selectedApp.version)
            .first()

        if (allBundles.isEmpty()) {
            app.toast(app.getString(R.string.morphe_home_no_patches_available))
            cleanupPendingData()
            return
        }

        // Patch filter: exclude GmsCore support in root mode
        val shouldIncludePatch: (Int, PatchInfo) -> Boolean = { _, patch ->
            patch.include && (!usingMountInstall || !patch.name.equals("GmsCore support", ignoreCase = true))
        }

        if (expertModeEnabled) {
            // Expert Mode: show all patches from all bundles
            val patches = allBundles.toPatchSelection(true, shouldIncludePatch)

            val savedOptions = optionsRepository.getOptions(
                selectedApp.packageName,
                allBundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
            )

            expertModeSelectedApp = selectedApp
            expertModeBundles = allBundles
            expertModePatches = patches.toMutableMap()
            expertModeOptions = savedOptions.toMutableMap()
            showExpertModeDialog = true
        } else {
            // Simple Mode: check if this is a main app or "other app"
            val isMainApp = selectedApp.packageName in mainAppPackages

            if (isMainApp) {
                // For main apps: use only default bundle
                val defaultBundle = allBundles.find { it.uid == DEFAULT_SOURCE_UID }

                if (defaultBundle == null || !defaultBundle.enabled) {
                    app.toast(app.getString(R.string.morphe_home_default_source_disabled))
                    cleanupPendingData()
                    return
                }

                val patchNames = defaultBundle.patchSequence(allowIncompatible)
                    .filter { shouldIncludePatch(defaultBundle.uid, it) }
                    .mapTo(mutableSetOf()) { it.name }

                if (patchNames.isEmpty()) {
                    app.toast(app.getString(R.string.morphe_home_no_patches_available))
                    cleanupPendingData()
                    return
                }

                proceedWithPatching(selectedApp, mapOf(defaultBundle.uid to patchNames), emptyMap())
            } else {
                // For "Other Apps": search all enabled bundles for patches
                val bundleWithPatches = allBundles
                    .filter { it.enabled }
                    .map { bundle ->
                        val patchNames = bundle.patchSequence(allowIncompatible)
                            .filter { shouldIncludePatch(bundle.uid, it) }
                            .mapTo(mutableSetOf()) { it.name }
                        bundle to patchNames
                    }
                    .filter { (_, patches) -> patches.isNotEmpty() }

                if (bundleWithPatches.isEmpty()) {
                    app.toast(app.getString(R.string.morphe_home_no_patches_available))
                    cleanupPendingData()
                    return
                }

                // Use all available patches from all bundles
                val allPatches = bundleWithPatches.associate { (bundle, patches) ->
                    bundle.uid to patches
                }

                proceedWithPatching(selectedApp, allPatches, emptyMap())
            }
        }
    }

    /**
     * Proceed with patching
     */
    fun proceedWithPatching(
        selectedApp: SelectedApp,
        patches: PatchSelection,
        options: Options
    ) {
        onStartQuickPatch?.invoke(
            QuickPatchParams(
                selectedApp = selectedApp,
                patches = patches,
                options = options
            )
        )

        // Clean only UI state
        pendingPackageName = null
        pendingAppName = null
        pendingRecommendedVersion = null
        resolvedDownloadUrl = null
        showDownloadInstructionsDialog = false
        showFilePickerPromptDialog = false
    }

    /**
     * Toggle patch in expert mode
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
     * Update option in expert mode
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

    /**
     * Resolve download redirect
     */
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
                url
            } catch (ex: Exception) {
                Log.d(tag, "Exception while resolving search redirect: $ex")
                getApiOfflineWebSearchUrl()
            }
        }

        // Handle null pendingRecommendedVersion
        val escapedVersion = pendingRecommendedVersion?.let { encode(it, "UTF-8") } ?: ""
        val searchQuery = "$pendingPackageName:$escapedVersion:${Build.SUPPORTED_ABIS.first()}"
        val searchUrl = "$MORPHE_API_URL/v2/web-search/$searchQuery"
        Log.d(tag, "Using search url: $searchUrl")

        resolvedDownloadUrl = searchUrl

        viewModelScope.launch(Dispatchers.IO) {
            var resolved = resolveUrlRedirect(searchUrl)

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
            " (${Build.SUPPORTED_ABIS.first()})"
        } else {
            "nodpi"
        }

        // Handle null pendingRecommendedVersion
        val versionPart = pendingRecommendedVersion?.let { "\"$it\"" } ?: ""
        val searchQuery = "\"$pendingPackageName\" $versionPart \"$architecture\" site:APKMirror.com"
        val searchUrl = "https://google.com/search?q=${encode(searchQuery, "UTF-8")}"
        Log.d(tag, "Using search query: $searchQuery")
        return searchUrl
    }

    /**
     * Handle download instructions continue
     */
    fun handleDownloadInstructionsContinue(onOpenUrl: (String) -> Boolean) {
        val urlToOpen = resolvedDownloadUrl!!

        if (onOpenUrl(urlToOpen)) {
            showDownloadInstructionsDialog = false
            showFilePickerPromptDialog = true
        } else {
            Log.d(tag, "Failed to open URL")
            app.toast(app.getString(R.string.morphe_sources_failed_to_open_url))
            showDownloadInstructionsDialog = false
            cleanupPendingData()
        }
    }

    /**
     * Get localized app name
     */
    fun getAppName(packageName: String): String {
        return when (packageName) {
            PACKAGE_YOUTUBE -> app.getString(R.string.morphe_home_youtube)
            PACKAGE_YOUTUBE_MUSIC -> app.getString(R.string.morphe_home_youtube_music)
            PACKAGE_REDDIT -> app.getString(R.string.morphe_home_reddit)
            else -> packageName
        }
    }

    /**
     * Clean up pending data
     */
    fun cleanupPendingData(keepSelectedApp: Boolean = false) {
        pendingPackageName = null
        pendingAppName = null
        pendingRecommendedVersion = null
        resolvedDownloadUrl = null
        if (!keepSelectedApp) {
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
     * Save options to repository
     */
    fun saveOptions(packageName: String, options: Options) {
        viewModelScope.launch(Dispatchers.IO) {
            optionsRepository.saveOptions(packageName, options)
        }
    }

    /**
     * Extract recommended versions from bundle info
     */
    private fun extractRecommendedVersions(bundleInfo: Map<Int, Any>): Map<String, String> {
        return bundleInfo[0]?.let { apiBundleInfo ->
            val info = apiBundleInfo as? PatchBundleInfo
            info?.let {
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
                        .orEmpty(),
                    PACKAGE_REDDIT to it.patches
                        .filter { patch ->
                            patch.compatiblePackages?.any { pkg -> pkg.packageName == PACKAGE_REDDIT } == true
                        }
                        .flatMap { patch ->
                            patch.compatiblePackages
                                ?.firstOrNull { pkg -> pkg.packageName == PACKAGE_REDDIT }
                                ?.versions
                                ?: emptyList()
                        }
                        .maxByOrNull { it }
                        .orEmpty()
                ).filterValues { it.isNotEmpty() }
            } ?: emptyMap()
        } ?: emptyMap()
    }

    /**
     * Load local APK and extract package info
     * Supports both single APK and split APK archives (apkm, apks, xapk)
     */
    private suspend fun loadLocalApk(
        context: Context,
        uri: Uri
    ): SelectedApp.Local? = withContext(Dispatchers.IO) {
        try {
            // Copy file to cache with original extension detection
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "temp_${System.currentTimeMillis()}"

            val extension = fileName.substringAfterLast('.', "apk").lowercase()
            val tempFile = File(context.cacheDir, "temp_apk_${System.currentTimeMillis()}.$extension")

            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Check if it's a split APK archive
            val isSplitArchive = SplitApkPreparer.isSplitArchive(tempFile)

            val packageInfo = if (isSplitArchive) {
                // Extract base APK from archive and get package info
                extractPackageInfoFromSplitArchive(context, tempFile)
            } else {
                // Regular APK - parse directly
                context.packageManager.getPackageArchiveInfo(
                    tempFile.absolutePath,
                    PackageManager.GET_META_DATA
                )
            }

            if (packageInfo == null) {
                tempFile.delete()
                return@withContext null
            }

            SelectedApp.Local(
                packageName = packageInfo.packageName,
                version = packageInfo.versionName ?: "unknown",
                file = tempFile,
                temporary = true
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to load APK", e)
            null
        }
    }

    /**
     * Extract package info from split APK archive (apkm, apks, xapk)
     */
    private fun extractPackageInfoFromSplitArchive(
        context: Context,
        archiveFile: File
    ): PackageInfo? {
        return try {
            ZipInputStream(archiveFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    // Look for base APK (usually named base.apk, or the main APK without split suffix)
                    if (name.endsWith(".apk") &&
                        (name.contains("base") || !name.contains("split") && !name.contains("config"))) {

                        // Extract base APK to temp file
                        val tempBaseApk = File(context.cacheDir, "temp_base_${System.currentTimeMillis()}.apk")
                        tempBaseApk.outputStream().use { output ->
                            zip.copyTo(output)
                        }

                        val packageInfo = context.packageManager.getPackageArchiveInfo(
                            tempBaseApk.absolutePath,
                            PackageManager.GET_META_DATA
                        )

                        tempBaseApk.delete()

                        if (packageInfo != null) {
                            return packageInfo
                        }
                    }
                    entry = zip.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to extract package info from split archive", e)
            null
        }
    }
}
