package app.morphe.manager.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Data source priority for app information
 */
enum class AppDataSource {
    INSTALLED,     // Installed app via PackageManager
    ORIGINAL_APK,  // Saved original APK file
    PATCHED_APK,   // Saved patched APK file
    CONSTANTS      // Fallback to hardcoded constants
}

/**
 * Resolved app data from any available source
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
    private val context: Context,
    private val pm: PM,
    private val originalApkRepository: OriginalApkRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val filesystem: Filesystem
) {
    private val packageManager: PackageManager = context.packageManager

    /**
     * Resolve app data from any available source
     * @param packageName Package name to resolve
     * @param preferredSource Preferred data source (will still fallback if unavailable)
     * @return ResolvedAppData with information from the best available source
     */
    suspend fun resolveAppData(
        packageName: String,
        preferredSource: AppDataSource = AppDataSource.INSTALLED
    ): ResolvedAppData = withContext(Dispatchers.IO) {
        // Define source check order based on preference
        val sourceOrder = when (preferredSource) {
            AppDataSource.INSTALLED -> listOf(
                AppDataSource.INSTALLED,
                AppDataSource.ORIGINAL_APK,
                AppDataSource.PATCHED_APK,
                AppDataSource.CONSTANTS
            )
            AppDataSource.ORIGINAL_APK -> listOf(
                AppDataSource.ORIGINAL_APK,
                AppDataSource.INSTALLED,
                AppDataSource.PATCHED_APK,
                AppDataSource.CONSTANTS
            )
            AppDataSource.PATCHED_APK -> listOf(
                AppDataSource.PATCHED_APK,
                AppDataSource.ORIGINAL_APK,
                AppDataSource.INSTALLED,
                AppDataSource.CONSTANTS
            )
            AppDataSource.CONSTANTS -> listOf(AppDataSource.CONSTANTS)
        }

        // Try each source in order until we get data
        for (source in sourceOrder) {
            val result = when (source) {
                AppDataSource.INSTALLED -> tryGetFromInstalled(packageName)
                AppDataSource.ORIGINAL_APK -> tryGetFromOriginalApk(packageName)
                AppDataSource.PATCHED_APK -> tryGetFromPatchedApk(packageName)
                AppDataSource.CONSTANTS -> getFromConstants(packageName)
            }
            if (result != null) return@withContext result
        }

        // Ultimate fallback - return package name as display name
        ResolvedAppData(
            packageName = packageName,
            displayName = packageName,
            version = null,
            icon = null,
            packageInfo = null,
            source = AppDataSource.CONSTANTS
        )
    }

    /**
     * Try to get app data from installed app
     */
    private fun tryGetFromInstalled(packageName: String): ResolvedAppData? {
        return try {
            val packageInfo = pm.getPackageInfo(packageName, 0) ?: return null
            val appInfo = packageInfo.applicationInfo ?: return null

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
     * Try to get app data from saved original APK
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
     * Try to get app data from saved patched APK
     * Searches both by direct package name match and by originalPackageName
     * to handle cases where the search uses original package but app is patched with different name
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
     * Get app data from hardcoded constants
     */
    private fun getFromConstants(packageName: String): ResolvedAppData {
        return ResolvedAppData(
            packageName = packageName,
            displayName = AppPackages.getAppName(context, packageName),
            version = null,
            icon = null,
            packageInfo = null,
            source = AppDataSource.CONSTANTS
        )
    }
}
