/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/domain/repository/PatchOptionsRepository.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.domain.repository

import android.util.Log
import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.options.Option
import app.morphe.manager.data.room.options.OptionGroup
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.LOG_TAG
import app.morphe.manager.util.Options
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PatchOptionsRepository(db: AppDatabase) {
    private val dao = db.optionDao()

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
    ): Map<String, Map<String, Any?>> =
        dao.getOptionsForBundle(packageName, bundleUid).readAgainst(
            bundlePatchInfo,
            Option::patchName,
            Option::key,
            Option::value
        )

    /**
     * Get all options for a package across all bundles
     */
    suspend fun getAllOptionsForPackage(
        packageName: String,
        bundlePatches: Map<Int, Map<String, PatchInfo>>
    ): Map<Int, Map<String, Map<String, Any?>>> =
        dao.getOptions(packageName).mapValues { (sourceUid, bundlePatchOptionsList) ->
            bundlePatchOptionsList.readAgainst(
                bundlePatches[sourceUid].orEmpty(),
                Option::patchName,
                Option::key,
                Option::value
            )
        }

    /**
     * Save options for multiple bundles for a package, replacing what each of them had.
     *
     * @param keeping Per bundle, patches whose stored options stay as they are. Their rows are
     *   carried over as stored, since the patches may not be loaded to read them against.
     */
    suspend fun saveOptions(packageName: String, options: Options, keeping: Map<Int, Set<String>> = emptyMap()) {
        val stored = if (keeping.isEmpty()) emptyMap() else dao.getOptions(packageName)
        dao.updateOptions(options.entries.associate { (sourceUid, bundlePatchOptions) ->
            val groupId = getOrCreateGroup(sourceUid, packageName)
            val kept = keeping[sourceUid].orEmpty() - bundlePatchOptions.keys

            groupId to bundlePatchOptions.serializedOptions { patchName, key, serialized ->
                Option(groupId, patchName, key, serialized)
            } + stored[sourceUid].orEmpty().filter { it.patchName in kept }
        })
    }

    /**
     * Get all packages that have saved options for any bundle
     */
    fun getPackagesWithSavedOptions() =
        dao.getPackagesWithOptions().map(Iterable<String>::toSet).distinctUntilChanged()

    /**
     * Reset options for a specific package (all bundles)
     */
    suspend fun resetOptionsForPackage(packageName: String) {
        dao.resetOptionsForPackage(packageName)
    }

    /**
     * Reset options for a specific package and bundle combination
     */
    suspend fun resetOptionsForPackageAndBundle(packageName: String, bundleUid: Int) {
        dao.resetOptionsForPackageAndBundle(packageName, bundleUid)
    }

    /**
     * Reset options for a specific bundle (all packages)
     */
    suspend fun resetOptionsForPatchBundle(uid: Int) {
        dao.resetOptionsForPatchBundle(uid)
    }

    /**
     * Reset all options
     */
    suspend fun reset() {
        dao.reset()
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
}

/**
 * One source's option values as they are stored, a row each from [row]. A value that cannot be
 * stored is left out, so the option falls back to the default its patch declares.
 */
internal inline fun <T> Map<String, Map<String, Any?>>.serializedOptions(
    row: (patchName: String, key: String, value: Option.SerializedValue) -> T
): List<T> = flatMap { (patchName, patchOptions) ->
    patchOptions.mapNotNull { (key, value) ->
        val serialized = try {
            Option.SerializedValue.fromValue(value)
        } catch (e: Option.SerializationException) {
            Log.e(LOG_TAG, "Option $patchName:$key could not be serialized", e)
            return@mapNotNull null
        }
        row(patchName, key, serialized)
    }
}

/**
 * Stored option rows of one source read back as the options [patches] declare today, keyed by patch
 * and option. Values of options that are gone, or that no longer fit them, are left out.
 */
internal inline fun <T> List<T>.readAgainst(
    patches: Map<String, PatchInfo>,
    patchName: (T) -> String,
    key: (T) -> String,
    value: (T) -> Option.SerializedValue
): Map<String, Map<String, Any?>> = buildMap<String, MutableMap<String, Any?>> {
    this@readAgainst.forEach { row ->
        val name = patchName(row)
        val deserializedPatchOptions = getOrPut(name, ::mutableMapOf)
        val patchOption = patches[name]?.options?.find { it.key == key(row) } ?: return@forEach
        try {
            deserializedPatchOptions[patchOption.key] = value(row).deserializeFor(patchOption)
        } catch (e: Option.SerializationException) {
            Log.w(LOG_TAG, "Option $name:${patchOption.key} could not be deserialized", e)
        }
    }
}
