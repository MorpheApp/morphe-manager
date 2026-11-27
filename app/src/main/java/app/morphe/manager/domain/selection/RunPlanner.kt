/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.selection

import app.morphe.manager.domain.installer.RootInstaller
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.domain.repository.withoutMutedSources
import app.morphe.manager.patcher.patch.ApkArchitectureResolver
import app.morphe.manager.patcher.patch.PatchBundleInfo
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.contributingAt
import app.morphe.manager.patcher.patch.PatchBundleInfo.Extensions.toPatchSelection
import app.morphe.manager.patcher.patch.installerTypeFor
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.util.Options
import app.morphe.manager.util.PM
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * A single-app run to plan, see [RunPlanner].
 *
 * @param bundleUid The source chosen for the APK. The run patches from it alone when it was picked
 *   by hand, unless the others are shown again in the expert dialog.
 * @param versionPicked Whether the user picked the version, which is a pick of its source too.
 * @param repatchPackageName The install a rebuild is for, whose saved patches it starts from.
 */
data class RunRequest(
    val app: SelectedApp,
    val useMount: Boolean,
    val allowIncompatible: Boolean,
    val bundleUid: Int?,
    val versionPicked: Boolean,
    val repatchPackageName: String?
)

/** What a single-app run does next once it has its APK, see [RunPlanner.plan]. */
sealed interface RunPlan {
    /** No source has patches for the APK. */
    data object NoPatches : RunPlan

    /**
     * The patches are picked by hand in the expert dialog.
     *
     * @param allBundles Every source the app has patches in, those in [mutedSources] included,
     *   which the dialog can offer back.
     * @param leadingBundleUid The source the dialog opens on.
     * @param removedCount Saved patches the sources no longer carry, which were left out.
     */
    class Expert(
        val allBundles: List<PatchBundleInfo.Scoped>,
        val mutedSources: Set<Int>,
        val leadingBundleUid: Int?,
        val patches: PatchSelection,
        val options: Options,
        val newPatches: Map<Int, Set<String>>,
        val target: PatchTarget,
        val removedCount: Int
    ) : RunPlan

    /** Simple mode found patches in several sources, and the user picks the one to patch from. */
    class ChooseSource(val candidates: List<Pair<PatchBundleInfo.Scoped, Set<String>>>) : RunPlan

    /**
     * Simple mode settled the patches, and the run starts.
     *
     * @param scope The sources the run was offered.
     */
    class Start(
        val patches: PatchSelection,
        val options: Options,
        val allowIncompatible: Boolean,
        val scope: Set<Int>
    ) : RunPlan
}

/** What simple mode makes of the sources, before anything is read for it, see [simpleModeChoice]. */
internal sealed interface SimpleChoice {
    /** No source selects anything by default, so the patches are picked by hand. */
    data object NothingByDefault : SimpleChoice

    /** The run starts with [patches]. */
    data class Patches(val patches: PatchSelection, val allowIncompatible: Boolean) : SimpleChoice

    /** Several sources have patches, and the user picks one. */
    data class Choose(val candidates: List<Pair<PatchBundleInfo.Scoped, Set<String>>>) : SimpleChoice
}

/**
 * How simple mode patches: with the default patches of a single source, the one the user chose
 * where there is one, and asking which where several have any. [contributing] are the sources
 * standing behind the version, [allBundles] every source the run may take, [chosenUid] included.
 */
internal fun simpleModeChoice(
    contributing: List<PatchBundleInfo.Scoped>,
    allBundles: List<PatchBundleInfo.Scoped>,
    chosenUid: Int?,
    target: PatchTarget,
    allowIncompatible: Boolean
): SimpleChoice {
    // A patch is applicable if it is universal, see PatchInfo.isUniversal, or names the package
    val withPatches = contributing
        .filter { it.enabled }
        .map { bundle ->
            bundle to bundle.patchSequence(allowIncompatible)
                .filter(target::defaultSelected)
                .mapTo(mutableSetOf()) { it.name }
        }
        .filter { (_, patches) -> patches.isNotEmpty() }

    // Third-party sources may ship every patch with use=false, which leaves them to be set up by hand
    if (withPatches.isEmpty()) return SimpleChoice.NothingByDefault

    // The user picked both this source and the version, so both are respected even where the
    // source does not list the version. One with no patches at all falls through
    allBundles.find { it.uid == chosenUid }?.let { chosen ->
        val patches = chosen.patchSequence(allowIncompatible = true)
            .filter(target::defaultSelected)
            .mapTo(mutableSetOf()) { it.name }
        if (patches.isNotEmpty()) return SimpleChoice.Patches(mapOf(chosen.uid to patches), allowIncompatible = true)
    }

    if (withPatches.size > 1) return SimpleChoice.Choose(withPatches)
    return SimpleChoice.Patches(withPatches.associate { (bundle, patches) -> bundle.uid to patches }, allowIncompatible)
}

/**
 * Decides how a single-app run goes on once it has its APK: which sources it takes, and whether
 * its patches are settled by simple mode or picked by hand in the expert dialog.
 */
