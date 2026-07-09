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

enum class HomeAppSortMode {
    CUSTOM,
    RECOMMENDED,
    NAME_ASC,
    NAME_DESC,
    UPDATES_FIRST;

    companion object {
        fun fromPreference(value: String?): HomeAppSortMode =
            entries.firstOrNull { it.name == value } ?: CUSTOM
    }
}

/**
 * Manages user preferences for home screen app buttons: hidden packages, custom order, and sort mode.
 *
 * Morphe ordering:
 * 1. Patched (installed) apps first
 * 2. Apps with isPinnedByDefault = true
 * 3. All other apps, alphabetical
 *
 * Custom sort mode applies the saved manual order, falling back to Morphe ordering when no manual order exists.
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

    private fun loadSortMode(): HomeAppSortMode =
        HomeAppSortMode.fromPreference(prefs.getString(KEY_SORT_MODE, null))

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
            putString(KEY_SORT_MODE, HomeAppSortMode.CUSTOM.name)
        }
        _customOrder.value = packageNames
        _sortMode.value = HomeAppSortMode.CUSTOM
    }

    fun resetOrder() {
        prefs.edit {
            remove(KEY_CUSTOM_ORDER)
            putString(KEY_SORT_MODE, HomeAppSortMode.CUSTOM.name)
        }
        _customOrder.value = emptyList()
        _sortMode.value = HomeAppSortMode.CUSTOM
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
