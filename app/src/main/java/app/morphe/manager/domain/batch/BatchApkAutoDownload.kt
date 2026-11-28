/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.batch

import app.morphe.manager.domain.bundles.BundledAppTarget
import app.morphe.manager.util.compareVersions

/**
 * Versions the automatic pass may ask for [item], from the sources it is patched from. Their order
 * is kept, as it decides which version is the recommended one.
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
 * Whether the automatic pass should fetch [newest] for this app: it has no APK, one behind it, or
 * one past it that no patch accepts. Choices the user made by hand are left alone, since a new APK
 * resolves the app from scratch.
 *
 * @param patchedVersion The newest version among the installs the run rebuilds, see [replacedBy].
 */
internal fun BatchPatchItem.autoDownloadWanted(
    newest: BundledAppTarget?,
    canVerifySignature: Boolean,
    ignoredVersion: String? = null,
    patchedVersion: String? = null
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

    // Nothing names a version, so only an app without an APK has something to download
    val target = newest?.target ?: return state == BatchItemState.NEEDS_APK

    val source = source ?: return true
    val comparison = compareVersions(source.version, target.version)
    if (comparison < 0) return true
    // Going back a version is safe only where nothing installed has to follow: a mount needs the
    // stock app at the patched version, and Android refuses an older build over a newer one
    if (comparison > 0) {
        return state == BatchItemState.VERSION_MISMATCH && !useMount &&
                compareVersions(patchedVersion, target.version) <= 0
    }

    // READY already means some patch accepts the build code, so only a mismatch can be refused
    // over it while the version itself is the newest one
    if (state != BatchItemState.VERSION_MISMATCH) return false

    val codes = newest.buildCodes
    val versionCode = source.versionCode
    return codes != null &&
            versionCode != null &&
            versionCode.toInt() !in codes
}
