/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.network.dto

/** What a check for a newer version of the manager came back with. */
sealed interface AppUpdateCheck {
    /** A newer version is out and its APK can be downloaded. */
    data class Available(val asset: ReleaseAsset) : AppUpdateCheck

    /** The installed version is the newest one offered on its channel. */
    data object UpToDate : AppUpdateCheck

    /** The check could not be made, which says nothing about whether there is an update. */
    data object Failed : AppUpdateCheck
}
