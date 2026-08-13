package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.apps.installed.AppliedPatch
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.util.PM
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "Morphe InstalledAppRepository"

/**
 * Package names a record keeps its retained copies under, given whether a record already occupies
 * its original name. A rename leaves a copy under the original package name, but that same path is
 * where a separate, unrenamed record would keep its own APK.
 */
internal fun retainedPatchedApkOwners(
    currentPackageName: String,
    originalPackageName: String,
    originalPackageIsTracked: Boolean
): List<String> = if (originalPackageName == currentPackageName || originalPackageIsTracked) {
    listOf(currentPackageName)
} else {
    listOf(currentPackageName, originalPackageName)
}

/**
 * How the package occupying a saved record's name relates to its retained patched APK.
 * Signature failures remain [UNKNOWN] so storage cleanup never destroys tracking evidence merely
 * because either side could not be inspected.
 */
internal enum class InstalledSavedApkIdentity {
    ABSENT,
    MATCHES_RETAINED,
    DIFFERS_FROM_RETAINED,
    UNKNOWN
}

internal fun installedSavedApkIdentity(
    packageIsInstalled: Boolean,
    installedSignatureHashes: Set<String>,
    retainedSignatureHashes: Set<String>
): InstalledSavedApkIdentity = when {
    !packageIsInstalled -> InstalledSavedApkIdentity.ABSENT
    installedSignatureHashes.isEmpty() || retainedSignatureHashes.isEmpty() ->
        InstalledSavedApkIdentity.UNKNOWN
    installedSignatureHashes.any { it in retainedSignatureHashes } ->
        InstalledSavedApkIdentity.MATCHES_RETAINED
    else -> InstalledSavedApkIdentity.DIFFERS_FROM_RETAINED
}

/**
 * Whether the record still describes something once its retained copies are gone.
 * A [InstallType.SAVED] record outlives the archive when the installed package matches it, or when
 * identity could not be established. A confirmed different package is not the exported build.
 */
internal fun outlivesRetainedPatchedApk(
    installType: InstallType,
    installedPackageIdentity: InstalledSavedApkIdentity
) = installType != InstallType.SAVED ||
        installedPackageIdentity == InstalledSavedApkIdentity.MATCHES_RETAINED ||
        installedPackageIdentity == InstalledSavedApkIdentity.UNKNOWN

/**
 * Deletes every retained copy in [files] and returns those still on storage afterwards.
 * Storage has the last word rather than the return value of the delete: a path something else
 * cleared in the meantime reports failure while being gone all the same.
 */
internal fun deleteRetainedCopies(files: List<File>): List<File> = files.filter { file ->
    runCatching { file.delete() }
    file.exists()
}

/** What became of the retained copies a record owned after a delete pass over them. */
private enum class SavedApkDeletion {
    /** The record owned no copy on storage. */
    Nothing,

    /** Every copy the record owned is gone. */
    Deleted,

    /** At least one copy survived and still occupies storage. */
    Failed
}

private data class SavedApkDeletionResult(
    val status: SavedApkDeletion,
    val installedPackageIdentity: InstalledSavedApkIdentity = InstalledSavedApkIdentity.UNKNOWN
)