class RunPlanner(
    private val patchBundleRepository: PatchBundleRepository,
    private val sourceMuteRepository: SourceMuteRepository,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val optionsRepository: PatchOptionsRepository,
    private val patchOptionsPrefs: PatchOptionsPreferencesManager,
    private val savedConfiguration: SavedPatchConfiguration,
    private val prefs: PreferencesManager,
    private val rootInstaller: RootInstaller,
    private val pm: PM
) {
    suspend fun plan(request: RunRequest): RunPlan {
        val app = request.app
        val expertMode = prefs.useExpertMode.get()

        // Read before the first selection is resolved, so every rule below and every later edit
        // in the expert dialog is answered for the APK this run actually starts from. The rules
        // are only ignored where the patches are shown, so nothing they would have kept out is
        // built in unseen
        val target = PatchTarget(
            installerType = installerTypeFor(request.useMount),
            apkArchitecture = ApkArchitectureResolver.resolve(app, pm),
            ignoresRules = expertMode && rootInstaller.isDeviceRooted() && prefs.ignoreInstallTargetRules.get()
        )

        // Everything this app has patches in, the sources it is kept from included: the expert
        // dialog offers those back, so it has to be told what it is hiding
        val availableBundles = patchBundleRepository
            .scopedBundleInfoFlow(app.packageName, app.version, app.versionCode)
            .first()
        if (availableBundles.isEmpty()) return RunPlan.NoPatches

        // A source picked by hand answers for the run alone, so the others are held back the way
        // the sources the app is kept from are, for this run only. The expert dialog offers them
        // back, which is how a run mixes sources. The preselected version is no such pick: it
        // leaves every source in, as a user who mixes sources expects
        val bundleUid = request.bundleUid
        val fromChosenAlone = !expertMode || request.versionPicked
        val heldBackSources = if (bundleUid != null && fromChosenAlone) {
            availableBundles.mapTo(mutableSetOf()) { it.uid } - bundleUid
        } else withContext(Dispatchers.IO) {
            sourceMuteRepository.getMutedFor(app.packageName)
        }
        // Every rule below reads this rather than the full list, so a hidden source contributes
        // no defaults, no new-patch badges and no tab
        val allBundles = availableBundles.withoutMutedSources(heldBackSources) { it.uid }

        val configurationKey = patchSelectionRepository.configurationKeyFor(app.packageName, request.repatchPackageName)

        // A source not standing behind this version, one marking it experimental with those
        // switched off, contributes nothing unless the user adds it back by hand
        val contributingBundles = allBundles.contributingAt(
            prefs.bundleExperimentalVersionsEnabled.get(),
            chosenUid = bundleUid
        )

        // Opens the selection by hand, which the patches' own availability for the install
        // target has the final say on unless the run is set to ignore it
        fun expert(
            patches: PatchSelection,
            options: Options,
            newPatches: Map<Int, Set<String>> = emptyMap(),
            removedCount: Int = 0
        ) = RunPlan.Expert(
            allBundles = availableBundles,
            mutedSources = heldBackSources,
            // The dialog opens on the source answering for the APK, or on the first one
            // contributing when the others were left out
            leadingBundleUid = bundleUid ?: contributingBundles
                .takeIf { it.size < allBundles.size }
                ?.firstOrNull()?.uid,
            patches = target.applyTo(patches, allBundles),
            options = options,
            newPatches = newPatches,
            target = target,
            removedCount = removedCount
        )

        if (expertMode) {
            val saved = savedConfiguration.selection(
                configurationKey,
                contributingBundles,
                target.installerType,
                target.apkArchitecture
            )
            // A hidden source keeps what was saved for it, so offering it back brings that along.
            // Set apart, since the run does not reach it: what it holds decides nothing about the
            // patches of the sources the run starts with
            val heldBackSelections = savedConfiguration.validSelection(
                configurationKey,
                availableBundles.filter { it.uid in heldBackSources }
            )

            // No saved selections - use default for the contributing bundles
            val patches = saved?.patches ?: contributingBundles.toPatchSelection(request.allowIncompatible) { _, patch ->
                target.defaultSelected(patch)
            }

            // Spans every source, hidden ones included: what the user saved for a hidden one has
            // to survive validation, or offering the source back would write emptiness over it
            val options = savedConfiguration.options(configurationKey, availableBundles)
            if (options.cleaned) {
                withContext(Dispatchers.IO) {
                    optionsRepository.saveOptions(configurationKey, options.options)
                }
            }

            return expert(
                patches = heldBackSelections + patches,
                options = options.options,
                newPatches = saved?.newPatches.orEmpty(),
                removedCount = saved?.removedCount ?: 0
            )
        }

        return when (val choice = simpleModeChoice(contributingBundles, allBundles, bundleUid, target, request.allowIncompatible)) {
            // Hidden sources are read here too, for the same reason the expert branch reads
            // them: the dialog can offer one back, and it has to have something to offer.
            // Validated as the expert branch does, so patches the sources dropped are not offered
            SimpleChoice.NothingByDefault -> expert(
                savedConfiguration.validSelection(configurationKey, availableBundles),
                savedConfiguration.options(configurationKey, availableBundles).options
            )

            is SimpleChoice.Choose -> RunPlan.ChooseSource(choice.candidates)

            // Patches simple mode picked go with the options it keeps per app, while a selection
            // made by hand carries its own
            is SimpleChoice.Patches -> RunPlan.Start(
                patches = target.applyTo(choice.patches, allBundles),
                options = withContext(Dispatchers.IO) { patchOptionsPrefs.exportPatchOptions(app.packageName) },
                allowIncompatible = choice.allowIncompatible,
                scope = allBundles.mapTo(mutableSetOf()) { it.uid }
            )
        }
    }
}
