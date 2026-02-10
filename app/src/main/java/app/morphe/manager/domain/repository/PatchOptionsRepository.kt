package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.options.Option
import app.morphe.manager.data.room.options.OptionGroup
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.Options
import app.morphe.manager.util.tag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PatchOptionsRepository(db: AppDatabase) {
    private val dao = db.optionDao()
    private val resetEventsFlow = MutableSharedFlow<ResetEvent>(extraBufferCapacity = 4)

    private suspend fun getOrCreateGroup(bundleUid: Int, packageName: String) =
        dao.getGroupId(bundleUid, packageName) ?: OptionGroup(
            uid = AppDatabase.generateUid(),
            patchBundle = bundleUid,
            packageName = packageName
        ).also { dao.createOptionGroup(it) }.uid

    suspend fun getOptions(
        packageName: String,
        bundlePatches: Map<Int, Map<String, PatchInfo>>
    ): Options {
        val options = dao.getOptions(packageName)
        // Bundle -> Patches
        return buildMap<Int, MutableMap<String, MutableMap<String, Any?>>>(options.size) {
            options.forEach { (sourceUid, bundlePatchOptionsList) ->
                // Patches -> Patch options
                this[sourceUid] =
                    bundlePatchOptionsList.fold(mutableMapOf()) { bundlePatchOptions, dbOption ->
                        val deserializedPatchOptions =
                            bundlePatchOptions.getOrPut(dbOption.patchName, ::mutableMapOf)

                        val option =
                            bundlePatches[sourceUid]?.get(dbOption.patchName)?.options?.find { it.key == dbOption.key }
                        if (option != null) {
                            try {
                                deserializedPatchOptions[option.key] =
                                    dbOption.value.deserializeFor(option)
                            } catch (e: Option.SerializationException) {
                                Log.w(
                                    tag,
                                    "Option ${dbOption.patchName}:${option.key} could not be deserialized",
                                    e
                                )
                            }
                        }

                        bundlePatchOptions
                    }
            }
        }
    }

    suspend fun saveOptions(packageName: String, options: Options) =
        dao.updateOptions(options.entries.associate { (sourceUid, bundlePatchOptions) ->
            val groupId = getOrCreateGroup(sourceUid, packageName)

            groupId to bundlePatchOptions.flatMap { (patchName, patchOptions) ->
                patchOptions.mapNotNull { (key, value) ->
                    val serialized = try {
                        Option.SerializedValue.fromValue(value)
                    } catch (e: Option.SerializationException) {
                        Log.e(tag, "Option $patchName:$key could not be serialized", e)
                        return@mapNotNull null
                    }

                    Option(groupId, patchName, key, serialized)
                }
            }
        })

    fun getPackagesWithSavedOptions() =
        dao.getPackagesWithOptions().map(Iterable<String>::toSet).distinctUntilChanged()

    suspend fun resetOptionsForPackage(packageName: String) {
        dao.resetOptionsForPackage(packageName)
        resetEventsFlow.emit(ResetEvent.Package(packageName))
    }

    suspend fun resetOptionsForPatchBundle(uid: Int) {
        dao.resetOptionsForPatchBundle(uid)
        resetEventsFlow.emit(ResetEvent.Bundle(uid))
    }

    suspend fun reset() {
        dao.reset()
        resetEventsFlow.emit(ResetEvent.All)
    }

    sealed interface ResetEvent {
        data object All : ResetEvent
        data class Package(val packageName: String) : ResetEvent
        data class Bundle(val bundleUid: Int) : ResetEvent
    }
}
