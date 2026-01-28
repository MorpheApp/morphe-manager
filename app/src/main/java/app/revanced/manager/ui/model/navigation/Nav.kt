package app.revanced.manager.ui.model.navigation

import android.os.Parcelable
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlinx.serialization.Serializable

interface ComplexParameter<T : Parcelable>

@Serializable
object MorpheHomeScreen

@Serializable
object MorpheSettings

@Serializable
data class Update(val downloadOnScreenEntry: Boolean = false)

@Serializable
data object SelectedApplicationInfo : ComplexParameter<SelectedApplicationInfo.ViewModelParams> {
    @Parcelize
    data class ViewModelParams(
        val app: SelectedApp,
        val patches: PatchSelection? = null,
        val profileId: Int? = null,
        val requiresSourceSelection: Boolean = false
    ) : Parcelable

    @Serializable
    object Main

    @Serializable
    data object PatchesSelector : ComplexParameter<PatchesSelector.ViewModelParams> {
        @Parcelize
        data class ViewModelParams(
            val app: SelectedApp,
            val currentSelection: PatchSelection?,
            val options: @RawValue Options,
            val preferredAppVersion: String? = null,
            val missingPatchNames: @RawValue List<String>? = null,
            val preferredBundleVersion: String? = null,
            val preferredBundleUid: Int? = null,
            val preferredBundleOverride: String? = null,
            val preferredBundleTargetsAllVersions: Boolean = false
        ) : Parcelable
    }

    @Serializable
    data object RequiredOptions : ComplexParameter<PatchesSelector.ViewModelParams>
}

@Serializable
data object Patcher : ComplexParameter<Patcher.ViewModelParams> {
    @Parcelize
    data class ViewModelParams(
        val selectedApp: SelectedApp,
        val selectedPatches: PatchSelection,
        val options: @RawValue Options
    ) : Parcelable
}
