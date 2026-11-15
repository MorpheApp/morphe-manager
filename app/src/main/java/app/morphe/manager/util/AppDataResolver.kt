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
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.original.OriginalApk
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
 *
 * @param loadIcon Decodes the icon on first read of [icon], so unseen rows are not decoded
 */
data class ResolvedAppData(
    val packageName: String,
    val displayName: String,
    val version: String?,
    val packageInfo: PackageInfo?,
    val source: AppDataSource,
    private val loadIcon: () -> Drawable? = { null }
) {
    val icon: Drawable? by lazy(loadIcon)
}

/**
 * Tracked installs and saved original APKs, read once per lookup or once per batch.
 */
class ResolverRecords(installed: List<InstalledApp>, originals: List<OriginalApk>) {
    private val installedByPackage = installed.associateBy { it.currentPackageName }
    private val installedByApp = installed.groupBy { it.originalPackageName }
    private val originalsByPackage = originals.associateBy { it.packageName }

    fun original(packageName: String): OriginalApk? = originalsByPackage[packageName]

    /**
     * The record for [packageName], else the app's only install when patching renamed it. With
     * several installs there is no fallback, since any of the clones could be meant.
     */
    fun installed(packageName: String): InstalledApp? =
        installedByPackage[packageName] ?: installedByApp[packageName]?.singleOrNull()
}

/**
 * Universal app data resolver that checks multiple sources in priority order:
 * 1. Installed app (via PackageManager)
 * 2. Original APK (from OriginalApkRepository)
 * 3. Patched APK (from InstalledAppRepository)
 * 4. Constants (hardcoded app names)
 *
 * With none of 1-3 at hand, the icon alone may still come from a disabled install or from the
 * patched install under the name patching gave it.
 */
