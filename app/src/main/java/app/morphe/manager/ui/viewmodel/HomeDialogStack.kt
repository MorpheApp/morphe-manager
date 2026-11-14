/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.morphe.manager.patcher.patch.PatchBundleInfo

/**
 * A dialog of the patch flow, or the information on an installed app the flow can start from.
 * Each one opened goes over those already open, see [HomeDialogStack].
 */
sealed interface HomeDialog {
    /** [token] tells one opening of the app apart from the next, which starts afresh. */
    data class AppInfo(val packageName: String, val token: Int) : HomeDialog

    data class NoCompatibleVersions(val packageName: String) : HomeDialog

    /** Asks which of [candidates], each with the patches it would apply, to patch [packageName] from. */
    data class SimpleBundleSelect(
        val packageName: String,
        val candidates: List<Pair<PatchBundleInfo.Scoped, Set<String>>>
    ) : HomeDialog

    data class LowDiskSpace(val freeBytes: Long) : HomeDialog
    data object OutdatedPatches : HomeDialog

    /** A step of getting the APK to patch, which going back from returns to the step before. */
    sealed interface ApkSource : HomeDialog
    data object ApkAvailability : ApkSource
    data object DownloadInstructions : ApkSource
    data object FilePickerPrompt : ApkSource
    data object InstalledAppPicker : ApkSource

    data class WrongPackage(val state: WrongPackageDialogState) : HomeDialog
    data class SplitApkWarning(val packageName: String) : HomeDialog
    data class InvalidSignature(val state: InvalidSignatureDialogState) : HomeDialog
    data class UnsupportedVersion(val state: UnsupportedVersionDialogState) : HomeDialog
    data class ExperimentalVersion(val state: UnsupportedVersionDialogState) : HomeDialog
    data object ExpertMode : HomeDialog
    data object PrePatchInstaller : HomeDialog
}

/**
 * The [HomeDialog]s open, bottom first, which is the order they are drawn in. Held apart from the
 * screen, so the stack is the same one whenever the screen comes back to it.
 */
class HomeDialogStack {
    var entries by mutableStateOf<List<HomeDialog>>(emptyList())
        private set

    val top: HomeDialog? get() = entries.lastOrNull()

    /** Opens [dialog] over the others, or brings it up there if it is open already. */
    fun open(dialog: HomeDialog) {
        entries = entries - dialog + dialog
    }

    /**
     * Opens [dialog] as the run's next step, in place of the flow's dialogs over the app
     * information. They leave under it, which covers them from its first frame, so nothing behind
     * shows between them.
     */
    fun advanceTo(dialog: HomeDialog) {
        entries = entries.filterIsInstance<HomeDialog.AppInfo>() + dialog
    }

    fun close(dialog: HomeDialog) {
        entries = entries - dialog
    }

    /** Closes every dialog [predicate] picks, all of them unless told otherwise. */
    fun closeAll(predicate: (HomeDialog) -> Boolean = { true }) {
        entries = entries.filterNot(predicate)
    }
}
