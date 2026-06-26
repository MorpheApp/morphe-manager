package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.apps.installed.AppliedPatch
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "Morphe InstalledAppRepository"

class InstalledAppRepository(
    db: AppDatabase,
    private val patchBundleRepository: PatchBundleRepository,
    private val filesystem: Filesystem
) {
    private val dao = db.installedAppDao()
    private val bundleDao = db.patchBundleDao()

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
     * Update only the [InstalledApp.version] of an existing record and drop the orphan
     * patched APK at the old version path. Applied patches and selectionPayload are
     * left untouched.
     */
    suspend fun updateInstalledVersion(app: InstalledApp, newVersion: String) =
        withContext(Dispatchers.IO) {
            if (app.version == newVersion) return@withContext
            dao.upsertApp(app.copy(version = newVersion))
            val orphans = listOf(
                filesystem.getPatchedAppFile(app.currentPackageName, app.version),
                filesystem.getPatchedAppFile(app.originalPackageName, app.version)
            ).distinctBy { it.absolutePath }
            orphans.forEach { file ->
                if (file.exists()) {
                    runCatching { file.delete() }.onFailure {
                        Log.w(TAG, "Failed to delete ${file.absolutePath}", it)
                    }
                }
            }
            Log.i(TAG, "Reconciled version for ${app.currentPackageName}: ${app.version} → $newVersion")
        }

    /**
     * Delete installed app record and its saved patched APK file from storage.
     * Looks up the file by both currentPackageName and originalPackageName to handle renamed packages.
     */
    suspend fun delete(installedApp: InstalledApp) = withContext(Dispatchers.IO) {
        // Find the saved patched APK file
        val savedFile = listOf(
            filesystem.getPatchedAppFile(installedApp.currentPackageName, installedApp.version),
            filesystem.getPatchedAppFile(installedApp.originalPackageName, installedApp.version)
        ).distinct().firstOrNull { it.exists() }

        if (savedFile != null) {
            val deleted = runCatching { savedFile.delete() }.getOrElse { false }
            if (deleted) {
                Log.d(TAG, "Deleted patched APK: ${savedFile.absolutePath}")
            } else {
                Log.w(TAG, "Failed to delete patched APK: ${savedFile.absolutePath}")
            }
        } else {
            Log.d(TAG, "No saved APK found for ${installedApp.currentPackageName} v${installedApp.version}")
        }

        dao.delete(installedApp)
    }
}
