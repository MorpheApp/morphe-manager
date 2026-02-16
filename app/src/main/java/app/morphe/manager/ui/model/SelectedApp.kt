package app.morphe.manager.ui.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

sealed interface SelectedApp : Parcelable {
    val packageName: String
    val version: String?

    @Parcelize
    data class Download(
        override val packageName: String,
        override val version: String?
    ) : SelectedApp

    @Parcelize
    data class Search(override val packageName: String, override val version: String?) : SelectedApp

    @Parcelize
    data class Local(
        override val packageName: String,
        override val version: String,
        val file: File,
        val temporary: Boolean,
        val resolved: Boolean = true
    ) : SelectedApp

    @Parcelize
    data class Installed(
        override val packageName: String,
        override val version: String
    ) : SelectedApp
}
