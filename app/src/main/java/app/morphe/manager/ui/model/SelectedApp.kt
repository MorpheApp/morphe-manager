/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/ui/model/SelectedApp.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

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
