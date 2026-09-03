package app.morphe.manager.util

import app.morphe.manager.data.room.apps.installed.SelectionPayload
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.PatchLockState
import app.morphe.manager.util.PatchSelectionUtils.bulkEnablePatches
import app.morphe.manager.util.PatchSelectionUtils.sanitizeForPatcher
import app.morphe.manager.util.PatchSelectionUtils.spansMultipleBundles
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability

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
 * Returns: A Pair of (remapped payload, extracted selection).
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
     * Puts [patches] under [bundleUid], dropping the entry when nothing is left selected.
     *
     * A bundle absent from the map and a bundle mapped to an empty set would otherwise both mean
     * "nothing selected here", and the rest of the selection code reads only the first as such.
     */
    fun PatchSelection.withBundle(bundleUid: Int, patches: Set<String>): PatchSelection =
        if (patches.isEmpty()) this - bundleUid else this + (bundleUid to patches)

    /**
     * Toggle a patch in a selection map.
     * If the patch is selected, it will be deselected and vice versa.
     * Allows adding patches from bundles not yet in the selection (creates new entry).
     */
    fun PatchSelection.togglePatch(bundleUid: Int, patchName: String): PatchSelection {
        val bundlePatches = this[bundleUid]?.toMutableSet() ?: mutableSetOf()

        if (patchName in bundlePatches) {
            bundlePatches.remove(patchName)
        } else {
            bundlePatches.add(patchName)
        }

        return withBundle(bundleUid, bundlePatches)
    }

    /**
     * Patch names a bulk enable adds to the [selected] patches of one bundle.
     *
     * Universal patches are staged behind the regular ones: applying them blindly is a common
     * cause of failed patching, so they join the selection only once every regular patch is on
     * and [universalArmed] confirms that a previous bulk enable already left it that way.
     * [patches] is the list the user currently sees, so an active search narrows both stages.
     */
    fun bulkEnablePatches(
        patches: List<Pair<PatchInfo, Boolean>>,
        selected: Set<String>,
        universalArmed: Boolean,
        lockStateOf: (PatchInfo) -> PatchLockState
    ): Set<String> {
        val selectable = patches.selectable(lockStateOf)
        val staged = if (universalArmed && selectable.allRegularSelected()) {
            selectable
        } else {
            selectable.filterNot { (patch, _) -> patch.isUniversal }
        }
        return selected + staged.map { (patch, _) -> patch.name }
    }

    /** True when [bulkEnablePatches] leaves universal patches of [patches] for another tap. */
    fun bulkEnableHoldsUniversal(
        patches: List<Pair<PatchInfo, Boolean>>,
        universalArmed: Boolean,
        lockStateOf: (PatchInfo) -> PatchLockState
    ): Boolean {
        val selectable = patches.selectable(lockStateOf)
        val hasUnselectedUniversal = selectable.any { (patch, enabled) -> patch.isUniversal && !enabled }
        return hasUnselectedUniversal && !(universalArmed && selectable.allRegularSelected())
    }

    /**
     * True when a bulk enable of [this] would turn on at least one universal patch, i.e. the
     * locked off ones do not count because [bulkEnablePatches] leaves them alone.
     */
    fun List<Pair<PatchInfo, Boolean>>.hasEnablableUniversal(
        lockStateOf: (PatchInfo) -> PatchLockState
    ) = selectable(lockStateOf).any { (patch, enabled) -> patch.isUniversal && !enabled }

    /** Patches the user is allowed to turn on, i.e. everything except the locked off ones. */
    private fun List<Pair<PatchInfo, Boolean>>.selectable(lockStateOf: (PatchInfo) -> PatchLockState) =
        filterNot { (patch, _) -> lockStateOf(patch) == PatchLockState.LOCKED_OFF }

    private fun List<Pair<PatchInfo, Boolean>>.allRegularSelected() =
        none { (patch, enabled) -> !patch.isUniversal && !enabled }

    /**
     * Update a single option value in an options map.
     * Creates intermediate maps as needed.
     *
     * Value semantics:
     *  - null  → remove the key entirely; used only by "Reset options" so the repository
     *            will re-inject the bundled default on the next load.
     *  - ""    → keep the key with an empty string; the user explicitly cleared the field
     *            via the ✕ button. The key stays in the map so the repository does NOT
     *            re-inject the bundled default. The empty string is stripped to null
     *            by [sanitizeForPatcher] before options reach the patcher engine.
     *  - other → store as-is.
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
            // null = explicit reset → remove key so the repository can re-inject the default
            patchOptions.remove(optionKey)
        } else {
            // "" or any real value → store explicitly
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
     * Strips UI-only empty strings from options before they are handed to the patcher engine.
     *
     * When the user clears a text field via the ✕ button, we store "" in [Options] so the
     * repository does not re-inject the bundled default on the next dialog open.
     * However, the patcher itself should receive null / no key for such fields
     * so it falls back to its own default instead of receiving a literal empty string.
     */
    fun Options.sanitizeForPatcher(): Options =
        mapNotNull { (bundleUid, bundlePatchOptions) ->
            val cleanedBundle = bundlePatchOptions.mapNotNull { (patchName, patchOptions) ->
                val cleanedPatch = patchOptions.filterValues { v ->
                    // Drop blank strings - they are UI placeholders, not real values
                    !(v is String && v.isBlank())
                }
                if (cleanedPatch.isEmpty()) null else patchName to cleanedPatch
            }.toMap()
            if (cleanedBundle.isEmpty()) null else bundleUid to cleanedBundle
        }.toMap()

    /**
     * True when [values] holds at least one option the user moved off the value the patch itself
     * would use. Options are only ever stored on an explicit edit, but a stored value can still
     * match the default, and blanks are the cleared fields [sanitizeForPatcher] drops before the
     * run, so neither counts as a customization.
     */
    fun PatchInfo.hasCustomizedOptions(values: Map<String, Any?>?): Boolean {
        if (values.isNullOrEmpty()) return false

        return options?.any { option ->
            val value = values[option.key] ?: return@any false
            if (value is String && value.isBlank()) return@any false
            value != option.default
        } == true
    }

    /**
     * True when a required option is left without a usable value: neither a stored one nor a
     * default of the patch's own. A blank counts as missing only where the default is not itself
     * blank, since a patch may ship an empty default on purpose.
     */
    fun PatchInfo.hasMissingRequiredOptions(values: Map<String, Any?>?): Boolean =
        options?.any { option ->
            if (!option.required) return@any false
            val effectiveValue = values?.get(option.key) ?: option.default
            effectiveValue == null || (
                effectiveValue is String && effectiveValue.isBlank() &&
                !(option.default is String && option.default.isBlank())
            )
        } == true

    /**
     * Merge [bundleOptions] into the entry of [bundleUid], replacing whole patches rather than
     * individual keys, so a patch it does not mention keeps the values it already had.
     */
    fun Options.mergeBundleOptions(
        bundleUid: Int,
        bundleOptions: Map<String, Map<String, Any?>>
    ): Options {
        val merged = this[bundleUid].orEmpty() + bundleOptions
        return if (merged.isEmpty()) this - bundleUid else this + (bundleUid to merged)
    }

    /**
     * Reset all options for a specific patch in an options map.
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
     * Validate patch selection against current bundle info.
     * Removes patches that no longer exist in the current bundles.
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
     * Validate patch options against current bundle info.
     * Removes options for patches that no longer exist or options that are no longer valid.
     *
     * Uses [allBundlePatches] (including disabled bundles) to avoid falsely removing
     * options from bundles that are merely disabled.
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

    /** True when the run draws patches from more than one bundle. */
    fun PatchSelection.spansMultipleBundles() = count { (_, patches) -> patches.isNotEmpty() } > 1

    /**
     * Apply per-patch availability rules to a selection.
     *
     * For every patch that ships an availability resolver in its bundle, resolve it against the
     * current [installerType] and [apkArchitecture] and adjust the selection:
     *  - REQUIRED: force the patch into the selection even if the user did not pick it
     *  - UNAVAILABLE: remove the patch from the selection even if the user did pick it
     *  - ENABLED / DISABLED: leave the selection untouched (user choice wins)
     *
     * Only bundles the run draws patches from are touched. A bundle nothing is selected from takes
     * no part in the run, so no rule of its own may pull it back in. REQUIRED additionally stops
     * forcing once the selection [spansMultipleBundles]: the user has to stay free to drop one of
     * them, and a patch that cannot be unselected would hold its bundle in the run for good. Such a
     * patch still starts out selected through [PatchInfo.defaultSelected], it merely stays
     * unlockable until the run is down to a single bundle again.
     *
     * Patches without an availability resolver are left untouched here.
     */
    fun PatchSelection.applyAvailability(
        installerType: InstallerType,
        apkArchitecture: ApkArchitecture,
        allBundlePatches: Map<Int, Map<String, PatchInfo>>,
    ): PatchSelection {
        val enforceRequired = !spansMultipleBundles()

        return mapNotNull { (bundleUid, selected) ->
            if (selected.isEmpty()) return@mapNotNull null
            val patchesInBundle = allBundlePatches[bundleUid] ?: return@mapNotNull bundleUid to selected

            val current = selected.toMutableSet()
            patchesInBundle.values.forEach { info ->
                val resolver = info.availabilityResolver ?: return@forEach

                when (resolver.resolve(installerType, apkArchitecture)) {
                    PatchAvailability.REQUIRED    -> if (enforceRequired) current.add(info.name)
                    PatchAvailability.UNAVAILABLE -> current.remove(info.name)
                    PatchAvailability.ENABLED,
                    PatchAvailability.DISABLED    -> Unit
                }
            }

            if (current.isEmpty()) null else bundleUid to current.toSet()
        }.toMap()
    }
}
