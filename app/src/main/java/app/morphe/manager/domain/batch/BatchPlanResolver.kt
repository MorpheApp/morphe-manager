/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.batch

import android.content.pm.PackageInfo
import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.bundles.AppVersionCatalog
import app.morphe.manager.domain.bundles.AppVersionHints
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.morphe.manager.patcher.patch.SELECTION_APK_ARCHITECTURE
import app.morphe.manager.patcher.patch.installerTypeFor
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.Options
import app.morphe.manager.util.PM
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability
import app.morphe.manager.util.PatchSelectionUtils.filterGmsCore
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "Morphe BatchPlanResolver"

/**
 * Turns a list of package names into a runnable batch plan.
 *
 * Every decision the interactive flow would raise a dialog for is resolved here into an item
 * state instead: a missing APK becomes [BatchItemState.NEEDS_APK], an unsupported version
 * becomes [BatchItemState.VERSION_MISMATCH]. The queue itself then runs without prompts.
 */
class BatchPlanResolver(
    private val patchBundleRepository: PatchBundleRepository,
    private val originalApkRepository: OriginalApkRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val patchOptionsRepository: PatchOptionsRepository,
    private val patchOptionsPrefs: PatchOptionsPreferencesManager,
    private val prefs: PreferencesManager,
    private val fs: Filesystem,
    private val appDataResolver: AppDataResolver,
    private val versionCatalog: AppVersionCatalog,
    private val pm: PM
) {
    /**
     * Resolves every package in parallel and returns the items in the requested order.
     *
     * @param useMount Picks the install target every selection is resolved against, so patches
     *   that declare themselves unavailable for it are dropped just like the single-app flow does.
     */
    suspend fun resolve(
        packageNames: List<String>,
        useMount: Boolean
    ): List<BatchPatchItem> = coroutineScope {
        // Built once for the whole plan: it is derived from every patch of every source, and
        // resolving it per app would repeat that work for each one of them
        val hints = versionCatalog.hints()
        packageNames
            .distinct()
            .map { packageName -> async { resolve(packageName, useMount, hints = hints[packageName]) } }
            .awaitAll()
    }

    /**
     * Resolves a single package. [attachedFile] overrides source discovery and is used when
     * the user attaches an APK from the preflight screen.
     */
    suspend fun resolve(
        packageName: String,
        useMount: Boolean,
        attachedFile: File? = null,
        hints: AppVersionHints? = null,
        allowIncompatible: Boolean = false,
        preferInstalled: Boolean = false
    ): BatchPatchItem = withContext(Dispatchers.IO) {
        val appName = resolveAppName(packageName)
        val versions = hints ?: versionCatalog.hints(packageName)
        val suggested = versions?.recommendedVersion

        val source = try {
            attachedFile?.let { readAttachedFile(it) } ?: findSource(packageName, preferInstalled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve APK source for $packageName", e)
            null
        }

        if (source == null) {
            return@withContext BatchPatchItem(
                packageName = packageName,
                appName = appName,
                source = null,
                selection = emptyMap(),
                options = emptyMap(),
                bundles = emptyList(),
                suggestedVersion = suggested,
                state = BatchItemState.NEEDS_APK
            )
        }

        if (attachedFile != null) {
            val actualPackage = readAttachedPackageName(attachedFile)
            if (actualPackage != null && actualPackage != packageName) {
                return@withContext BatchPatchItem(
                    packageName = packageName,
                    appName = appName,
                    source = null,
                    selection = emptyMap(),
                    options = emptyMap(),
                    bundles = emptyList(),
                    suggestedVersion = suggested,
                    state = BatchItemState.NEEDS_APK,
                    message = actualPackage
                )
            }
        }

        buildItem(
            packageName = packageName,
            appName = appName,
            source = source,
            useMount = useMount,
            suggested = suggested,
            experimental = source.version in versions?.experimentalVersions.orEmpty(),
            forceIncompatible = allowIncompatible
        )
    }

    /**
     * Packages whose patches have moved on since they were last built: any bundle an app was
     * patched with now reports a different version than the one recorded at patch time.
     *
     * Shared by the automatic schedule and the launcher shortcut, both of which need the same
     * answer to the question "what is worth re-patching right now".
     */
    suspend fun findOutdatedPackages(): List<String> = withContext(Dispatchers.IO) {
        // Scoped to the sources planning will actually use. A disabled or blocked source moving
        // on is not a reason to re-patch, and the plan would only report No patches anyway
        val currentVersions = patchBundleRepository.enabledBundlesInfoFlow.first()
            .mapValues { (_, info) -> info.version }
        if (currentVersions.isEmpty()) return@withContext emptyList()

        installedAppRepository.getAll().first()
            .filter { installed ->
                val storedVersions = installedAppRepository
                    .getBundleVersionsForApp(installed.currentPackageName)
                storedVersions.any { (uid, storedVersion) ->
                    val currentVersion = currentVersions[uid] ?: return@any false
                    storedVersion != null && storedVersion != currentVersion
                }
            }
            .map { it.originalPackageName }
            .distinct()
    }

    /**
     * Rebuilds an existing item against a newly attached APK, keeping the user's
     * force-version choice.
     */
    suspend fun reattach(item: BatchPatchItem, file: File, useMount: Boolean): BatchPatchItem =
        resolve(
            packageName = item.packageName,
            useMount = useMount,
            attachedFile = file,
            // A forced item stays runnable after swapping its APK, so the user does not have
            // to confirm the same version warning twice, and keeps the patches that came with it
            allowIncompatible = item.forceVersionMismatch
        ).copy(forceVersionMismatch = item.forceVersionMismatch)

    /**
     * Re-resolves an app against the APK the user picked in the availability dialog. Only the
     * order of preference changes: the file itself is still discovered the usual way.
     */
    suspend fun useSource(item: BatchPatchItem, useMount: Boolean, preferInstalled: Boolean): BatchPatchItem =
        resolve(
            packageName = item.packageName,
            useMount = useMount,
            allowIncompatible = item.forceVersionMismatch,
            preferInstalled = preferInstalled
        ).copy(forceVersionMismatch = item.forceVersionMismatch)

    /**
     * Re-resolves an app the user accepted an unsupported version for, this time keeping the
     * patches that declare a different version.
     */
    suspend fun forceVersion(item: BatchPatchItem, useMount: Boolean): BatchPatchItem =
        resolve(
            packageName = item.packageName,
            useMount = useMount,
            attachedFile = (item.source as? BatchApkSource.UserFile)?.file,
            allowIncompatible = true
        ).copy(forceVersionMismatch = true)

    private suspend fun buildItem(
        packageName: String,
        appName: String,
        source: BatchApkSource,
        useMount: Boolean,
        suggested: String?,
        experimental: Boolean,
        forceIncompatible: Boolean
    ): BatchPatchItem {
        val bundles = patchBundleRepository
            .scopedBundleInfoFlow(packageName, source.version, source.versionCode)
            .first()
            .filter { it.enabled }

        // Forced per app from the preflight screen, or globally by the compatibility setting
        val allowIncompatible = forceIncompatible || prefs.disablePatchVersionCompatCheck.get()
        val hasCompatible = bundles.any { it.compatible.isNotEmpty() }
        val hasIncompatible = bundles.any { it.incompatible.isNotEmpty() }
        val hasUniversal = bundles.any { it.universal.isNotEmpty() }

        val versionMismatch = !hasCompatible && hasIncompatible && !allowIncompatible

        /**
         * Nothing to run with. Which of the two reasons it is matters: an app whose patches
         * simply declare other versions still has a way forward, and calling that "no patches"
         * would hide both the reason and the buttons that fix it.
         */
        fun blocked(contributing: List<PatchBundleInfo.Scoped> = emptyList()) = BatchPatchItem(
            packageName = packageName,
            appName = appName,
            source = source,
            selection = emptyMap(),
            options = emptyMap(),
            bundles = contributing.map { it.toRef() },
            experimentalVersion = experimental,
            suggestedVersion = suggested,
            state = if (versionMismatch) BatchItemState.VERSION_MISMATCH else BatchItemState.NO_PATCHES
        )

        if (!hasCompatible && !hasIncompatible && !hasUniversal) return blocked()

        // Every source that has something to contribute is used, the same way the single-app
        // flow merges them. Picking just one would silently drop patches the user relies on
        val contributing = bundles.filter { it.patchSequence(allowIncompatible).any() }
        if (contributing.isEmpty()) return blocked()

        val selection = resolveSelection(packageName, contributing, allowIncompatible, useMount)

        if (selection.values.sumOf { it.size } == 0) return blocked(contributing)

        val options = resolveOptions(packageName, contributing)

        return BatchPatchItem(
            packageName = packageName,
            appName = appName,
            source = source,
            selection = selection,
            options = options,
            bundles = contributing.map { it.toRef() },
            experimentalVersion = experimental,
            suggestedVersion = suggested,
            state = if (versionMismatch) BatchItemState.VERSION_MISMATCH else BatchItemState.READY
        )
    }

    private fun PatchBundleInfo.Scoped.toRef() = BatchBundleRef(
        uid = uid,
        name = name,
        version = version,
        patchNames = patches.mapTo(mutableSetOf()) { it.name }
    )

    /**
     * Mirrors the single-app selection rules across every contributing bundle: a validated
     * saved selection wins, otherwise the bundle defaults are used. Whichever selection is
     * reached, the patches' own availability for the install target has the final say.
     */
    private suspend fun resolveSelection(
        packageName: String,
        bundles: List<PatchBundleInfo.Scoped>,
        allowIncompatible: Boolean,
        useMount: Boolean
    ): PatchSelection {
        val installerType = installerTypeFor(useMount)
        val uids = bundles.mapTo(mutableSetOf()) { it.uid }
        val patchesByName = bundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
        val saved = patchSelectionRepository.getAllSelectionsForPackage(packageName)
            .filterKeys { it in uids }

        if (saved.isNotEmpty()) {
            val validated = validatePatchSelection(saved, patchesByName)

            val merged = bundles.associate { bundle ->
                val seen = patchSelectionRepository.getSeenPatches(packageName, bundle.uid)
                val known = seen ?: saved[bundle.uid] ?: emptySet()

                // Patches added to the bundle since the last run follow their own default,
                // the same rule the expert dialog applies when it merges new patches in
                val newDefaults = bundle.patches
                    .filter {
                        it.name !in known && it.defaultSelected(installerType, SELECTION_APK_ARCHITECTURE)
                    }
                    .mapTo(mutableSetOf()) { it.name }

                bundle.uid to (validated[bundle.uid].orEmpty() + newDefaults)
            }.filterValues { it.isNotEmpty() }

            if (merged.isNotEmpty()) {
                return merged
                    .applyAvailability(installerType, SELECTION_APK_ARCHITECTURE, patchesByName)
                    .applyLegacyMountRules(useMount)
            }
        }

        return bundles
            .toPatchSelection(allowIncompatible) { _, patch ->
                patch.defaultSelected(installerType, SELECTION_APK_ARCHITECTURE)
            }
            .filterValues { it.isNotEmpty() }
            .applyAvailability(installerType, SELECTION_APK_ARCHITECTURE, patchesByName)
            .applyLegacyMountRules(useMount)
    }

    // Safety net for bundles that have not adopted the availability API
    // TODO: Drop this fallback together with PatchSelectionUtils.filterGmsCore
    @Suppress("DEPRECATION")
    private fun PatchSelection.applyLegacyMountRules(useMount: Boolean): PatchSelection =
        if (useMount) filterGmsCore() else this

    /**
     * Expert mode stores options per bundle in the database, simple mode derives them from the
     * per-app preference screen. The patcher is handed whichever set the active mode owns.
     */
    private suspend fun resolveOptions(
        packageName: String,
        bundles: List<PatchBundleInfo.Scoped>
    ): Options {
        if (!prefs.useExpertMode.get()) {
            return runCatching { patchOptionsPrefs.exportPatchOptions(packageName) }
                .getOrDefault(emptyMap())
        }

        val uids = bundles.mapTo(mutableSetOf()) { it.uid }
        val patchesByName = bundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
        val saved = patchOptionsRepository.getAllOptionsForPackage(packageName, patchesByName)
            .filterKeys { it in uids }
        return validatePatchOptions(saved, patchesByName)
    }

    /**
     * Same resolution the home screen uses. Patching renames packages, so an app that is only
     * saved can be named from its APK alone, which is what the resolver falls back through.
     */
    private suspend fun resolveAppName(packageName: String): String =
        appDataResolver.resolveAppData(
            packageName = packageName,
            preferredSource = AppDataSource.ORIGINAL_APK
        ).displayName

    private suspend fun readAttachedFile(file: File): BatchApkSource? {
        if (!file.exists()) return null

        val info = readAttachedPackageInfo(file)
        return BatchApkSource.UserFile(
            file = file,
            version = info?.versionName?.takeUnless { it.isBlank() } ?: "unspecified",
            versionCode = info?.let { pm.getVersionCode(it) }
        )
    }

    /** Package name declared by an attached file, or null when it cannot be read. */
    private suspend fun readAttachedPackageName(file: File): String? =
        runCatching { readAttachedPackageInfo(file)?.packageName }.getOrNull()

    /**
     * Reads package info from an attached file. Split archives are not valid APKs, so the
     * representative base entry is extracted first, exactly like the single-app picker does.
     */
    private suspend fun readAttachedPackageInfo(file: File): PackageInfo? =
        if (SplitApkPreparer.isSplitArchive(file)) {
            val extracted = SplitApkInspector.extractRepresentativeApk(
                source = file,
                workspace = fs.uiTempDir
            )
            try {
                extracted?.let { pm.getPackageInfo(it.file) }
            } finally {
                extracted?.cleanup()
            }
        } else {
            pm.getPackageInfo(file)
        }

    /**
     * Source priority for unattended runs: the saved original first because it is known to be
     * unpatched and already on disk, then the installed APK when it still looks like the
     * stock app.
     */
    private suspend fun findSource(packageName: String, preferInstalled: Boolean): BatchApkSource? {
        if (preferInstalled) {
            installedSource(packageName)?.let { return it }
        }
        savedOriginalSource(packageName)?.let { return it }
        return installedSource(packageName)
    }

    private suspend fun savedOriginalSource(packageName: String): BatchApkSource.SavedOriginal? {
        val record = originalApkRepository.get(packageName) ?: return null
        val file = File(record.filePath).takeIf { it.exists() } ?: return null
        val info = pm.getPackageInfo(file)
        return BatchApkSource.SavedOriginal(
            file = file,
            version = info?.versionName?.takeUnless { it.isBlank() } ?: record.version,
            versionCode = info?.let { pm.getVersionCode(it) }
        )
    }

    /**
     * Returns the installed APK only when it can be trusted to be the unpatched app.
     * Anything that hints at a previous patch (mounted install, mismatching signature,
     * a tracked patched record) disqualifies it, because patching an already patched APK
     * silently produces a broken build.
     */
    private suspend fun installedSource(packageName: String): BatchApkSource.Installed? {
        val pkgInfo = pm.getPackageInfo(packageName) ?: return null
        if (pm.hasSourceApkSignatureMismatch(packageName)) return null
        if (pm.isInstalledByPatchManager(packageName)) return null

        val version = pkgInfo.versionName?.takeUnless { it.isBlank() } ?: return null
        val tracked = installedAppRepository.get(packageName)
        if (tracked != null && tracked.version == version) return null

        val referenceHashes = patchBundleRepository.appMetadata.value[packageName]?.signatures.orEmpty()
        if (referenceHashes.isNotEmpty()) {
            val installedHashes = pm.getInstalledSignatureHashes(packageName)
            if (installedHashes.isNotEmpty() && installedHashes.none { it in referenceHashes }) return null
        }

        val appInfo = pkgInfo.applicationInfo ?: return null
        val sourceDir = appInfo.sourceDir?.takeIf { File(it).exists() } ?: return null

        return BatchApkSource.Installed(
            apkPath = sourceDir,
            splitPaths = appInfo.splitSourceDirs?.filter { File(it).exists() }.orEmpty(),
            version = version,
            versionCode = pm.getVersionCode(pkgInfo)
        )
    }
}
