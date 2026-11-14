/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import androidx.compose.runtime.Immutable

/**
 * What a home screen card says about its app. A card has room for one line of it, so an app is
 * described by the state that matters most, see [HomeAppItem.status], and its dialog tells the rest.
 */
@Immutable
sealed interface HomeAppStatus {
    /** Whether rebuilding the app is what this state asks for, which the card then offers. */
    val offersRebuild: Boolean get() = false

    /** The install has not been checked against the record yet. */
    data object Pending : HomeAppStatus

    /** A build without the patches went in over the one Morphe made. */
    data object UnpatchedInstall : HomeAppStatus

    /** The build is gone from the device, with its record kept. */
    data object Uninstalled : HomeAppStatus

    /** The install could not be checked against the record. */
    data object Unverified : HomeAppStatus

    /** The sources support a newer [version] of the app, which takes a newer APK. */
    data class NewerVersion(val version: String) : HomeAppStatus {
        override val offersRebuild: Boolean get() = true
    }

    /** Newer patches for the version installed, which the APK already there can take. */
    data object NewerPatches : HomeAppStatus {
        override val offersRebuild: Boolean get() = true
    }

    /** Patched and up to date, at [version], empty where neither the device nor the record knows it. */
    data class Patched(val version: String) : HomeAppStatus

    /** Morphe has no build of the app yet. */
    data object NotPatchedYet : HomeAppStatus
}
