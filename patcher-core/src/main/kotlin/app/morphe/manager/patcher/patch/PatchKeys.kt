/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import app.morphe.patcher.patch.Patch

/** Whether the patch applies to [packageName], which a universal patch does to every package. */
fun Patch<*>.compatibleWith(packageName: String) =
    compatibility.let { targets ->
        targets == null || targets.any { it.packageName == null || it.packageName == packageName }
    }

/** Compatible package names, sorted, or empty for a universal patch. */
fun Patch<*>.compatibilityKey() =
    compatibility?.mapNotNull { it.packageName }?.sorted()?.joinToString().orEmpty()

/**
 * The patches that apply to [packageName], by the key selections and options name them with. The
 * patcher has to resolve a selection to the very patch the user picked, so the app keys what it
 * shows the same way.
 */
fun Collection<Patch<*>>.keyedFor(packageName: String): Map<String, Patch<*>> {
    val relevant = filter { it.compatibleWith(packageName) }
    return uniqueKeys(relevant, { it.name.orEmpty() }, { it.compatibilityKey() })
        .zip(relevant)
        .toMap()
}

/**
 * Keys for [items], in the same order, keeping same-named ones apart so they cannot share one
 * entry. Scoped to a single app's list, because that is how selections are stored.
 */
fun <T> uniqueKeys(items: List<T>, name: (T) -> String, compatibilityKey: (T) -> String): List<String> {
    val keys = items.mapTo(mutableListOf(), name)
    val duplicates = items.withIndex().groupBy { name(it.value) }.filterValues { it.size > 1 }
    if (duplicates.isEmpty()) return keys

    val taken = keys.toMutableSet()
    duplicates.forEach { (duplicateName, group) ->
        // The most specific patch keeps the plain name, so already stored keys stay valid
        group.sortedByDescending { compatibilityKey(it.value) }.drop(1).forEach { (index, _) ->
            var occurrence = 1
            var key = duplicateName
            while (!taken.add(key)) key = "$duplicateName (${++occurrence})"
            keys[index] = key
        }
    }
    return keys
}
