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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

private const val TAG = "Morphe InstalledAppRepository"

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

    private fun savedPatchedApkFiles(installedApp: InstalledApp) =
        listOf(
            filesystem.getPatchedAppFile(installedApp.currentPackageName, installedApp.version),
            filesystem.getPatchedAppFile(installedApp.originalPackageName, installedApp.version)
        ).distinctBy { it.absolutePath }

    /** Deletes every current or legacy retained copy and reports whether any file changed. */
    private fun deleteSavedPatchedApkFiles(installedApp: InstalledApp): Boolean {
        val savedFiles = savedPatchedApkFiles(installedApp).filter { it.exists() }
        if (savedFiles.isEmpty()) {
            Log.d(TAG, "No saved APK found for ${installedApp.currentPackageName} v${installedApp.version}")
            return false
        }

        var changed = false
        savedFiles.forEach { savedFile ->
            val deleted = runCatching { savedFile.delete() }.getOrDefault(false)
            if (deleted) {
                changed = true
                Log.d(TAG, "Deleted patched APK: ${savedFile.absolutePath}")
            } else {
                Log.w(TAG, "Failed to delete patched APK: ${savedFile.absolutePath}")
            }
        }
        return changed
    }

    /**
     * Deletes retained patched APK files while preserving their patch records and settings.
     * Consumers are notified because this changes the evidence used to verify live installs.
     */
    suspend fun deleteSavedPatchedApks(installedApps: Collection<InstalledApp>) =
        withContext(Dispatchers.IO) {
            val changedPackages = buildSet {
                installedApps.forEach { installedApp ->
                    if (deleteSavedPatchedApkFiles(installedApp)) {
                        add(installedApp.currentPackageName)
                        add(installedApp.originalPackageName)
                    }
                }
            }
            if (changedPackages.isNotEmpty()) {
                _savedPatchedApkChanges.emit(changedPackages)
            }
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
