package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.options.Option
import app.morphe.manager.data.room.options.OptionGroup
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.Options
import app.morphe.manager.util.tag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PatchOptionsRepository(db: AppDatabase) {
    private val dao = db.optionDao()
    private val _resetEventsFlow = MutableSharedFlow<ResetEvent>(extraBufferCapacity = 4)
    val resetEventsFlow: SharedFlow<ResetEvent> = _resetEventsFlow.asSharedFlow()

    private suspend fun getOrCreateGroup(bundleUid: Int, packageName: String) =
        dao.getGroupId(bundleUid, packageName) ?: OptionGroup(
            uid = AppDatabase.generateUid(),
            patchBundle = bundleUid,
            packageName = packageName
        ).also { dao.createOptionGroup(it) }.uid

    /**
     * Get options for a specific bundle and package
     */
    suspend fun getOptionsForBundle(
        packageName: String,
        bundleUid: Int,
        bundlePatchInfo: Map<String, PatchInfo>
    ): Map<String, Map<String, Any?>> {
        val options = dao.getOptionsForBundle(packageName, bundleUid)

        // Patches -> Patch options
        return buildMap<String, MutableMap<String, Any?>> {
            options.forEach { dbOption ->
                val deserializedPatchOptions = this.getOrPut(dbOption.patchName, ::mutableMapOf)

                val patchOption = bundlePatchInfo[dbOption.patchName]?.options?.find { it.key == dbOption.key }
                if (patchOption != null) {
                    try {
                        deserializedPatchOptions[patchOption.key] = dbOption.value.deserializeFor(patchOption)
                    } catch (e: Option.SerializationException) {
                        Log.w(
                            tag,
                            "Option ${dbOption.patchName}:${patchOption.key} could not be deserialized",
                            e
                        )
                    }
                }
            }
        }
    }

    /**
     * Get all options for a package across all bundles
     */
    suspend fun getAllOptionsForPackage(
        packageName: String,
        bundlePatches: Map<Int, Map<String, PatchInfo>>
    ): Map<Int, Map<String, Map<String, Any?>>> {
        val options = dao.getOptions(packageName)

        // Bundle -> Patches
        return buildMap<Int, MutableMap<String, MutableMap<String, Any?>>>(options.size) {
            options.forEach { (sourceUid, bundlePatchOptionsList) ->
                // Patches -> Patch options
                this[sourceUid] =
                    bundlePatchOptionsList.fold(mutableMapOf()) { bundlePatchOptions, dbOption ->
                        val deserializedPatchOptions =
                            bundlePatchOptions.getOrPut(dbOption.patchName, ::mutableMapOf)

                        val patchOption =
                            bundlePatches[sourceUid]?.get(dbOption.patchName)?.options?.find { it.key == dbOption.key }
                        if (patchOption != null) {
                            try {
                                deserializedPatchOptions[patchOption.key] =
                                    dbOption.value.deserializeFor(patchOption)
                            } catch (e: Option.SerializationException) {
                                Log.w(
                                    tag,
                                    "Option ${dbOption.patchName}:${patchOption.key} could not be deserialized",
                                    e
                                )
                            }
                        }

                        bundlePatchOptions
                    }
            }
        }
    }

    /**
     * Get options for a package - returns combined data from all bundles
     * @deprecated Use getOptionsForBundle or getAllOptionsForPackage instead
     */
    @Deprecated(
        "Use getOptionsForBundle for bundle-specific options or getAllOptionsForPackage for all bundles",
        ReplaceWith("getAllOptionsForPackage(packageName, bundlePatches)")
    )
    suspend fun getOptions(
        packageName: String,
        bundlePatches: Map<Int, Map<String, PatchInfo>>
    ): Options = getAllOptionsForPackage(packageName, bundlePatches)

    /**
     * Save options for a specific bundle and package
     */
    suspend fun saveOptionsForBundle(
        packageName: String,
        bundleUid: Int,
        patchOptions: Map<String, Map<String, Any?>>
    ) {
        val groupId = getOrCreateGroup(bundleUid, packageName)

        val optionsList = patchOptions.flatMap { (patchName, options) ->
            options.mapNotNull { (key, value) ->
                val serialized = try {
                    Option.SerializedValue.fromValue(value)
                } catch (e: Option.SerializationException) {
                    Log.e(tag, "Option $patchName:$key could not be serialized", e)
                    return@mapNotNull null
                }

                Option(groupId, patchName, key, serialized)
            }
        }

        dao.updateOptionsForGroup(groupId, optionsList)
    }

    /**
     * Save options for multiple bundles for a package
     */
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

    /**
     * Get all packages that have saved options for any bundle
     */
    fun getPackagesWithSavedOptions() =
        dao.getPackagesWithOptions().map(Iterable<String>::toSet).distinctUntilChanged()

    /**
     * Get all packages that have saved options for a specific bundle
     */
    fun getPackagesWithSavedOptionsForBundle(bundleUid: Int) =
        dao.getPackagesWithOptionsForBundle(bundleUid).map(Iterable<String>::toSet).distinctUntilChanged()

    /**
     * Get summary of options per bundle+package
     * Returns: Map<PackageName, Map<BundleUid, OptionCount>>
     */
    suspend fun getOptionsSummary(): Map<String, Map<Int, Int>> {
        return dao.getOptionsSummary()
    }

    /**
     * Reset options for a specific package (all bundles)
     */
    suspend fun resetOptionsForPackage(packageName: String) {
        dao.resetOptionsForPackage(packageName)
        _resetEventsFlow.emit(ResetEvent.Package(packageName))
    }

    /**
     * Reset options for a specific package and bundle combination
     */
    suspend fun resetOptionsForPackageAndBundle(packageName: String, bundleUid: Int) {
        dao.resetOptionsForPackageAndBundle(packageName, bundleUid)
        _resetEventsFlow.emit(ResetEvent.PackageBundle(packageName, bundleUid))
    }

    /**
     * Reset options for a specific bundle (all packages)
     */
    suspend fun resetOptionsForPatchBundle(uid: Int) {
        dao.resetOptionsForPatchBundle(uid)
        _resetEventsFlow.emit(ResetEvent.Bundle(uid))
    }

    /**
     * Reset all options
     */
    suspend fun reset() {
        dao.reset()
        _resetEventsFlow.emit(ResetEvent.All)
    }

    /**
     * Export raw option values for a specific package and bundle
     * Returns: Map<PatchName, Map<OptionKey, JsonString>>
     */
    suspend fun exportOptionsForBundle(packageName: String, bundleUid: Int): Map<String, Map<String, String>> {
        return dao.exportOptionsForBundle(packageName, bundleUid)
    }

    /**
     * Import raw option values for a specific package and bundle
     * Accepts JSON strings from export and stores them directly
     */
    suspend fun importOptionsForBundle(
        packageName: String,
        bundleUid: Int,
        options: Map<String, Map<String, String>>
    ) {
        dao.importOptionsForBundle(packageName, bundleUid, options)
        _resetEventsFlow.emit(ResetEvent.PackageBundle(packageName, bundleUid))
    }

    sealed interface ResetEvent {
        data object All : ResetEvent
        data class Package(val packageName: String) : ResetEvent
        data class Bundle(val bundleUid: Int) : ResetEvent
        data class PackageBundle(val packageName: String, val bundleUid: Int) : ResetEvent
    }

    /**
     * Get count of options for a package across all bundles
     * Used for displaying counts in UI without deserialization
     */
    suspend fun getOptionsCountForPackage(packageName: String): Int {
        return dao.getOptions(packageName).values.sumOf { optionsList -> optionsList.size }
    }

    /**
     * Get count of options for a specific package+bundle
     * Used for displaying counts in UI without deserialization
     */
    suspend fun getOptionsCountForBundle(packageName: String, bundleUid: Int): Int {
        return dao.getOptionsForBundle(packageName, bundleUid).size
    }

    /**
     * Get total count of all options across all packages
     * Used for displaying counts in UI without deserialization
     */
    suspend fun getTotalOptionsCount(): Int {
        return dao.getOptionsSummaryRaw().sumOf { it.optionCount }
    }
}
