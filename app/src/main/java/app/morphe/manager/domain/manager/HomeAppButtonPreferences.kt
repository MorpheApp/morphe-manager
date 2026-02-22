/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * Manages user preferences for home screen app buttons.
 *
 * Ordering is handled automatically:
 * 1. Patched (installed) apps first
 * 2. Apps with isPinnedByDefault = true
 * 3. All other apps — alphabetical
 */
class HomeAppButtonPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hiddenPackages = MutableStateFlow(loadHiddenPackages())
    val hiddenPackages: StateFlow<Set<String>> = _hiddenPackages.asStateFlow()

    private fun loadHiddenPackages(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN, null) ?: emptySet()
    }

    fun isHidden(packageName: String): Boolean =
        _hiddenPackages.value.contains(packageName)

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

    /**
     * Get all currently hidden packages (for "show hidden" UI).
     */
    fun getHiddenPackages(): Set<String> = _hiddenPackages.value

    private fun saveHiddenPackages(packages: Set<String>) {
        prefs.edit {
            putStringSet(KEY_HIDDEN, packages)
        }
        _hiddenPackages.value = packages
    }

    companion object {
        private const val PREFS_NAME = "home_app_buttons"
        private const val KEY_HIDDEN = "hidden_packages"
    }
}
