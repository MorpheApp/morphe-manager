package app.morphe.manager.util

import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.patcher.patch.PatchInfo

/**
 * Converts SelectionPayload back to PatchSelection for runtime use.
 */
fun SelectionPayload.toPatchSelection(): PatchSelection {
    return bundles.associate { bundle ->
        bundle.bundleUid to bundle.patches.filter { it.isNotBlank() }.toSet()
    }.filterValues { it.isNotEmpty() }
}

/**
 * Remaps bundle UIDs in SelectionPayload and extracts selection.
 * Used when loading saved selections that may reference old/renamed bundles.
 *
 * Returns: Pair of (remapped payload, extracted selection)
 */
fun SelectionPayload.remapAndExtractSelection(
    sources: List<PatchBundleSource>
): Pair<SelectionPayload, PatchSelection> {
    val sourceMap = sources.associateBy { it.uid }

    val remappedBundles = mutableListOf<SelectionPayload.BundleSelection>()
    val selection = mutableMapOf<Int, MutableSet<String>>()

    bundles.forEach { bundle ->
        // Simply check if source with this UID exists
        val source = sourceMap[bundle.bundleUid]

        // Only include if we found a matching source
        if (source != null) {
            remappedBundles.add(bundle)

            val patchSet = selection.getOrPut(bundle.bundleUid) { mutableSetOf() }
            bundle.patches.filter { it.isNotBlank() }.forEach { patchSet.add(it) }
        }
    }

    val remappedPayload = SelectionPayload(bundles = remappedBundles)
    val cleanedSelection = selection.mapValues { it.value.toSet() }.filterValues { it.isNotEmpty() }

    return remappedPayload to cleanedSelection
}

object PatchSelectionUtils {

    /**
     * Toggle a patch in a selection map
     * If the patch is selected, it will be deselected and vice versa
     * Allows adding patches from bundles not yet in the selection (creates new entry)
     */
    fun PatchSelection.togglePatch(bundleUid: Int, patchName: String): PatchSelection {
        val current = this.toMutableMap()
        val bundlePatches = current[bundleUid]?.toMutableSet() ?: mutableSetOf()

        if (patchName in bundlePatches) {
            bundlePatches.remove(patchName)
        } else {
            bundlePatches.add(patchName)
        }

        if (bundlePatches.isEmpty()) {
            current.remove(bundleUid)
        } else {
            current[bundleUid] = bundlePatches
        }

        return current
    }

    /**
     * Update a single option value in an options map
     * Creates intermediate maps as needed
     */
    fun Options.updateOption(
        bundleUid: Int,
        patchName: String,
        optionKey: String,
        value: Any?
    ): Options {
        val currentOptions = this.toMutableMap()
        val bundleOptions = currentOptions[bundleUid]?.toMutableMap() ?: mutableMapOf()
        val patchOptions = bundleOptions[patchName]?.toMutableMap() ?: mutableMapOf()

        if (value == null) {
            patchOptions.remove(optionKey)
        } else {
            patchOptions[optionKey] = value
        }

        if (patchOptions.isEmpty()) {
            bundleOptions.remove(patchName)
        } else {
            bundleOptions[patchName] = patchOptions
        }

        if (bundleOptions.isEmpty()) {
            currentOptions.remove(bundleUid)
        } else {
            currentOptions[bundleUid] = bundleOptions
        }

        return currentOptions
    }

    /**
     * Reset all options for a specific patch in an options map
     */
    fun Options.resetOptionsForPatch(bundleUid: Int, patchName: String): Options {
        val currentOptions = this.toMutableMap()
        val bundleOptions = currentOptions[bundleUid]?.toMutableMap() ?: return this

        bundleOptions.remove(patchName)

        if (bundleOptions.isEmpty()) {
            currentOptions.remove(bundleUid)
        } else {
            currentOptions[bundleUid] = bundleOptions
        }

        return currentOptions
    }

    /**
     * Validate patch selection against current bundle info
     * - Removes patches that no longer exist in the current bundles
     *
     * Uses [allBundlePatches] (including disabled bundles) to avoid falsely removing
     * patches from bundles that are merely disabled rather than deleted.
     */
    fun validatePatchSelection(
        savedSelection: PatchSelection,
        allBundlePatches: Map<Int, Map<String, PatchInfo>>
    ): PatchSelection {
        return savedSelection.mapNotNull { (bundleUid, patchNames) ->
            val currentBundlePatches = allBundlePatches[bundleUid] ?: return@mapNotNull null

            // Keep saved patches that still exist in the current bundle
            val validSavedPatches = patchNames.filter { patchName ->
                currentBundlePatches.containsKey(patchName)
            }

            val finalPatches = (validSavedPatches).toSet()

            if (finalPatches.isEmpty()) null else bundleUid to finalPatches
        }.toMap()
    }

    /**
     * Validate patch options against current bundle info
     * Removes options for patches that no longer exist or options that are no longer valid
     *
     * Uses [allBundlePatches] (including disabled bundles) to avoid falsely removing
     * options from bundles that are merely disabled
     */
    fun validatePatchOptions(
        savedOptions: Options,
        allBundlePatches: Map<Int, Map<String, PatchInfo>>
    ): Options {
        return savedOptions.mapNotNull { (bundleUid, bundlePatchOptions) ->
            val currentBundlePatches = allBundlePatches[bundleUid] ?: return@mapNotNull null

            val validOptions = bundlePatchOptions.mapNotNull { (patchName, patchOptions) ->
                val patchInfo = currentBundlePatches[patchName] ?: return@mapNotNull null

                val validPatchOptions = patchOptions.filterKeys { optionKey ->
                    patchInfo.options?.any { it.key == optionKey } == true
                }

                if (validPatchOptions.isEmpty()) null else patchName to validPatchOptions
            }.toMap()

            if (validOptions.isEmpty()) null else bundleUid to validOptions
        }.toMap()
    }

    /**
     * Filter out GmsCore support patch from selection (for mount installs)
     */
    fun PatchSelection.filterGmsCore(): PatchSelection {
        return mapValues { (_, patches) ->
            patches.filterNot { it.equals("GmsCore support", ignoreCase = true) }.toSet()
        }.filterValues { it.isNotEmpty() }
    }
}
