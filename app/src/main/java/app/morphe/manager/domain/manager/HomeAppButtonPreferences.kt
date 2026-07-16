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
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A user-defined home-screen category. [id] is a stable opaque key; [name] is the
 * user-visible label; [collapsed] mirrors the last expand/collapse state.
 */
data class HomeAppCategory(
    val id: String,
    val name: String,
    val collapsed: Boolean = false
)

/**
 * Snapshot of the custom-grouping state: ordered user categories, package assignments, and
 * the uncategorized bucket's collapse flag. Packages not in [assignments] are uncategorized;
 * the bucket flag rides here so it persists alongside the rest of the category state.
 */
data class HomeAppCategoryState(
    val categories: List<HomeAppCategory>,
    val assignments: Map<String, String>,
    val uncategorizedCollapsed: Boolean = false
)

/**
 * How the home app list is grouped. [ALL_APPS] is a flat list; [SOURCES] groups by patch
 * source; [CUSTOM] groups by user-defined categories.
 */
enum class HomeAppCategoryViewMode {
    ALL_APPS,
    SOURCES,
    CUSTOM;

    companion object {
        /** Parse a persisted enum name; unknown values fall back to [ALL_APPS]. */
        fun fromPreference(value: String?): HomeAppCategoryViewMode =
            entries.firstOrNull { it.name == value } ?: ALL_APPS
    }
}

/**
 * Serializable projection of a single [HomeAppCategory] for backup/restore.
 */
@Serializable
data class HomeAppCategorySnapshot(
    val id: String,
    val name: String,
    val collapsed: Boolean = false
)

/**
 * Portable snapshot of [HomeAppButtonPreferences] for the settings export/import flow.
 * All fields are nullable so partial or older backups apply cleanly: missing fields are
 * left as the current on-device value. Per-device-random keys (e.g. source group uids)
 * are intentionally excluded because they don't map across installations.
 */
