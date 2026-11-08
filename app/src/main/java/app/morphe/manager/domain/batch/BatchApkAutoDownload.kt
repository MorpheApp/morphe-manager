/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.batch

import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.util.compareVersions

/**
 * Versions an automatic helper pass may choose from for [item]: the sources the app is patched
 * from, the source the user picked, or every source while nothing has contributed yet. Sources
 * keep the order the user put them in, which is what makes one version the recommended one.
 */
internal fun batchAutoDownloadTargets(
    item: BatchPatchItem,
    all: List<BundledAppTarget>
): List<BundledAppTarget> {
    val uids = item.bundles.mapTo(mutableSetOf()) { it.uid }
    val scoped = when {
        uids.isNotEmpty() -> all.filter { it.bundleUid in uids }
        item.chosenBundleUid != null -> all.filter { it.bundleUid == item.chosenBundleUid }
        else -> all
    }
    return scoped.ifEmpty { all }
}

/**
 * Whether an automatic helper pass should fetch an APK for this queued app.
 *
 * The pass asks for the newest version the patches offer, so an app is wanted when it has no
 * APK at all, when the one it has is older than that version, or when a version the patches do
 * accept still carries a build code they do not. An app already at the newest version is left
 * alone, and one past it is never downgraded. An app the pass cannot check the signature of, one
 * the user took out of the run, and one whose patches are not selected have nothing to gain from
 * a download either.
 *
 * An app whose selection was edited by hand is left as it is: fetching another APK resolves the
 * app from scratch, which would drop that edit without the user asking for it. The same goes for
 * a version the user accepted despite the patches not supporting it, and for a version they
 * turned down on the home screen, which they can still pick by hand.
 *
 * @param newest Newest version the sources offer for the app, null when they name none, which is
 *   what universal patches leave behind.
 * @param canVerifySignature Whether the APK this item would receive can have its signature
 *   checked, see [app.morphe.manager.ui.model.helperSignatureCheckAvailable].
 * @param ignoredVersion Version the user turned down for this app on the home screen, if any.
 */
internal fun BatchPatchItem.autoDownloadWanted(
    newest: BundledAppTarget?,
    canVerifySignature: Boolean,
    ignoredVersion: String? = null
): Boolean {
    if (!canVerifySignature || selectionEdited || forceVersionMismatch) return false
    if (ignoredVersion != null && newest?.target?.version == ignoredVersion) return false

    when (state) {
        BatchItemState.NEEDS_APK,
        BatchItemState.READY,
        BatchItemState.VERSION_MISMATCH,
        BatchItemState.UNVERIFIED_SIGNATURE -> Unit
        else -> return false
    }

    val target = newest?.target
    // Nothing names a version, so only an app without an APK has something to download
    if (target == null) return state == BatchItemState.NEEDS_APK

    val source = source ?: return true
    val comparison = compareVersions(source.version, target.version)
    if (comparison != 0) return comparison < 0

    // READY already means some patch accepts the build code, so only a mismatch can be refused
    // over it while the version itself is the newest one
    if (state != BatchItemState.VERSION_MISMATCH) return false

    val codes = newest.buildCodes
    val versionCode = source.versionCode
    return codes != null &&
            versionCode != null &&
            versionCode.toInt() !in codes
}
