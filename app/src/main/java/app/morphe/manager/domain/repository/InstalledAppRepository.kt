package app.morphe.manager.domain.repository

import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.apps.installed.AppliedPatch
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

class InstalledAppRepository(
    db: AppDatabase,
    private val patchBundleRepository: PatchBundleRepository
) {
    private val dao = db.installedAppDao()

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

        dao.upsertApp(
            InstalledApp(
                currentPackageName = currentPackageName,
                originalPackageName = originalPackageName,
                version = version,
                installType = installType,
                selectionPayload = selectionPayload,
                patchedAt = patchedAt
            ),
            patchSelection.flatMap { (uid, patches) ->
                patches.map { patch ->
                    AppliedPatch(
                        packageName = currentPackageName,
                        bundle = uid,
                        patchName = patch,
                        bundleVersion = bundleVersions[uid] // Store bundle version at patch time
                    )
                }
            }
        )
    }

    suspend fun delete(installedApp: InstalledApp) {
        dao.delete(installedApp)
    }
}
