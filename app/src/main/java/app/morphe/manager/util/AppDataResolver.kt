/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Optional
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

    // Per-source lookups keyed by the source they came from rather than the caller's preference.
    // The same APK is otherwise re-read once per preferredSource, and every archive read costs a
    // full PackageManager parse that leaks an ApkAssets object until the finalizer runs.
    private val sourceCache = ConcurrentHashMap<Pair<String, AppDataSource>, Optional<ResolvedAppData>>()

    /**
     * Invalidate cached data for a specific package.
     * Call this after installation, uninstallation, or any state change
     * that affects what source the package data comes from.
     */
    fun invalidate(packageName: String) {
        cache.keys.removeAll { it.first == packageName }
        sourceCache.keys.removeAll { it.first == packageName }
    }

    /** Invalidate all cached data. Call this when a global refresh is needed. */
    fun invalidateAll() {
        cache.clear()
        sourceCache.clear()
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
        val apkResult = apkSources.firstNotNullOfOrNull { source -> resolveFromSource(packageName, source) }

        // Phase 2: display name
        // apkResult already reflects the preferred source order (PATCHED_APK → ORIGINAL_APK → INSTALLED),
        // so its label is the best available. Bundle metadata is a fallback for when no APK is found
        val bundleName = tryGetFromBundleMetadata(packageName)?.displayName
        val displayName = apkResult?.displayName
            ?: bundleName
            ?: getFromConstants(packageName).displayName

        ResolvedAppData(
            packageName = packageName,
            displayName = displayName,
            version = apkResult?.version,
            icon = apkResult?.icon,
            packageInfo = apkResult?.packageInfo,
            source = apkResult?.source ?: if (bundleName != null) AppDataSource.BUNDLE_METADATA else AppDataSource.CONSTANTS
        ).also { cache[packageName to preferredSource] = it }
    }

    /**
     * Reads one source, reusing the previous answer for that exact source. A miss is remembered
     * too, so a package without a saved APK does not reparse on every lookup.
     */
    private suspend fun resolveFromSource(
        packageName: String,
        source: AppDataSource
    ): ResolvedAppData? = sourceCache.getOrPut(packageName to source) {
        Optional.ofNullable(
            when (source) {
                AppDataSource.INSTALLED -> tryGetFromInstalled(packageName)
                AppDataSource.ORIGINAL_APK -> tryGetFromOriginalApk(packageName)
                AppDataSource.PATCHED_APK -> tryGetFromPatchedApk(packageName)
                else -> null
            }
        )
    }.orElse(null)

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
            val file = File(originalApk.filePath).takeIf { it.exists() } ?: return null

            readApkArchive(packageName, file, originalApk.version, AppDataSource.ORIGINAL_APK)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try to get app data from saved patched APK.
     *
     * The record is the one that answers to [packageName], falling back to the app's only install
     * when the name is the app's own and patching renamed that install. An app with several
     * installs has no such fallback: any of them could be the one meant, and describing the app
     * as whichever came first would attribute one clone's build to another.
     */
    private suspend fun tryGetFromPatchedApk(packageName: String): ResolvedAppData? {
        return try {
            val installedApp = installedAppRepository.get(packageName)
                ?: installedAppRepository.getAll().first()
                    .singleOrNull { it.originalPackageName == packageName }
                ?: return null

            // Get saved APK file from filesystem - try both current and original package names
            val savedFile = listOf(
                filesystem.getPatchedAppFile(installedApp.currentPackageName, installedApp.version),
                filesystem.getPatchedAppFile(installedApp.originalPackageName, installedApp.version)
            ).distinct().firstOrNull { it.exists() } ?: return null

            readApkArchive(packageName, savedFile, installedApp.version, AppDataSource.PATCHED_APK)
        } catch (_: Exception) {
            null
        }
    }

    /** Reads an APK on disk as an app data source, or null when it cannot be parsed. */
    private fun readApkArchive(
        packageName: String,
        file: File,
        version: String?,
        source: AppDataSource
    ): ResolvedAppData? {
        val packageInfo = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_META_DATA
        ) ?: return null

        // Set source paths so we can load icon
        val appInfo = packageInfo.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }

        return ResolvedAppData(
            packageName = packageName,
            displayName = appInfo?.loadLabel(packageManager)?.toString() ?: packageName,
            version = version,
            icon = appInfo?.let(::archiveIcon),
            packageInfo = packageInfo,
            source = source
        )
    }

    /**
     * Icon read from the archive's own resources: the cache behind [ApplicationInfo.loadIcon] is
     * keyed by package name and icon resource id alone, so a saved APK would otherwise serve its
     * icon to the installed app of the same name for the rest of the process, and the other way
     * round. Falls back the way [ApplicationInfo.loadIcon] does.
     */
    private fun archiveIcon(appInfo: ApplicationInfo): Drawable =
        runCatching {
            val resources = packageManager.getResourcesForApplication(appInfo)
            appInfo.icon.takeIf { it != 0 }?.let { ResourcesCompat.getDrawable(resources, it, null) }
        }.getOrNull() ?: packageManager.defaultActivityIcon

    /**
     * Try to get app display name from patch bundle metadata.
     * Uses [PatchBundleRepository.appMetadata] snapshot, no allocations.
     * Returns null if bundles are not yet loaded or package isn't in any bundle.
     */
    private fun tryGetFromBundleMetadata(packageName: String): ResolvedAppData? {
        // Disabled bundles are still consulted, because a name is worth more than the package of
        // an app whose source the user has since turned off
        val displayName = patchBundleRepository.appMetadata.value[packageName]?.displayName
            ?: patchBundleRepository.allAppMetadata.value[packageName]?.displayName
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
