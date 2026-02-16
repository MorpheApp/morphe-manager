package app.morphe.manager.ui.model.navigation

import android.os.Parcelable
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
data object Patcher : ComplexParameter<Patcher.ViewModelParams> {
    @Parcelize
    data class ViewModelParams(
        val selectedApp: SelectedApp,
        val selectedPatches: PatchSelection,
        val options: @RawValue Options
    ) : Parcelable
}
