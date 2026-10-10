/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/ui/model/navigation/Nav.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.ui.model.navigation

import android.os.Parcelable
import app.morphe.manager.domain.batch.BatchTarget
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlinx.serialization.Serializable

interface ComplexParameter<T : Parcelable>

@Serializable
object HomeScreen

@Serializable
object Settings

@Serializable
data object BatchPatcher : ComplexParameter<BatchPatcher.ViewModelParams> {
    @Parcelize
    data class ViewModelParams(
        val targets: List<BatchTarget>,
        val useMount: Boolean
    ) : Parcelable
}

@Serializable
data object Patcher : ComplexParameter<Patcher.ViewModelParams> {
    /**
     * Everything a patch run is started with, carried across navigation.
     *
     * @param selectedApp The APK to patch and where it came from.
     * @param selectedPatches The patches to apply, per source.
     * @param options The values configured for those patches, per source.
     * @param targetPackageName The install this run is aimed at when that is a clone rather than
     *   the app's own, which is not something [selectedApp] can say.
     * @param allowIncompatible Whether patches outside the APK's version were offered, kept so
     *   the selection reopened after a failed run offers the same ones.
     */
    @Parcelize
    data class ViewModelParams(
        val selectedApp: SelectedApp,
        val selectedPatches: PatchSelection,
        val options: @RawValue Options,
        val targetPackageName: String? = null,
        val allowIncompatible: Boolean = false
    ) : Parcelable
}
