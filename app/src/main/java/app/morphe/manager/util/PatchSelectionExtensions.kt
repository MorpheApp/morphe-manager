package app.morphe.manager.util

import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.patcher.patch.PatchBundleInfo

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

/**
 * Converts PatchBundleInfo map to signature map (uid -> version).
 * Used for bundle remapping when loading old selections.
 */
fun Map<Int, PatchBundleInfo.Global>.toSignatureMap(): Map<Int, String> {
    return mapNotNull { (uid, info) ->
        info.version?.takeIf { it.isNotBlank() }?.let { version -> uid to version }
    }.toMap()
}
