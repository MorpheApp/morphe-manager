/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.batch

import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.trackingKey
import app.morphe.manager.domain.bundles.AppVersionCatalog
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.patcher.patch.ApkArchitectureResolver
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.installerTypeFor
import app.morphe.manager.patcher.split.SplitApkInspector
import app.morphe.manager.patcher.split.SplitApkPreparer
import app.morphe.manager.ui.model.declaresPackageName
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.Options
import app.morphe.manager.util.PM
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.PatchSelectionUtils.applyAvailability
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.InstallerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "Morphe BatchPlanResolver"

/** Stands in for the version of an APK that does not declare one, which is still patchable. */
private const val UNSPECIFIED_VERSION = "unspecified"

/**
 * Whether an attached APK may be patched without asking, given the certificates the sources
 * [declared] for the app and the [hashes] read out of the file itself.
 *
 * The same question the single-app picker answers before patching, and answered permissively
 * wherever it cannot be answered at all: [sdkInt] 29 and below cannot read certificates out of an
 * archive, an app no source declares certificates for has nothing to be checked against, and null
 * [hashes] mean the archive would not open, which says nothing either way. Empty [hashes] are the
 * opposite case and do count against the file: the archive opened and carried no certificate.
 */
internal fun apkSignatureAccepted(sdkInt: Int, declared: Set<String>?, hashes: Set<String>?): Boolean {
    if (sdkInt <= Build.VERSION_CODES.Q) return true
    if (declared.isNullOrEmpty()) return true
    if (hashes == null) return true
    return hashes.any { it in declared }
}

/**
 * The patches of a source that appeared since a saved configuration was last written, and so are
 * selected by their own default rather than by what the user saved.
 *
 * [known] is what that configuration recorded for the source: its seen-patch snapshot, or the
 * saved selection itself where no snapshot exists yet. Null means the source was not part of the
 * configuration at all, which is a different thing entirely from a source whose patches are all
 * new: a source added after the app was configured contributes nothing until the user picks from
 * it, the same way the expert dialog leaves it alone.
 */
internal fun newlyAddedDefaults(
    patches: List<PatchInfo>,
    known: Set<String>?,
    installerType: InstallerType,
    apkArchitecture: ApkArchitecture
): Set<String> {
    if (known == null) return emptySet()
    return patches
        .filter { it.name !in known && it.defaultSelected(installerType, apkArchitecture) }
        .mapTo(mutableSetOf()) { it.name }
}

/** Architecture of the APK an item is patched from, see [ApkArchitectureResolver]. */
internal suspend fun BatchApkSource.apkArchitecture() = when (this) {
    is BatchApkSource.SavedOriginal -> ApkArchitectureResolver.resolve(file)
    is BatchApkSource.UserFile -> ApkArchitectureResolver.resolve(file)
    is BatchApkSource.Installed ->
        ApkArchitectureResolver.resolve((listOf(apkPath) + splitPaths).map(::File))
}

