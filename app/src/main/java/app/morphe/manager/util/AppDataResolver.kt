/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Data source priority for app information.
 */
enum class AppDataSource {
    INSTALLED,        // Installed app via PackageManager
    ORIGINAL_APK,     // Saved original APK file
    PATCHED_APK,      // Saved patched APK file
    BUNDLE_METADATA,  // Display name declared in the patch bundle (BundleAppMetadata)
    CONSTANTS         // Fallback to hardcoded constants
}

/**
 * Resolved app data from any available source.
 */
data class ResolvedAppData(
    val packageName: String,
    val displayName: String,
    val version: String?,
    val icon: Drawable?,
    val packageInfo: PackageInfo?,
    val source: AppDataSource
)

/**
 * Universal app data resolver that checks multiple sources in priority order:
 * 1. Installed app (via PackageManager)
 * 2. Original APK (from OriginalApkRepository)
 * 3. Patched APK (from InstalledAppRepository)
 * 4. Constants (hardcoded app names)
 */
class AppDataResolver(
    context: Context,
    private val pm: PM,
    private val originalApkRepository: OriginalApkRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val filesystem: Filesystem,
    private val patchBundleRepository: PatchBundleRepository
) {
    private val packageManager: PackageManager = context.packageManager

    // In-memory cache - keyed by packageName + preferredSource.
    // Avoids redundant IO when multiple composables resolve the same package simultaneously.
    // Entries are never evicted: the resolver is a singleton and package data rarely changes
    // during a single session.
    private val cache = ConcurrentHashMap<Pair<String, AppDataSource>, ResolvedAppData>()

    /**
     * Invalidate cached data for a specific package.
     * Call this after installation, uninstallation, or any state change
     * that affects what source the package data comes from.
     */
    fun invalidate(packageName: String) {
        cache.keys.removeAll { it.first == packageName }
    }

    /**
     * Resolve app data from any available source.
     *
     * Display name and icon are resolved **independently**:
     * - Icon/packageInfo: best available APK source ordered by [preferredSource]
     * - Name: [AppDataSource.BUNDLE_METADATA] always wins when available, because patched APK
     *   labels may contain internal class names instead of the real product name.
     *   Falls back to APK label → constants.
     *
     * @param packageName Package name to resolve
     * @param preferredSource Preferred data source for icon/packageInfo (will still fallback)
     * @return [ResolvedAppData] with the best available name and icon, potentially from
     *   different sources
     */
    suspend fun resolveAppData(
        packageName: String,
        preferredSource: AppDataSource = AppDataSource.INSTALLED
    ): ResolvedAppData = withContext(Dispatchers.IO) {
        cache[packageName to preferredSource]?.let { return@withContext it }

        // APK sources ordered by preference - provide icon, packageInfo and raw label
        val apkSources = when (preferredSource) {
            AppDataSource.ORIGINAL_APK -> listOf(
                AppDataSource.ORIGINAL_APK,
                AppDataSource.INSTALLED,
                AppDataSource.PATCHED_APK,
            )
            AppDataSource.PATCHED_APK -> listOf(
                AppDataSource.PATCHED_APK,
                AppDataSource.ORIGINAL_APK,
                AppDataSource.INSTALLED,
            )
            else -> listOf(
                AppDataSource.INSTALLED,
                AppDataSource.ORIGINAL_APK,
                AppDataSource.PATCHED_APK,
            )
        }

        // Phase 1: find the best available icon + packageInfo from APK sources
        val apkResult = apkSources.firstNotNullOfOrNull { source ->
            when (source) {
                AppDataSource.INSTALLED -> tryGetFromInstalled(packageName)
                AppDataSource.ORIGINAL_APK -> tryGetFromOriginalApk(packageName)
                AppDataSource.PATCHED_APK -> tryGetFromPatchedApk(packageName)
                else -> null
            }
        }

        // Phase 2: display name - bundle metadata always wins when available
        val bundleName = tryGetFromBundleMetadata(packageName)?.displayName
        val displayName = bundleName
            ?: apkResult?.displayName
            ?: getFromConstants(packageName).displayName

        ResolvedAppData(
            packageName = packageName,
            displayName = displayName,
            version = apkResult?.version,
            icon = apkResult?.icon,
            packageInfo = apkResult?.packageInfo,
            source = when {
                bundleName != null -> AppDataSource.BUNDLE_METADATA
                apkResult != null -> apkResult.source
                else -> AppDataSource.CONSTANTS
            }
        ).also { cache[packageName to preferredSource] = it }
    }

    /**
     * Try to get app data from installed app.
     */
    private fun tryGetFromInstalled(packageName: String): ResolvedAppData? {
        return try {
            val packageInfo = pm.getPackageInfo(packageName, 0) ?: return null
            val appInfo = packageInfo.applicationInfo ?: return null

            // Skip disabled apps - they should not take priority over saved APKs
            if (!appInfo.enabled) return null

            ResolvedAppData(
                packageName = packageName,
                displayName = appInfo.loadLabel(packageManager).toString(),
                version = packageInfo.versionName,
                icon = appInfo.loadIcon(packageManager),
                packageInfo = packageInfo,
                source = AppDataSource.INSTALLED
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try to get app data from saved original APK.
     */
    private suspend fun tryGetFromOriginalApk(packageName: String): ResolvedAppData? {
        return try {
            val originalApk = originalApkRepository.get(packageName) ?: return null
            val file = File(originalApk.filePath)
            if (!file.exists()) return null

            val packageInfo = packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_META_DATA
            ) ?: return null

            // Set source paths so we can load icon
            packageInfo.applicationInfo?.apply {
                sourceDir = file.absolutePath
                publicSourceDir = file.absolutePath
            }

            val appInfo = packageInfo.applicationInfo
            ResolvedAppData(
                packageName = packageName,
                displayName = appInfo?.loadLabel(packageManager)?.toString()
                    ?: packageName,
                version = originalApk.version,
                icon = appInfo?.loadIcon(packageManager),
                packageInfo = packageInfo,
                source = AppDataSource.ORIGINAL_APK
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try to get app data from saved patched APK.
     * Searches both by direct package name match and by originalPackageName
     * to handle cases where the search uses original package but app is patched with different name.
     */
    private suspend fun tryGetFromPatchedApk(packageName: String): ResolvedAppData? {
        return try {
            // Try to find installed app record by package name
            // First try direct lookup (packageName might be currentPackageName)
            var installedApp = installedAppRepository.get(packageName)

            // If not found, search all installed apps to find one with matching originalPackageName
            // This handles case where packageName is the original package but app is patched with different name
            if (installedApp == null) {
                val allApps = installedAppRepository.getAll().first()
                installedApp = allApps.firstOrNull { it.originalPackageName == packageName }
            }

            if (installedApp == null) return null

            // Get saved APK file from filesystem - try both current and original package names
            val savedFile = listOf(
                filesystem.getPatchedAppFile(installedApp.currentPackageName, installedApp.version),
                filesystem.getPatchedAppFile(installedApp.originalPackageName, installedApp.version),
                // Also try with the search packageName in case it differs
                filesystem.getPatchedAppFile(packageName, installedApp.version)
            ).distinct().firstOrNull { it.exists() } ?: return null

            val packageInfo = packageManager.getPackageArchiveInfo(
                savedFile.absolutePath,
                PackageManager.GET_META_DATA
            ) ?: return null

            // Set source paths so we can load icon
            packageInfo.applicationInfo?.apply {
                sourceDir = savedFile.absolutePath
                publicSourceDir = savedFile.absolutePath
            }

            val appInfo = packageInfo.applicationInfo
            ResolvedAppData(
                packageName = packageName,
                displayName = appInfo?.loadLabel(packageManager)?.toString()
                    ?: packageName,
                version = installedApp.version,
                icon = appInfo?.loadIcon(packageManager),
                packageInfo = packageInfo,
                source = AppDataSource.PATCHED_APK
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try to get app display name from patch bundle metadata.
     * Uses [PatchBundleRepository.appMetadata] snapshot, no allocations.
     * Returns null if bundles are not yet loaded or package isn't in any bundle.
     */
    private fun tryGetFromBundleMetadata(packageName: String): ResolvedAppData? {
        val displayName = patchBundleRepository.appMetadata.value[packageName]?.displayName
            ?: return null
        return ResolvedAppData(
            packageName = packageName,
            displayName = displayName,
            version = null,
            icon = null,
            packageInfo = null,
            source = AppDataSource.BUNDLE_METADATA
        )
    }

    /**
     * Get app data from hardcoded constants.
     */
    private fun getFromConstants(packageName: String): ResolvedAppData {
        return ResolvedAppData(
            packageName = packageName,
            displayName = KnownApps.getAppName(packageName),
            version = null,
            icon = null,
            packageInfo = null,
            source = AppDataSource.CONSTANTS
        )
    }
}