@Serializable
data class HomeAppButtonSnapshot(
    val hiddenPackages: Set<String>? = null,
    val customOrder: List<String>? = null,
    val sortMode: String? = null,
    val categories: List<HomeAppCategorySnapshot>? = null,
    val assignments: Map<String, String>? = null,
    val uncategorizedCollapsed: Boolean? = null,
    val categoryViewMode: String? = null,
    val showCategoryViewSwitcher: Boolean? = null
)

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

    private val _sourceOrders = MutableStateFlow(loadSourceOrders())
    val sourceOrders: StateFlow<Map<Int, List<String>>> = _sourceOrders.asStateFlow()

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

    private fun loadSourceOrders(): Map<Int, List<String>> {
        val raw = prefs.getString(KEY_SOURCE_ORDERS, null) ?: return emptyMap()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split(CATEGORY_SEPARATOR)
                val uid = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val packageNames = parts.drop(1)
                    .filter { it.isNotBlank() }
                    .distinct()
                if (packageNames.isEmpty()) null else uid to packageNames
            }
            .toMap()
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

    fun saveSourceOrder(sourceUid: Int, packageNames: List<String>) {
        val current = _sourceOrders.value.toMutableMap()
        val cleanPackageNames = packageNames.filter { it.isNotBlank() }.distinct()
        if (cleanPackageNames.isEmpty()) {
            current.remove(sourceUid)
        } else {
            current[sourceUid] = cleanPackageNames
        }
        saveSourceOrders(current)
    }

    fun resetSourceOrder(sourceUid: Int) {
        val current = _sourceOrders.value.toMutableMap()
        current.remove(sourceUid)
        saveSourceOrders(current)
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

    /**
     * Create a new custom category with the given user-entered [name]. Returns the new
     * category's opaque id, or an empty string if [name] is blank after normalization.
     */
    fun createCategory(name: String): String {
        val trimmed = normalizeCategoryName(name) ?: return ""
        val current = _categoryState.value
        // Random suffix guards against millisecond-level collisions from rapid programmatic creation
        val existingIds = current.categories.mapTo(mutableSetOf()) { it.id }
        var id: String
        do {
            id = "cat_${System.currentTimeMillis().toString(36)}_${
                Random.nextInt(0, Int.MAX_VALUE).toString(36)
            }"
        } while (id in existingIds)
        saveCategoryState(
            current.copy(categories = current.categories + HomeAppCategory(id, trimmed))
        )
        return id
    }

    /** Rename an existing category. No-op if [categoryId] is unknown or [name] is blank. */
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

    /**
     * Delete a custom category and drop any package assignments that pointed to it, so those
     * apps fall back to uncategorized.
     */
    fun deleteCategory(categoryId: String) {
        val current = _categoryState.value
        saveCategoryState(
            current.copy(
                categories = current.categories.filterNot { it.id == categoryId },
                assignments = current.assignments.filterValues { it != categoryId }
            )
        )
    }

    /**
     * Reorder categories to match [categoryIds]. Unknown ids are ignored; categories missing
     * from [categoryIds] are appended at the end in their original order.
     */
    fun saveCategoryOrder(categoryIds: List<String>) {
        val current = _categoryState.value
        val byId = current.categories.associateBy { it.id }
        val ordered = categoryIds.mapNotNull { byId[it] }
        val orderedIds = ordered.mapTo(mutableSetOf()) { it.id }
        saveCategoryState(
            current.copy(categories = ordered + current.categories.filter { it.id !in orderedIds })
        )
    }

    /** Flip a custom category's collapsed state, or the Uncategorized buckets when [categoryId] is null. */
    fun toggleCategoryCollapsed(categoryId: String?) {
        val current = _categoryState.value
        val next = if (categoryId == null) {
            current.copy(uncategorizedCollapsed = !current.uncategorizedCollapsed)
        } else {
            current.copy(
                categories = current.categories.map { category ->
                    if (category.id == categoryId) category.copy(collapsed = !category.collapsed) else category
                }
            )
        }
        saveCategoryState(next)
    }

    /**
     * Flip the collapse state of a source group. The default (Morphe) source is always kept
     * expanded, so calls with its uid are ignored.
     */
    fun toggleSourceGroupCollapsed(sourceUid: Int) {
        if (sourceUid == DEFAULT_SOURCE_UID) return
        val current = _expandedSourceGroups.value.toMutableSet()
        if (!current.add(sourceUid)) {
            current.remove(sourceUid)
        }
        saveExpandedSourceGroups(current)
    }

    /**
     * Assign [packageNames] to [categoryId], or clear their assignment (uncategorize) when
     * [categoryId] is null or refers to an unknown category.
     */
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

    /** Serialize the full state for the settings backup file. */
    fun exportState(): HomeAppButtonSnapshot {
        val category = _categoryState.value
        return HomeAppButtonSnapshot(
            hiddenPackages = _hiddenPackages.value,
            customOrder = _customOrder.value,
            sortMode = _sortMode.value.name,
            categories = category.categories.map { HomeAppCategorySnapshot(it.id, it.name, it.collapsed) },
            assignments = category.assignments,
            uncategorizedCollapsed = category.uncategorizedCollapsed,
            categoryViewMode = _categoryViewMode.value.name,
            showCategoryViewSwitcher = _showCategoryViewSwitcher.value
        )
    }

    /**
     * Apply an imported [snapshot]. Fields left as null in the snapshot keep the current
     * value, so partial or older backups apply cleanly without wiping unrelated state.
     */
    fun importState(snapshot: HomeAppButtonSnapshot) {
        prefs.edit {
            snapshot.hiddenPackages?.let { putStringSet(KEY_HIDDEN, it) }
            snapshot.customOrder?.let { putString(KEY_CUSTOM_ORDER, it.joinToString("\n")) }
            snapshot.sortMode?.let { putString(KEY_SORT_MODE, it) }
            snapshot.categoryViewMode?.let { putString(KEY_CATEGORY_VIEW_MODE, it) }
            snapshot.showCategoryViewSwitcher?.let { putBoolean(KEY_SHOW_CATEGORY_VIEW_SWITCHER, it) }
        }
        snapshot.hiddenPackages?.let { _hiddenPackages.value = it }
        snapshot.customOrder?.let { _customOrder.value = it }
        snapshot.sortMode?.let { _sortMode.value = HomeAppSortMode.fromPreference(it) }
        snapshot.categoryViewMode?.let { _categoryViewMode.value = HomeAppCategoryViewMode.fromPreference(it) }
        snapshot.showCategoryViewSwitcher?.let { _showCategoryViewSwitcher.value = it }

        // Route category state through saveCategoryState so persist + StateFlow stay in sync
        val hasCategoryData = snapshot.categories != null ||
                snapshot.assignments != null ||
                snapshot.uncategorizedCollapsed != null
        if (hasCategoryData) {
            val current = _categoryState.value
            saveCategoryState(
                HomeAppCategoryState(
                    categories = snapshot.categories
                        ?.map { HomeAppCategory(it.id, it.name, it.collapsed) }
                        ?: current.categories,
                    assignments = snapshot.assignments ?: current.assignments,
                    uncategorizedCollapsed = snapshot.uncategorizedCollapsed ?: current.uncategorizedCollapsed
                )
            )
        }
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

        return HomeAppCategoryState(
            categories = categories,
            assignments = assignments,
            uncategorizedCollapsed = prefs.getBoolean(KEY_UNCATEGORIZED_COLLAPSED, false)
        )
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
            putBoolean(KEY_UNCATEGORIZED_COLLAPSED, state.uncategorizedCollapsed)
        }
        _categoryState.value = HomeAppCategoryState(
            categories = state.categories,
            assignments = cleanAssignments,
            uncategorizedCollapsed = state.uncategorizedCollapsed
        )
    }

    private fun saveExpandedSourceGroups(sourceUids: Set<Int>) {
        val cleanSourceUids = sourceUids - DEFAULT_SOURCE_UID
        prefs.edit {
            putStringSet(KEY_EXPANDED_SOURCE_GROUPS, cleanSourceUids.mapTo(mutableSetOf()) { it.toString() })
        }
        _expandedSourceGroups.value = cleanSourceUids
    }

    private fun saveSourceOrders(sourceOrders: Map<Int, List<String>>) {
        val cleanSourceOrders = sourceOrders
            .mapValues { (_, packageNames) -> packageNames.filter { it.isNotBlank() }.distinct() }
            .filterValues { it.isNotEmpty() }
        prefs.edit {
            if (cleanSourceOrders.isEmpty()) {
                remove(KEY_SOURCE_ORDERS)
            } else {
                putString(
                    KEY_SOURCE_ORDERS,
                    cleanSourceOrders.entries.joinToString("\n") { (uid, packageNames) ->
                        (listOf(uid.toString()) + packageNames.map { it.sanitizePersistedField() })
                            .joinToString(CATEGORY_SEPARATOR)
                    }
                )
            }
            putString(KEY_SORT_MODE, HomeAppSortMode.MANUAL.name)
        }
        _sourceOrders.value = cleanSourceOrders
        _sortMode.value = HomeAppSortMode.MANUAL
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
        private const val KEY_SOURCE_ORDERS = "source_orders"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_CATEGORY_ASSIGNMENTS = "category_assignments"
        private const val KEY_CATEGORY_VIEW_MODE = "category_view_mode"
        private const val KEY_SHOW_CATEGORY_VIEW_SWITCHER = "show_category_view_switcher"
        private const val KEY_EXPANDED_SOURCE_GROUPS = "expanded_source_groups"
        private const val KEY_UNCATEGORIZED_COLLAPSED = "uncategorized_collapsed"
        private const val CATEGORY_SEPARATOR = "\t"
    }
}
