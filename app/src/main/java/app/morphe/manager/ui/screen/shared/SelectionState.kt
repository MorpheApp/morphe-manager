/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Snapshot-backed set of selection keys shared by the home multi-select bar
 * and the saved-APK dialog. Holds only the keys - a separate explicit
 * "selection mode" flag can live alongside if a caller needs one.
 */
@Stable
class SelectionState<K : Any> internal constructor(initial: Collection<K> = emptyList()) {
    private val backing = mutableStateListOf<K>().apply { addAll(initial.distinct()) }

    val keys: List<K> get() = backing
    val size: Int get() = backing.size
    val isEmpty: Boolean get() = backing.isEmpty()
    val isNotEmpty: Boolean get() = backing.isNotEmpty()

    fun contains(key: K): Boolean = key in backing

    fun toggle(key: K) {
        if (!backing.remove(key)) backing.add(key)
    }

    fun add(key: K) {
        if (key !in backing) backing.add(key)
    }

    fun remove(key: K) {
        backing.remove(key)
    }

    fun setAll(keys: Collection<K>) {
        backing.clear()
        backing.addAll(keys.distinct())
    }

    fun clear() {
        backing.clear()
    }

    // Drop keys that no longer satisfy the predicate, e.g. after an external list update
    fun retain(predicate: (K) -> Boolean) {
        backing.removeAll { !predicate(it) }
    }
}

@Composable
fun <K : Any> rememberSelectionState(): SelectionState<K> =
    remember { SelectionState() }

/**
 * Variant that survives configuration changes when the key type is [String].
 */
@Composable
fun rememberSaveableStringSelectionState(): SelectionState<String> =
    rememberSaveable(saver = StringSelectionStateSaver) { SelectionState() }

private val StringSelectionStateSaver: Saver<SelectionState<String>, Any> =
    listSaver(
        save = { it.keys.toList() },
        restore = { SelectionState(it) }
    )
