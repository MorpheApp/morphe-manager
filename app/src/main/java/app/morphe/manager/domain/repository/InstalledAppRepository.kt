/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/repository/InstalledAppRepository.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.repository

import android.content.pm.PackageInfo
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
 * Package names a record keeps its retained copies under, given whether the same app is described
 * by more than one record. A rename leaves a copy under the original package name, but that path
 * is only attributable while a single record can claim it: any sibling record, renamed or not,
 * could have written it just as well.
 */
internal fun retainedPatchedApkOwners(
    currentPackageName: String,
    originalPackageName: String,
    hasSiblingRecords: Boolean
): List<String> = if (originalPackageName == currentPackageName || hasSiblingRecords) {
    listOf(currentPackageName)
} else {
    listOf(currentPackageName, originalPackageName)
}

/**
 * The name a record keeps: the label just read from an artifact, unless it says nothing the
 * package name does not already say, in which case an earlier write's answer still stands.
 */
internal fun rememberedAppLabel(
    readLabel: String?,
    packageName: String,
    previousLabel: String?
): String? = readLabel?.takeUnless { it.isBlank() || it == packageName } ?: previousLabel

/**
 * Whether the record still describes something once its retained copies are gone.
 * A saved-only record is the archive, so nothing is left to track without it.
 */
internal fun outlivesRetainedPatchedApk(installType: InstallType) =
    installType != InstallType.SAVED

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

class InstalledAppRepository(
    db: AppDatabase,
    private val patchBundleRepository: PatchBundleRepository,
    private val filesystem: Filesystem,
    private val pm: PM
) {
    private val dao = db.installedAppDao()
    private val bundleDao = db.patchBundleDao()

    private val _savedPatchedApkChanges = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)

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

    /**
     * Records an install under the package name it ended up with.
     *
     * A run that renamed the package describes a second install rather than a replacement: the
     * rename leaves the previous copy on the device, so it keeps the record that tracks it and
     * goes on receiving updates of its own.
     *
     * @param isClone Whether the install is a copy the user asked for. Callers rewriting a record
     *   that already exists pass what it says, since only the run that built it knows the answer.
     */
    suspend fun addOrUpdate(
        currentPackageName: String,
        originalPackageName: String,
        isClone: Boolean,
        version: String,
        installType: InstallType,
        patchSelection: PatchSelection,
        selectionPayload: SelectionPayload? = null,
        patchedAt: Long? = System.currentTimeMillis() // Default to current time for new patches
    ) {
        // Get current bundle versions at the time of patching
        val bundleVersions = patchBundleRepository.sources.first()
            .associate { it.uid to it.version }

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
                patchedAt = patchedAt,
                appLabel = resolveAppLabel(currentPackageName, version),
                isClone = isClone
            ),
            appliedPatches
        )
    }

    /**
     * Reads the app's own name while the artifacts carrying it are still around: the install that
     * was just made, otherwise the patched APK kept beside the record.
     *
     * A record whose name was already resolved keeps it, since the sources are read in the order
     * they disappear and the last known name beats falling back to the package.
     */
    private suspend fun resolveAppLabel(currentPackageName: String, version: String): String? =
        withContext(Dispatchers.IO) {
            val labeled = readLabel { pm.getPackageInfo(currentPackageName) }
                ?: readLabel {
                    val file = filesystem.getPatchedAppFile(currentPackageName, version)
                    pm.readSavedApkInfo(file, version, currentPackageName)
                }

            rememberedAppLabel(
                readLabel = labeled,
                packageName = currentPackageName,
                previousLabel = dao.get(currentPackageName)?.appLabel
            )
        }

    /** Loading a label reaches into an archive that may have no application entry to read. */
    private fun readLabel(packageInfo: () -> PackageInfo?): String? =
        runCatching { packageInfo()?.let { with(pm) { it.label() } } }.getOrNull()

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

    /**
     * Whether another record describes an install of the same app, which is what keeps the
     * artifacts they share alive once one of those records goes.
     */
    suspend fun hasSiblingRecords(installedApp: InstalledApp): Boolean =
        dao.countSiblings(
            originalPackageName = installedApp.originalPackageName,
            currentPackageName = installedApp.currentPackageName
        ) > 0

    /** Every storage path this record owns a retained copy at, current and legacy. */
    suspend fun savedPatchedApkFiles(installedApp: InstalledApp): List<File> =
        retainedPatchedApkOwners(
            currentPackageName = installedApp.currentPackageName,
            originalPackageName = installedApp.originalPackageName,
            hasSiblingRecords = hasSiblingRecords(installedApp)
        ).map { packageName ->
            filesystem.getPatchedAppFile(packageName, installedApp.version)
        }

    /** Deletes every retained copy this record owns and reports what became of them. */
    private suspend fun deleteSavedPatchedApkFiles(installedApp: InstalledApp): SavedApkDeletion {
        val savedFiles = savedPatchedApkFiles(installedApp).filter { it.exists() }
        if (savedFiles.isEmpty()) {
            Log.d(TAG, "No saved APK found for ${installedApp.currentPackageName} v${installedApp.version}")
            return SavedApkDeletion.Nothing
        }

        val survivors = deleteRetainedCopies(savedFiles)
        if (survivors.isNotEmpty()) {
            Log.w(TAG, "Patched APKs left behind: ${survivors.map { it.absolutePath }}")
            return SavedApkDeletion.Failed
        }

        Log.d(TAG, "Deleted patched APKs for ${installedApp.currentPackageName} v${installedApp.version}")
        return SavedApkDeletion.Deleted
    }

    /**
     * Deletes retained patched APK files while preserving the records that describe an install.
     * A saved-only record describes nothing once its archive is gone, so it goes with the file.
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
                    when (deleteSavedPatchedApkFiles(installedApp)) {
                        SavedApkDeletion.Nothing -> return@forEach
                        SavedApkDeletion.Failed -> deletedEverything = false
                        SavedApkDeletion.Deleted ->
                            if (!outlivesRetainedPatchedApk(installedApp.installType)) {
                                dao.delete(installedApp)
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
