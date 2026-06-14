package app.morphe.manager.ui.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

sealed interface SelectedApp : Parcelable {
    val packageName: String
    val version: String?
    val versionCode: Long?

    @Parcelize
    data class Local(
        override val packageName: String,
        override val version: String,
        override val versionCode: Long? = null,
        val file: File,
        val temporary: Boolean,
        val resolved: Boolean = true,
        val fromInstalledDevice: Boolean = false
    ) : SelectedApp

    @Parcelize
    data class Installed(
        override val packageName: String,
        override val version: String,
        override val versionCode: Long? = null
    ) : SelectedApp
}
