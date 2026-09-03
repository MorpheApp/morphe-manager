/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.batch

import android.os.Parcelable
import app.morphe.manager.ui.model.PatchRunProgress
import app.morphe.manager.ui.model.producesClone
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * One app a batch run is aimed at.
 *
 * @param packageName The app itself, which is what its APK and its patches are found by.
 * @param repatchedPackageName The tracked install to rebuild, when the run is aimed at one of
 *   several the app was cloned into. Null rebuilds the app's own install, or creates it.
 */
@Parcelize
data class BatchTarget(
    val packageName: String,
    val repatchedPackageName: String? = null
) : Parcelable {
    /** Identifies the queued app, which the package name alone cannot once clones exist. */
    val id get() = repatchedPackageName ?: packageName
}

/**
 * Where the unpatched APK for a queued app comes from.
 *
 * Sources are resolved during planning but materialized as late as possible: copying an
 * installed APK or packing its splits is expensive and pointless for an item the user
 * ends up excluding before starting the queue.
 */
sealed interface BatchApkSource {
    val version: String
    val versionCode: Long?

    /** Original APK kept by the manager after a previous patch, ready to use as is. */
    data class SavedOriginal(
        val file: File,
        override val version: String,
        override val versionCode: Long?
    ) : BatchApkSource

    /** Unpatched APK of the app currently installed on the device. */
    data class Installed(
        val apkPath: String,
        val splitPaths: List<String>,
        override val version: String,
        override val versionCode: Long?
    ) : BatchApkSource {
        val isSplit get() = splitPaths.isNotEmpty()
    }

    /** APK the user attached manually on the preflight screen. */
    data class UserFile(
        val file: File,
        override val version: String,
        override val versionCode: Long?
    ) : BatchApkSource
}

/**
 * Lifecycle of a single queued app. The planning states double as the reason an item is
 * not runnable, so the preflight screen never needs a parallel error field.
 */
enum class BatchItemState {
    /** Source and patches resolved, waiting for the queue. */
    READY,

    /** No saved original and no usable installed APK, the user has to attach a file. */
    NEEDS_APK,

    /** The source version is not covered by any enabled bundle. */
    VERSION_MISMATCH,

    /** The attached APK is not signed with a certificate the bundles declare for this app. */
    UNVERIFIED_SIGNATURE,

    /** No enabled bundle contributes any patch for this package. */
    NO_PATCHES,

    /** Removed from the run by the user. */
    EXCLUDED,

    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    /** True when the item can be handed to the patcher as is. */
    val isRunnable get() = this == READY

    /** True when the item is blocked by something the user can fix on the preflight screen. */
    val needsAttention get() =
        this == NEEDS_APK || this == VERSION_MISMATCH || this == UNVERIFIED_SIGNATURE || this == NO_PATCHES

    val isTerminal get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
}

/** What happened when the patched APK was handed to the installer. */
enum class BatchInstallOutcome { INSTALLED, FAILED }

/**
 * A patch source contributing to one queued app, kept for the run scope and for display.
 *
 * @param patchNames Every patch the source offered at plan time, written back as the seen
 *   snapshot after a successful run so the next plan can tell a genuinely new patch from one
 *   the user deselected.
 * @param renamingPatchNames Those of [patchNames] that build the app under a package name of
 *   their own, which is what a run needs to know to tell a copy of the app from the app itself.
 */
data class BatchBundleRef(
    val uid: Int,
    val name: String,
    val version: String?,
    val patchNames: Set<String>,
    val renamingPatchNames: Set<String>
)

/**
 * A single app queued for patching, carrying everything the patcher needs so the run
 * itself never has to ask the user anything.
 *
 * @param bundles Every enabled source that contributes patches to this app. An app covered by
 *   more than one source is patched with all of them at once, exactly like the single-app flow.
 * @param forceVersionMismatch Set when the user chose to patch despite an unsupported version.
 * @param forceUnverifiedSignature Set when the user chose to patch an APK whose signing certificate
 *   none of the bundles vouch for. Carried along so re-resolving the item does not ask again.
 * @param restoreState State to return to when the user un-excludes the item.
 * @param patchedFile Populated after a successful run with the retained patched APK.
 * @param installOutcome Set once the user installs from the summary, so a failure is visible
 *   on the app it belongs to rather than only in a toast that is gone a moment later.
 */
