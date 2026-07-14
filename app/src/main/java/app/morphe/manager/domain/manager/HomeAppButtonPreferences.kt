/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeAppCategory(
    val id: String,
    val name: String,
    val collapsed: Boolean = false
)

data class HomeAppCategoryState(
    val categories: List<HomeAppCategory>,
    val assignments: Map<String, String>
)

enum class HomeAppCategoryViewMode {
    ALL_APPS,
    SOURCES,
    CUSTOM;

    companion object {
        fun fromPreference(value: String?): HomeAppCategoryViewMode =
            entries.firstOrNull { it.name == value } ?: ALL_APPS
    }
}

/**
 * Manages user preferences for home screen app buttons: hidden packages, manual order,
 * sort mode, and grouping.
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

    private val _categoryState = MutableStateFlow(loadCategoryState())
    val categoryState: StateFlow<HomeAppCategoryState> = _categoryState.asStateFlow()

    private val _categoryViewMode = MutableStateFlow(loadCategoryViewMode())
    val categoryViewMode: StateFlow<HomeAppCategoryViewMode> = _categoryViewMode.asStateFlow()

    private val _showCategoryViewSwitcher = MutableStateFlow(loadShowCategoryViewSwitcher())
    val showCategoryViewSwitcher: StateFlow<Boolean> = _showCategoryViewSwitcher.asStateFlow()

    private val _expandedSourceGroups = MutableStateFlow(loadExpandedSourceGroups())
    val expandedSourceGroups: StateFlow<Set<Int>> = _expandedSourceGroups.asStateFlow()

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

    fun setCategoryViewMode(mode: HomeAppCategoryViewMode) {
        prefs.edit { putString(KEY_CATEGORY_VIEW_MODE, mode.name) }
        _categoryViewMode.value = mode
    }

    fun setShowCategoryViewSwitcher(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_CATEGORY_VIEW_SWITCHER, show) }
        _showCategoryViewSwitcher.value = show
    }

    fun createCategory(name: String): String {
        val trimmed = normalizeCategoryName(name) ?: return ""
        val current = _categoryState.value
        val id = "cat_${System.currentTimeMillis().toString(36)}"
        saveCategoryState(
            current.copy(categories = current.categories + HomeAppCategory(id, trimmed))
        )
        return id
    }

    fun renameCategory(categoryId: String, name: String) {
        val trimmed = normalizeCategoryName(name) ?: return
        val current = _categoryState.value
        saveCategoryState(
            current.copy(
                categories = current.categories.map { category ->
                    if (category.id == categoryId) category.copy(name = trimmed) else category
                }
            )
        )
    }

    fun deleteCategory(categoryId: String) {
        val current = _categoryState.value
        saveCategoryState(
            HomeAppCategoryState(
                categories = current.categories.filterNot { it.id == categoryId },
                assignments = current.assignments.filterValues { it != categoryId }
            )
        )
    }

    fun saveCategoryOrder(categoryIds: List<String>) {
        val current = _categoryState.value
        val byId = current.categories.associateBy { it.id }
        val ordered = categoryIds.mapNotNull { byId[it] }
        val orderedIds = ordered.mapTo(mutableSetOf()) { it.id }
        saveCategoryState(
            current.copy(categories = ordered + current.categories.filter { it.id !in orderedIds })
        )
    }

    fun toggleCategoryCollapsed(categoryId: String) {
        val current = _categoryState.value
        saveCategoryState(
            current.copy(
                categories = current.categories.map { category ->
                    if (category.id == categoryId) category.copy(collapsed = !category.collapsed) else category
                }
            )
        )
    }

    fun toggleSourceGroupCollapsed(sourceUid: Int) {
        if (sourceUid == DEFAULT_SOURCE_UID) return
        val current = _expandedSourceGroups.value.toMutableSet()
        if (!current.add(sourceUid)) {
            current.remove(sourceUid)
        }
        saveExpandedSourceGroups(current)
    }

    fun assignToCategory(packageNames: Set<String>, categoryId: String?) {
        val current = _categoryState.value
        val validCategoryId = categoryId?.takeIf { id -> current.categories.any { it.id == id } }
        val nextAssignments = current.assignments.toMutableMap()
        packageNames.forEach { packageName ->
            if (validCategoryId == null) nextAssignments.remove(packageName)
            else nextAssignments[packageName] = validCategoryId
        }
        saveCategoryState(current.copy(assignments = nextAssignments))
    }

    private fun saveHiddenPackages(packages: Set<String>) {
        prefs.edit {
            putStringSet(KEY_HIDDEN, packages)
        }
        _hiddenPackages.value = packages
    }

    private fun loadCategoryState(): HomeAppCategoryState {
        val categories = prefs.getString(KEY_CATEGORIES, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split(CATEGORY_SEPARATOR)
                val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HomeAppCategory(id, name, parts.getOrNull(2)?.toBooleanStrictOrNull() == true)
            }
            ?.toList()
            ?: emptyList()

        val validCategoryIds = categories.mapTo(mutableSetOf()) { it.id }
        val assignments = prefs.getString(KEY_CATEGORY_ASSIGNMENTS, null)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val parts = line.split(CATEGORY_SEPARATOR)
                val packageName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val categoryId = parts.getOrNull(1)?.takeIf { it in validCategoryIds } ?: return@mapNotNull null
                packageName to categoryId
            }
            ?.toMap()
            ?: emptyMap()

        return HomeAppCategoryState(categories, assignments)
    }

    private fun loadCategoryViewMode(): HomeAppCategoryViewMode =
        HomeAppCategoryViewMode.fromPreference(prefs.getString(KEY_CATEGORY_VIEW_MODE, null))

    private fun loadShowCategoryViewSwitcher(): Boolean =
        prefs.getBoolean(KEY_SHOW_CATEGORY_VIEW_SWITCHER, false)

    private fun loadExpandedSourceGroups(): Set<Int> =
        prefs.getStringSet(KEY_EXPANDED_SOURCE_GROUPS, null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    private fun saveCategoryState(state: HomeAppCategoryState) {
        val validIds = state.categories.mapTo(mutableSetOf()) { it.id }
        val cleanAssignments = state.assignments.filterValues { it in validIds }
        prefs.edit {
            putString(
                KEY_CATEGORIES,
                state.categories.joinToString("\n") { category ->
                    listOf(
                        category.id,
                        category.name.sanitizePersistedField(),
                        category.collapsed.toString()
                    ).joinToString(CATEGORY_SEPARATOR)
                }
            )
            putString(
                KEY_CATEGORY_ASSIGNMENTS,
                cleanAssignments.entries.joinToString("\n") { (packageName, categoryId) ->
                    listOf(
                        packageName.sanitizePersistedField(),
                        categoryId
                    ).joinToString(CATEGORY_SEPARATOR)
                }
            )
        }
        _categoryState.value = HomeAppCategoryState(state.categories, cleanAssignments)
    }

    private fun saveExpandedSourceGroups(sourceUids: Set<Int>) {
        val cleanSourceUids = sourceUids - DEFAULT_SOURCE_UID
        prefs.edit {
            putStringSet(KEY_EXPANDED_SOURCE_GROUPS, cleanSourceUids.mapTo(mutableSetOf()) { it.toString() })
        }
        _expandedSourceGroups.value = cleanSourceUids
    }

    private fun normalizeCategoryName(name: String): String? =
        name.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

    private fun String.sanitizePersistedField(): String =
        replace(CATEGORY_SEPARATOR, " ")
            .replace("\n", " ")
            .replace("\r", " ")

    companion object {
        private const val PREFS_NAME = "home_app_buttons"
        private const val KEY_HIDDEN = "hidden_packages"
        private const val KEY_CUSTOM_ORDER = "custom_order"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_CATEGORY_ASSIGNMENTS = "category_assignments"
        private const val KEY_CATEGORY_VIEW_MODE = "category_view_mode"
        private const val KEY_SHOW_CATEGORY_VIEW_SWITCHER = "show_category_view_switcher"
        private const val KEY_EXPANDED_SOURCE_GROUPS = "expanded_source_groups"
        private const val CATEGORY_SEPARATOR = "\t"
    }
}
