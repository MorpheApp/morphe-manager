/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.ui.screen.shared.CopySelectionCandidate
import app.morphe.manager.util.AppDataResolver
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Assemble the candidate list for
 * [app.morphe.manager.ui.screen.shared.CopySelectionFromBundleDialog], excluding the
 * target itself and enriching each row with the intersection count against
 * [targetPatchNames], same-source detection, and a resolved app display name.
 *
 * Sorted so the most useful match is first: same normalized endpoint, then same
 * package, then largest intersection, then bundle name.
 */
suspend fun loadCopySelectionCandidates(
    patchSelectionRepository: PatchSelectionRepository,
    patchBundleRepository: PatchBundleRepository,
    appDataResolver: AppDataResolver,
    targetPackageName: String,
    targetBundleUid: Int,
    targetPatchNames: Set<String>
): List<CopySelectionCandidate> {
    val summary = patchSelectionRepository.getSelectionsSummaryFlow().first()
    if (summary.isEmpty()) return emptyList()

    val sources = patchBundleRepository.sources.first().associateBy { it.uid }
    val targetEndpointKey = normalizedEndpointKey(patchBundleRepository, targetBundleUid)
    val displayNameCache = mutableMapOf<String, String>()

    val candidates = summary.flatMap { (packageName, bundleMap) ->
        bundleMap.mapNotNull { (bundleUid, _) ->
            if (bundleUid == targetBundleUid && packageName == targetPackageName) return@mapNotNull null

            val source = sources[bundleUid] ?: return@mapNotNull null
            val sourcePatches = patchSelectionRepository.exportForPackageAndBundle(packageName, bundleUid)
            if (sourcePatches.isEmpty()) return@mapNotNull null

            val applicable = sourcePatches.count { it in targetPatchNames }
            val packageDisplayName = displayNameCache.getOrPut(packageName) {
                appDataResolver.resolveAppData(packageName).displayName
            }

            CopySelectionCandidate(
                bundleUid = bundleUid,
                bundleName = source.displayTitle,
                packageName = packageName,
                packageDisplayName = packageDisplayName,
                sourceCount = sourcePatches.size,
                applicableCount = applicable,
                isSameSource = targetEndpointKey != null &&
                        normalizedEndpointKey(patchBundleRepository, bundleUid) == targetEndpointKey,
                isSamePackage = packageName == targetPackageName
            )
        }
    }

    return candidates.sortedWith(
        compareByDescending<CopySelectionCandidate> { it.isSameSource }
            .thenByDescending { it.isSamePackage }
            .thenByDescending { it.applicableCount }
            .thenBy { it.bundleName.lowercase(Locale.getDefault()) }
    )
}

/**
 * Reduce a raw options export to the (patch, option) pairs that still exist in
 * [targetPatches] - required before importing into a bundle whose schema may
 * differ from the source's (renamed patches, dropped option keys).
 */
fun filterOptionsForTarget(
    sourceOptions: Map<String, Map<String, String>>,
    targetPatches: Map<String, PatchInfo>
): Map<String, Map<String, String>> = sourceOptions.mapNotNull { (patchName, patchOptions) ->
    val targetPatch = targetPatches[patchName] ?: return@mapNotNull null
    val validKeys = targetPatch.options?.mapTo(mutableSetOf()) { it.key } ?: return@mapNotNull null
    val kept = patchOptions.filterKeys { it in validKeys }
    if (kept.isEmpty()) null else patchName to kept
}.toMap()

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