class AppDataResolver(
    context: Context,
    private val pm: PM,
    private val originalApkRepository: OriginalApkRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val filesystem: Filesystem,
    private val patchBundleRepository: PatchBundleRepository,
    scope: AppCoroutineScope
) {
    private val packageManager: PackageManager = context.packageManager
    private val resources = context.resources

    // In-memory cache - keyed by packageName + preferredSource.
    // Avoids redundant IO when multiple composables resolve the same package simultaneously.
    // Entries live until the package is invalidated.
    private val cache = ConcurrentHashMap<Pair<String, AppDataSource>, ResolvedAppData>()

    // Per-source lookups keyed by the source they came from rather than the caller's preference.
    // The same APK is otherwise re-read once per preferredSource, and every archive read costs a
    // full PackageManager parse that leaks an ApkAssets object until the finalizer runs.
    private val sourceCache = ConcurrentHashMap<Pair<String, AppDataSource>, Optional<ResolvedAppData>>()

    // Bumped by every invalidation, so a lookup that started before one does not cache what it read
    private val generations = ConcurrentHashMap<String, Long>()
    @Volatile
    private var globalGeneration = 0L

    private fun generationOf(packageName: String) = globalGeneration to (generations[packageName] ?: 0L)

    /** Stores [value] under [key] unless [packageName] was invalidated since [generation] was read. */
    private fun <V : Any> ConcurrentHashMap<Pair<String, AppDataSource>, V>.putUnlessInvalidated(
        key: Pair<String, AppDataSource>,
        value: V,
        packageName: String,
        generation: Pair<Long, Long>
    ) {
        put(key, value)
        if (generationOf(packageName) != generation) remove(key, value)
    }

    init {
        // An original is kept partway through patching, after its app may already have been looked
        // up without one, so an app is looked up again whenever its kept original comes or goes
        scope.launch {
            var kept: Map<String, Pair<String, Long>>? = null
            originalApkRepository.getAll()
                .map { apks -> apks.associate { it.packageName to (it.filePath to it.fileSize) } }
                .distinctUntilChanged()
                .collect { current ->
                    kept?.let { previous ->
                        (previous.keys + current.keys)
                            .filter { previous[it] != current[it] }
                            .forEach(::invalidate)
                    }
                    kept = current
                }
        }
    }

    /**
     * Invalidate cached data for a specific package.
     * Call this after installation, uninstallation, or any state change
     * that affects what source the package data comes from.
     */
    fun invalidate(packageName: String) {
        generations.merge(packageName, 1L, Long::plus)
        cache.keys.removeAll { it.first == packageName }
        sourceCache.keys.removeAll { it.first == packageName }
    }

    /** Invalidate all cached data. Call this when a global refresh is needed. */
    fun invalidateAll() {
        globalGeneration++
        cache.clear()
        sourceCache.clear()
    }

    /**
     * Resolve app data from any available source.
     *
     * Display name and icon are resolved **independently**:
     * - Icon/packageInfo: best available APK source ordered by [preferredSource]
     * - Name: the label of that APK, cleaned of the class names a patched one may carry, then
     *   [AppDataSource.BUNDLE_METADATA], then constants.
     *
     * @param packageName Package name to resolve
     * @param preferredSource Preferred data source for icon/packageInfo (will still fallback)
     * @param records Preloaded records for batch callers, read from the database otherwise
     * @return [ResolvedAppData] with the best available name and icon, potentially from
     *   different sources
     */
    suspend fun resolveAppData(
        packageName: String,
        preferredSource: AppDataSource = AppDataSource.INSTALLED,
        records: ResolverRecords? = null
    ): ResolvedAppData = withContext(Dispatchers.IO) {
        cache[packageName to preferredSource]?.let { return@withContext it }
        val generation = generationOf(packageName)

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
        // Read only once a saved APK is looked for, and then once for the whole lookup
        var lookupRecords = records
        val apkResult = apkSources.firstNotNullOfOrNull { source ->
            resolveFromSource(packageName, source) {
                lookupRecords ?: ResolverRecords(
                    installedAppRepository.getAll().first(),
                    originalApkRepository.getAll().first()
                ).also { lookupRecords = it }
            }
        }

        // Phase 1b: an install the sources above pass over still shows the right icon. Only the
        // icon is taken, since neither a disabled app nor a renamed clone is the app asked about
        val fallbackIcon = if (apkResult == null) {
            installedIconFallback(
                packageName,
                lookupRecords ?: ResolverRecords(
                    installedAppRepository.getAll().first(),
                    originalApkRepository.getAll().first()
                )
            )
        } else null

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
            packageInfo = apkResult?.packageInfo,
            source = apkResult?.source
                ?: if (bundleName != null) AppDataSource.BUNDLE_METADATA else AppDataSource.CONSTANTS,
            loadIcon = { apkResult?.icon ?: fallbackIcon?.loadIcon(packageManager) }
        ).also {
            // Bundle metadata may not have loaded yet, so a constants name is not worth keeping
            if (it.source != AppDataSource.CONSTANTS) {
                cache.putUnlessInvalidated(packageName to preferredSource, it, packageName, generation)
            }
        }
    }

    /**
     * Package manager data for [packageName], or null when not installed. Saved APKs are not read,
     * so a caller describing many packages is not held up by archive parsing.
     */
    suspend fun resolveInstalled(packageName: String): ResolvedAppData? =
        withContext(Dispatchers.IO) {
            cachedSource(packageName, AppDataSource.INSTALLED) { tryGetFromInstalled(packageName) }
        }

    /** Reads one source, see [cachedSource]. */
    private suspend fun resolveFromSource(
        packageName: String,
        source: AppDataSource,
        records: suspend () -> ResolverRecords
    ): ResolvedAppData? = cachedSource(packageName, source) {
        when (source) {
            AppDataSource.INSTALLED -> tryGetFromInstalled(packageName)
            AppDataSource.ORIGINAL_APK -> tryGetFromOriginalApk(packageName, records())
            AppDataSource.PATCHED_APK -> tryGetFromPatchedApk(packageName, records())
            else -> null
        }
    }

    /**
     * [read] once per package and source. Misses are cached too, so a missing APK is not reparsed.
     */
    private inline fun cachedSource(
        packageName: String,
        source: AppDataSource,
        read: () -> ResolvedAppData?
    ): ResolvedAppData? {
        val key = packageName to source
        sourceCache[key]?.let { return it.orElse(null) }
        val generation = generationOf(packageName)
        val value = Optional.ofNullable(read())
        sourceCache.putUnlessInvalidated(key, value, packageName, generation)
        return value.orElse(null)
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
                displayName = cleanPackageLabel(appInfo.loadLabel(packageManager).toString(), packageName),
                version = packageInfo.versionName,
                packageInfo = packageInfo,
                source = AppDataSource.INSTALLED,
                loadIcon = { appInfo.loadIcon(packageManager) }
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The installed app whose icon stands in when no source has [packageName]: the app itself
     * while disabled, else the patched install tracked under a name of its own.
     */
    private fun installedIconFallback(packageName: String, records: ResolverRecords): ApplicationInfo? =
        listOfNotNull(packageName, records.installed(packageName)?.currentPackageName)
            .distinct()
            .firstNotNullOfOrNull { name ->
                runCatching { pm.getPackageInfo(name, 0)?.applicationInfo }.getOrNull()
            }

    /**
     * Try to get app data from saved original APK.
     */
    private fun tryGetFromOriginalApk(packageName: String, records: ResolverRecords): ResolvedAppData? {
        return try {
            val originalApk = records.original(packageName) ?: return null
            val file = File(originalApk.filePath).takeIf { it.exists() } ?: return null

            readApkArchive(packageName, file, originalApk.version, AppDataSource.ORIGINAL_APK)
        } catch (_: Exception) {
            null
        }
    }

    /** Try to get app data from saved patched APK, see [ResolverRecords.installed]. */
    private fun tryGetFromPatchedApk(packageName: String, records: ResolverRecords): ResolvedAppData? {
        return try {
            val installedApp = records.installed(packageName) ?: return null

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
        val packageInfo = pm.getPackageInfo(file) ?: return null
        val appInfo = packageInfo.applicationInfo

        return ResolvedAppData(
            packageName = packageName,
            displayName = appInfo?.loadLabel(packageManager)?.toString()
                ?.let { cleanPackageLabel(it, packageName) } ?: packageName,
            version = version,
            packageInfo = packageInfo,
            source = source,
            loadIcon = { appInfo?.let(::archiveIcon) }
        )
    }

    /**
     * Icon read from the archive's own resources: the cache behind [ApplicationInfo.loadIcon] is
     * keyed by package name and icon resource id alone, so a saved APK would otherwise serve its
     * icon to the installed app of the same name for the rest of the process, and the other way
     * round. Falls back the way [ApplicationInfo.loadIcon] does.
     */
    private fun archiveIcon(appInfo: ApplicationInfo): Drawable =
        archiveIconOrNull(appInfo) ?: packageManager.defaultActivityIcon

    private fun archiveIconOrNull(appInfo: ApplicationInfo): Drawable? =
        runCatching {
            val resources = packageManager.getResourcesForApplication(appInfo)
            appInfo.icon.takeIf { it != 0 }?.let { ResourcesCompat.getDrawable(resources, it, null) }
        }.getOrNull()

    /** Icon of a parsed archive drawn into a bitmap, so it outlives the file. Null where it has none. */
    fun detachedArchiveIcon(packageInfo: PackageInfo): Drawable? =
        packageInfo.applicationInfo
            ?.let(::archiveIconOrNull)
            ?.let { icon -> runCatching { icon.toBitmap().toDrawable(resources) }.getOrNull() }

    /**
     * Try to get app display name from patch bundle metadata, see
     * [PatchBundleRepository.appDisplayNames]. Returns null if bundles are not yet loaded or
     * package isn't in any bundle.
     */
    private fun tryGetFromBundleMetadata(packageName: String): ResolvedAppData? {
        val displayName = patchBundleRepository.appDisplayNames.value[packageName] ?: return null
        return ResolvedAppData(
            packageName = packageName,
            displayName = displayName,
            version = null,
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
            packageInfo = null,
            source = AppDataSource.CONSTANTS
        )
    }
}