data class BatchPatchItem(
    val target: BatchTarget,
    val appName: String,
    val source: BatchApkSource?,
    val selection: PatchSelection,
    val options: Options,
    val bundles: List<BatchBundleRef>,
    val state: BatchItemState,
    val message: String? = null,
    /** True when the sources mark this APK version as experimental. */
    val experimentalVersion: Boolean = false,
    /** Version the sources recommend, offered for download when the APK is missing or wrong. */
    val suggestedVersion: String? = null,
    val forceVersionMismatch: Boolean = false,
    val forceUnverifiedSignature: Boolean = false,
    /** Selection before the user narrowed it to one source, so another can still be chosen. */
    val resolvedSelection: PatchSelection? = null,
    val restoreState: BatchItemState? = null,
    val patchedFile: File? = null,
    val installOutcome: BatchInstallOutcome? = null,
    val installMessage: String? = null,
    /** Package the app ended up under, which patching may have renamed. */
    val installedPackageName: String? = null
) {
    val id get() = target.id
    val packageName get() = target.packageName

    /** Where this item's patches and options are saved, which for a clone is its own package. */
    val configurationKey get() = target.id

    val patchCount get() = selection.values.sumOf { it.size }
    val version get() = source?.version

    /**
     * Whether patching this item under [resultPackageName] produced a copy of the app rather
     * than the install the app itself has.
     */
    fun producesCloneAs(resultPackageName: String) = producesClone(
        originalPackageName = packageName,
        resultPackageName = resultPackageName,
        selection = selection,
        declaresPackageName = { bundleUid, patchName ->
            bundles.any { it.uid == bundleUid && patchName in it.renamingPatchNames }
        }
    )
}

/** What the queue does with each APK once patching succeeds. */
enum class BatchInstallPolicy {
    /** Keep the patched APK only, the user installs later from the summary. */
    SAVE_ONLY,

    /** Install every patched APK right after the queue finishes patching. */
    INSTALL_AFTER
}

/** Phase of a batch run, drives which part of the batch screen is visible. */
enum class BatchPhase {
    PLANNING,
    PREFLIGHT,
    RUNNING,
    FINISHED
}

/**
 * Immutable snapshot of a batch run, published by
 * [app.morphe.manager.domain.batch.BatchPatchCoordinator] on every state change.
 *
 * @param activeRun Live step, log and progress state of the app being patched right now.
 *   The batch screen hands it to the same patching screens the single-app flow uses.
 */
data class BatchRunState(
    val items: List<BatchPatchItem>,
    val phase: BatchPhase,
    val policy: BatchInstallPolicy,
    val useMount: Boolean = false,
    val activeIndex: Int? = null,
    val activeRun: PatchRunProgress? = null
) {
    val activeItem get() = activeIndex?.let(items::getOrNull)
    val runnable get() = items.filter { it.state.isRunnable }
    val succeeded get() = items.count { it.state == BatchItemState.SUCCEEDED }
    val failed get() = items.count { it.state == BatchItemState.FAILED }
    val skipped get() = items.count { it.state.needsAttention || it.state == BatchItemState.EXCLUDED }

    /** Number of items already processed by the queue, used for the "3 / 7" counter. */
    val processed get() = items.count { it.state.isTerminal }

    /** Total number of items the queue will process in this run. */
    val total get() = items.count { it.state.isRunnable || it.state.isTerminal || it.state == BatchItemState.RUNNING }

    val isActive get() = phase == BatchPhase.RUNNING

    /** Successful items that still have their patched APK on disk, in queue order. */
    val patchedItems get() = items.filter { it.state == BatchItemState.SUCCEEDED && it.patchedFile != null }
}
