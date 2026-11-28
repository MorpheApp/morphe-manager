/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.selection

import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.otherBuildPatchesByUid
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import app.morphe.manager.util.PatchSelectionUtils.validatePatchOptions
import app.morphe.manager.util.PatchSelectionUtils.validatePatchSelection
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.InstallerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The patches of a source that appeared since a saved configuration was last written, and so are
 * selected by their own default rather than by what the user saved.
 *
 * [known] is the seen-patch snapshot that configuration recorded for the source. Null means there
 * is none, which is a different thing entirely from a source whose patches are all new: a source
 * added after the app was configured contributes nothing until the user picks from it, the same way
 * the expert dialog leaves it alone. A selection with no snapshot behind it, such as one restored
 * from a backup, stands as saved too, or a patch the user turned off would read as new.
 */
internal fun newlyAddedDefaults(
    patches: List<PatchInfo>,
    known: Set<String>?,
    installerType: InstallerType,
    apkArchitecture: ApkArchitecture
): Set<String> {
    if (known == null) return emptySet()
    return patches
        .filter { it.name !in known && it.defaultSelected(installerType, apkArchitecture) }
        .mapTo(mutableSetOf()) { it.name }
}

/**
 * A saved selection brought up to date with the patches added since it was made.
 *
 * [validated] is that selection with patches the sources no longer carry already removed. Every
 * bundle in [bundles] then contributes what [newlyAddedDefaults] asks for, measured against the
 * names [known] recalls for it.
 *
 * A bundle outside [bundles] keeps whatever [validated] holds for it. That is how a source the app
 * is kept from keeps the selection made from it while taking no part in the run.
 */
internal fun mergeNewlyAdded(
    bundles: List<PatchBundleInfo.Scoped>,
    validated: PatchSelection,
    known: (bundleUid: Int) -> Set<String>?,
    installerType: InstallerType,
    apkArchitecture: ApkArchitecture
): PatchSelection = buildMap {
    putAll(validated)

    bundles.forEach { bundle ->
        val added = newlyAddedDefaults(
            patches = bundle.patches,
            known = known(bundle.uid),
            installerType = installerType,
            apkArchitecture = apkArchitecture
        )
        if (added.isNotEmpty()) put(bundle.uid, getOrDefault(bundle.uid, emptySet()) + added)
    }
}.filterValues { it.isNotEmpty() }

/**
 * [selection] with what [stored] holds for the patches of another build of the app put back. A run
 * for one build neither shows nor offers those, so it must not clear what the user chose for them.
 */
internal fun keepingOtherBuilds(
    selection: PatchSelection,
    stored: PatchSelection,
    otherBuildPatches: Map<Int, Set<String>>
): PatchSelection = buildMap {
    putAll(selection)

    otherBuildPatches.forEach { (bundleUid, names) ->
        val kept = stored[bundleUid].orEmpty() intersect names
        if (kept.isNotEmpty()) put(bundleUid, getOrDefault(bundleUid, emptySet()) + kept)
    }
}

/**
 * What the user set up by hand for an app, read against the patches its sources carry now, and
 * recorded once a run is set up or built.
 *
 * The single-app flow and the queue both go through here, so the same app starts from the same
 * patches either way. Reading writes nothing back: whether a cleaned configuration is saved, or
 * the user told about it, is up to the caller.
 *
 * Builds of one app for different devices share a configuration. What is saved for the patches of
 * another build, see [PatchBundleInfo.Scoped.otherBuildPatches], is neither read as outdated nor
 * written over.
 */
