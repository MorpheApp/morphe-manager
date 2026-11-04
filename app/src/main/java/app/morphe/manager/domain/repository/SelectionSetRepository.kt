/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import app.morphe.manager.data.room.AppDatabase
import app.morphe.manager.data.room.AppDatabase.Companion.generateUid
import app.morphe.manager.data.room.options.Option
import app.morphe.manager.data.room.selection.SelectionSet
import app.morphe.manager.data.room.selection.SelectionSetOption
import app.morphe.manager.data.room.selection.SelectionSetPatch
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

/** A named set as the lists show it, with how many patches it selects from each source. */
data class SelectionSetSummary(
    val uid: Int,
    val packageName: String,
    val name: String,
    val patchCounts: Map<Int, Int>
) {
    val patchCount get() = patchCounts.values.sum()
}

/** The patches and option values of a whole app, as a set or a saved selection holds them. */
data class SelectionSnapshot(
    val patches: PatchSelection,
    val options: Options
)

/**
 * A set as it is stored, its option values still serialized, for a backup to carry. Keyed by
 * source uid, which the backup maps onto the sources of the device it is restored on.
 */
data class StoredSelectionSet(
    val packageName: String,
    val name: String,
    val patches: Map<Int, List<String>>,
    // Map<BundleUid, Map<PatchName, Map<OptionKey, JsonValue>>>
    val options: Map<Int, Map<String, Map<String, String>>>
)

class SelectionSetRepository(db: AppDatabase) {
    private val dao = db.selectionSetDao()

    /** Every set there is, in name order. */
    val summaries: Flow<List<SelectionSetSummary>> = dao.getSummaryRows().map { rows ->
        rows.groupBy { it.uid }.values.map { setRows ->
            val first = setRows.first()
            SelectionSetSummary(
                uid = first.uid,
                packageName = first.packageName,
                name = first.name,
                patchCounts = setRows.mapNotNull { row -> row.patchBundle?.let { it to row.patchCount } }.toMap()
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /** The sets of [packageName], in name order. */
    fun summariesFor(packageName: String): Flow<List<SelectionSetSummary>> =
        summaries.map { sets -> sets.filter { it.packageName == packageName } }.distinctUntilChanged()

    /**
     * Keeps [selection] and [options] under [name] for [packageName], in place of any set it already
     * has under that name. Options of sources the selection leaves unused go with them.
     */
    suspend fun save(packageName: String, name: String, selection: PatchSelection, options: Options) {
        val uid = generateUid()
        dao.save(
            set = SelectionSet(uid, packageName, name, System.currentTimeMillis()),
            patches = selection.flatMap { (bundleUid, patchNames) ->
                patchNames.map { SelectionSetPatch(uid, bundleUid, it) }
            },
            options = options.filterKeys { it in selection }.flatMap { (bundleUid, bundleOptions) ->
                bundleOptions.serializedOptions { patchName, key, value ->
                    SelectionSetOption(uid, bundleUid, patchName, key, value)
                }
            }
        )
    }

    /**
     * What set [setUid] holds, read against [bundlePatches], the patches each source offers the app
     * today. Patches and options a source no longer has are left out, as they are from a saved
     * selection.
     */
    suspend fun load(setUid: Int, bundlePatches: Map<Int, Map<String, PatchInfo>>): SelectionSnapshot {
        val patches = dao.getPatches(setUid)
            .filter { bundlePatches[it.patchBundle]?.containsKey(it.patchName) == true }
            .groupBy({ it.patchBundle }, { it.patchName })
            .mapValues { (_, names) -> names.toSet() }
        val options = dao.getOptions(setUid)
            .groupBy { it.patchBundle }
            .mapNotNull { (bundleUid, rows) ->
                val offered = bundlePatches[bundleUid] ?: return@mapNotNull null
                val read = rows.readAgainst(
                    offered,
                    SelectionSetOption::patchName,
                    SelectionSetOption::key,
                    SelectionSetOption::value
                ).filterValues { it.isNotEmpty() }
                if (read.isEmpty()) null else bundleUid to read
            }
            .toMap()
        return SelectionSnapshot(patches, options)
    }

    /** The patches set [setUid] selects, by source. */
    suspend fun patchNames(setUid: Int): Map<Int, List<String>> =
        dao.getPatches(setUid).groupBy({ it.patchBundle }, { it.patchName })

    suspend fun delete(setUid: Int) = dao.delete(setUid)

    /** Names [set] [name], in place of any other set its app has under that name. */
    suspend fun rename(set: SelectionSetSummary, name: String) = dao.rename(set.uid, set.packageName, name)

    suspend fun reset() = dao.reset()

    /** Every set with what it holds, for a backup. */
    suspend fun exportAll(): List<StoredSelectionSet> = dao.getAll().map { stored(it) }

    /** What set [setUid] holds as it is stored, or null once it is gone. */
    suspend fun stored(setUid: Int): StoredSelectionSet? = dao.get(setUid)?.let { stored(it) }

    private suspend fun stored(set: SelectionSet) = StoredSelectionSet(
        packageName = set.packageName,
        name = set.name,
        patches = patchNames(set.uid),
        options = dao.getOptions(set.uid)
            .groupBy { it.patchBundle }
            .mapValues { (_, rows) ->
                rows.groupBy { it.patchName }.mapValues { (_, patchRows) ->
                    patchRows.associate { it.key to it.value.toJsonString() }
                }
            }
    )

    /**
     * Restores [set], in place of any set its app has under that name. Every source it names must be
     * one this device has, as a set refers to its sources.
     */
    suspend fun import(set: StoredSelectionSet) {
        val uid = generateUid()
        dao.save(
            set = SelectionSet(uid, set.packageName, set.name, System.currentTimeMillis()),
            patches = set.patches.flatMap { (bundleUid, patchNames) ->
                patchNames.distinct().map { SelectionSetPatch(uid, bundleUid, it) }
            },
            options = set.options.flatMap { (bundleUid, bundleOptions) ->
                bundleOptions.flatMap { (patchName, patchOptions) ->
                    patchOptions.map { (key, json) ->
                        SelectionSetOption(uid, bundleUid, patchName, key, Option.SerializedValue.fromJsonString(json))
                    }
                }
            }
        )
    }
}
