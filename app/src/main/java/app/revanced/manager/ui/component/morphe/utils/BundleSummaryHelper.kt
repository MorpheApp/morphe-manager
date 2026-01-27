package app.revanced.manager.ui.component.morphe.utils

import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.data.room.profile.PatchProfilePayload
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.ui.viewmodel.InstalledAppsViewModel
import app.revanced.manager.util.PatchSelection

/**
 * Build bundle summaries for an installed app
 * Used to show which bundles were used to patch the app
 */
fun buildBundleSummaries(
    app: InstalledApp,
    selection: PatchSelection,
    bundleInfo: Map<Int, Any>,
    sourceMap: Map<Int, PatchBundleSource>
): List<InstalledAppsViewModel.AppBundleSummary> {
    val payloadBundles = app.selectionPayload?.bundles.orEmpty()
    val summaries = mutableListOf<InstalledAppsViewModel.AppBundleSummary>()
    val processed = mutableSetOf<Int>()

    // Process bundles from selection
    selection.keys.forEach { uid ->
        processed += uid
        buildSummaryEntry(uid, payloadBundles, bundleInfo, sourceMap)?.let(summaries::add)
    }

    // Process remaining bundles from payload
    payloadBundles.forEach { bundle ->
        if (bundle.bundleUid in processed) return@forEach
        buildSummaryEntry(bundle.bundleUid, payloadBundles, bundleInfo, sourceMap)?.let(summaries::add)
    }

    return summaries
}

/**
 * Build a single bundle summary entry
 */
private fun buildSummaryEntry(
    uid: Int,
    payloadBundles: List<PatchProfilePayload.Bundle>,
    bundleInfo: Map<Int, Any>,
    sourceMap: Map<Int, PatchBundleSource>
): InstalledAppsViewModel.AppBundleSummary? {
    val info = (bundleInfo[uid] as? PatchBundleInfo.Global)
    val source = sourceMap[uid]
    val payloadBundle = payloadBundles.firstOrNull { it.bundleUid == uid }

    val title = source?.displayTitle
        ?: payloadBundle?.displayName
        ?: payloadBundle?.sourceName
        ?: info?.name
        ?: return null

    val version = payloadBundle?.version?.takeUnless { it.isBlank() } ?: info?.version
    return InstalledAppsViewModel.AppBundleSummary(title = title, version = version)
}
