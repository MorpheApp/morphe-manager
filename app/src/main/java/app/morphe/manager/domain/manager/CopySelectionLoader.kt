/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.SelectionSetRepository
import app.morphe.manager.domain.repository.SelectionSetSummary
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.util.AppDataResolver
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Assemble the candidate list for
 * [app.morphe.manager.ui.screen.shared.SelectionPanel], excluding the
 * target itself and enriching each row with the intersection count against
 * [targetPatchNames], same-source detection, and a resolved app display name.
 * Saved selections are offered along with the sets of other apps, one row per source a set uses.
 * Only those with a patch the target has are offered, as any other would copy nothing.
 *
 * Sorted so the most useful match is first: same normalized endpoint, then same
 * package, then largest intersection, then bundle name.
 */
suspend fun loadCopySelectionCandidates(
    patchSelectionRepository: PatchSelectionRepository,
    setRepository: SelectionSetRepository,
    patchBundleRepository: PatchBundleRepository,
    appDataResolver: AppDataResolver,
    targetPackageName: String,
    targetBundleUid: Int,
    targetPatchNames: Set<String>
): List<CopySelectionCandidate> {
    val summary = patchSelectionRepository.getSelectionsSummaryFlow().first()
    val sets = setRepository.summaries.first().filter { it.packageName != targetPackageName }
    if (summary.isEmpty() && sets.isEmpty()) return emptyList()

    val sources = patchBundleRepository.sources.first().associateBy { it.uid }
    val targetEndpointKey = normalizedEndpointKey(patchBundleRepository, targetBundleUid)
    val displayNameCache = mutableMapOf<String, String>()

    suspend fun candidate(
        packageName: String,
        bundleUid: Int,
        sourcePatches: Collection<String>,
        set: SelectionSetSummary? = null
    ): CopySelectionCandidate? {
        val source = sources[bundleUid] ?: return null
        if (sourcePatches.isEmpty()) return null

        val applicable = sourcePatches.count { it in targetPatchNames }
        if (applicable == 0) return null
        val packageDisplayName = displayNameCache.getOrPut(packageName) {
            appDataResolver.resolveAppData(packageName).displayName
        }

        return CopySelectionCandidate(
            bundleUid = bundleUid,
            bundleName = source.displayTitle,
            packageName = packageName,
            packageDisplayName = packageDisplayName,
            sourceCount = sourcePatches.size,
            applicableCount = applicable,
            isSameSource = targetEndpointKey != null &&
                    normalizedEndpointKey(patchBundleRepository, bundleUid) == targetEndpointKey,
            isSamePackage = packageName == targetPackageName,
            setUid = set?.uid,
            setName = set?.name
        )
    }

    val selections = summary.flatMap { (packageName, bundleMap) ->
        bundleMap.keys.mapNotNull { bundleUid ->
            if (bundleUid == targetBundleUid && packageName == targetPackageName) return@mapNotNull null
            candidate(
                packageName = packageName,
                bundleUid = bundleUid,
                sourcePatches = patchSelectionRepository.exportForPackageAndBundle(packageName, bundleUid)
            )
        }
    }
    val setCandidates = sets.flatMap { set ->
        setRepository.patchNames(set.uid).mapNotNull { (bundleUid, patchNames) ->
            candidate(set.packageName, bundleUid, patchNames, set)
        }
    }

    return (selections + setCandidates).sortedWith(
        compareByDescending<CopySelectionCandidate> { it.isSameSource }
            .thenByDescending { it.isSamePackage }
            .thenByDescending { it.applicableCount }
            .thenBy { it.bundleName.lowercase(Locale.getDefault()) }
    )
}

/**
 * Normalised endpoint identity, used to detect the same remote source imported
 * under multiple uids. Returns null for local bundles or when the URL cannot be parsed.
 */
private fun normalizedEndpointKey(
    patchBundleRepository: PatchBundleRepository,
    bundleUid: Int
): String? {
    val endpoint = patchBundleRepository.getEndpointForUid(bundleUid) ?: return null
    return runCatching { patchBundleRepository.normalizeRemoteBundleUrl(endpoint) }
        .getOrElse { endpoint.lowercase(Locale.US) }
}
