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
import app.morphe.manager.util.AppPackages

/**
 * Manages user preferences for home screen app buttons:
 * - Pinned apps (shown at the top, starred)
 * - Hidden apps (not shown on home screen)
 *
 * Default pinned packages: YouTube, YouTube Music, Reddit
 * All other packages from bundles are visible but not pinned by default.
 */
class HomeAppButtonPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _pinnedPackages = MutableStateFlow(loadPinnedPackages())
    val pinnedPackages: StateFlow<Set<String>> = _pinnedPackages.asStateFlow()

    private val _hiddenPackages = MutableStateFlow(loadHiddenPackages())
    val hiddenPackages: StateFlow<Set<String>> = _hiddenPackages.asStateFlow()

    /**
     * Whether the user has ever customized pinned packages.
     * If false, default pinned packages are used.
     */
    private val hasCustomizedPins: Boolean
        get() = prefs.getBoolean(KEY_HAS_CUSTOMIZED_PINS, false)

    // --- Pinned packages ---

    private fun loadPinnedPackages(): Set<String> {
        if (!hasCustomizedPins) return DEFAULT_PINNED_PACKAGES
        val saved = prefs.getStringSet(KEY_PINNED, null) ?: return DEFAULT_PINNED_PACKAGES
        // Always ensure defaults are included unless user explicitly unpinned them.
        // We track explicit unpins separately so defaults are never silently lost.
        val explicitUnpins = prefs.getStringSet(KEY_EXPLICIT_UNPINS, null) ?: emptySet()
        val defaults = DEFAULT_PINNED_PACKAGES.filter { it !in explicitUnpins }.toSet()
        return saved + defaults
    }

    fun isPinned(packageName: String): Boolean =
        _pinnedPackages.value.contains(packageName)

    fun togglePin(packageName: String) {
        val current = _pinnedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
            // Track explicit unpin of a default package so it's not auto-restored
            if (packageName in DEFAULT_PINNED_PACKAGES) {
                val unpins = prefs.getStringSet(KEY_EXPLICIT_UNPINS, null)?.toMutableSet() ?: mutableSetOf()
                unpins.add(packageName)
                prefs.edit { putStringSet(KEY_EXPLICIT_UNPINS, unpins) }
            }
        } else {
            current.add(packageName)
            // Remove from explicit unpins if user re-pins a default
            if (packageName in DEFAULT_PINNED_PACKAGES) {
                val unpins = prefs.getStringSet(KEY_EXPLICIT_UNPINS, null)?.toMutableSet() ?: mutableSetOf()
                unpins.remove(packageName)
                prefs.edit { putStringSet(KEY_EXPLICIT_UNPINS, unpins) }
            }
            // Unhide if pinning
            if (_hiddenPackages.value.contains(packageName)) {
                unhide(packageName)
            }
        }
        savePinnedPackages(current)
    }

    fun pin(packageName: String) {
        val current = _pinnedPackages.value.toMutableSet()
        current.add(packageName)
        savePinnedPackages(current)
        // Unhide if was hidden
        if (_hiddenPackages.value.contains(packageName)) {
            unhide(packageName)
        }
    }

    fun unpin(packageName: String) {
        val current = _pinnedPackages.value.toMutableSet()
        current.remove(packageName)
        savePinnedPackages(current)
    }

    private fun savePinnedPackages(packages: Set<String>) {
        prefs.edit {
            putStringSet(KEY_PINNED, packages)
            putBoolean(KEY_HAS_CUSTOMIZED_PINS, true)
        }
        _pinnedPackages.value = packages
    }

    // --- Hidden packages ---

    private fun loadHiddenPackages(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN, null) ?: emptySet()
    }

    fun isHidden(packageName: String): Boolean =
        _hiddenPackages.value.contains(packageName)

    fun hide(packageName: String) {
        val current = _hiddenPackages.value.toMutableSet()
        current.add(packageName)
        saveHiddenPackages(current)
        // Unpin if hiding
        if (_pinnedPackages.value.contains(packageName)) {
            unpin(packageName)
        }
    }

    fun unhide(packageName: String) {
        val current = _hiddenPackages.value.toMutableSet()
        current.remove(packageName)
        saveHiddenPackages(current)
    }

    /**
     * Get all currently hidden packages (for "show hidden" UI)
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
        private const val KEY_PINNED = "pinned_packages"
        private const val KEY_HIDDEN = "hidden_packages"
        private const val KEY_HAS_CUSTOMIZED_PINS = "has_customized_pins"
        private const val KEY_EXPLICIT_UNPINS = "explicit_unpins"

        /** Delegates to AppPackages so the source of truth is in Constants.kt */
        val DEFAULT_PINNED_PACKAGES get() = AppPackages.DEFAULT_PINNED_PACKAGES
    }
}