class InstalledAppRepository(
    db: AppDatabase,
    private val patchBundleRepository: PatchBundleRepository,
    private val filesystem: Filesystem,
    private val pm: PM
) {
    private val dao = db.installedAppDao()
    private val bundleDao = db.patchBundleDao()

    private val _orphanedInstalls = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _savedPatchedApkChanges = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)

    /**
     * Copies that patching left behind on the device under their previous package name.
     * They are no longer tracked in the database, so the UI offers to remove them before
     * they turn into installs nothing points at.
     */
    val orphanedInstalls = _orphanedInstalls.asStateFlow()

    /** Package names whose retained patched APK evidence changed without changing its record. */
    val savedPatchedApkChanges = _savedPatchedApkChanges.asSharedFlow()

    fun getAll() = dao.getAll().distinctUntilChanged()

    suspend fun get(packageName: String) = dao.get(packageName)

    fun getAsFlow(packageName: String): Flow<InstalledApp?> =
        dao.getAsFlow(packageName).distinctUntilChanged()

    suspend fun getAppliedPatches(packageName: String): PatchSelection =
        dao.getPatchesSelection(packageName).mapValues { (_, patches) -> patches.toSet() }

    suspend fun getBundleVersionsForApp(packageName: String): Map<Int, String?> =
        dao.getBundleVersions(packageName)
            .associate { it.bundleUid to it.version }

    suspend fun addOrUpdate(
        currentPackageName: String,
        originalPackageName: String,
        version: String,
        installType: InstallType,
        patchSelection: PatchSelection,
        selectionPayload: SelectionPayload? = null,
        patchedAt: Long? = System.currentTimeMillis() // Default to current time for new patches
    ) {
        // Get current bundle versions at the time of patching
        val bundleVersions = patchBundleRepository.sources.first()
            .associate { it.uid to it.version }

        // Remove stale records for the same original package but different current package name
        // This happens when repatching with a different bundle
        val staleEntries = dao.getStaleEntries(originalPackageName, currentPackageName)
        staleEntries.forEach { dao.delete(it) }
        rememberOrphanedInstalls(staleEntries)

        // Skip applied patches whose bundle uid is no longer in patch_bundles:
        // the FK constraint would otherwise abort the entire upsert transaction.
        // Unknown uids are still preserved in selectionPayload (JSON)
        val knownBundleUids = bundleDao.allUids().toSet()
        val appliedPatches = patchSelection.flatMap { (uid, patches) ->
            if (uid !in knownBundleUids) {
                Log.w(TAG, "Skipping applied patches for unknown bundle uid=$uid (kept in selectionPayload)")
                return@flatMap emptyList()
            }
            patches.map { patch ->
                AppliedPatch(
                    packageName = currentPackageName,
                    bundle = uid,
                    patchName = patch,
                    bundleVersion = bundleVersions[uid] // Store bundle version at patch time
                )
            }
        }

        dao.upsertApp(
            InstalledApp(
                currentPackageName = currentPackageName,
                originalPackageName = originalPackageName,
                version = version,
                installType = installType,
                selectionPayload = selectionPayload,
                patchedAt = patchedAt
            ),
            appliedPatches
        )
    }

    /**
     * A package rename does not replace the previous install, it installs alongside it, so the
     * records dropped above may still have a live app behind them. Collect those so the user can
     * remove the leftovers instead of ending up with an untracked copy that never sees an update.
     *
     * Only apps a patch manager put there are offered: a record can outlive the patched APK it
     * described, and by then the package name may well belong to the stock app from the Play Store.
     * Mount installs never qualify anyway, since their package is the stock app and has to be
     * unmounted rather than uninstalled.
     */
    private fun rememberOrphanedInstalls(staleEntries: List<InstalledApp>) {
        val stillInstalled = staleEntries.filter {
            it.installType != InstallType.MOUNT &&
                    pm.getPackageInfo(it.currentPackageName) != null &&
                    pm.isInstalledByPatchManager(it.currentPackageName)
        }
        if (stillInstalled.isEmpty()) return

        _orphanedInstalls.update { current ->
            val known = current.mapTo(mutableSetOf()) { it.currentPackageName }
            current + stillInstalled.filter { it.currentPackageName !in known }
        }
        Log.i(TAG, "Orphaned installs left by rename: ${stillInstalled.map { it.currentPackageName }}")
    }

    /** Stops offering [installedApp] for cleanup once the user has removed or kept it. */
    fun forgetOrphanedInstall(installedApp: InstalledApp) = _orphanedInstalls.update { current ->
        current.filterNot { it.currentPackageName == installedApp.currentPackageName }
    }

    /**
     * Update only the [InstalledApp.version] of an existing record and drop the orphan
     * patched APKs it owned at the old version path. Applied patches and selectionPayload
     * are left untouched.
     */
    suspend fun updateInstalledVersion(app: InstalledApp, newVersion: String) =
        withContext(Dispatchers.IO) {
            if (app.version == newVersion) return@withContext
            dao.upsertApp(app.copy(version = newVersion))
            savedPatchedApkFiles(app).forEach { file ->
                if (file.exists()) {
                    runCatching { file.delete() }.onFailure {
                        Log.w(TAG, "Failed to delete ${file.absolutePath}", it)
                    }
                }
            }
            Log.i(TAG, "Reconciled version for ${app.currentPackageName}: ${app.version} → $newVersion")
        }

    /** Every storage path this record owns a retained copy at, current and legacy. */
    suspend fun savedPatchedApkFiles(installedApp: InstalledApp): List<File> =
        retainedPatchedApkOwners(
            currentPackageName = installedApp.currentPackageName,
            originalPackageName = installedApp.originalPackageName,
            originalPackageIsTracked = dao.get(installedApp.originalPackageName) != null
        ).map { packageName ->
            filesystem.getPatchedAppFile(packageName, installedApp.version)
        }

    /**
     * Resolves whether the package at a saved record's name is the retained export.
     * Only archives that still match the record may prove a signature mismatch; a corrupt or stale
     * file leaves the result unknown and therefore cannot cause the record to be discarded.
     */
    private fun resolveInstalledSavedApkIdentity(
        installedApp: InstalledApp,
        savedFiles: List<File>
    ): InstalledSavedApkIdentity {
        if (installedApp.installType != InstallType.SAVED) {
            return InstalledSavedApkIdentity.UNKNOWN
        }

        val packageIsInstalled = pm.getPackageInfo(installedApp.currentPackageName) != null
        if (!packageIsInstalled) return InstalledSavedApkIdentity.ABSENT

        val installedHashes = pm.getInstalledSignatureHashes(installedApp.currentPackageName)
        val retainedHashes = savedFiles.asSequence()
            .filter { file ->
                pm.readSavedApkInfo(
                    file,
                    installedApp.version,
                    installedApp.currentPackageName
                ) != null
            }
            .flatMap { pm.getApkFileSignatureHashes(it).asSequence() }
            .toSet()

        return installedSavedApkIdentity(
            packageIsInstalled = true,
            installedSignatureHashes = installedHashes,
            retainedSignatureHashes = retainedHashes
        )
    }

    /** Deletes every retained copy this record owns and reports what became of them. */
    private suspend fun deleteSavedPatchedApkFiles(installedApp: InstalledApp): SavedApkDeletionResult {
        val savedFiles = savedPatchedApkFiles(installedApp).filter { it.exists() }
        if (savedFiles.isEmpty()) {
            Log.d(TAG, "No saved APK found for ${installedApp.currentPackageName} v${installedApp.version}")
            return SavedApkDeletionResult(SavedApkDeletion.Nothing)
        }

        val installedPackageIdentity = resolveInstalledSavedApkIdentity(installedApp, savedFiles)
        val survivors = deleteRetainedCopies(savedFiles)
        if (survivors.isNotEmpty()) {
            Log.w(TAG, "Patched APKs left behind: ${survivors.map { it.absolutePath }}")
            return SavedApkDeletionResult(SavedApkDeletion.Failed)
        }

        Log.d(TAG, "Deleted patched APKs for ${installedApp.currentPackageName} v${installedApp.version}")
        return SavedApkDeletionResult(SavedApkDeletion.Deleted, installedPackageIdentity)
    }

    /**
     * Deletes retained patched APK files while preserving the records that describe an install.
     * Exporting records an app as [InstallType.SAVED], and installing that export later does not
     * change the record. A matching installed package therefore keeps the row, while a confirmed
     * different package does not make the saved-only record outlive its archive. An unreadable
     * identity is preserved so a transient inspection failure cannot destroy tracking evidence.
     * A copy that survives the attempt keeps its record: the listing is built from records, so
     * dropping one would leave the file occupying storage with nothing left to remove it with.
     * Consumers are notified because this changes the evidence used to verify live installs.
     *
     * @return whether every copy the given records owned is gone.
     */
    suspend fun deleteSavedPatchedApks(installedApps: Collection<InstalledApp>): Boolean =
        withContext(Dispatchers.IO) {
            var deletedEverything = true
            val changedPackages = buildSet {
                installedApps.forEach { installedApp ->
                    val deletion = deleteSavedPatchedApkFiles(installedApp)
                    when (deletion.status) {
                        SavedApkDeletion.Nothing -> return@forEach
                        SavedApkDeletion.Failed -> deletedEverything = false
                        SavedApkDeletion.Deleted -> {
                            if (!outlivesRetainedPatchedApk(
                                    installedApp.installType,
                                    deletion.installedPackageIdentity
                                )
                            ) {
                                dao.delete(installedApp)
                            }
                        }
                    }
                    add(installedApp.currentPackageName)
                    add(installedApp.originalPackageName)
                }
            }
            if (changedPackages.isNotEmpty()) {
                _savedPatchedApkChanges.emit(changedPackages)
            }
            deletedEverything
        }

    suspend fun deleteSavedPatchedApk(installedApp: InstalledApp) =
        deleteSavedPatchedApks(listOf(installedApp))

    /**
     * Deletes an installed app record together with every retained patched APK copy.
     * This is the explicit "forget app" operation, not storage-only APK cleanup.
     */
    suspend fun delete(installedApp: InstalledApp) = withContext(Dispatchers.IO) {
        deleteSavedPatchedApkFiles(installedApp)

        dao.delete(installedApp)
    }
}
