package app.revanced.manager.domain.repository

import app.revanced.manager.data.room.AppDatabase
import app.revanced.manager.data.room.AppDatabase.Companion.generateUid
import app.revanced.manager.data.room.selection.PatchSelection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

class PatchSelectionRepository(db: AppDatabase) {
    private val dao = db.selectionDao()
    private val resetEventsFlow = MutableSharedFlow<ResetEvent>(extraBufferCapacity = 4)
    val resetEvents: SharedFlow<ResetEvent> = resetEventsFlow.asSharedFlow()

    private suspend fun getOrCreateSelection(bundleUid: Int, packageName: String) =
        dao.getSelectionId(bundleUid, packageName) ?: PatchSelection(
            uid = generateUid(),
            patchBundle = bundleUid,
            packageName = packageName
        ).also { dao.createSelection(it) }.uid

    suspend fun getSelection(packageName: String): Map<Int, Set<String>> =
        dao.getSelectedPatches(packageName)
            .mapValues { it.value.toSet() }
            .filterValues { it.isNotEmpty() }

    suspend fun updateSelection(packageName: String, selection: Map<Int, Set<String>>) =
        dao.updateSelections(selection.mapKeys { (sourceUid, _) ->
            getOrCreateSelection(
                sourceUid,
                packageName
            )
        })

    fun getPackagesWithSavedSelection() =
        dao.getPackagesWithSelection().map(Iterable<String>::toSet).distinctUntilChanged()

    suspend fun resetSelectionForPackage(packageName: String) {
        dao.resetForPackage(packageName)
        resetEventsFlow.emit(ResetEvent.Package(packageName))
    }

    suspend fun resetSelectionForPatchBundle(uid: Int) {
        dao.resetForPatchBundle(uid)
        resetEventsFlow.emit(ResetEvent.Bundle(uid))
    }

    suspend fun reset() {
        dao.reset()
        resetEventsFlow.emit(ResetEvent.All)
    }

    suspend fun export(bundleUid: Int): SerializedSelection = dao.exportSelection(bundleUid)

    suspend fun import(bundleUid: Int, selection: SerializedSelection) {
        dao.resetForPatchBundle(bundleUid)
        dao.updateSelections(selection.entries.associate { (packageName, patches) ->
            getOrCreateSelection(bundleUid, packageName) to patches.toSet()
        })
        resetEventsFlow.emit(ResetEvent.Bundle(bundleUid))
    }

    sealed interface ResetEvent {
        data object All : ResetEvent
        data class Package(val packageName: String) : ResetEvent
        data class Bundle(val bundleUid: Int) : ResetEvent
    }
}

/**
 * A [Map] of package name -> selected patches.
 */
typealias SerializedSelection = Map<String, List<String>>