class SavedPatchConfiguration(
    private val selectionRepository: PatchSelectionRepository,
    private val optionsRepository: PatchOptionsRepository
) {
    /**
     * A saved selection, see [selection].
     *
     * @param removedCount Patches it named that the sources no longer carry, which are left out.
     * @param newPatches Per source, the patches added since the selection was saved, including
     *   those their default left out.
     */
    class Selection(
        val patches: PatchSelection,
        val removedCount: Int,
        val newPatches: Map<Int, Set<String>>
    )

    /**
     * What was saved under [configurationKey] for [bundles], without the patches they no longer
     * carry. Other sources are left out.
     */
    suspend fun validSelection(
        configurationKey: String,
        bundles: List<PatchBundleInfo.Scoped>
    ): PatchSelection = validatePatchSelection(saved(configurationKey, bundles), bundles.patchesByName())

    /**
     * [validSelection] brought up to date with the patches added to [bundles] since it was saved,
     * which follow their own default for the install target. Null when nothing was saved for them,
     * so a first run tells no patch apart as new.
     */
    suspend fun selection(
        configurationKey: String,
        bundles: List<PatchBundleInfo.Scoped>,
        installerType: InstallerType,
        apkArchitecture: ApkArchitecture
    ): Selection? {
        val saved = saved(configurationKey, bundles).takeIf { it.isNotEmpty() } ?: return null
        val validated = validatePatchSelection(saved, bundles.patchesByName())

        // The seen-patch snapshot is what tells a genuinely new patch from one the user deselected
        val seenByBundle = withContext(Dispatchers.IO) {
            bundles.associate { it.uid to selectionRepository.getSeenPatches(configurationKey, it.uid) }
        }
        val newPatches = bundles.mapNotNull { bundle ->
            val seen = seenByBundle[bundle.uid] ?: return@mapNotNull null
            val new = bundle.patches.mapTo(mutableSetOf()) { it.name } - seen
            new.takeIf { it.isNotEmpty() }?.let { bundle.uid to it }
        }.toMap()

        return Selection(
            // Runs after validation, so patches the sources dropped never sneak back in
            patches = mergeNewlyAdded(
                bundles = bundles,
                validated = validated,
                known = seenByBundle::get,
                installerType = installerType,
                apkArchitecture = apkArchitecture
            ),
            removedCount = saved.values.sumOf { it.size } - validated.values.sumOf { it.size },
            newPatches = newPatches
        )
    }

    /**
     * Saved options, see [options].
     *
     * @param cleaned Whether anything saved was left out, which is when saving [options] back
     *   would change what is stored.
     */
    class SavedOptions(val options: Options, val cleaned: Boolean)

    /**
     * The options saved under [configurationKey] for [bundles], without those the patches no
     * longer declare or accept.
     */
    suspend fun options(configurationKey: String, bundles: List<PatchBundleInfo.Scoped>): SavedOptions {
        val patchesByName = bundles.patchesByName()
        val uids = patchesByName.keys
        val otherBuildPatches = bundles.otherBuildPatchesByUid()
        val saved = withContext(Dispatchers.IO) {
            optionsRepository.getAllOptionsForPackage(configurationKey, patchesByName)
        }
            .filterKeys { it in uids }
            .mapValues { (bundleUid, patchOptions) -> patchOptions - otherBuildPatches[bundleUid].orEmpty() }
        val validated = validatePatchOptions(saved, patchesByName)
        return SavedOptions(validated, cleaned = validated != saved)
    }

    /**
     * Records [selection] under [configurationKey], clearing what was saved for the sources in
     * [scope] it takes nothing from.
     *
     * @param options Saved with it where the patches were picked by hand. Null leaves the saved
     *   ones alone, as simple mode keeps its options on the app's preference screen.
     * @param seenPatches Per source, every patch it carries now. Without this snapshot the next
     *   run cannot tell a patch the user deselected from one added since.
     * @param otherBuildPatches Per source, the patches of other builds of the app, whose saved
     *   selection, options and seen state are kept as they are.
     */
    suspend fun save(
        configurationKey: String,
        selection: PatchSelection,
        scope: Set<Int>,
        options: Options?,
        seenPatches: Map<Int, Set<String>>,
        otherBuildPatches: Map<Int, Set<String>>
    ) = withContext(Dispatchers.IO) {
        val kept = otherBuildPatches.filterValues { it.isNotEmpty() }
        val stored = if (kept.isEmpty()) emptyMap() else {
            selectionRepository.getAllSelectionsForPackage(configurationKey)
        }
        selectionRepository.updateSelection(configurationKey, keepingOtherBuilds(selection, stored, kept), scope)
        options?.let { optionsRepository.saveOptions(configurationKey, it, keeping = kept) }
        seenPatches.forEach { (bundleUid, patchNames) ->
            val seenForOtherBuilds = kept[bundleUid]?.let { names ->
                selectionRepository.getSeenPatches(configurationKey, bundleUid)?.intersect(names)
            }.orEmpty()
            selectionRepository.saveSeenPatches(configurationKey, bundleUid, patchNames + seenForOtherBuilds)
        }
    }

    private suspend fun saved(configurationKey: String, bundles: List<PatchBundleInfo.Scoped>): PatchSelection {
        val byUid = bundles.associateBy { it.uid }
        return withContext(Dispatchers.IO) {
            selectionRepository.getAllSelectionsForPackage(configurationKey)
        }
            .filterKeys { it in byUid }
            .mapValues { (bundleUid, names) -> names - byUid.getValue(bundleUid).otherBuildPatches }
            .filterValues { it.isNotEmpty() }
    }

    private fun List<PatchBundleInfo.Scoped>.patchesByName() =
        associate { it.uid to it.patches.associateBy { patch -> patch.name } }
}
