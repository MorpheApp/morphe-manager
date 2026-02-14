package app.morphe.manager.domain.repository

import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.AppDatabase.Companion.generateUid
import app.morphe.manager.data.room.selection.PatchSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PatchSelectionRepository(db: AppDatabase) {
    private val dao = db.selectionDao()
    private val _resetEventsFlow = MutableSharedFlow<ResetEvent>(extraBufferCapacity = 4)

    private suspend fun getOrCreateSelection(bundleUid: Int, packageName: String) =
        dao.getSelectionId(bundleUid, packageName) ?: PatchSelection(
            uid = generateUid(),
            patchBundle = bundleUid,
            packageName = packageName
        ).also { dao.createSelection(it) }.uid

    /**
     * Get selection for a specific bundle and package
     */
    suspend fun getSelectionForBundle(packageName: String, bundleUid: Int): Set<String> =
        dao.getSelectedPatchesForBundle(packageName, bundleUid).toSet()

    /**
     * Get all selections for a package (across all bundles)
     * Returns: Map<BundleUid, Set<PatchNames>>
     */
    suspend fun getAllSelectionsForPackage(packageName: String): Map<Int, Set<String>> =
        dao.getAllSelectionsForPackage(packageName)
            .mapValues { it.value.toSet() }
            .filterValues { it.isNotEmpty() }

    /**
     * Get selection for a package - returns combined data from all bundles
     * @deprecated Use getSelectionForBundle for bundle-specific selection or getAllSelectionsForPackage for all bundles
     */
    @Deprecated(
        "Use getSelectionForBundle for bundle-specific selection or getAllSelectionsForPackage for all bundles",
        ReplaceWith("getAllSelectionsForPackage(packageName)")
    )
    suspend fun getSelection(packageName: String): Map<Int, Set<String>> =
        dao.getSelectedPatches(packageName)
            .mapValues { it.value.toSet() }
            .filterValues { it.isNotEmpty() }

    /**
     * Update selection for a specific bundle and package
     */
    suspend fun updateSelectionForBundle(
        packageName: String,
        bundleUid: Int,
        patches: Set<String>
    ) {
        val selectionId = getOrCreateSelection(bundleUid, packageName)
        dao.updateSelections(mapOf(selectionId to patches))
    }

    /**
     * Update selections for multiple bundles for a package
     */
    suspend fun updateSelection(packageName: String, selection: Map<Int, Set<String>>) =
        dao.updateSelections(selection.mapKeys { (sourceUid, _) ->
            getOrCreateSelection(
                sourceUid,
                packageName
            )
        })

    /**
     * Get all packages that have saved selections for any bundle
     */
    fun getPackagesWithSavedSelection() =
        dao.getPackagesWithSelection().map(Iterable<String>::toSet).distinctUntilChanged()

    /**
     * Get all packages that have saved selections for a specific bundle
     */
    fun getPackagesWithSavedSelectionForBundle(bundleUid: Int) =
        dao.getPackagesWithSelectionForBundle(bundleUid).map(Iterable<String>::toSet).distinctUntilChanged()

    /**
     * Get data about saved selections per bundle+package
     * Returns: Map<PackageName, Map<BundleUid, PatchCount>>
     */
    fun getSelectionsSummaryFlow(): Flow<Map<String, Map<Int, Int>>> {
        return dao.getSelectionsSummaryFlow()
    }

    /**
     * Reset selection for a specific package (all bundles)
     */
    suspend fun resetSelectionForPackage(packageName: String) {
        dao.resetForPackage(packageName)
        _resetEventsFlow.emit(ResetEvent.Package(packageName))
    }

    /**
     * Reset selection for a specific package and bundle combination
     */
    suspend fun resetSelectionForPackageAndBundle(packageName: String, bundleUid: Int) {
        dao.resetForPackageAndBundle(packageName, bundleUid)
        _resetEventsFlow.emit(ResetEvent.PackageBundle(packageName, bundleUid))
    }

    /**
     * Reset all selections for a specific bundle (all packages)
     */
    suspend fun resetSelectionForPatchBundle(uid: Int) {
        dao.resetForPatchBundle(uid)
        _resetEventsFlow.emit(ResetEvent.Bundle(uid))
    }

    /**
     * Reset all selections
     */
    suspend fun reset() {
        dao.reset()
        _resetEventsFlow.emit(ResetEvent.All)
    }

    /**
     * Export selection for a specific bundle and package
     */
    suspend fun exportForPackageAndBundle(packageName: String, bundleUid: Int): List<String> =
        dao.exportSelectionForPackageAndBundle(packageName, bundleUid)

    /**
     * Export all selections for a bundle
     */
    suspend fun export(bundleUid: Int): SerializedSelection = dao.exportSelection(bundleUid)

    /**
     * Import selection for a specific bundle
     */
    suspend fun import(bundleUid: Int, selection: SerializedSelection) {
        dao.resetForPatchBundle(bundleUid)
        dao.updateSelections(selection.entries.associate { (packageName, patches) ->
            getOrCreateSelection(bundleUid, packageName) to patches.toSet()
        })
        _resetEventsFlow.emit(ResetEvent.Bundle(bundleUid))
    }

    /**
     * Import selection for a specific package and bundle
     */
    suspend fun importForPackageAndBundle(
        packageName: String,
        bundleUid: Int,
        patches: List<String>
    ) {
        val selectionId = getOrCreateSelection(bundleUid, packageName)
        dao.updateSelections(mapOf(selectionId to patches.toSet()))
        _resetEventsFlow.emit(ResetEvent.PackageBundle(packageName, bundleUid))
    }

    sealed interface ResetEvent {
        data object All : ResetEvent
        data class Package(val packageName: String) : ResetEvent
        data class Bundle(val bundleUid: Int) : ResetEvent
        data class PackageBundle(val packageName: String, val bundleUid: Int) : ResetEvent
    }
}

/**
 * A [Map] of package name -> selected patches.
 */
typealias SerializedSelection = Map<String, List<String>>
