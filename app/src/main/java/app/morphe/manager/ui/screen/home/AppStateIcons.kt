/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.autoMirrored
import app.morphe.patcher.patch.FormFactor
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Icons an app's states go by, shared by its home card and its details so a state is told by the
 * same picture in both.
 */
internal object AppStateIcons {
    val Clone: ImageVector get() = MaterialSymbols.Rounded.Content_copy
    val Uninstalled: ImageVector get() = MaterialSymbols.Rounded.Delete
    val NotPatched: ImageVector get() = MaterialSymbols.Rounded.Auto_fix_high
    val Unverified: ImageVector get() = MaterialSymbols.Rounded.Help.autoMirrored
    val Pending: ImageVector get() = MaterialSymbols.Rounded.Hourglass_empty

    /** A newer app version the sources support, which takes a newer APK. */
    val NewerVersion: ImageVector get() = MaterialSymbols.Rounded.Arrow_upward

    /** Newer patches for the version installed, which the APK already there can take. */
    val NewerPatches: ImageVector get() = MaterialSymbols.Rounded.Extension
}

/** How an app's card and details name and draw the device the app is built for. */
internal class FormFactorMark(@StringRes val label: Int, val icon: ImageVector)

/** The mark of a device other than a phone, null for a phone app or one any device runs. */
internal fun FormFactor?.mark(): FormFactorMark? = when (this) {
    FormFactor.TV -> FormFactorMark(R.string.form_factor_tv, MaterialSymbols.Rounded.Tv)
    FormFactor.WEARABLE -> FormFactorMark(R.string.form_factor_wearable, MaterialSymbols.Rounded.Watch)
    FormFactor.AUTOMOTIVE -> FormFactorMark(R.string.form_factor_automotive, MaterialSymbols.Rounded.Directions_car)
    FormFactor.MOBILE, null -> null
}