/**
 * Turns a list of package names into a runnable batch plan.
 *
 * Every decision the interactive flow would raise a dialog for is resolved here into an item
 * state instead: a missing APK becomes [BatchItemState.NEEDS_APK], an unsupported version
 * becomes [BatchItemState.VERSION_MISMATCH], and an APK signed by someone the bundles do not
 * vouch for becomes [BatchItemState.UNVERIFIED_SIGNATURE]. The queue itself then runs without
 * prompts.
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
        targets: List<BatchTarget>,
        useMount: Boolean
    ): List<BatchPatchItem> = coroutineScope {
        // Built once for the whole plan: it is derived from every patch of every source, and
        // resolving it per app would repeat that work for each one of them
        val recommended = versionCatalog.recommendedVersions.first()
        targets
            .distinctBy { it.id }
            .map { target ->
                async {
                    resolve(target, useMount, suggestedVersion = recommended[target.packageName]?.version)
                }
            }
            .awaitAll()
    }

    /**
     * Resolves a single target. [attachedFile] overrides source discovery and is used when
     * the user attaches an APK from the preflight screen.
     *
     * @param suggestedVersion Passed in when the whole plan already resolved it, so the version
     *   catalog is not rebuilt once per app; looked up here otherwise.
     * @param allowUnverifiedSignature Set once the user has accepted an APK whose signing
     *   certificate no bundle vouches for, so the same file is not questioned twice.
     */
    suspend fun resolve(
        target: BatchTarget,
        useMount: Boolean,
        attachedFile: File? = null,
        suggestedVersion: String? = null,
        allowIncompatible: Boolean = false,
        allowUnverifiedSignature: Boolean = false,
        preferInstalled: Boolean = false
    ): BatchPatchItem = withContext(Dispatchers.IO) {
        val packageName = target.packageName
        val appName = resolveAppName(target)
        val suggested = suggestedVersion ?: versionCatalog.recommendedVersion(packageName)

        val attached = try {
            attachedFile?.let { readAttachedApk(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read the attached APK for $packageName", e)
            null
        }

        val source = try {
            attached?.asSource() ?: findSource(packageName, preferInstalled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve APK source for $packageName", e)
            null
        }

        /** Blocks the item without a source, which is every reason it has to be replaced. */
        fun unusable(state: BatchItemState, message: String? = null) = BatchPatchItem(
            target = target,
            appName = appName,
            source = null,
            selection = emptyMap(),
            options = emptyMap(),
            bundles = emptyList(),
            suggestedVersion = suggested,
            state = state,
            message = message
        )

        if (source == null) return@withContext unusable(BatchItemState.NEEDS_APK)

        if (attached != null) {
            if (attached.packageName != null && attached.packageName != packageName) {
                return@withContext unusable(BatchItemState.NEEDS_APK, attached.packageName)
            }

            if (!allowUnverifiedSignature && !attached.isSignedAsDeclaredFor(packageName)) {
                // The file is kept rather than dropped: accepting it is one tap away, and the
                // user would otherwise have to download the very same APK again to get there
                return@withContext BatchPatchItem(
                    target = target,
                    appName = appName,
                    source = source,
                    selection = emptyMap(),
                    options = emptyMap(),
                    bundles = emptyList(),
                    suggestedVersion = suggested,
                    state = BatchItemState.UNVERIFIED_SIGNATURE
                )
            }
        }

        buildItem(
            target = target,
            appName = appName,
            source = source,
            useMount = useMount,
            suggested = suggested,
            forceIncompatible = allowIncompatible
        )
    }

    /**
     * Installs whose patches have moved on since they were last built: any bundle an install was
     * patched with now reports a different version than the one recorded at patch time.
     *
     * Every install is answered for separately, so an app kept in several cloned copies has each
     * of them rebuilt rather than only whichever copy the app's package name resolves to.
     *
     * Shared by the automatic schedule and the launcher shortcut, both of which need the same
     * answer to the question "what is worth re-patching right now".
     */
    suspend fun findOutdatedTargets(): List<BatchTarget> = withContext(Dispatchers.IO) {
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
            .map { installed ->
                BatchTarget(
                    packageName = installed.originalPackageName,
                    repatchedPackageName = installed.trackingKey
                )
            }
    }

    /**
     * Rebuilds an existing item against a newly attached APK, keeping the user's
     * force-version choice.
     */
    suspend fun reattach(item: BatchPatchItem, file: File, useMount: Boolean): BatchPatchItem =
        resolve(
            target = item.target,
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
            target = item.target,
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
            target = item.target,
            useMount = useMount,
            attachedFile = (item.source as? BatchApkSource.UserFile)?.file,
            allowIncompatible = true,
            allowUnverifiedSignature = item.forceUnverifiedSignature
        ).copy(
            forceVersionMismatch = true,
            forceUnverifiedSignature = item.forceUnverifiedSignature
        )

    /**
     * Re-resolves an app whose attached APK the user accepted despite its unknown signing
     * certificate. The file is reused rather than asked for again, so accepting costs a tap
     * instead of a second download.
     */
    suspend fun acceptUnverifiedSignature(item: BatchPatchItem, useMount: Boolean): BatchPatchItem =
        resolve(
            target = item.target,
            useMount = useMount,
            attachedFile = (item.source as? BatchApkSource.UserFile)?.file,
            allowIncompatible = item.forceVersionMismatch,
            allowUnverifiedSignature = true
        ).copy(
            forceVersionMismatch = item.forceVersionMismatch,
            forceUnverifiedSignature = true
        )

    private suspend fun buildItem(
        target: BatchTarget,
        appName: String,
        source: BatchApkSource,
        useMount: Boolean,
        suggested: String?,
        forceIncompatible: Boolean
    ): BatchPatchItem {
        val packageName = target.packageName
        val bundles = patchBundleRepository
            .scopedBundleInfoFlow(packageName, source.version, source.versionCode)
            .first()
            .filter { it.enabled }

        // Asked of the bundles the APK is being resolved against rather than of the version
        // catalog, so the badge cannot disagree with the warning the single-app flow shows
        val experimental = bundles.any { it.isVersionExperimental }

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
            target = target,
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

        val configurationKey = configurationKeyFor(target)
        val selection = resolveSelection(
            configurationKey = configurationKey,
            bundles = contributing,
            allowIncompatible = allowIncompatible,
            useMount = useMount,
            apkArchitecture = source.apkArchitecture()
        )

        if (selection.values.sumOf { it.size } == 0) return blocked(contributing)

        val options = resolveOptions(target, configurationKey, contributing)

        return BatchPatchItem(
            target = target,
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
        patchNames = patches.mapTo(mutableSetOf()) { it.name },
        renamingPatchNames = patches
            .filter { it.declaresPackageName }
            .mapTo(mutableSetOf()) { it.name }
    )

    /**
     * Mirrors the single-app selection rules across every contributing bundle: a validated
     * saved selection wins, otherwise the bundle defaults are used. Whichever selection is
     * reached, the patches' own availability for the install target has the final say.
     */
    private suspend fun resolveSelection(
        configurationKey: String,
        bundles: List<PatchBundleInfo.Scoped>,
        allowIncompatible: Boolean,
        useMount: Boolean,
        apkArchitecture: ApkArchitecture
    ): PatchSelection {
        val installerType = installerTypeFor(useMount)
        val uids = bundles.mapTo(mutableSetOf()) { it.uid }
        val patchesByName = bundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
        val saved = patchSelectionRepository.getAllSelectionsForPackage(configurationKey)
            .filterKeys { it in uids }

        if (saved.isNotEmpty()) {
            val validated = validatePatchSelection(saved, patchesByName)

            val merged = bundles.associate { bundle ->
                val seen = patchSelectionRepository.getSeenPatches(configurationKey, bundle.uid)

                // Patches added to the bundle since the last run follow their own default,
                // the same rule the expert dialog applies when it merges new patches in
                val newDefaults = newlyAddedDefaults(
                    patches = bundle.patches,
                    known = seen ?: saved[bundle.uid],
                    installerType = installerType,
                    apkArchitecture = apkArchitecture
                )

                bundle.uid to (validated[bundle.uid].orEmpty() + newDefaults)
            }.filterValues { it.isNotEmpty() }

            if (merged.isNotEmpty()) {
                return merged.applyAvailability(installerType, apkArchitecture, patchesByName)
            }
        }

        return bundles
            .toPatchSelection(allowIncompatible) { _, patch ->
                patch.defaultSelected(installerType, apkArchitecture)
            }
            .filterValues { it.isNotEmpty() }
            .applyAvailability(installerType, apkArchitecture, patchesByName)
    }

    /**
     * Expert mode stores options per bundle in the database, simple mode derives them from the
     * per-app preference screen. The patcher is handed whichever set the active mode owns.
     *
     * Only the database is keyed per install: the preference screen belongs to the app, so every
     * copy of it is built with what the user set there.
     */
    private suspend fun resolveOptions(
        target: BatchTarget,
        configurationKey: String,
        bundles: List<PatchBundleInfo.Scoped>
    ): Options {
        if (!prefs.useExpertMode.get()) {
            return runCatching { patchOptionsPrefs.exportPatchOptions(target.packageName) }
                .getOrDefault(emptyMap())
        }

        val uids = bundles.mapTo(mutableSetOf()) { it.uid }
        val patchesByName = bundles.associate { it.uid to it.patches.associateBy { patch -> patch.name } }
        val saved = patchOptionsRepository.getAllOptionsForPackage(configurationKey, patchesByName)
            .filterKeys { it in uids }
        return validatePatchOptions(saved, patchesByName)
    }

    /**
     * Where a target's saved patches and options live: its own package once it is an install of
     * its own, falling back to the app for a clone that predates configurations of their own.
     */
    private suspend fun configurationKeyFor(target: BatchTarget): String {
        val repatched = target.repatchedPackageName?.takeUnless { it == target.packageName }
            ?: return target.packageName

        val hasOwnConfiguration = patchSelectionRepository
            .getAllSelectionsForPackage(repatched)
            .isNotEmpty()
        return if (hasOwnConfiguration) repatched else target.packageName
    }

    /**
     * Same resolution the home screen uses. Patching renames packages, so an app that is only
     * saved can be named from its APK alone, which is what the resolver falls back through.
     */
    private suspend fun resolveAppName(target: BatchTarget): String =
        appDataResolver.resolveAppData(
            packageName = target.id,
            preferredSource = AppDataSource.ORIGINAL_APK
        ).displayName

    /**
     * Everything one read of an attached archive yields.
     *
     * Identity and certificates come from the same read because unpacking a split archive to get
     * at either of them is the expensive part, and doing it once keeps the two answers consistent.
     *
     * @param signatureHashes Empty when the certificates are unreadable, which is not the same as
     *   null: null means the archive itself could not be opened, so it says nothing about the APK.
     */
    private data class AttachedApk(
        val file: File,
        val packageName: String?,
        val version: String,
        val versionCode: Long?,
        val signatureHashes: Set<String>?
    ) {
        fun asSource() = BatchApkSource.UserFile(
            file = file,
            version = version,
            versionCode = versionCode
        )
    }

    private suspend fun readAttachedApk(file: File): AttachedApk? {
        if (!file.exists()) return null
        if (!SplitApkPreparer.isSplitArchive(file)) return readApk(file, file)

        // A split archive is not a valid APK, so the representative base entry is extracted
        // first, exactly like the single-app picker does
        val extracted = SplitApkInspector.extractRepresentativeApk(
            source = file,
            workspace = fs.uiTempDir
        ) ?: return AttachedApk(
            file = file,
            packageName = null,
            version = UNSPECIFIED_VERSION,
            versionCode = null,
            signatureHashes = null
        )

        return try {
            readApk(extracted.file, file)
        } finally {
            extracted.cleanup()
        }
    }

    /**
     * Reads a plain APK, reporting it as [attachedTo] so a split archive is described by its
     * base entry while the queue keeps working with the archive the user actually attached.
     */
    private fun readApk(apk: File, attachedTo: File): AttachedApk {
        val info: PackageInfo? = pm.getPackageInfo(apk)
        return AttachedApk(
            file = attachedTo,
            packageName = info?.packageName,
            version = info?.versionName?.takeUnless { it.isBlank() } ?: UNSPECIFIED_VERSION,
            versionCode = info?.let { pm.getVersionCode(it) },
            signatureHashes = pm.getApkFileSignatureHashes(apk)
        )
    }

    private fun AttachedApk.isSignedAsDeclaredFor(packageName: String) = apkSignatureAccepted(
        sdkInt = Build.VERSION.SDK_INT,
        declared = patchBundleRepository.appMetadata.value[packageName]?.signatures,
        hashes = signatureHashes
    )

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
