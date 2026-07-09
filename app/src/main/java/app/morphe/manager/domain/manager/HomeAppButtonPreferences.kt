/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages user preferences for home screen app buttons: hidden packages, manual order, and sort mode.
 *
 * Recommended ordering:
 * 1. Patched (installed) apps first
 * 2. Apps with isPinnedByDefault = true
 * 3. All other apps, alphabetical
 *
 * Manual sort mode applies the user's saved order, falling back to recommended ordering when no
 * manual order exists. Fresh installations land on recommended; users upgrading from a pre-sort-mode
 * build that already had a manual order keep it.
 */
class HomeAppButtonPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hiddenPackages = MutableStateFlow(loadHiddenPackages())
    val hiddenPackages: StateFlow<Set<String>> = _hiddenPackages.asStateFlow()

    private val _customOrder = MutableStateFlow(loadCustomOrder())
    val customOrder: StateFlow<List<String>> = _customOrder.asStateFlow()

    private val _sortMode = MutableStateFlow(loadSortMode())
    val sortMode: StateFlow<HomeAppSortMode> = _sortMode.asStateFlow()

    private fun loadHiddenPackages(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN, null) ?: emptySet()
    }

    private fun loadCustomOrder(): List<String> {
        val raw = prefs.getString(KEY_CUSTOM_ORDER, null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotEmpty() }
    }

    private fun loadSortMode(): HomeAppSortMode {
        val stored = prefs.getString(KEY_SORT_MODE, null)
        return when {
            stored != null -> HomeAppSortMode.fromPreference(stored)
            // Preserve manual order for users upgrading from a build without KEY_SORT_MODE
            loadCustomOrder().isNotEmpty() -> HomeAppSortMode.MANUAL
            else -> HomeAppSortMode.RECOMMENDED
        }
    }

    fun hide(packageName: String) {
        val current = _hiddenPackages.value.toMutableSet()
        current.add(packageName)
        saveHiddenPackages(current)
    }

    fun unhide(packageName: String) {
        val current = _hiddenPackages.value.toMutableSet()
        current.remove(packageName)
        saveHiddenPackages(current)
    }

    fun saveOrder(packageNames: List<String>) {
        prefs.edit {
            putString(KEY_CUSTOM_ORDER, packageNames.joinToString("\n"))
            putString(KEY_SORT_MODE, HomeAppSortMode.MANUAL.name)
        }
        _customOrder.value = packageNames
        _sortMode.value = HomeAppSortMode.MANUAL
    }

    fun resetOrder() {
        prefs.edit {
            remove(KEY_CUSTOM_ORDER)
            putString(KEY_SORT_MODE, HomeAppSortMode.MANUAL.name)
        }
        _customOrder.value = emptyList()
        _sortMode.value = HomeAppSortMode.MANUAL
    }

    fun setSortMode(mode: HomeAppSortMode) {
        prefs.edit { putString(KEY_SORT_MODE, mode.name) }
        _sortMode.value = mode
    }

    private fun saveHiddenPackages(packages: Set<String>) {
        prefs.edit {
            putStringSet(KEY_HIDDEN, packages)
        }
        _hiddenPackages.value = packages
    }

    companion object {
        private const val PREFS_NAME = "home_app_buttons"
        private const val KEY_HIDDEN = "hidden_packages"
        private const val KEY_CUSTOM_ORDER = "custom_order"
        private const val KEY_SORT_MODE = "sort_mode"
    }
}
